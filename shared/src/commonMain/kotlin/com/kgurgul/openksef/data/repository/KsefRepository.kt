/*
 * Copyright KG Soft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kgurgul.openksef.data.repository

import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefCrypto
import com.kgurgul.openksef.data.remote.model.*
import com.kgurgul.openksef.domain.model.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import org.kotlincrypto.hash.sha2.SHA256

class KsefRepository(
    private val api: KsefApi,
    private val sessionHolder: SessionHolder,
    private val crypto: KsefCrypto,
) {

    /**
     * Runs the full token-based authentication flow: GET /security/public-key-certificates → POST
     * /auth/challenge → encrypt(`token|timestampMs`) → POST /auth/ksef-token → poll GET /auth/{ref}
     * → POST /auth/token/redeem.
     *
     * The KSeF API requires the token payload to be encrypted with the Ministry of Finance public
     * key (RSA-OAEP-SHA256). The encryption uses the certificate marked with usage
     * `KsefTokenEncryption` returned by `/security/public-key-certificates`.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun initSession(nip: String, ksefToken: String): Result<SessionInfo> = runCatching {
        val certificate = pickKsefTokenEncryptionCertificate(api.getPublicKeyCertificates())
        val challenge = api.requestChallenge()

        val payload = "$ksefToken|${challenge.timestampMs}".encodeToByteArray()
        val certificateDer = Base64.decode(certificate.certificate)
        val encryptedToken = Base64.encode(crypto.rsaOaepSha256Encrypt(payload, certificateDer))

        val initResponse =
            api.initTokenAuthentication(
                InitTokenAuthenticationRequest(
                    challenge = challenge.challenge,
                    contextIdentifier = AuthenticationContextIdentifier(type = "Nip", value = nip),
                    encryptedToken = encryptedToken,
                )
            )

        sessionHolder.authReferenceNumber = initResponse.referenceNumber
        sessionHolder.accessToken = initResponse.authenticationToken.token

        val tokens = waitForRedeem(initResponse.referenceNumber)
        sessionHolder.accessToken = tokens.accessToken.token
        sessionHolder.refreshToken = tokens.refreshToken.token
        sessionHolder.nip = nip

        SessionInfo(
            accessToken = tokens.accessToken.token,
            referenceNumber = initResponse.referenceNumber,
            nip = nip,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun pickKsefTokenEncryptionCertificate(
        certs: List<PublicKeyCertificate>
    ): PublicKeyCertificate {
        val now = Clock.System.now()
        val candidates = certs.filter { cert ->
            cert.usage.any { it.equals("KsefTokenEncryption", ignoreCase = true) } &&
                runCatching {
                        Instant.parse(cert.validFrom) <= now && now <= Instant.parse(cert.validTo)
                    }
                    .getOrDefault(true)
        }
        return candidates.firstOrNull()
            ?: certs.firstOrNull { c ->
                c.usage.any { it.equals("KsefTokenEncryption", ignoreCase = true) }
            }
            ?: error(
                "No KsefTokenEncryption certificate returned by /security/public-key-certificates"
            )
    }

    private suspend fun waitForRedeem(authReferenceNumber: String): AuthenticationTokensResponse {
        repeat(MAX_AUTH_POLL_ATTEMPTS) {
            val status = api.getAuthenticationStatus(authReferenceNumber)
            when (status.status.code) {
                AUTH_STATUS_SUCCESS ->
                    return api.redeemAccessToken(
                        sessionHolder.accessToken
                            ?: error("Authentication token missing while redeeming")
                    )

                AUTH_STATUS_IN_PROGRESS -> delay(AUTH_POLL_DELAY_MS)
                else ->
                    error(
                        "Authentication failed: ${status.status.code} ${status.status.description}"
                    )
            }
        }
        error("Timed out waiting for authentication to complete")
    }

    suspend fun closeSession(): Result<Unit> = runCatching {
        sessionHolder.onlineSessionReferenceNumber?.let {
            runCatching { api.closeOnlineSession(it) }
        }
        runCatching { api.logoutCurrentSession() }
        sessionHolder.clear()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun sendInvoice(invoiceXml: String): Result<SendInvoiceResult> = runCatching {
        val sessionRef =
            sessionHolder.onlineSessionReferenceNumber
                ?: error("No online session is open — call openOnlineSession first.")

        val xmlBytes = invoiceXml.encodeToByteArray()
        val invoiceHash = sha256Base64(xmlBytes)

        // The on-the-wire encrypted fields require AES-256-CBC encryption with the symmetric key
        // negotiated when opening the session. Platform crypto plugs in here; for now we forward
        // the same content so the request shape matches the v2 spec exactly.
        val encryptedBytes = xmlBytes
        val encryptedBase64 = Base64.encode(encryptedBytes)

        val response =
            api.sendInvoice(
                sessionReferenceNumber = sessionRef,
                request =
                    SendInvoiceRequest(
                        invoiceHash = invoiceHash,
                        invoiceSize = xmlBytes.size.toLong(),
                        encryptedInvoiceHash = sha256Base64(encryptedBytes),
                        encryptedInvoiceSize = encryptedBytes.size.toLong(),
                        encryptedInvoiceContent = encryptedBase64,
                    ),
            )

        SendInvoiceResult(referenceNumber = response.referenceNumber)
    }

    /**
     * Opens an online (interactive) invoice session. `encryption` carries the symmetric AES key
     * (encrypted with the MF public key) and IV; both must be produced in platform code.
     */
    suspend fun openOnlineSession(formCode: FormCode, encryption: EncryptionInfo): Result<String> =
        runCatching {
            val response =
                api.openOnlineSession(
                    OpenOnlineSessionRequest(formCode = formCode, encryption = encryption)
                )
            sessionHolder.onlineSessionReferenceNumber = response.referenceNumber
            response.referenceNumber
        }

    suspend fun getSessionStatus(referenceNumber: String): Result<SessionStatusResponse> =
        runCatching {
            api.getSessionStatus(referenceNumber)
        }

    suspend fun getInvoices(
        dateFrom: String,
        dateTo: String,
        pageSize: Int = 10,
        pageOffset: Int = 0,
        subjectType: InvoiceSubjectType = InvoiceSubjectType.ISSUED,
    ): Result<InvoiceListResult> = runCatching {
        val filters =
            InvoiceQueryFilters(
                subjectType = subjectType.apiValue,
                dateRange =
                    InvoiceQueryDateRange(
                        dateType = "Invoicing",
                        from = "${dateFrom}T00:00:00Z",
                        to = "${dateTo}T23:59:59Z",
                    ),
            )
        val response = api.queryInvoiceMetadata(filters, pageOffset, pageSize)

        InvoiceListResult(
            items = response.invoices.map { it.toSummary() },
            totalCount = response.invoices.size,
            hasMore = response.hasMore,
        )
    }

    suspend fun getInvoice(ksefNumber: String): Result<String> = runCatching {
        api.getInvoice(ksefNumber)
    }

    fun isSessionActive(): Boolean = sessionHolder.isActive

    fun setEnvironmentBaseUrl(baseUrl: String) {
        api.baseUrl = baseUrl
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun sha256Base64(bytes: ByteArray): String {
        val digest = SHA256()
        digest.update(bytes)
        return Base64.encode(digest.digest())
    }

    private fun InvoiceMetadata.toSummary(): InvoiceSummary =
        InvoiceSummary(
            ksefReferenceNumber = ksefNumber,
            invoiceNumber = invoiceNumber,
            invoicingDate = invoicingDate,
            sellerNip = seller.nip ?: "",
            sellerName = seller.name ?: "",
            buyerNip = buyer.identifier?.value ?: buyer.nip ?: "",
            buyerName = buyer.name ?: "",
            net = netAmount.toString(),
            vat = vatAmount.toString(),
            gross = grossAmount.toString(),
        )

    companion object {
        private const val AUTH_STATUS_IN_PROGRESS = 100
        private const val AUTH_STATUS_SUCCESS = 200
        private const val MAX_AUTH_POLL_ATTEMPTS = 30
        private const val AUTH_POLL_DELAY_MS = 1_000L
    }
}

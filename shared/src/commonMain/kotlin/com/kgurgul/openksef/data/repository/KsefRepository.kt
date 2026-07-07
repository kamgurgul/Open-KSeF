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
import com.kgurgul.openksef.domain.money.Money
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
        val certificate =
            pickCertificate(api.getPublicKeyCertificates(), CERT_USAGE_KSEF_TOKEN_ENCRYPTION)
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

        sessionHolder.update(
            authReferenceNumber = initResponse.referenceNumber,
            accessToken = initResponse.authenticationToken.token,
        )

        val tokens = waitForRedeem(initResponse.referenceNumber)
        sessionHolder.update(
            accessToken = tokens.accessToken.token,
            refreshToken = tokens.refreshToken.token,
            nip = nip,
        )
        // The Auth plugin cached the temporary authentication token while polling auth status;
        // drop it so the permanent access token is sent on subsequent requests.
        api.clearTokenCache()

        SessionInfo(
            accessToken = tokens.accessToken.token,
            referenceNumber = initResponse.referenceNumber,
            nip = nip,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun pickCertificate(
        certs: List<PublicKeyCertificate>,
        usage: String,
    ): PublicKeyCertificate {
        val now = Clock.System.now()
        fun PublicKeyCertificate.hasUsage() = this.usage.any { it.equals(usage, ignoreCase = true) }
        val candidates = certs.filter { cert ->
            cert.hasUsage() &&
                runCatching {
                        Instant.parse(cert.validFrom) <= now && now <= Instant.parse(cert.validTo)
                    }
                    .getOrDefault(true)
        }
        return candidates.firstOrNull()
            ?: certs.firstOrNull { it.hasUsage() }
            ?: error("No $usage certificate returned by /security/public-key-certificates")
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
        api.clearTokenCache()
    }

    /**
     * Sends a single invoice through an online (interactive) session.
     *
     * The KSeF v2 online-session protocol encrypts the invoice symmetrically: a fresh AES-256 key
     * and IV are generated, the key is wrapped with the Ministry of Finance public key (RSA-OAEP)
     * to open the session, and the invoice XML is encrypted with AES-256-CBC before upload. A new
     * session is opened per send, so the caller never has to manage session lifecycle.
     *
     * The upload only returns 202 Accepted; processing (schema validation, duplicate check, KSeF
     * number assignment) is asynchronous, so the invoice status is polled until it reaches a
     * terminal state and a rejection surfaces as a failed [Result].
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun sendInvoice(invoiceXml: String): Result<SendInvoiceResult> = runCatching {
        val aesKey = crypto.secureRandomBytes(AES_KEY_SIZE_BYTES)
        val iv = crypto.secureRandomBytes(AES_IV_SIZE_BYTES)
        val sessionRef = openOnlineSession(aesKey, iv)

        val xmlBytes = invoiceXml.encodeToByteArray()
        val encryptedBytes = crypto.aesCbcEncrypt(xmlBytes, aesKey, iv)

        val response =
            api.sendInvoice(
                sessionReferenceNumber = sessionRef,
                request =
                    SendInvoiceRequest(
                        invoiceHash = sha256Base64(xmlBytes),
                        invoiceSize = xmlBytes.size.toLong(),
                        encryptedInvoiceHash = sha256Base64(encryptedBytes),
                        encryptedInvoiceSize = encryptedBytes.size.toLong(),
                        encryptedInvoiceContent = Base64.encode(encryptedBytes),
                    ),
            )

        val status = waitForInvoiceProcessing(sessionRef, response.referenceNumber)

        SendInvoiceResult(
            referenceNumber = response.referenceNumber,
            ksefNumber = status.ksefNumber,
        )
    }

    /**
     * Polls GET /sessions/{ref}/invoices/{invoiceRef} until KSeF finishes processing the invoice.
     * Status codes below 200 mean the invoice is still being processed, 200 means it was accepted
     * (and got a KSeF number); anything else is a rejection.
     */
    private suspend fun waitForInvoiceProcessing(
        sessionReferenceNumber: String,
        invoiceReferenceNumber: String,
    ): SessionInvoiceStatusResponse {
        repeat(MAX_INVOICE_POLL_ATTEMPTS) {
            val response =
                api.getSessionInvoiceStatus(sessionReferenceNumber, invoiceReferenceNumber)
            when {
                response.status.code == INVOICE_STATUS_SUCCESS -> return response
                response.status.code < INVOICE_STATUS_SUCCESS -> delay(INVOICE_POLL_DELAY_MS)
                else -> {
                    val details = response.status.details.orEmpty().joinToString(" ")
                    error(
                        "Invoice rejected: ${response.status.description}" +
                                if (details.isBlank()) "" else " $details"
                    )
                }
            }
        }
        error("Timed out waiting for invoice processing")
    }

    /**
     * Opens an online session for the FA(3) schema. [aesKey] is wrapped with the symmetric-key
     * encryption certificate from `/security/public-key-certificates`; [iv] is sent in the clear as
     * required by the spec. Returns the new session reference number (also stored on the session).
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun openOnlineSession(aesKey: ByteArray, iv: ByteArray): String {
        val certificate =
            pickCertificate(api.getPublicKeyCertificates(), CERT_USAGE_SYMMETRIC_KEY_ENCRYPTION)
        val certificateDer = Base64.decode(certificate.certificate)
        val encryptedSymmetricKey =
            Base64.encode(crypto.rsaOaepSha256Encrypt(aesKey, certificateDer))

        val response =
            api.openOnlineSession(
                OpenOnlineSessionRequest(
                    formCode = FA3_FORM_CODE,
                    encryption =
                        EncryptionInfo(
                            encryptedSymmetricKey = encryptedSymmetricKey,
                            initializationVector = Base64.encode(iv),
                        ),
                )
            )
        sessionHolder.update(onlineSessionReferenceNumber = response.referenceNumber)
        return response.referenceNumber
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
        sessionHolder.update(baseUrl = baseUrl)
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
            net = Money.fromDouble(netAmount),
            vat = Money.fromDouble(vatAmount),
            gross = Money.fromDouble(grossAmount),
        )

    companion object {
        private const val AUTH_STATUS_IN_PROGRESS = 100
        private const val AUTH_STATUS_SUCCESS = 200
        private const val MAX_AUTH_POLL_ATTEMPTS = 30
        private const val AUTH_POLL_DELAY_MS = 1_000L

        private const val INVOICE_STATUS_SUCCESS = 200
        private const val MAX_INVOICE_POLL_ATTEMPTS = 60
        private const val INVOICE_POLL_DELAY_MS = 1_000L

        private const val AES_KEY_SIZE_BYTES = 32
        private const val AES_IV_SIZE_BYTES = 16

        private const val CERT_USAGE_KSEF_TOKEN_ENCRYPTION = "KsefTokenEncryption"
        private const val CERT_USAGE_SYMMETRIC_KEY_ENCRYPTION = "SymmetricKeyEncryption"

        /** Form code for the FA(3) invoice schema produced by InvoiceBuilder. */
        private val FA3_FORM_CODE =
            FormCode(systemCode = "FA (3)", schemaVersion = "1-0E", value = "FA")
    }
}

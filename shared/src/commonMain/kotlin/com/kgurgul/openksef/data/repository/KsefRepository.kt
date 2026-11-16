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

import com.kgurgul.openksef.common.Constants.DEFAULT_CURRENCY_CODE
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefAuthenticator
import com.kgurgul.openksef.data.remote.KsefCrypto
import com.kgurgul.openksef.data.remote.model.*
import com.kgurgul.openksef.data.remote.pickCertificate
import com.kgurgul.openksef.domain.model.*
import com.kgurgul.openksef.domain.money.Money
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.kotlincrypto.hash.sha2.SHA256

class KsefRepository(
    private val api: KsefApi,
    private val sessionHolder: SessionHolder,
    private val crypto: KsefCrypto,
    private val authenticator: KsefAuthenticator,
) {

    /**
     * Runs the full token-based authentication flow (see [KsefAuthenticator]) and stores the
     * resulting tokens in the session.
     */
    suspend fun initSession(nip: String, ksefToken: String): Result<SessionInfo> = runCatching {
        val result =
            authenticator.authenticate(
                baseUrl = sessionHolder.baseUrl,
                nip = nip,
                ksefToken = ksefToken,
            )
        sessionHolder.update(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            authReferenceNumber = result.referenceNumber,
            nip = nip,
        )
        // The Auth plugin may still hold a token from a previous session; drop it so the new
        // access token is sent on subsequent requests.
        api.clearTokenCache()

        SessionInfo(
            accessToken = result.accessToken,
            referenceNumber = result.referenceNumber,
            nip = nip,
        )
    }

    suspend fun closeSession(): Result<Unit> = runCatching {
        sessionHolder.onlineSessionReferenceNumber?.let { closeOnlineSessionQuietly(it) }
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
     * terminal state and a rejection surfaces as a failed [Result]. Once the invoice reached a
     * terminal state the session is closed, which lets KSeF finish it and generate the UPO instead
     * of keeping it open until it expires.
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

        val status =
            try {
                waitForInvoiceProcessing(sessionRef, response.referenceNumber)
            } catch (e: InvoiceProcessingTimeoutException) {
                // Still being processed: leave the session open so the invoice is not cancelled.
                throw e
            } catch (e: Exception) {
                closeOnlineSessionQuietly(sessionRef)
                throw e
            }
        closeOnlineSessionQuietly(sessionRef)

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
                else -> throw InvoiceRejectedException(response.status)
            }
        }
        throw InvoiceProcessingTimeoutException()
    }

    /** Closes an online session, ignoring failures (it expires on its own anyway). */
    private suspend fun closeOnlineSessionQuietly(sessionReferenceNumber: String) {
        try {
            api.closeOnlineSession(sessionReferenceNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort only.
        }
        if (sessionHolder.onlineSessionReferenceNumber == sessionReferenceNumber) {
            sessionHolder.update(onlineSessionReferenceNumber = null)
        }
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
                            publicKeyId = certificate.publicKeyId,
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

    /**
     * Queries one page of invoice metadata, newest first.
     *
     * @param dateFrom first day (inclusive, `yyyy-MM-dd`) of the invoicing-date range
     * @param dateTo last day (inclusive, `yyyy-MM-dd`) of the invoicing-date range; the API accepts
     *   ranges of up to [MAX_DATE_RANGE_DAYS] days
     * @param pageIndex zero-based index of the page
     * @param timeZone zone in which [dateFrom] and [dateTo] are interpreted
     */
    suspend fun getInvoices(
        dateFrom: String,
        dateTo: String,
        pageSize: Int = KsefApi.MIN_PAGE_SIZE,
        pageIndex: Int = 0,
        subjectType: InvoiceSubjectType = InvoiceSubjectType.ISSUED,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Result<InvoiceListResult> = runCatching {
        // Day boundaries are taken in the given time zone and sent as UTC instants. Sending
        // "T00:00:00Z" directly would shift the range by the zone offset and drop the invoices
        // accepted around midnight.
        val from = LocalDate.parse(dateFrom).atStartOfDayIn(timeZone)
        val to =
            LocalDate.parse(dateTo).plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone) -
                    1.milliseconds
        val filters =
            InvoiceQueryFilters(
                subjectType = subjectType.apiValue,
                dateRange =
                    InvoiceQueryDateRange(
                        dateType = "Invoicing",
                        from = from.toString(),
                        to = to.toString(),
                    ),
            )
        val response = api.queryInvoiceMetadata(filters, pageIndex, pageSize)

        InvoiceListResult(
            items = response.invoices.map { it.toSummary() },
            hasMore = response.hasMore,
            isTruncated = response.isTruncated,
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
            buyerNip = buyer.identifier?.value ?: "",
            buyerName = buyer.name ?: "",
            net = Money.fromDouble(netAmount),
            vat = Money.fromDouble(vatAmount),
            gross = Money.fromDouble(grossAmount),
            issueDate = issueDate,
            currency = currency.ifBlank { DEFAULT_CURRENCY_CODE },
            kind = InvoiceKind.fromApiValue(invoiceType),
        )

    companion object {
        /** Longest date range accepted by POST /invoices/query/metadata. */
        const val MAX_DATE_RANGE_DAYS = 100

        private const val INVOICE_STATUS_SUCCESS = 200
        private const val MAX_INVOICE_POLL_ATTEMPTS = 60
        private const val INVOICE_POLL_DELAY_MS = 1_000L

        private const val AES_KEY_SIZE_BYTES = 32
        private const val AES_IV_SIZE_BYTES = 16

        private const val CERT_USAGE_SYMMETRIC_KEY_ENCRYPTION = "SymmetricKeyEncryption"

        /** Form code for the FA(3) invoice schema produced by InvoiceBuilder. */
        private val FA3_FORM_CODE =
            FormCode(systemCode = "FA (3)", schemaVersion = "1-0E", value = "FA")
    }
}

/**
 * KSeF finished processing a sent invoice with a status other than success (see the status table of
 * `GET /sessions/{referenceNumber}/invoices/{invoiceReferenceNumber}`), e.g. 440 - duplicate, 450 -
 * semantic validation error.
 *
 * @property originalKsefNumber for a duplicate (440): KSeF number of the invoice sent before
 */
class InvoiceRejectedException(
    val statusCode: Int,
    val description: String,
    val details: List<String> = emptyList(),
    val originalKsefNumber: String? = null,
) :
    Exception(
        buildString {
            append(description.ifBlank { "Invoice rejected ($statusCode)" })
            val detailsText = details.filter { it.isNotBlank() }.joinToString(separator = " ")
            if (detailsText.isNotEmpty()) append(": ").append(detailsText)
            if (originalKsefNumber != null) append(" (").append(originalKsefNumber).append(")")
        }
    ) {
    constructor(
        status: InvoiceStatusInfo
    ) : this(
        statusCode = status.code,
        description = status.description,
        details = status.details.orEmpty(),
        originalKsefNumber = status.extensions?.get("originalKsefNumber"),
    )
}

/** The invoice was accepted for processing but did not reach a terminal state in time. */
class InvoiceProcessingTimeoutException : Exception("Timed out waiting for invoice processing")

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

package com.kgurgul.openksef.data.remote.model

import kotlinx.serialization.Serializable

// Models mirror the KSeF API v2 OpenAPI specification
// (https://api.ksef.mf.gov.pl/docs/v2/index.html). Only the fields the app depends on are
// non-nullable; everything else is optional so that an environment running a slightly older or
// newer API build does not break deserialization.

// ---------- Public-key certificates ----------

@Serializable
data class PublicKeyCertificate(
    /** DER certificate, Base64-encoded. */
    val certificate: String,
    val certificateId: String? = null,
    /**
     * Key selector echoed back in requests to tell KSeF which public key was used for encryption.
     */
    val publicKeyId: String? = null,
    val validFrom: String,
    val validTo: String,
    /** `KsefTokenEncryption` and/or `SymmetricKeyEncryption`. */
    val usage: List<String> = emptyList(),
)

// ---------- Authentication: challenge ----------

@Serializable
data class AuthChallengeResponse(
    val challenge: String,
    val timestamp: String,
    val timestampMs: Long,
    val clientIp: String? = null,
)

// ---------- Authentication: ksef-token ----------

/** [type] is one of `Nip`, `InternalId`, `NipVatUe`, `PeppolId`. */
@Serializable
data class AuthenticationContextIdentifier(val type: String = "Nip", val value: String)

@Serializable
data class InitTokenAuthenticationRequest(
    val challenge: String,
    val contextIdentifier: AuthenticationContextIdentifier,
    val encryptedToken: String,
    val publicKeyId: String? = null,
    val authorizationPolicy: AuthorizationPolicy? = null,
)

@Serializable data class AuthorizationPolicy(val allowedIps: AllowedIps? = null)

@Serializable
data class AllowedIps(
    val ip4Addresses: List<String>? = null,
    val ip4Ranges: List<String>? = null,
    val ip4Masks: List<String>? = null,
)

@Serializable data class TokenInfo(val token: String, val validUntil: String)

@Serializable
data class AuthenticationInitResponse(
    val referenceNumber: String,
    val authenticationToken: TokenInfo,
)

@Serializable
data class AuthenticationMethodInfo(
    /** `XadesSignature`, `NationalNode`, `Token` or `Other`. */
    val category: String? = null,
    val code: String? = null,
    val displayName: String? = null,
)

@Serializable
data class AuthenticationOperationStatusResponse(
    val startDate: String? = null,
    /** Replaces the deprecated `authenticationMethod` field. */
    val authenticationMethodInfo: AuthenticationMethodInfo? = null,
    val status: StatusInfo,
    val isTokenRedeemed: Boolean? = null,
    val lastTokenRefreshDate: String? = null,
    val refreshTokenValidUntil: String? = null,
)

@Serializable
data class StatusInfo(
    val code: Int,
    val description: String = "",
    val details: List<String>? = null,
)

// ---------- Authentication: redeem / refresh ----------

@Serializable
data class AuthenticationTokensResponse(val accessToken: TokenInfo, val refreshToken: TokenInfo)

@Serializable data class AuthenticationTokenRefreshResponse(val accessToken: TokenInfo)

// ---------- Online session ----------

@Serializable
data class FormCode(val systemCode: String, val schemaVersion: String, val value: String)

@Serializable
data class EncryptionInfo(
    /** 32-byte AES key encrypted with RSA-OAEP (SHA-256), Base64-encoded. */
    val encryptedSymmetricKey: String,
    /** 16-byte initialization vector, Base64-encoded. */
    val initializationVector: String,
    val publicKeyId: String? = null,
)

@Serializable
data class OpenOnlineSessionRequest(val formCode: FormCode, val encryption: EncryptionInfo)

@Serializable
data class OpenOnlineSessionResponse(val referenceNumber: String, val validUntil: String? = null)

// ---------- Send invoice ----------

@Serializable
data class SendInvoiceRequest(
    val invoiceHash: String,
    val invoiceSize: Long,
    val encryptedInvoiceHash: String,
    val encryptedInvoiceSize: Long,
    val encryptedInvoiceContent: String,
    val offlineMode: Boolean = false,
    val hashOfCorrectedInvoice: String? = null,
)

@Serializable data class SendInvoiceResponse(val referenceNumber: String)

/** Invoice processing status; [extensions] carries case-specific data (e.g. duplicate origin). */
@Serializable
data class InvoiceStatusInfo(
    val code: Int,
    val description: String = "",
    val details: List<String>? = null,
    val extensions: Map<String, String?>? = null,
)

@Serializable
data class SessionInvoiceStatusResponse(
    val referenceNumber: String,
    val ordinalNumber: Int? = null,
    val invoiceNumber: String? = null,
    val ksefNumber: String? = null,
    val invoiceHash: String? = null,
    val invoiceFileName: String? = null,
    val invoicingDate: String? = null,
    val acquisitionDate: String? = null,
    val permanentStorageDate: String? = null,
    val upoDownloadUrl: String? = null,
    val upoDownloadUrlExpirationDate: String? = null,
    val invoicingMode: String? = null,
    val status: InvoiceStatusInfo,
)

// ---------- Session status ----------

@Serializable
data class UpoPageResponse(
    val referenceNumber: String,
    val downloadUrl: String,
    val downloadUrlExpirationDate: String? = null,
)

@Serializable data class UpoResponse(val pages: List<UpoPageResponse> = emptyList())

@Serializable
data class SessionStatusResponse(
    val status: StatusInfo,
    val dateCreated: String,
    val dateUpdated: String,
    val validUntil: String? = null,
    val upo: UpoResponse? = null,
    val invoiceCount: Int? = null,
    val successfulInvoiceCount: Int? = null,
    val failedInvoiceCount: Int? = null,
)

// ---------- Invoice query / metadata ----------

@Serializable
data class InvoiceQueryDateRange(
    /** `Issue`, `Invoicing` or `PermanentStorage`. */
    val dateType: String = "Invoicing",
    /** ISO-8601 date-time with `Z` or an explicit offset. The range may span at most 100 days. */
    val from: String,
    val to: String? = null,
    val restrictToPermanentStorageHwmDate: Boolean? = null,
)

/** [type] is one of `Brutto`, `Netto`, `Vat`. */
@Serializable
data class InvoiceQueryAmount(val type: String, val from: Double? = null, val to: Double? = null)

/** [type] is one of `Nip`, `VatUe`, `Other`, `None`. */
@Serializable data class InvoiceQueryBuyerIdentifier(val type: String, val value: String? = null)

@Serializable
data class InvoiceQueryFilters(
    /** `Subject1` (seller), `Subject2` (buyer), `Subject3` or `SubjectAuthorized`. */
    val subjectType: String = "Subject1",
    val dateRange: InvoiceQueryDateRange,
    val ksefNumber: String? = null,
    val invoiceNumber: String? = null,
    val amount: InvoiceQueryAmount? = null,
    val sellerNip: String? = null,
    val buyerIdentifier: InvoiceQueryBuyerIdentifier? = null,
    val currencyCodes: List<String>? = null,
    val invoicingMode: String? = null,
    val isSelfInvoicing: Boolean? = null,
    val formType: String? = null,
    val invoiceTypes: List<String>? = null,
    val hasAttachment: Boolean? = null,
)

/** Identifier of a buyer or a third subject; [type] is `Nip`, `VatUe`, `Other`, `None`, ... */
@Serializable data class InvoiceMetadataIdentifier(val type: String, val value: String? = null)

@Serializable data class InvoiceMetadataSeller(val nip: String? = null, val name: String? = null)

@Serializable
data class InvoiceMetadataBuyer(
    val identifier: InvoiceMetadataIdentifier? = null,
    val name: String? = null,
)

@Serializable
data class InvoiceMetadataThirdSubject(
    val identifier: InvoiceMetadataIdentifier? = null,
    val name: String? = null,
    val role: Int? = null,
)

@Serializable
data class InvoiceMetadataAuthorizedSubject(
    val nip: String? = null,
    val name: String? = null,
    val role: Int? = null,
)

@Serializable
data class InvoiceMetadata(
    val ksefNumber: String,
    val invoiceNumber: String = "",
    /** Issue date (`yyyy-MM-dd`). */
    val issueDate: String = "",
    /** Date-time the invoice was accepted by KSeF. */
    val invoicingDate: String = "",
    val acquisitionDate: String? = null,
    val permanentStorageDate: String? = null,
    val seller: InvoiceMetadataSeller = InvoiceMetadataSeller(),
    val buyer: InvoiceMetadataBuyer = InvoiceMetadataBuyer(),
    /** Total net amount in [currency]. */
    val netAmount: Double = 0.0,
    /** Total gross amount in [currency]. */
    val grossAmount: Double = 0.0,
    /** Total VAT amount, always expressed in PLN. */
    val vatAmount: Double = 0.0,
    val currency: String = "PLN",
    /** `Online` or `Offline`. */
    val invoicingMode: String? = null,
    /** `Vat`, `Zal`, `Kor`, `Roz`, `Upr`, `KorZal`, `KorRoz`, `VatPef`, `VatPefSp`, ... */
    val invoiceType: String? = null,
    val formCode: FormCode? = null,
    val isSelfInvoicing: Boolean = false,
    val hasAttachment: Boolean = false,
    val invoiceHash: String? = null,
    val hashOfCorrectedInvoice: String? = null,
    val thirdSubjects: List<InvoiceMetadataThirdSubject>? = null,
    val authorizedSubject: InvoiceMetadataAuthorizedSubject? = null,
)

@Serializable
data class QueryInvoicesMetadataResponse(
    val hasMore: Boolean = false,
    /** True when the 10 000 record limit for one filter set was reached. */
    val isTruncated: Boolean = false,
    val permanentStorageHwmDate: String? = null,
    val invoices: List<InvoiceMetadata> = emptyList(),
)

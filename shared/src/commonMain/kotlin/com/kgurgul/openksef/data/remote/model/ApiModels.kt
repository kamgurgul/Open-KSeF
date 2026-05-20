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

// ---------- Public-key certificates ----------

@Serializable
data class PublicKeyCertificate(
    val certificate: String,
    val validFrom: String,
    val validTo: String,
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

@Serializable
data class AuthenticationContextIdentifier(val type: String = "Nip", val value: String)

@Serializable
data class InitTokenAuthenticationRequest(
    val challenge: String,
    val contextIdentifier: AuthenticationContextIdentifier,
    val encryptedToken: String,
    val authorizationPolicy: AuthorizationPolicy? = null,
)

@Serializable data class AuthorizationPolicy(val allowedIps: List<String>? = null)

@Serializable data class TokenInfo(val token: String, val validUntil: String)

@Serializable
data class AuthenticationInitResponse(
    val referenceNumber: String,
    val authenticationToken: TokenInfo,
)

@Serializable
data class AuthenticationOperationStatusResponse(
    val startDate: String,
    val authenticationMethod: String? = null,
    val status: StatusInfo,
    val isTokenRedeemed: Boolean? = null,
    val refreshTokenValidUntil: String? = null,
)

@Serializable
data class StatusInfo(val code: Int, val description: String, val details: List<String>? = null)

// ---------- Authentication: redeem / refresh ----------

@Serializable
data class AuthenticationTokensResponse(val accessToken: TokenInfo, val refreshToken: TokenInfo)

@Serializable data class AuthenticationTokenRefreshResponse(val accessToken: TokenInfo)

// ---------- Online session ----------

@Serializable
data class FormCode(val systemCode: String, val schemaVersion: String, val value: String)

@Serializable
data class EncryptionInfo(val encryptedSymmetricKey: String, val initializationVector: String)

@Serializable
data class OpenOnlineSessionRequest(val formCode: FormCode, val encryption: EncryptionInfo)

@Serializable
data class OpenOnlineSessionResponse(val referenceNumber: String, val validUntil: String)

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

// ---------- Session status ----------

@Serializable
data class SessionStatusResponse(
    val status: StatusInfo,
    val dateCreated: String,
    val dateUpdated: String,
    val validUntil: String? = null,
    val invoiceCount: Int? = null,
    val successfulInvoiceCount: Int? = null,
    val failedInvoiceCount: Int? = null,
)

// ---------- Invoice query / metadata ----------

@Serializable
data class InvoiceQueryDateRange(
    val dateType: String = "Invoicing",
    val from: String,
    val to: String? = null,
    val restrictToPermanentStorageHwmDate: Boolean? = null,
)

@Serializable
data class InvoiceQueryAmount(val type: String, val from: Double? = null, val to: Double? = null)

@Serializable data class InvoiceQueryBuyerIdentifier(val type: String, val value: String? = null)

@Serializable
data class InvoiceQueryFilters(
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

@Serializable data class InvoiceMetadataIdentifier(val type: String, val value: String? = null)

@Serializable
data class InvoiceMetadataParty(
    val nip: String? = null,
    val identifier: InvoiceMetadataIdentifier? = null,
    val name: String? = null,
)

@Serializable
data class InvoiceMetadata(
    val ksefNumber: String,
    val invoiceNumber: String,
    val issueDate: String,
    val invoicingDate: String,
    val acquisitionDate: String,
    val permanentStorageDate: String,
    val seller: InvoiceMetadataParty,
    val buyer: InvoiceMetadataParty,
    val netAmount: Double,
    val grossAmount: Double,
    val vatAmount: Double,
    val currency: String,
    val invoicingMode: String,
    val invoiceType: String,
    val formCode: FormCode,
    val isSelfInvoicing: Boolean,
    val hasAttachment: Boolean,
    val invoiceHash: String,
    val hashOfCorrectedInvoice: String? = null,
)

@Serializable
data class QueryInvoicesMetadataResponse(
    val hasMore: Boolean = false,
    val isTruncated: Boolean = false,
    val permanentStorageHwmDate: String? = null,
    val invoices: List<InvoiceMetadata> = emptyList(),
)

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

package com.kgurgul.openksef.data.remote

import com.kgurgul.openksef.data.remote.model.*
import com.kgurgul.openksef.domain.model.KsefEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

class KsefApi(private val client: HttpClient) {
    var baseUrl: String = KsefEnvironment.TEST.baseUrl

    /**
     * GET /security/public-key-certificates — returns the certificates used to encrypt KSeF tokens
     * and AES keys.
     */
    suspend fun getPublicKeyCertificates(): List<PublicKeyCertificate> =
        client.get("$baseUrl/security/public-key-certificates").body()

    /** POST /auth/challenge — generates the challenge used as input to /auth/ksef-token. */
    suspend fun requestChallenge(): AuthChallengeResponse =
        client.post("$baseUrl/auth/challenge").body()

    /**
     * POST /auth/ksef-token — initialises an authentication operation using a KSeF token.
     * `encryptedToken` must be the user's KSeF token concatenated with the challenge timestamp
     * (`token|timestampMs`), encrypted with the Ministry of Finance public key (RSA-OAEP-SHA256)
     * and Base64-encoded. Returns 202 with a temporary `authenticationToken`.
     */
    suspend fun initTokenAuthentication(
        request: InitTokenAuthenticationRequest
    ): AuthenticationInitResponse =
        client.post("$baseUrl/auth/ksef-token") { setBody(request) }.body()

    /** GET /auth/{referenceNumber} — poll the authentication status. */
    suspend fun getAuthenticationStatus(
        referenceNumber: String
    ): AuthenticationOperationStatusResponse = client.get("$baseUrl/auth/$referenceNumber").body()

    /**
     * POST /auth/token/redeem — exchange the authentication token (sent as Bearer) for the
     * permanent access + refresh token pair.
     */
    suspend fun redeemAccessToken(authenticationToken: String): AuthenticationTokensResponse =
        client
            .post("$baseUrl/auth/token/redeem") {
                header(HttpHeaders.Authorization, "Bearer $authenticationToken")
            }
            .body()

    /**
     * POST /auth/token/refresh — refreshes a soon-to-expire access token using the refresh token.
     */
    suspend fun refreshAccessToken(refreshToken: String): AuthenticationTokenRefreshResponse =
        client
            .post("$baseUrl/auth/token/refresh") {
                header(HttpHeaders.Authorization, "Bearer $refreshToken")
            }
            .body()

    /** DELETE /auth/sessions/current — invalidates the current authentication session. */
    suspend fun logoutCurrentSession() {
        client.delete("$baseUrl/auth/sessions/current")
    }

    /**
     * Drops the bearer token cached by the Ktor `Auth` plugin so the next request re-reads the
     * current token from the session. The plugin caches whatever `loadTokens` returned on its first
     * call — during login that is the temporary authentication token — and never re-reads it
     * afterwards, so this must be called once the permanent access token has been obtained.
     */
    fun clearTokenCache() {
        client.authProviders.filterIsInstance<BearerAuthProvider>().forEach { it.clearToken() }
    }

    /** POST /sessions/online — opens an interactive (online) invoice-sending session. */
    suspend fun openOnlineSession(request: OpenOnlineSessionRequest): OpenOnlineSessionResponse =
        client.post("$baseUrl/sessions/online") { setBody(request) }.body()

    /**
     * POST /sessions/online/{referenceNumber}/invoices — send a single invoice in an online
     * session.
     */
    suspend fun sendInvoice(
        sessionReferenceNumber: String,
        request: SendInvoiceRequest,
    ): SendInvoiceResponse =
        client
            .post("$baseUrl/sessions/online/$sessionReferenceNumber/invoices") { setBody(request) }
            .body()

    /**
     * GET /sessions/{referenceNumber}/invoices/{invoiceReferenceNumber} — retrieves the processing
     * status of a single invoice sent in a session.
     */
    suspend fun getSessionInvoiceStatus(
        sessionReferenceNumber: String,
        invoiceReferenceNumber: String,
    ): SessionInvoiceStatusResponse =
        client
            .get("$baseUrl/sessions/$sessionReferenceNumber/invoices/$invoiceReferenceNumber")
            .body()

    /** POST /sessions/online/{referenceNumber}/close — closes an online session. Returns 204. */
    suspend fun closeOnlineSession(sessionReferenceNumber: String) {
        client.post("$baseUrl/sessions/online/$sessionReferenceNumber/close")
    }

    /** GET /sessions/{referenceNumber} — retrieves session status and counters. */
    suspend fun getSessionStatus(referenceNumber: String): SessionStatusResponse =
        client.get("$baseUrl/sessions/$referenceNumber").body()

    /** GET /invoices/ksef/{ksefNumber} — downloads the invoice XML by its KSeF number. */
    suspend fun getInvoice(ksefNumber: String): String =
        client.get("$baseUrl/invoices/ksef/$ksefNumber").bodyAsText()

    /** POST /invoices/query/metadata — searches invoice metadata. */
    suspend fun queryInvoiceMetadata(
        filters: InvoiceQueryFilters,
        pageOffset: Int = 0,
        pageSize: Int = 10,
        sortOrder: String = "Desc",
    ): QueryInvoicesMetadataResponse =
        client
            .post("$baseUrl/invoices/query/metadata") {
                parameter("pageOffset", pageOffset)
                parameter("pageSize", pageSize)
                parameter("sortOrder", sortOrder)
                setBody(filters)
            }
            .body()
}

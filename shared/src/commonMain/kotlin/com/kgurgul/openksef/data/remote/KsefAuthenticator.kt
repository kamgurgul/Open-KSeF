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

import com.kgurgul.openksef.data.remote.model.AuthChallengeResponse
import com.kgurgul.openksef.data.remote.model.AuthenticationContextIdentifier
import com.kgurgul.openksef.data.remote.model.AuthenticationInitResponse
import com.kgurgul.openksef.data.remote.model.AuthenticationOperationStatusResponse
import com.kgurgul.openksef.data.remote.model.AuthenticationTokensResponse
import com.kgurgul.openksef.data.remote.model.InitTokenAuthenticationRequest
import com.kgurgul.openksef.data.remote.model.PublicKeyCertificate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Runs the full token-based KSeF authentication flow: GET /security/public-key-certificates → POST
 * /auth/challenge → encrypt(`token|timestampMs`) → POST /auth/ksef-token → poll GET /auth/{ref} →
 * POST /auth/token/redeem.
 *
 * The KSeF API requires the token payload to be encrypted with the Ministry of Finance public key
 * (RSA-OAEP-SHA256). The encryption uses the certificate marked with usage `KsefTokenEncryption`
 * returned by `/security/public-key-certificates`.
 *
 * The [client] must be a plain HTTP client without the `Auth` plugin: every request in the flow is
 * either anonymous or explicitly authorised with the temporary authentication token, and keeping
 * the flow off the authenticated client lets it run inside that client's `refreshTokens` block
 * (automatic re-login) without recursing into the plugin's own refresh machinery.
 */
class KsefAuthenticator(private val client: HttpClient, private val crypto: KsefCrypto) {

    /** Tokens obtained from a completed authentication flow. */
    data class AuthenticationResult(
        val accessToken: String,
        val refreshToken: String,
        val referenceNumber: String,
    )

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun authenticate(
        baseUrl: String,
        nip: String,
        ksefToken: String,
    ): AuthenticationResult {
        val certificates: List<PublicKeyCertificate> =
            client.get("$baseUrl/security/public-key-certificates").body()
        val certificate = pickCertificate(certificates, CERT_USAGE_KSEF_TOKEN_ENCRYPTION)
        val challenge: AuthChallengeResponse = client.post("$baseUrl/auth/challenge").body()

        val payload = "$ksefToken|${challenge.timestampMs}".encodeToByteArray()
        val certificateDer = Base64.decode(certificate.certificate)
        val encryptedToken = Base64.encode(crypto.rsaOaepSha256Encrypt(payload, certificateDer))

        val initResponse: AuthenticationInitResponse =
            client
                .post("$baseUrl/auth/ksef-token") {
                    setBody(
                        InitTokenAuthenticationRequest(
                            challenge = challenge.challenge,
                            contextIdentifier =
                                AuthenticationContextIdentifier(type = "Nip", value = nip),
                            encryptedToken = encryptedToken,
                        )
                    )
                }
                .body()

        val tokens =
            waitForRedeem(
                baseUrl = baseUrl,
                referenceNumber = initResponse.referenceNumber,
                authenticationToken = initResponse.authenticationToken.token,
            )
        return AuthenticationResult(
            accessToken = tokens.accessToken.token,
            refreshToken = tokens.refreshToken.token,
            referenceNumber = initResponse.referenceNumber,
        )
    }

    private suspend fun waitForRedeem(
        baseUrl: String,
        referenceNumber: String,
        authenticationToken: String,
    ): AuthenticationTokensResponse {
        repeat(MAX_AUTH_POLL_ATTEMPTS) {
            val status: AuthenticationOperationStatusResponse =
                client
                    .get("$baseUrl/auth/$referenceNumber") {
                        header(HttpHeaders.Authorization, "Bearer $authenticationToken")
                    }
                    .body()
            when (status.status.code) {
                AUTH_STATUS_SUCCESS ->
                    return client
                        .post("$baseUrl/auth/token/redeem") {
                            header(HttpHeaders.Authorization, "Bearer $authenticationToken")
                        }
                        .body()

                AUTH_STATUS_IN_PROGRESS -> delay(AUTH_POLL_DELAY_MS)
                else ->
                    error(
                        "Authentication failed: ${status.status.code} ${status.status.description}"
                    )
            }
        }
        error("Timed out waiting for authentication to complete")
    }

    companion object {
        private const val AUTH_STATUS_IN_PROGRESS = 100
        private const val AUTH_STATUS_SUCCESS = 200
        private const val MAX_AUTH_POLL_ATTEMPTS = 30
        private const val AUTH_POLL_DELAY_MS = 1_000L

        private const val CERT_USAGE_KSEF_TOKEN_ENCRYPTION = "KsefTokenEncryption"

        /** Builds the authenticator on its own plain HTTP client (no `Auth` plugin). */
        fun create(json: Json, crypto: KsefCrypto): KsefAuthenticator =
            KsefAuthenticator(
                client =
                    HttpClient {
                        expectSuccess = true
                        install(ContentNegotiation) { json(json, contentType = ContentType.Any) }
                        install(Logging) { level = LogLevel.HEADERS }
                        defaultRequest { contentType(ContentType.Application.Json) }
                    },
                crypto = crypto,
            )
    }
}

/**
 * Picks a certificate with the given [usage] that is valid now, falling back to any certificate
 * with that usage when validity can't be established.
 */
@OptIn(ExperimentalTime::class)
internal fun pickCertificate(
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

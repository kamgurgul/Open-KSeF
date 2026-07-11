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

import com.kgurgul.openksef.data.SessionEventBus
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.local.TokenStore
import com.kgurgul.openksef.data.remote.model.AuthenticationTokenRefreshResponse
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

object KsefApiClient {

    fun create(
        sessionHolder: SessionHolder,
        json: Json,
        sessionEventBus: SessionEventBus,
        tokenStore: TokenStore,
        authenticator: KsefAuthenticator,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        val config: HttpClientConfig<*>.() -> Unit = {
            expectSuccess = true
            install(ContentNegotiation) { json(json, contentType = ContentType.Any) }
            install(Logging) { level = LogLevel.HEADERS }
            install(Auth) {
                bearer {
                    loadTokens {
                        val access = sessionHolder.accessToken ?: return@loadTokens null
                        BearerTokens(
                            accessToken = access,
                            refreshToken = sessionHolder.refreshToken ?: "",
                        )
                    }
                    refreshTokens {
                        val refreshToken = sessionHolder.refreshToken
                        if (refreshToken != null) {
                            try {
                                val response =
                                    client.post("${sessionHolder.baseUrl}/auth/token/refresh") {
                                        markAsRefreshTokenRequest()
                                        header(HttpHeaders.Authorization, "Bearer $refreshToken")
                                        contentType(ContentType.Application.Json)
                                    }
                                if (response.status.isSuccess()) {
                                    val tokens = response.body<AuthenticationTokenRefreshResponse>()
                                    sessionHolder.update(accessToken = tokens.accessToken.token)
                                    return@refreshTokens BearerTokens(
                                        accessToken = tokens.accessToken.token,
                                        refreshToken = refreshToken,
                                    )
                                }
                            } catch (_: Exception) {
                                // Fall through to full re-authentication below.
                            }
                        }
                        reauthenticate(sessionHolder, tokenStore, authenticator, sessionEventBus)
                    }
                }
            }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return if (engine != null) HttpClient(engine, config) else HttpClient(config)
    }

    /**
     * Runs the full authentication flow again with the credentials remembered at login (the user
     * ticked "remember"), so an expired refresh token signs the user back in transparently. Returns
     * fresh tokens, or null — after clearing the session and signalling expiry — when nothing is
     * remembered or the re-authentication itself fails.
     */
    private suspend fun reauthenticate(
        sessionHolder: SessionHolder,
        tokenStore: TokenStore,
        authenticator: KsefAuthenticator,
        sessionEventBus: SessionEventBus,
    ): BearerTokens? {
        val nip = tokenStore.getNip().first()
        val ksefToken = tokenStore.getToken()
        if (nip == null || ksefToken == null) {
            sessionHolder.clear()
            sessionEventBus.notifySessionExpired()
            return null
        }
        return try {
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
            BearerTokens(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
            )
        } catch (_: Exception) {
            sessionHolder.clear()
            sessionEventBus.notifySessionExpired()
            null
        }
    }
}

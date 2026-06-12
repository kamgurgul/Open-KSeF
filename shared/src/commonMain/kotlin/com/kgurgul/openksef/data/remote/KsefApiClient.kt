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
import com.kgurgul.openksef.data.remote.model.AuthenticationTokenRefreshResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.json.Json

object KsefApiClient {

    fun create(
        sessionHolder: SessionHolder,
        json: Json,
        sessionEventBus: SessionEventBus,
    ): HttpClient {
        return HttpClient {
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
                        if (refreshToken == null) {
                            sessionHolder.clear()
                            return@refreshTokens null
                        }
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
                                BearerTokens(
                                    accessToken = tokens.accessToken.token,
                                    refreshToken = refreshToken,
                                )
                            } else {
                                sessionHolder.clear()
                                sessionEventBus.notifySessionExpired()
                                null
                            }
                        } catch (_: Exception) {
                            sessionHolder.clear()
                            sessionEventBus.notifySessionExpired()
                            null
                        }
                    }
                }
            }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
    }
}

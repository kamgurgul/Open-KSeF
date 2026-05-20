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

import com.kgurgul.openksef.data.SessionHolder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KsefApiClient {

    fun create(sessionHolder: SessionHolder, json: Json): HttpClient {
        return HttpClient {
            expectSuccess = false
            install(ContentNegotiation) { json(json, contentType = ContentType.Any) }
            install(Logging) { level = LogLevel.HEADERS }
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        val body = runCatching { response.bodyAsText() }.getOrDefault("")
                        throw KsefApiException(
                            statusCode = response.status.value,
                            responseBody = body,
                            url = response.call.request.url.toString(),
                        )
                    }
                }
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                sessionHolder.accessToken?.let { token ->
                    if (headers[HttpHeaders.Authorization] == null) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        }
    }
}

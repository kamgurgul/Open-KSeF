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
            install(ContentNegotiation) {
                json(json, contentType = ContentType.Any)
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        val body = runCatching { response.bodyAsText() }.getOrDefault("")
                        throw KsefApiException(
                            statusCode = response.status.value,
                            responseBody = body,
                            url = response.call.request.url.toString()
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

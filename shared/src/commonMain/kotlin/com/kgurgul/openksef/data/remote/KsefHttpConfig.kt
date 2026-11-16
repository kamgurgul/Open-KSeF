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

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Header asking KSeF to report errors as RFC 7807 *Problem Details* instead of the old format. */
internal const val ERROR_FORMAT_HEADER = "X-Error-Format"
internal const val ERROR_FORMAT_PROBLEM_DETAILS = "problem-details"

/** A 429 is retried automatically only when KSeF asks to wait no longer than this. */
internal const val MAX_AUTO_RETRY_AFTER_SECONDS = 5
private const val MAX_RATE_LIMIT_RETRIES = 2
private const val DEFAULT_RETRY_DELAY_MS = 1_000L

/** `Retry-After` of a response in seconds, or null when the header is absent or not a number. */
internal fun HttpResponse.retryAfterSeconds(): Int? =
    headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()

/**
 * Configuration shared by every HTTP client talking to the KSeF API:
 * - JSON (de)serialization,
 * - errors requested in the *Problem Details* format and surfaced as [KsefApiException],
 * - short `429 Too Many Requests` waits (see `Retry-After`) retried transparently.
 */
internal fun HttpClientConfig<*>.installKsefDefaults(json: Json) {
    expectSuccess = true
    install(ContentNegotiation) { json(json, contentType = ContentType.Any) }
    install(Logging) { level = LogLevel.HEADERS }
    install(HttpRequestRetry) {
        retryOnExceptionIf { _, _ -> false }
        retryIf(maxRetries = MAX_RATE_LIMIT_RETRIES) { _, response ->
            response.status == HttpStatusCode.TooManyRequests &&
                    (response.retryAfterSeconds() ?: 1) <= MAX_AUTO_RETRY_AFTER_SECONDS
        }
        delayMillis(respectRetryAfterHeader = true) { DEFAULT_RETRY_DELAY_MS }
    }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, request ->
            if (cause is ResponseException) {
                val response = cause.response
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw KsefApiException.from(
                    statusCode = response.status.value,
                    body = body,
                    url = request.url.toString(),
                    retryAfterSeconds = response.retryAfterSeconds(),
                )
            }
        }
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
        header(ERROR_FORMAT_HEADER, ERROR_FORMAT_PROBLEM_DETAILS)
    }
}

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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Error returned by the KSeF API. The [message] is the human-readable description sent by KSeF (so
 * it can be shown to the user as is), falling back to the HTTP status when the body carries none.
 *
 * @property statusCode HTTP status code
 * @property errorCodes KSeF error codes (e.g. 21405 - input validation error)
 * @property reasonCode reason of a 403 response (e.g. `missing-permissions`)
 * @property retryAfterSeconds value of the `Retry-After` header sent with a 429 response
 */
class KsefApiException(
    val statusCode: Int,
    message: String,
    val errorCodes: List<Int> = emptyList(),
    val reasonCode: String? = null,
    val retryAfterSeconds: Int? = null,
    val responseBody: String = "",
    val url: String? = null,
) : Exception(message) {

    val isRateLimited: Boolean
        get() = statusCode == HTTP_TOO_MANY_REQUESTS

    companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Builds the exception from an error response. Understands the *Problem Details* format
         * (`application/problem+json`, requested with `X-Error-Format: problem-details`) as well as
         * the deprecated `ExceptionResponse` / `TooManyRequestsResponse` formats.
         */
        fun from(
            statusCode: Int,
            body: String,
            url: String? = null,
            retryAfterSeconds: Int? = null,
        ): KsefApiException {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            val errors = root?.let(::parseErrors).orEmpty()
            val message =
                errors
                    .map { it.text }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(separator = "\n")
                    .ifBlank { root?.string("detail").orEmpty() }
                    .ifBlank { root?.obj("status")?.let(::legacyStatusText).orEmpty() }
                    .ifBlank { root?.string("title").orEmpty() }
                    .ifBlank { "KSeF API HTTP $statusCode" }
            return KsefApiException(
                statusCode = statusCode,
                message = message,
                errorCodes = errors.mapNotNull { it.code },
                reasonCode = root?.string("reasonCode"),
                retryAfterSeconds = retryAfterSeconds,
                responseBody = body,
                url = url,
            )
        }

        private class ParsedError(val code: Int?, val text: String)

        private fun parseErrors(root: JsonObject): List<ParsedError> {
            // Problem Details: { "errors": [ { "code", "description", "details": [] } ] }
            root.array("errors")?.let { errors ->
                return errors.mapNotNull { (it as? JsonObject)?.toError("code", "description") }
            }
            // Deprecated: { "exception": { "exceptionDetailList": [ { "exceptionCode", ... } ] } }
            return root
                .obj("exception")
                ?.array("exceptionDetailList")
                ?.mapNotNull {
                    (it as? JsonObject)?.toError("exceptionCode", "exceptionDescription")
                }
                .orEmpty()
        }

        private fun JsonObject.toError(codeKey: String, descriptionKey: String): ParsedError {
            val description = string(descriptionKey).orEmpty().trim()
            val details = strings("details").joinToString(separator = " ")
            val text =
                when {
                    description.isEmpty() -> details
                    details.isEmpty() -> description
                    else -> "${description.trimEnd('.', ':')}: $details"
                }
            return ParsedError(code = (get(codeKey) as? JsonPrimitive)?.intOrNull, text = text)
        }

        /** Deprecated 429 body: { "status": { "code", "description", "details": [] } } */
        private fun legacyStatusText(status: JsonObject): String =
            status.strings("details").joinToString(separator = " ").ifBlank {
                status.string("description").orEmpty()
            }

        private fun JsonObject.string(key: String): String? =
            (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

        private fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray

        private fun JsonObject.strings(key: String): List<String> =
            array(key)
                ?.mapNotNull { element: JsonElement ->
                    (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
                }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
    }
}

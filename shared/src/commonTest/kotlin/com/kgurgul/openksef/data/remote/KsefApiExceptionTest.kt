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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KsefApiExceptionTest {

    @Test
    fun from_problemDetails_usesErrorDescriptionsAndCodes() {
        val body =
            """{
            "title": "Bad Request", "status": 400, "instance": "/v2/sessions/online",
            "detail": "Żądanie jest nieprawidłowe.",
            "errors": [
                {"code": 21405, "description": "Błąd walidacji danych wejściowych.",
                 "details": ["Wskazany kod formularza nie jest wspierany."]},
                {"code": 21157, "description": "Nieprawidłowy rozmiar części pakietu."}
            ],
            "timestamp": "2025-07-11T12:23:56.0154302+00:00", "traceId": "abc"
        }"""

        val exception = KsefApiException.from(statusCode = 400, body = body, url = "https://x/y")

        assertEquals(400, exception.statusCode)
        assertEquals(listOf(21405, 21157), exception.errorCodes)
        assertEquals(
            "Błąd walidacji danych wejściowych: Wskazany kod formularza nie jest wspierany.\n" +
                "Nieprawidłowy rozmiar części pakietu.",
            exception.message,
        )
        assertEquals("https://x/y", exception.url)
        assertFalse(exception.isRateLimited)
    }

    @Test
    fun from_forbiddenProblemDetails_usesDetailAndReasonCode() {
        val body =
            """{"title": "Forbidden", "status": 403,
            "detail": "Brak wymaganych uprawnień do wykonania operacji w bieżącym kontekście.",
            "reasonCode": "missing-permissions",
            "security": {"requiredAnyOfPermissions": ["InvoiceRead"], "presentPermissions": []}}"""

        val exception = KsefApiException.from(statusCode = 403, body = body)

        assertEquals(
            "Brak wymaganych uprawnień do wykonania operacji w bieżącym kontekście.",
            exception.message,
        )
        assertEquals("missing-permissions", exception.reasonCode)
        assertTrue(exception.errorCodes.isEmpty())
    }

    @Test
    fun from_legacyExceptionResponse_isStillUnderstood() {
        val body =
            """{"exception": {"exceptionDetailList": [
                {"exceptionCode": 21164,
                 "exceptionDescription": "Faktura o podanym identyfikatorze nie istnieje.",
                 "details": ["Faktura o numerze KSeF X nie została znaleziona."]}
            ], "serviceCode": "00-abc", "timestamp": "2025-10-11T12:23:56.0154302"}}"""

        val exception = KsefApiException.from(statusCode = 400, body = body)

        assertEquals(listOf(21164), exception.errorCodes)
        assertEquals(
            "Faktura o podanym identyfikatorze nie istnieje: " +
                "Faktura o numerze KSeF X nie została znaleziona.",
            exception.message,
        )
    }

    @Test
    fun from_rateLimitResponses_keepRetryAfter() {
        val problemDetails =
            """{"title": "Too Many Requests", "status": 429,
            "detail": "Przekroczono limit 20 żądań na minutę. Spróbuj ponownie po 30 sekundach."}"""
        val legacy =
            """{"status": {"code": 429, "description": "Too Many Requests",
            "details": ["Przekroczono limit 20 żądań na minutę."]}}"""

        val modern = KsefApiException.from(429, problemDetails, retryAfterSeconds = 30)
        val old = KsefApiException.from(429, legacy)

        assertTrue(modern.isRateLimited)
        assertEquals(30, modern.retryAfterSeconds)
        assertEquals(
            "Przekroczono limit 20 żądań na minutę. Spróbuj ponownie po 30 sekundach.",
            modern.message,
        )
        assertTrue(old.isRateLimited)
        assertNull(old.retryAfterSeconds)
        assertEquals("Przekroczono limit 20 żądań na minutę.", old.message)
    }

    @Test
    fun from_unparsableBody_fallsBackToStatus() {
        assertEquals("KSeF API HTTP 500", KsefApiException.from(500, "Server Error").message)
        assertEquals("KSeF API HTTP 502", KsefApiException.from(502, "").message)
        assertEquals("KSeF API HTTP 400", KsefApiException.from(400, "[1, 2]").message)
    }
}

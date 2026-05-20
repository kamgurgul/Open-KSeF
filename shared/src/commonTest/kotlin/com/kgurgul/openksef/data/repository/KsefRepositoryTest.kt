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

package com.kgurgul.openksef.data.repository

import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefCrypto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class KsefRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private object FakeCrypto : KsefCrypto {
        override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data
    }

    @Test
    fun initSession_happyPath_returnsSessionInfo() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/security/public-key-certificates") ->
                    respond(
                        content =
                            """[{"certificate":"AAAA","validFrom":"2024-01-01T00:00:00Z","validTo":"2099-01-01T00:00:00Z","usage":["KsefTokenEncryption"]}]""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                path.endsWith("/auth/challenge") ->
                    respond(
                        content =
                            """{"challenge":"abc123","timestamp":"2024-01-01T00:00:00Z","timestampMs":1704067200000,"clientIp":"127.0.0.1"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                path.endsWith("/auth/ksef-token") ->
                    respond(
                        content =
                            """{"referenceNumber":"ref-123","authenticationToken":{"token":"auth-token-xyz","validUntil":"2024-01-01T01:00:00Z"}}""",
                        status = HttpStatusCode.Accepted,
                        headers = jsonHeaders,
                    )

                path.endsWith("/auth/ref-123") ->
                    respond(
                        content =
                            """{"startDate":"2024-01-01T00:00:00Z","authenticationMethodInfo":{"category":"Token","code":"Token","displayName":"Token"},"status":{"code":200,"description":"OK"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                path.endsWith("/auth/token/redeem") ->
                    respond(
                        content =
                            """{"accessToken":{"token":"access-token-abc","validUntil":"2024-01-01T02:00:00Z"},"refreshToken":{"token":"refresh-token-def","validUntil":"2024-01-08T00:00:00Z"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                else ->
                    respond(content = "{}", status = HttpStatusCode.NotFound, headers = jsonHeaders)
            }
        }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
                defaultRequest { contentType(ContentType.Application.Json) }
                expectSuccess = false
            }

        val sessionHolder = SessionHolder()
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.initSession("1234567890", "encrypted-token")

        assertTrue(
            result.isSuccess,
            "Expected success but got: ${result.exceptionOrNull()?.message}",
        )
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals("access-token-abc", session.accessToken)
        assertEquals("ref-123", session.referenceNumber)
        assertEquals("1234567890", session.nip)
        assertEquals("access-token-abc", sessionHolder.accessToken)
        assertEquals("refresh-token-def", sessionHolder.refreshToken)
    }

    @Test
    fun initSession_challengeFailure_returnsError() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = "Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
                defaultRequest { contentType(ContentType.Application.Json) }
            }

        val sessionHolder = SessionHolder()
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.initSession("1234567890", "encrypted-token")

        assertTrue(result.isFailure)
    }

    @Test
    fun getInvoices_mapsResponseCorrectly() = runTest {
        val responseBody =
            """{
            "hasMore": false,
            "isTruncated": false,
            "invoices": [{
                "ksefNumber": "KSEF-REF-001",
                "invoiceNumber": "FV/2024/001",
                "issueDate": "2024-01-15",
                "invoicingDate": "2024-01-15T10:00:00Z",
                "acquisitionDate": "2024-01-15T10:00:01Z",
                "permanentStorageDate": "2024-01-15T10:00:02Z",
                "seller": {"nip": "1111111111", "name": "Seller Corp"},
                "buyer": {"identifier": {"type": "Nip", "value": "2222222222"}, "name": "Buyer Corp"},
                "netAmount": 1000.0,
                "grossAmount": 1230.0,
                "vatAmount": 230.0,
                "currency": "PLN",
                "invoicingMode": "Online",
                "invoiceType": "Vat",
                "formCode": {"systemCode": "FA (2)", "schemaVersion": "1-0E", "value": "FA"},
                "isSelfInvoicing": false,
                "hasAttachment": false,
                "invoiceHash": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
            }]
        }"""

        val engine = MockEngine { _ ->
            respond(content = responseBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
                defaultRequest { contentType(ContentType.Application.Json) }
                expectSuccess = false
            }

        val sessionHolder = SessionHolder()
        sessionHolder.accessToken = "active-token"
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.getInvoices("2024-01-01", "2024-01-31")

        assertTrue(
            result.isSuccess,
            "Expected success but got: ${result.exceptionOrNull()?.message}",
        )
        val invoiceList = result.getOrNull()
        assertNotNull(invoiceList)
        assertEquals(1, invoiceList.totalCount)
        assertEquals(1, invoiceList.items.size)

        val invoice = invoiceList.items.first()
        assertEquals("KSEF-REF-001", invoice.ksefReferenceNumber)
        assertEquals("FV/2024/001", invoice.invoiceNumber)
        assertEquals("1111111111", invoice.sellerNip)
        assertEquals("Seller Corp", invoice.sellerName)
        assertEquals("2222222222", invoice.buyerNip)
        assertEquals("1000.0", invoice.net)
        assertEquals("1230.0", invoice.gross)
    }

    @Test
    fun sendInvoice_returnsReferenceNumber() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"referenceNumber":"INV-REF-001"}""",
                status = HttpStatusCode.Accepted,
                headers = jsonHeaders,
            )
        }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
                defaultRequest { contentType(ContentType.Application.Json) }
                expectSuccess = false
            }

        val sessionHolder = SessionHolder()
        sessionHolder.accessToken = "active-token"
        sessionHolder.onlineSessionReferenceNumber = "session-ref"
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.sendInvoice("<Faktura>test</Faktura>")

        assertTrue(
            result.isSuccess,
            "Expected success but got: ${result.exceptionOrNull()?.message}",
        )
        val sendResult = result.getOrNull()
        assertNotNull(sendResult)
        assertEquals("INV-REF-001", sendResult.referenceNumber)
    }
}

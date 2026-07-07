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
import com.kgurgul.openksef.domain.money.Money
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
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

        override fun secureRandomBytes(size: Int) = ByteArray(size)

        override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray) = data
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Creates a test HttpClient that mimics KsefApiClient behaviour (Auth plugin + validator) using
     * a given [MockEngine].
     */
    private fun buildTestClient(engine: MockEngine, sessionHolder: SessionHolder): HttpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(json, contentType = ContentType.Any) }
            install(Auth) {
                bearer {
                    loadTokens {
                        val access = sessionHolder.accessToken ?: return@loadTokens null
                        BearerTokens(access, sessionHolder.refreshToken ?: "")
                    }
                    refreshTokens {
                        val refreshToken =
                            sessionHolder.refreshToken
                                ?: run {
                                    sessionHolder.clear()
                                    return@refreshTokens null
                                }
                        try {
                            val response =
                                client.post("${sessionHolder.baseUrl}/auth/token/refresh") {
                                    markAsRefreshTokenRequest()
                                }
                            if (response.status.isSuccess()) {
                                val body = response.bodyAsText()
                                // parse just the token out of JSON for test simplicity
                                val token =
                                    json
                                        .decodeFromString<kotlinx.serialization.json.JsonObject>(
                                            body
                                        )
                                        .let { it["accessToken"] }
                                        ?.let {
                                            json.decodeFromString<
                                                com.kgurgul.openksef.data.remote.model.TokenInfo
                                            >(
                                                it.toString()
                                            )
                                        }
                                if (token != null) {
                                    sessionHolder.update(accessToken = token.token)
                                    BearerTokens(token.token, refreshToken)
                                } else {
                                    sessionHolder.clear()
                                    null
                                }
                            } else {
                                sessionHolder.clear()
                                null
                            }
                        } catch (_: Exception) {
                            sessionHolder.clear()
                            null
                        }
                    }
                }
            }
            defaultRequest { contentType(ContentType.Application.Json) }
        }

    // ---------------------------------------------------------------------------
    // initSession
    // ---------------------------------------------------------------------------

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

        val sessionHolder = SessionHolder()
        val client = buildTestClient(engine, sessionHolder)
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
        val sessionHolder = SessionHolder()
        val client = buildTestClient(engine, sessionHolder)
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.initSession("1234567890", "encrypted-token")

        assertTrue(result.isFailure)
    }

    // ---------------------------------------------------------------------------
    // getInvoices
    // ---------------------------------------------------------------------------

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
        val sessionHolder = SessionHolder().apply { update(accessToken = "active-token") }
        val client = buildTestClient(engine, sessionHolder)
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
        val invoice = invoiceList.items.first()
        assertEquals("KSEF-REF-001", invoice.ksefReferenceNumber)
        assertEquals("1111111111", invoice.sellerNip)
        assertEquals(Money.fromMajorUnits(1000), invoice.net)
    }

    /**
     * MockEngine for the full send-invoice flow; [invoiceStatusResponses] are returned by the
     * status endpoint one by one (the last one repeats for any further polls).
     */
    private fun sendInvoiceEngine(
        vararg invoiceStatusResponses: String,
        onOpenSession: () -> Unit = {},
    ): MockEngine {
        var statusCallCount = 0
        return MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/security/public-key-certificates") ->
                    respond(
                        content =
                            """[{"certificate":"AAAA","validFrom":"2024-01-01T00:00:00Z","validTo":"2099-01-01T00:00:00Z","usage":["SymmetricKeyEncryption"]}]""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                path.endsWith("/sessions/online") -> {
                    onOpenSession()
                    respond(
                        content =
                            """{"referenceNumber":"session-ref","validUntil":"2099-01-01T00:00:00Z"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }

                path.endsWith("/sessions/online/session-ref/invoices") ->
                    respond(
                        content = """{"referenceNumber":"INV-REF-001"}""",
                        status = HttpStatusCode.Accepted,
                        headers = jsonHeaders,
                    )

                path.endsWith("/sessions/session-ref/invoices/INV-REF-001") -> {
                    val index = statusCallCount.coerceAtMost(invoiceStatusResponses.lastIndex)
                    statusCallCount++
                    respond(
                        content = invoiceStatusResponses[index],
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }

                else ->
                    respond(content = "{}", status = HttpStatusCode.NotFound, headers = jsonHeaders)
            }
        }
    }

    @Test
    fun sendInvoice_opensOnlineSessionThenReturnsKsefNumber() = runTest {
        var openSessionCalled = false
        val engine =
            sendInvoiceEngine(
                """{"referenceNumber":"INV-REF-001","ksefNumber":"1111111111-20240115-ABCDEF-12","status":{"code":200,"description":"Sukces"}}""",
                onOpenSession = { openSessionCalled = true },
            )
        val sessionHolder = SessionHolder().apply { update(accessToken = "active-token") }
        val client = buildTestClient(engine, sessionHolder)
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.sendInvoice("<Faktura>test</Faktura>")

        assertTrue(
            result.isSuccess,
            "Expected success but got: ${result.exceptionOrNull()?.message}",
        )
        assertTrue(openSessionCalled, "Expected an online session to be opened before sending")
        assertEquals("session-ref", sessionHolder.onlineSessionReferenceNumber)
        val sendResult = result.getOrNull()
        assertNotNull(sendResult)
        assertEquals("INV-REF-001", sendResult.referenceNumber)
        assertEquals("1111111111-20240115-ABCDEF-12", sendResult.ksefNumber)
    }

    @Test
    fun sendInvoice_pollsUntilProcessingFinishes() = runTest {
        val engine =
            sendInvoiceEngine(
                """{"referenceNumber":"INV-REF-001","status":{"code":100,"description":"Przetwarzanie"}}""",
                """{"referenceNumber":"INV-REF-001","status":{"code":100,"description":"Przetwarzanie"}}""",
                """{"referenceNumber":"INV-REF-001","ksefNumber":"1111111111-20240115-ABCDEF-12","status":{"code":200,"description":"Sukces"}}""",
            )
        val sessionHolder = SessionHolder().apply { update(accessToken = "active-token") }
        val client = buildTestClient(engine, sessionHolder)
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.sendInvoice("<Faktura>test</Faktura>")

        assertTrue(
            result.isSuccess,
            "Expected success but got: ${result.exceptionOrNull()?.message}",
        )
        assertEquals("1111111111-20240115-ABCDEF-12", result.getOrNull()?.ksefNumber)
    }

    @Test
    fun sendInvoice_rejectedInvoice_returnsErrorWithDescription() = runTest {
        val engine =
            sendInvoiceEngine(
                """{"referenceNumber":"INV-REF-001","status":{"code":440,"description":"Duplikat faktury","details":["Faktura o numerze KSeF: X została już przesłana"]}}"""
            )
        val sessionHolder = SessionHolder().apply { update(accessToken = "active-token") }
        val client = buildTestClient(engine, sessionHolder)
        val api = KsefApi(client)
        val repository = KsefRepository(api, sessionHolder, FakeCrypto)

        val result = repository.sendInvoice("<Faktura>test</Faktura>")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("Duplikat faktury"), "Unexpected message: $message")
    }
}

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

package com.kgurgul.openksef.ui.invoices

import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefCrypto
import com.kgurgul.openksef.data.repository.KsefRepository
import io.ktor.client.HttpClient
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    private val emptyListResponse = """{"hasMore": false, "isTruncated": false, "invoices": []}"""

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun initialState_hasDateRange() = runTest {
        val viewModel = createViewModel(emptyListResponse)
        val state = viewModel.uiState.first { it.dateFrom.isNotBlank() }
        assertTrue(state.dateFrom.isNotBlank())
        assertTrue(state.dateTo.isNotBlank())
    }

    @Test
    fun loadInvoices_success_updatesState() = runTest {
        val response =
            """{
            "hasMore": false, "isTruncated": false,
            "invoices": [${invoiceJson("KSEF-001", "FV/001", "Seller", "Buyer")}]
        }"""
        val viewModel = createViewModel(response)
        val state = viewModel.uiState.first { it.invoices.isNotEmpty() }
        assertEquals(1, state.invoices.size)
        assertEquals("KSEF-001", state.invoices.first().ksefReferenceNumber)
    }

    @Test
    fun loadInvoices_emptyList_showsEmpty() = runTest {
        val viewModel = createViewModel(emptyListResponse)
        val state = viewModel.uiState.first { it.dateFrom.isNotBlank() && !it.isLoading }
        assertTrue(state.invoices.isEmpty())
        assertEquals(0, state.totalCount)
    }

    @Test
    fun onSearchQueryChanged_filtersDisplayedInvoices() = runTest {
        val response =
            """{
            "hasMore": false, "isTruncated": false,
            "invoices": [
                ${invoiceJson("KSEF-001", "FV/001", "Alpha", "Beta")},
                ${invoiceJson("KSEF-002", "FV/002", "Gamma", "Delta")}
            ]
        }"""
        val viewModel = createViewModel(response)
        viewModel.uiState.first { it.invoices.size == 2 }

        viewModel.onSearchQueryChanged("Alpha")
        val filtered = viewModel.uiState.first { it.searchQuery == "Alpha" }
        assertEquals(1, filtered.displayedInvoices.size)
        assertEquals("KSEF-001", filtered.displayedInvoices.first().ksefReferenceNumber)

        viewModel.onSearchQueryChanged("")
        val cleared = viewModel.uiState.first { it.searchQuery.isEmpty() }
        assertEquals(2, cleared.displayedInvoices.size)
    }

    @Test
    fun onDateRangeChanged_updatesState() = runTest {
        val viewModel = createViewModel(emptyListResponse)
        viewModel.uiState.first { it.dateFrom.isNotBlank() }
        viewModel.onDateFromChanged("2024-01-01")
        viewModel.onDateToChanged("2024-12-31")
        val state =
            viewModel.uiState.first { it.dateFrom == "2024-01-01" && it.dateTo == "2024-12-31" }
        assertEquals("2024-01-01", state.dateFrom)
        assertEquals("2024-12-31", state.dateTo)
    }

    /**
     * 401 with no refresh token → Auth plugin returns null → SessionExpiredException. The redirect
     * is now handled globally by MainViewModel, so the list VM only surfaces a generic error.
     */
    @Test
    fun loadInvoices_on401_noRefreshToken_emitsShowError() = runTest {
        val viewModel =
            createViewModel(
                responseBody = "Unauthorized",
                responseStatus = HttpStatusCode.Unauthorized,
                refreshToken = null,
            )
        backgroundScope.launch { viewModel.uiState.collect {} }
        val event = viewModel.events.first()
        assertTrue(event is InvoiceListEvent.ShowError)
    }

    /** 401 → Auth plugin refreshes → retries → success, no SessionEnded event. */
    @Test
    fun loadInvoices_on401_withRefreshToken_authPluginRefreshesAndSucceeds() = runTest {
        var invoiceCallCount = 0
        val viewModel =
            createViewModelWithRouter(refreshToken = "valid-refresh") { path ->
                when {
                    path.endsWith("/invoices/query/metadata") -> {
                        invoiceCallCount++
                        if (invoiceCallCount == 1) Pair(HttpStatusCode.Unauthorized, "Unauthorized")
                        else Pair(HttpStatusCode.OK, emptyListResponse)
                    }

                    path.endsWith("/auth/token/refresh") ->
                        Pair(
                            HttpStatusCode.OK,
                            """{"accessToken":{"token":"new-token","validUntil":"2099-01-01T00:00:00Z"}}""",
                        )

                    else -> Pair(HttpStatusCode.NotFound, "{}")
                }
            }

        // Wait past the stateIn placeholder (blank dateFrom) for the real post-load state.
        val state = viewModel.uiState.first { it.dateFrom.isNotBlank() && !it.isLoading }
        assertTrue(state.invoices.isEmpty())
        assertEquals(2, invoiceCallCount)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun invoiceJson(
        ksefNumber: String,
        invoiceNumber: String,
        sellerName: String,
        buyerName: String,
    ) =
        """{
        "ksefNumber":"$ksefNumber","invoiceNumber":"$invoiceNumber",
        "issueDate":"2024-01-15","invoicingDate":"2024-01-15T00:00:00Z",
        "acquisitionDate":"2024-01-15T00:00:00Z","permanentStorageDate":"2024-01-15T00:00:00Z",
        "seller":{"nip":"1111111111","name":"$sellerName"},
        "buyer":{"identifier":{"type":"Nip","value":"2222222222"},"name":"$buyerName"},
        "netAmount":100.0,"grossAmount":123.0,"vatAmount":23.0,"currency":"PLN",
        "invoicingMode":"Online","invoiceType":"Vat",
        "formCode":{"systemCode":"FA (2)","schemaVersion":"1-0E","value":"FA"},
        "isSelfInvoicing":false,"hasAttachment":false,
        "invoiceHash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }"""

    private fun createViewModel(
        responseBody: String,
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        refreshToken: String? = null,
    ): InvoiceListViewModel {
        val contentType =
            if (responseStatus.isSuccess()) ContentType.Application.Json.toString()
            else ContentType.Text.Plain.toString()
        val engine = MockEngine { _ ->
            respond(responseBody, responseStatus, headersOf(HttpHeaders.ContentType, contentType))
        }
        return buildViewModel(engine, refreshToken)
    }

    private fun createViewModelWithRouter(
        refreshToken: String?,
        router: (String) -> Pair<HttpStatusCode, String>,
    ): InvoiceListViewModel {
        val engine = MockEngine { request ->
            val (status, body) = router(request.url.encodedPath)
            val ct =
                if (status.isSuccess()) ContentType.Application.Json.toString()
                else ContentType.Text.Plain.toString()
            respond(body, status, headersOf(HttpHeaders.ContentType, ct))
        }
        return buildViewModel(engine, refreshToken)
    }

    private fun buildViewModel(engine: MockEngine, refreshToken: String?): InvoiceListViewModel {
        val sessionHolder =
            SessionHolder().apply {
                update(accessToken = "test-token", refreshToken = refreshToken)
            }
        val client = buildTestClient(engine, sessionHolder)
        val crypto =
            object : KsefCrypto {
                override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data

                override fun secureRandomBytes(size: Int) = ByteArray(size)

                override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray) = data
            }
        val repository = KsefRepository(KsefApi(client), sessionHolder, crypto)
        return InvoiceListViewModel(repository)
    }

    /** Mirrors the Auth-plugin + validator setup of KsefApiClient. */
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
                        val rt =
                            sessionHolder.refreshToken
                                ?: run {
                                    sessionHolder.clear()
                                    return@refreshTokens null
                                }
                        try {
                            val resp =
                                client.post("${sessionHolder.baseUrl}/auth/token/refresh") {
                                    markAsRefreshTokenRequest()
                                }
                            if (resp.status.isSuccess()) {
                                val obj =
                                    json.decodeFromString<kotlinx.serialization.json.JsonObject>(
                                        resp.bodyAsText()
                                    )
                                val newToken =
                                    obj["accessToken"]?.let {
                                        json.decodeFromString<
                                            com.kgurgul.openksef.data.remote.model.TokenInfo
                                        >(
                                            it.toString()
                                        )
                                    }
                                if (newToken != null) {
                                    sessionHolder.update(accessToken = newToken.token)
                                    BearerTokens(newToken.token, rt)
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
}

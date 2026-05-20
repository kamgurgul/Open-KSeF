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
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class InvoiceListViewModelTest {

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
            "hasMore": false,
            "isTruncated": false,
            "invoices": [{
                "ksefNumber": "KSEF-001",
                "invoiceNumber": "FV/001",
                "issueDate": "2024-01-15",
                "invoicingDate": "2024-01-15T00:00:00Z",
                "acquisitionDate": "2024-01-15T00:00:00Z",
                "permanentStorageDate": "2024-01-15T00:00:00Z",
                "seller": {"nip": "1111111111", "name": "Seller"},
                "buyer": {"identifier": {"type": "Nip", "value": "2222222222"}, "name": "Buyer"},
                "netAmount": 100.0,
                "grossAmount": 123.0,
                "vatAmount": 23.0,
                "currency": "PLN",
                "invoicingMode": "Online",
                "invoiceType": "Vat",
                "formCode": {"systemCode": "FA (2)", "schemaVersion": "1-0E", "value": "FA"},
                "isSelfInvoicing": false,
                "hasAttachment": false,
                "invoiceHash": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
            }]
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
            "hasMore": false,
            "isTruncated": false,
            "invoices": [
                ${invoiceJson("KSEF-001", "FV/001", "Alpha", "Beta")},
                ${invoiceJson("KSEF-002", "FV/002", "Gamma", "Delta")}
            ]
        }"""

        val viewModel = createViewModel(response)
        viewModel.uiState.first { it.invoices.size == 2 }

        viewModel.onSearchQueryChanged("Alpha")
        val filtered = viewModel.uiState.first { it.searchQuery == "Alpha" }

        assertEquals(2, filtered.invoices.size)
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

    private val emptyListResponse = """{"hasMore": false, "isTruncated": false, "invoices": []}"""

    private fun invoiceJson(
        ksefNumber: String,
        invoiceNumber: String,
        sellerName: String,
        buyerName: String,
    ): String =
        """{
        "ksefNumber": "$ksefNumber",
        "invoiceNumber": "$invoiceNumber",
        "issueDate": "2024-01-15",
        "invoicingDate": "2024-01-15T00:00:00Z",
        "acquisitionDate": "2024-01-15T00:00:00Z",
        "permanentStorageDate": "2024-01-15T00:00:00Z",
        "seller": {"nip": "1111111111", "name": "$sellerName"},
        "buyer": {"identifier": {"type": "Nip", "value": "2222222222"}, "name": "$buyerName"},
        "netAmount": 100.0,
        "grossAmount": 123.0,
        "vatAmount": 23.0,
        "currency": "PLN",
        "invoicingMode": "Online",
        "invoiceType": "Vat",
        "formCode": {"systemCode": "FA (2)", "schemaVersion": "1-0E", "value": "FA"},
        "isSelfInvoicing": false,
        "hasAttachment": false,
        "invoiceHash": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }"""

    private fun createViewModel(responseBody: String): InvoiceListViewModel {
        val engine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers =
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val mockClient =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                            encodeDefaults = true
                        }
                    )
                }
                defaultRequest { contentType(ContentType.Application.Json) }
                expectSuccess = false
            }

        val sessionHolder = SessionHolder()
        sessionHolder.accessToken = "test-token"
        val api = KsefApi(mockClient)
        val crypto =
            object : KsefCrypto {
                override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data
            }
        val repository = KsefRepository(api, sessionHolder, crypto)

        return InvoiceListViewModel(repository)
    }
}

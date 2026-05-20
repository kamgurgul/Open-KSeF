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

package com.kgurgul.openksef.ui.sendinvoice

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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class SendInvoiceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_prefillsSellerNip() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("9999999999", viewModel.uiState.value.sellerNip)
    }

    @Test
    fun addItem_increasesItemCount() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.items.size)

        viewModel.addItem()

        assertEquals(2, viewModel.uiState.value.items.size)
    }

    @Test
    fun removeItem_decreasesItemCount() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addItem()
        assertEquals(2, viewModel.uiState.value.items.size)

        viewModel.removeItem(0)
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun removeItem_cannotRemoveLastItem() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.items.size)

        viewModel.removeItem(0)
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun send_emptyFields_setsValidationErrors() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.uiState.value.validationErrors
        assertTrue(errors.containsKey("sellerName"))
        assertTrue(errors.containsKey("buyerNip"))
        assertTrue(errors.containsKey("buyerName"))
        assertTrue(errors.containsKey("invoiceNumber"))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun updateItem_calculatesNetAndGross() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val item =
            InvoiceLineItemUi(description = "Test", quantity = "2", unitPrice = "100", vatRate = 23)
        viewModel.updateItem(0, item)

        val updated = viewModel.uiState.value.items[0]
        assertEquals("200.00", updated.netValue)
        assertEquals("246.00", updated.grossValue)
    }

    private fun createViewModel(): SendInvoiceViewModel {
        val engine = MockEngine { _ ->
            respond(
                content = """{"referenceNumber":"R-001"}""",
                status = HttpStatusCode.Accepted,
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
        sessionHolder.onlineSessionReferenceNumber = "session-ref"
        sessionHolder.nip = "9999999999"
        val api = KsefApi(mockClient)
        val crypto =
            object : KsefCrypto {
                override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data
            }
        val repository = KsefRepository(api, sessionHolder, crypto)

        return SendInvoiceViewModel(repository, sessionHolder)
    }
}

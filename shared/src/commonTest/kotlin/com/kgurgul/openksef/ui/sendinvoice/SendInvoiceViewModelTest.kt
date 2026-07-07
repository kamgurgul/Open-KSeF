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

import com.kgurgul.openksef.common.TestDispatchersProvider
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefCrypto
import com.kgurgul.openksef.data.repository.InvoiceTemplateRepository
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.invoice.InvoiceTemplate
import com.kgurgul.openksef.domain.invoice.SellerConfig
import com.kgurgul.openksef.domain.money.Money
import com.kgurgul.openksef.domain.observable.InvoiceTemplatesObservable
import com.kgurgul.openksef.domain.observable.SellerConfigObservable
import com.kgurgul.openksef.domain.result.DeleteInvoiceTemplateInteractor
import com.kgurgul.openksef.domain.result.GetSessionNipInteractor
import com.kgurgul.openksef.domain.result.SaveInvoiceTemplateInteractor
import com.kgurgul.openksef.domain.result.SendInvoiceInteractor
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
        collectUiState(viewModel)

        assertEquals("9999999999", viewModel.uiState.value.sellerNip)
    }

    @Test
    fun initialState_prefillsSellerConfig() = runTest {
        val viewModel =
            createViewModel(SellerConfig(name = "ACME Sp. z o.o.", address = "ul. Testowa 1"))
        collectUiState(viewModel)

        assertEquals("ACME Sp. z o.o.", viewModel.uiState.value.sellerName)
        assertEquals("ul. Testowa 1", viewModel.uiState.value.sellerAddress)
    }

    @Test
    fun addItem_increasesItemCount() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        assertEquals(1, viewModel.uiState.value.items.size)

        viewModel.addItem()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.items.size)
    }

    @Test
    fun removeItem_decreasesItemCount() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        viewModel.addItem()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.items.size)

        viewModel.removeItem(0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun removeItem_cannotRemoveLastItem() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        assertEquals(1, viewModel.uiState.value.items.size)

        viewModel.removeItem(0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun send_emptyFields_setsValidationErrors() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

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
    fun send_success_setsSentStateWithKsefNumber() = runTest {
        val viewModel =
            createViewModel(SellerConfig(name = "ACME Sp. z o.o.", address = "ul. Testowa 1"))
        collectUiState(viewModel)

        viewModel.onBuyerNipChanged("2222222222")
        viewModel.onBuyerNameChanged("Firma XYZ")
        viewModel.onInvoiceNumberChanged("FV/2024/001")
        viewModel.updateItem(
            0,
            InvoiceLineItemUi(description = "Usługa", quantity = "1", unitPrice = "100"),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        val state = viewModel.uiState.first { it.isSent || it.error != null }

        assertTrue(state.isSent, "Expected isSent, error: ${state.error}")
        assertEquals("R-001", state.sentReferenceNumber)
        assertEquals("KSEF-NUM-001", state.sentKsefNumber)
        assertFalse(state.isLoading)
    }

    @Test
    fun updateItem_calculatesNetAndGross() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        val item =
            InvoiceLineItemUi(description = "Test", quantity = "2", unitPrice = "100", vatRate = 23)
        viewModel.updateItem(0, item)
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = viewModel.uiState.value.items[0]
        assertEquals(Money.fromMajorUnits(200), updated.netValue)
        assertEquals(Money.fromMajorUnits(246), updated.grossValue)
    }

    @Test
    fun initialState_hasNoTemplates() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        assertTrue(viewModel.uiState.value.templates.isEmpty())
        assertEquals(null, viewModel.uiState.value.selectedTemplateId)
    }

    @Test
    fun onSaveTemplate_persistsCurrentForm() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        viewModel.onBuyerNipChanged("2222222222")
        viewModel.onBuyerNameChanged("Firma XYZ")
        viewModel.updateItem(
            0,
            InvoiceLineItemUi(
                description = "Usługa",
                quantity = "2",
                unit = "godz.",
                unitPrice = "100",
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onSaveTemplate("My template")
        testDispatcher.scheduler.advanceUntilIdle()

        val templates = viewModel.uiState.value.templates
        assertEquals(1, templates.size)
        val template = templates[0]
        assertEquals("My template", template.name)
        assertEquals("2222222222", template.buyerNip)
        assertEquals("Usługa", template.items[0].description)
        assertEquals("godz.", template.items[0].unit)
    }

    @Test
    fun onSaveTemplate_blankName_doesNothing() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        viewModel.onSaveTemplate("  ")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.templates.isEmpty())
    }

    @Test
    fun onTemplateSelected_prefillsItemsAndCalculatesValues() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        viewModel.updateItem(
            0,
            InvoiceLineItemUi(
                description = "Usługa programistyczna",
                quantity = "8",
                unit = "godz.",
                unitPrice = "150",
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onSaveTemplate("Software")
        testDispatcher.scheduler.advanceUntilIdle()

        // Reset the form, then re-apply the saved template.
        viewModel.updateItem(0, InvoiceLineItemUi())
        testDispatcher.scheduler.advanceUntilIdle()
        val template = viewModel.uiState.value.templates.first { it.name == "Software" }
        viewModel.onTemplateSelected(template)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(template.id, state.selectedTemplateId)
        assertEquals(1, state.items.size)
        val item = state.items[0]
        assertEquals("Usługa programistyczna", item.description)
        assertEquals("godz.", item.unit)
        assertEquals("8", item.quantity)
        assertEquals("150", item.unitPrice)
        // 8 * 150 = 1200 net, gross = 1200 * 1.23 = 1476
        assertEquals(Money.fromMajorUnits(1200), item.netValue)
        assertEquals(Money.fromMajorUnits(1476), item.grossValue)
    }

    @Test
    fun onDeleteTemplate_removesTemplate() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        viewModel.onSaveTemplate("Temp")
        testDispatcher.scheduler.advanceUntilIdle()
        val template = viewModel.uiState.value.templates.first()

        viewModel.onDeleteTemplate(template.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.templates.isEmpty())
    }

    @Test
    fun updateItem_keepsUnit() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        val item =
            InvoiceLineItemUi(description = "Test", quantity = "2", unit = "kg", unitPrice = "100")
        viewModel.updateItem(0, item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("kg", viewModel.uiState.value.items[0].unit)
    }

    /** Keeps [SendInvoiceViewModel.uiState] hot for `value` reads and runs pending init work. */
    private fun TestScope.collectUiState(viewModel: SendInvoiceViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(sellerConfig: SellerConfig? = null): SendInvoiceViewModel {
        val jsonHeaders =
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/security/public-key-certificates") ->
                    respond(
                        content =
                            """[{"certificate":"AAAA","validFrom":"2024-01-01T00:00:00Z","validTo":"2099-01-01T00:00:00Z","usage":["SymmetricKeyEncryption"]}]""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                path.endsWith("/sessions/online") ->
                    respond(
                        content =
                            """{"referenceNumber":"session-ref","validUntil":"2099-01-01T00:00:00Z"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                path.endsWith("/sessions/online/session-ref/invoices") ->
                    respond(
                        content = """{"referenceNumber":"R-001"}""",
                        status = HttpStatusCode.Accepted,
                        headers = jsonHeaders,
                    )

                path.endsWith("/sessions/session-ref/invoices/R-001") ->
                    respond(
                        content =
                            """{"referenceNumber":"R-001","ksefNumber":"KSEF-NUM-001","status":{"code":200,"description":"Sukces"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                else -> respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound,
                    headers = jsonHeaders
                )
            }
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
        sessionHolder.update(
            accessToken = "test-token",
            onlineSessionReferenceNumber = "session-ref",
            nip = "9999999999",
        )
        val api = KsefApi(mockClient)
        val crypto =
            object : KsefCrypto {
                override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data

                override fun secureRandomBytes(size: Int) = ByteArray(size)

                override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray) = data
            }
        val repository = KsefRepository(api, sessionHolder, crypto)

        val dispatchers = TestDispatchersProvider(testDispatcher)
        val templateRepository = FakeInvoiceTemplateRepository()
        val sellerConfigRepository = FakeSellerConfigRepository(sellerConfig)
        return SendInvoiceViewModel(
            sendInvoiceInteractor = SendInvoiceInteractor(dispatchers, repository),
            getSessionNipInteractor = GetSessionNipInteractor(dispatchers, sessionHolder),
            invoiceTemplatesObservable =
                InvoiceTemplatesObservable(dispatchers, templateRepository),
            sellerConfigObservable = SellerConfigObservable(dispatchers, sellerConfigRepository),
            saveInvoiceTemplateInteractor =
                SaveInvoiceTemplateInteractor(dispatchers, templateRepository),
            deleteInvoiceTemplateInteractor =
                DeleteInvoiceTemplateInteractor(dispatchers, templateRepository),
        )
    }

    private class FakeInvoiceTemplateRepository : InvoiceTemplateRepository {
        private val state = MutableStateFlow<List<InvoiceTemplate>>(emptyList())
        override val templates: Flow<List<InvoiceTemplate>> = state

        override suspend fun save(template: InvoiceTemplate) {
            state.value = state.value.filterNot { it.id == template.id } + template
        }

        override suspend fun delete(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeSellerConfigRepository(initial: SellerConfig? = null) :
        SellerConfigRepository {
        private val state = MutableStateFlow(initial)
        override val config: Flow<SellerConfig?> = state

        override suspend fun save(config: SellerConfig) {
            state.value = config
        }
    }
}

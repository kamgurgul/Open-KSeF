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
import com.kgurgul.openksef.data.remote.KsefAuthenticator
import com.kgurgul.openksef.data.remote.KsefCrypto
import com.kgurgul.openksef.data.repository.InvoiceTemplateRepository
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.invoice.BuyerIdType
import com.kgurgul.openksef.domain.invoice.InvoiceTemplate
import com.kgurgul.openksef.domain.invoice.PaymentMethod
import com.kgurgul.openksef.domain.invoice.SellerConfig
import com.kgurgul.openksef.domain.invoice.VatRate
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
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class SendInvoiceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** The invoice XML the view model sent to the mocked KSeF API, if any. */
    private var sentXml: String? = null

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
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun send_invalidIssueDate_setsValidationErrorAndSkipsSending() = runTest {
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
        viewModel.onIssueDateChanged("08.07.2026")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.validationErrors.containsKey("issueDate"))
        assertNotNull(state.error)
        assertFalse(state.isSent)
        assertFalse(state.isLoading)
    }

    @Test
    fun onIssueDateChanged_clearsIssueDateValidationError() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        viewModel.onIssueDateChanged("not-a-date")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.send()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.validationErrors.containsKey("issueDate"))

        viewModel.onIssueDateChanged("2026-07-08")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.validationErrors.containsKey("issueDate"))
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
            InvoiceLineItemUi(
                description = "Test",
                quantity = "2",
                unitPrice = "100",
                vatRate = VatRate.RATE_23,
            )
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

    @Test
    fun initialState_prefillsBankAccountFromSellerConfig() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)

        val state = viewModel.uiState.value
        assertEquals(PaymentMethod.TRANSFER, state.paymentMethod)
        assertEquals("61 1090 1014 0000 0712 1981 2874", state.bankAccount)
        assertEquals("wbkpplpp", state.bankSwift)
        assertEquals("Santander", state.bankName)
    }

    @Test
    fun send_transfer_writesNormalizedSellerBankAccount() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)
        fillValidDomesticForm(viewModel)

        viewModel.send()
        viewModel.uiState.first { it.isSent || it.error != null }

        val xml = assertNotNull(sentXml)
        assertTrue(xml.contains("<FormaPlatnosci>6</FormaPlatnosci>"), xml)
        assertTrue(xml.contains("<NrRB>61109010140000071219812874</NrRB>"), xml)
        assertTrue(xml.contains("<SWIFT>WBKPPLPP</SWIFT>"), xml)
        assertTrue(xml.contains("<NazwaBanku>Santander</NazwaBanku>"), xml)
        assertFalse(xml.contains("<Zaplacono>"), xml)
    }

    @Test
    fun send_cashPayment_omitsBankAccount() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)
        fillValidDomesticForm(viewModel)
        viewModel.onPaymentMethodChanged(PaymentMethod.CASH)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        viewModel.uiState.first { it.isSent || it.error != null }

        val xml = assertNotNull(sentXml)
        assertTrue(xml.contains("<FormaPlatnosci>1</FormaPlatnosci>"), xml)
        assertFalse(xml.contains("<RachunekBankowy>"), xml)
    }

    @Test
    fun send_invalidBankAccount_setsValidationError() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)
        fillValidDomesticForm(viewModel)
        viewModel.onBankAccountChanged("61 1090 1014 0000 0712 1981 2875")
        viewModel.onBankSwiftChanged("BPKO")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.uiState.value.validationErrors
        assertTrue(errors.containsKey(SendInvoiceViewModel.FIELD_BANK_ACCOUNT))
        assertTrue(errors.containsKey(SendInvoiceViewModel.FIELD_BANK_SWIFT))
        assertNull(sentXml)
    }

    @Test
    fun updateItem_npIIRate_requiresReverseCharge() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)
        assertFalse(viewModel.uiState.value.isReverseChargeRequired)

        viewModel.updateItem(
            0,
            InvoiceLineItemUi(
                description = "Google AdMob",
                unitPrice = "307.10",
                vatRate = VatRate.NOT_SUBJECT_EU_SERVICES,
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isReverseChargeRequired)
        // np II is out of scope of Polish VAT, so gross equals net.
        assertEquals(Money.fromMajorUnits(307, 10), state.items[0].grossValue)
    }

    @Test
    fun send_euBuyerWithNpIIService_sendsReverseChargeInvoice() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)

        viewModel.onBuyerIdTypeChanged(BuyerIdType.EU_VAT)
        viewModel.onBuyerEuCountryChanged("IE")
        viewModel.onBuyerEuVatNumberChanged("IE 6388047V")
        viewModel.onBuyerNameChanged("Google Ireland Limited")
        viewModel.onBuyerAddressChanged("Gordon House, Barrow Street, Dublin 4")
        viewModel.onInvoiceNumberChanged("FV/10/2026")
        viewModel.onIssueDateChanged("2026-10-21")
        viewModel.updateItem(
            0,
            InvoiceLineItemUi(
                description = "Google AdMob",
                unitPrice = "307,10",
                vatRate = VatRate.NOT_SUBJECT_EU_SERVICES,
            ),
        )
        viewModel.onPaymentStatusChanged(PaymentStatusOption.PAID)
        viewModel.onDueDateChanged("2026-10-21")
        testDispatcher.scheduler.advanceUntilIdle()
        // The payment date defaults to the issue date.
        assertEquals("2026-10-21", viewModel.uiState.value.paymentDate)

        viewModel.send()
        val state = viewModel.uiState.first { it.isSent || it.error != null }

        assertTrue(state.isSent, "Expected isSent, errors: ${state.validationErrors}")
        val xml = assertNotNull(sentXml)
        assertTrue(xml.contains("<KodUE>IE</KodUE>"), xml)
        assertTrue(xml.contains("<NrVatUE>6388047V</NrVatUE>"), xml)
        assertTrue(xml.contains("<KodKraju>IE</KodKraju>"), xml)
        assertTrue(xml.contains("<P_12>np II</P_12>"), xml)
        assertTrue(xml.contains("<P_13_9>307.10</P_13_9>"), xml)
        assertTrue(xml.contains("<P_15>307.10</P_15>"), xml)
        assertTrue(xml.contains("<P_18>1</P_18>"), xml)
        assertTrue(xml.contains("<Zaplacono>1</Zaplacono>"), xml)
        assertTrue(xml.contains("<DataZaplaty>2026-10-21</DataZaplaty>"), xml)
        assertTrue(xml.contains("<Termin>2026-10-21</Termin>"), xml)
        assertTrue(xml.contains("<FormaPlatnosci>6</FormaPlatnosci>"), xml)
    }

    @Test
    fun send_reverseChargeToggle_setsP18ForNpIRate() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)
        fillValidDomesticForm(viewModel)
        viewModel.updateItem(
            0,
            InvoiceLineItemUi(
                description = "Consulting",
                unitPrice = "100",
                vatRate = VatRate.NOT_SUBJECT_ABROAD,
            ),
        )
        viewModel.onReverseChargeChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        viewModel.uiState.first { it.isSent || it.error != null }

        val xml = assertNotNull(sentXml)
        assertTrue(xml.contains("<P_13_8>100.00</P_13_8>"), xml)
        assertTrue(xml.contains("<P_18>1</P_18>"), xml)
    }

    @Test
    fun send_euBuyerWithoutCountryAndWithInvalidNumber_setsValidationErrors() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)

        viewModel.onBuyerIdTypeChanged(BuyerIdType.EU_VAT)
        viewModel.onBuyerEuVatNumberChanged("1234567890123")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.send()
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.uiState.value.validationErrors
        assertTrue(errors.containsKey(SendInvoiceViewModel.FIELD_BUYER_EU_COUNTRY))
        assertTrue(errors.containsKey(SendInvoiceViewModel.FIELD_BUYER_EU_VAT))
        assertFalse(errors.containsKey(SendInvoiceViewModel.FIELD_BUYER_NIP))
    }

    @Test
    fun onBuyerIdTypeChanged_clearsBuyerIdErrors() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)
        viewModel.send()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.validationErrors.containsKey("buyerNip"))

        viewModel.onBuyerIdTypeChanged(BuyerIdType.EU_VAT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.validationErrors.containsKey("buyerNip"))
    }

    @Test
    fun send_partialPayment_writesPaidAmount() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)
        fillValidDomesticForm(viewModel)
        viewModel.onPaymentStatusChanged(PaymentStatusOption.PARTIALLY_PAID)
        viewModel.onPaidAmountChanged("50,50")
        viewModel.onPaymentDateChanged("2026-11-20")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        val state = viewModel.uiState.first { it.isSent || it.error != null }

        assertTrue(state.isSent, "Expected isSent, errors: ${state.validationErrors}")
        val xml = assertNotNull(sentXml)
        assertTrue(xml.contains("<ZnacznikZaplatyCzesciowej>1</ZnacznikZaplatyCzesciowej>"), xml)
        assertTrue(xml.contains("<KwotaZaplatyCzesciowej>50.50</KwotaZaplatyCzesciowej>"), xml)
        assertTrue(xml.contains("<DataZaplatyCzesciowej>2026-11-20</DataZaplatyCzesciowej>"), xml)
    }

    @Test
    fun send_partialPaymentNotBelowGross_setsValidationError() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)
        // Gross = 100 * 1.23 = 123.
        fillValidDomesticForm(viewModel)
        viewModel.onPaymentStatusChanged(PaymentStatusOption.PARTIALLY_PAID)

        for (amount in listOf("123", "200", "0", "abc")) {
            viewModel.onPaidAmountChanged(amount)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.send()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.validationErrors.containsKey(
                    SendInvoiceViewModel.FIELD_PAID_AMOUNT
                ),
                "Expected an error for $amount",
            )
        }
        assertNull(sentXml)
    }

    @Test
    fun send_paidWithInvalidDates_setsValidationErrors() = runTest {
        val viewModel = createViewModel(SELLER_WITH_BANK)
        collectUiState(viewModel)
        fillValidDomesticForm(viewModel)
        viewModel.onPaymentStatusChanged(PaymentStatusOption.PAID)
        viewModel.onPaymentDateChanged("21.10.2026")
        viewModel.onDueDateChanged("tomorrow")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.uiState.value.validationErrors
        assertTrue(errors.containsKey(SendInvoiceViewModel.FIELD_PAYMENT_DATE))
        assertTrue(errors.containsKey(SendInvoiceViewModel.FIELD_DUE_DATE))
    }

    @Test
    fun onPaymentStatusChanged_keepsUserPaymentDate() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)
        viewModel.onPaymentDateChanged("2026-01-05")

        viewModel.onPaymentStatusChanged(PaymentStatusOption.PAID)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("2026-01-05", viewModel.uiState.value.paymentDate)
    }

    @Test
    fun onTemplateSelected_restoresVatRateAndEuBuyer() = runTest {
        val viewModel = createViewModel()
        collectUiState(viewModel)
        viewModel.onBuyerIdTypeChanged(BuyerIdType.EU_VAT)
        viewModel.onBuyerEuCountryChanged("IE")
        viewModel.onBuyerEuVatNumberChanged("6388047V")
        viewModel.onBuyerNameChanged("Google Ireland Limited")
        viewModel.updateItem(
            0,
            InvoiceLineItemUi(
                description = "Google AdMob",
                unitPrice = "100",
                vatRate = VatRate.NOT_SUBJECT_EU_SERVICES,
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onSaveTemplate("AdMob")
        testDispatcher.scheduler.advanceUntilIdle()

        // Reset the form, then re-apply the saved template.
        viewModel.onBuyerIdTypeChanged(BuyerIdType.NIP)
        viewModel.updateItem(0, InvoiceLineItemUi())
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTemplateSelected(viewModel.uiState.value.templates.first())
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BuyerIdType.EU_VAT, state.buyerIdType)
        assertEquals("IE", state.buyerEuCountryCode)
        assertEquals("6388047V", state.buyerEuVatNumber)
        assertEquals(VatRate.NOT_SUBJECT_EU_SERVICES, state.items[0].vatRate)
        assertEquals(Money.fromMajorUnits(100), state.items[0].grossValue)
        assertTrue(state.isReverseChargeRequired)
    }

    /** Fills a valid domestic invoice: one 23% item of 100 net. */
    private fun fillValidDomesticForm(viewModel: SendInvoiceViewModel) {
        viewModel.onBuyerNipChanged("2222222222")
        viewModel.onBuyerNameChanged("Firma XYZ")
        viewModel.onInvoiceNumberChanged("FV/2026/001")
        viewModel.onIssueDateChanged("2026-11-20")
        viewModel.updateItem(
            0,
            InvoiceLineItemUi(description = "Usługa", quantity = "1", unitPrice = "100"),
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** Keeps [SendInvoiceViewModel.uiState] hot for `value` reads and runs pending init work. */
    private fun TestScope.collectUiState(viewModel: SendInvoiceViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** Recovers the invoice XML from a send request; the fake crypto leaves it unencrypted. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeSentInvoice(requestBody: String): String {
        val content =
            Json.parseToJsonElement(requestBody)
                .jsonObject
                .getValue("encryptedInvoiceContent")
                .jsonPrimitive
                .content
        return Base64.decode(content).decodeToString()
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

                path.endsWith("/sessions/online/session-ref/invoices") -> {
                    sentXml = decodeSentInvoice(request.body.toByteArray().decodeToString())
                    respond(
                        content = """{"referenceNumber":"R-001"}""",
                        status = HttpStatusCode.Accepted,
                        headers = jsonHeaders,
                    )
                }

                path.endsWith("/sessions/session-ref/invoices/R-001") ->
                    respond(
                        content =
                            """{"referenceNumber":"R-001","ksefNumber":"KSEF-NUM-001","status":{"code":200,"description":"Sukces"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )

                else ->
                    respond(
                        content = "{}",
                        status = HttpStatusCode.NotFound,
                        headers = jsonHeaders,
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
        val repository =
            KsefRepository(api, sessionHolder, crypto, KsefAuthenticator(mockClient, crypto))

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

    private companion object {
        val SELLER_WITH_BANK =
            SellerConfig(
                name = "ACME Sp. z o.o.",
                address = "ul. Testowa 1",
                bankAccount = "61 1090 1014 0000 0712 1981 2874",
                bankSwift = "wbkpplpp",
                bankName = "Santander",
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

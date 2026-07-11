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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.domain.invoice.InvoiceBuilder
import com.kgurgul.openksef.domain.invoice.InvoiceData
import com.kgurgul.openksef.domain.invoice.InvoiceLineItem
import com.kgurgul.openksef.domain.invoice.InvoiceTemplate
import com.kgurgul.openksef.domain.invoice.InvoiceTemplateItem
import com.kgurgul.openksef.domain.invoke
import com.kgurgul.openksef.domain.money.Money
import com.kgurgul.openksef.domain.observable.InvoiceTemplatesObservable
import com.kgurgul.openksef.domain.observable.SellerConfigObservable
import com.kgurgul.openksef.domain.observe
import com.kgurgul.openksef.domain.result.DeleteInvoiceTemplateInteractor
import com.kgurgul.openksef.domain.result.GetSessionNipInteractor
import com.kgurgul.openksef.domain.result.SaveInvoiceTemplateInteractor
import com.kgurgul.openksef.domain.result.SendInvoiceInteractor
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_form_invalid
import openksef.shared.generated.resources.error_issue_date_invalid
import openksef.shared.generated.resources.error_items_required
import openksef.shared.generated.resources.error_nip_invalid
import openksef.shared.generated.resources.error_required
import openksef.shared.generated.resources.error_send_invoice

data class InvoiceLineItemUi(
    val description: String = "",
    val quantity: String = "1",
    val unit: String = "szt.",
    val unitPrice: String = "",
    val vatRate: Int = 23,
    val netValue: Money = Money.ZERO,
    val grossValue: Money = Money.ZERO,
)

data class SendInvoiceUiState(
    val sellerNip: String = "",
    val sellerName: String = "",
    val sellerAddress: String = "",
    val buyerNip: String = "",
    val buyerName: String = "",
    val buyerAddress: String = "",
    val invoiceNumber: String = "",
    val issueDate: String = "",
    val items: List<InvoiceLineItemUi> = listOf(InvoiceLineItemUi()),
    val templates: List<InvoiceTemplate> = emptyList(),
    val selectedTemplateId: String? = null,
    val currency: String = "PLN",
    val isLoading: Boolean = false,
    val isSent: Boolean = false,
    val sentReferenceNumber: String = "",
    val sentKsefNumber: String = "",
    val error: UiText? = null,
    val validationErrors: Map<String, UiText> = emptyMap(),
)

class SendInvoiceViewModel(
    private val sendInvoiceInteractor: SendInvoiceInteractor,
    getSessionNipInteractor: GetSessionNipInteractor,
    invoiceTemplatesObservable: InvoiceTemplatesObservable,
    sellerConfigObservable: SellerConfigObservable,
    private val saveInvoiceTemplateInteractor: SaveInvoiceTemplateInteractor,
    private val deleteInvoiceTemplateInteractor: DeleteInvoiceTemplateInteractor,
) : ViewModel() {

    private val formState =
        MutableStateFlow(
            SendInvoiceUiState(
                issueDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            )
        )

    /** Seller edits; `null` fields fall back to the persisted seller config. */
    private val sellerEdits = MutableStateFlow(SellerEdits())
    private val sessionNip = MutableStateFlow("")

    val uiState: StateFlow<SendInvoiceUiState> =
        combine(
                formState,
                sellerEdits,
                sessionNip,
                invoiceTemplatesObservable.observe(),
                sellerConfigObservable.observe(),
            ) { form, edits, nip, templates, config ->
                form.copy(
                    sellerNip = nip,
                    sellerName = edits.name ?: config?.name ?: "",
                    sellerAddress = edits.address ?: config?.address ?: "",
                    templates = templates,
                    selectedTemplateId =
                        form.selectedTemplateId.takeIf { id -> templates.any { it.id == id } },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SendInvoiceUiState())

    init {
        viewModelScope.launch { sessionNip.value = getSessionNipInteractor() ?: "" }
    }

    fun onSellerNameChanged(name: String) {
        sellerEdits.update { it.copy(name = name) }
        formState.update { it.copy(validationErrors = it.validationErrors - FIELD_SELLER_NAME) }
    }

    fun onSellerAddressChanged(address: String) {
        sellerEdits.update { it.copy(address = address) }
        formState.update { it.copy(validationErrors = it.validationErrors - FIELD_SELLER_ADDRESS) }
    }

    fun onBuyerNipChanged(nip: String) {
        formState.update {
            it.copy(buyerNip = nip, validationErrors = it.validationErrors - FIELD_BUYER_NIP)
        }
    }

    fun onBuyerNameChanged(name: String) {
        formState.update {
            it.copy(buyerName = name, validationErrors = it.validationErrors - FIELD_BUYER_NAME)
        }
    }

    fun onBuyerAddressChanged(address: String) {
        formState.update { it.copy(buyerAddress = address) }
    }

    fun onInvoiceNumberChanged(number: String) {
        formState.update {
            it.copy(
                invoiceNumber = number,
                validationErrors = it.validationErrors - FIELD_INVOICE_NUMBER,
            )
        }
    }

    fun onIssueDateChanged(date: String) {
        formState.update {
            it.copy(issueDate = date, validationErrors = it.validationErrors - FIELD_ISSUE_DATE)
        }
    }

    fun addItem() {
        formState.update { it.copy(items = it.items + InvoiceLineItemUi()) }
    }

    fun removeItem(index: Int) {
        formState.update { state ->
            if (state.items.size > 1) {
                state.copy(items = state.items.toMutableList().apply { removeAt(index) })
            } else state
        }
    }

    fun updateItem(index: Int, item: InvoiceLineItemUi) {
        val updated = withCalculatedValues(item)

        formState.update { state ->
            state.copy(
                items = state.items.toMutableList().apply { set(index, updated) },
                validationErrors = state.validationErrors - FIELD_ITEMS,
            )
        }
    }

    fun onTemplateSelected(template: InvoiceTemplate) {
        val items =
            template.items.map { templateItem ->
                withCalculatedValues(
                    InvoiceLineItemUi(
                        description = templateItem.description,
                        quantity = templateItem.quantity,
                        unit = templateItem.unit,
                        unitPrice = templateItem.unitPrice,
                    )
                )
            }

        formState.update { state ->
            state.copy(
                selectedTemplateId = template.id,
                buyerNip = template.buyerNip.ifBlank { state.buyerNip },
                buyerName = template.buyerName.ifBlank { state.buyerName },
                buyerAddress = template.buyerAddress.ifBlank { state.buyerAddress },
                items = items.ifEmpty { listOf(InvoiceLineItemUi()) },
                validationErrors = emptyMap(),
            )
        }
    }

    fun onSaveTemplate(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val state = uiState.value
        val template =
            InvoiceTemplate(
                id = Clock.System.now().toEpochMilliseconds().toString(),
                name = trimmedName,
                buyerNip = state.buyerNip,
                buyerName = state.buyerName,
                buyerAddress = state.buyerAddress,
                items =
                    state.items.map { item ->
                        InvoiceTemplateItem(
                            description = item.description,
                            quantity = item.quantity,
                            unit = item.unit,
                            unitPrice = item.unitPrice,
                            vatRate = item.vatRate,
                        )
                    },
            )
        viewModelScope.launch { saveInvoiceTemplateInteractor(template) }
    }

    fun onDeleteTemplate(id: String) {
        viewModelScope.launch { deleteInvoiceTemplateInteractor(id) }
    }

    private fun withCalculatedValues(item: InvoiceLineItemUi): InvoiceLineItemUi {
        val qty = item.quantity.toDoubleOrNull() ?: 0.0
        val unitPrice = Money.fromFormattedString(item.unitPrice)
        val net = unitPrice * qty
        val gross = net.withVatRate(item.vatRate)
        return item.copy(netValue = net, grossValue = gross)
    }

    fun send() {
        val state = uiState.value
        val errors = mutableMapOf<String, UiText>()

        if (state.sellerName.isBlank()) {
            errors[FIELD_SELLER_NAME] = UiText.Resource(Res.string.error_required)
        }
        // FA(3) requires the seller address element.
        if (state.sellerAddress.isBlank()) {
            errors[FIELD_SELLER_ADDRESS] = UiText.Resource(Res.string.error_required)
        }
        if (state.buyerNip.length != 10 || !state.buyerNip.all { it.isDigit() }) {
            errors[FIELD_BUYER_NIP] = UiText.Resource(Res.string.error_nip_invalid)
        }
        if (state.buyerName.isBlank()) {
            errors[FIELD_BUYER_NAME] = UiText.Resource(Res.string.error_required)
        }
        if (state.invoiceNumber.isBlank()) {
            errors[FIELD_INVOICE_NUMBER] = UiText.Resource(Res.string.error_required)
        }
        // FA(3) requires ISO-8601 dates (YYYY-MM-DD) in P_1 and DataWytworzeniaFa.
        if (!isValidIsoDate(state.issueDate)) {
            errors[FIELD_ISSUE_DATE] = UiText.Resource(Res.string.error_issue_date_invalid)
        }
        if (state.items.all { it.description.isBlank() }) {
            errors[FIELD_ITEMS] = UiText.Resource(Res.string.error_items_required)
        }

        if (errors.isNotEmpty()) {
            formState.update {
                it.copy(
                    validationErrors = errors,
                    error = UiText.Resource(Res.string.error_form_invalid),
                )
            }
            return
        }

        formState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val invoiceData =
                InvoiceData(
                    invoiceNumber = state.invoiceNumber,
                    issueDate = state.issueDate,
                    sellerNip = state.sellerNip,
                    sellerName = state.sellerName,
                    sellerAddress = state.sellerAddress,
                    buyerNip = state.buyerNip,
                    buyerName = state.buyerName,
                    buyerAddress = state.buyerAddress,
                    currency = state.currency,
                    items =
                        state.items
                            .filter { it.description.isNotBlank() }
                            .map { item ->
                                InvoiceLineItem(
                                    description = item.description,
                                    quantity = item.quantity.toDoubleOrNull() ?: 0.0,
                                    unit = item.unit.ifBlank { "szt." },
                                    unitPrice = Money.fromFormattedString(item.unitPrice),
                                    vatRate = item.vatRate,
                                    netValue = item.netValue,
                                    grossValue = item.grossValue,
                                )
                            },
                )

            val xml = InvoiceBuilder.buildXml(invoiceData)
            sendInvoiceInteractor(xml)
                .onSuccess { result ->
                    formState.update {
                        it.copy(
                            isLoading = false,
                            isSent = true,
                            sentReferenceNumber = result.referenceNumber,
                            sentKsefNumber = result.ksefNumber ?: "",
                        )
                    }
                }
                .onFailure { e ->
                    formState.update {
                        it.copy(
                            isLoading = false,
                            error =
                                e.message?.let { msg -> UiText.Raw(msg) }
                                    ?: UiText.Resource(Res.string.error_send_invoice),
                        )
                    }
                }
        }
    }

    fun clearError() {
        formState.update { it.copy(error = null) }
    }

    private fun isValidIsoDate(date: String): Boolean = runCatching {
        LocalDate.parse(date)
    }
        .isSuccess

    private data class SellerEdits(val name: String? = null, val address: String? = null)

    companion object {
        const val FIELD_SELLER_NAME = "sellerName"
        const val FIELD_SELLER_ADDRESS = "sellerAddress"
        const val FIELD_BUYER_NIP = "buyerNip"
        const val FIELD_BUYER_NAME = "buyerName"
        const val FIELD_INVOICE_NUMBER = "invoiceNumber"
        const val FIELD_ISSUE_DATE = "issueDate"
        const val FIELD_ITEMS = "items"
    }
}

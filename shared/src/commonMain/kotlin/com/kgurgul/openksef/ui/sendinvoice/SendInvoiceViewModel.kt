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
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.repository.InvoiceTemplateRepository
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.invoice.InvoiceBuilder
import com.kgurgul.openksef.domain.invoice.InvoiceData
import com.kgurgul.openksef.domain.invoice.InvoiceLineItem
import com.kgurgul.openksef.domain.invoice.InvoiceTemplate
import com.kgurgul.openksef.domain.invoice.InvoiceTemplateItem
import com.kgurgul.openksef.domain.money.Money
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import openksef.shared.generated.resources.Res
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
    val error: UiText? = null,
    val validationErrors: Map<String, UiText> = emptyMap(),
)

class SendInvoiceViewModel(
    private val repository: KsefRepository,
    private val sessionHolder: SessionHolder,
    private val templateRepository: InvoiceTemplateRepository,
    private val sellerConfigRepository: SellerConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendInvoiceUiState())
    val uiState: StateFlow<SendInvoiceUiState> = _uiState.asStateFlow()

    init {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        _uiState.update {
            it.copy(sellerNip = sessionHolder.nip ?: "", issueDate = today.toString())
        }
        viewModelScope.launch {
            sellerConfigRepository.config.first()?.let { config ->
                _uiState.update {
                    it.copy(sellerName = config.name, sellerAddress = config.address)
                }
            }
        }
        templateRepository.templates
            .onEach { templates ->
                _uiState.update { state ->
                    val stillExists = templates.any { it.id == state.selectedTemplateId }
                    state.copy(
                        templates = templates,
                        selectedTemplateId = state.selectedTemplateId.takeIf { stillExists },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSellerNameChanged(name: String) {
        _uiState.update {
            it.copy(sellerName = name, validationErrors = it.validationErrors - FIELD_SELLER_NAME)
        }
    }

    fun onSellerAddressChanged(address: String) {
        _uiState.update { it.copy(sellerAddress = address) }
    }

    fun onBuyerNipChanged(nip: String) {
        _uiState.update {
            it.copy(buyerNip = nip, validationErrors = it.validationErrors - FIELD_BUYER_NIP)
        }
    }

    fun onBuyerNameChanged(name: String) {
        _uiState.update {
            it.copy(buyerName = name, validationErrors = it.validationErrors - FIELD_BUYER_NAME)
        }
    }

    fun onBuyerAddressChanged(address: String) {
        _uiState.update { it.copy(buyerAddress = address) }
    }

    fun onInvoiceNumberChanged(number: String) {
        _uiState.update {
            it.copy(
                invoiceNumber = number,
                validationErrors = it.validationErrors - FIELD_INVOICE_NUMBER,
            )
        }
    }

    fun onIssueDateChanged(date: String) {
        _uiState.update { it.copy(issueDate = date) }
    }

    fun addItem() {
        _uiState.update { it.copy(items = it.items + InvoiceLineItemUi()) }
    }

    fun removeItem(index: Int) {
        _uiState.update { state ->
            if (state.items.size > 1) {
                state.copy(items = state.items.toMutableList().apply { removeAt(index) })
            } else state
        }
    }

    fun updateItem(index: Int, item: InvoiceLineItemUi) {
        val updated = withCalculatedValues(item)

        _uiState.update { state ->
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
                        vatRate = templateItem.vatRate,
                    )
                )
            }

        _uiState.update { state ->
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
        val state = _uiState.value
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
        viewModelScope.launch { templateRepository.save(template) }
    }

    fun onDeleteTemplate(id: String) {
        viewModelScope.launch { templateRepository.delete(id) }
    }

    private fun withCalculatedValues(item: InvoiceLineItemUi): InvoiceLineItemUi {
        val qty = item.quantity.toDoubleOrNull() ?: 0.0
        val unitPrice = Money.fromFormattedString(item.unitPrice)
        val net = unitPrice * qty
        val gross = net.withVatRate(item.vatRate)
        return item.copy(netValue = net, grossValue = gross)
    }

    fun send() {
        val state = _uiState.value
        val errors = mutableMapOf<String, UiText>()

        if (state.sellerName.isBlank()) {
            errors[FIELD_SELLER_NAME] = UiText.Resource(Res.string.error_required)
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
        if (state.items.all { it.description.isBlank() }) {
            errors[FIELD_ITEMS] = UiText.Resource(Res.string.error_items_required)
        }

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

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
            repository
                .sendInvoice(xml)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSent = true,
                            sentReferenceNumber = result.referenceNumber,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
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
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        const val FIELD_SELLER_NAME = "sellerName"
        const val FIELD_BUYER_NIP = "buyerNip"
        const val FIELD_BUYER_NAME = "buyerName"
        const val FIELD_INVOICE_NUMBER = "invoiceNumber"
        const val FIELD_ITEMS = "items"
    }
}

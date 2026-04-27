package com.kgurgul.openksef.ui.sendinvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.domain.invoice.InvoiceBuilder
import com.kgurgul.openksef.domain.invoice.InvoiceData
import com.kgurgul.openksef.domain.invoice.InvoiceLineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class InvoiceLineItemUi(
    val description: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val vatRate: Int = 23,
    val netValue: String = "0.00",
    val grossValue: String = "0.00"
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
    val currency: String = "PLN",
    val isLoading: Boolean = false,
    val isSent: Boolean = false,
    val sentReferenceNumber: String = "",
    val error: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
)

class SendInvoiceViewModel(
    private val repository: KsefRepository,
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendInvoiceUiState())
    val uiState: StateFlow<SendInvoiceUiState> = _uiState.asStateFlow()

    init {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        _uiState.update {
            it.copy(
                sellerNip = sessionHolder.nip ?: "",
                issueDate = today.toString()
            )
        }
    }

    fun onSellerNameChanged(name: String) {
        _uiState.update {
            it.copy(
                sellerName = name,
                validationErrors = it.validationErrors - "sellerName"
            )
        }
    }

    fun onSellerAddressChanged(address: String) {
        _uiState.update { it.copy(sellerAddress = address) }
    }

    fun onBuyerNipChanged(nip: String) {
        _uiState.update {
            it.copy(
                buyerNip = nip,
                validationErrors = it.validationErrors - "buyerNip"
            )
        }
    }

    fun onBuyerNameChanged(name: String) {
        _uiState.update {
            it.copy(
                buyerName = name,
                validationErrors = it.validationErrors - "buyerName"
            )
        }
    }

    fun onBuyerAddressChanged(address: String) {
        _uiState.update { it.copy(buyerAddress = address) }
    }

    fun onInvoiceNumberChanged(number: String) {
        _uiState.update {
            it.copy(
                invoiceNumber = number,
                validationErrors = it.validationErrors - "invoiceNumber"
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
        val qty = item.quantity.toDoubleOrNull() ?: 0.0
        val price = item.unitPrice.toDoubleOrNull() ?: 0.0
        val net = qty * price
        val gross = net * (1 + item.vatRate / 100.0)

        val updated = item.copy(
            netValue = formatAmount(net),
            grossValue = formatAmount(gross)
        )

        _uiState.update { state ->
            state.copy(
                items = state.items.toMutableList().apply { set(index, updated) },
                validationErrors = state.validationErrors - "items"
            )
        }
    }

    fun send() {
        val state = _uiState.value
        val errors = mutableMapOf<String, String>()

        if (state.sellerName.isBlank()) errors["sellerName"] = "Wymagane"
        if (state.buyerNip.length != 10 || !state.buyerNip.all { it.isDigit() }) {
            errors["buyerNip"] = "NIP musi mieć 10 cyfr"
        }
        if (state.buyerName.isBlank()) errors["buyerName"] = "Wymagane"
        if (state.invoiceNumber.isBlank()) errors["invoiceNumber"] = "Wymagane"
        if (state.items.all { it.description.isBlank() }) errors["items"] = "Dodaj min. 1 pozycję"

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val invoiceData = InvoiceData(
                invoiceNumber = state.invoiceNumber,
                issueDate = state.issueDate,
                sellerNip = state.sellerNip,
                sellerName = state.sellerName,
                sellerAddress = state.sellerAddress,
                buyerNip = state.buyerNip,
                buyerName = state.buyerName,
                buyerAddress = state.buyerAddress,
                currency = state.currency,
                items = state.items
                    .filter { it.description.isNotBlank() }
                    .map { item ->
                        InvoiceLineItem(
                            description = item.description,
                            quantity = item.quantity.toDoubleOrNull() ?: 0.0,
                            unitPrice = item.unitPrice.toDoubleOrNull() ?: 0.0,
                            vatRate = item.vatRate,
                            netValue = item.netValue.toDoubleOrNull() ?: 0.0,
                            grossValue = item.grossValue.toDoubleOrNull() ?: 0.0
                        )
                    }
            )

            val xml = InvoiceBuilder.buildXml(invoiceData)
            repository.sendInvoice(xml)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSent = true,
                            sentReferenceNumber = result.referenceNumber
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Błąd wysyłania faktury"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun formatAmount(value: Double): String {
        val rounded = kotlin.math.round(value * 100) / 100.0
        val str = rounded.toString()
        val dotIndex = str.indexOf('.')
        return if (dotIndex == -1) "$str.00"
        else {
            val decimals = str.length - dotIndex - 1
            when {
                decimals == 1 -> "${str}0"
                decimals >= 2 -> str.substring(0, dotIndex + 3)
                else -> str
            }
        }
    }
}

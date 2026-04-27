package com.kgurgul.openksef.ui.invoicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.data.repository.KsefRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvoiceDetailUiState(
    val ksefReferenceNumber: String = "",
    val invoiceXml: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class InvoiceDetailViewModel(
    private val repository: KsefRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceDetailUiState())
    val uiState: StateFlow<InvoiceDetailUiState> = _uiState.asStateFlow()

    fun loadInvoice(ksefReferenceNumber: String) {
        _uiState.update {
            it.copy(
                ksefReferenceNumber = ksefReferenceNumber,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            repository.getInvoice(ksefReferenceNumber)
                .onSuccess { xml ->
                    _uiState.update {
                        it.copy(invoiceXml = xml, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Błąd pobierania faktury"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

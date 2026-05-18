package com.kgurgul.openksef.ui.invoicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.data.repository.KsefRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_load_invoice

data class InvoiceDetailUiState(
    val ksefReferenceNumber: String = "",
    val invoiceXml: String? = null,
    val isLoading: Boolean = false,
)

sealed interface InvoiceDetailEvent {
    data class ShowError(val message: UiText) : InvoiceDetailEvent
}

class InvoiceDetailViewModel(
    private val ksefReferenceNumber: String,
    private val repository: KsefRepository,
) : ViewModel() {

    private val eventChannel = Channel<InvoiceDetailEvent>(Channel.BUFFERED)
    val events: Flow<InvoiceDetailEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<InvoiceDetailUiState> = flow {
        emit(loadingState)
        repository.getInvoice(ksefReferenceNumber)
            .onSuccess { xml ->
                emit(
                    InvoiceDetailUiState(
                        ksefReferenceNumber = ksefReferenceNumber,
                        invoiceXml = xml,
                        isLoading = false,
                    )
                )
            }
            .onFailure { e ->
                emit(loadingState.copy(isLoading = false))
                eventChannel.send(
                    InvoiceDetailEvent.ShowError(
                        e.message?.let { UiText.Raw(it) }
                            ?: UiText.Resource(Res.string.error_load_invoice)
                    )
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), loadingState)

    private val loadingState
        get() = InvoiceDetailUiState(
            ksefReferenceNumber = ksefReferenceNumber,
            isLoading = true,
        )
}

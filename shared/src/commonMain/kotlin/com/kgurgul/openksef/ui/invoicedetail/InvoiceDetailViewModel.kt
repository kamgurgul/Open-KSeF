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

package com.kgurgul.openksef.ui.invoicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.domain.pdf.InvoicePdfExporter
import com.kgurgul.openksef.domain.pdf.PdfExportResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_export_pdf
import openksef.shared.generated.resources.error_load_invoice

data class InvoiceDetailUiState(
    val ksefReferenceNumber: String = "",
    val invoiceXml: String? = null,
    val isLoading: Boolean = false,
    val canExportPdf: Boolean = false,
    val isExportingPdf: Boolean = false,
)

sealed interface InvoiceDetailEvent {
    data class ShowError(val message: UiText) : InvoiceDetailEvent

    data object PdfExported : InvoiceDetailEvent
}

class InvoiceDetailViewModel(
    private val ksefReferenceNumber: String,
    private val repository: KsefRepository,
    private val pdfExporter: InvoicePdfExporter,
) : ViewModel() {

    private val eventChannel = Channel<InvoiceDetailEvent>(Channel.BUFFERED)
    val events: Flow<InvoiceDetailEvent> = eventChannel.receiveAsFlow()

    private val isExportingPdf = MutableStateFlow(false)

    private val invoiceLoad: Flow<InvoiceLoad> = flow {
        emit(InvoiceLoad(isLoading = true))
        repository
            .getInvoice(ksefReferenceNumber)
            .onSuccess { xml -> emit(InvoiceLoad(isLoading = false, invoiceXml = xml)) }
            .onFailure { e ->
                emit(InvoiceLoad(isLoading = false))
                eventChannel.send(
                    InvoiceDetailEvent.ShowError(
                        e.message?.let { UiText.Raw(it) }
                            ?: UiText.Resource(Res.string.error_load_invoice)
                    )
                )
            }
    }

    val uiState: StateFlow<InvoiceDetailUiState> =
        combine(invoiceLoad, isExportingPdf) { load, exporting ->
                InvoiceDetailUiState(
                    ksefReferenceNumber = ksefReferenceNumber,
                    invoiceXml = load.invoiceXml,
                    isLoading = load.isLoading,
                    canExportPdf = pdfExporter.isSupported,
                    isExportingPdf = exporting,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), loadingState)

    fun onExportPdfClick() {
        val invoiceXml = uiState.value.invoiceXml ?: return
        if (isExportingPdf.value) return
        viewModelScope.launch {
            isExportingPdf.value = true
            val result =
                try {
                    pdfExporter.export(invoiceXml, ksefReferenceNumber)
                } finally {
                    isExportingPdf.value = false
                }
            when (result) {
                is PdfExportResult.Success -> eventChannel.send(InvoiceDetailEvent.PdfExported)

                is PdfExportResult.Failure ->
                    eventChannel.send(
                        InvoiceDetailEvent.ShowError(
                            result.reason?.let { UiText.Raw(it) }
                                ?: UiText.Resource(Res.string.error_export_pdf)
                        )
                    )

                PdfExportResult.Cancelled -> Unit
            }
        }
    }

    private val loadingState
        get() =
            InvoiceDetailUiState(
                ksefReferenceNumber = ksefReferenceNumber,
                isLoading = true,
                canExportPdf = pdfExporter.isSupported,
            )

    private data class InvoiceLoad(val isLoading: Boolean, val invoiceXml: String? = null)
}

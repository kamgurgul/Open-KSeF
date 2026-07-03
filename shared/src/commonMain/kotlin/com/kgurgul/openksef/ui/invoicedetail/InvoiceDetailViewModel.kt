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
import com.kgurgul.openksef.domain.pdf.InvoicePdfExporter
import com.kgurgul.openksef.domain.pdf.InvoicePdfSharer
import com.kgurgul.openksef.domain.pdf.KsefWebPdfRenderer
import com.kgurgul.openksef.domain.pdf.PdfExportResult
import com.kgurgul.openksef.domain.result.GetInvoiceInteractor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_export_pdf
import openksef.shared.generated.resources.error_load_invoice
import openksef.shared.generated.resources.error_render_pdf

data class InvoiceDetailUiState(
    val ksefReferenceNumber: String = "",
    val invoiceXml: String? = null,
    val isLoading: Boolean = false,
    val canPreviewPdf: Boolean = false,
    val canExportPdf: Boolean = false,
    val pdfBytes: ByteArray? = null,
    val isPdfLoading: Boolean = false,
    val isPdfError: Boolean = false,
    val isExportingPdf: Boolean = false,
    val isDownloading: Boolean = false,
) {
    val canDownload: Boolean
        get() = pdfBytes != null && !isDownloading

    // ByteArray needs structural equality so StateFlow does not treat identical content as a change.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InvoiceDetailUiState) return false
        return ksefReferenceNumber == other.ksefReferenceNumber &&
                invoiceXml == other.invoiceXml &&
                isLoading == other.isLoading &&
                canPreviewPdf == other.canPreviewPdf &&
                canExportPdf == other.canExportPdf &&
                isPdfLoading == other.isPdfLoading &&
                isPdfError == other.isPdfError &&
                isExportingPdf == other.isExportingPdf &&
                isDownloading == other.isDownloading &&
                pdfBytes.contentEquals(other.pdfBytes)
    }

    override fun hashCode(): Int {
        var result = ksefReferenceNumber.hashCode()
        result = 31 * result + (invoiceXml?.hashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + canPreviewPdf.hashCode()
        result = 31 * result + canExportPdf.hashCode()
        result = 31 * result + (pdfBytes?.contentHashCode() ?: 0)
        result = 31 * result + isPdfLoading.hashCode()
        result = 31 * result + isPdfError.hashCode()
        result = 31 * result + isExportingPdf.hashCode()
        result = 31 * result + isDownloading.hashCode()
        return result
    }
}

sealed interface InvoiceDetailEvent {
    data class ShowError(val message: UiText) : InvoiceDetailEvent

    data object PdfExported : InvoiceDetailEvent

    data object PdfSaved : InvoiceDetailEvent
}

class InvoiceDetailViewModel(
    private val ksefReferenceNumber: String,
    private val getInvoiceInteractor: GetInvoiceInteractor,
    private val pdfExporter: InvoicePdfExporter,
    private val webPdfRenderer: KsefWebPdfRenderer,
    private val pdfSharer: InvoicePdfSharer,
) : ViewModel() {

    private val eventChannel = Channel<InvoiceDetailEvent>(Channel.BUFFERED)
    val events: Flow<InvoiceDetailEvent> = eventChannel.receiveAsFlow()

    private val invoiceLoad = MutableStateFlow(InvoiceLoad(isLoading = true))
    private val pdfState = MutableStateFlow(PdfState(isLoading = webPdfRenderer.isSupported))
    private val isExportingPdf = MutableStateFlow(false)
    private val isDownloading = MutableStateFlow(false)

    val uiState: StateFlow<InvoiceDetailUiState> =
        combine(invoiceLoad, pdfState, isExportingPdf, isDownloading) { load,
                                                                        pdf,
                                                                        exporting,
                                                                        downloading ->
                InvoiceDetailUiState(
                    ksefReferenceNumber = ksefReferenceNumber,
                    invoiceXml = load.invoiceXml,
                    isLoading = load.isLoading,
                    canPreviewPdf = webPdfRenderer.isSupported,
                    canExportPdf = pdfExporter.isSupported,
                    pdfBytes = pdf.pdfBytes,
                    isPdfLoading = pdf.isLoading,
                    isPdfError = pdf.isError,
                    isExportingPdf = exporting,
                    isDownloading = downloading,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), loadingState)

    init {
        load()
    }

    fun onRetryPdfClick() {
        val xml = invoiceLoad.value.invoiceXml ?: return
        if (pdfState.value.isLoading) return
        viewModelScope.launch { renderPdf(xml) }
    }

    fun onDownloadClick() {
        val bytes = pdfState.value.pdfBytes ?: return
        if (isDownloading.value) return
        viewModelScope.launch {
            isDownloading.value = true
            val result =
                try {
                    pdfSharer.share(bytes, ksefReferenceNumber)
                } finally {
                    isDownloading.value = false
                }
            when (result) {
                is PdfExportResult.Success -> eventChannel.send(InvoiceDetailEvent.PdfSaved)

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

    fun onExportPdfClick() {
        val invoiceXml = invoiceLoad.value.invoiceXml ?: return
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

    private fun load() {
        invoiceLoad.value = InvoiceLoad(isLoading = true)
        pdfState.value = PdfState(isLoading = webPdfRenderer.isSupported)
        viewModelScope.launch {
            getInvoiceInteractor(ksefReferenceNumber)
                .onSuccess { xml ->
                    invoiceLoad.value = InvoiceLoad(isLoading = false, invoiceXml = xml)
                    if (webPdfRenderer.isSupported) renderPdf(xml)
                }
                .onFailure { e ->
                    invoiceLoad.value = InvoiceLoad(isLoading = false)
                    pdfState.value = PdfState(isError = webPdfRenderer.isSupported)
                    eventChannel.send(
                        InvoiceDetailEvent.ShowError(
                            e.message?.let { UiText.Raw(it) }
                                ?: UiText.Resource(Res.string.error_load_invoice)
                        )
                    )
                }
        }
    }

    private suspend fun renderPdf(xml: String) {
        pdfState.value = PdfState(isLoading = true)
        try {
            val bytes = webPdfRenderer.render(xml, ksefReferenceNumber)
            pdfState.value = PdfState(pdfBytes = bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pdfState.value = PdfState(isError = true)
            eventChannel.send(
                InvoiceDetailEvent.ShowError(
                    e.message?.let { UiText.Raw(it) }
                        ?: UiText.Resource(Res.string.error_render_pdf)
                )
            )
        }
    }

    private val loadingState
        get() =
            InvoiceDetailUiState(
                ksefReferenceNumber = ksefReferenceNumber,
                isLoading = true,
                canPreviewPdf = webPdfRenderer.isSupported,
                canExportPdf = pdfExporter.isSupported,
                isPdfLoading = webPdfRenderer.isSupported,
            )

    private data class InvoiceLoad(val isLoading: Boolean, val invoiceXml: String? = null)

    private data class PdfState(
        val pdfBytes: ByteArray? = null,
        val isLoading: Boolean = false,
        val isError: Boolean = false,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PdfState) return false
            return isLoading == other.isLoading &&
                    isError == other.isError &&
                    pdfBytes.contentEquals(other.pdfBytes)
        }

        override fun hashCode(): Int {
            var result = pdfBytes?.contentHashCode() ?: 0
            result = 31 * result + isLoading.hashCode()
            result = 31 * result + isError.hashCode()
            return result
        }
    }
}

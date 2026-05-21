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

package com.kgurgul.openksef.domain.pdf

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/**
 * Android [InvoicePdfExporter]. Apache FOP cannot run on Android, so the invoice XML is parsed into
 * an [InvoiceDocument] and drawn onto a native `PdfDocument` by [AndroidInvoicePdfRenderer]. The
 * file is stored in app-private storage and handed to a system PDF viewer.
 */
class AndroidInvoicePdfExporter(private val context: Context) : InvoicePdfExporter {

    override val isSupported: Boolean = true

    override suspend fun export(invoiceXml: String, ksefReferenceNumber: String): PdfExportResult =
        withContext(Dispatchers.IO) {
            try {
                val document = InvoiceXmlParser.parse(invoiceXml)
                val pdfBytes = AndroidInvoicePdfRenderer().render(document, ksefReferenceNumber)
                val file = writePdf(pdfBytes, ksefReferenceNumber)
                openInViewer(file)
                PdfExportResult.Success(file.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PdfExportResult.Failure(e.message)
            }
        }

    private fun writePdf(bytes: ByteArray, ksefReferenceNumber: String): File {
        val directory = File(context.filesDir, INVOICES_DIR).apply { mkdirs() }
        val base = ksefReferenceNumber.ifBlank { "invoice" }.replace(UNSAFE_FILE_CHARS, "_")
        return File(directory, "invoice_$base.pdf").apply { writeBytes(bytes) }
    }

    private fun openInViewer(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        // Best effort: the file is already saved even if no PDF viewer is installed.
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val INVOICES_DIR = "invoices"
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}

actual fun defaultInvoicePdfExporter(): InvoicePdfExporter =
    AndroidInvoicePdfExporter(KoinPlatform.getKoin().get<Context>())

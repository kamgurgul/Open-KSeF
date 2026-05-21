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

import io.alapierre.ksef.fop.InvoiceGenerationParams
import io.alapierre.ksef.fop.InvoiceSchema
import io.alapierre.ksef.fop.PdfGenerator
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

/**
 * Desktop [InvoicePdfExporter] backed by the `ksef-fop` library (Apache FOP). It reproduces the
 * official KSeF invoice visualization, then prompts the user for a save location and opens the
 * resulting file in the system PDF viewer.
 */
class DesktopInvoicePdfExporter : InvoicePdfExporter {

    override val isSupported: Boolean = true

    override suspend fun export(invoiceXml: String, ksefReferenceNumber: String): PdfExportResult {
        val pdfBytes =
            try {
                withContext(Dispatchers.IO) { generatePdf(invoiceXml, ksefReferenceNumber) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return PdfExportResult.Failure(e.message)
            }

        val target =
            withContext(Dispatchers.Swing) {
                chooseSaveLocation(defaultFileName(ksefReferenceNumber))
            } ?: return PdfExportResult.Cancelled

        return try {
            withContext(Dispatchers.IO) {
                target.writeBytes(pdfBytes)
                openInSystemViewer(target)
            }
            PdfExportResult.Success(target.absolutePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PdfExportResult.Failure(e.message)
        }
    }

    private fun generatePdf(invoiceXml: String, ksefReferenceNumber: String): ByteArray {
        val classLoader = this::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
        val previousClassLoader = Thread.currentThread().contextClassLoader
        // The library resolves bundled `classpath:` fonts via the thread context class loader.
        Thread.currentThread().contextClassLoader = classLoader
        try {
            val config =
                classLoader.getResourceAsStream(FOP_CONFIG_RESOURCE)
                    ?: error("Missing $FOP_CONFIG_RESOURCE on the classpath")
            val generator = config.use { PdfGenerator(it) }

            val params =
                InvoiceGenerationParams.builder()
                    .schema(InvoiceXmlParser.detectSchema(invoiceXml).toFopSchema())
                    .also {
                        if (ksefReferenceNumber.isNotBlank()) it.ksefNumber(ksefReferenceNumber)
                    }
                    .build()

            return ByteArrayOutputStream().use { out ->
                generator.generateInvoice(invoiceXml.encodeToByteArray(), params, out)
                out.toByteArray()
            }
        } finally {
            Thread.currentThread().contextClassLoader = previousClassLoader
        }
    }

    private fun chooseSaveLocation(defaultName: String): File? {
        val dialog =
            FileDialog(null as Frame?, "Save invoice PDF", FileDialog.SAVE).apply {
                file = defaultName
                isVisible = true
            }
        val directory = dialog.directory ?: return null
        val fileName = dialog.file ?: return null
        val selected = File(directory, fileName)
        return if (selected.extension.equals("pdf", ignoreCase = true)) {
            selected
        } else {
            File(selected.parentFile, "${selected.name}.pdf")
        }
    }

    private fun openInSystemViewer(file: File) {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            runCatching { desktop.open(file) }
        }
    }

    private fun defaultFileName(ksefReferenceNumber: String): String {
        val base = ksefReferenceNumber.ifBlank { "invoice" }.replace(UNSAFE_FILE_CHARS, "_")
        return "invoice_$base.pdf"
    }

    private fun InvoiceSchemaType.toFopSchema(): InvoiceSchema =
        when (this) {
            InvoiceSchemaType.FA2 -> InvoiceSchema.FA2_1_0_E
            InvoiceSchemaType.FA3 -> InvoiceSchema.FA3_1_0_E
            InvoiceSchemaType.FA_RR -> InvoiceSchema.FA_RR_1_1_E
        }

    private companion object {
        const val FOP_CONFIG_RESOURCE = "fop.xconf"
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}

actual fun defaultInvoicePdfExporter(): InvoicePdfExporter = DesktopInvoicePdfExporter()

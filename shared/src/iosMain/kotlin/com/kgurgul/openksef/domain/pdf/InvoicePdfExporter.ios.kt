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

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableData
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSValue
import platform.Foundation.setValue
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPage
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetPDFContextBounds
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPrintPageRenderer
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.UIKit.valueWithCGRect

/**
 * iOS [InvoicePdfExporter]. Apache FOP cannot run on iOS, so the invoice XML is parsed into an
 * [InvoiceDocument], rendered to HTML by [InvoiceHtmlRenderer] and converted to a PDF with
 * `UIPrintPageRenderer`. The file is stored in app storage and offered through a share sheet.
 */
@OptIn(ExperimentalForeignApi::class)
class IosInvoicePdfExporter : InvoicePdfExporter {

    override val isSupported: Boolean = true

    override suspend fun export(invoiceXml: String, ksefReferenceNumber: String): PdfExportResult =
        withContext(Dispatchers.Main) {
            try {
                val document = InvoiceXmlParser.parse(invoiceXml)
                val html = InvoiceHtmlRenderer.render(document, ksefReferenceNumber)
                val pdfData = renderHtmlToPdf(html)
                val fileUrl = writePdf(pdfData, ksefReferenceNumber)
                presentShareSheet(fileUrl)
                PdfExportResult.Success(fileUrl.lastPathComponent ?: "invoice.pdf")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PdfExportResult.Failure(e.message)
            }
        }

    /** Lays out the HTML on A4 pages and renders them into in-memory PDF data. */
    private fun renderHtmlToPdf(html: String): NSData {
        val renderer = UIPrintPageRenderer()
        renderer.addPrintFormatter(
            UIMarkupTextPrintFormatter(markupText = html),
            startingAtPageAtIndex = 0L,
        )

        val pageRect = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT)
        val printableRect =
            CGRectMake(MARGIN, MARGIN, PAGE_WIDTH - 2 * MARGIN, PAGE_HEIGHT - 2 * MARGIN)
        renderer.setValue(NSValue.valueWithCGRect(pageRect), forKey = "paperRect")
        renderer.setValue(NSValue.valueWithCGRect(printableRect), forKey = "printableRect")

        val pdfData = NSMutableData()
        UIGraphicsBeginPDFContextToData(pdfData, pageRect, null)
        val pageCount = renderer.numberOfPages.toInt()
        renderer.prepareForDrawingPages(NSMakeRange(0.convert(), pageCount.convert()))
        val bounds = UIGraphicsGetPDFContextBounds()
        for (page in 0 until pageCount) {
            UIGraphicsBeginPDFPage()
            renderer.drawPageAtIndex(page.toLong(), inRect = bounds)
        }
        UIGraphicsEndPDFContext()
        return pdfData
    }

    private fun writePdf(data: NSData, ksefReferenceNumber: String): NSURL {
        val fileManager = NSFileManager.defaultManager
        val documents =
            fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull()
                    as? NSURL ?: error("Documents directory is unavailable")
        val directory =
            documents.URLByAppendingPathComponent(INVOICES_DIR, isDirectory = true)
                ?: error("Cannot resolve the invoices directory")
        fileManager.createDirectoryAtURL(
            url = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val base = ksefReferenceNumber.ifBlank { "invoice" }.replace(UNSAFE_FILE_CHARS, "_")
        val fileUrl =
            directory.URLByAppendingPathComponent("invoice_$base.pdf")
                ?: error("Cannot resolve the target file")
        if (!data.writeToURL(fileUrl, atomically = true)) {
            error("Failed to write the PDF file")
        }
        return fileUrl
    }

    /** Best effort: the file is already saved even if no presenting controller is available. */
    private fun presentShareSheet(fileUrl: NSURL) {
        val controller = topViewController() ?: return
        val activityController =
            UIActivityViewController(activityItems = listOf(fileUrl), applicationActivities = null)
        // Required on iPad, where the share sheet is shown as a popover.
        activityController.popoverPresentationController?.apply {
            sourceView = controller.view
            controller.view.bounds.useContents {
                sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
        }
        controller.presentViewController(activityController, animated = true, completion = null)
    }

    /** Resolves the front-most view controller to present the share sheet from. */
    private fun topViewController(): UIViewController? {
        val windows = UIApplication.sharedApplication.windows
        val window =
            windows.firstNotNullOfOrNull { (it as? UIWindow)?.takeIf { w -> w.keyWindow } }
                ?: windows.firstNotNullOfOrNull { it as? UIWindow }
        var controller = window?.rootViewController
        while (true) {
            controller = controller?.presentedViewController ?: break
        }
        return controller
    }

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 32.0
        const val INVOICES_DIR = "invoices"
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}

actual fun defaultInvoicePdfExporter(): InvoicePdfExporter = IosInvoicePdfExporter()

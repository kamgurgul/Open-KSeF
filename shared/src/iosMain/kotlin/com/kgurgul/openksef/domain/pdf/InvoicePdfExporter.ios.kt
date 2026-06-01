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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * iOS [InvoicePdfExporter]. Apache FOP cannot run on iOS, so the invoice XML is parsed into an
 * [InvoiceDocument], rendered to HTML by [InvoiceHtmlRenderer] and converted to a PDF with
 * `UIPrintPageRenderer`. The file is stored in app storage and offered through a share sheet.
 */
@OptIn(ExperimentalForeignApi::class)
class IosInvoicePdfExporter : InvoicePdfExporter {

    override val isSupported: Boolean = true

    override suspend fun export(invoiceXml: String, ksefReferenceNumber: String): PdfExportResult =
        try {
            val document = withContext(Dispatchers.Default) { InvoiceXmlParser.parse(invoiceXml) }
            val html =
                withContext(Dispatchers.Default) {
                    InvoiceHtmlRenderer.render(document, ksefReferenceNumber)
                }
            // WKWebView lays out and rasterizes the page on its own queue, so Main stays free
            // for the spinner and other UI updates.
            val pdfData = renderHtmlToPdf(html)
            val fileUrl =
                withContext(Dispatchers.Default) { writePdf(pdfData, ksefReferenceNumber) }
            withContext(Dispatchers.Main) { presentShareSheet(fileUrl) }
            PdfExportResult.Success(fileUrl.lastPathComponent ?: "invoice.pdf")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PdfExportResult.Failure(e.message)
        }

    /**
     * Renders [html] to PDF data via `WKWebView.createPDF`, which performs layout on WebKit's
     * internal queue and never blocks the main thread.
     */
    private suspend fun renderHtmlToPdf(html: String): NSData =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val frame = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT)
                val webView = WKWebView(frame = frame, configuration = WKWebViewConfiguration())
                val delegate =
                    PdfNavigationDelegate(
                        webView = webView,
                        onResult = { data, error ->
                            when {
                                cont.isCompleted -> Unit
                                data != null -> cont.resume(data)
                                else ->
                                    cont.resumeWithException(
                                        IllegalStateException(
                                            error?.localizedDescription ?: "PDF render failed"
                                        )
                                    )
                            }
                        },
                    )
                webView.navigationDelegate = delegate
                cont.invokeOnCancellation {
                    webView.stopLoading()
                    @Suppress("UNUSED_EXPRESSION") delegate // keep alive until cancellation
                }
                webView.loadHTMLString(html, baseURL = null)
            }
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
        const val INVOICES_DIR = "invoices"
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PdfNavigationDelegate(
    private val webView: WKWebView,
    private val onResult: (NSData?, NSError?) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        webView.createPDFWithConfiguration(null) { data, error -> onResult(data, error) }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        onResult(null, withError)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        onResult(null, withError)
    }
}

actual fun defaultInvoicePdfExporter(): InvoicePdfExporter = IosInvoicePdfExporter()

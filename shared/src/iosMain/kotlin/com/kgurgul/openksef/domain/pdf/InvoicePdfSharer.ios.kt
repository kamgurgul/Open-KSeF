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

import com.kgurgul.openksef.common.toNSData
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController

/**
 * iOS share/download for a rendered invoice PDF. The file is written to app storage and offered
 * through the system share sheet (which includes "Save to Files").
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun shareInvoicePdf(
    pdfBytes: ByteArray,
    ksefReferenceNumber: String,
): PdfExportResult =
    try {
        val data = pdfBytes.toNSData()
        val fileUrl = withContext(Dispatchers.Default) { writePdf(data, ksefReferenceNumber) }
        withContext(Dispatchers.Main) { presentShareSheet(fileUrl) }
        PdfExportResult.Success(fileUrl.lastPathComponent ?: "invoice.pdf")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PdfExportResult.Failure(e.message)
    }

@OptIn(ExperimentalForeignApi::class)
private fun writePdf(data: NSData, ksefReferenceNumber: String): NSURL {
    val fileManager = NSFileManager.defaultManager
    val documents =
        fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull() as? NSURL
            ?: error("Documents directory is unavailable")
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

@OptIn(ExperimentalForeignApi::class)
private fun presentShareSheet(fileUrl: NSURL) {
    val controller = topViewController() ?: return
    val activityController =
        UIActivityViewController(activityItems = listOf(fileUrl), applicationActivities = null)
    activityController.popoverPresentationController?.apply {
        sourceView = controller.view
        controller.view.bounds.useContents {
            sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
        }
    }
    controller.presentViewController(activityController, animated = true, completion = null)
}

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

private const val INVOICES_DIR = "invoices"
private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")

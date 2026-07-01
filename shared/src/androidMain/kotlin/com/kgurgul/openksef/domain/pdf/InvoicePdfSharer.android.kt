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
 * Android share/download for a rendered invoice PDF. The file is written to app-private storage and
 * offered through a share chooser so the user can save it or send it elsewhere.
 */
actual suspend fun shareInvoicePdf(
    pdfBytes: ByteArray,
    ksefReferenceNumber: String,
): PdfExportResult {
    val context = KoinPlatform.getKoin().get<Context>()
    return try {
        val file = withContext(Dispatchers.IO) { writePdf(context, pdfBytes, ksefReferenceNumber) }
        withContext(Dispatchers.Main) { shareFile(context, file) }
        PdfExportResult.Success(file.name)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PdfExportResult.Failure(e.message)
    }
}

private fun writePdf(context: Context, bytes: ByteArray, ksefReferenceNumber: String): File {
    val directory = File(context.filesDir, INVOICES_DIR).apply { mkdirs() }
    val base = ksefReferenceNumber.ifBlank { "invoice" }.replace(UNSAFE_FILE_CHARS, "_")
    return File(directory, "invoice_$base.pdf").apply { writeBytes(bytes) }
}

private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser =
        Intent.createChooser(sendIntent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(chooser)
}

private const val INVOICES_DIR = "invoices"
private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")

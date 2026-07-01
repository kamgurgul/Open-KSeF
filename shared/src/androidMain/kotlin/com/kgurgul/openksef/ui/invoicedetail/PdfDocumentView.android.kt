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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun PdfDocumentView(pdfBytes: ByteArray, modifier: Modifier) {
    val context = LocalContext.current
    val pages by
    produceState(initialValue = emptyList<ImageBitmap>(), pdfBytes) {
        value = withContext(Dispatchers.IO) { renderPdfToImages(context, pdfBytes) }
    }

    LazyColumn(modifier = modifier) {
        items(pages) { page ->
            Image(
                bitmap = page,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}

/** Rasterizes every page of [pdfBytes] into an [ImageBitmap] using the platform [PdfRenderer]. */
private fun renderPdfToImages(context: Context, pdfBytes: ByteArray): List<ImageBitmap> {
    val file =
        File.createTempFile("preview", ".pdf", context.cacheDir).apply { writeBytes(pdfBytes) }
    return try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = TARGET_WIDTH_PX.toFloat() / page.width
                        val width = TARGET_WIDTH_PX
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap.asImageBitmap()
                    }
                }
            }
        }
    } finally {
        file.delete()
    }
}

private const val TARGET_WIDTH_PX = 1240

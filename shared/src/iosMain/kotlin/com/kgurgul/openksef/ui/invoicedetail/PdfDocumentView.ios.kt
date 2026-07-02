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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.kgurgul.openksef.common.toNSData
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView

@Composable
actual fun PdfDocumentView(pdfBytes: ByteArray, modifier: Modifier) {
    val pdfDocument = remember(pdfBytes) { PDFDocument(data = pdfBytes.toNSData()) }
    UIKitView(
        factory = {
            PDFView().apply {
                autoScales = true
                document = pdfDocument
            }
        },
        modifier = modifier,
        update = { view -> view.document = pdfDocument },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true
        )
    )
}

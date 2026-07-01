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

/**
 * Persists a rendered PDF and offers it to the user for download/sharing. Injected into the preview
 * ViewModel so it can be substituted in tests.
 */
fun interface InvoicePdfSharer {

    suspend fun share(pdfBytes: ByteArray, ksefReferenceNumber: String): PdfExportResult
}

/** Returns the platform-default [InvoicePdfSharer] (delegates to [shareInvoicePdf]). */
fun defaultInvoicePdfSharer(): InvoicePdfSharer =
    InvoicePdfSharer { pdfBytes, ksefReferenceNumber ->
        shareInvoicePdf(
            pdfBytes,
            ksefReferenceNumber
        )
    }

/**
 * Persists [pdfBytes] and offers them to the user for download/sharing (a share sheet on iOS, a
 * viewer/share intent on Android). Implemented on Android and iOS; the desktop stub reports failure
 * because the preview flow is mobile-only.
 */
expect suspend fun shareInvoicePdf(
    pdfBytes: ByteArray,
    ksefReferenceNumber: String,
): PdfExportResult

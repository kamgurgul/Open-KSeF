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
 * Renders a KSeF invoice into a PDF file.
 *
 * The implementation is platform specific:
 * - desktop uses the `ksef-fop` library (Apache FOP) to reproduce the official KSeF visualization,
 * - Android draws the parsed [InvoiceDocument] onto a native `PdfDocument` canvas,
 * - iOS is currently unsupported ([isSupported] is `false`).
 */
interface InvoicePdfExporter {

    /** Whether PDF export is available on the current platform. */
    val isSupported: Boolean

    /**
     * Generates a PDF for the given invoice and hands it to the platform (a save dialog on desktop,
     * a viewer intent on Android). Must be called off the main thread by the caller's dispatcher;
     * implementations switch dispatchers internally where needed.
     */
    suspend fun export(invoiceXml: String, ksefReferenceNumber: String): PdfExportResult
}

/** Outcome of an [InvoicePdfExporter.export] call. */
sealed interface PdfExportResult {

    /** The PDF was generated and saved/opened; [location] is a human-readable destination. */
    data class Success(val location: String) : PdfExportResult

    /** The user dismissed the save dialog. No error should be surfaced. */
    data object Cancelled : PdfExportResult

    /** Generation failed; [reason] carries an optional diagnostic message. */
    data class Failure(val reason: String?) : PdfExportResult
}

/** Returns the platform-default [InvoicePdfExporter]. */
expect fun defaultInvoicePdfExporter(): InvoicePdfExporter

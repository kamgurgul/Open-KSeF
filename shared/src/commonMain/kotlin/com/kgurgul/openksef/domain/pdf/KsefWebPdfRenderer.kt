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

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import openksef.shared.generated.resources.Res

/**
 * Renders a KSeF invoice into PDF bytes using the official Ministry of Finance visualization
 * library ([CIRFMF/ksef-pdf-generator]) executed inside a headless web view.
 *
 * Unlike [InvoicePdfExporter] (which saves/opens the file directly), this returns the raw bytes so
 * the caller can both preview and download them. It is available on Android and iOS; desktop keeps
 * the Apache FOP based [InvoicePdfExporter] and reports [isSupported] as `false` here.
 */
interface KsefWebPdfRenderer {

    /** Whether the official web-based renderer is available on the current platform. */
    val isSupported: Boolean

    /** Generates a PDF for [invoiceXml] and returns its raw bytes. Throws on failure. */
    suspend fun render(invoiceXml: String, ksefReferenceNumber: String): ByteArray
}

/** Returns the platform-default [KsefWebPdfRenderer]. */
expect fun defaultKsefWebPdfRenderer(): KsefWebPdfRenderer

/**
 * Builds the self-contained HTML document that hosts the official JS bundle inside a web view.
 *
 * The bundle exposes the UMD global `window["ksef-fe-invoice-converter"]`. The injected
 * `window.__ksefGenerate(xml, nrKSeF, qrCode)` bridge calls `generateInvoice(...)` and forwards the
 * resulting base64 PDF (or an error) to platform-defined `window.__ksefResult` /
 * `window.__ksefError` callbacks. Each platform installs those two callbacks before invoking
 * `__ksefGenerate`.
 */
internal object KsefPdfHtml {

    private const val BUNDLE_PATH = "files/ksefpdf/ksef-fe-invoice-converter.umd.js"

    private var cached: String? = null

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun build(): String {
        cached?.let {
            return it
        }
        val js = Res.readBytes(BUNDLE_PATH).decodeToString()
        // Guard against an accidental `</script>` inside a JS string literal closing the tag early.
        val safeJs = js.replace("</script", "<\\/script")
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("</head><body>")
            append("<script>").append(safeJs).append("</script>")
            append("<script>").append(BRIDGE_JS).append("</script>")
            append("</body></html>")
        }
        cached = html
        return html
    }

    private const val BRIDGE_JS =
        """
        window.__ksefGenerate = function (xml, nrKSeF, qrCode) {
            try {
                var lib = window["ksef-fe-invoice-converter"];
                if (!lib || typeof lib.generateInvoice !== "function") {
                    window.__ksefError("KSeF visualization library failed to load");
                    return;
                }
                var file = new File([xml], "invoice.xml", { type: "application/xml" });
                var data = { nrKSeF: nrKSeF || "" };
                if (qrCode) { data.qrCode = qrCode; }
                lib.generateInvoice(file, data, "base64")
                    .then(function (b64) { window.__ksefResult(b64); })
                    .catch(function (e) { window.__ksefError(String((e && e.message) || e)); });
            } catch (e) {
                window.__ksefError(String((e && e.message) || e));
            }
        };
        """
}

/** Decodes the base64 PDF string returned by the JS bridge into raw bytes. */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodePdfBase64(base64: String): ByteArray =
    Base64.decode(base64.substringAfter("base64,").trim())

/** Encodes [value] as a JS string literal (quoted and escaped) safe to inline into evaluated JS. */
internal fun jsStringLiteral(value: String): String =
    Json.encodeToString(String.serializer(), value)

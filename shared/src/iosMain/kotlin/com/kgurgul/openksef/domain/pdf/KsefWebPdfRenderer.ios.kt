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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * iOS [KsefWebPdfRenderer]. Runs the official Ministry of Finance JS bundle inside an offscreen
 * [WKWebView]: the page loads the bundle, a JS bridge calls `generateInvoice(...)` and posts the
 * resulting base64 PDF back through a [WKScriptMessageHandlerProtocol].
 */
@OptIn(ExperimentalForeignApi::class)
class IosKsefWebPdfRenderer : KsefWebPdfRenderer {

    override val isSupported: Boolean = true

    override suspend fun render(invoiceXml: String, ksefReferenceNumber: String): ByteArray {
        val html = KsefPdfHtml.build()
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var settled = false
                val configuration = WKWebViewConfiguration()
                val controller = configuration.userContentController
                val frame = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT)
                val webView = WKWebView(frame = frame, configuration = configuration)

                fun teardown() {
                    webView.stopLoading()
                    controller.removeScriptMessageHandlerForName(BRIDGE_NAME)
                }

                val handler = BridgeMessageHandler { type, data ->
                    if (settled) return@BridgeMessageHandler
                    settled = true
                    teardown()
                    if (type == "result") {
                        val bytes = runCatching { decodePdfBase64(data) }
                        cont.resumeWith(bytes)
                    } else {
                        cont.resumeWith(
                            Result.failure(
                                IllegalStateException(data.ifBlank { "PDF render failed" })
                            )
                        )
                    }
                }
                controller.addScriptMessageHandler(handler, BRIDGE_NAME)

                val delegate =
                    GenerateNavigationDelegate(invoiceXml, ksefReferenceNumber) { onLoadError ->
                        if (settled) return@GenerateNavigationDelegate
                        settled = true
                        teardown()
                        cont.resumeWith(Result.failure(IllegalStateException(onLoadError)))
                    }
                webView.navigationDelegate = delegate

                cont.invokeOnCancellation {
                    if (!settled) {
                        settled = true
                        teardown()
                    }
                    // navigationDelegate is weak; keep the delegate alive until the
                    // continuation settles or WebKit never calls back.
                    @Suppress("UNUSED_EXPRESSION") delegate
                }

                webView.loadHTMLString(html, baseURL = null)
            }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "ksefBridge"
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
    }
}

/** Receives `{ type, data }` payloads posted by the JS bridge. */
@OptIn(ExperimentalForeignApi::class)
private class BridgeMessageHandler(private val onMessage: (type: String, data: String) -> Unit) :
    NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
        val type = body["type"] as? String ?: return
        val data = body["data"] as? String ?: ""
        onMessage(type, data)
    }
}

/** Kicks off generation once the bundle page has loaded; reports navigation failures. */
@OptIn(ExperimentalForeignApi::class)
private class GenerateNavigationDelegate(
    private val invoiceXml: String,
    private val ksefReferenceNumber: String,
    private val onLoadError: (String) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        webView.evaluateJavaScript(GLUE_JS, null)
        val script =
            "window.__ksefGenerate(" +
                "${jsStringLiteral(invoiceXml)}," +
                "${jsStringLiteral(ksefReferenceNumber)},null);"
        webView.evaluateJavaScript(script, null)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: platform.Foundation.NSError,
    ) {
        onLoadError(withError.localizedDescription)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: platform.Foundation.NSError,
    ) {
        onLoadError(withError.localizedDescription)
    }
}

/** JS installed after page load so the bundle can post results back through the native bridge. */
private const val GLUE_JS =
    "window.__ksefResult=function(b){" +
        "window.webkit.messageHandlers.ksefBridge.postMessage({type:'result',data:b});};" +
        "window.__ksefError=function(m){" +
        "window.webkit.messageHandlers.ksefBridge.postMessage({type:'error',data:m});};"

actual fun defaultKsefWebPdfRenderer(): KsefWebPdfRenderer = IosKsefWebPdfRenderer()

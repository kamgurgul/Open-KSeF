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

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/**
 * Android [KsefWebPdfRenderer]. Runs the official Ministry of Finance JS bundle inside an offscreen
 * [WebView]: the page loads the bundle, a JS bridge calls `generateInvoice(...)` and posts the
 * resulting base64 PDF back through [JavascriptInterface].
 */
class AndroidKsefWebPdfRenderer(private val context: Context) : KsefWebPdfRenderer {

    override val isSupported: Boolean = true

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun render(invoiceXml: String, ksefReferenceNumber: String): ByteArray {
        val html = KsefPdfHtml.build()
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val resumed = AtomicBoolean(false)
                lateinit var webView: WebView

                fun finish(action: WebView.() -> Unit) {
                    // Tear the WebView down on the main thread once we are done with it.
                    webView.post {
                        webView.action()
                        webView.destroy()
                    }
                }

                val bridge =
                    object {
                        @JavascriptInterface
                        fun onResult(base64: String) {
                            if (!resumed.compareAndSet(false, true)) return
                            val bytes = runCatching {
                                decodePdfBase64(base64)
                            }
                                .getOrElse {
                                    finish {}
                                    cont.resumeWith(Result.failure(it))
                                    return
                                }
                            finish {}
                            cont.resumeWith(Result.success(bytes))
                        }

                        @JavascriptInterface
                        fun onError(message: String) {
                            if (!resumed.compareAndSet(false, true)) return
                            finish {}
                            cont.resumeWith(Result.failure(IllegalStateException(message)))
                        }
                    }

                webView =
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        addJavascriptInterface(bridge, BRIDGE_NAME)
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    view.evaluateJavascript(GLUE_JS, null)
                                    val script =
                                        "window.__ksefGenerate(" +
                                            "${jsStringLiteral(invoiceXml)}," +
                                            "${jsStringLiteral(ksefReferenceNumber)},null);"
                                    view.evaluateJavascript(script, null)
                                }
                            }
                    }

                cont.invokeOnCancellation {
                    if (resumed.compareAndSet(false, true)) finish { stopLoading() }
                }

                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "AndroidKsefBridge"
        const val GLUE_JS =
            "window.__ksefResult=function(b){$BRIDGE_NAME.onResult(b);};" +
                "window.__ksefError=function(m){$BRIDGE_NAME.onError(m);};"
    }
}

actual fun defaultKsefWebPdfRenderer(): KsefWebPdfRenderer =
    AndroidKsefWebPdfRenderer(KoinPlatform.getKoin().get<Context>())

package com.example.myapplication.local.monitoreo.mapa

import android.app.AlertDialog
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun MapaMonitoreoWebViewSeguro(
    modifier: Modifier = Modifier,
    htmlMapa: String,
    ubicacionUsuario: Pair<Double, Double>?,
    internetDisponible: Boolean,
    onInternetDisponibleChange: (Boolean) -> Unit,
    onPuntoValidoClick: (Long) -> Unit
) {
    AndroidView<View>(
        modifier = modifier,
        factory = { ctx ->
            try {
                WebView(ctx).apply {
                    webViewClient = WebViewClient()

                    addJavascriptInterface(
                        MapaBridge { idTargetPoint ->
                            onPuntoValidoClick(idTargetPoint)
                        },
                        "Android"
                    )

                    webChromeClient = object : WebChromeClient() {
                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            AlertDialog.Builder(ctx)
                                .setMessage(message ?: "")
                                .setPositiveButton("Aceptar") { dialog, _ ->
                                    dialog.dismiss()
                                    result?.confirm()
                                }
                                .setOnCancelListener {
                                    result?.cancel()
                                }
                                .show()

                            return true
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            android.util.Log.d(
                                "MapaWebView",
                                consoleMessage?.message() ?: "Mensaje vacío"
                            )
                            return true
                        }
                    }

                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = settings.userAgentString + " AndroidWebViewMonitoreo"

                    tag = htmlMapa

                    loadDataWithBaseURL(
                        "file:///android_asset/",
                        htmlMapa,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            } catch (e: Throwable) {
                TextView(ctx).apply {
                    text = "No se pudo abrir el mapa.\n\nDetalle: ${e.javaClass.simpleName}: ${e.message}"
                    setTextColor(android.graphics.Color.RED)
                    setPadding(24, 24, 24, 24)
                }
            }
        },
        update = { view ->
            if (view is WebView) {
                if (view.tag != htmlMapa) {
                    view.tag = htmlMapa
                    view.loadDataWithBaseURL(
                        "file:///android_asset/",
                        htmlMapa,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }

                ubicacionUsuario?.let { location ->
                    val js = """
                        if (window.updateUserLocation) {
                            window.updateUserLocation(${location.first}, ${location.second});
                        }
                    """.trimIndent()

                    view.postDelayed({
                        view.evaluateJavascript(js, null)
                    }, 300)
                }
            }
        }
    )
}

private class MapaBridge(
    private val onPuntoSeleccionado: (Long) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPuntoSeleccionado(idTargetPoint: String) {
        val id = idTargetPoint.toLongOrNull() ?: return

        mainHandler.post {
            onPuntoSeleccionado(id)
        }
    }
}

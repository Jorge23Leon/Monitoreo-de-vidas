package com.example.myapplication.local.monitoreo.mapa

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun MapaMonitoreoWebViewSeguro(
    modifier: Modifier = Modifier,
    htmlMapa: String,
    ubicacionUsuario: Pair<Double, Double>?,
    puntoLibreSeleccionado: Pair<Double, Double>?,
    internetDisponible: Boolean,
    onInternetDisponibleChange: (Boolean) -> Unit,
    onPuntoLibreSeleccionado: (Double, Double) -> Unit
) {
    AndroidView<View>(
        modifier = modifier,
        factory = { ctx ->
            try {
                WebView(ctx).apply {
                    webViewClient = MapaTileCacheWebViewClient(
                        context = ctx,
                        internetDisponible = internetDisponible
                    )

                    addJavascriptInterface(
                        MapaBridge { lat, lon ->
                            onPuntoLibreSeleccionado(lat, lon)
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

                    /*
                     * Aunque el WebView tiene cache propia, no es confiable para trabajar offline.
                     * Por eso interceptamos los tiles satelitales y los guardamos en filesDir/map_tiles.
                     */
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
                /*
                 * Se vuelve a asignar para que cuando cambie internetDisponible:
                 * - con internet: descargue y guarde tiles
                 * - sin internet: lea los tiles guardados
                 */
                view.webViewClient = MapaTileCacheWebViewClient(
                    context = view.context,
                    internetDisponible = internetDisponible
                )

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
                    }, 250)
                }

                val selectedJs = puntoLibreSeleccionado?.let { punto ->
                    """
                        if (window.setSelectedFreePoint) {
                            window.setSelectedFreePoint(${punto.first}, ${punto.second});
                        }
                    """.trimIndent()
                } ?: """
                    if (window.clearSelectedFreePoint) {
                        window.clearSelectedFreePoint();
                    }
                """.trimIndent()

                view.postDelayed({
                    view.evaluateJavascript(selectedJs, null)
                }, 250)
            }
        }
    )
}

/**
 * Cache real para tiles satelitales del mapa.
 *
 * Qué hace:
 * 1. Si el tile ya existe en filesDir/map_tiles, lo sirve desde local.
 * 2. Si no existe y hay internet, lo descarga, lo guarda y lo entrega al WebView.
 * 3. Si no existe y no hay internet, entrega un tile transparente.
 *
 * Así el mapa se ve satelital offline siempre que esa zona/zoom ya se haya visto antes con internet.
 */
private class MapaTileCacheWebViewClient(
    private val context: Context,
    private val internetDisponible: Boolean
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

        if (!esTileSatelitalEsri(url)) {
            return super.shouldInterceptRequest(view, request)
        }

        val archivoTile = archivoCacheParaTile(url)
        val archivoMime = File(archivoTile.absolutePath + ".mime")

        if (archivoTile.exists() && archivoTile.length() > 0L) {
            val mime = archivoMime
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.takeIf { it.startsWith("image/") }
                ?: "image/jpeg"

            return WebResourceResponse(
                mime,
                null,
                FileInputStream(archivoTile)
            )
        }

        if (!internetDisponible) {
            return tileTransparente()
        }

        return try {
            descargarTile(url, archivoTile, archivoMime)
        } catch (e: Throwable) {
            android.util.Log.e("MapaTileCache", "No se pudo cachear tile: $url", e)
            tileTransparente()
        }
    }

    private fun esTileSatelitalEsri(url: String): Boolean {
        return url.contains(
            "server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
            ignoreCase = true
        )
    }

    private fun archivoCacheParaTile(url: String): File {
        val despuesDeTile = url.substringAfter("/tile/", missingDelimiterValue = "")
        val partes = despuesDeTile
            .substringBefore("?")
            .split("/")
            .filter { it.isNotBlank() }

        val z = partes.getOrNull(0)?.soloSeguro() ?: "z"
        val y = partes.getOrNull(1)?.soloSeguro() ?: "y"
        val x = partes.getOrNull(2)?.soloSeguro() ?: "x"

        return File(context.filesDir, "map_tiles/esri/$z/$y/$x.tile")
    }

    private fun descargarTile(
        url: String,
        archivoTile: File,
        archivoMime: File
    ): WebResourceResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 10000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "Android CIAgro Offline Tile Cache")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                return tileTransparente()
            }

            val mime = connection.contentType
                ?.substringBefore(";")
                ?.trim()
                ?.takeIf { it.startsWith("image/") }
                ?: "image/jpeg"

            archivoTile.parentFile?.mkdirs()

            connection.inputStream.use { input ->
                archivoTile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            archivoMime.writeText(mime)

            return WebResourceResponse(
                mime,
                null,
                FileInputStream(archivoTile)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun tileTransparente(): WebResourceResponse {
        val bytes = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lCjQ9wAAAABJRU5ErkJggg==",
            Base64.DEFAULT
        )

        return WebResourceResponse(
            "image/png",
            null,
            ByteArrayInputStream(bytes)
        )
    }

    private fun String.soloSeguro(): String {
        return replace(Regex("[^0-9A-Za-z_-]"), "_")
    }
}

private class MapaBridge(
    private val onPuntoLibreSeleccionado: (Double, Double) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPuntoLibreSeleccionado(lat: String, lon: String) {
        val latDouble = lat.toDoubleOrNull() ?: return
        val lonDouble = lon.toDoubleOrNull() ?: return

        mainHandler.post {
            onPuntoLibreSeleccionado(latDouble, lonDouble)
        }
    }
}

package com.example.myapplication.local.monitoreo.reporte

import java.util.Locale
import java.io.File
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.local.common.ImageCache
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.monitoreo.severidad.calcularSeveridadPorPunto
import com.example.myapplication.local.monitoreo.severidad.limpiarMetadataRangosSeveridad
import org.json.JSONArray
import org.json.JSONObject


private data class FotoMapaDetalleUi(
    val titulo: String,
    val photo: String
)

private class ReporteMapaJsBridge(
    private val onAbrirFoto: (String, String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun abrirFoto(titulo: String?, photo: String?) {
        val tituloLimpio = titulo
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Evidencia fotográfica"

        val photoLimpia = photo
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return

        mainHandler.post {
            onAbrirFoto(tituloLimpio, photoLimpia)
        }
    }
}

@Composable
private fun FotoMapaDetalleDialog(
    data: FotoMapaDetalleUi,
    onCerrar: () -> Unit
) {
    val context = LocalContext.current
    var cargando by remember(data.photo) { mutableStateOf(true) }
    var imagen by remember(data.photo) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(data.photo) {
        cargando = true
        imagen = runCatching {
            ImageCache.cargarBitmap(
                context = context.applicationContext,
                photo = data.photo
            )
        }.getOrNull()
        cargando = false
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Text(
                text = data.titulo,
                fontWeight = FontWeight.Black,
                color = Color(0xFF123D1F)
            )
        },
        text = {
            Column(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    cargando -> {
                        CircularProgressIndicator(color = Color(0xFF1B5E20))
                        Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                        Text(
                            text = "Cargando evidencia...",
                            color = Color(0xFF1B5E20),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    imagen != null -> {
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFF4F4F4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = imagen!!,
                                contentDescription = "Evidencia fotográfica",
                                modifier = androidx.compose.ui.Modifier
                                    .fillMaxWidth()
                                    .height(380.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = "No se pudo abrir la evidencia. Revisa internet, sesión o que la foto exista en el servidor.",
                            color = Color(0xFFB3261E),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCerrar) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
internal fun MapaReporteWebViewUi(
    htmlMapa: String,
    modifier: Modifier = Modifier
) {
    var fotoMapaDetalle by remember { mutableStateOf<FotoMapaDetalleUi?>(null) }

    AndroidView<View>(
        modifier = modifier,
        factory = { ctx ->
            try {
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.postDelayed({
                                view.evaluateJavascript(
                                    "if (window.ajustarMapaReporte) { window.ajustarMapaReporte(); } else if (window.reporteMap) { window.reporteMap.invalidateSize(true); }",
                                    null
                                )
                            }, 350)
                        }
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    addJavascriptInterface(
                        ReporteMapaJsBridge { titulo, photo ->
                            fotoMapaDetalle = FotoMapaDetalleUi(titulo, photo)
                        },
                        "AndroidReporte"
                    )

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = false
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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
                    text = "No se pudo abrir el mapa del reporte. Revisa Android System WebView y los assets de Leaflet.\n\nDetalle: ${e.javaClass.simpleName}: ${e.message}"
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
                } else {
                    view.postDelayed({
                        view.evaluateJavascript(
                            "if (window.ajustarMapaReporte) { window.ajustarMapaReporte(); } else if (window.reporteMap) { window.reporteMap.invalidateSize(true); }",
                            null
                        )
                    }, 350)
                }
            }
        }
    )

    fotoMapaDetalle?.let { data ->
        FotoMapaDetalleDialog(
            data = data,
            onCerrar = { fotoMapaDetalle = null }
        )
    }
}

internal fun crearHtmlMapaReporteUi(
    vertices: List<LocalPlotVertexEntity>,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    catalogo: List<LocalPhytosanitaryCatalogEntity>,
    pantallaCompleta: Boolean = false
): String {
    val catalogoMap = catalogo.associateBy { it.idPhytosanitary }

    val verticesJson = JSONArray().apply {
        vertices
            .sortedBy { it.level }
            .forEach { vertex ->
                put(JSONObject().apply {
                    put("level", vertex.level)
                    put("lat", vertex.lat)
                    put("lon", vertex.lon)
                })
            }
    }.toString()

    val puntosOrdenados = puntos.sortedBy { it.idTargetPoint }

    val gruposPorCoordenada = puntosOrdenados.groupBy { punto ->
        String.format(
            Locale.US,
            "%.6f,%.6f",
            punto.lat,
            punto.lon
        )
    }

    val puntosJson = JSONArray().apply {
        gruposPorCoordenada.values.forEachIndexed { index, puntosMismaCoordenada ->

            val puntoBase = puntosMismaCoordenada.first()
            val idsMismaCoordenada = puntosMismaCoordenada
                .map { it.idTargetPoint }
                .toSet()

            val capturasMismaCoordenada = checkpoints.filter { checkpoint ->
                checkpoint.idTargetPoint in idsMismaCoordenada
            }

            val severidadPunto = calcularSeveridadPorPunto(
                checkpointsPunto = capturasMismaCoordenada,
                catalogoPorId = catalogoMap
            )

            val capturasArray = JSONArray().apply {
                severidadPunto.fitos.forEach { fito ->
                    put(JSONObject().apply {
                        val esEnfermedad = esEnfermedadReporte(fito.tipo)
                        val fasesResumen = fito.etapasResumen
                            ?.trim()
                            .orEmpty()

                        val fase = when {
                            !esEnfermedad -> fasesResumen.ifBlank { "-" }
                            fito.cantidadTotal <= 0 -> "No presente"
                            fasesResumen.isBlank() || fasesResumen == "-" -> "Presente"
                            else -> "Presente / $fasesResumen"
                        }

                        val capturasFito = capturasMismaCoordenada.filter { checkpoint ->
                            checkpoint.idPhytosanitary == fito.idPhytosanitary
                        }

                        val comentario = capturasFito
                            .mapNotNull { checkpoint ->
                                limpiarMetadataRangosSeveridad(checkpoint.notes)
                                    .trim()
                                    .takeIf { it.isNotBlank() }
                            }
                            .distinct()
                            .joinToString("\n\n")

                        val photoSrc = capturasFito
                            .asSequence()
                            .mapNotNull { checkpoint ->
                                val local = checkpoint.photoLocalPath
                                    ?.trim()
                                    ?.takeIf { ruta ->
                                        ruta.isNotBlank() && (
                                                ruta.startsWith("content://", ignoreCase = true) ||
                                                        ruta.startsWith("file://", ignoreCase = true) ||
                                                        ruta.startsWith("http://", ignoreCase = true) ||
                                                        ruta.startsWith("https://", ignoreCase = true) ||
                                                        runCatching {
                                                            val archivo = File(ruta)
                                                            archivo.exists() && archivo.length() > 0L
                                                        }.getOrDefault(false)
                                                )
                                    }

                                local ?: checkpoint.photoUrl
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            }
                            .firstOrNull()

                        put("idPhytosanitary", fito.idPhytosanitary)
                        put("nombre", fito.nombre)
                        put("tipo", textoTipoCatalogo(fito.tipo))
                        put("fase", fase)
                        put("cantidad", fito.cantidadTotal)
                        put("fecha", formatearFechaOpcionalReporteUi(fito.fechaUltimaCaptura))
                        put("severidad", fito.nivel.etiqueta)
                        put("color", fito.nivel.colorHex)
                        put("comentario", comentario)
                        put("tieneComentario", comentario.isNotBlank())
                        put("photoSrc", photoSrc.orEmpty())
                        put("tieneFoto", !photoSrc.isNullOrBlank())
                    })
                }
            }

            val todosCancelados = puntosMismaCoordenada.all { punto ->
                punto.status.equals("cancelled", true) ||
                        punto.status.equals("cancelado", true)
            }

            val statusFinal = when {
                capturasMismaCoordenada.isNotEmpty() -> "completed"
                todosCancelados -> "cancelled"
                else -> "not_monitored"
            }

            put(JSONObject().apply {
                put("numero", index + 1)
                put("id", puntoBase.idTargetPoint)
                put("lat", puntoBase.lat)
                put("lon", puntoBase.lon)
                put("radius", puntoBase.radiusM)
                put("status", statusFinal)
                put("severity", severidadPunto.nivelFinal.name.lowercase())
                put("severityLabel", severidadPunto.nivelFinal.etiqueta)
                put("severityColor", severidadPunto.nivelFinal.colorHex)
                put("totalCantidad", severidadPunto.totalCantidadPunto)
                put("capturas", capturasArray)
            })
        }
    }.toString()

    val radioBordeMapa = if (pantallaCompleta) "0px" else "18px"
    val minHeightMapa = if (pantallaCompleta) "100%" else "420px"
    val pantallaCompletaJs = if (pantallaCompleta) "true" else "false"

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes" />
            <link rel="stylesheet" href="leaflet/leaflet.css" />
            <style>
                html, body {
                    position: fixed;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    min-height: $minHeightMapa;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    font-family: Arial, sans-serif;
                    background: #edf5e8;
                    touch-action: manipulation;
                }

                #map {
                    display: block;
                    position: absolute;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    min-height: $minHeightMapa;
                    border-radius: $radioBordeMapa;
                    overflow: hidden;
                    background:
                        linear-gradient(135deg, rgba(123,179,66,0.20) 25%, transparent 25%) -16px 0,
                        linear-gradient(225deg, rgba(123,179,66,0.20) 25%, transparent 25%) -16px 0,
                        linear-gradient(315deg, rgba(123,179,66,0.20) 25%, transparent 25%),
                        linear-gradient(45deg, rgba(123,179,66,0.20) 25%, transparent 25%);
                    background-size: 32px 32px;
                    background-color: #dfe8d1;
                }

                .leaflet-container {
                    width: 100% !important;
                    height: 100% !important;
                    min-height: $minHeightMapa !important;
                    background: #dfe8d1;
                }

                .legend {
                    background: rgba(255,255,255,0.95);
                    padding: 9px 11px;
                    border-radius: 12px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.25);
                    font-size: 12px;
                    line-height: 19px;
                    color: #222;
                    max-width: 245px;
                    box-sizing: border-box;
                }

                .legend-title {
                    font-weight: bold;
                    margin-bottom: 4px;
                    color: #123D1F;
                }

                .dot {
                    height: 10px;
                    width: 10px;
                    border-radius: 50%;
                    display: inline-block;
                    margin-right: 6px;
                }

               

                .no-data-box {
                    background: rgba(255,255,255,0.96);
                    color: #5F6F64;
                    padding: 10px 12px;
                    border-radius: 12px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.20);
                    font-size: 12px;
                    line-height: 17px;
                    max-width: 240px;
                }

                .popup-title {
                    color: #123D1F;
                    font-weight: bold;
                    font-size: 14px;
                    margin-bottom: 4px;
                }

                .popup-row {
                    font-size: 12px;
                    margin-top: 2px;
                }

                .popup-captura {
                    margin-top: 6px;
                    padding-top: 5px;
                    border-top: 1px solid #e0e0e0;
                    font-size: 12px;
                }


                .full-map-pill {
                    background: rgba(18,61,31,0.86);
                    color: white;
                    padding: 9px 12px;
                    border-radius: 999px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.22);
                    font-size: 12px;
                    font-weight: bold;
                    max-width: 84vw;
                    white-space: nowrap;
                }

                .leaflet-control-zoom a {
                    font-size: 22px;
                    font-weight: bold;
                    color: #111;
                }

                .selected-marker-ring {
                    filter: drop-shadow(0 0 7px rgba(255,255,255,0.95));
                }

                .point-sheet {
                    position: fixed;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    z-index: 999999;
                    background: rgba(255,255,255,0.985);
                    border-radius: 24px 24px 0 0;
                    box-shadow: 0 -8px 28px rgba(0,0,0,0.25);
                    padding: 10px 14px 88px;
                    max-height: 76%;
                    overflow-y: auto;
                    display: none;
                    box-sizing: border-box;
                    pointer-events: auto;
                    -webkit-overflow-scrolling: touch;
                }

                .point-sheet.active {
                    display: block;
                }

                .sheet-handle {
                    width: 46px;
                    height: 5px;
                    border-radius: 10px;
                    background: #D0D0D0;
                    margin: 0 auto 8px;
                }

                .sheet-header {
                    display: flex;
                    align-items: flex-start;
                    justify-content: space-between;
                    gap: 10px;
                    margin-bottom: 6px;
                }

                .sheet-title {
                    color: #111;
                    font-size: 22px;
                    font-weight: 800;
                    line-height: 1.1;
                }

                .sheet-badges {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 7px;
                    margin-top: 7px;
                }

                .badge {
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    border-radius: 999px;
                    padding: 6px 10px;
                    font-size: 12px;
                    font-weight: 800;
                }

                .badge-red {
                    background: #FDE8E8;
                    color: #C62828;
                }

                .badge-green {
                    background: #E8F5E9;
                    color: #1B5E20;
                }

                .badge-orange {
                    background: #FFF3E0;
                    color: #C96A00;
                }

                .sheet-close {
                    border: none;
                    background: #F2F5F1;
                    color: #123D1F;
                    border-radius: 999px;
                    padding: 7px 10px;
                    font-size: 12px;
                    font-weight: 800;
                }

                .sheet-text {
                    color: #34403A;
                    font-size: 13px;
                    line-height: 18px;
                    margin: 8px 0;
                }

                .sheet-grid {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 8px;
                    border-top: 1px solid #E2E8DF;
                    padding-top: 9px;
                    margin-top: 10px;
                }

                .sheet-field {
                    background: #F7FAF6;
                    border-radius: 14px;
                    padding: 9px;
                    min-height: 48px;
                }

                .sheet-field-wide {
                    grid-column: 1 / -1;
                }

                .sheet-label {
                    color: #1B5E20;
                    font-size: 11px;
                    font-weight: 800;
                    margin-bottom: 4px;
                }

                .sheet-value {
                    color: #1F2933;
                    font-size: 12px;
                    font-weight: 700;
                    line-height: 16px;
                }

                .sheet-list {
                    margin-top: 8px;
                    border-top: 1px solid #E2E8DF;
                    padding-top: 9px;
                }

                .sheet-section-title {
                    color: #123D1F;
                    font-size: 16px;
                    font-weight: 900;
                    margin-bottom: 7px;
                }

                .sheet-capture {
                    background: #FFFFFF;
                    border: 1px solid #E2E8DF;
                    border-radius: 16px;
                    padding: 10px;
                    margin-top: 8px;
                    font-size: 12px;
                    line-height: 17px;
                }

                .capture-head {
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-start;
                    gap: 8px;
                    margin-bottom: 7px;
                }

                .capture-name {
                    color: #111;
                    font-size: 14px;
                    font-weight: 900;
                    line-height: 18px;
                }

                .capture-chip {
                    display: inline-flex;
                    align-items: center;
                    border-radius: 999px;
                    padding: 4px 8px;
                    font-size: 11px;
                    font-weight: 800;
                    background: #E8F5E9;
                    color: #123D1F;
                    white-space: nowrap;
                }

                .capture-actions {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 7px;
                    margin: 2px 0 8px;
                }

                .capture-action {
                    border: none;
                    border-radius: 999px;
                    padding: 6px 10px;
                    font-size: 11px;
                    font-weight: 900;
                    background: #E8F5E9;
                    color: #123D1F;
                    box-shadow: 0 1px 4px rgba(0,0,0,0.08);
                }

                .capture-action.photo {
                    background: #E3F2FD;
                    color: #0D47A1;
                }

                .capture-action.disabled {
                    background: #F1F3F1;
                    color: #9AA39C;
                    box-shadow: none;
                }

                .media-modal {
                    position: fixed;
                    inset: 0;
                    z-index: 1000000;
                    background: rgba(0,0,0,0.46);
                    display: none;
                    align-items: center;
                    justify-content: center;
                    padding: 18px;
                    box-sizing: border-box;
                }

                .media-modal.active {
                    display: flex;
                }

                .media-card {
                    width: min(94vw, 420px);
                    max-height: 82vh;
                    overflow-y: auto;
                    background: #FFFFFF;
                    border-radius: 22px;
                    box-shadow: 0 10px 34px rgba(0,0,0,0.34);
                    padding: 16px;
                    box-sizing: border-box;
                }

                .media-title {
                    color: #123D1F;
                    font-size: 18px;
                    font-weight: 900;
                    margin-bottom: 10px;
                }

                .media-text {
                    color: #263238;
                    font-size: 14px;
                    line-height: 21px;
                    white-space: pre-wrap;
                    background: #F7FAF6;
                    border-radius: 14px;
                    padding: 12px;
                }

                .media-img {
                    width: 100%;
                    max-height: 62vh;
                    object-fit: contain;
                    border-radius: 16px;
                    background: #F4F4F4;
                }

                .media-close {
                    width: 100%;
                    border: none;
                    border-radius: 16px;
                    padding: 11px;
                    margin-top: 12px;
                    background: #123D1F;
                    color: white;
                    font-size: 14px;
                    font-weight: 900;
                }

                .capture-line {
                    margin-top: 3px;
                    color: #2E3B32;
                    font-size: 12px;
                    line-height: 17px;
                }

                .capture-label {
                    color: #123D1F;
                    font-weight: 900;
                }

                .capture-severity {
                    font-weight: 900;
                }

                .error-box {
                    padding: 12px;
                    color: #b00020;
                    font-size: 14px;
                    background: #ffffff;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <div id="point-sheet" class="point-sheet"></div>
            <div id="media-modal" class="media-modal" onclick="cerrarModalDetalle()">
                <div class="media-card" onclick="event.stopPropagation()">
                    <div id="media-title" class="media-title"></div>
                    <div id="media-body"></div>
                    <button class="media-close" onclick="cerrarModalDetalle()">Cerrar</button>
                </div>
            </div>
            <script src="leaflet/leaflet.js"></script>
            <script>
                const vertices = $verticesJson;
                const puntos = $puntosJson;
                const pantallaCompleta = $pantallaCompletaJs;

                function mostrarError(mensaje) {
                    document.body.innerHTML = '<div class="error-box"><b>Error al cargar mapa:</b><br>' + mensaje + '</div>';
                }

                function textoEstado(status) {
                    if (status === 'completed') return 'Monitoreado';
                    if (status === 'cancelled') return 'Cancelado';
                    return 'No monitoreado';
                }

                function colorEstado(status, p) {
                    if (status === 'completed') return (p && p.severityColor) ? p.severityColor : '#16A34A';
                    if (status === 'cancelled') return '#DC2626';
                    return '#D98A00';
                }

                function escaparHtml(valor) {
                    return String(valor == null || valor === '' ? '-' : valor)
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#039;');
                }

                function claseBadgePorSeveridad(label, status) {
                    const texto = String(label || '').toLowerCase();
                    if (status === 'completed' && (texto.indexOf('mayor') >= 0 || texto.indexOf('severa') >= 0 || texto.indexOf('rojo') >= 0)) return 'badge-red';
                    if (status === 'completed' && (texto.indexOf('menor') >= 0 || texto.indexOf('amarillo') >= 0)) return 'badge-orange';
                    return 'badge-green';
                }

                function formatearCoordenadasPunto(p) {
                    const lat = Number(p.lat);
                    const lon = Number(p.lon);
                    if (!isNaN(lat) && !isNaN(lon)) {
                        return lat.toFixed(6) + ', ' + lon.toFixed(6);
                    }
                    return escaparHtml(p.lat) + ', ' + escaparHtml(p.lon);
                }

                function normalizarSrcImagen(src) {
                    if (!src || String(src).trim() === '') return '';
                    const valor = String(src).trim();
                    const bajo = valor.toLowerCase();
                    if (bajo.startsWith('http://') || bajo.startsWith('https://') || bajo.startsWith('file://') || bajo.startsWith('content://')) {
                        return valor;
                    }
                    if (valor.startsWith('/')) {
                        return 'file://' + valor;
                    }
                    return valor;
                }

                function mostrarModalDetalle(titulo, contenidoHtml) {
                    const modal = document.getElementById('media-modal');
                    const title = document.getElementById('media-title');
                    const body = document.getElementById('media-body');
                    if (!modal || !title || !body) return;
                    title.innerHTML = titulo;
                    body.innerHTML = contenidoHtml;
                    modal.classList.add('active');
                    modal.style.display = 'flex';
                }

                function cerrarModalDetalle() {
                    const modal = document.getElementById('media-modal');
                    if (!modal) return;
                    modal.classList.remove('active');
                    modal.style.display = 'none';
                }

                let puntoSeleccionadoActual = null;

                function abrirComentarioCaptura(index) {
                    if (!puntoSeleccionadoActual || !puntoSeleccionadoActual.capturas) return;
                    const c = puntoSeleccionadoActual.capturas[index];
                    const comentario = c && c.comentario ? String(c.comentario).trim() : '';
                    if (!comentario) return;
                    mostrarModalDetalle(
                        'Comentario de ' + escaparHtml(c.nombre || 'captura'),
                        '<div class="media-text">' + escaparHtml(comentario) + '</div>'
                    );
                }

                function abrirFotoCaptura(index) {
                    if (!puntoSeleccionadoActual || !puntoSeleccionadoActual.capturas) return;
                    const c = puntoSeleccionadoActual.capturas[index];
                    const src = normalizarSrcImagen(c ? c.photoSrc : '');
                    if (!src) return;

                    const titulo = 'Foto de ' + (c.nombre || 'captura');

                    if (window.AndroidReporte && window.AndroidReporte.abrirFoto) {
                        window.AndroidReporte.abrirFoto(titulo, src);
                        return;
                    }

                    mostrarModalDetalle(
                        escaparHtml(titulo),
                        '<img class="media-img" src="' + escaparHtml(src) + '" alt="Evidencia fotográfica" />'
                    );
                }

                function obtenerResumenCaptura(p) {
                    if (!p.capturas || p.capturas.length === 0) {
                        return 'No se registró captura para este punto.';
                    }

                    const nombres = [];
                    p.capturas.forEach(function(c) {
                        const nombre = c.nombre || 'Captura registrada';
                        if (nombres.indexOf(nombre) < 0) nombres.push(nombre);
                    });

                    return 'Registros en esta coordenada: ' + p.capturas.length +
                        ' • ' + nombres.join(' / ') +
                        ' • Cantidad total: ' + (p.totalCantidad || 0);
                }

                function crearCapturaHtml(c, index) {
                    const colorSeveridad = c.color || '#1B5E20';
                    let html = '';
                    html += '<div class="sheet-capture">';
                    html += '<div class="capture-head">';
                    html += '<div class="capture-name">' + escaparHtml(index + 1) + '. ' + escaparHtml(c.nombre) + '</div>';
                    html += '<span class="capture-chip">' + escaparHtml(c.tipo) + '</span>';
                    html += '</div>';

                    const claseComentario = c.tieneComentario ? 'capture-action' : 'capture-action disabled';
                    const claseFoto = c.tieneFoto ? 'capture-action photo' : 'capture-action photo disabled';
                    const eventoComentario = c.tieneComentario ? ' onclick="abrirComentarioCaptura(' + index + ')"' : '';
                    const eventoFoto = c.tieneFoto ? ' onclick="abrirFotoCaptura(' + index + ')"' : '';
                    html += '<div class="capture-actions">';
                    html += '<button type="button" class="' + claseComentario + '"' + eventoComentario + '>💬 Comentario ' + (c.tieneComentario ? '1' : '0') + '</button>';
                    html += '<button type="button" class="' + claseFoto + '"' + eventoFoto + '>🖼️ Foto ' + (c.tieneFoto ? '1' : '0') + '</button>';
                    html += '</div>';

                    html += '<div class="capture-line"><span class="capture-label">Fase / presencia:</span> ' + escaparHtml(c.fase) + '</div>';
                    html += '<div class="capture-line"><span class="capture-label">Cantidad:</span> ' + escaparHtml(c.cantidad) + '</div>';
                    html += '<div class="capture-line"><span class="capture-label">Severidad:</span> <span class="capture-severity" style="color:' + colorSeveridad + '">' + escaparHtml(c.severidad || '-') + '</span></div>';
                    html += '<div class="capture-line"><span class="capture-label">Fecha:</span> ' + escaparHtml(c.fecha) + '</div>';
                    html += '</div>';
                    return html;
                }

                function crearDetallePuntoHtml(p) {
                    const severidad = p.severityLabel || 'Sin plaga';
                    const badgeClass = claseBadgePorSeveridad(severidad, p.status);
                    const estado = textoEstado(p.status);
                    const total = p.totalCantidad || 0;
                    const fecha = (p.capturas && p.capturas.length > 0) ? (p.capturas[0].fecha || 'No registrado') : 'No registrado';
                    const resumen = obtenerResumenCaptura(p);
                    const coordenadas = formatearCoordenadasPunto(p);

                    let html = '';
                    html += '<div class="sheet-handle"></div>';
                    html += '<div class="sheet-header">';
                    html += '<div>';
                    html += '<div class="sheet-title">Punto ' + escaparHtml(p.numero) + '</div>';
                    html += '<div class="sheet-badges">';
                    html += '<span class="badge ' + badgeClass + '">● ' + escaparHtml(severidad) + '</span>';
                    html += '<span class="badge badge-green">✓ ' + escaparHtml(estado) + '</span>';
                    html += '</div>';
                    html += '</div>';
                    html += '<button class="sheet-close" onclick="cerrarDetallePunto()">Cerrar</button>';
                    html += '</div>';

                    html += '<div class="sheet-text">' + escaparHtml(resumen) + '</div>';

                    html += '<div class="sheet-list">';
                    html += '<div class="sheet-section-title">Detalle capturado</div>';
                    if (!p.capturas || p.capturas.length === 0) {
                        html += '<div class="sheet-capture">No se registraron plagas o enfermedades en este punto.</div>';
                    } else {
                        p.capturas.forEach(function(c, index) {
                            html += crearCapturaHtml(c, index);
                        });
                    }
                    html += '</div>';

                    html += '<div class="sheet-grid">';
                    html += '<div class="sheet-field"><div class="sheet-label">Estado</div><div class="sheet-value">' + escaparHtml(estado) + '</div></div>';
                    html += '<div class="sheet-field"><div class="sheet-label">Fecha principal</div><div class="sheet-value">' + escaparHtml(fecha) + '</div></div>';
                    html += '<div class="sheet-field"><div class="sheet-label">Total registrado</div><div class="sheet-value">' + escaparHtml(total) + '</div></div>';
                    html += '<div class="sheet-field"><div class="sheet-label">Radio</div><div class="sheet-value">' + escaparHtml(p.radius) + ' m</div></div>';
                    html += '<div class="sheet-field sheet-field-wide"><div class="sheet-label">Coordenadas</div><div class="sheet-value">' + escaparHtml(coordenadas) + '</div></div>';
                    html += '</div>';

                    return html;
                }

                let marcadorSeleccionado = null;

                function seleccionarMarcador(marker, p) {
                    try {
                        if (marcadorSeleccionado) {
                            marcadorSeleccionado.setStyle({
                                radius: 11,
                                color: '#1A1A1A',
                                weight: 2
                            });
                        }

                        marcadorSeleccionado = marker;
                        puntoSeleccionadoActual = p;
                        marker.setStyle({
                            radius: 15,
                            color: '#FFFFFF',
                            weight: 4
                        });
                        marker.bringToFront();

                        if (!pantallaCompleta) {
                            marker.openTooltip();
                            return;
                        }

                        const sheet = document.getElementById('point-sheet');
                        if (!sheet) {
                            alert(textoPlanoPunto(p));
                            return;
                        }
                        sheet.innerHTML = crearDetallePuntoHtml(p);
                        sheet.style.display = 'block';
                        sheet.classList.add('active');
                    } catch (err) {
                        alert(textoPlanoPunto(p));
                    }
                }

                function cerrarDetallePunto() {
                    const sheet = document.getElementById('point-sheet');
                    sheet.classList.remove('active');
                    sheet.style.display = 'none';
                    sheet.innerHTML = '';

                    if (marcadorSeleccionado) {
                        marcadorSeleccionado.setStyle({
                            radius: 11,
                            color: '#1A1A1A',
                            weight: 2
                        });
                        marcadorSeleccionado = null;
                    }
                    puntoSeleccionadoActual = null;
                    cerrarModalDetalle();
                }

                function textoPlanoPunto(p) {
                    let txt = 'Punto ' + p.numero + '\nEstado: ' + textoEstado(p.status);
                    if (p.capturas.length === 0) {
                        txt += '\nNo se realizó monitoreo en este punto.';
                    } else {
                        p.capturas.forEach(function(c) {
                            txt += '\n\n' + c.nombre + '\nTipo: ' + c.tipo + '\nFase: ' + c.fase + '\nCantidad: ' + c.cantidad + '\nSeveridad: ' + (c.severidad || '-') + '\nFecha: ' + c.fecha;
                        });
                    }
                    return txt;
                }

                function escaparParaAlert(texto) {
                    return texto
                        .replace(/\\/g, '\\\\')
                        .replace(/'/g, "\\'")
                        .replace(/\r/g, '')
                        .replace(/\n/g, '\\n');
                }

                function dibujarFallback(motivo) {
                    const todos = [];
                    vertices.forEach(function(v) { todos.push({lat: v.lat, lon: v.lon}); });
                    puntos.forEach(function(p) { todos.push({lat: p.lat, lon: p.lon}); });

                    if (todos.length === 0) {
                        document.getElementById('map').innerHTML =
                            '<div class="no-data-box" style="margin:16px"><b>Sin datos para dibujar</b><br>No se encontraron vértices ni puntos para este monitoreo.</div>';
                        return;
                    }

                    let minLat = todos[0].lat;
                    let maxLat = todos[0].lat;
                    let minLon = todos[0].lon;
                    let maxLon = todos[0].lon;

                    todos.forEach(function(c) {
                        minLat = Math.min(minLat, c.lat);
                        maxLat = Math.max(maxLat, c.lat);
                        minLon = Math.min(minLon, c.lon);
                        maxLon = Math.max(maxLon, c.lon);
                    });

                    if (Math.abs(maxLat - minLat) < 0.000001) {
                        maxLat += 0.0001;
                        minLat -= 0.0001;
                    }
                    if (Math.abs(maxLon - minLon) < 0.000001) {
                        maxLon += 0.0001;
                        minLon -= 0.0001;
                    }

                    const pad = 8;
                    function x(lon) {
                        return pad + ((lon - minLon) / (maxLon - minLon)) * (100 - pad * 2);
                    }
                    function y(lat) {
                        return pad + (1 - ((lat - minLat) / (maxLat - minLat))) * (100 - pad * 2);
                    }

                    let polygonPoints = '';
                    vertices.forEach(function(v) {
                        polygonPoints += x(v.lon).toFixed(2) + ',' + y(v.lat).toFixed(2) + ' ';
                    });

                    let svg = '';
                    svg += '<div class="map-title-box" style="position:absolute;left:12px;top:12px;z-index:2">Mapa local del reporte</div>';
                    svg += '<svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet" style="position:absolute;left:0;top:0;width:100%;height:100%;background:#dfe8d1">';
                    svg += '<defs><pattern id="grid" width="8" height="8" patternUnits="userSpaceOnUse"><path d="M 8 0 L 0 0 0 8" fill="none" stroke="#c8d8bd" stroke-width="0.25"/></pattern></defs>';
                    svg += '<rect x="0" y="0" width="100" height="100" fill="url(#grid)" />';

                    if (vertices.length > 0) {
                        svg += '<polygon points="' + polygonPoints + '" fill="#7CB342" fill-opacity="0.30" stroke="#1B5E20" stroke-width="0.9" />';
                    }

                    puntos.forEach(function(p) {
                        const px = x(p.lon).toFixed(2);
                        const py = y(p.lat).toFixed(2);
                        const color = colorEstado(p.status, p);
                        svg += '<circle cx="' + px + '" cy="' + py + '" r="3.4" fill="' + color + '" stroke="#1A1A1A" stroke-width="0.7" onclick="alert(\'' + escaparParaAlert(textoPlanoPunto(p)) + '\')" />';
                        svg += '<text x="' + px + '" y="' + (parseFloat(py) - 4.5).toFixed(2) + '" font-size="3.2" text-anchor="middle" fill="#123D1F" font-weight="bold">' + p.numero + '</text>';
                    });

                    svg += '</svg>';
                   svg += '<div class="legend" style="position:absolute;left:12px;bottom:12px;z-index:2"><div class="legend-title">Semáforo</div><span class="dot" style="background:#16A34A"></span>Verde: sin plaga<br><span class="dot" style="background:#FACC15"></span>Amarillo: severidad menor<br><span class="dot" style="background:#F97316"></span>Naranja: severidad mayor<br><span class="dot" style="background:#DC2626"></span>Rojo: severidad alta</div>';
                    if (motivo) {
                        svg += '<div class="no-data-box" style="position:absolute;right:12px;top:12px;z-index:2"><b>Vista local</b><br>' + motivo + '</div>';
                    }

                    document.getElementById('map').style.position = 'relative';
                    document.getElementById('map').innerHTML = svg;
                }

                let intentosInicioMapa = 0;

                function iniciarMapaReporteSeguro() {
                    try {
                    const contenedorMapa = document.getElementById('map');
                    if (contenedorMapa && contenedorMapa.clientHeight < 40 && intentosInicioMapa < 12) {
                        intentosInicioMapa++;
                        setTimeout(iniciarMapaReporteSeguro, 180);
                        return;
                    }
                    if (typeof L === 'undefined') {
                        dibujarFallback('Leaflet local no cargó, pero se dibujó el polígono y los puntos con vista local.');
                    } else {
                        const map = L.map('map', {
                        zoomControl: true,
                        preferCanvas: true,
                        zoomSnap: 0.25,
                        zoomDelta: 0.5,
                        minZoom: 3,
                        maxZoom: 28,
                        bounceAtZoomLimits: false
});
                        window.reporteMap = map;
                        let boundsReporte = null;

                        function ajustarVistaReporte() {
                            if (!window.reporteMap) return;
                            window.reporteMap.invalidateSize(true);
                            if (boundsReporte) {
                                window.reporteMap.fitBounds(boundsReporte, {
                                    padding: pantallaCompleta ? [34, 34] : [24, 24],
                                    maxZoom: pantallaCompleta ? 18 : 18
                                });
                            }
                        }

                        window.ajustarMapaReporte = ajustarVistaReporte;
                        window.addEventListener('resize', function() {
                            setTimeout(ajustarVistaReporte, 120);
                        });

                        const capaSatelital = L.tileLayer(
                            'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                            {
                                minZoom: 3,
                                maxZoom: 28,
                                maxNativeZoom: 18,
                                attribution: 'Tiles © Esri',
                                errorTileUrl: ''
                            }
                        ).addTo(map);

                        const polygonLatLng = vertices.map(function(v) {
                            return [v.lat, v.lon];
                        });

                        if (polygonLatLng.length > 0) {
                            const polygon = L.polygon(polygonLatLng, {
                                color: '#1B5E20',
                                weight: 4,
                                opacity: 1,
                                fillColor: '#7CB342',
                                fillOpacity: 0.28
                            }).addTo(map);

                            polygon.bindPopup('<b>Parcela del monitoreo</b><br>Vértices: ' + polygonLatLng.length);

                            boundsReporte = polygon.getBounds();
                            ajustarVistaReporte();
                        } else if (puntos.length > 0) {
                            boundsReporte = L.latLngBounds(puntos.map(function(p) { return [p.lat, p.lon]; }));
                            ajustarVistaReporte();
                        } else {
                            map.setView([20.6767, -101.3563], 14);

                            const noData = L.control({ position: 'topright' });
                            noData.onAdd = function() {
                                const div = L.DomUtil.create('div', 'no-data-box');
                                div.innerHTML = '<b>Sin datos para dibujar</b><br>No se encontraron vértices ni puntos para este monitoreo.';
                                return div;
                            };
                            noData.addTo(map);
                        }

                        puntos.forEach(function(p) {
                            const color = colorEstado(p.status, p);

                            L.circle([p.lat, p.lon], {
                                radius: p.radius,
                                color: color,
                                weight: 1,
                                fillColor: color,
                                fillOpacity: 0.08
                            }).addTo(map);

                            const marker = L.circleMarker([p.lat, p.lon], {
                                radius: 11,
                                color: '#1A1A1A',
                                weight: 2,
                                fillColor: color,
                                fillOpacity: 0.95
                            })
                            .addTo(map)
                            .bindTooltip('Punto ' + p.numero + ' • ' + textoEstado(p.status), {
                                permanent: false,
                                direction: 'top'
                            });

                            marker.on('click', function(e) {
                                if (e && e.originalEvent) {
                                    L.DomEvent.stopPropagation(e.originalEvent);
                                    L.DomEvent.preventDefault(e.originalEvent);
                                }
                                seleccionarMarcador(marker, p);
                            });
                        });

                       

                        const legend = L.control({ position: 'bottomleft' });
                        legend.onAdd = function() {
                            const div = L.DomUtil.create('div', 'legend');
                            div.innerHTML =
                        '<div class="legend-title">Semáforo</div>' +
                        '<span class="dot" style="background:#16A34A"></span>Verde: sin plaga<br>' +
                        '<span class="dot" style="background:#FACC15"></span>Amarillo: severidad menor<br>' +
                        '<span class="dot" style="background:#F97316"></span>Naranja: severidad mayor<br>' +
                        '<span class="dot" style="background:#DC2626"></span>Rojo: severidad alta';
                            return div;
                        };
                        legend.addTo(map);

                        [150, 350, 700, 1200, 2000].forEach(function(ms) {
                            setTimeout(ajustarVistaReporte, ms);
                        });
                    }
                } catch (e) {
                    dibujarFallback(e.message);
                }
                }

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        requestAnimationFrame(function() {
                            setTimeout(iniciarMapaReporteSeguro, 250);
                        });
                    });
                } else {
                    requestAnimationFrame(function() {
                        setTimeout(iniciarMapaReporteSeguro, 250);
                    });
                }

            </script>
        </body>
        </html>
    """.trimIndent()
}

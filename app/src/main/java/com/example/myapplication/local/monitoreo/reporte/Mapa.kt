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


private const val COLOR_REPORTE_VERDE = "#16A34A"
private const val COLOR_REPORTE_AMARILLO = "#FACC15"
private const val COLOR_REPORTE_NARANJA = "#F97316"
private const val COLOR_REPORTE_ROJO = "#DC2626"

private data class EstadoMitadMapaReporte(
    val color: String,
    val texto: String,
    val nivel: Int,
    val total: Int = 0
)

private fun calcularEstadoPlagaMapaReporte(
    checkpointsPunto: List<LocalPhytomonitoringCheckpointEntity>,
    catalogoPorId: Map<Long, LocalPhytosanitaryCatalogEntity>
): EstadoMitadMapaReporte {
    val capturasPlaga = checkpointsPunto.filter { checkpoint ->
        val fito = checkpoint.idPhytosanitary?.let(catalogoPorId::get)

        fito != null &&
                !esEnfermedadReporte(fito.type) &&
                !esSinPlagaReporte(checkpoint, fito) &&
                (checkpoint.qty ?: 0) > 0
    }

    if (capturasPlaga.isEmpty()) {
        val seRegistroSinPlaga = checkpointsPunto.any { checkpoint ->
            val fito = checkpoint.idPhytosanitary?.let(catalogoPorId::get)
            esSinPlagaReporte(checkpoint, fito)
        }

        return EstadoMitadMapaReporte(
            color = COLOR_REPORTE_VERDE,
            texto = "Sin plaga detectada",
            nivel = 0,
            total = 0
        )
    }

    val severidad = calcularSeveridadPorPunto(
        checkpointsPunto = capturasPlaga,
        catalogoPorId = catalogoPorId
    )

    return EstadoMitadMapaReporte(
        color = severidad.nivelFinal.colorHex,
        texto = severidad.nivelFinal.etiqueta,
        nivel = severidad.nivelFinal.orden,
        total = severidad.totalCantidadPunto
    )
}

private fun prioridadEnfermedadMapaReporte(
    checkpoint: LocalPhytomonitoringCheckpointEntity
): Int {
    if (checkpoint.presenceStatus == 0) return 1

    val fase = checkpoint.stage
        ?.trim()
        ?.lowercase(Locale.getDefault())
        .orEmpty()

    return when {
        fase.contains("avanz") -> 4
        fase.contains("desarrollo") -> 3
        fase.contains("inicio") -> 2
        checkpoint.presenceStatus == 1 -> 2
        else -> 0
    }
}

private fun calcularEstadoEnfermedadMapaReporte(
    checkpointsPunto: List<LocalPhytomonitoringCheckpointEntity>,
    catalogoPorId: Map<Long, LocalPhytosanitaryCatalogEntity>
): EstadoMitadMapaReporte {
    val capturasEnfermedad = checkpointsPunto.filter { checkpoint ->
        val fito = checkpoint.idPhytosanitary?.let(catalogoPorId::get)
        esEnfermedadReporte(fito?.type)
    }

    if (capturasEnfermedad.isEmpty()) {
        return EstadoMitadMapaReporte(
            color = COLOR_REPORTE_VERDE,
            texto = "No presente",
            nivel = 1
        )
    }

    return when (
        capturasEnfermedad.maxOfOrNull(::prioridadEnfermedadMapaReporte) ?: 0
    ) {
        4 -> EstadoMitadMapaReporte(
            color = COLOR_REPORTE_ROJO,
            texto = "Presente / Avanzado",
            nivel = 4
        )

        3 -> EstadoMitadMapaReporte(
            color = COLOR_REPORTE_NARANJA,
            texto = "Presente / Desarrollo",
            nivel = 3
        )

        2 -> EstadoMitadMapaReporte(
            color = COLOR_REPORTE_AMARILLO,
            texto = "Presente / Inicio",
            nivel = 2
        )

        1 -> EstadoMitadMapaReporte(
            color = COLOR_REPORTE_VERDE,
            texto = "No presente",
            nivel = 1
        )

        else -> EstadoMitadMapaReporte(
            color = COLOR_REPORTE_VERDE,
            texto = "No presente",
            nivel = 1
        )
    }
}

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

            val estadoPlaga = calcularEstadoPlagaMapaReporte(
                checkpointsPunto = capturasMismaCoordenada,
                catalogoPorId = catalogoMap
            )

            val estadoEnfermedad = calcularEstadoEnfermedadMapaReporte(
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

                        val capturasFito = capturasMismaCoordenada.filter { checkpoint ->
                            checkpoint.idPhytosanitary == fito.idPhytosanitary
                        }

                        val estadoEnfermedadFito = if (esEnfermedad) {
                            calcularEstadoEnfermedadMapaReporte(
                                checkpointsPunto = capturasFito,
                                catalogoPorId = catalogoMap
                            )
                        } else {
                            null
                        }

                        val fase = if (esEnfermedad) {
                            estadoEnfermedadFito?.texto ?: "Sin evaluar"
                        } else {
                            fasesResumen.ifBlank { "-" }
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
                        put(
                            "severidad",
                            estadoEnfermedadFito?.texto ?: fito.nivel.etiqueta
                        )
                        put(
                            "color",
                            estadoEnfermedadFito?.color ?: fito.nivel.colorHex
                        )
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

            val colorGlobal = when {
                statusFinal == "cancelled" -> "#6B7280"
                statusFinal != "completed" -> COLOR_REPORTE_VERDE
                estadoPlaga.color.equals(COLOR_REPORTE_ROJO, ignoreCase = true) ||
                        estadoEnfermedad.color.equals(COLOR_REPORTE_ROJO, ignoreCase = true) -> {
                    COLOR_REPORTE_ROJO
                }
                estadoPlaga.color.equals(COLOR_REPORTE_NARANJA, ignoreCase = true) ||
                        estadoEnfermedad.color.equals(COLOR_REPORTE_NARANJA, ignoreCase = true) ||
                        estadoPlaga.color.equals(COLOR_REPORTE_AMARILLO, ignoreCase = true) ||
                        estadoEnfermedad.color.equals(COLOR_REPORTE_AMARILLO, ignoreCase = true) -> {
                    COLOR_REPORTE_NARANJA
                }
                else -> COLOR_REPORTE_VERDE
            }

            val nivelGlobal = when {
                statusFinal != "completed" -> 0
                colorGlobal.equals(COLOR_REPORTE_ROJO, ignoreCase = true) -> 3
                colorGlobal.equals(COLOR_REPORTE_NARANJA, ignoreCase = true) -> 2
                else -> 1
            }

            val conProblema = statusFinal == "completed" && nivelGlobal >= 2

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

                put("plagaColor", estadoPlaga.color)
                put("plagaTexto", estadoPlaga.texto)
                put("plagaNivel", estadoPlaga.nivel)
                put("totalCantidadPlaga", estadoPlaga.total)

                put("enfermedadColor", estadoEnfermedad.color)
                put("enfermedadTexto", estadoEnfermedad.texto)
                put("enfermedadNivel", estadoEnfermedad.nivel)

                put("colorGlobal", colorGlobal)
                put("nivelGlobal", nivelGlobal)
                put("conProblema", conProblema)

                put("capturas", capturasArray)
            })
        }
    }.toString()

    val radioBordeMapa = if (pantallaCompleta) "0px" else "14px"
    val minHeightMapa = if (pantallaCompleta) "100%" else "320px"
    val pantallaCompletaJs = if (pantallaCompleta) "true" else "false"
    val claseModoMapa = if (pantallaCompleta) "modo-completo" else "modo-resumen"
    val posicionLeyenda = if (pantallaCompleta) "topright" else "bottomleft"
    val anchoLeyenda = if (pantallaCompleta) "230px" else "205px"
    val altoMaximoLeyenda = if (pantallaCompleta) "calc(100vh - 105px)" else "150px"
    val posicionLeyendaFallback = if (pantallaCompleta) {
        "right:12px;top:12px;"
    } else {
        "left:12px;bottom:12px;"
    }

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
                    background: rgba(255,255,255,0.96);
                    padding: 7px 9px;
                    border: 1px solid rgba(18,61,31,0.16);
                    border-radius: 11px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.24);
                    font-size: 10.5px;
                    line-height: 15px;
                    color: #222;
                    width: max-content;
                    max-width: $anchoLeyenda;
                    max-height: $altoMaximoLeyenda;
                    overflow-y: auto;
                    box-sizing: border-box;
                    -webkit-overflow-scrolling: touch;
                }

                .legend-title {
                    font-size: 11.5px;
                    font-weight: bold;
                    margin-bottom: 3px;
                    color: #123D1F;
                }

                /* Vista pequeña: únicamente muestra las leyendas P/E. */
                .leyendas-pe-control {
                    width: min(318px, calc(100vw - 24px));
                    display: grid;
                    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
                    gap: 8px;
                    box-sizing: border-box;
                    pointer-events: auto;
                }

                .leyenda-pe-card {
                    min-width: 0;
                    min-height: 102px;
                    background: rgba(255,255,255,0.96);
                    color: #263238;
                    border: 1px solid rgba(18,61,31,0.14);
                    border-radius: 12px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.22);
                    padding: 7px 8px;
                    box-sizing: border-box;
                    font-size: 9.4px;
                    line-height: 13px;
                }

                .leyenda-pe-titulo {
                    color: #123D1F;
                    font-size: 10.6px;
                    line-height: 14px;
                    font-weight: 900;
                    margin-bottom: 3px;
                    white-space: nowrap;
                }

                .leyenda-pe-fila {
                    display: flex;
                    align-items: center;
                    gap: 5px;
                    min-width: 0;
                    white-space: nowrap;
                }

                .leyenda-pe-fila .dot {
                    flex: 0 0 auto;
                    width: 9px;
                    height: 9px;
                    margin-right: 0;
                }

                /* Sube la atribución para que no tape la leyenda de enfermedades. */
                .modo-resumen .leaflet-bottom.leaflet-right {
                    bottom: 108px;
                }

                @media (max-width: 380px) {
                    .leyendas-pe-control {
                        width: calc(100vw - 18px);
                        gap: 6px;
                    }

                    .leyenda-pe-card {
                        min-height: 98px;
                        padding: 6px;
                        font-size: 8.7px;
                        line-height: 12px;
                    }

                    .leyenda-pe-titulo {
                        font-size: 9.7px;
                    }
                }

                .dot {
                    height: 10px;
                    width: 10px;
                    border-radius: 50%;
                    display: inline-block;
                    margin-right: 6px;
                }

                .marcador-pe-wrapper {
                    background: transparent !important;
                    border: none !important;
                }

                .marcador-pe {
                    position: relative;
                    width: 28px;
                    height: 32px;
                    user-select: none;
                    -webkit-user-select: none;
                }

                .marcador-pe-letras {
                    position: absolute;
                    top: 0;
                    left: 5px;
                    width: 18px;
                    display: flex;
                    justify-content: space-around;
                    color: #FFFFFF;
                    font-size: 9px;
                    line-height: 11px;
                    font-weight: 900;
                    text-shadow:
                        -1px -1px 2px #111111,
                         1px -1px 2px #111111,
                        -1px  1px 2px #111111,
                         1px  1px 2px #111111;
                }

                .marcador-pe-circulo {
                    position: absolute;
                    top: 12px;
                    left: 5px;
                    display: flex;
                    width: 18px;
                    height: 18px;
                    overflow: hidden;
                    border: 2px solid #17211B;
                    border-radius: 50%;
                    box-sizing: border-box;
                    background: #16A34A;
                    box-shadow:
                        0 2px 7px rgba(0,0,0,0.48),
                        0 0 0 2px rgba(255,255,255,0.82);
                }

                .marcador-pe-mitad {
                    width: 50%;
                    height: 100%;
                    box-sizing: border-box;
                }

                .marcador-pe-plaga {
                    border-right: 1.5px solid #17211B;
                }

                .marcador-pe-enfermedad {
                    border-left: 1.5px solid #17211B;
                }

                .marcador-pe-wrapper.selected-marker-ring .marcador-pe-circulo {
                    box-shadow:
                        0 0 0 4px rgba(255,255,255,0.98),
                        0 0 13px 7px rgba(18,61,31,0.78);
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
                    overscroll-behavior-y: contain;
                    touch-action: pan-y;
                    will-change: transform, opacity;
                    transition: transform 180ms ease-out, opacity 180ms ease-out;
                }

                .point-sheet.active {
                    display: block;
                    transform: translateY(0);
                    opacity: 1;
                }

                .point-sheet.dragging {
                    transition: none;
                    user-select: none;
                    -webkit-user-select: none;
                }

                .sheet-handle {
                    width: 46px;
                    height: 5px;
                    border-radius: 10px;
                    background: #D0D0D0;
                    margin: 0 auto 8px;
                    cursor: grab;
                    touch-action: none;
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

                .heat-canvas {
                    pointer-events: none;
                    z-index: 350;
                    opacity: 0.92;
                    mix-blend-mode: multiply;
                }

                .resumen-superficie {
                    width: 276px;
                    max-width: calc(100vw - 24px);
                    background: rgba(255,255,255,0.97);
                    color: #263238;
                    border: 1px solid rgba(18,61,31,0.18);
                    border-radius: 14px;
                    box-shadow: 0 4px 18px rgba(0,0,0,0.28);
                    padding: 10px 12px;
                    box-sizing: border-box;
                    font-size: 11.5px;
                    line-height: 16px;
                }

                .resumen-titulo {
                    color: #123D1F;
                    font-size: 15px;
                    font-weight: 900;
                    margin-bottom: 5px;
                }

                .resumen-info {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    margin-top: 2px;
                }

                .resumen-subtitulo {
                    color: #4B5563;
                    font-size: 10px;
                    font-weight: 900;
                    letter-spacing: 0.25px;
                    margin-top: 9px;
                    margin-bottom: 3px;
                    text-transform: uppercase;
                }

                .resumen-fila {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 8px;
                    margin-top: 3px;
                }

                .resumen-fila-label {
                    display: flex;
                    align-items: center;
                    min-width: 0;
                }

                .resumen-valor {
                    color: #111827;
                    font-weight: 900;
                    white-space: nowrap;
                }

                .selector-vista {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 7px;
                    margin-top: 9px;
                }

                .selector-vista button {
                    border: 1px solid #D8DED9;
                    border-radius: 9px;
                    background: #F7F9F7;
                    color: #374151;
                    padding: 7px 5px;
                    font-size: 11px;
                    font-weight: 900;
                }

                .selector-vista button.active {
                    background: #1F2937;
                    color: #FFFFFF;
                    border-color: #1F2937;
                }

                .resumen-nota {
                    margin-top: 6px;
                    color: #6B7280;
                    font-size: 9.5px;
                    line-height: 13px;
                }

                @media (max-width: 480px) {
                    .resumen-superficie {
                        width: 240px;
                        padding: 8px 10px;
                        font-size: 10.5px;
                        line-height: 14px;
                    }
                    .resumen-titulo { font-size: 13px; }
                    .selector-vista button { padding: 6px 4px; font-size: 10px; }
                }

                .error-box {
                    padding: 12px;
                    color: #b00020;
                    font-size: 14px;
                    background: #ffffff;
                }
            </style>
        </head>
        <body class="$claseModoMapa">
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
                    if (status === 'completed') return '#334155';
                    if (status === 'cancelled') return '#6B7280';
                    return '#D98A00';
                }

                function colorGlobalPunto(p) {
                    if (p && p.colorGlobal) return p.colorGlobal;
                    if (!p || p.status !== 'completed') return '#16A34A';
                    return '#16A34A';
                }

                function hexARgba(hex, alpha) {
                    const limpio = String(hex || '#16A34A').replace('#', '');
                    const valor = limpio.length === 3
                        ? limpio.split('').map(function(c) { return c + c; }).join('')
                        : limpio.padEnd(6, '0').substring(0, 6);
                    const r = parseInt(valor.substring(0, 2), 16) || 0;
                    const g = parseInt(valor.substring(2, 4), 16) || 0;
                    const b = parseInt(valor.substring(4, 6), 16) || 0;
                    return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
                }

                function crearProyectorMetros(verticesBase) {
                    const referencia = verticesBase && verticesBase.length > 0
                        ? verticesBase
                        : puntos;
                    const lat0 = referencia.length > 0
                        ? referencia.reduce(function(a, p) { return a + Number(p.lat || 0); }, 0) / referencia.length
                        : 0;
                    const lon0 = referencia.length > 0
                        ? referencia.reduce(function(a, p) { return a + Number(p.lon || 0); }, 0) / referencia.length
                        : 0;
                    const radioTierra = 6378137.0;
                    const cosLat = Math.cos(lat0 * Math.PI / 180.0);

                    return function(lat, lon) {
                        return {
                            x: (Number(lon) - lon0) * Math.PI / 180.0 * radioTierra * cosLat,
                            y: (Number(lat) - lat0) * Math.PI / 180.0 * radioTierra
                        };
                    };
                }

                function areaPoligonoMetros(poly) {
                    if (!poly || poly.length < 3) return 0;
                    let suma = 0;
                    for (let i = 0; i < poly.length; i++) {
                        const a = poly[i];
                        const b = poly[(i + 1) % poly.length];
                        suma += a.x * b.y - b.x * a.y;
                    }
                    return Math.abs(suma) / 2.0;
                }

                function puntoDentroPoligono(x, y, poly) {
                    let dentro = false;
                    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
                        const xi = poly[i].x;
                        const yi = poly[i].y;
                        const xj = poly[j].x;
                        const yj = poly[j].y;
                        const cruza = ((yi > y) !== (yj > y)) &&
                            (x < (xj - xi) * (y - yi) / ((yj - yi) || 0.0000001) + xi);
                        if (cruza) dentro = !dentro;
                    }
                    return dentro;
                }

                function calcularResumenSuperficie() {
                    if (!vertices || vertices.length < 3) {
                        return {
                            areaTotalHa: 0,
                            areaProblemaHa: 0,
                            areaSinProblemaHa: 0,
                            porcentajeProblema: 0,
                            porcentajeSinProblema: 0,
                            disponible: false
                        };
                    }

                    const proyectar = crearProyectorMetros(vertices);
                    const poligono = vertices.map(function(v) {
                        return proyectar(v.lat, v.lon);
                    });
                    const areaTotalM2 = areaPoligonoMetros(poligono);
                    const puntosProblema = puntos
                        .filter(function(p) { return p.conProblema === true || p.conProblema === 'true'; })
                        .map(function(p) {
                            const c = proyectar(p.lat, p.lon);
                            c.radio = Math.max(0, Number(p.radius || 0));
                            return c;
                        })
                        .filter(function(p) { return p.radio > 0; });

                    if (areaTotalM2 <= 0) {
                        return {
                            areaTotalHa: 0,
                            areaProblemaHa: 0,
                            areaSinProblemaHa: 0,
                            porcentajeProblema: 0,
                            porcentajeSinProblema: 0,
                            disponible: false
                        };
                    }

                    if (puntosProblema.length === 0) {
                        return {
                            areaTotalHa: areaTotalM2 / 10000.0,
                            areaProblemaHa: 0,
                            areaSinProblemaHa: areaTotalM2 / 10000.0,
                            porcentajeProblema: 0,
                            porcentajeSinProblema: 100,
                            disponible: true
                        };
                    }

                    let minX = poligono[0].x;
                    let maxX = poligono[0].x;
                    let minY = poligono[0].y;
                    let maxY = poligono[0].y;
                    poligono.forEach(function(p) {
                        minX = Math.min(minX, p.x);
                        maxX = Math.max(maxX, p.x);
                        minY = Math.min(minY, p.y);
                        maxY = Math.max(maxY, p.y);
                    });

                    const ancho = Math.max(1, maxX - minX);
                    const alto = Math.max(1, maxY - minY);
                    const radioMinimo = Math.min.apply(null, puntosProblema.map(function(p) { return p.radio; }));
                    let paso = Math.sqrt((ancho * alto) / 90000.0);
                    paso = Math.max(1.25, Math.min(6.0, paso));
                    if (isFinite(radioMinimo) && radioMinimo > 0) {
                        paso = Math.min(paso, Math.max(1.25, radioMinimo / 3.0));
                    }

                    let dentroTotal = 0;
                    let dentroProblema = 0;
                    for (let x = minX + paso / 2.0; x <= maxX; x += paso) {
                        for (let y = minY + paso / 2.0; y <= maxY; y += paso) {
                            if (!puntoDentroPoligono(x, y, poligono)) continue;
                            dentroTotal++;

                            let problema = false;
                            for (let i = 0; i < puntosProblema.length; i++) {
                                const p = puntosProblema[i];
                                const dx = x - p.x;
                                const dy = y - p.y;
                                if ((dx * dx + dy * dy) <= (p.radio * p.radio)) {
                                    problema = true;
                                    break;
                                }
                            }
                            if (problema) dentroProblema++;
                        }
                    }

                    const proporcion = dentroTotal > 0
                        ? Math.max(0, Math.min(1, dentroProblema / dentroTotal))
                        : 0;
                    const areaProblemaM2 = areaTotalM2 * proporcion;
                    const areaSinProblemaM2 = Math.max(0, areaTotalM2 - areaProblemaM2);

                    return {
                        areaTotalHa: areaTotalM2 / 10000.0,
                        areaProblemaHa: areaProblemaM2 / 10000.0,
                        areaSinProblemaHa: areaSinProblemaM2 / 10000.0,
                        porcentajeProblema: proporcion * 100.0,
                        porcentajeSinProblema: (1.0 - proporcion) * 100.0,
                        disponible: true
                    };
                }

                function crearCapaCalorSimple(puntosCalor) {
                    return L.Layer.extend({
                        initialize: function(lista) {
                            this._lista = lista || [];
                        },
                        onAdd: function(map) {
                            this._map = map;
                            this._canvas = L.DomUtil.create('canvas', 'heat-canvas leaflet-zoom-animated');
                            map.getPanes().overlayPane.appendChild(this._canvas);
                            map.on('move zoom resize viewreset', this._dibujar, this);
                            this._dibujar();
                        },
                        onRemove: function(map) {
                            map.off('move zoom resize viewreset', this._dibujar, this);
                            if (this._canvas && this._canvas.parentNode) {
                                this._canvas.parentNode.removeChild(this._canvas);
                            }
                            this._canvas = null;
                            this._map = null;
                        },
                        _dibujar: function() {
                            if (!this._map || !this._canvas) return;
                            const size = this._map.getSize();
                            const dpr = Math.max(1, window.devicePixelRatio || 1);
                            this._canvas.width = Math.round(size.x * dpr);
                            this._canvas.height = Math.round(size.y * dpr);
                            this._canvas.style.width = size.x + 'px';
                            this._canvas.style.height = size.y + 'px';
                            L.DomUtil.setPosition(
                                this._canvas,
                                this._map.containerPointToLayerPoint([0, 0])
                            );

                            const ctx = this._canvas.getContext('2d');
                            if (!ctx) return;
                            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                            ctx.clearRect(0, 0, size.x, size.y);

                            this._lista.forEach(function(p) {
                                const centro = L.latLng(Number(p.lat), Number(p.lon));
                                const cp = this._map.latLngToContainerPoint(centro);
                                const radioM = Math.max(5, Number(p.radius || 7.5));
                                const deltaLon = radioM / (111320.0 * Math.max(0.2, Math.cos(Number(p.lat) * Math.PI / 180.0)));
                                const borde = this._map.latLngToContainerPoint([
                                    Number(p.lat),
                                    Number(p.lon) + deltaLon
                                ]);
                                const radioPxReal = Math.max(4, Math.abs(borde.x - cp.x));
                                const radioVisual = Math.max(24, Math.min(90, radioPxReal * 5.0));
                                const color = colorGlobalPunto(p);
                                const alphaCentro = Number(p.nivelGlobal || 0) >= 3
                                    ? 0.74
                                    : Number(p.nivelGlobal || 0) >= 2
                                        ? 0.58
                                        : 0.32;

                                const gradiente = ctx.createRadialGradient(
                                    cp.x, cp.y, 0,
                                    cp.x, cp.y, radioVisual
                                );
                                gradiente.addColorStop(0, hexARgba(color, alphaCentro));
                                gradiente.addColorStop(0.38, hexARgba(color, alphaCentro * 0.62));
                                gradiente.addColorStop(1, hexARgba(color, 0));
                                ctx.fillStyle = gradiente;
                                ctx.beginPath();
                                ctx.arc(cp.x, cp.y, radioVisual, 0, Math.PI * 2);
                                ctx.fill();
                            }, this);
                        }
                    });
                }

                function crearIconoPuntoDividido(p) {
                    const colorPlaga = p.plagaColor || '#16A34A';
                    const colorEnfermedad = p.enfermedadColor || '#16A34A';

                    const html =
                        '<div class="marcador-pe">' +
                            '<div class="marcador-pe-letras">' +
                                '<span>P</span><span>E</span>' +
                            '</div>' +
                            '<div class="marcador-pe-circulo">' +
                                '<div class="marcador-pe-mitad marcador-pe-plaga" style="background:' + colorPlaga + '"></div>' +
                                '<div class="marcador-pe-mitad marcador-pe-enfermedad" style="background:' + colorEnfermedad + '"></div>' +
                            '</div>' +
                        '</div>';

                    return L.divIcon({
                        className: 'marcador-pe-wrapper',
                        html: html,
                        iconSize: [28, 32],
                        iconAnchor: [14, 21],
                        tooltipAnchor: [0, -16]
                    });
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
                        ' • Cantidad de plagas: ' + (p.totalCantidadPlaga || 0);
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
                    const estado = textoEstado(p.status);
                    const totalPlaga = p.totalCantidadPlaga || 0;
                    const fecha = (p.capturas && p.capturas.length > 0)
                        ? (p.capturas[0].fecha || 'No registrado')
                        : 'No registrado';
                    const resumen = obtenerResumenCaptura(p);
                    const coordenadas = formatearCoordenadasPunto(p);
                    const plagaTexto = p.plagaTexto || 'Sin evaluar';
                    const enfermedadTexto = p.enfermedadTexto || 'Sin evaluar';
                    const plagaColor = p.plagaColor || '#16A34A';
                    const enfermedadColor = p.enfermedadColor || '#16A34A';

                    let html = '';
                    html += '<div class="sheet-handle"></div>';
                    html += '<div class="sheet-header">';
                    html += '<div>';
                    html += '<div class="sheet-title">Punto ' + escaparHtml(p.numero) + '</div>';
                    html += '<div class="sheet-badges">';
                    html += '<span class="badge" style="background:#F7F8F7;color:#263238;border:1px solid #E0E0E0"><span style="color:' + plagaColor + '">●</span> P · ' + escaparHtml(plagaTexto) + '</span>';
                    html += '<span class="badge" style="background:#F7F8F7;color:#263238;border:1px solid #E0E0E0"><span style="color:' + enfermedadColor + '">●</span> E · ' + escaparHtml(enfermedadTexto) + '</span>';
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
                    html += '<div class="sheet-field"><div class="sheet-label">Cantidad de plagas</div><div class="sheet-value">' + escaparHtml(totalPlaga) + '</div></div>';
                    html += '<div class="sheet-field"><div class="sheet-label">Radio</div><div class="sheet-value">' + escaparHtml(p.radius) + ' m</div></div>';
                    html += '<div class="sheet-field sheet-field-wide"><div class="sheet-label">Coordenadas</div><div class="sheet-value">' + escaparHtml(coordenadas) + '</div></div>';
                    html += '</div>';

                    return html;
                }

                let marcadorSeleccionado = null;
                let puntoMarcadorSeleccionado = null;

                function resaltarMarcador(marker, p, activo) {
                    const elemento = marker && marker.getElement ? marker.getElement() : null;
                    if (elemento) {
                        if (activo) elemento.classList.add('selected-marker-ring');
                        else elemento.classList.remove('selected-marker-ring');
                    }

                    if (marker && marker.setRadius) {
                        marker.setRadius(activo ? 15 : 11);
                    }

                    if (marker && marker.setStyle && p && p.status !== 'completed') {
                        marker.setStyle({
                            color: activo ? '#FFFFFF' : '#1A1A1A',
                            weight: activo ? 4 : 2
                        });
                    }

                    if (marker && marker.setZIndexOffset) {
                        marker.setZIndexOffset(activo ? 1000 : 0);
                    }

                    if (activo && marker && marker.bringToFront) {
                        marker.bringToFront();
                    }
                }

                function seleccionarMarcador(marker, p) {
                    try {
                        if (marcadorSeleccionado) {
                            resaltarMarcador(
                                marcadorSeleccionado,
                                puntoMarcadorSeleccionado,
                                false
                            );
                        }

                        marcadorSeleccionado = marker;
                        puntoMarcadorSeleccionado = p;
                        puntoSeleccionadoActual = p;
                        resaltarMarcador(marker, p, true);

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
                        sheet.style.transform = 'translateY(0)';
                        sheet.style.opacity = '1';
                        sheet.style.display = 'block';
                        sheet.classList.remove('dragging');
                        sheet.classList.add('active');
                        prepararGestoCerrarDetalle();
                    } catch (err) {
                        alert(textoPlanoPunto(p));
                    }
                }

                let gestoDetallePreparado = false;
                let gestoDetalleInicioY = 0;
                let gestoDetalleUltimoY = 0;
                let gestoDetalleInicioTiempo = 0;
                let gestoDetalleArrastrando = false;
                let gestoDetallePermitido = false;

                function restaurarPosicionDetalle(sheet) {
                    if (!sheet) return;
                    sheet.classList.remove('dragging');
                    sheet.style.transform = 'translateY(0)';
                    sheet.style.opacity = '1';
                }

                function cerrarDetalleConAnimacion(sheet) {
                    if (!sheet) {
                        cerrarDetallePunto();
                        return;
                    }

                    sheet.classList.remove('dragging');
                    sheet.style.transform = 'translateY(100%)';
                    sheet.style.opacity = '0.72';

                    window.setTimeout(function() {
                        cerrarDetallePunto();
                    }, 180);
                }

                function prepararGestoCerrarDetalle() {
                    const sheet = document.getElementById('point-sheet');
                    if (!sheet || gestoDetallePreparado) return;

                    gestoDetallePreparado = true;

                    sheet.addEventListener('touchstart', function(event) {
                        if (!sheet.classList.contains('active')) return;
                        if (!event.touches || event.touches.length !== 1) return;

                        const touch = event.touches[0];
                        const rect = sheet.getBoundingClientRect();
                        const inicioEnCabecera = touch.clientY <= rect.top + 118;
                        const contenidoEnInicio = sheet.scrollTop <= 1;

                        gestoDetallePermitido = inicioEnCabecera || contenidoEnInicio;
                        gestoDetalleArrastrando = false;
                        gestoDetalleInicioY = touch.clientY;
                        gestoDetalleUltimoY = touch.clientY;
                        gestoDetalleInicioTiempo = Date.now();
                    }, { passive: true });

                    sheet.addEventListener('touchmove', function(event) {
                        if (!gestoDetallePermitido) return;
                        if (!event.touches || event.touches.length !== 1) return;

                        const touch = event.touches[0];
                        const desplazamiento = touch.clientY - gestoDetalleInicioY;

                        // Al mover hacia arriba se mantiene el desplazamiento normal del contenido.
                        if (desplazamiento <= 0) {
                            if (gestoDetalleArrastrando) {
                                restaurarPosicionDetalle(sheet);
                                gestoDetalleArrastrando = false;
                            }
                            return;
                        }

                        // Si el contenido ya está desplazado, primero se permite volver al inicio.
                        if (sheet.scrollTop > 1 && !gestoDetalleArrastrando) return;

                        gestoDetalleArrastrando = true;
                        gestoDetalleUltimoY = touch.clientY;
                        sheet.classList.add('dragging');
                        sheet.style.transform = 'translateY(' + desplazamiento + 'px)';
                        sheet.style.opacity = String(Math.max(0.72, 1 - desplazamiento / 700));
                        event.preventDefault();
                    }, { passive: false });

                    sheet.addEventListener('touchend', function() {
                        if (!gestoDetallePermitido) return;

                        const desplazamiento = Math.max(0, gestoDetalleUltimoY - gestoDetalleInicioY);
                        const duracion = Math.max(1, Date.now() - gestoDetalleInicioTiempo);
                        const velocidad = desplazamiento / duracion;
                        const umbralDistancia = Math.max(90, sheet.clientHeight * 0.18);
                        const debeCerrar = gestoDetalleArrastrando && (
                            desplazamiento >= umbralDistancia ||
                            (desplazamiento >= 42 && velocidad >= 0.55)
                        );

                        gestoDetallePermitido = false;
                        gestoDetalleArrastrando = false;

                        if (debeCerrar) {
                            cerrarDetalleConAnimacion(sheet);
                        } else {
                            restaurarPosicionDetalle(sheet);
                        }
                    }, { passive: true });

                    sheet.addEventListener('touchcancel', function() {
                        gestoDetallePermitido = false;
                        gestoDetalleArrastrando = false;
                        restaurarPosicionDetalle(sheet);
                    }, { passive: true });
                }

                function cerrarDetallePunto() {
                    const sheet = document.getElementById('point-sheet');
                    if (sheet) {
                        sheet.classList.remove('active');
                        sheet.classList.remove('dragging');
                        sheet.style.transform = 'translateY(0)';
                        sheet.style.opacity = '1';
                        sheet.style.display = 'none';
                        sheet.innerHTML = '';
                    }

                    if (marcadorSeleccionado) {
                        resaltarMarcador(
                            marcadorSeleccionado,
                            puntoMarcadorSeleccionado,
                            false
                        );
                    }

                    marcadorSeleccionado = null;
                    puntoMarcadorSeleccionado = null;
                    puntoSeleccionadoActual = null;
                    cerrarModalDetalle();
                }

                function textoPlanoPunto(p) {
                    let txt = 'Punto ' + p.numero + '\nEstado: ' + textoEstado(p.status);
                    if (p.status === 'completed') {
                        txt += '\nPlaga (P): ' + (p.plagaTexto || 'Sin evaluar');
                        txt += '\nEnfermedad (E): ' + (p.enfermedadTexto || 'Sin evaluar');
                    }
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
                        const px = Number(x(p.lon).toFixed(2));
                        const py = Number(y(p.lat).toFixed(2));
                        const evento = "alert('" + escaparParaAlert(textoPlanoPunto(p)) + "')";

                        if (p.status === 'completed') {
                            const r = 3.1;
                            const colorP = p.plagaColor || '#16A34A';
                            const colorE = p.enfermedadColor || '#16A34A';
                            const top = (py - r).toFixed(2);
                            const bottom = (py + r).toFixed(2);

                            svg += '<g onclick="' + evento + '" style="cursor:pointer">';
                            svg += '<path d="M ' + px + ' ' + top + ' A ' + r + ' ' + r + ' 0 0 0 ' + px + ' ' + bottom + ' L ' + px + ' ' + top + ' Z" fill="' + colorP + '" />';
                            svg += '<path d="M ' + px + ' ' + top + ' A ' + r + ' ' + r + ' 0 0 1 ' + px + ' ' + bottom + ' L ' + px + ' ' + top + ' Z" fill="' + colorE + '" />';
                            svg += '<circle cx="' + px + '" cy="' + py + '" r="' + r + '" fill="none" stroke="#17211B" stroke-width="0.75" />';
                            svg += '<line x1="' + px + '" y1="' + top + '" x2="' + px + '" y2="' + bottom + '" stroke="#17211B" stroke-width="0.45" />';
                            svg += '<text x="' + (px - 1.7).toFixed(2) + '" y="' + (py - 4.5).toFixed(2) + '" font-size="2.3" text-anchor="middle" fill="#123D1F" font-weight="bold">P</text>';
                            svg += '<text x="' + (px + 1.7).toFixed(2) + '" y="' + (py - 4.5).toFixed(2) + '" font-size="2.3" text-anchor="middle" fill="#123D1F" font-weight="bold">E</text>';
                            svg += '</g>';
                        } else {
                            const color = colorEstado(p.status, p);
                            svg += '<circle cx="' + px + '" cy="' + py + '" r="3.4" fill="' + color + '" stroke="#1A1A1A" stroke-width="0.7" onclick="' + evento + '" />';
                        }

                        svg += '<text x="' + px + '" y="' + (py - 6.4).toFixed(2) + '" font-size="2.3" text-anchor="middle" fill="#123D1F" font-weight="bold">' + p.numero + '</text>';
                    });

                    svg += '</svg>';

                    if (pantallaCompleta) {
                        svg += '<div class="legend" style="position:absolute;${posicionLeyendaFallback}z-index:2">' +
                            '<div class="legend-title">Semáforo P / E</div>' +
                            '<b>P</b> = Plaga · <b>E</b> = Enfermedad<br>' +
                            '<span class="dot" style="background:#16A34A"></span>Verde: sin plaga / enfermedad no presente<br>' +
                            '<span class="dot" style="background:#FACC15"></span>Amarillo: inicio / severidad menor<br>' +
                            '<span class="dot" style="background:#F97316"></span>Naranja: desarrollo / severidad mayor<br>' +
                            '<span class="dot" style="background:#DC2626"></span>Rojo: avanzado / supera umbral</div>';
                    } else {
                        svg += '<div class="leyendas-pe-control" style="position:absolute;left:12px;bottom:12px;z-index:2">' +
                            '<div class="leyenda-pe-card">' +
                                '<div class="leyenda-pe-titulo">P · Plagas</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#16A34A"></span>Sin plaga</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#FACC15"></span>Severidad menor</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#F97316"></span>Severidad mayor</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#DC2626"></span>Severidad alta</div>' +
                            '</div>' +
                            '<div class="leyenda-pe-card">' +
                                '<div class="leyenda-pe-titulo">E · Enfermedades</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#16A34A"></span>Sin presencia</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#FACC15"></span>Baja</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#F97316"></span>Media</div>' +
                                '<div class="leyenda-pe-fila"><span class="dot" style="background:#DC2626"></span>Alta</div>' +
                            '</div>' +
                        '</div>';
                    }
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
                                    padding: pantallaCompleta ? [26, 26] : [12, 12],
                                    maxZoom: pantallaCompleta ? 19 : 19
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

                        const capaDiscos = L.layerGroup();
                        const capaMarcadores = L.layerGroup().addTo(map);
                        const CapaCalorSimple = crearCapaCalorSimple(puntos);
                        const capaCalor = new CapaCalorSimple(puntos);
                        let visualizacionActual = 'discos';

                        puntos.forEach(function(p) {
                            const colorBase = colorEstado(p.status, p);
                            const colorMancha = colorGlobalPunto(p);

                            /*
                             * Círculo visual fijo en píxeles. L.circle usaba metros y
                             * cambiaba de tamaño al hacer zoom. El radio real del punto
                             * se conserva en p.radius para los cálculos de superficie.
                             */
                            L.circleMarker([p.lat, p.lon], {
                                radius: 13,
                                color: colorMancha,
                                weight: p.conProblema ? 2 : 1.25,
                                opacity: 0.92,
                                fillColor: colorMancha,
                                fillOpacity: p.status === 'completed' ? 0.34 : 0.18,
                                interactive: false
                            }).addTo(capaDiscos);

                            let marker;

                            if (p.status === 'completed') {
                                marker = L.marker([p.lat, p.lon], {
                                    icon: crearIconoPuntoDividido(p),
                                    keyboard: false,
                                    riseOnHover: true,
                                    riseOffset: 500
                                }).addTo(capaMarcadores);
                            } else {
                                marker = L.circleMarker([p.lat, p.lon], {
                                    radius: 9,
                                    color: '#1A1A1A',
                                    weight: 2,
                                    fillColor: p.status === 'cancelled' ? '#6B7280' : '#16A34A',
                                    fillOpacity: 0.95
                                }).addTo(capaMarcadores);
                            }

                            const tooltip = p.status === 'completed'
                                ? 'Punto ' + p.numero +
                                    '<br>P · ' + escaparHtml(p.plagaTexto || 'Sin evaluar') +
                                    '<br>E · ' + escaparHtml(p.enfermedadTexto || 'Sin evaluar')
                                : 'Punto ' + p.numero + ' • ' + textoEstado(p.status);

                            marker.bindTooltip(tooltip, {
                                permanent: false,
                                direction: 'top',
                                opacity: 0.97
                            });

                            marker.on('click', function(e) {
                                if (e && e.originalEvent) {
                                    L.DomEvent.stopPropagation(e.originalEvent);
                                    L.DomEvent.preventDefault(e.originalEvent);
                                }
                                seleccionarMarcador(marker, p);
                            });
                        });

                        capaDiscos.addTo(map);

                        function actualizarBotonesVisualizacion() {
                            const btnCalor = document.getElementById('btn-mapa-calor');
                            const btnDiscos = document.getElementById('btn-discos');
                            if (btnCalor) btnCalor.classList.toggle('active', visualizacionActual === 'calor');
                            if (btnDiscos) btnDiscos.classList.toggle('active', visualizacionActual === 'discos');
                        }

                        function cambiarVisualizacion(tipo) {
                            visualizacionActual = tipo === 'calor' ? 'calor' : 'discos';

                            if (visualizacionActual === 'calor') {
                                if (map.hasLayer(capaDiscos)) map.removeLayer(capaDiscos);
                                if (!map.hasLayer(capaCalor)) capaCalor.addTo(map);
                            } else {
                                if (map.hasLayer(capaCalor)) map.removeLayer(capaCalor);
                                if (!map.hasLayer(capaDiscos)) capaDiscos.addTo(map);
                            }

                            actualizarBotonesVisualizacion();
                        }

                        const resumenSuperficie = calcularResumenSuperficie();
                        const radiosDisponibles = puntos
                            .map(function(p) { return Number(p.radius || 0); })
                            .filter(function(r) { return isFinite(r) && r > 0; });
                        const radiosUnicos = radiosDisponibles.filter(function(r, i, arr) {
                            return arr.findIndex(function(v) { return Math.abs(v - r) < 0.01; }) === i;
                        });
                        const textoRadio = radiosUnicos.length === 1
                            ? 'MANCHAS DE ' + radiosUnicos[0].toFixed(1).replace('.0', '') + ' M'
                            : radiosUnicos.length > 1
                                ? 'RADIOS VARIABLES'
                                : 'RADIO SIN DEFINIR';

                        const controlLeyendasPE = L.control({ position: 'bottomleft' });
                        controlLeyendasPE.onAdd = function() {
                            const div = L.DomUtil.create('div', 'leyendas-pe-control');

                            div.innerHTML =
                                '<div class="leyenda-pe-card">' +
                                    '<div class="leyenda-pe-titulo">P · Plagas</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#16A34A"></span>Sin plaga</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#FACC15"></span>Severidad menor</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#F97316"></span>Severidad mayor</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#DC2626"></span>Severidad alta</div>' +
                                '</div>' +
                                '<div class="leyenda-pe-card">' +
                                    '<div class="leyenda-pe-titulo">E · Enfermedades</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#16A34A"></span>Sin presencia</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#FACC15"></span>Baja</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#F97316"></span>Media</div>' +
                                    '<div class="leyenda-pe-fila"><span class="dot" style="background:#DC2626"></span>Alta</div>' +
                                '</div>';

                            L.DomEvent.disableClickPropagation(div);
                            L.DomEvent.disableScrollPropagation(div);
                            return div;
                        };

                        const controlResumen = L.control({ position: 'bottomleft' });
                        controlResumen.onAdd = function() {
                            const div = L.DomUtil.create('div', 'resumen-superficie');
                            const areaProblema = resumenSuperficie.disponible
                                ? resumenSuperficie.areaProblemaHa.toFixed(2) + ' ha (' + resumenSuperficie.porcentajeProblema.toFixed(1) + '%)'
                                : 'Sin polígono';
                            const areaSinProblema = resumenSuperficie.disponible
                                ? resumenSuperficie.areaSinProblemaHa.toFixed(2) + ' ha (' + resumenSuperficie.porcentajeSinProblema.toFixed(1) + '%)'
                                : 'Sin polígono';

                            div.innerHTML =
                                '<div class="resumen-titulo">Presencia</div>' +
                                '<div class="resumen-info"><span class="dot" style="background:#DC2626"></span>Crítica</div>' +
                                '<div class="resumen-info"><span class="dot" style="background:#F97316"></span>Advertencia</div>' +
                                '<div class="resumen-info"><span class="dot" style="background:#16A34A"></span>Baja / Sin monitorear</div>' +
                                '<div class="resumen-subtitulo">SUPERFICIE (' + escaparHtml(textoRadio) + ')</div>' +
                                '<div class="resumen-fila">' +
                                    '<div class="resumen-fila-label"><span class="dot" style="background:#DC2626"></span>Con problemas</div>' +
                                    '<div class="resumen-valor">' + escaparHtml(areaProblema) + '</div>' +
                                '</div>' +
                                '<div class="resumen-fila">' +
                                    '<div class="resumen-fila-label"><span class="dot" style="background:#16A34A"></span>Baja / Sin monitoreo</div>' +
                                    '<div class="resumen-valor">' + escaparHtml(areaSinProblema) + '</div>' +
                                '</div>' +
                                '<div class="selector-vista">' +
                                    '<button type="button" id="btn-mapa-calor">Mapa de calor</button>' +
                                    '<button type="button" id="btn-discos" class="active">Discos</button>' +
                                '</div>' +
                                '<div class="resumen-nota">La superficie es una estimación que recorta las manchas al polígono y evita contar dos veces las zonas superpuestas.</div>';

                            L.DomEvent.disableClickPropagation(div);
                            L.DomEvent.disableScrollPropagation(div);

                            setTimeout(function() {
                                const btnCalor = document.getElementById('btn-mapa-calor');
                                const btnDiscos = document.getElementById('btn-discos');
                                if (btnCalor) btnCalor.addEventListener('click', function() { cambiarVisualizacion('calor'); });
                                if (btnDiscos) btnDiscos.addEventListener('click', function() { cambiarVisualizacion('discos'); });
                                actualizarBotonesVisualizacion();
                            }, 0);

                            return div;
                        };
                        if (pantallaCompleta) {
                            controlResumen.addTo(map);
                        } else {
                            controlLeyendasPE.addTo(map);
                        }

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

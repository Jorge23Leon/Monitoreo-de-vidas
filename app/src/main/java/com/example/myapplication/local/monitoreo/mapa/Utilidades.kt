package com.example.myapplication.local.monitoreo.mapa

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.monitoreo.severidad.RangosSeveridad
import com.example.myapplication.local.monitoreo.severidad.calcularSeveridadPorPunto
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

private const val COLOR_MAPA_VERDE = "#16A34A"
private const val COLOR_MAPA_AMARILLO = "#FACC15"
private const val COLOR_MAPA_NARANJA = "#F97316"
private const val COLOR_MAPA_ROJO = "#DC2626"
internal const val PRECISION_GPS_MAXIMA_CAPTURA_M = 35f
private const val PRECISION_GPS_MAXIMA_LECTURA_M = 60f
private const val ANTIGUEDAD_GPS_MAXIMA_CAPTURA_MS = 15_000L
private const val ANTIGUEDAD_GPS_ULTIMA_CONOCIDA_MS = 120_000L

internal data class UbicacionGpsMapa(
    val lat: Double,
    val lon: Double,
    val latCaptura: Double,
    val lonCaptura: Double,
    val precisionMetros: Float,
    val tiempoMillis: Long,
    val proveedor: String?
) {
    val coordenadasVisuales: Pair<Double, Double>
        get() = lat to lon

    val coordenadasCaptura: Pair<Double, Double>
        get() = latCaptura to lonCaptura
}

internal fun ubicacionGpsListaParaCaptura(
    ubicacion: UbicacionGpsMapa?,
    ahoraMillis: Long = System.currentTimeMillis()
): Boolean {
    if (ubicacion == null) return false

    val antiguedadMillis = ahoraMillis - ubicacion.tiempoMillis
    return ubicacion.precisionMetros <= PRECISION_GPS_MAXIMA_CAPTURA_M &&
            antiguedadMillis >= -5_000L &&
            antiguedadMillis <= ANTIGUEDAD_GPS_MAXIMA_CAPTURA_MS
}

/**
 * Rechaza lecturas imprecisas, antiguas o con saltos físicamente improbables.
 * Las lecturas válidas se suavizan para evitar que el marcador salte en pantalla.
 */
internal fun filtrarUbicacionGpsMapa(
    anterior: UbicacionGpsMapa?,
    nueva: Location,
    ahoraMillis: Long = System.currentTimeMillis()
): UbicacionGpsMapa? {
    val lat = nueva.latitude
    val lon = nueva.longitude
    if (!lat.isFinite() || !lon.isFinite()) return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    if (!nueva.hasAccuracy()) return null

    val precision = nueva.accuracy
    if (!precision.isFinite() || precision <= 0f) return null
    if (precision > PRECISION_GPS_MAXIMA_LECTURA_M) return null

    val tiempo = nueva.time.takeIf { it > 0L } ?: ahoraMillis
    val antiguedadMillis = ahoraMillis - tiempo
    if (antiguedadMillis < -5_000L) return null
    if (antiguedadMillis > ANTIGUEDAD_GPS_ULTIMA_CONOCIDA_MS) return null

    if (anterior == null) {
        return UbicacionGpsMapa(
            lat = lat,
            lon = lon,
            latCaptura = lat,
            lonCaptura = lon,
            precisionMetros = precision,
            tiempoMillis = tiempo,
            proveedor = nueva.provider
        )
    }

    val diferenciaTiempoMs = tiempo - anterior.tiempoMillis
    if (diferenciaTiempoMs <= 0L) return null

    if (
        diferenciaTiempoMs < 12_000L &&
        precision > anterior.precisionMetros * 1.8f &&
        precision - anterior.precisionMetros > 8f
    ) {
        return null
    }

    val distanciaResultado = FloatArray(1)
    Location.distanceBetween(
        anterior.latCaptura,
        anterior.lonCaptura,
        lat,
        lon,
        distanciaResultado
    )
    val distanciaMetros = distanciaResultado[0]
    val segundos = max(1f, diferenciaTiempoMs / 1_000f)
    val velocidadMetrosSegundo = distanciaMetros / segundos
    val saltoPermitido = max(
        25f,
        max(anterior.precisionMetros, precision) * 2.5f
    )

    if (
        diferenciaTiempoMs < 10_000L &&
        distanciaMetros > saltoPermitido &&
        velocidadMetrosSegundo > 8.5f
    ) {
        return null
    }

    val factorSuavizado = when {
        precision <= anterior.precisionMetros * 0.7f -> 0.80
        distanciaMetros > max(8f, precision) -> 0.65
        else -> 0.35
    }

    return UbicacionGpsMapa(
        lat = anterior.lat + (lat - anterior.lat) * factorSuavizado,
        lon = anterior.lon + (lon - anterior.lon) * factorSuavizado,
        latCaptura = lat,
        lonCaptura = lon,
        precisionMetros = precision,
        tiempoMillis = tiempo,
        proveedor = nueva.provider
    )
}

private data class EstadoMitadMapa(
    val color: String,
    val texto: String,
    val nivel: Int,
    val total: Int = 0
)

internal fun crearVerticesJson(
    vertices: List<LocalPlotVertexEntity>
): String {
    val array = JSONArray()

    vertices
        .sortedBy { it.level }
        .forEach { vertex ->
            array.put(
                JSONObject().apply {
                    put("level", vertex.level)
                    put("lat", vertex.lat)
                    put("lon", vertex.lon)
                }
            )
        }

    return array.toString()
}

private fun esEnfermedadMapa(
    fito: LocalPhytosanitaryCatalogEntity?
): Boolean {
    val tipo = fito
        ?.type
        ?.trim()
        ?.lowercase()
        .orEmpty()

    return tipo.contains("enfermedad") ||
            tipo.contains("disease")
}

private fun esSinPlagaMapa(
    fito: LocalPhytosanitaryCatalogEntity?
): Boolean {
    if (fito == null) return false

    val texto = listOf(
        fito.name,
        fito.type.orEmpty(),
        fito.description.orEmpty()
    )
        .joinToString(" ")
        .uppercase()
        .replace("Á", "A")
        .replace("É", "E")
        .replace("Í", "I")
        .replace("Ó", "O")
        .replace("Ú", "U")

    return texto.contains("SIN_PLAGA") ||
            texto.contains("SIN PLAGA") ||
            texto.contains("NO PLAGA") ||
            texto.trim() == "NONE"
}

private fun calcularEstadoPlagaMapa(
    checkpointsPunto: List<LocalPhytomonitoringCheckpointEntity>,
    catalogoPorId: Map<Long, LocalPhytosanitaryCatalogEntity>
): EstadoMitadMapa {
    val checkpointsPlaga = checkpointsPunto.filter { checkpoint ->
        val fito = checkpoint.idPhytosanitary
            ?.let { id -> catalogoPorId[id] }

        fito != null &&
                !esEnfermedadMapa(fito) &&
                !esSinPlagaMapa(fito)
    }

    val totalPlagas = checkpointsPlaga.sumOf { checkpoint ->
        (checkpoint.qty ?: 0).coerceAtLeast(0)
    }

    val existeRegistroSinPlaga = checkpointsPunto.any { checkpoint ->
        val fito = checkpoint.idPhytosanitary
            ?.let { id -> catalogoPorId[id] }

        checkpoint.idPhytosanitary == null ||
                esSinPlagaMapa(fito)
    }

    if (checkpointsPlaga.isEmpty() || totalPlagas <= 0) {
        return EstadoMitadMapa(
            color = COLOR_MAPA_VERDE,
            texto = "Sin plaga detectada",
            nivel = 0,
            total = 0
        )
    }

    /*
     * calcularSeveridadPorPunto trabaja solamente con las capturas
     * de plagas y devuelve el nivel más grave del punto.
     */
    val severidadPlaga = calcularSeveridadPorPunto(
        checkpointsPunto = checkpointsPlaga,
        catalogoPorId = catalogoPorId,
        rangos = RangosSeveridad()
    )

    return EstadoMitadMapa(
        color = severidadPlaga.nivelFinal.colorHex,
        texto = severidadPlaga.nivelFinal.etiqueta,
        nivel = severidadPlaga.nivelFinal.orden,
        total = severidadPlaga.totalCantidadPunto
    )
}

private fun prioridadEnfermedadMapa(
    checkpoint: LocalPhytomonitoringCheckpointEntity
): Int {
    if (checkpoint.presenceStatus == 0) {
        return 1
    }

    val fase = checkpoint.stage
        ?.trim()
        ?.lowercase()
        .orEmpty()

    return when {
        fase.contains("avanz") ||
                fase.contains("terminal") -> 4

        fase.contains("desarrollo") -> 3
        fase.contains("inicio") -> 2

        /*
         * Respaldo para registros antiguos presentes sin fase.
         * Se muestra como Inicio para no perder la alerta.
         */
        checkpoint.presenceStatus == 1 -> 2

        else -> 0
    }
}

private fun calcularEstadoEnfermedadMapa(
    checkpointsPunto: List<LocalPhytomonitoringCheckpointEntity>,
    catalogoPorId: Map<Long, LocalPhytosanitaryCatalogEntity>
): EstadoMitadMapa {
    val checkpointsEnfermedad = checkpointsPunto.filter { checkpoint ->
        val fito = checkpoint.idPhytosanitary
            ?.let { id -> catalogoPorId[id] }

        esEnfermedadMapa(fito)
    }

    if (checkpointsEnfermedad.isEmpty()) {
        return EstadoMitadMapa(
            color = COLOR_MAPA_VERDE,
            texto = "Sin presencia",
            nivel = 1
        )
    }

    val nivelMayor = checkpointsEnfermedad
        .maxOfOrNull(::prioridadEnfermedadMapa)
        ?: 0

    return when (nivelMayor) {
        4 -> EstadoMitadMapa(
            color = COLOR_MAPA_ROJO,
            texto = "Alta",
            nivel = 4
        )

        3 -> EstadoMitadMapa(
            color = COLOR_MAPA_NARANJA,
            texto = "Media",
            nivel = 3
        )

        2 -> EstadoMitadMapa(
            color = COLOR_MAPA_AMARILLO,
            texto = "Baja",
            nivel = 2
        )

        1 -> EstadoMitadMapa(
            color = COLOR_MAPA_VERDE,
            texto = "Sin presencia",
            nivel = 1
        )

        else -> EstadoMitadMapa(
            color = COLOR_MAPA_VERDE,
            texto = "Sin presencia",
            nivel = 1
        )
    }
}

internal fun crearPuntosJson(
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    catalogo: List<LocalPhytosanitaryCatalogEntity> = emptyList()
): String {
    val checkpointsPorPunto = checkpoints.groupBy { checkpoint ->
        checkpoint.idTargetPoint
    }

    val catalogoPorId = catalogo.associateBy { item ->
        item.idPhytosanitary
    }

    val idsPuntosConCheckpoint = checkpoints
        .map { checkpoint ->
            checkpoint.idTargetPoint
        }
        .toSet()

    val array = JSONArray()

    puntos
        .sortedBy { punto ->
            punto.idTargetPoint
        }
        .forEach { punto ->
            val statusBase = punto.status
                .trim()
                .lowercase()

            val statusFinal = when {
                statusBase == "cancelled" ||
                        statusBase == "cancelado" ||
                        statusBase == "canceled" -> {
                    "cancelled"
                }

                punto.idTargetPoint in idsPuntosConCheckpoint -> {
                    "completed"
                }

                statusBase == "completed" ||
                        statusBase == "completado" ||
                        statusBase == "capturado" -> {
                    "completed"
                }

                statusBase == "in_progress" ||
                        statusBase == "en proceso" -> {
                    "in_progress"
                }

                else -> {
                    "pending"
                }
            }

            val checkpointsDelPunto = checkpointsPorPunto[
                punto.idTargetPoint
            ].orEmpty()

            val estadoPlaga = calcularEstadoPlagaMapa(
                checkpointsPunto = checkpointsDelPunto,
                catalogoPorId = catalogoPorId
            )

            val estadoEnfermedad = calcularEstadoEnfermedadMapa(
                checkpointsPunto = checkpointsDelPunto,
                catalogoPorId = catalogoPorId
            )

            /*
             * Se conservan los campos anteriores para no romper
             * otras pantallas mientras se prueba el marcador P/E.
             */
            val severidadAnterior = calcularSeveridadPorPunto(
                checkpointsPunto = checkpointsDelPunto,
                catalogoPorId = catalogoPorId,
                rangos = RangosSeveridad()
            )

            val colorSemaforoAnterior = when (statusFinal) {
                "completed" ->
                    severidadAnterior.nivelFinal.colorHex

                "cancelled" ->
                    "#6B7280"

                else ->
                    "#D98A00"
            }

            val textoSemaforoAnterior = when (statusFinal) {
                "completed" ->
                    severidadAnterior.nivelFinal.etiqueta

                "cancelled" ->
                    "Cancelado"

                else ->
                    "Pendiente"
            }

            array.put(
                JSONObject().apply {
                    put("idTargetPoint", punto.idTargetPoint)
                    put("lat", punto.lat)
                    put("lon", punto.lon)
                    put("radius", punto.radiusM)
                    put("radiusM", punto.radiusM)
                    put("status", statusFinal)

                    put(
                        "totalCantidadPunto",
                        severidadAnterior.totalCantidadPunto
                    )
                    put(
                        "severityStatus",
                        textoSemaforoAnterior
                    )
                    put(
                        "severityColor",
                        colorSemaforoAnterior
                    )
                    put(
                        "severityLevel",
                        severidadAnterior.nivelFinal.orden
                    )

                    put("plagaColor", estadoPlaga.color)
                    put("plagaTexto", estadoPlaga.texto)
                    put("plagaNivel", estadoPlaga.nivel)
                    put("totalCantidadPlaga", estadoPlaga.total)

                    put(
                        "enfermedadColor",
                        estadoEnfermedad.color
                    )
                    put(
                        "enfermedadTexto",
                        estadoEnfermedad.texto
                    )
                    put(
                        "enfermedadNivel",
                        estadoEnfermedad.nivel
                    )
                }
            )
        }

    return array.toString()
}

@SuppressLint("MissingPermission")
internal fun obtenerUltimaUbicacion(
    context: Context
): UbicacionGpsMapa? {
    return try {
        val locationManager =
            context.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        val ahoraMillis = System.currentTimeMillis()
        val location = providers
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(
                        provider
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .filter { location ->
                val antiguedadMillis = ahoraMillis - location.time
                location.hasAccuracy() &&
                        location.accuracy > 0f &&
                        location.accuracy <= PRECISION_GPS_MAXIMA_LECTURA_M &&
                        antiguedadMillis >= -5_000L &&
                        antiguedadMillis <= ANTIGUEDAD_GPS_ULTIMA_CONOCIDA_MS
            }
            .minByOrNull { location ->
                location.accuracy +
                        ((ahoraMillis - location.time).coerceAtLeast(0L) / 1_000f) * 0.25f
            }

        location?.let { filtrarUbicacionGpsMapa(null, it, ahoraMillis) }
    } catch (_: Exception) {
        null
    }
}

internal fun hayInternet(
    context: Context
): Boolean {
    return try {
        val connectivityManager =
            context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network =
            connectivityManager.activeNetwork
                ?: return false

        val capabilities =
            connectivityManager.getNetworkCapabilities(
                network
            ) ?: return false

        capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
                capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
    } catch (_: Exception) {
        false
    }
}

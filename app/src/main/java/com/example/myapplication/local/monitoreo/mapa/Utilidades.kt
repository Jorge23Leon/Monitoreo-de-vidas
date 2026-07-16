package com.example.myapplication.local.monitoreo.mapa

import android.annotation.SuppressLint
import android.content.Context
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

private const val COLOR_MAPA_VERDE = "#16A34A"
private const val COLOR_MAPA_AMARILLO = "#FACC15"
private const val COLOR_MAPA_NARANJA = "#F97316"
private const val COLOR_MAPA_ROJO = "#DC2626"

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
): Pair<Double, Double>? {
    return try {
        val locationManager =
            context.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

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
            .maxByOrNull { it.time }

        location?.let {
            Pair(it.latitude, it.longitude)
        }
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
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

internal fun crearVerticesJson(vertices: List<LocalPlotVertexEntity>): String {
    val array = JSONArray()

    vertices.sortedBy { it.level }.forEach { vertex ->
        val obj = JSONObject()
        obj.put("level", vertex.level)
        obj.put("lat", vertex.lat)
        obj.put("lon", vertex.lon)
        array.put(obj)
    }

    return array.toString()
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
        .map { checkpoint -> checkpoint.idTargetPoint }
        .toSet()

    val array = JSONArray()

    puntos.forEach { punto ->
        val statusBase = punto.status.trim().lowercase()

        val statusFinal = when {
            statusBase == "cancelled" ||
                    statusBase == "cancelado" ||
                    statusBase == "canceled" -> "cancelled"

            punto.idTargetPoint in idsPuntosConCheckpoint -> "completed"

            statusBase == "completed" ||
                    statusBase == "completado" ||
                    statusBase == "capturado" -> "completed"

            statusBase == "in_progress" ||
                    statusBase == "en proceso" -> "in_progress"

            else -> "pending"
        }

        val checkpointsDelPunto = checkpointsPorPunto[punto.idTargetPoint].orEmpty()

        val severidad = calcularSeveridadPorPunto(
            checkpointsPunto = checkpointsDelPunto,
            catalogoPorId = catalogoPorId,
            rangos = RangosSeveridad()
        )

        val colorSemaforo = when (statusFinal) {
            "completed" -> severidad.nivelFinal.colorHex
            "cancelled" -> "#6B7280"
            else -> "#D98A00"
        }

        val textoSemaforo = when (statusFinal) {
            "completed" -> severidad.nivelFinal.etiqueta
            "cancelled" -> "Cancelado"
            else -> "Pendiente"
        }

        val obj = JSONObject()
        obj.put("idTargetPoint", punto.idTargetPoint)
        obj.put("lat", punto.lat)
        obj.put("lon", punto.lon)
        obj.put("radius", punto.radiusM)
        obj.put("radiusM", punto.radiusM)
        obj.put("status", statusFinal)

        // Semáforo GLOBAL por punto:
        // suma plagas + enfermedades + todas sus fases del mismo punto.
        obj.put("totalCantidadPunto", severidad.totalCantidadPunto)
        obj.put("severityStatus", textoSemaforo)
        obj.put("severityColor", colorSemaforo)
        obj.put("severityLevel", severidad.nivelFinal.orden)

        array.put(obj)
    }

    return array.toString()
}

@SuppressLint("MissingPermission")
internal fun obtenerUltimaUbicacion(context: Context): Pair<Double, Double>? {
    return try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        val location = providers
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
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

internal fun hayInternet(context: Context): Boolean {
    return try {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false

        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        false
    }
}

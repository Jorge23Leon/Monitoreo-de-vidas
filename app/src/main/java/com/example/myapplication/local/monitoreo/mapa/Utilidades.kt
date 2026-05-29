package com.example.myapplication.local.monitoreo.mapa

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
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
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>
): String {
    val idsPuntosActuales = puntos
        .map { it.idTargetPoint }
        .toSet()

    val idsCapturados = checkpoints
        .filter { checkpoint ->
            checkpoint.idTargetPoint in idsPuntosActuales
        }
        .map { checkpoint ->
            checkpoint.idTargetPoint
        }
        .toSet()

    val array = JSONArray()

    puntos.forEach { punto ->
        val statusBase = punto.status.lowercase().trim()

        val statusFinal = when {
            punto.idTargetPoint in idsCapturados -> "completed"

            statusBase == "completed" ||
                    statusBase == "completado" ||
                    statusBase == "capturado" -> "completed"

            statusBase == "cancelled" ||
                    statusBase == "cancelado" -> "cancelled"

            statusBase == "in_progress" ||
                    statusBase == "en proceso" -> "in_progress"

            else -> "pending"
        }

        val obj = JSONObject()
        obj.put("idTargetPoint", punto.idTargetPoint)
        obj.put("lat", punto.lat)
        obj.put("lon", punto.lon)
        obj.put("radius", punto.radiusM)
        obj.put("status", statusFinal)

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

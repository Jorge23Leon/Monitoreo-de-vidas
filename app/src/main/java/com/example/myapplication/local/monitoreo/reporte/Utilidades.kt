package com.example.myapplication.local.monitoreo.reporte

// CAMBIO PUNTOS/CSV: función central para obtener el número real desde label,
// usada por reporte, mapa y registro de punto.

import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


internal fun textoTipoCatalogo(type: String?): String {
    return when (type?.lowercase()?.trim()) {
        "plaga" -> "Plaga"
        "enfermedad" -> "Enfermedad"
        else -> type ?: "-"
    }
}

internal fun textoEstadoReporteUi(status: String): String {
    return when (status.lowercase().trim()) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado" -> "Completado"
        "cancelled", "cancelado" -> "Cancelado"
        else -> status
    }
}

internal fun formatearFechaReporteUi(fecha: Long?): String {
    if (fecha == null) return "No programada"
    return try {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(fecha))
    } catch (_: Exception) {
        "-"
    }
}

internal fun formatearFechaOpcionalReporteUi(fecha: Long?): String {
    if (fecha == null) return "No registrado"

    return try {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(fecha))
    } catch (_: Exception) {
        "-"
    }
}

internal data class ReporteDataUi(
    val puntos: List<LocalPhytomonitoringTargetPointEntity>,
    val checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    val vertices: List<LocalPlotVertexEntity>,
    val catalogo: List<LocalPhytosanitaryCatalogEntity>,
    val cultivo: String,
    val fotoCultivo: String?,
    val mensajeSync: String? = null
)

internal data class FilaReporteCapturaUi(
    val numeroPunto: Int,
    val lat: Double?,
    val lon: Double?,
    val coordenadas: String,
    val plagaEnfermedad: String,
    val tipo: String,
    val fase: String,
    val cantidad: Int,
    val severidad: String,
    val colorSeveridadHex: String,
    val fechaCaptura: String,
    val notas: String
)

internal fun formatearCoordenadasReporteUi(lat: Double?, lon: Double?): String {
    if (lat == null || lon == null) return "-"
    return String.format(Locale.US, "%.6f, %.6f", lat, lon)
}


/**
 * La API etiqueta los puntos como "Punto 2", "Punto 17", etc.
 * Se usa ese número real en tabla, CSV y mapa. Solo se usa el orden local
 * como respaldo para puntos antiguos que todavía no tienen etiqueta.
 */
internal fun numeroPuntoRealReporteUi(label: String?, fallback: Int): Int {
    val numero = Regex("\\d+")
        .find(label.orEmpty())
        ?.value
        ?.toIntOrNull()

    return numero?.takeIf { it > 0 } ?: fallback
}

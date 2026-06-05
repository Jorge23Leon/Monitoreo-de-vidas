package com.example.myapplication.local.monitoreo.reporte

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
    val fotoCultivo: String?
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

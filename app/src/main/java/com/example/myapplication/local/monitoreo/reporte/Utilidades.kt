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

internal fun esEnfermedadReporte(type: String?): Boolean {
    return type
        ?.trim()
        ?.lowercase()
        ?.contains("enfermedad") == true
}

internal fun esSinPlagaReporte(
    checkpoint: LocalPhytomonitoringCheckpointEntity,
    fito: LocalPhytosanitaryCatalogEntity?
): Boolean {
    if (checkpoint.idPhytosanitary == null) return true

    val texto = listOf(
        fito?.name.orEmpty(),
        fito?.type.orEmpty(),
        fito?.description.orEmpty()
    ).joinToString(" ")
        .trim()
        .uppercase(Locale.getDefault())
        .replace("Á", "A")
        .replace("É", "E")
        .replace("Í", "I")
        .replace("Ó", "O")
        .replace("Ú", "U")

    return texto.contains("SIN_PLAGA") ||
            texto.contains("SIN PLAGA") ||
            texto.contains("NO PLAGA") ||
            texto.contains("AUSENTE")
}

internal fun textoPresenciaFaseReporte(
    checkpoint: LocalPhytomonitoringCheckpointEntity,
    fito: LocalPhytosanitaryCatalogEntity?
): String {
    if (esSinPlagaReporte(checkpoint, fito)) return "-"

    if (esEnfermedadReporte(fito?.type)) {
        if (checkpoint.presenceStatus == 0) return "No presente"

        val fase = checkpoint.stage
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return if (fase == null) {
            "Presente"
        } else {
            "Presente / $fase"
        }
    }

    return checkpoint.stage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "-"
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
    val plagaEnfermedad: String,
    val tipo: String,
    val fase: String,
    val cantidad: Int,
    val severidad: String,
    val colorSeveridadHex: String,
    val fechaCaptura: String,
    val notas: String,

    /** Ruta local válida o URL remota que se muestra mientras se guarda caché offline. */
    val rutaFotoLocal: String? = null,
    val photoRef: String? = null,
    val photoUrl: String? = null,
    val idHeader: Long = 0L,
    val idTargetPoint: Long = 0L,
    val capturedAtMillis: Long? = null
)

internal fun crearNumeroPuntoMapPorCoordenada(
    puntos: List<LocalPhytomonitoringTargetPointEntity>
): Map<Long, Int> {
    val numeroPorCoordenada = linkedMapOf<String, Int>()

    return puntos
        .sortedBy { it.idTargetPoint }
        .associate { punto ->
            // Se usan 6 decimales para considerar la misma ubicación.
            val clave = String.format(
                Locale.US,
                "%.6f,%.6f",
                punto.lat,
                punto.lon
            )

            val numeroPunto = numeroPorCoordenada.getOrPut(clave) {
                numeroPorCoordenada.size + 1
            }

            punto.idTargetPoint to numeroPunto
        }
}

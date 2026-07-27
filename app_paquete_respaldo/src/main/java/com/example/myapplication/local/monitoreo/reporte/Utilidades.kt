package com.example.myapplication.local.monitoreo.reporte

import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
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


internal data class EstadoSeveridadEnfermedadReporteUi(
    val etiqueta: String,
    val colorHex: String,
    val orden: Int
)

internal fun calcularEstadoSeveridadEnfermedadReporte(
    checkpoint: LocalPhytomonitoringCheckpointEntity
): EstadoSeveridadEnfermedadReporteUi {
    if (checkpoint.presenceStatus == 0) {
        return EstadoSeveridadEnfermedadReporteUi(
            etiqueta = "No presente",
            colorHex = "#16A34A",
            orden = 0
        )
    }

    val fase = checkpoint.stage
        ?.trim()
        ?.lowercase(Locale.getDefault())
        .orEmpty()

    return when {
        fase.contains("avanz") || fase.contains("terminal") -> {
            EstadoSeveridadEnfermedadReporteUi(
                etiqueta = "Avanzado",
                colorHex = "#DC2626",
                orden = 3
            )
        }

        fase.contains("desarrollo") -> {
            EstadoSeveridadEnfermedadReporteUi(
                etiqueta = "Desarrollo",
                colorHex = "#F97316",
                orden = 2
            )
        }

        fase.contains("inicio") -> {
            EstadoSeveridadEnfermedadReporteUi(
                etiqueta = "Inicio",
                colorHex = "#FACC15",
                orden = 1
            )
        }

        checkpoint.presenceStatus == 1 -> {
            EstadoSeveridadEnfermedadReporteUi(
                etiqueta = "Presente",
                colorHex = "#FACC15",
                orden = 1
            )
        }

        else -> {
            EstadoSeveridadEnfermedadReporteUi(
                etiqueta = "No presente",
                colorHex = "#16A34A",
                orden = 0
            )
        }
    }
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

internal data class FechaHoraCompactaReporteUi(
    val fecha: String,
    val hora: String
)

internal fun formatearFechaHoraCompactaReporteUi(
    fecha: Long?,
    textoVacio: String
): FechaHoraCompactaReporteUi {
    if (fecha == null) {
        return FechaHoraCompactaReporteUi(
            fecha = textoVacio,
            hora = ""
        )
    }

    return try {
        val locale = Locale("es", "MX")
        val fechaTexto = SimpleDateFormat("EEEE, dd MMM yyyy", locale)
            .format(Date(fecha))
            .replace(".", "")
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }

        val horaTexto = SimpleDateFormat("HH:mm 'h'", locale)
            .format(Date(fecha))

        FechaHoraCompactaReporteUi(
            fecha = fechaTexto,
            hora = horaTexto
        )
    } catch (_: Exception) {
        FechaHoraCompactaReporteUi(
            fecha = "-",
            hora = ""
        )
    }
}

internal fun formatearFechaReporteUi(fecha: Long?): String {
    if (fecha == null) return "No programada"

    return try {
        val locale = Locale("es", "MX")
        SimpleDateFormat("EEE dd MMM yyyy • HH:mm", locale)
            .format(Date(fecha))
            .replace(".", "")
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }
    } catch (_: Exception) {
        "-"
    }
}

internal fun formatearFechaOpcionalReporteUi(fecha: Long?): String {
    if (fecha == null) return "No registrado"

    return try {
        val locale = Locale("es", "MX")
        SimpleDateFormat("dd-MM-yyyy HH:mm", locale).format(Date(fecha))
    } catch (_: Exception) {
        "-"
    }
}

internal data class ReporteDataUi(
    val header: LocalPhytomonitoringHeaderEntity,
    val puntos: List<LocalPhytomonitoringTargetPointEntity>,
    val checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    val vertices: List<LocalPlotVertexEntity>,
    val catalogo: List<LocalPhytosanitaryCatalogEntity>,
    val cultivo: String,
    val fotoCultivo: String?,
    val fotosPendientes: Int,
    val mensajeSync: String? = null
)

internal data class FilaReporteCapturaUi(
    val numeroPunto: Int,
    val lat: Double?,
    val lon: Double?,
    val plagaEnfermedad: String,
    val tipo: String,
    val fase: String,
    val cantidad: String,
    val severidad: String,
    val colorSeveridadHex: String,
    val fechaCaptura: String,
    val notas: String,
    val rutaFotoLocal: String?,
    val photoRef: String?,
    val photoUrl: String?,
    val idHeader: Long,
    val idTargetPoint: Long,
    val capturedAtMillis: Long?
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

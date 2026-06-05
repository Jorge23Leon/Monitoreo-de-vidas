package com.example.myapplication.local.monitoreo.lista

import androidx.compose.ui.graphics.Color
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun obtenerNombreParcelaLista(parcela: LocalPlotEntity): String {
    return parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
}

internal fun esMonitoreoPausadoLista(
    status: String,
    additionalNotes: String
): Boolean {
    val estado = status.trim().lowercase(Locale.getDefault())

    return (estado == "pending" || estado == "pendiente") &&
            additionalNotes.trim().startsWith("PAUSADO:", ignoreCase = true)
}

internal fun textoEstadoLista(
    status: String,
    additionalNotes: String = ""
): String {
    if (esMonitoreoPausadoLista(status, additionalNotes)) {
        return "Pausado"
    }

    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado", "terminado", "cerrado" -> "Completado"
        "cancelled", "cancelado", "canceled" -> "Cancelado"
        else -> status.trim().ifBlank { "Pendiente" }
    }
}

internal fun colorEstadoLista(
    status: String,
    additionalNotes: String = ""
): Color {
    if (esMonitoreoPausadoLista(status, additionalNotes)) {
        return Color(0xFFE69500)
    }

    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> Color(0xFF9A6A00)
        "in_progress", "en proceso", "vigente" -> Color(0xFF255EA8)
        "completed", "completado", "finalizado", "terminado", "cerrado" -> Color(0xFF1F6D2A)
        "cancelled", "cancelado", "canceled" -> Color(0xFFB3261E)
        else -> Color.Black
    }
}

internal fun colorFondoEstadoLista(
    status: String,
    additionalNotes: String = ""
): Color {
    if (esMonitoreoPausadoLista(status, additionalNotes)) {
        return Color(0xFFFFF0D6)
    }

    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> Color(0xFFFFF4CC)
        "in_progress", "en proceso", "vigente" -> Color(0xFFE2F0FF)
        "completed", "completado", "finalizado", "terminado", "cerrado" -> Color(0xFFE4F4DF)
        "cancelled", "cancelado", "canceled" -> Color(0xFFFFE1E1)
        else -> Color(0xFFEDEDED)
    }
}

internal fun colorFondoImagenLista(
    status: String,
    additionalNotes: String = ""
): Color {
    if (esMonitoreoPausadoLista(status, additionalNotes)) {
        return Color(0xFFFFF0D6)
    }

    val estado = status.trim().lowercase(Locale.getDefault())

    return when {
        estado.contains("complet") || estado.contains("final") ||
                estado.contains("terminado") || estado.contains("cerrado") -> Color(0xFFE5F4DF)

        estado.contains("cancel") -> Color(0xFFFFE1E1)

        estado.contains("proceso") || estado.contains("progress") || estado.contains("vigente") -> Color(0xFFE7F0FF)

        else -> Color(0xFFFFF4CC)
    }
}

internal fun iconoCultivoFallbackLista(nombreCultivo: String?): String {
    val cultivo = nombreCultivo?.trim()?.lowercase(Locale.getDefault()) ?: return "🌱"

    return when {
        cultivo.contains("maiz") || cultivo.contains("maíz") -> "🌽"
        cultivo.contains("papa") -> "🥔"
        cultivo.contains("trigo") -> "🌾"
        cultivo.contains("cebolla") -> "🧅"
        cultivo.contains("chile") -> "🌶️"
        cultivo.contains("tomate") || cultivo.contains("jitomate") -> "🍅"
        cultivo.contains("lechuga") -> "🥬"
        cultivo.contains("zanahoria") -> "🥕"
        else -> "🌱"
    }
}

internal fun formatearFechaCorta(fecha: Long?): String {
    if (fecha == null) return "-"

    return try {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}

internal fun formatearFechaResumen(fecha: Long?): String {
    if (fecha == null) return "-"

    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
        sdf.format(Date(fecha)).replace(".", "")
    } catch (e: Exception) {
        "-"
    }
}

internal fun parseFechaInicioLista(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null
    return parseFechaListaFlexible(fechaTexto)
}

internal fun parseFechaFinLista(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null

    val time = parseFechaListaFlexible(fechaTexto) ?: return null

    return Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

private fun parseFechaListaFlexible(texto: String): Long? {
    val formatos = listOf("dd-MM-yyyy", "dd/MM/yyyy")

    formatos.forEach { patron ->
        try {
            val sdf = SimpleDateFormat(patron, Locale.getDefault()).apply {
                isLenient = false
            }
            return sdf.parse(texto)?.time
        } catch (_: Exception) {
        }
    }

    return null
}

internal fun esEstadoCerradoLista(status: String): Boolean {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "completed",
        "completado",
        "finalizado",
        "terminado",
        "cerrado",
        "cancelado",
        "cancelled",
        "canceled" -> true

        else -> false
    }
}
internal fun esEstadoCanceladoLista(status: String): Boolean {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "cancelado",
        "cancelled",
        "canceled" -> true

        else -> false
    }
}
internal fun cumpleEstadoLista(
    status: String,
    estadoFiltro: String,
    additionalNotes: String = ""
): Boolean {
    val estado = status.trim().lowercase(Locale.getDefault())
    val pausado = esMonitoreoPausadoLista(status, additionalNotes)

    return when (estadoFiltro) {
        "Pendiente" -> (estado == "pending" || estado == "pendiente") && !pausado
        "Pausado" -> pausado
        "En proceso" -> estado == "in_progress" || estado == "en proceso" || estado == "vigente"
        "Completado" -> estado == "completed" || estado == "completado" ||
                estado == "finalizado" || estado == "terminado" || estado == "cerrado"
        "Cancelado" -> estado == "cancelled" || estado == "cancelado" || estado == "canceled"
        else -> true
    }
}

internal fun textoCicloLista(ciclo: String?): String {
    val limpio = normalizarCicloBaseLista(ciclo)

    return when (limpio) {
        "" -> "Sin ciclo"
        "p v",
        "pv",
        "p.v",
        "p v 2026",
        "primavera verano",
        "primavera verano 2026",
        "ciclo primavera verano",
        "ciclo primavera verano 2026" -> "Primavera-Verano"

        "o i",
        "oi",
        "o.i",
        "otono invierno",
        "otoño invierno",
        "otono invierno 2026",
        "otoño invierno 2026",
        "ciclo otono invierno",
        "ciclo otoño invierno",
        "ciclo otono invierno 2026",
        "ciclo otoño invierno 2026" -> "Otoño-Invierno"

        else -> ciclo
            .orEmpty()
            .trim()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
            .ifBlank { "Sin ciclo" }
    }
}

internal fun claveCicloLista(ciclo: String?): String {
    return when (val limpio = normalizarCicloBaseLista(ciclo)) {
        "", "sin ciclo" -> "sin_ciclo"

        "p v",
        "pv",
        "p.v",
        "p v 2026",
        "primavera verano",
        "primavera verano 2026",
        "ciclo primavera verano",
        "ciclo primavera verano 2026" -> "primavera_verano"

        "o i",
        "oi",
        "o.i",
        "otono invierno",
        "otoño invierno",
        "otono invierno 2026",
        "otoño invierno 2026",
        "ciclo otono invierno",
        "ciclo otoño invierno",
        "ciclo otono invierno 2026",
        "ciclo otoño invierno 2026" -> "otono_invierno"

        else -> limpio
    }
}

internal fun programasUnicosPorCicloLista(
    programas: List<LocalProgramEntity>
): List<LocalProgramEntity> {
    return programas
        .groupBy { programa -> claveCicloLista(programa.cycle) }
        .mapNotNull { (_, programasDelMismoCiclo) ->
            programasDelMismoCiclo.maxWithOrNull(
                compareBy<LocalProgramEntity> { it.estStartDate ?: Long.MIN_VALUE }
                    .thenBy { it.idProgram }
            )
        }
        .sortedWith(
            compareByDescending<LocalProgramEntity> { it.estStartDate ?: Long.MIN_VALUE }
                .thenBy { textoCicloLista(it.cycle) }
        )
}

private fun normalizarCicloBaseLista(ciclo: String?): String {
    return ciclo
        .orEmpty()
        .trim()
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("_", " ")
        .replace("-", " ")
        .replace(".", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

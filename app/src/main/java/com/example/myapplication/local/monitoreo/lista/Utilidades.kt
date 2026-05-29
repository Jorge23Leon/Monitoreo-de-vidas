package com.example.myapplication.local.monitoreo.lista

import androidx.compose.ui.graphics.Color
import com.example.myapplication.local.entities.LocalPlotEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun obtenerNombreParcelaLista(parcela: LocalPlotEntity): String {
    return parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
}

internal fun textoEstadoLista(status: String): String {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado", "terminado", "cerrado" -> "Completado"
        "cancelled", "cancelado", "canceled" -> "Cancelado"
        else -> status.trim().ifBlank { "Pendiente" }
    }
}

internal fun colorEstadoLista(status: String): Color {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> Color(0xFF9A6A00)
        "in_progress", "en proceso", "vigente" -> Color(0xFF255EA8)
        "completed", "completado", "finalizado", "terminado", "cerrado" -> Color(0xFF1F6D2A)
        "cancelled", "cancelado", "canceled" -> Color(0xFFB3261E)
        else -> Color.Black
    }
}

internal fun colorFondoEstadoLista(status: String): Color {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> Color(0xFFFFF4CC)
        "in_progress", "en proceso", "vigente" -> Color(0xFFE2F0FF)
        "completed", "completado", "finalizado", "terminado", "cerrado" -> Color(0xFFE4F4DF)
        "cancelled", "cancelado", "canceled" -> Color(0xFFFFE1E1)
        else -> Color(0xFFEDEDED)
    }
}

internal fun colorFondoImagenLista(status: String): Color {
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
            val sdf = SimpleDateFormat(patron, Locale.getDefault()).apply { isLenient = false }
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

internal fun cumpleEstadoLista(status: String, estadoFiltro: String): Boolean {
    val estado = status.trim().lowercase(Locale.getDefault())

    return when (estadoFiltro) {
        "Pendiente" -> estado == "pending" || estado == "pendiente"
        "En proceso" -> estado == "in_progress" || estado == "en proceso" || estado == "vigente"
        "Completado" -> estado == "completed" || estado == "completado" ||
                estado == "finalizado" || estado == "terminado" || estado == "cerrado"
        "Cancelado" -> estado == "cancelled" || estado == "cancelado" || estado == "canceled"
        else -> true
    }
}

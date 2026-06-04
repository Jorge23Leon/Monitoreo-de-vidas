package com.example.myapplication.local.monitoreo.filtros

import androidx.compose.ui.graphics.Color
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun resumenFiltrosPrincipales(
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?
): String {
    return "Productor: ${productor?.commercial_name ?: "Todos"}  |  " +
            "Rancho: ${rancho?.name ?: "Todos"}  |  " +
            "Parcela: ${parcela?.let { obtenerNombreParcelaFiltro(it) } ?: "Todas"}"
}

internal fun obtenerNombreParcelaFiltro(parcela: LocalPlotEntity): String {
    return parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
}

internal fun textoEstadoFiltro(status: String): String {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado", "terminado", "cerrado" -> "Completado"
        "cancelled", "cancelado", "canceled" -> "Cancelado"
        else -> status.trim().ifBlank { "Pendiente" }
    }
}

internal fun iconoCultivoFallbackFiltro(nombreCultivo: String?): String {
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

internal fun colorFondoImagen(status: String): Color {
    val normalizado = status.trim().lowercase(Locale.getDefault())

    return when {
        normalizado.contains("complet") ||
                normalizado.contains("final") ||
                normalizado.contains("terminado") ||
                normalizado.contains("cerrado") -> Color(0xFFE5F4DF)

        normalizado.contains("cancel") -> Color(0xFFFFE1E1)

        normalizado.contains("proceso") ||
                normalizado.contains("progress") ||
                normalizado.contains("vigente") -> Color(0xFFE7F0FF)

        else -> Color(0xFFFFF4CC)
    }
}

internal fun formatoFechaCorta(fecha: Long?): String {
    if (fecha == null) return "-"

    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
        sdf.format(Date(fecha)).replace(".", "")
    } catch (e: Exception) {
        "-"
    }
}

internal fun parseFechaInicioFiltro(texto: String): Long? {
    if (texto.isBlank()) return null
    return parseFechaFiltroFlexible(texto)
}

internal fun parseFechaFinFiltro(texto: String): Long? {
    if (texto.isBlank()) return null

    val time = parseFechaFiltroFlexible(texto) ?: return null

    return Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

private fun parseFechaFiltroFlexible(texto: String): Long? {
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

internal fun esEstadoCerradoFiltro(status: String): Boolean {
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

internal fun cumpleEstadoFiltro(status: String, estadoFiltro: String): Boolean {
    val estado = status.trim().lowercase(Locale.getDefault())

    return when (estadoFiltro) {
        "Pendiente" -> estado == "pending" || estado == "pendiente"

        "En proceso" -> estado == "in_progress" ||
                estado == "en proceso" ||
                estado == "vigente"

        "Completado" -> estado == "completed" ||
                estado == "completado" ||
                estado == "finalizado" ||
                estado == "terminado" ||
                estado == "cerrado"

        "Cancelado" -> estado == "cancelled" ||
                estado == "cancelado" ||
                estado == "canceled"

        else -> true
    }
}
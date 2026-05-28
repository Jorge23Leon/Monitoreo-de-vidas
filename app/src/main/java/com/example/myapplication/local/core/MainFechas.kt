package com.example.myapplication.local.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun obtenerEstadosSeleccionadosVm(
    saltarFiltros: Boolean,
    rolUsuarioActual: String,
    vigentesChecked: Boolean,
    finalizadosChecked: Boolean,
    canceladosChecked: Boolean
): List<String> {
    val todosLosEstados = listOf(
        "Pendiente",
        "En proceso",
        "Completado",
        "Cancelado",
        "pending",
        "in_progress",
        "completed",
        "cancelled",
        "pendiente",
        "en proceso",
        "completado",
        "cancelado",
        "vigente",
        "finalizado"
    )

    val rol = normalizarRolVm(rolUsuarioActual)

    if (saltarFiltros || rol == "admin") {
        return todosLosEstados
    }

    val estados = mutableListOf<String>()

    if (vigentesChecked) {
        estados.addAll(
            listOf(
                "Pendiente",
                "En proceso",
                "pending",
                "in_progress",
                "pendiente",
                "en proceso",
                "vigente"
            )
        )
    }

    if (finalizadosChecked) {
        estados.addAll(
            listOf(
                "Completado",
                "completed",
                "completado",
                "finalizado"
            )
        )
    }

    if (canceladosChecked) {
        estados.addAll(
            listOf(
                "Cancelado",
                "cancelled",
                "cancelado"
            )
        )
    }

    return if (estados.isEmpty()) {
        todosLosEstados
    } else {
        estados.distinct()
    }
}

fun parseFechaInicioVm(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null

    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.isLenient = false
        sdf.parse(fechaTexto)?.time
    } catch (e: Exception) {
        null
    }
}

fun parseFechaFinVm(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null

    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.isLenient = false

        val fecha = sdf.parse(fechaTexto) ?: return null

        val calendar = Calendar.getInstance()
        calendar.time = fecha
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)

        calendar.timeInMillis
    } catch (e: Exception) {
        null
    }
}

fun normalizarEstadoMonitoreoVm(status: String): String {
    return status
        .trim()
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("_", " ")
        .replace("-", " ")
        .replace(".", "")
        .replace(Regex("""\s+"""), " ")
}

fun esEstadoCerradoVm(status: String): Boolean {
    return when (normalizarEstadoMonitoreoVm(status)) {
        "completed",
        "completado",
        "finalizado",
        "finalizada",
        "terminado",
        "terminada",
        "cerrado",
        "cerrada",
        "cancelado",
        "cancelada",
        "cancelled",
        "canceled",
        "done",
        "finished" -> true

        else -> false
    }
}

fun esEstadoFinalizadoVm(status: String): Boolean {
    return esEstadoCerradoVm(status)
}

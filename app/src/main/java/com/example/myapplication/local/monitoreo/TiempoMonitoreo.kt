package com.example.myapplication.local.monitoreo

import com.example.myapplication.local.entities.LocalProgramEntity
import java.util.Locale

/**
 * La fecha de cierre es absoluta: corresponde a est_finish_date del Programa.
 * No se calcula una duración desde el inicio del Programa ni se vuelve a sumar
 * esa duración al momento en que el técnico empieza el monitoreo.
 */
internal fun fechaCierreProgramadaMonitoreoMs(
    programa: LocalProgramEntity?
): Long? {
    return programa?.estFinishDate?.takeIf { it > 0L }
}

internal fun tiempoMonitoreoAgotado(
    fechaCierreProgramadaMs: Long?,
    ahoraMs: Long
): Boolean {
    return fechaCierreProgramadaMs != null && ahoraMs >= fechaCierreProgramadaMs
}

internal fun estadoMuestraTiempoMonitoreo(status: String): Boolean {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending",
        "pendiente",
        "in_progress",
        "en proceso",
        "vigente" -> true

        else -> false
    }
}

internal fun textoResumenTiempoMonitoreo(
    fechaCierreProgramadaMs: Long?,
    ahoraMs: Long
): String {
    val cierreAutomaticoMs = fechaCierreProgramadaMs
        ?: return "Tiempo restante: sin fecha de cierre"

    val restanteMs = cierreAutomaticoMs - ahoraMs
    if (restanteMs <= 0L) return "Tiempo restante: agotado"

    return "Tiempo restante: ${formatearDuracionMonitoreo(restanteMs)}"
}

private fun formatearDuracionMonitoreo(duracionMs: Long): String {
    val duracionPositivaMs = duracionMs.coerceAtLeast(0L)
    val totalSegundos = duracionPositivaMs / 1_000L +
            if (duracionPositivaMs % 1_000L == 0L) 0L else 1L
    val dias = totalSegundos / 86_400L
    val horas = (totalSegundos % 86_400L) / 3_600L
    val minutos = (totalSegundos % 3_600L) / 60L
    val segundos = totalSegundos % 60L

    return String.format(
        Locale.getDefault(),
        "%dd %02dh %02dmin %02ds",
        dias,
        horas,
        minutos,
        segundos
    )
}

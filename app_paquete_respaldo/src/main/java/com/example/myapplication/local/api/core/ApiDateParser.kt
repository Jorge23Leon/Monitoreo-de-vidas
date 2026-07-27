package com.example.myapplication.local.api.core

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Convierte fechas de Django/DRF sin perder compatibilidad con Android 7.
 * SimpleDateFormat interpreta seis S como milisegundos; por eso la fracción
 * de microsegundos se normaliza primero a exactamente tres dígitos.
 */
object ApiDateParser {
    private val fraccionLarga = Regex("([.,]\\d{3})\\d+")

    private val formatos = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
        "yyyy-MM-dd'T'HH:mm:ssXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    fun parsearMillis(valor: String?): Long? {
        val limpio = valor
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.replace(fraccionLarga, "\$1")
            ?: return null

        for (patron in formatos) {
            val posicion = ParsePosition(0)
            val fecha = SimpleDateFormat(patron, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(limpio, posicion)

            if (fecha != null && posicion.index == limpio.length) {
                return fecha.time
            }
        }

        return null
    }
}

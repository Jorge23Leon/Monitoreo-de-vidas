package com.example.myapplication.local.api.core

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Convierte fechas de Django/DRF sin perder compatibilidad con Android 7.
 * SimpleDateFormat interpreta seis S como milisegundos; por eso la fracción
 * de microsegundos se normaliza primero a exactamente tres dígitos.
 */
object ApiDateParser {
    private val fraccionLarga = Regex("([.,]\\d{3})\\d+")
    private val fechaSimple = Regex("\\d{4}-\\d{2}-\\d{2}")

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
        "yyyy-MM-dd HH:mm:ss"
    )

    fun parsearMillis(valor: String?): Long? {
        val limpio = valor
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.replace(fraccionLarga, "\$1")
            ?: return null

        /*
         * Un valor sin hora representa un día del calendario, no medianoche UTC.
         * Interpretarlo como UTC desplaza la fecha al día anterior en México y en
         * otras zonas al oeste de Greenwich.
         */
        if (fechaSimple.matches(limpio)) {
            return parsearFechaSimple(limpio, finDeDia = false)
        }

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

    /**
     * Las fechas de cierre que solo incluyen yyyy-MM-dd siguen vigentes hasta
     * terminar ese día en la zona horaria configurada en el dispositivo.
     * Los timestamps que ya traen hora u offset se respetan exactamente.
     */
    fun parsearFinDeDiaMillis(valor: String?): Long? {
        val limpio = valor
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null

        if (!fechaSimple.matches(limpio)) {
            return parsearMillis(limpio)
        }

        return parsearFechaSimple(limpio, finDeDia = true)
    }

    private fun parsearFechaSimple(
        valor: String,
        finDeDia: Boolean
    ): Long? {
        val zonaLocal = TimeZone.getDefault()
        val posicion = ParsePosition(0)
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = zonaLocal
        }.parse(valor, posicion)

        if (fecha == null || posicion.index != valor.length) {
            return null
        }

        if (!finDeDia) return fecha.time

        return Calendar.getInstance(zonaLocal, Locale.US).apply {
            time = fecha
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}
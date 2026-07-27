package com.example.myapplication.local.api.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ApiDateParserTest {
    @Test
    fun aceptaMicrosegundosDeDjango() {
        val millis = ApiDateParser.parsearMillis("2026-07-23T18:00:00.123456Z")
        assertNotNull(millis)

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = millis!!
        }
        assertEquals(123, calendar.get(Calendar.MILLISECOND))
        assertEquals(23, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun respetaOffsetHorario() {
        assertEquals(
            ApiDateParser.parsearMillis("2026-07-23T18:00:00Z"),
            ApiDateParser.parsearMillis("2026-07-23T12:00:00-06:00")
        )
    }

    @Test
    fun rechazaFechaInvalidaCompleta() {
        assertNull(ApiDateParser.parsearMillis("2026-07-23 basura"))
    }
}

package com.example.myapplication.local.monitoreo

import com.example.myapplication.local.entities.LocalProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiempoMonitoreoTest {
    private val cierre = 1_800_000_000_000L

    private val programa = LocalProgramEntity(
        cycle = "Primavera-2026",
        estStartDate = cierre - 3L * 24L * 60L * 60L * 1000L,
        estFinishDate = cierre,
        status = "En proceso",
        idLocalCia = 1,
        idLocalAgroUnit = 1,
        idLocalRanch = 1,
        idCrop = 1,
        idLocalPlot = 1
    )

    @Test
    fun usaFechaFinAbsolutaDelPrograma() {
        assertEquals(cierre, fechaCierreProgramadaMonitoreoMs(programa))
    }

    @Test
    fun detectaVencimientoEnElInstanteCorrecto() {
        assertFalse(tiempoMonitoreoAgotado(cierre, cierre - 1))
        assertTrue(tiempoMonitoreoAgotado(cierre, cierre))
    }

    @Test
    fun resumenIncluyeDiasHorasMinutosYSegundos() {
        assertEquals(
            "Tiempo restante: 1d 01h 01min 01s",
            textoResumenTiempoMonitoreo(
                fechaCierreProgramadaMs = cierre,
                ahoraMs = cierre - 90_061_000L
            )
        )
    }
}

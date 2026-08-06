package com.example.myapplication.local.aspersion.ui

import com.example.myapplication.local.entities.LocalAspersionPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AspersionMapLogicTest {

    @Test
    fun applicationLegendUsesReportThresholds() {
        val points = listOf(
            point("deficiente", applied = 74.99, target = 100.0),
            point("baja", applied = 75.0, target = 100.0),
            point("esperada", applied = 90.0, target = 100.0),
            point("excelente", applied = 100.0, target = 100.0),
            point("sobredosis", applied = 115.01, target = 100.0)
        )

        val legend = buildAspersionLegend(
            points = points,
            layer = AspersionLayer.APPLICATION
        )

        assertEquals(1, legend.first { it.key == "deficiente" }.pointCount)
        assertEquals(1, legend.first { it.key == "baja" }.pointCount)
        assertEquals(1, legend.first { it.key == "esperada" }.pointCount)
        assertEquals(1, legend.first { it.key == "excelente" }.pointCount)
        assertEquals(1, legend.first { it.key == "sobredosis" }.pointCount)
    }

    @Test
    fun quartileLayerKeepsEveryNumericPoint() {
        val points = (1..8).map { value ->
            point(
                id = value.toString(),
                applied = 100.0,
                target = 100.0,
                speed = value.toDouble()
            )
        }

        val legend = buildAspersionLegend(
            points = points,
            layer = AspersionLayer.SPEED
        )

        assertEquals(4, legend.size)
        assertEquals(points.size, legend.sumOf(AspersionLegendItem::pointCount))
    }

    @Test
    fun disabledBucketRemovesOnlyItsPoints() {
        val points = listOf(
            point("low", applied = 70.0, target = 100.0),
            point("good", applied = 105.0, target = 100.0)
        )
        val legend = buildAspersionLegend(points, AspersionLayer.APPLICATION)
        val visibleKeys = legend
            .map(AspersionLegendItem::key)
            .toMutableSet()
            .apply {
                remove("deficiente")
            }

        val visible = visibleAspersionPoints(
            points = points,
            layer = AspersionLayer.APPLICATION,
            legendItems = legend,
            visibleBucketKeys = visibleKeys
        )

        assertEquals(1, visible.size)
        assertTrue(visible.single().pointId == "good")
    }

    @Test
    fun agreedFilterLabelsAreUsed() {
        assertEquals("Proporción de volumen", AspersionLayer.APPLICATION.label)
        assertEquals("Dosis objetivo", AspersionLayer.TARGET_RATE.label)
        assertEquals("Calidad de aplicación", AspersionLayer.RATE_QUALITY.label)
    }

    private fun point(
        id: String,
        applied: Double,
        target: Double,
        speed: Double = 5.0
    ): LocalAspersionPointEntity {
        return LocalAspersionPointEntity(
            pointId = id,
            sessionId = "session",
            longitude = -100.60,
            latitude = 21.10,
            speedKmh = speed,
            boomWidthM = 14.0,
            distanceM = 1.4,
            targetRateL = target,
            appliedRateL = applied,
            areaHa = 0.01
        )
    }
}

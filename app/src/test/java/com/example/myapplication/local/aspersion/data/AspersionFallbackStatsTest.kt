package com.example.myapplication.local.aspersion.data

import com.example.myapplication.local.entities.LocalAspersionPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AspersionFallbackStatsTest {

    @Test
    fun localFallbackKeepsDownloadedPointsWhenServerStatsArePending() {
        val points = listOf(
            point("low", target = 100.0, applied = 70.0, area = 0.2),
            point("ok", target = 100.0, applied = 100.0, area = 0.3),
            point("high", target = 100.0, applied = 120.0, area = 0.4)
        )

        val stats = buildLocalAspersionStats(
            sessionId = "session",
            plotId = "plot",
            points = points,
            cachedAt = 99L
        )

        assertEquals(3, stats.pointsCount)
        assertEquals(0.9, stats.areaTotalHa!!, 0.000001)
        assertEquals(33.333333, stats.pctBelow!!, 0.0001)
        assertEquals(33.333333, stats.pctInRange!!, 0.0001)
        assertEquals(33.333333, stats.pctAbove!!, 0.0001)
    }

    private fun point(
        id: String,
        target: Double,
        applied: Double,
        area: Double
    ) = LocalAspersionPointEntity(
        pointId = id,
        sessionId = "session",
        longitude = -100.60,
        latitude = 21.10,
        targetRateL = target,
        appliedRateL = applied,
        areaHa = area
    )
}

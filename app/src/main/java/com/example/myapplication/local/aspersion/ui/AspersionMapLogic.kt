package com.example.myapplication.local.aspersion.ui

import com.example.myapplication.local.entities.LocalAspersionPointEntity
import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.floor

private data class QuartileCuts(
    val q1: Double,
    val q2: Double,
    val q3: Double
)

private data class BaseLegend(
    val key: String,
    val label: String,
    val range: String? = null,
    val colorHex: String
)

private val applicationLegend = listOf(
    BaseLegend("deficiente", "Deficiente", "< 75%", "#dc2626"),
    BaseLegend("baja", "Baja", "75–90%", "#eab308"),
    BaseLegend("esperada", "Esperada", "90–100%", "#84cc16"),
    BaseLegend("excelente", "Excelente", "100–115%", "#5bb304"),
    BaseLegend("sobredosis", "Sobredosis", "> 115%", "#4052D6")
)

private val rateQualityLegend = listOf(
    BaseLegend("bajo", "Bajo objetivo", colorHex = "#dc2626"),
    BaseLegend("bien", "Bien", colorHex = "#16a34a"),
    BaseLegend("sobre", "Sobre objetivo", colorHex = "#2563eb")
)

private val targetPalette = listOf(
    "#2563eb",
    "#16a34a",
    "#f59e0b",
    "#9333ea",
    "#dc2626",
    "#0891b2",
    "#c026d3",
    "#65a30d",
    "#ea580c",
    "#4f46e5"
)

private fun quartilePalette(layer: AspersionLayer): List<String> = when (layer) {
    AspersionLayer.LIQUID_FLOW -> listOf("#bbf7d0", "#4ade80", "#16a34a", "#14532d")
    AspersionLayer.BOOM_PRESSURE -> listOf("#bfdbfe", "#60a5fa", "#2563eb", "#1e3a8a")
    AspersionLayer.PRODUCTIVITY -> listOf("#e9d5ff", "#c084fc", "#9333ea", "#581c87")
    AspersionLayer.SPEED -> listOf("#fecaca", "#f87171", "#dc2626", "#7f1d1d")
    else -> listOf("#d1d5db", "#9ca3af", "#6b7280", "#374151")
}

internal fun valueForLayer(
    point: LocalAspersionPointEntity,
    layer: AspersionLayer
): Double? = when (layer) {
    AspersionLayer.APPLICATION -> {
        val applied = point.appliedRateL
        val target = point.targetRateL
        if (applied == null || target == null || target == 0.0) {
            null
        } else {
            (applied / target) * 100.0
        }
    }

    AspersionLayer.SPEED -> point.speedKmh
    AspersionLayer.TARGET_RATE -> point.targetRateL
    AspersionLayer.BOOM_PRESSURE -> point.boomPressureBar
    AspersionLayer.LIQUID_FLOW -> point.liquidFlowLs
    AspersionLayer.RATE_QUALITY -> null
    AspersionLayer.PRODUCTIVITY -> point.productionHah
}

internal fun buildAspersionLegend(
    points: List<LocalAspersionPointEntity>,
    layer: AspersionLayer
): List<AspersionLegendItem> {
    val base = when (layer.kind) {
        AspersionLayerKind.APPLICATION -> applicationLegend
        AspersionLayerKind.QUALITY -> rateQualityLegend
        AspersionLayerKind.TARGET -> buildTargetLegend(points)
        AspersionLayerKind.QUARTILE -> buildQuartileLegend(points, layer)
    }

    return base.map { item ->
        val matchingPoints = points.filter { point ->
            bucketForPointBase(point, layer, base) == item.key
        }
        AspersionLegendItem(
            key = item.key,
            label = item.label,
            range = item.range,
            colorHex = item.colorHex,
            pointCount = matchingPoints.size,
            areaHa = matchingPoints.sumOf { point -> point.areaHa ?: 0.0 }
        )
    }
}

internal fun bucketForPoint(
    point: LocalAspersionPointEntity,
    layer: AspersionLayer,
    legendItems: List<AspersionLegendItem>
): String = bucketForPointBase(
    point = point,
    layer = layer,
    baseLegend = legendItems.map { item ->
        BaseLegend(
            key = item.key,
            label = item.label,
            range = item.range,
            colorHex = item.colorHex
        )
    }
)

private fun bucketForPointBase(
    point: LocalAspersionPointEntity,
    layer: AspersionLayer,
    baseLegend: List<BaseLegend>
): String {
    return when (layer.kind) {
        AspersionLayerKind.APPLICATION -> {
            val percent = valueForLayer(point, layer)
            when {
                percent == null -> "sin_meta"
                percent < 75.0 -> "deficiente"
                percent < 90.0 -> "baja"
                percent < 100.0 -> "esperada"
                percent <= 115.0 -> "excelente"
                else -> "sobredosis"
            }
        }

        AspersionLayerKind.QUALITY -> classifyRateQuality(point.rateQuality)

        AspersionLayerKind.TARGET -> {
            val value = point.targetRateL ?: return "sin_meta"
            targetKey(value)
        }

        AspersionLayerKind.QUARTILE -> {
            val value = valueForLayer(point, layer) ?: return "sin_dato"
            val keyRanges = baseLegend.mapNotNull { item ->
                val parts = item.range
                    ?.substringBefore(" ")
                    ?.split(":")
                    ?: return@mapNotNull null
                if (parts.size != 2) return@mapNotNull null
                item.key to parts[1].toDoubleOrNull()
            }.toMap()

            val q1 = keyRanges["q1"] ?: return "sin_dato"
            val q2 = keyRanges["q2"] ?: return "sin_dato"
            val q3 = keyRanges["q3"] ?: return "sin_dato"

            when {
                value < q1 -> "q1"
                value < q2 -> "q2"
                value < q3 -> "q3"
                else -> "q4"
            }
        }
    }
}

internal fun visibleAspersionPoints(
    points: List<LocalAspersionPointEntity>,
    layer: AspersionLayer,
    legendItems: List<AspersionLegendItem>,
    visibleBucketKeys: Set<String>
): List<LocalAspersionPointEntity> {
    if (legendItems.isEmpty()) return points

    val allKeys = legendItems.map(AspersionLegendItem::key).toSet()
    if (visibleBucketKeys == allKeys) {
        // Coincide con el visor web: al estar todo activo también se ven los
        // puntos sin valor, usando el color gris de respaldo.
        return points
    }

    return points.filter { point ->
        bucketForPoint(point, layer, legendItems) in visibleBucketKeys
    }
}

internal fun colorForAspersionPoint(
    point: LocalAspersionPointEntity,
    layer: AspersionLayer,
    legendItems: List<AspersionLegendItem>
): String {
    val bucket = bucketForPoint(point, layer, legendItems)
    return legendItems
        .firstOrNull { item -> item.key == bucket }
        ?.colorHex
        ?: "#94a3b8"
}

private fun buildTargetLegend(
    points: List<LocalAspersionPointEntity>
): List<BaseLegend> {
    return points
        .mapNotNull(LocalAspersionPointEntity::targetRateL)
        .filter(Double::isFinite)
        .distinct()
        .sorted()
        .mapIndexed { index, value ->
            BaseLegend(
                key = targetKey(value),
                label = "${formatNumber(value, 2)} L/ha",
                colorHex = targetPalette[index % targetPalette.size]
            )
        }
}

private fun buildQuartileLegend(
    points: List<LocalAspersionPointEntity>,
    layer: AspersionLayer
): List<BaseLegend> {
    val values = points
        .mapNotNull { point -> valueForLayer(point, layer) }
        .filter(Double::isFinite)
        .sorted()

    val cuts = computeQuartiles(values) ?: return emptyList()
    val palette = quartilePalette(layer)
    val q1 = formatNumber(cuts.q1, 2)
    val q2 = formatNumber(cuts.q2, 2)
    val q3 = formatNumber(cuts.q3, 2)

    /*
     * El prefijo técnico qN:<corte> permite clasificar sin guardar otra
     * estructura y no se muestra en pantalla.
     */
    return listOf(
        BaseLegend(
            key = "q1",
            label = "Q1",
            range = "q1:${cuts.q1} · < $q1 ${layer.unit}",
            colorHex = palette[0]
        ),
        BaseLegend(
            key = "q2",
            label = "Q2",
            range = "q2:${cuts.q2} · $q1–$q2 ${layer.unit}",
            colorHex = palette[1]
        ),
        BaseLegend(
            key = "q3",
            label = "Q3",
            range = "q3:${cuts.q3} · $q2–$q3 ${layer.unit}",
            colorHex = palette[2]
        ),
        BaseLegend(
            key = "q4",
            label = "Q4",
            range = "q4:${cuts.q3} · ≥ $q3 ${layer.unit}",
            colorHex = palette[3]
        )
    )
}

internal fun displayLegendRange(item: AspersionLegendItem): String? {
    val range = item.range ?: return null
    return if (range.startsWith("q") && " · " in range) {
        range.substringAfter(" · ")
    } else {
        range
    }
}

private fun computeQuartiles(values: List<Double>): QuartileCuts? {
    if (values.isEmpty()) return null

    fun quantile(percent: Double): Double {
        val index = (values.size - 1) * percent
        val low = floor(index).toInt()
        val high = ceil(index).toInt()
        if (low == high) return values[low]

        return values[low] + (values[high] - values[low]) * (index - low)
    }

    return QuartileCuts(
        q1 = quantile(0.25),
        q2 = quantile(0.50),
        q3 = quantile(0.75)
    )
}

private fun classifyRateQuality(value: String?): String {
    val normalized = Normalizer.normalize(
        value.orEmpty().trim().lowercase(),
        Normalizer.Form.NFD
    ).replace("\\p{Mn}+".toRegex(), "")

    return when {
        normalized.startsWith("bajo") -> "bajo"
        normalized.startsWith("bien") -> "bien"
        normalized.startsWith("sobre") -> "sobre"
        else -> "sin_dato"
    }
}

private fun targetKey(value: Double): String = "target:${java.lang.Double.toString(value)}"

internal fun formatNumber(value: Double?, decimals: Int = 2): String {
    if (value == null || !value.isFinite()) return "—"
    return "%.${decimals}f".format(java.util.Locale.US, value)
}

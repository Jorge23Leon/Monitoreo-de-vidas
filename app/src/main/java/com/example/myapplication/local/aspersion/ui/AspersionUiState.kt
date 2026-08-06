package com.example.myapplication.local.aspersion.ui

import com.example.myapplication.local.entities.LocalAspersionPointEntity
import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import com.example.myapplication.local.entities.LocalAspersionStatsEntity
import com.example.myapplication.local.entities.LocalAspersionVariableStatEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity

enum class AspersionLayerKind {
    APPLICATION,
    QUARTILE,
    TARGET,
    QUALITY
}

enum class AspersionLayer(
    val key: String,
    val label: String,
    val unit: String,
    val kind: AspersionLayerKind
) {
    APPLICATION(
        key = "application",
        label = "Proporción de volumen",
        unit = "%",
        kind = AspersionLayerKind.APPLICATION
    ),
    SPEED(
        key = "speed",
        label = "Velocidad",
        unit = "km/h",
        kind = AspersionLayerKind.QUARTILE
    ),
    TARGET_RATE(
        key = "target_rate",
        label = "Dosis objetivo",
        unit = "L/ha",
        kind = AspersionLayerKind.TARGET
    ),
    BOOM_PRESSURE(
        key = "boom_pressure",
        label = "Presión",
        unit = "bar",
        kind = AspersionLayerKind.QUARTILE
    ),
    LIQUID_FLOW(
        key = "liquid_flow",
        label = "Caudal",
        unit = "L/s",
        kind = AspersionLayerKind.QUARTILE
    ),
    RATE_QUALITY(
        key = "rate_quality",
        label = "Calidad de aplicación",
        unit = "",
        kind = AspersionLayerKind.QUALITY
    ),
    PRODUCTIVITY(
        key = "production",
        label = "Productividad",
        unit = "ha/h",
        kind = AspersionLayerKind.QUARTILE
    )
}

data class AspersionLegendItem(
    val key: String,
    val label: String,
    val range: String? = null,
    val colorHex: String,
    val pointCount: Int,
    val areaHa: Double
)

/** Una sesión de aspersión tal como viene relacionada dentro del subprograma. */
data class AspersionProgramSessionItem(
    val sessionId: String,
    val aspersionDate: String? = null,
    val importStatus: String? = null,
    val cachedSession: LocalAspersionSessionEntity? = null
)

/**
 * Fila principal del listado de aspersión.
 *
 * La unidad visible es el subprograma, no el encabezado de sesión. De esta
 * forma la app conserva la misma estructura de producción y también enseña
 * los subprogramas que aún no tienen una sesión de aspersión.
 */
data class AspersionProgramItem(
    val programId: String,
    val programName: String,
    val masterProgramId: String? = null,
    val masterProgramName: String? = null,
    val plotId: String? = null,
    val plotName: String? = null,
    val estStartDate: String? = null,
    val estFinishDate: String? = null,
    val status: String? = null,
    val context: AspersionSessionContext,
    val sessions: List<AspersionProgramSessionItem> = emptyList()
)

data class AspersionUiState(
    val programs: List<AspersionProgramItem> = emptyList(),
    val totalProgramsBeforeFilters: Int = 0,
    val sessions: List<LocalAspersionSessionEntity> = emptyList(),
    val totalSessionsBeforeFilters: Int = 0,
    val unresolvedSessionsCount: Int = 0,
    val sessionContextsById: Map<String, AspersionSessionContext> = emptyMap(),
    val accessMode: AspersionAccessMode = AspersionAccessMode.MANAGER,
    val filters: AspersionFilters = AspersionFilters(),
    val filterOptions: AspersionFilterOptions = AspersionFilterOptions(),
    val sessionFiltersExpanded: Boolean = false,
    val plotNamesById: Map<String, String> = emptyMap(),
    val programNamesById: Map<String, String> = emptyMap(),

    val selectedSessionId: String? = null,
    val selectedSession: LocalAspersionSessionEntity? = null,
    val selectedPlotName: String? = null,
    val selectedProgramName: String? = null,
    val plotVertices: List<LocalPlotVertexEntity> = emptyList(),

    val points: List<LocalAspersionPointEntity> = emptyList(),
    val stats: LocalAspersionStatsEntity? = null,
    val variableStats: List<LocalAspersionVariableStatEntity> = emptyList(),

    val selectedLayer: AspersionLayer = AspersionLayer.APPLICATION,
    val legendItems: List<AspersionLegendItem> = emptyList(),
    val visibleBucketKeys: Set<String> = emptySet(),
    val filtersExpanded: Boolean = true,

    val syncingSessions: Boolean = false,
    val syncingSelectedSession: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

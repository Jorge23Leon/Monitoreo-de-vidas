package com.example.myapplication.local.ndvi.ui

import com.example.myapplication.local.entities.LocalNdviPointEntity
import com.example.myapplication.local.entities.LocalNdviSessionEntity
import com.example.myapplication.local.ndvi.model.NdviIndex


data class NdviUiState(

    /** Sesiones visibles después de aplicar filtros. */
    val sessions: List<LocalNdviSessionEntity> =
        emptyList(),

    val totalSessionsBeforeFilters: Int =
        0,

    val sessionContextsById: Map<String, NdviSessionContext> =
        emptyMap(),

    val filters: NdviFilters =
        NdviFilters(),

    val filterOptions: NdviFilterOptions =
        NdviFilterOptions(),

    /** Igual que Aspersión: los filtros inician contraídos. */
    val sessionFiltersExpanded: Boolean =
        false,

    val selectedSessionId: String? =
        null,

    val selectedSession: LocalNdviSessionEntity? =
        null,

    val points: List<LocalNdviPointEntity> =
        emptyList(),

    val selectedIndex: NdviIndex =
        NdviIndex.NDVI,

    val availableIndices: List<NdviIndex> =
        NdviIndex.entries,


    // =====================================================
    // CONFIGURACIÓN VISUAL DE LA CIA
    // =====================================================

    val variableConfigJson: String? =
        null,

    val loadingVariableConfig: Boolean =
        false,

    val variableConfigError: String? =
        null,


    // =====================================================
    // SINCRONIZACIÓN
    // =====================================================

    val syncingSessions: Boolean =
        false,

    val syncingSelectedSession: Boolean =
        false,


    // =====================================================
    // MENSAJES
    // =====================================================

    val message: String? =
        null,

    val error: String? =
        null
)

package com.example.myapplication.local.ndvi.ui

import com.example.myapplication.local.entities.LocalNdviSessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


data class NdviSessionContext(
    val sessionId: String,
    val ciaId: String? = null,
    val producerId: String? = null,
    val producerName: String? = null,
    val ranchId: String? = null,
    val ranchName: String? = null,
    val plotId: String? = null,
    val plotName: String? = null,
    val programId: String? = null,
    val programName: String? = null
)


data class NdviFilterOption(
    val id: String,
    val label: String
)


data class NdviFilters(
    val producerId: String? = null,
    val ranchId: String? = null,
    val plotId: String? = null,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null
) {

    val isActive: Boolean
        get() =
            producerId != null ||
                    ranchId != null ||
                    plotId != null ||
                    startDateMillis != null ||
                    endDateMillis != null
}


data class NdviFilterOptions(
    val producers: List<NdviFilterOption> =
        emptyList(),

    val ranches: List<NdviFilterOption> =
        emptyList(),

    val plots: List<NdviFilterOption> =
        emptyList()
)


internal data class NdviFilterPresentation(
    val filters: NdviFilters,
    val options: NdviFilterOptions,
    val sessions: List<LocalNdviSessionEntity>
)


internal fun buildNdviFilterPresentation(
    sessions: List<LocalNdviSessionEntity>,
    contexts: Map<String, NdviSessionContext>,
    requestedFilters: NdviFilters
): NdviFilterPresentation {


    var filters =
        requestedFilters


    // =========================================================
    // CONTEXTOS DISPONIBLES
    // =========================================================

    val availableContexts =
        sessions.mapNotNull { session ->

            contexts[
                session.sessionId
            ]
        }


    // =========================================================
    // PRODUCTORES
    // =========================================================

    val producerOptions =
        availableContexts
            .toNdviOptions(
                id =
                NdviSessionContext::producerId,
                label =
                NdviSessionContext::producerName
            )


    /**
     * Si el productor seleccionado ya no existe
     * dentro de las sesiones disponibles,
     * limpiamos productor, rancho y parcela.
     */
    if (
        filters.producerId !in
        producerOptions.ndviIds()
    ) {

        filters =
            filters.copy(
                producerId = null,
                ranchId = null,
                plotId = null
            )
    }


    // =========================================================
    // RANCHOS
    // =========================================================

    val ranchContexts =
        filters.producerId
            ?.let { producerId ->

                availableContexts.filter {
                        context ->

                    context.producerId ==
                            producerId
                }
            }
            .orEmpty()


    val ranchOptions =
        ranchContexts
            .toNdviOptions(
                id =
                NdviSessionContext::ranchId,
                label =
                NdviSessionContext::ranchName
            )


    /**
     * Si cambia productor y el rancho anterior
     * ya no pertenece al productor,
     * limpiamos rancho y parcela.
     */
    if (
        filters.ranchId !in
        ranchOptions.ndviIds()
    ) {

        filters =
            filters.copy(
                ranchId = null,
                plotId = null
            )
    }


    // =========================================================
    // PARCELAS
    // =========================================================

    val plotContexts =
        filters.ranchId
            ?.let { ranchId ->

                ranchContexts.filter {
                        context ->

                    context.ranchId ==
                            ranchId
                }
            }
            .orEmpty()


    val plotOptions =
        plotContexts
            .toNdviOptions(
                id =
                NdviSessionContext::plotId,
                label =
                NdviSessionContext::plotName
            )


    /**
     * Si cambia rancho y la parcela anterior
     * ya no pertenece al rancho,
     * limpiamos únicamente parcela.
     */
    if (
        filters.plotId !in
        plotOptions.ndviIds()
    ) {

        filters =
            filters.copy(
                plotId = null
            )
    }


    // =========================================================
    // NORMALIZAR FECHAS
    // =========================================================

    val normalizedStart =
        filters.startDateMillis
            ?.ndviStartOfDay()


    val normalizedEnd =
        filters.endDateMillis
            ?.ndviEndOfDay()


    /**
     * Si el usuario seleccionó primero una fecha mayor
     * y después una menor, las intercambiamos.
     */
    filters =
        if (
            normalizedStart != null &&
            normalizedEnd != null &&
            normalizedStart > normalizedEnd
        ) {

            filters.copy(
                startDateMillis =
                normalizedEnd.ndviStartOfDay(),

                endDateMillis =
                normalizedStart.ndviEndOfDay()
            )

        } else {

            filters.copy(
                startDateMillis =
                normalizedStart,

                endDateMillis =
                normalizedEnd
            )
        }


    // =========================================================
    // COPIAS INMUTABLES DE FILTROS
    // =========================================================

    /**
     * Estas variables son importantes.
     *
     * filters es un var, por eso Kotlin no permite
     * hacer smart cast seguro directamente sobre:
     *
     * filters.startDateMillis
     * filters.endDateMillis
     *
     * Copiándolas a val resolvemos el error:
     *
     * Smart cast to Long is impossible.
     */
    val activeProducerId =
        filters.producerId


    val activeRanchId =
        filters.ranchId


    val activePlotId =
        filters.plotId


    val activeStartDateMillis =
        filters.startDateMillis


    val activeEndDateMillis =
        filters.endDateMillis


    // =========================================================
    // FILTRAR SESIONES
    // =========================================================

    val filteredSessions =
        sessions.filter {
                session ->


            val context =
                contexts[
                    session.sessionId
                ]


            // -------------------------------------------------
            // PRODUCTOR
            // -------------------------------------------------

            if (
                activeProducerId != null &&
                context?.producerId !=
                activeProducerId
            ) {

                return@filter false
            }


            // -------------------------------------------------
            // RANCHO
            // -------------------------------------------------

            if (
                activeRanchId != null &&
                context?.ranchId !=
                activeRanchId
            ) {

                return@filter false
            }


            // -------------------------------------------------
            // PARCELA
            // -------------------------------------------------

            if (
                activePlotId != null &&
                context?.plotId !=
                activePlotId
            ) {

                return@filter false
            }


            // -------------------------------------------------
            // FECHA DE LA SESIÓN
            // -------------------------------------------------

            val dateMillis =
                parseNdviSessionDateMillis(
                    session.sessionDate
                        ?: session.estStartDate
                )


            // -------------------------------------------------
            // FECHA INICIO
            // -------------------------------------------------

            if (
                activeStartDateMillis != null &&
                (
                        dateMillis == null ||
                                dateMillis <
                                activeStartDateMillis
                        )
            ) {

                return@filter false
            }


            // -------------------------------------------------
            // FECHA FIN
            // -------------------------------------------------

            if (
                activeEndDateMillis != null &&
                (
                        dateMillis == null ||
                                dateMillis >
                                activeEndDateMillis
                        )
            ) {

                return@filter false
            }


            true
        }


    // =========================================================
    // RESULTADO
    // =========================================================

    return NdviFilterPresentation(

        filters =
        filters,

        options =
        NdviFilterOptions(

            producers =
            producerOptions,

            ranches =
            ranchOptions,

            plots =
            plotOptions
        ),

        sessions =
        filteredSessions
    )
}


// =============================================================
// CONVERTIR CONTEXTOS EN OPCIONES
// =============================================================

private fun Collection<NdviSessionContext>
        .toNdviOptions(

    id:
        (NdviSessionContext) -> String?,

    label:
        (NdviSessionContext) -> String?

): List<NdviFilterOption> {


    return mapNotNull {
            context ->


        val cleanId =
            id(context)
                ?.trim()
                ?.takeIf(
                    String::isNotEmpty
                )
                ?: return@mapNotNull null


        val cleanLabel =
            label(context)
                ?.trim()
                ?.takeIf(
                    String::isNotEmpty
                )
                ?: cleanId.take(
                    12
                )


        NdviFilterOption(
            id =
            cleanId,

            label =
            cleanLabel
        )
    }
        .distinctBy(
            NdviFilterOption::id
        )
        .sortedBy {
                option ->

            option.label
                .lowercase(
                    Locale.getDefault()
                )
        }
}


// =============================================================
// IDS DISPONIBLES
// =============================================================

private fun List<NdviFilterOption>
        .ndviIds(): Set<String?> {


    return mapTo(
        mutableSetOf<String?>(
            null
        ),
        NdviFilterOption::id
    )
}


// =============================================================
// PARSEAR FECHA NDVI
// =============================================================

internal fun parseNdviSessionDateMillis(
    value: String?
): Long? {


    val clean =
        value
            ?.trim()
            ?.takeIf(
                String::isNotEmpty
            )
            ?: return null


    /**
     * Soporta valores como:
     *
     * 2026-04-03
     *
     * o:
     *
     * 2026-04-03T10:20:30Z
     */
    val datePart =
        clean.take(
            10
        )


    val format =
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).apply {

            isLenient =
                false
        }


    return runCatching {

        format
            .parse(
                datePart
            )
            ?.time
    }
        .getOrNull()
        ?.ndviStartOfDay()
}


// =============================================================
// INICIO DEL DÍA
// =============================================================

private fun Long.ndviStartOfDay(): Long {


    return Calendar
        .getInstance()
        .apply {

            timeInMillis =
                this@ndviStartOfDay


            set(
                Calendar.HOUR_OF_DAY,
                0
            )


            set(
                Calendar.MINUTE,
                0
            )


            set(
                Calendar.SECOND,
                0
            )


            set(
                Calendar.MILLISECOND,
                0
            )
        }
        .timeInMillis
}


// =============================================================
// FIN DEL DÍA
// =============================================================

private fun Long.ndviEndOfDay(): Long {


    return Calendar
        .getInstance()
        .apply {

            timeInMillis =
                this@ndviEndOfDay


            set(
                Calendar.HOUR_OF_DAY,
                23
            )


            set(
                Calendar.MINUTE,
                59
            )


            set(
                Calendar.SECOND,
                59
            )


            set(
                Calendar.MILLISECOND,
                999
            )
        }
        .timeInMillis
}
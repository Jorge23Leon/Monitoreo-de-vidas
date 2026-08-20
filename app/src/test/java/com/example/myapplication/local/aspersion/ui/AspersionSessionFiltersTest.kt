package com.example.myapplication.local.aspersion.ui

import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AspersionSessionFiltersTest {

    @Test
    fun accessModesMatchMonitoringPermissions() {
        assertTrue(AspersionAccessMode.ADMIN.scopeBySelectedCia)
        assertTrue(AspersionAccessMode.MANAGER.scopeBySelectedCia)
        assertTrue(AspersionAccessMode.ADMIN.canFilterByPlot)
        assertTrue(AspersionAccessMode.MANAGER.canFilterByPlot)
    }

    @Test
    fun selectedCiaIsAnInternalScopeAndNotAVisibleActiveFilter() {
        assertFalse(
            AspersionFilters(
                ciaId = "cia-a"
            ).isActive
        )

        assertTrue(
            AspersionFilters(
                ciaId = "cia-a",
                producerId = "producer-a"
            ).isActive
        )
    }

    @Test
    fun fullHierarchyFiltersAdminSessionsProgressively() {
        val first = session(
            id = "s1",
            status = "pending",
            date = "2026-07-10"
        )

        val second = session(
            id = "s2",
            status = "completed",
            date = "2026-07-20"
        )

        val contexts = mapOf(
            "s1" to context(
                sessionId = "s1",
                cia = "cia-a",
                producer = "producer-a",
                ranch = "ranch-a",
                plot = "plot-a",
                program = "program-a"
            ),
            "s2" to context(
                sessionId = "s2",
                cia = "cia-b",
                producer = "producer-b",
                ranch = "ranch-b",
                plot = "plot-b",
                program = "program-b"
            )
        )

        val presentation = buildAspersionFilterPresentation(
            sessions = listOf(first, second),
            contexts = contexts,
            requestedFilters = AspersionFilters(
                ciaId = "cia-a",
                producerId = "producer-a",
                ranchId = "ranch-a",
                plotId = "plot-a",
                programId = "program-a"
            ),
            canFilterByPlot = true
        )

        assertEquals(
            listOf("s1"),
            presentation.sessions.map { it.sessionId }
        )

        assertEquals(
            listOf("cia-a", "cia-b"),
            presentation.options.cias.map { it.id }
        )

        assertEquals(
            listOf("ranch-a"),
            presentation.options.ranches.map { it.id }
        )

        assertEquals(
            listOf("plot-a"),
            presentation.options.plots.map { it.id }
        )

        assertEquals(
            listOf("program-a"),
            presentation.options.programs.map { it.id }
        )
    }

    @Test
    fun localCatalogKeepsHierarchyVisibleWithoutSessions() {
        val catalog = listOf(
            context(
                sessionId = "catalog",
                cia = "cia-a",
                producer = "producer-a",
                ranch = "ranch-a",
                plot = "plot-a",
                program = "program-a"
            )
        )

        val presentation = buildAspersionFilterPresentation(
            sessions = emptyList(),
            contexts = emptyMap(),
            requestedFilters = AspersionFilters(
                ciaId = "cia-a",
                producerId = "producer-a",
                ranchId = "ranch-a"
            ),
            canFilterByPlot = true,
            availableContexts = catalog
        )

        assertEquals(
            listOf("cia-a"),
            presentation.options.cias.map { it.id }
        )

        assertEquals(
            listOf("producer-a"),
            presentation.options.producers.map { it.id }
        )

        assertEquals(
            listOf("ranch-a"),
            presentation.options.ranches.map { it.id }
        )

        assertEquals(
            listOf("plot-a"),
            presentation.options.plots.map { it.id }
        )

        assertTrue(presentation.sessions.isEmpty())
    }

    @Test
    fun invalidPlotSelectionIsCleared() {
        val item = session("s1")

        val contexts = mapOf(
            "s1" to context(
                sessionId = "s1",
                cia = "cia-a",
                producer = "producer-a",
                ranch = "ranch-a",
                plot = "plot-a",
                program = "program-a"
            )
        )

        val presentation = buildAspersionFilterPresentation(
            sessions = listOf(item),
            contexts = contexts,
            requestedFilters = AspersionFilters(
                ciaId = "cia-a",
                producerId = "producer-a",
                ranchId = "ranch-a",
                plotId = "plot-other",
                programId = "program-a"
            ),
            canFilterByPlot = true
        )

        assertNull(presentation.filters.plotId)
        assertNull(presentation.filters.programId)
        assertEquals(1, presentation.sessions.size)
    }

    @Test
    fun dateStatusAndSearchWorkTogether() {
        val first = session(
            id = "s1",
            status = "pending",
            date = "2026-07-10"
        )

        val second = session(
            id = "s2",
            status = "completed",
            date = "2026-07-20"
        )

        val contexts = mapOf(
            "s1" to context(
                sessionId = "s1",
                cia = "cia-a",
                plotName = "Lote Norte"
            ),
            "s2" to context(
                sessionId = "s2",
                cia = "cia-a",
                plotName = "Lote Sur"
            )
        )

        val presentation = buildAspersionFilterPresentation(
            sessions = listOf(first, second),
            contexts = contexts,
            requestedFilters = AspersionFilters(
                ciaId = "cia-a",
                query = "sur",
                startDateMillis = parseAspersionSessionDateMillis(
                    "2026-07-15"
                ),
                status = "completed"
            ),
            canFilterByPlot = true
        )

        assertEquals(
            listOf("s2"),
            presentation.sessions.map { it.sessionId }
        )
    }

    @Test
    fun unresolvedRemoteSessionIsNotHiddenWhenProducerAndPlotAreAllowed() {
        val item = session(
            id = "s-new",
            program = "program-new",
            plot = "plot-new"
        )

        val context = AspersionSessionContext(
            sessionId = item.sessionId,
            producerId = "producer-a",
            plotId = "plot-new",
            programId = "program-new"
        )

        assertTrue(
            isAspersionSessionInCiaScope(
                session = item,
                context = context,
                ciaId = "cia-a",
                allowedProducerIds = setOf("producer-a"),
                allowedProgramIds = emptySet(),
                allowedPlotIds = setOf("plot-new")
            )
        )
    }

    @Test
    fun knownSessionFromAnotherCiaIsExcluded() {
        val item = session(
            id = "s-other",
            program = "program-other",
            plot = "plot-other"
        )

        val context = AspersionSessionContext(
            sessionId = item.sessionId,
            dataCentralIds = setOf("cia-b"),
            producerId = "producer-b",
            programId = "program-other",
            plotId = "plot-other"
        )

        assertFalse(
            isAspersionSessionInCiaScope(
                session = item,
                context = context,
                ciaId = "cia-a",
                allowedProducerIds = setOf("producer-a"),
                allowedProgramIds = setOf("program-a"),
                allowedPlotIds = setOf("plot-a")
            )
        )
    }

    @Test
    fun serverDataCentralContextHasPriorityOverLocalCatalog() {
        val item = session(
            id = "s1",
            program = "program-other",
            plot = "plot-other"
        )

        val context = AspersionSessionContext(
            sessionId = item.sessionId,
            dataCentralIds = setOf("cia-a"),
            producerId = "producer-a",
            programId = "program-other",
            plotId = "plot-other"
        )

        assertTrue(
            isAspersionSessionInCiaScope(
                session = item,
                context = context,
                ciaId = "cia-a",
                allowedProducerIds = setOf("producer-a"),
                allowedProgramIds = emptySet(),
                allowedPlotIds = emptySet()
            )
        )
    }

    private fun session(
        id: String,
        status: String = "pending",
        date: String = "2026-07-10",
        program: String? = null,
        plot: String? = null
    ) = LocalAspersionSessionEntity(
        sessionId = id,
        programId = program,
        plotId = plot,
        aspersionDate = date,
        status = status
    )

    private fun context(
        sessionId: String,
        cia: String? = null,
        producer: String? = null,
        ranch: String? = null,
        plot: String? = null,
        program: String? = null,
        plotName: String? = null
    ) = AspersionSessionContext(
        sessionId = sessionId,
        dataCentralIds = setOfNotNull(cia),
        dataCentralNames = cia
            ?.let {
                mapOf(
                    it to it.replace('-', ' ')
                )
            }
            .orEmpty(),

        producerId = producer,
        producerName = producer?.replace('-', ' '),

        ranchId = ranch,
        ranchName = ranch?.replace('-', ' '),

        plotId = plot,
        plotName = plotName ?: plot?.replace('-', ' '),

        programId = program,
        programName = program?.replace('-', ' ')
    )
}
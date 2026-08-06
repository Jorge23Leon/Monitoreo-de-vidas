package com.example.myapplication.local.aspersion.ui

import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class AspersionAccessMode {
    ADMIN,
    MANAGER;

    /** Aspersión siempre respeta la CIA elegida antes de abrir el módulo. */
    val scopeBySelectedCia: Boolean
        get() = true

    val canFilterByPlot: Boolean
        get() = true
}

data class AspersionSessionContext(
    val sessionId: String,
    val dataCentralIds: Set<String> = emptySet(),
    val dataCentralNames: Map<String, String> = emptyMap(),
    val producerId: String? = null,
    val producerName: String? = null,
    val ranchId: String? = null,
    val ranchName: String? = null,
    val plotId: String? = null,
    val plotName: String? = null,
    val programId: String? = null,
    val programName: String? = null
)

data class AspersionFilterOption(
    val id: String,
    val label: String
)

data class AspersionFilters(
    val query: String = "",
    val ciaId: String? = null,
    val producerId: String? = null,
    val ranchId: String? = null,
    val plotId: String? = null,
    val programId: String? = null,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val status: String? = null
) {
    val isActive: Boolean
        get() = producerId != null ||
                ranchId != null ||
                plotId != null ||
                startDateMillis != null ||
                endDateMillis != null
}

data class AspersionFilterOptions(
    val cias: List<AspersionFilterOption> = emptyList(),
    val producers: List<AspersionFilterOption> = emptyList(),
    val ranches: List<AspersionFilterOption> = emptyList(),
    val plots: List<AspersionFilterOption> = emptyList(),
    val programs: List<AspersionFilterOption> = emptyList(),
    val statuses: List<AspersionFilterOption> = emptyList()
)

internal data class AspersionFilterPresentation(
    val filters: AspersionFilters,
    val options: AspersionFilterOptions,
    val sessions: List<LocalAspersionSessionEntity>
)

internal data class AspersionProgramFilterPresentation(
    val filters: AspersionFilters,
    val options: AspersionFilterOptions,
    val programs: List<AspersionProgramItem>
)

/**
 * Aplica los filtros visibles sobre la estructura programa maestro ->
 * subprograma -> sesiones. La CIA sigue siendo un alcance obligatorio e
 * invisible; productor, rancho y parcela se obtienen de los subprogramas
 * disponibles en esa CIA.
 */
internal fun buildAspersionProgramFilterPresentation(
    programs: List<AspersionProgramItem>,
    requestedFilters: AspersionFilters,
    canFilterByPlot: Boolean
): AspersionProgramFilterPresentation {
    var filters = requestedFilters.copy(
        query = "",
        programId = null,
        status = null
    )
    val contexts = programs.map(AspersionProgramItem::context)

    val ciaOptions = contexts.toCiaOptions()
    val selectedCiaId = filters.ciaId
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    /*
     * La CIA es un alcance obligatorio.
     * No convertir una CIA desconocida en null porque null significaba
     * mostrar todos los programas.
     */
    if (selectedCiaId == null) {
        return AspersionProgramFilterPresentation(
            filters = filters.copy(
                ciaId = null,
                producerId = null,
                ranchId = null,
                plotId = null
            ),
            options = AspersionFilterOptions(),
            programs = emptyList()
        )
    }

    val ciaContexts = contexts.filter { context ->
        selectedCiaId in context.dataCentralIds
    }

    if (ciaContexts.isEmpty()) {
        return AspersionProgramFilterPresentation(
            filters = filters.copy(
                ciaId = selectedCiaId,
                producerId = null,
                ranchId = null,
                plotId = null
            ),
            options = AspersionFilterOptions(
                cias = ciaOptions
            ),
            programs = emptyList()
        )
    }

    filters = filters.copy(ciaId = selectedCiaId)
    val producerOptions = ciaContexts.toOptions(
        id = AspersionSessionContext::producerId,
        label = AspersionSessionContext::producerName
    )
    if (filters.producerId !in producerOptions.ids()) {
        filters = filters.copy(
            producerId = null,
            ranchId = null,
            plotId = null
        )
    }

    val ranchContexts = filters.producerId?.let { producerId ->
        ciaContexts.filter { context -> context.producerId == producerId }
    }.orEmpty()
    val ranchOptions = ranchContexts.toOptions(
        id = AspersionSessionContext::ranchId,
        label = AspersionSessionContext::ranchName
    )
    if (filters.ranchId !in ranchOptions.ids()) {
        filters = filters.copy(ranchId = null, plotId = null)
    }

    val plotContexts = if (canFilterByPlot) {
        filters.ranchId?.let { ranchId ->
            ranchContexts.filter { context -> context.ranchId == ranchId }
        }.orEmpty()
    } else {
        emptyList()
    }
    val plotOptions = plotContexts.toOptions(
        id = AspersionSessionContext::plotId,
        label = AspersionSessionContext::plotName
    )
    filters = when {
        !canFilterByPlot -> filters.copy(plotId = null)
        filters.plotId !in plotOptions.ids() -> filters.copy(plotId = null)
        else -> filters
    }

    val normalizedStart = filters.startDateMillis?.toStartOfDay()
    val normalizedEnd = filters.endDateMillis?.toEndOfDay()
    filters = if (
        normalizedStart != null &&
        normalizedEnd != null &&
        normalizedStart > normalizedEnd
    ) {
        filters.copy(
            startDateMillis = normalizedEnd.toStartOfDay(),
            endDateMillis = normalizedStart.toEndOfDay()
        )
    } else {
        filters.copy(
            startDateMillis = normalizedStart,
            endDateMillis = normalizedEnd
        )
    }

    val filteredPrograms = programs.filter { program ->
        matchesAspersionProgramFilters(program, filters)
    }

    return AspersionProgramFilterPresentation(
        filters = filters,
        options = AspersionFilterOptions(
            cias = ciaOptions,
            producers = producerOptions,
            ranches = ranchOptions,
            plots = plotOptions
        ),
        programs = filteredPrograms
    )
}

internal fun matchesAspersionProgramFilters(
    program: AspersionProgramItem,
    filters: AspersionFilters
): Boolean {
    val context = program.context
    val selectedCiaId = filters.ciaId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return false

    if (selectedCiaId !in context.dataCentralIds) {
        return false
    }
    if (filters.producerId != null && context.producerId != filters.producerId) {
        return false
    }
    if (filters.ranchId != null && context.ranchId != filters.ranchId) {
        return false
    }
    if (filters.plotId != null && context.plotId != filters.plotId) {
        return false
    }

    val sessionDates = program.sessions.mapNotNull { session ->
        parseAspersionSessionDateMillis(
            session.aspersionDate ?: session.cachedSession?.aspersionDate
        )
    }
    val programStart = parseAspersionSessionDateMillis(program.estStartDate)
        ?: sessionDates.minOrNull()
    val programEnd = parseAspersionSessionDateMillis(program.estFinishDate)
        ?: sessionDates.maxOrNull()
        ?: programStart

    /* El intervalo del subprograma debe cruzarse con el intervalo consultado. */
    if (
        filters.startDateMillis != null &&
        (programEnd == null || programEnd < filters.startDateMillis)
    ) {
        return false
    }
    if (
        filters.endDateMillis != null &&
        (programStart == null || programStart > filters.endDateMillis)
    ) {
        return false
    }

    return true
}

internal fun buildAspersionFilterPresentation(
    sessions: List<LocalAspersionSessionEntity>,
    contexts: Map<String, AspersionSessionContext>,
    requestedFilters: AspersionFilters,
    canFilterByPlot: Boolean,
    availableContexts: Collection<AspersionSessionContext> = contexts.values
): AspersionFilterPresentation {
    var filters = requestedFilters.copy(
        query = "",
        programId = null,
        status = null
    )

    /*
     * Las opciones se construyen desde el catalogo local completo y no solo
     * desde las sesiones descargadas. La CIA permanece como alcance interno;
     * productor, rancho y parcela siguen disponibles aunque una combinacion
     * todavia no tenga aspersiones.
     */
    val optionContexts = availableContexts.ifEmpty { contexts.values }
    val ciaOptions = optionContexts.toCiaOptions()
    val selectedCiaId = filters.ciaId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return AspersionFilterPresentation(
            filters = filters.copy(
                ciaId = null,
                producerId = null,
                ranchId = null,
                plotId = null,
                programId = null
            ),
            options = AspersionFilterOptions(),
            sessions = emptyList()
        )

    val ciaContexts = optionContexts.filter { context ->
        selectedCiaId in context.dataCentralIds
    }

    if (ciaContexts.isEmpty()) {
        return AspersionFilterPresentation(
            filters = filters.copy(
                ciaId = selectedCiaId,
                producerId = null,
                ranchId = null,
                plotId = null,
                programId = null
            ),
            options = AspersionFilterOptions(cias = ciaOptions),
            sessions = emptyList()
        )
    }

    filters = filters.copy(ciaId = selectedCiaId)

    val producerOptions = ciaContexts.toOptions(
        id = AspersionSessionContext::producerId,
        label = AspersionSessionContext::producerName
    )
    if (filters.producerId !in producerOptions.ids()) {
        filters = filters.copy(
            producerId = null,
            ranchId = null,
            plotId = null,
            programId = null
        )
    }

    val ranchContexts = if (filters.producerId == null) {
        emptyList()
    } else {
        ciaContexts.filter { context ->
            context.producerId == filters.producerId
        }
    }
    val ranchOptions = ranchContexts.toOptions(
        id = AspersionSessionContext::ranchId,
        label = AspersionSessionContext::ranchName
    )
    if (filters.ranchId !in ranchOptions.ids()) {
        filters = filters.copy(
            ranchId = null,
            plotId = null,
            programId = null
        )
    }

    val plotContexts = if (!canFilterByPlot || filters.ranchId == null) {
        emptyList()
    } else {
        ranchContexts.filter { context ->
            context.ranchId == filters.ranchId
        }
    }
    val plotOptions = plotContexts.toOptions(
        id = AspersionSessionContext::plotId,
        label = AspersionSessionContext::plotName
    )
    if (!canFilterByPlot) {
        filters = filters.copy(plotId = null)
    } else if (filters.plotId !in plotOptions.ids()) {
        filters = filters.copy(plotId = null, programId = null)
    }

    val programContexts = when {
        canFilterByPlot && filters.plotId != null -> {
            plotContexts.filter { context -> context.plotId == filters.plotId }
        }

        !canFilterByPlot && filters.ranchId != null -> {
            ranchContexts.filter { context -> context.ranchId == filters.ranchId }
        }

        else -> emptyList()
    }
    val programOptions = programContexts.toOptions(
        id = AspersionSessionContext::programId,
        label = AspersionSessionContext::programName
    )
    if (filters.programId !in programOptions.ids()) {
        filters = filters.copy(programId = null)
    }

    val statusOptions = sessions
        .mapNotNull { session ->
            val raw = session.status?.trim()?.takeIf(String::isNotEmpty)
                ?: session.importStatus?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            AspersionFilterOption(
                id = normalizeAspersionText(raw),
                label = aspersionStatusLabel(raw)
            )
        }
        .distinctBy(AspersionFilterOption::id)
        .sortedBy { option -> normalizeAspersionText(option.label) }
    if (filters.status !in statusOptions.ids()) {
        filters = filters.copy(status = null)
    }

    val normalizedStart = filters.startDateMillis?.toStartOfDay()
    val normalizedEnd = filters.endDateMillis?.toEndOfDay()
    filters = if (
        normalizedStart != null &&
        normalizedEnd != null &&
        normalizedStart > normalizedEnd
    ) {
        filters.copy(
            startDateMillis = normalizedEnd.toStartOfDay(),
            endDateMillis = normalizedStart.toEndOfDay()
        )
    } else {
        filters.copy(
            startDateMillis = normalizedStart,
            endDateMillis = normalizedEnd
        )
    }

    val filteredSessions = sessions.filter { session ->
        matchesAspersionFilters(
            session = session,
            context = contexts[session.sessionId],
            filters = filters
        )
    }

    return AspersionFilterPresentation(
        filters = filters,
        options = AspersionFilterOptions(
            cias = ciaOptions,
            producers = producerOptions,
            ranches = ranchOptions,
            plots = plotOptions,
            programs = programOptions,
            statuses = statusOptions
        ),
        sessions = filteredSessions
    )
}

internal fun matchesAspersionFilters(
    session: LocalAspersionSessionEntity,
    context: AspersionSessionContext?,
    filters: AspersionFilters
): Boolean {
    val selectedCiaId = filters.ciaId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return false

    if (selectedCiaId !in context?.dataCentralIds.orEmpty()) {
        return false
    }
    if (filters.producerId != null && context?.producerId != filters.producerId) {
        return false
    }
    if (filters.ranchId != null && context?.ranchId != filters.ranchId) {
        return false
    }
    if (filters.plotId != null && context?.plotId != filters.plotId) {
        return false
    }
    if (filters.programId != null && context?.programId != filters.programId) {
        return false
    }

    val status = session.status?.trim()?.takeIf(String::isNotEmpty)
        ?: session.importStatus?.trim().orEmpty()
    if (
        filters.status != null &&
        normalizeAspersionText(status) != filters.status
    ) {
        return false
    }

    val sessionDate = parseAspersionSessionDateMillis(
        session.aspersionDate ?: session.estStartDate
    )
    if (
        filters.startDateMillis != null &&
        (sessionDate == null || sessionDate < filters.startDateMillis)
    ) {
        return false
    }
    if (
        filters.endDateMillis != null &&
        (sessionDate == null || sessionDate > filters.endDateMillis)
    ) {
        return false
    }

    val query = normalizeAspersionText(filters.query)
    if (query.isBlank()) return true

    return listOf(
        context?.dataCentralNames?.values?.joinToString(" "),
        context?.producerName,
        context?.ranchName,
        context?.plotName,
        context?.programName,
        session.assignedToUsername,
        session.status,
        session.importStatus,
        session.sessionId,
        session.plotId,
        session.programId
    ).any { value ->
        normalizeAspersionText(value.orEmpty()).contains(query)
    }
}

private fun Collection<AspersionSessionContext>.toCiaOptions(): List<AspersionFilterOption> {
    return flatMap { context ->
        context.dataCentralIds.map { ciaId ->
            AspersionFilterOption(
                id = ciaId,
                label = context.dataCentralNames[ciaId]
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: ciaId.take(12)
            )
        }
    }
        .distinctBy(AspersionFilterOption::id)
        .sortedBy { option -> normalizeAspersionText(option.label) }
}

internal fun isAspersionSessionAssignedTo(
    session: LocalAspersionSessionEntity,
    userId: String?,
    username: String?
): Boolean {
    return (userId != null && session.assignedToId == userId) ||
            (
                    username != null &&
                            session.assignedToUsername
                                ?.equals(username, ignoreCase = true) == true
                    )
}

internal fun isAspersionSessionInCiaScope(
    session: LocalAspersionSessionEntity,
    context: AspersionSessionContext?,
    ciaId: String?,
    allowedProducerIds: Set<String>,
    allowedProgramIds: Set<String>,
    allowedPlotIds: Set<String>
): Boolean {
    val cleanCiaId = ciaId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return false

    val producerId = (context?.producerId ?: session.producerId)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return false

    if (producerId !in allowedProducerIds) {
        return false
    }

    val contextCiaIds = context?.dataCentralIds.orEmpty()
    if (contextCiaIds.isNotEmpty() && cleanCiaId !in contextCiaIds) {
        return false
    }

    val programId = (context?.programId ?: session.programId)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (programId != null && programId in allowedProgramIds) {
        return true
    }

    val plotId = (context?.plotId ?: session.plotId)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    return plotId != null && plotId in allowedPlotIds
}

private fun Collection<AspersionSessionContext>.toOptions(
    id: (AspersionSessionContext) -> String?,
    label: (AspersionSessionContext) -> String?
): List<AspersionFilterOption> {
    return mapNotNull { context ->
        val cleanId = id(context)?.trim()?.takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val cleanLabel = label(context)?.trim()?.takeIf(String::isNotEmpty)
            ?: cleanId.take(12)
        AspersionFilterOption(cleanId, cleanLabel)
    }
        .distinctBy(AspersionFilterOption::id)
        .sortedBy { option -> normalizeAspersionText(option.label) }
}

private fun List<AspersionFilterOption>.ids(): Set<String?> =
    mapTo(mutableSetOf<String?>(null), AspersionFilterOption::id)

internal fun normalizeAspersionText(value: String): String {
    val decomposed = Normalizer.normalize(
        value.trim().lowercase(Locale.getDefault()),
        Normalizer.Form.NFD
    )
    return decomposed
        .replace(Regex("\\p{Mn}+"), "")
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
}

internal fun aspersionStatusLabel(value: String): String {
    return when (normalizeAspersionText(value)) {
        "pending", "pendiente" -> "Pendiente"
        "processing", "procesando" -> "Procesando"
        "in progress", "en proceso", "vigente" -> "En proceso"
        "loaded", "cargado", "done" -> "Cargado"
        "completed", "complete", "completado", "finalizado" -> "Completado"
        "error" -> "Con error"
        "cancelled", "canceled", "cancelado" -> "Cancelado"
        "pending mapping" -> "Mapeo pendiente"
        else -> value.trim().replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
        }
    }
}

internal fun parseAspersionSessionDateMillis(value: String?): Long? {
    val clean = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val datePart = clean.take(10)
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
    return runCatching { format.parse(datePart)?.time }
        .getOrNull()
        ?.toStartOfDay()
}

private fun Long.toStartOfDay(): Long {
    return Calendar.getInstance().apply {
        timeInMillis = this@toStartOfDay
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun Long.toEndOfDay(): Long {
    return Calendar.getInstance().apply {
        timeInMillis = this@toEndOfDay
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
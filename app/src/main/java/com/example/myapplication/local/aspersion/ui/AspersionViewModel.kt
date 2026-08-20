package com.example.myapplication.local.aspersion.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.local.aspersion.data.AspersionRepository
import com.example.myapplication.local.aspersion.data.AspersionPdfDownloadResult
import com.example.myapplication.local.aspersion.data.AspersionReportLookupResult
import com.example.myapplication.local.aspersion.data.AspersionSyncResult
import com.example.myapplication.local.api.fieldops.FieldOpsRepository
import com.example.myapplication.local.api.fieldops.MasterProgramTreeApiItem
import com.example.myapplication.local.api.fieldops.ResultadoMasterProgramTreeApi
import com.example.myapplication.local.api.fieldops.ResultadoMasterProgramsApi
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.myapplication.local.api.sync.AgroSyncRepository
import com.example.myapplication.local.api.sync.ResultadoAgroSync


import com.example.myapplication.local.aspersion.report.AspersionReportFileManager
/**
 * Estado exclusivo del módulo de aspersión.
 *
 * Se mantiene separado de MainViewModel para que sesiones, puntos, filtros y
 * mapa no aumenten el tamaño ni la fragilidad del flujo fitosanitario.
 */
class AspersionViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application.applicationContext)
    private val repository = AspersionRepository(
        context = application.applicationContext,
        database = database
    )
    private val fieldOpsRepository = FieldOpsRepository(
        context = application.applicationContext
    )
    private val agroSyncRepository = AgroSyncRepository(
        context = application.applicationContext,
        database = database
    )

    var uiState: AspersionUiState by mutableStateOf(AspersionUiState())
        private set

    private var selectedSessionJob: Job? = null
    private var selectedSessionSyncJob: Job? = null
    private var selectedSessionReportJob: Job? = null
    private var sessionsSyncJob: Job? = null
    private var scopeVersion = 0L
    private var hierarchyVersion = 0L
    private var selectionVersion = 0L
    private var initialSyncRequested = false
    private var scopeConfigured = false
    private var activeUserExtId: String? = null
    private var activeUsername: String? = null
    private var activeCiaLocalId: Long? = null
    private var activeCiaExtId: String? = null
    private var accessMode = AspersionAccessMode.MANAGER
    private var allSessionsCache: List<LocalAspersionSessionEntity> = emptyList()
    private var scopedSessionsCache: List<LocalAspersionSessionEntity> = emptyList()
    private var sessionContextsCache: Map<String, AspersionSessionContext> = emptyMap()
    private var programItemsCache: List<AspersionProgramItem> = emptyList()
    private var remoteProgramTreesCache: List<MasterProgramTreeApiItem> = emptyList()
    private var remoteHierarchyLoaded = false

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.observeSessions().collectLatest { sessions ->
                val observedScopeVersion = scopeVersion
                val observedHierarchyVersion = hierarchyVersion
                allSessionsCache = sessions
                val presentation = withContext(Dispatchers.IO) {
                    buildSessionPresentation(sessions)
                }

                if (
                    observedScopeVersion != scopeVersion ||
                    observedHierarchyVersion != hierarchyVersion
                ) {
                    return@collectLatest
                }

                acceptSessionPresentation(presentation)
            }
        }
    }

    fun onModuleOpened(
        userExtId: String?,
        username: String?,
        ciaLocalId: Long?,
        ciaExtId: String?,
        requestedAccessMode: AspersionAccessMode
    ) {
        val cleanUserExtId = userExtId?.trim()?.takeIf(String::isNotEmpty)
        val cleanUsername = username?.trim()?.takeIf(String::isNotEmpty)
        val cleanCiaExtId = ciaExtId?.trim()?.takeIf(String::isNotEmpty)
        val identityChanged =
            !scopeConfigured ||
                    activeUserExtId != cleanUserExtId ||
                    activeUsername != cleanUsername ||
                    activeCiaLocalId != ciaLocalId ||
                    activeCiaExtId != cleanCiaExtId ||
                    accessMode != requestedAccessMode

        if (identityChanged) {
            scopeVersion += 1L
            hierarchyVersion += 1L
            sessionsSyncJob?.cancel()
            sessionsSyncJob = null
            scopeConfigured = true
            activeUserExtId = cleanUserExtId
            activeUsername = cleanUsername
            activeCiaLocalId = ciaLocalId
            activeCiaExtId = cleanCiaExtId
            accessMode = requestedAccessMode
            initialSyncRequested = false
            remoteProgramTreesCache = emptyList()
            remoteHierarchyLoaded = false
            programItemsCache = emptyList()
            clearSelectedSession()

            updateState {
                it.copy(
                    accessMode = requestedAccessMode,
                    programs = emptyList(),
                    totalProgramsBeforeFilters = 0,
                    filters = defaultSessionFilters(),
                    filterOptions = AspersionFilterOptions(),
                    sessionFiltersExpanded = false,
                    syncingSessions = false,
                    message = null,
                    error = null
                )
            }

            viewModelScope.launch {
                val requestedScopeVersion = scopeVersion
                val requestedHierarchyVersion = hierarchyVersion
                val presentation = withContext(Dispatchers.IO) {
                    buildSessionPresentation(allSessionsCache)
                }
                if (
                    requestedScopeVersion != scopeVersion ||
                    requestedHierarchyVersion != hierarchyVersion
                ) {
                    return@launch
                }
                acceptSessionPresentation(presentation)
            }
        } else if (uiState.sessionFiltersExpanded) {
            updateState { it.copy(sessionFiltersExpanded = false) }
        }

        if (initialSyncRequested) return
        initialSyncRequested = true
        syncSessions(showSuccessMessage = false)
    }

    fun syncSessions(showSuccessMessage: Boolean = true) {
        if (sessionsSyncJob?.isActive == true) return

        val requestedScopeVersion = scopeVersion
        val requestedCiaLocalId = activeCiaLocalId
        val requestedCiaExtId = activeCiaExtId

        sessionsSyncJob = viewModelScope.launch {

            updateState {
                it.copy(
                    syncingSessions = true,
                    error = null
                )
            }

            /*
             * IMPORTANTE:
             *
             * Aspersión necesita productor -> rancho -> parcela
             * para poder resolver correctamente los subprogramas.
             *
             * En una instalación nueva esa estructura todavía puede
             * no existir en Room.
             */
            val agroResult = if (requestedCiaLocalId == null) {

                ResultadoAgroSync.Error(
                    "La CIA seleccionada no está disponible localmente."
                )

            } else {

                withContext(Dispatchers.IO) {
                    agroSyncRepository.sincronizarProductoresRanchosParcelas(
                        idLocalCia = requestedCiaLocalId
                    )
                }
            }

            if (requestedScopeVersion != scopeVersion) {
                return@launch
            }

            /*
             * Después de asegurar la estructura agrícola,
             * descargamos sesiones y árbol productivo.
             */
            val (sessionsResult, hierarchyResult) = coroutineScope {

                val sessionsDeferred = async(Dispatchers.IO) {

                    if (requestedCiaExtId.isNullOrBlank()) {

                        AspersionSyncResult.Error(
                            "La CIA seleccionada todavía no tiene un UUID sincronizado."
                        )

                    } else {

                        repository.syncSessions(
                            dataCentralId = requestedCiaExtId,
                            assignedToId = null
                        )
                    }
                }

                val hierarchyDeferred = async(Dispatchers.IO) {
                    syncProgramHierarchy(requestedCiaExtId)
                }

                sessionsDeferred.await() to hierarchyDeferred.await()
            }

            if (requestedScopeVersion != scopeVersion) {
                return@launch
            }

            /*
             * Guardamos el árbol remoto.
             */
            if (hierarchyResult is AspersionHierarchySyncResult.Updated) {

                remoteProgramTreesCache = hierarchyResult.trees
                remoteHierarchyLoaded = true

                hierarchyVersion += 1L
            }

            /*
             * MUY IMPORTANTE:
             *
             * Llegamos aquí cuando AgroSync ya terminó.
             * Por eso Room ya debe tener:
             *
             * CIA -> productor -> rancho -> parcela
             *
             * y buildSessionPresentation puede resolver los
             * programas correctamente incluso en el primer uso.
             */
            val presentation = withContext(Dispatchers.IO) {
                buildSessionPresentation(allSessionsCache)
            }

            if (requestedScopeVersion != scopeVersion) {
                return@launch
            }

            acceptSessionPresentation(presentation)

            val agroError =
                (agroResult as? ResultadoAgroSync.Error)?.mensaje

            val sessionsError =
                (sessionsResult as? AspersionSyncResult.Error)?.message

            val hierarchyError =
                (hierarchyResult as? AspersionHierarchySyncResult.Error)?.message

            val errorMessage = listOfNotNull(
                agroError,
                sessionsError,
                hierarchyError
            )
                .distinct()
                .joinToString(" ")
                .takeIf(String::isNotBlank)

            val sessionsCount =
                (sessionsResult as? AspersionSyncResult.SessionsUpdated)
                    ?.sessionsCount
                    ?: scopedSessionsCache.size

            val programCount = programItemsCache.size

            updateState {

                it.copy(

                    syncingSessions = false,

                    message =
                    if (
                        showSuccessMessage &&
                        errorMessage == null
                    ) {

                        "Estructura actualizada: " +
                                "$programCount subprogramas y " +
                                "$sessionsCount sesiones"

                    } else {
                        null
                    },

                    error = errorMessage?.let { message ->

                        if (programItemsCache.isEmpty()) {
                            message
                        } else {
                            "No se pudo actualizar todo. " +
                                    "Se muestran los datos guardados."
                        }
                    }
                )
            }
        }
    }

    private suspend fun syncProgramHierarchy(
        ciaExtId: String?
    ): AspersionHierarchySyncResult {
        val cleanCiaId = ciaExtId?.trim()?.takeIf(String::isNotEmpty)
            ?: return AspersionHierarchySyncResult.Error(
                "La CIA seleccionada todavía no tiene un UUID sincronizado."
            )

        return when (
            val mastersResult = fieldOpsRepository.obtenerTodosLosProgramasMaestros(
                datacentral = cleanCiaId
            )
        ) {
            is ResultadoMasterProgramsApi.Error -> {
                AspersionHierarchySyncResult.Error(mastersResult.mensaje)
            }

            is ResultadoMasterProgramsApi.Exito -> {
                val trees = mutableListOf<MasterProgramTreeApiItem>()
                for (master in mastersResult.programasMaestros) {
                    when (
                        val treeResult = fieldOpsRepository.obtenerArbolProgramaMaestro(
                            master.id
                        )
                    ) {
                        is ResultadoMasterProgramTreeApi.Exito -> {
                            trees += treeResult.programaMaestro
                        }

                        is ResultadoMasterProgramTreeApi.Error -> {
                            return AspersionHierarchySyncResult.Error(
                                treeResult.mensaje
                            )
                        }
                    }
                }

                AspersionHierarchySyncResult.Updated(
                    trees = trees,
                    programsCount = trees.sumOf { tree -> tree.programas.size }
                )
            }
        }
    }

    fun updateSessionSearch(value: String) {
        setSessionFilters(uiState.filters.copy(query = value))
    }

    fun selectProducerFilter(producerId: String?) {
        setSessionFilters(
            uiState.filters.copy(
                producerId = producerId,
                ranchId = null,
                plotId = null,
                programId = null
            )
        )
    }

    fun selectRanchFilter(ranchId: String?) {
        setSessionFilters(
            uiState.filters.copy(
                ranchId = ranchId,
                plotId = null,
                programId = null
            )
        )
    }

    fun selectPlotFilter(plotId: String?) {
        setSessionFilters(
            uiState.filters.copy(
                plotId = plotId,
                programId = null
            )
        )
    }

    fun selectProgramFilter(programId: String?) {
        setSessionFilters(uiState.filters.copy(programId = programId))
    }

    fun selectStartDateFilter(dateMillis: Long?) {
        setSessionFilters(uiState.filters.copy(startDateMillis = dateMillis))
    }

    fun selectEndDateFilter(dateMillis: Long?) {
        setSessionFilters(uiState.filters.copy(endDateMillis = dateMillis))
    }

    fun selectStatusFilter(status: String?) {
        setSessionFilters(uiState.filters.copy(status = status))
    }

    fun clearSessionFilters() {
        setSessionFilters(defaultSessionFilters())
    }

    fun toggleSessionFiltersExpanded() {
        updateState {
            it.copy(sessionFiltersExpanded = !it.sessionFiltersExpanded)
        }
    }

    private fun setSessionFilters(filters: AspersionFilters) {
        val presentation = buildAspersionProgramFilterPresentation(
            programs = programItemsCache,
            requestedFilters = filters,
            canFilterByPlot = accessMode.canFilterByPlot
        )
        updateState {
            it.copy(
                programs = presentation.programs,
                filters = presentation.filters,
                filterOptions = presentation.options
            )
        }
    }

    fun selectSession(
        sessionId: String,
        syncIfNeeded: Boolean = true
    ) {
        val cleanId = sessionId.trim()
        if (cleanId.isEmpty()) return

        val sessionIsAllowed = programItemsCache.any { program ->
            program.sessions.any { session -> session.sessionId == cleanId }
        }
        if (!sessionIsAllowed) {
            updateState {
                it.copy(error = "No tienes permiso para abrir esta sesión de aspersión.")
            }
            return
        }

        selectionVersion += 1L
        val requestedSelectionVersion = selectionVersion
        val requestedScopeVersion = scopeVersion
        selectedSessionJob?.cancel()
        selectedSessionSyncJob?.cancel()
        selectedSessionSyncJob = null

        updateState {
            it.copy(
                selectedSessionId = cleanId,
                selectedSession = null,
                selectedPlotName = null,
                selectedProgramName = null,
                plotVertices = emptyList(),
                points = emptyList(),
                stats = null,
                variableStats = emptyList(),
                selectedLayer = AspersionLayer.APPLICATION,
                legendItems = emptyList(),
                visibleBucketKeys = emptySet(),
                filtersExpanded = true,
                syncingSelectedSession = false,
                sessionReport = null,
                loadingSessionReport = true,
                downloadingSessionReport = false,
                sessionReportError = null,
                message = null,
                error = null
            )
        }

        selectedSessionJob = viewModelScope.launch {
            combine(
                repository.observeSession(cleanId),
                repository.observePoints(cleanId),
                repository.observeStats(cleanId),
                repository.observeVariableStats(cleanId)
            ) { session, points, stats, variableStats ->
                SelectedSessionSnapshot(
                    session = session,
                    points = points,
                    stats = stats,
                    variableStats = variableStats
                )
            }.collectLatest { snapshot ->
                if (!isAspersionSelectionCurrent(
                        currentSessionId = uiState.selectedSessionId,
                        requestedSessionId = cleanId,
                        currentSelectionVersion = selectionVersion,
                        requestedSelectionVersion = requestedSelectionVersion,
                        currentScopeVersion = scopeVersion,
                        requestedScopeVersion = requestedScopeVersion
                    )
                ) {
                    return@collectLatest
                }

                val references = withContext(Dispatchers.IO) {
                    resolveSelectedSessionReferences(snapshot.session)
                }
                if (
                    uiState.selectedSessionId != cleanId ||
                    selectionVersion != requestedSelectionVersion
                ) {
                    return@collectLatest
                }
                val currentLayer = uiState.selectedLayer
                val legend = buildAspersionLegend(snapshot.points, currentLayer)
                val previousKeys = uiState.visibleBucketKeys
                val oldLegendKeys = uiState.legendItems
                    .map(AspersionLegendItem::key)
                    .toSet()
                val newKeys = legend
                    .map(AspersionLegendItem::key)
                    .toSet()

                updateState {
                    it.copy(
                        selectedSession = snapshot.session,
                        selectedPlotName = references.plotName,
                        selectedProgramName = references.programName,
                        plotVertices = references.vertices,
                        points = snapshot.points,
                        stats = snapshot.stats,
                        variableStats = snapshot.variableStats,
                        legendItems = legend,
                        visibleBucketKeys = if (
                            previousKeys.isEmpty() ||
                            oldLegendKeys != newKeys
                        ) {
                            newKeys
                        } else {
                            previousKeys.intersect(newKeys)
                        }
                    )
                }
            }
        }

        loadSessionReport(cleanId)

        if (syncIfNeeded) {
            val cachedSession = uiState.sessions.firstOrNull { session ->
                session.sessionId == cleanId
            }
            if (cachedSession?.pointsDownloadComplete != true) {
                syncSelectedSession(cleanId)
            }
        }
    }

    fun syncSelectedSession(
        sessionId: String? = uiState.selectedSessionId
    ) {
        val cleanId = sessionId?.trim().orEmpty()
        if (cleanId.isEmpty() || uiState.selectedSessionId != cleanId) return
        if (selectedSessionSyncJob?.isActive == true) return

        val requestedSelectionVersion = selectionVersion
        val requestedScopeVersion = scopeVersion

        selectedSessionSyncJob = viewModelScope.launch {
            updateState {
                it.copy(
                    syncingSelectedSession = true,
                    error = null
                )
            }

            val result = repository.syncCompleteSession(cleanId)
            if (!isAspersionSelectionCurrent(
                    currentSessionId = uiState.selectedSessionId,
                    requestedSessionId = cleanId,
                    currentSelectionVersion = selectionVersion,
                    requestedSelectionVersion = requestedSelectionVersion,
                    currentScopeVersion = scopeVersion,
                    requestedScopeVersion = requestedScopeVersion
                )
            ) {
                return@launch
            }

            when (result) {
                is AspersionSyncResult.SessionUpdated -> {
                    val area = result.areaTotalHa?.let { hectares ->
                        " · ${formatNumber(hectares, 4)} ha"
                    }.orEmpty()

                    updateState {
                        it.copy(
                            syncingSelectedSession = false,
                            message = "Aspersión descargada: " +
                                    "${result.downloadedPoints} puntos$area"
                        )
                    }
                }

                is AspersionSyncResult.Error -> {
                    updateState {
                        it.copy(
                            syncingSelectedSession = false,
                            error = if (it.points.isEmpty()) {
                                result.message
                            } else {
                                "No se pudo actualizar el mapa. Se conserva la copia guardada."
                            }
                        )
                    }
                }

                is AspersionSyncResult.SessionsUpdated -> {
                    updateState { it.copy(syncingSelectedSession = false) }
                }
            }
        }
    }

    fun refreshSessionReport() {
        val sessionId = uiState.selectedSessionId ?: return
        loadSessionReport(sessionId)
    }

    private fun loadSessionReport(sessionId: String) {
        val cleanSessionId = sessionId.trim()
        if (cleanSessionId.isEmpty()) return

        selectedSessionReportJob?.cancel()
        selectedSessionReportJob = viewModelScope.launch {
            updateState {
                it.copy(
                    loadingSessionReport = true,
                    sessionReportError = null
                )
            }

            when (val result = repository.fetchSessionReport(cleanSessionId)) {
                is AspersionReportLookupResult.Available -> {
                    val dto = result.report
                    updateState {
                        if (it.selectedSessionId != cleanSessionId) {
                            it
                        } else {
                            it.copy(
                                sessionReport = AspersionReportUi(
                                    reportId = dto.id.trim(),
                                    objectId = dto.objectId.trim(),
                                    activityLabel = dto.activityLabel
                                        ?.trim()
                                        ?.takeIf(String::isNotEmpty)
                                        ?: "Reporte de aspersión",
                                    reportDate = dto.reportDate?.trim(),
                                    status = dto.status?.trim(),
                                    statusDisplay = dto.statusDisplay
                                        ?.trim()
                                        ?.takeIf(String::isNotEmpty)
                                        ?: dto.status
                                            ?.replace('_', ' ')
                                            ?.replaceFirstChar { char -> char.uppercase() }
                                            .orEmpty()
                                            .ifBlank { "Disponible" },
                                    resumeText = dto.resumeText?.trim(),
                                    hasMapSnapshot = !dto.mapSnapshot.isNullOrBlank()
                                ),
                                loadingSessionReport = false,
                                sessionReportError = null
                            )
                        }
                    }
                }

                AspersionReportLookupResult.NotAvailable -> {
                    updateState {
                        if (it.selectedSessionId != cleanSessionId) it else it.copy(
                            sessionReport = null,
                            loadingSessionReport = false,
                            sessionReportError = null
                        )
                    }
                }

                is AspersionReportLookupResult.Error -> {
                    updateState {
                        if (it.selectedSessionId != cleanSessionId) it else it.copy(
                            sessionReport = null,
                            loadingSessionReport = false,
                            sessionReportError = result.message
                        )
                    }
                }
            }
        }
    }

    fun viewSessionReportPdf() {
        downloadAndHandleSessionReport(openAfterDownload = true)
    }

    fun downloadSessionReportPdf() {
        downloadAndHandleSessionReport(openAfterDownload = false)
    }

    private fun downloadAndHandleSessionReport(openAfterDownload: Boolean) {
        val report = uiState.sessionReport ?: return
        if (uiState.downloadingSessionReport) return

        viewModelScope.launch {
            updateState {
                it.copy(
                    downloadingSessionReport = true,
                    sessionReportError = null
                )
            }

            when (val result = repository.downloadSessionReportPdf(report.reportId)) {
                is AspersionPdfDownloadResult.Success -> {
                    val context = getApplication<Application>().applicationContext
                    val operation: Result<String> = runCatching {
                        if (openAfterDownload) {
                            val uri = AspersionReportFileManager.saveForViewing(
                                context = context,
                                bytes = result.bytes,
                                fileName = result.fileName
                            )

                            AspersionReportFileManager.openReadOnlyPdf(
                                context = context,
                                pdfUri = uri
                            )

                            "Reporte abierto en modo lectura."
                        } else {
                            AspersionReportFileManager.saveToDownloads(
                                context = context,
                                bytes = result.bytes,
                                fileName = result.fileName
                            )

                            "Reporte descargado en Descargas/Reportes CIAgro."
                        }
                    }

                    updateState {
                        it.copy(
                            downloadingSessionReport = false,
                            message = operation.getOrNull(),
                            sessionReportError = operation.exceptionOrNull()?.let { error ->
                                if (openAfterDownload) {
                                    "El PDF se descargó, pero no hay una aplicación disponible para abrirlo."
                                } else {
                                    "No se pudo guardar el PDF: " +
                                            (error.message ?: error.javaClass.simpleName)
                                }
                            }
                        )
                    }
                }

                is AspersionPdfDownloadResult.Error -> {
                    updateState {
                        it.copy(
                            downloadingSessionReport = false,
                            sessionReportError = result.message
                        )
                    }
                }
            }
        }
    }

    fun selectLayer(layer: AspersionLayer) {
        if (uiState.selectedLayer == layer) return

        val legend = buildAspersionLegend(
            points = uiState.points,
            layer = layer
        )
        updateState {
            it.copy(
                selectedLayer = layer,
                legendItems = legend,
                visibleBucketKeys = legend
                    .map(AspersionLegendItem::key)
                    .toSet()
            )
        }
    }

    fun toggleBucket(key: String) {
        val validKeys = uiState.legendItems
            .map(AspersionLegendItem::key)
            .toSet()
        if (key !in validKeys) return

        val next = uiState.visibleBucketKeys.toMutableSet()
        if (!next.add(key)) {
            next.remove(key)
        }

        updateState {
            it.copy(visibleBucketKeys = next)
        }
    }

    fun showAllBuckets() {
        updateState {
            it.copy(
                visibleBucketKeys = it.legendItems
                    .map(AspersionLegendItem::key)
                    .toSet()
            )
        }
    }

    fun hideAllBuckets() {
        updateState {
            it.copy(visibleBucketKeys = emptySet())
        }
    }

    fun toggleFiltersExpanded() {
        updateState {
            it.copy(filtersExpanded = !it.filtersExpanded)
        }
    }

    fun clearSelectedSession() {
        selectionVersion += 1L
        selectedSessionJob?.cancel()
        selectedSessionJob = null
        selectedSessionSyncJob?.cancel()
        selectedSessionSyncJob = null
        selectedSessionReportJob?.cancel()
        selectedSessionReportJob = null
        updateState {
            it.copy(
                selectedSessionId = null,
                selectedSession = null,
                selectedPlotName = null,
                selectedProgramName = null,
                plotVertices = emptyList(),
                points = emptyList(),
                stats = null,
                variableStats = emptyList(),
                selectedLayer = AspersionLayer.APPLICATION,
                legendItems = emptyList(),
                visibleBucketKeys = emptySet(),
                syncingSelectedSession = false,
                sessionReport = null,
                loadingSessionReport = false,
                downloadingSessionReport = false,
                sessionReportError = null,
                error = null
            )
        }
    }

    fun consumeMessage() {
        updateState { it.copy(message = null) }
    }

    fun consumeError() {
        updateState { it.copy(error = null) }
    }

    private suspend fun buildSessionPresentation(
        sessions: List<LocalAspersionSessionEntity>
    ): SessionPresentation {
        if (!scopeConfigured) {
            return SessionPresentation()
        }

        /*
         * Aspersión siempre requiere una CIA seleccionada.
         * Si la navegación no entregó ningún identificador,
         * nunca debemos mostrar datos almacenados de otras CIA.
         */
        if (
            accessMode.scopeBySelectedCia &&
            activeCiaLocalId == null &&
            activeCiaExtId.isNullOrBlank()
        ) {
            return SessionPresentation()
        }


        val cias = database.localCiaDao().getAllCias()
        val programs = database.localprogramDao().getAllPrograms()
        val plots = database.localPlotDao().getAllPlots()
        val ranches = database.localRanchDao().getAllRanches()
        val producers = database.localAgroUnitDao().getAllAgroUnits()

        val ciaLocalId = activeCiaLocalId
            ?: return SessionPresentation()

        val ciaFilterId = activeCiaFilterId()
            ?: return SessionPresentation()

        /*
         * Solo productores relacionados realmente con la CIA seleccionada.
         * Si la CIA no tiene productores permitidos, aspersión debe quedar vacía.
         */
        val allowedProducersForCia = database.localCiaAgroUnitDao()
            .getProductoresByCia(ciaLocalId)

        if (allowedProducersForCia.isEmpty()) {
            return SessionPresentation()
        }

        val allowedProducerLocalIds = allowedProducersForCia
            .map { producer -> producer.idLocalAgroUnit }
            .toSet()

        val allowedProducerIds = buildSet {
            allowedProducersForCia.forEach { producer ->
                add("local-producer:${producer.idLocalAgroUnit}")

                producer.ext_Id
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(::add)
            }
        }

        val programsByExtId = programs.mapNotNull { program ->
            program.extId?.trim()?.takeIf(String::isNotEmpty)?.let { extId ->
                extId to program
            }
        }.toMap()
        val plotsByExtId = plots.mapNotNull { plot ->
            plot.extId?.trim()?.takeIf(String::isNotEmpty)?.let { extId ->
                extId to plot
            }
        }.toMap()
        val plotsByLocalId = plots.associateBy { plot -> plot.idLocalPlot }
        val ranchesByLocalId = ranches.associateBy { ranch -> ranch.idLocalRanch }
        val producersByLocalId = producers.associateBy { producer ->
            producer.idLocalAgroUnit
        }
        val producersByExtId = producers.mapNotNull { producer ->
            producer.ext_Id?.trim()?.takeIf(String::isNotEmpty)?.let { extId ->
                extId to producer
            }
        }.toMap()
        val ciasByLocalId = cias.associateBy { cia -> cia.idLocalCia }
        val ciasByExtId = cias.mapNotNull { cia ->
            cia.extId?.trim()?.takeIf(String::isNotEmpty)?.let { extId ->
                extId to cia
            }
        }.toMap()

        /*
         * Resuelve el productor de cada subprograma remoto y conserva únicamente
         * los que pertenecen a los productores relacionados con la CIA activa.
         */
        val remoteProducerIdByProgramId = mutableMapOf<String, String>()
        if (remoteHierarchyLoaded) {
            remoteProgramTreesCache.forEach { tree ->
                tree.programas.forEach { remoteProgram ->
                    val localProgram = programsByExtId[remoteProgram.id]
                    val localPlot = remoteProgram.plot?.let(plotsByExtId::get)
                        ?: localProgram?.idLocalPlot?.let(plotsByLocalId::get)
                    val localRanch = localPlot?.idLocalRanch?.let(ranchesByLocalId::get)
                        ?: localProgram?.idLocalRanch?.let(ranchesByLocalId::get)
                    val localProducer = tree.agroUnit?.let(producersByExtId::get)
                        ?: localProgram?.idLocalAgroUnit?.let(producersByLocalId::get)
                        ?: localRanch?.idLocalAgroUnit?.let(producersByLocalId::get)

                    val resolvedProducerId = listOfNotNull(
                        tree.agroUnit?.trim()?.takeIf(String::isNotEmpty),
                        localProducer?.ext_Id?.trim()?.takeIf(String::isNotEmpty),
                        localProducer?.idLocalAgroUnit?.let { "local-producer:$it" }
                    ).firstOrNull { producerId -> producerId in allowedProducerIds }

                    if (resolvedProducerId != null) {
                        remoteProducerIdByProgramId[remoteProgram.id] = resolvedProducerId
                    }
                }
            }
        }

        val allowedRemoteProgramIds = remoteProducerIdByProgramId.keys.toSet()
        val allowedRemotePlotIds = if (remoteHierarchyLoaded) {
            remoteProgramTreesCache.flatMap { tree ->
                tree.programas.mapNotNull { remoteProgram ->
                    if (remoteProgram.id !in allowedRemoteProgramIds) {
                        null
                    } else {
                        remoteProgram.plot?.trim()?.takeIf(String::isNotEmpty)
                    }
                }
            }.toSet()
        } else {
            emptySet()
        }

        val contexts = sessions.associate { session ->
            val localProgram = session.programId?.let(programsByExtId::get)
            val localPlot = session.plotId?.let(plotsByExtId::get)
                ?: localProgram?.idLocalPlot?.let(plotsByLocalId::get)
            val localRanch = localPlot?.idLocalRanch?.let(ranchesByLocalId::get)
                ?: localProgram?.idLocalRanch?.let(ranchesByLocalId::get)
            val localProducer = localProgram
                ?.idLocalAgroUnit
                ?.let(producersByLocalId::get)
                ?: localRanch?.idLocalAgroUnit?.let(producersByLocalId::get)

            val remoteCiaIds = session.dataCentralIds
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.toSet()
                .orEmpty()
            val localProgramCia = localProgram
                ?.idLocalCia
                ?.let(ciasByLocalId::get)
            val localProgramCiaId = localProgramCia?.filterId()
            val contextCiaIds = remoteCiaIds.ifEmpty {
                setOfNotNull(localProgramCiaId)
            }
            val contextCiaNames = contextCiaIds.associateWith { ciaId ->
                ciasByExtId[ciaId]?.nombre
                    ?: cias.firstOrNull { cia -> cia.filterId() == ciaId }?.nombre
                    ?: ciaId.take(12)
            }

            val resolvedSessionProducerId = listOfNotNull(
                session.producerId?.trim()?.takeIf(String::isNotEmpty),
                localProducer?.ext_Id?.trim()?.takeIf(String::isNotEmpty),
                localProducer?.idLocalAgroUnit?.let { "local-producer:$it" }
            ).firstOrNull { producerId -> producerId in allowedProducerIds }

            val context = AspersionSessionContext(
                sessionId = session.sessionId,
                dataCentralIds = contextCiaIds,
                dataCentralNames = contextCiaNames,
                producerId = resolvedSessionProducerId,
                producerName = session.producerName
                    ?: localProducer?.commercial_name,
                ranchId = session.ranchId
                    ?: localRanch?.extId
                    ?: localRanch?.idLocalRanch?.let { "local-ranch:$it" },
                ranchName = session.ranchName ?: localRanch?.name,
                plotId = session.plotId
                    ?: localPlot?.extId
                    ?: localPlot?.idLocalPlot?.let { "local-plot:$it" },
                plotName = resolveAspersionPlotLabel(
                    apiName = session.plotName,
                    localName = localPlot?.name,
                    localCode = localPlot?.code,
                    fallbackId = session.plotId
                ),
                programId = session.programId
                    ?: localProgram?.extId
                    ?: localProgram?.idProgram?.let { "local-program:$it" },
                programName = session.programName
                    ?: session.programCycle
                    ?: localProgram?.cycle
            )
            session.sessionId to context
        }


        val programsForCia = programs.filter { program ->
            program.idLocalCia == ciaLocalId &&
                    program.idLocalAgroUnit in allowedProducerLocalIds
        }

        val allowedProgramIds = buildSet {
            programsForCia.forEach { program ->
                add(
                    program.extId
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: "local-program:${program.idProgram}"
                )
            }
            addAll(allowedRemoteProgramIds)
        }

        val allowedPlotLocalIds = programsForCia
            .map { program -> program.idLocalPlot }
            .toSet()
        val allowedPlotIds = buildSet {
            plots.filter { plot -> plot.idLocalPlot in allowedPlotLocalIds }
                .forEach { plot ->
                    add(
                        plot.extId
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: "local-plot:${plot.idLocalPlot}"
                    )
                }
            addAll(allowedRemotePlotIds)
        }

        val scoped = if (!accessMode.scopeBySelectedCia) {
            sessions
        } else {
            sessions.filter { session ->
                isAspersionSessionInCiaScope(
                    session = session,
                    context = contexts[session.sessionId],
                    ciaId = ciaFilterId,
                    allowedProducerIds = allowedProducerIds,
                    allowedProgramIds = allowedProgramIds,
                    allowedPlotIds = allowedPlotIds
                )
            }
        }

        val scopedContexts = scoped.associate { session ->
            session.sessionId to (
                    contexts[session.sessionId]
                        ?: AspersionSessionContext(session.sessionId)
                    )
        }

        val activeCiaFilterId = activeCiaFilterId()
        val activeCia = activeCiaLocalId?.let(ciasByLocalId::get)
            ?: activeCiaExtId?.let(ciasByExtId::get)
        val activeCiaIds = setOfNotNull(activeCiaFilterId)
        val activeCiaNames = activeCiaFilterId?.let { ciaId ->
            mapOf(ciaId to (activeCia?.nombre ?: ciaId.take(12)))
        }.orEmpty()
        val scopedById = scoped.associateBy(LocalAspersionSessionEntity::sessionId)
        val scopedByProgram = scoped.groupBy { session -> session.programId }

        val programItems = if (remoteHierarchyLoaded) {
            remoteProgramTreesCache.flatMap { tree ->
                tree.programas.mapNotNull { remoteProgram ->
                    val resolvedProducerId = remoteProducerIdByProgramId[remoteProgram.id]
                        ?: return@mapNotNull null
                    val localProgram = programsByExtId[remoteProgram.id]
                    val localPlot = remoteProgram.plot?.let(plotsByExtId::get)
                        ?: localProgram?.idLocalPlot?.let(plotsByLocalId::get)
                    val localRanch = localPlot?.idLocalRanch?.let(ranchesByLocalId::get)
                        ?: localProgram?.idLocalRanch?.let(ranchesByLocalId::get)
                    val localProducer = tree.agroUnit?.let(producersByExtId::get)
                        ?: localProgram?.idLocalAgroUnit?.let(producersByLocalId::get)
                        ?: localRanch?.idLocalAgroUnit?.let(producersByLocalId::get)
                    val programName = remoteProgram.title
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: remoteProgram.voucherCode
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                        ?: remoteProgram.cycle
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                        ?: "Subprograma"
                    val context = AspersionSessionContext(
                        sessionId = "program:${remoteProgram.id}",
                        dataCentralIds = activeCiaIds,
                        dataCentralNames = activeCiaNames,
                        producerId = resolvedProducerId,
                        producerName = localProducer?.commercial_name,
                        ranchId = localRanch?.extId
                            ?: localRanch?.idLocalRanch?.let { "local-ranch:$it" },
                        ranchName = localRanch?.name,
                        plotId = remoteProgram.plot
                            ?: localPlot?.extId
                            ?: localPlot?.idLocalPlot?.let { "local-plot:$it" },
                        plotName = resolveAspersionPlotLabel(
                            apiName = null,
                            localName = localPlot?.name,
                            localCode = localPlot?.code,
                            fallbackId = remoteProgram.plot
                        ),
                        programId = remoteProgram.id,
                        programName = programName
                    )
                    val linkedSessions = LinkedHashMap<String, AspersionProgramSessionItem>()

                    remoteProgram.aspersionSessions.forEach { summary ->
                        val sessionId = summary.id.trim()
                        if (sessionId.isNotEmpty()) {
                            val cached = scopedById[sessionId]
                            linkedSessions[sessionId] = AspersionProgramSessionItem(
                                sessionId = sessionId,
                                aspersionDate = summary.aspersionDate
                                    ?: cached?.aspersionDate,
                                importStatus = summary.importStatus
                                    ?: cached?.importStatus,
                                cachedSession = cached
                            )
                        }
                    }
                    scopedByProgram[remoteProgram.id].orEmpty().forEach { cached ->
                        linkedSessions.putIfAbsent(
                            cached.sessionId,
                            AspersionProgramSessionItem(
                                sessionId = cached.sessionId,
                                aspersionDate = cached.aspersionDate,
                                importStatus = cached.importStatus,
                                cachedSession = cached
                            )
                        )
                    }

                    AspersionProgramItem(
                        programId = remoteProgram.id,
                        programName = programName,
                        masterProgramId = tree.id,
                        masterProgramName = tree.title
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: tree.code,
                        plotId = context.plotId,
                        plotName = context.plotName,
                        estStartDate = remoteProgram.estStartDate,
                        estFinishDate = remoteProgram.estFinishDate,
                        status = remoteProgram.statusDisplay
                            ?: remoteProgram.status,
                        context = context,
                        sessions = linkedSessions.values
                            .sortedByDescending { session -> session.aspersionDate.orEmpty() }
                    )
                }
            }
        } else {
            val localItems = programsForCia.map { program ->
                val programId = program.extId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: "local-program:${program.idProgram}"
                val localPlot = plotsByLocalId[program.idLocalPlot]
                val localRanch = ranchesByLocalId[program.idLocalRanch]
                val localProducer = producersByLocalId[program.idLocalAgroUnit]
                val context = AspersionSessionContext(
                    sessionId = "program:$programId",
                    dataCentralIds = activeCiaIds,
                    dataCentralNames = activeCiaNames,
                    producerId = localProducer?.ext_Id
                        ?: localProducer?.idLocalAgroUnit?.let { "local-producer:$it" },
                    producerName = localProducer?.commercial_name,
                    ranchId = localRanch?.extId
                        ?: localRanch?.idLocalRanch?.let { "local-ranch:$it" },
                    ranchName = localRanch?.name,
                    plotId = localPlot?.extId
                        ?: localPlot?.idLocalPlot?.let { "local-plot:$it" },
                    plotName = resolveAspersionPlotLabel(
                        apiName = null,
                        localName = localPlot?.name,
                        localCode = localPlot?.code,
                        fallbackId = localPlot?.extId
                            ?: localPlot?.idLocalPlot?.let { "local-plot:$it" }
                    ),
                    programId = programId,
                    programName = program.cycle
                )
                AspersionProgramItem(
                    programId = programId,
                    programName = program.cycle,
                    plotId = context.plotId,
                    plotName = context.plotName,
                    estStartDate = formatLocalAspersionDate(program.estStartDate),
                    estFinishDate = formatLocalAspersionDate(program.estFinishDate),
                    status = program.status,
                    context = context,
                    sessions = scopedByProgram[programId].orEmpty().map { session ->
                        session.toAspersionProgramSessionItem()
                    }
                )
            }

            if (localItems.isNotEmpty()) {
                localItems
            } else {
                scoped.groupBy { session ->
                    session.programId ?: "session:${session.sessionId}"
                }.map { (programId, programSessions) ->
                    val first = programSessions.first()
                    val context = scopedContexts[first.sessionId]
                        ?: AspersionSessionContext(first.sessionId)
                    AspersionProgramItem(
                        programId = programId,
                        programName = context.programName
                            ?: first.programName
                            ?: first.programCycle
                            ?: "Subprograma",
                        plotId = context.plotId,
                        plotName = context.plotName,
                        estStartDate = first.estStartDate,
                        estFinishDate = first.estFinishDate,
                        status = first.status,
                        context = context.copy(
                            sessionId = "program:$programId",
                            dataCentralIds = context.dataCentralIds.ifEmpty { activeCiaIds },
                            dataCentralNames = context.dataCentralNames.ifEmpty { activeCiaNames },
                            programId = programId
                        ),
                        sessions = programSessions.map { session ->
                            session.toAspersionProgramSessionItem()
                        }
                    )
                }
            }
        }

        val contextsWithTree = scopedContexts.toMutableMap()
        programItems.forEach { program ->
            program.sessions.forEach { session ->
                contextsWithTree[session.sessionId] = program.context.copy(
                    sessionId = session.sessionId
                )
            }
        }
        return SessionPresentation(
            programItems = programItems,
            sessions = scoped,
            contexts = contextsWithTree,
            plotNamesById = scoped.mapNotNull { session ->
                val name = contextsWithTree[session.sessionId]?.plotName
                session.plotId?.let { id -> name?.let { id to it } }
            }.toMap(),
            programNamesById = scoped.mapNotNull { session ->
                val name = contextsWithTree[session.sessionId]?.programName
                session.programId?.let { id -> name?.let { id to it } }
            }.toMap(),
            unresolvedSessionsCount = scoped.count { session ->
                val context = contextsWithTree[session.sessionId]
                context == null ||
                        context.producerName == null ||
                        context.ranchName == null ||
                        context.plotName == null ||
                        context.programName == null
            }
        )
    }

    private fun acceptSessionPresentation(presentation: SessionPresentation) {
        scopedSessionsCache = presentation.sessions
        sessionContextsCache = presentation.contexts
        programItemsCache = presentation.programItems
        val filtered = buildAspersionProgramFilterPresentation(
            programs = programItemsCache,
            requestedFilters = uiState.filters,
            canFilterByPlot = accessMode.canFilterByPlot
        )

        updateState {
            it.copy(
                programs = filtered.programs,
                totalProgramsBeforeFilters = programItemsCache.size,
                sessions = scopedSessionsCache,
                totalSessionsBeforeFilters = scopedSessionsCache.size,
                unresolvedSessionsCount = presentation.unresolvedSessionsCount,
                sessionContextsById = sessionContextsCache,
                filters = filtered.filters,
                filterOptions = filtered.options,
                plotNamesById = presentation.plotNamesById,
                programNamesById = presentation.programNamesById
            )
        }
    }

    private suspend fun resolveSelectedSessionReferences(
        session: LocalAspersionSessionEntity?
    ): SelectedSessionReferences {
        if (session == null) return SelectedSessionReferences()

        val plot = session.plotId?.let { plotId ->
            database.localPlotDao().getPlotByExtId(plotId)
        }
        val program = session.programId?.let { programId ->
            database.localprogramDao().getProgramByExtId(programId)
        }
        val vertices = plot?.let { localPlot ->
            database.LocalPlotVertexDao()
                .getVerticesByPlot(localPlot.idLocalPlot)
        }.orEmpty()

        return SelectedSessionReferences(
            plotName = resolveAspersionPlotLabel(
                apiName = session.plotName,
                localName = plot?.name,
                localCode = plot?.code,
                fallbackId = session.plotId
            ),
            programName = session.programName
                ?: session.programCycle
                ?: program?.cycle,
            vertices = vertices
        )
    }

    private inline fun updateState(
        transform: (AspersionUiState) -> AspersionUiState
    ) {
        uiState = transform(uiState)
    }

    private fun defaultSessionFilters(): AspersionFilters {
        return AspersionFilters(
            ciaId = activeCiaFilterId().takeIf { accessMode.scopeBySelectedCia }
        )
    }

    private fun activeCiaFilterId(): String? {
        return activeCiaExtId
            ?: activeCiaLocalId?.let { localId -> "local-cia:$localId" }
    }
}


private fun resolveAspersionPlotLabel(
    apiName: String?,
    localName: String?,
    localCode: String?,
    fallbackId: String?
): String? {
    fun clean(value: String?): String? =
        value?.trim()?.takeIf(String::isNotEmpty)

    fun isGeneric(value: String): Boolean {
        return value.equals("Parcela", ignoreCase = true) ||
                value.equals("Plot", ignoreCase = true) ||
                value.equals("Lote", ignoreCase = true)
    }

    val api = clean(apiName)
    val name = clean(localName)
    val code = clean(localCode)

    return when {
        api != null && !isGeneric(api) -> api
        name != null && !isGeneric(name) -> name
        code != null -> code
        api != null -> fallbackId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { id -> "Parcela ${id.takeLast(6)}" }
            ?: api
        name != null -> fallbackId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { id -> "Parcela ${id.takeLast(6)}" }
            ?: name
        else -> fallbackId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { id -> "Parcela ${id.takeLast(6)}" }
    }
}

internal fun isAspersionSelectionCurrent(
    currentSessionId: String?,
    requestedSessionId: String,
    currentSelectionVersion: Long,
    requestedSelectionVersion: Long,
    currentScopeVersion: Long,
    requestedScopeVersion: Long
): Boolean {
    return currentSessionId == requestedSessionId &&
            currentSelectionVersion == requestedSelectionVersion &&
            currentScopeVersion == requestedScopeVersion
}

private data class SelectedSessionSnapshot(
    val session: LocalAspersionSessionEntity?,
    val points: List<com.example.myapplication.local.entities.LocalAspersionPointEntity>,
    val stats: com.example.myapplication.local.entities.LocalAspersionStatsEntity?,
    val variableStats: List<com.example.myapplication.local.entities.LocalAspersionVariableStatEntity>
)

private data class SelectedSessionReferences(
    val plotName: String? = null,
    val programName: String? = null,
    val vertices: List<com.example.myapplication.local.entities.LocalPlotVertexEntity> = emptyList()
)

private data class SessionPresentation(
    val programItems: List<AspersionProgramItem> = emptyList(),
    val sessions: List<LocalAspersionSessionEntity> = emptyList(),
    val contexts: Map<String, AspersionSessionContext> = emptyMap(),
    val plotNamesById: Map<String, String> = emptyMap(),
    val programNamesById: Map<String, String> = emptyMap(),
    val unresolvedSessionsCount: Int = 0
)

private sealed interface AspersionHierarchySyncResult {
    data class Updated(
        val trees: List<MasterProgramTreeApiItem>,
        val programsCount: Int
    ) : AspersionHierarchySyncResult

    data class Error(
        val message: String
    ) : AspersionHierarchySyncResult
}

private fun LocalAspersionSessionEntity.toAspersionProgramSessionItem(): AspersionProgramSessionItem {
    return AspersionProgramSessionItem(
        sessionId = sessionId,
        aspersionDate = aspersionDate,
        importStatus = importStatus,
        cachedSession = this
    )
}

private fun formatLocalAspersionDate(value: Long): String? {
    if (value <= 0L) return null
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date(value))
}

private fun com.example.myapplication.local.entities.LocalCiaEntity.filterId(): String {
    return extId?.trim()?.takeIf(String::isNotEmpty)
        ?: "local-cia:$idLocalCia"
}
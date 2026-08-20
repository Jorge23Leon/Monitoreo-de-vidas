package com.example.myapplication.local.ndvi.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalNdviSessionEntity
import com.example.myapplication.local.ndvi.data.NdviRepository
import com.example.myapplication.local.ndvi.data.NdviVariableConfigResult
import com.example.myapplication.local.ndvi.data.NdviSyncResult
import com.example.myapplication.local.ndvi.model.NdviIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * ViewModel exclusivo del módulo NDVI.
 *
 * Responsabilidades:
 *
 * - observar sesiones guardadas en Room
 * - sincronizar sesiones con Django
 * - seleccionar una sesión
 * - observar sus puntos
 * - sincronizar sus puntos
 * - controlar el índice seleccionado
 *
 * La UI no hablará directamente con Retrofit ni Room.
 */
class NdviViewModel(
    application: Application
) : AndroidViewModel(application) {



    // =========================================================
    // BASE DE DATOS Y REPOSITORY
    // =========================================================

    private val database =
        AppDatabase.getDatabase(
            application.applicationContext
        )


    private val repository =
        NdviRepository(
            context = application.applicationContext,
            database = database
        )


    // =========================================================
    // ESTADO DE LA INTERFAZ
    // =========================================================

    var uiState: NdviUiState by
    mutableStateOf(
        NdviUiState()
    )
        private set


    // =========================================================
    // JOBS
    // =========================================================

    /**
     * Observación de los puntos de la
     * sesión seleccionada.
     */
    private var selectedSessionJob: Job? = null


    /**
     * Sincronización de la sesión seleccionada.
     */
    private var selectedSessionSyncJob: Job? = null


    /**
     * Sincronización de la lista de sesiones.
     */
    private var sessionsSyncJob: Job? = null


    /** Configuración visual de la sesión seleccionada. */
    private var variableConfigJob: Job? = null

    /** CIA/DataCentral desde la que se abrió NDVI. */
    private var activeDataCentralId: String? = null


    /**
     * Fuente completa antes de filtros.
     * La UI recibe únicamente la lista filtrada.
     */
    private var allSessionsCache:
            List<LocalNdviSessionEntity> = emptyList()


    private var sessionContextsCache:
            Map<String, NdviSessionContext> = emptyMap()


    /**
     * Se incrementa cada vez que el usuario
     * cambia de sesión.
     *
     * Nos protege de resultados viejos.
     */
    private var selectionVersion: Long = 0L


    /**
     * Evita solicitar automáticamente la misma
     * sincronización varias veces al abrir pantalla.
     */
    private var initialSyncRequested: Boolean = false


    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    init {

        /**
         * Desde que nace el ViewModel
         * comenzamos a observar Room.
         */
        observeSessions()
    }


    // =========================================================
    // OBSERVAR SESIONES
    // =========================================================

    /**
     * Room será la fuente principal de la pantalla.
     *
     * Si el Repository actualiza Room:
     *
     * Room
     *   ↓
     * Flow
     *   ↓
     * ViewModel
     *   ↓
     * Compose
     */
    private fun observeSessions() {

        viewModelScope.launch {

            repository
                .observeSessions()
                .collectLatest { sessions ->

                    allSessionsCache =
                        sessions

                    val contextSnapshot =
                        withContext(Dispatchers.IO) {

                            buildNdviSessionContexts(
                                sessions = sessions
                            )
                        }

                    sessionContextsCache =
                        contextSnapshot

                    applyCurrentSessionFilters()
                }
        }
    }


    /**
     * Resuelve los nombres locales para que NDVI use la misma
     * estructura visual que Aspersión/Monitoreo:
     *
     * productor -> rancho -> parcela -> sesión.
     */
    private suspend fun buildNdviSessionContexts(
        sessions: List<LocalNdviSessionEntity>
    ): Map<String, NdviSessionContext> {

        return sessions.associate { session ->

            val localProgram =
                session.programId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { extId ->

                        database
                            .localprogramDao()
                            .getProgramByExtId(
                                extId
                            )
                    }


            val localPlot =
                session.plotId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { extId ->

                        database
                            .localPlotDao()
                            .getPlotByExtId(
                                extId
                            )
                    }
                    ?: localProgram
                        ?.idLocalPlot
                        ?.let { id ->

                            database
                                .localPlotDao()
                                .getPlotById(
                                    id
                                )
                        }


            val localRanch =
                localPlot
                    ?.idLocalRanch
                    ?.let { id ->

                        database
                            .localRanchDao()
                            .getRanchById(
                                id
                            )
                    }
                    ?: localProgram
                        ?.idLocalRanch
                        ?.let { id ->

                            database
                                .localRanchDao()
                                .getRanchById(
                                    id
                                )
                        }


            val localProducer =
                localRanch
                    ?.idLocalAgroUnit
                    ?.let { id ->

                        database
                            .localAgroUnitDao()
                            .getAgroUnitById(
                                id
                            )
                    }
                    ?: localProgram
                        ?.idLocalAgroUnit
                        ?.let { id ->

                            database
                                .localAgroUnitDao()
                                .getAgroUnitById(
                                    id
                                )
                        }


            val localCia =
                localProgram
                    ?.idLocalCia
                    ?.let { id ->

                        database
                            .localCiaDao()
                            .getCiaById(
                                id
                            )
                    }


            val context =
                NdviSessionContext(
                    sessionId =
                    session.sessionId,

                    ciaId =
                    localCia
                        ?.extId,

                    producerId =
                    localProducer
                        ?.ext_Id,

                    producerName =
                    localProducer
                        ?.commercial_name,

                    ranchId =
                    localRanch
                        ?.extId,

                    ranchName =
                    localRanch
                        ?.name,

                    plotId =
                    localPlot
                        ?.extId
                        ?: session.plotId,

                    plotName =
                    localPlot
                        ?.name,

                    programId =
                    localProgram
                        ?.extId
                        ?: session.programId,

                    programName =
                    localProgram
                        ?.cycle
                )


            session.sessionId to
                    context
        }
    }


    /**
     * Aplica alcance de CIA + filtros visibles.
     *
     * Si una sesión todavía no puede resolverse contra el catálogo
     * local no la eliminamos; el backend ya aplica permisos del usuario.
     */
    private fun applyCurrentSessionFilters(
        requestedFilters: NdviFilters =
            uiState.filters
    ) {

        val scopedSessions =
            activeDataCentralId
                ?.let { ciaId ->

                    allSessionsCache.filter { session ->

                        val context =
                            sessionContextsCache[
                                session.sessionId
                            ]

                        context
                            ?.ciaId == null ||
                                context?.ciaId == ciaId
                    }
                }
                ?: allSessionsCache


        val presentation =
            buildNdviFilterPresentation(
                sessions =
                scopedSessions,
                contexts =
                sessionContextsCache,
                requestedFilters =
                requestedFilters
            )


        val selected =
            uiState.selectedSessionId
                ?.let { selectedId ->

                    allSessionsCache
                        .firstOrNull { session ->

                            session.sessionId ==
                                    selectedId
                        }
                }


        updateState {

            it.copy(
                sessions =
                presentation.sessions,

                totalSessionsBeforeFilters =
                scopedSessions.size,

                sessionContextsById =
                sessionContextsCache,

                filters =
                presentation.filters,

                filterOptions =
                presentation.options,

                selectedSession =
                selected
            )
        }
    }





    // =========================================================
    // ABRIR MÓDULO
    // =========================================================

    /**
     * Se llamará cuando el usuario entre
     * al módulo NDVI.
     *
     * Por ahora programId y plotId son opcionales.
     *
     * Más adelante, cuando conectemos navegación
     * y permisos, definiremos el alcance exacto
     * de CIA -> productor -> rancho -> parcela.
     */
    fun onModuleOpened(
        programId: String? = null,
        plotId: String? = null,
        dataCentralId: String? = null
    ) {

        val cleanDataCentralId =
            dataCentralId
                ?.trim()
                ?.takeIf(String::isNotEmpty)

        val scopeChanged =
            activeDataCentralId != cleanDataCentralId

        if (scopeChanged) {

            activeDataCentralId = cleanDataCentralId
            initialSyncRequested = false

            /**
             * Evita reutilizar la configuración visual
             * de otra CIA sobre la misma sesión.
             */
            clearSelectedSession()

            updateState {
                it.copy(
                    filters = NdviFilters(),
                    filterOptions = NdviFilterOptions(),
                    sessionFiltersExpanded = false
                )
            }

            applyCurrentSessionFilters(
                requestedFilters =
                NdviFilters()
            )
        }

        if (initialSyncRequested) {
            return
        }

        initialSyncRequested = true

        refreshSessions(
            programId = programId,
            plotId = plotId
        )
    }


    // =========================================================
    // SINCRONIZAR SESIONES
    // =========================================================

    /**
     * Actualiza los headers NDVI.
     */
    fun refreshSessions(
        programId: String? = null,
        plotId: String? = null
    ) {

        /**
         * Evita disparar dos sincronizaciones
         * al mismo tiempo.
         */
        if (
            sessionsSyncJob?.isActive == true
        ) {
            return
        }


        sessionsSyncJob =
            viewModelScope.launch {


                updateState {

                    it.copy(
                        syncingSessions = true,
                        error = null,
                        message = null
                    )
                }


                val result =
                    repository.syncSessions(
                        programId = programId,
                        plotId = plotId
                    )


                when (result) {

                    is NdviSyncResult.SessionsUpdated -> {

                        updateState {

                            it.copy(
                                syncingSessions = false,

                                message =
                                "Sesiones NDVI actualizadas: " +
                                        result.sessionsCount
                            )
                        }
                    }


                    is NdviSyncResult.Error -> {

                        updateState {

                            it.copy(
                                syncingSessions = false,

                                error =
                                if (
                                    it.sessions.isEmpty()
                                ) {

                                    result.message

                                } else {

                                    /**
                                     * Si ya teníamos caché,
                                     * no dejamos la pantalla vacía.
                                     */
                                    "No se pudo actualizar NDVI. " +
                                            "Se muestran los datos guardados."
                                }
                            )
                        }
                    }


                    is NdviSyncResult.SessionUpdated -> {

                        /**
                         * Este resultado normalmente pertenece
                         * a syncSession(), no a syncSessions().
                         *
                         * Lo manejamos para mantener when exhaustivo.
                         */
                        updateState {

                            it.copy(
                                syncingSessions = false
                            )
                        }
                    }
                }
            }
    }


    // =========================================================
    // FILTROS DE SESIONES
    // =========================================================

    fun selectProducerFilter(
        producerId: String?
    ) {

        applyCurrentSessionFilters(
            requestedFilters =
            uiState.filters.copy(
                producerId =
                producerId,
                ranchId =
                null,
                plotId =
                null
            )
        )
    }


    fun selectRanchFilter(
        ranchId: String?
    ) {

        applyCurrentSessionFilters(
            requestedFilters =
            uiState.filters.copy(
                ranchId =
                ranchId,
                plotId =
                null
            )
        )
    }


    fun selectPlotFilter(
        plotId: String?
    ) {

        applyCurrentSessionFilters(
            requestedFilters =
            uiState.filters.copy(
                plotId =
                plotId
            )
        )
    }


    fun selectStartDateFilter(
        millis: Long?
    ) {

        applyCurrentSessionFilters(
            requestedFilters =
            uiState.filters.copy(
                startDateMillis =
                millis
            )
        )
    }


    fun selectEndDateFilter(
        millis: Long?
    ) {

        applyCurrentSessionFilters(
            requestedFilters =
            uiState.filters.copy(
                endDateMillis =
                millis
            )
        )
    }


    fun clearSessionFilters() {

        applyCurrentSessionFilters(
            requestedFilters =
            NdviFilters()
        )
    }


    fun toggleSessionFiltersExpanded() {

        updateState {

            it.copy(
                sessionFiltersExpanded =
                !it.sessionFiltersExpanded
            )
        }
    }


    // =========================================================
    // SELECCIONAR SESIÓN
    // =========================================================

    /**
     * Se ejecutará cuando el usuario toque
     * una sesión de la lista.
     */
    fun selectSession(
        sessionId: String,
        syncIfNeeded: Boolean = true
    ) {

        val cleanId =
            sessionId
                .trim()


        if (cleanId.isEmpty()) {
            return
        }


        // Nueva selección.
        selectionVersion += 1L

        val requestedSelectionVersion =
            selectionVersion


        /**
         * Cancelamos cualquier observación
         * perteneciente a la sesión anterior.
         */
        selectedSessionJob?.cancel()

        selectedSessionSyncJob?.cancel()

        selectedSessionSyncJob = null


        variableConfigJob?.cancel()

        variableConfigJob = null


        /**
         * Si la sesión ya existe en Room,
         * la mostramos inmediatamente.
         */
        val cachedSession =
            allSessionsCache.firstOrNull {
                    session ->

                session.sessionId ==
                        cleanId
            }


        updateState {

            it.copy(
                selectedSessionId = cleanId,
                selectedSession = cachedSession,
                points = emptyList(),

                selectedIndex =
                NdviIndex.NDVI,

                syncingSelectedSession = false,

                variableConfigJson = null,
                loadingVariableConfig = false,
                variableConfigError = null,

                message = null,
                error = null
            )
        }


        // =====================================================
        // OBSERVAR PUNTOS DE ROOM
        // =====================================================

        selectedSessionJob =
            viewModelScope.launch {

                repository
                    .observePoints(
                        sessionId = cleanId
                    )
                    .collectLatest { points ->


                        /**
                         * Puede pasar:
                         *
                         * Usuario toca sesión A
                         * ↓
                         * inmediatamente toca sesión B
                         * ↓
                         * A termina de emitir datos
                         *
                         * No queremos que los puntos de A
                         * aparezcan dentro de B.
                         */
                        if (
                            uiState.selectedSessionId !=
                            cleanId
                        ) {
                            return@collectLatest
                        }


                        if (
                            selectionVersion !=
                            requestedSelectionVersion
                        ) {
                            return@collectLatest
                        }


                        updateState {

                            it.copy(
                                points = points
                            )
                        }
                    }
            }


        // =====================================================
        // ¿HAY QUE DESCARGAR?
        // =====================================================

        if (syncIfNeeded) {

            /**
             * Si nunca terminamos de descargar
             * los puntos de esta sesión,
             * lanzamos la sincronización.
             */
            if (
                cachedSession
                    ?.pointsDownloadComplete != true
            ) {

                syncSelectedSession(
                    sessionId = cleanId
                )
            }
        }


        /**
         * Pedimos la configuración de bandas/colores de la CIA.
         * La superficie se calcula localmente con los puntos.
         */
        loadVariableConfig(
            sessionId = cleanId
        )
    }


    // =========================================================
    // SINCRONIZAR SESIÓN SELECCIONADA
    // =========================================================

    /**
     * Descarga:
     *
     * header
     * +
     * todos sus puntos NDVI
     */
    fun syncSelectedSession(
        sessionId: String? =
            uiState.selectedSessionId
    ) {

        val cleanId =
            sessionId
                ?.trim()
                .orEmpty()


        if (cleanId.isEmpty()) {
            return
        }


        /**
         * La sesión que se quiere sincronizar
         * debe seguir siendo la seleccionada.
         */
        if (
            uiState.selectedSessionId !=
            cleanId
        ) {
            return
        }


        /**
         * Evitamos sincronización duplicada.
         */
        if (
            selectedSessionSyncJob
                ?.isActive == true
        ) {
            return
        }


        val requestedSelectionVersion =
            selectionVersion


        selectedSessionSyncJob =
            viewModelScope.launch {


                updateState {

                    it.copy(
                        syncingSelectedSession = true,
                        error = null,
                        message = null
                    )
                }


                val result =
                    repository.syncSession(
                        sessionId = cleanId
                    )


                /**
                 * Si el usuario cambió de sesión
                 * mientras descargábamos, ignoramos
                 * el resultado visual.
                 *
                 * Los datos sí pueden haber quedado
                 * correctamente guardados en Room.
                 */
                if (
                    uiState.selectedSessionId !=
                    cleanId
                ) {
                    return@launch
                }


                if (
                    selectionVersion !=
                    requestedSelectionVersion
                ) {
                    return@launch
                }


                when (result) {

                    is NdviSyncResult.SessionUpdated -> {

                        updateState {

                            it.copy(
                                syncingSelectedSession = false,

                                message =
                                "NDVI descargado: " +
                                        "${result.downloadedPoints} puntos"
                            )
                        }
                    }


                    is NdviSyncResult.Error -> {

                        updateState {

                            it.copy(
                                syncingSelectedSession = false,

                                error =
                                if (
                                    it.points.isEmpty()
                                ) {

                                    result.message

                                } else {

                                    /**
                                     * Si existe caché anterior,
                                     * la seguimos mostrando.
                                     */
                                    "No se pudo actualizar NDVI. " +
                                            "Se conserva la copia guardada."
                                }
                            )
                        }
                    }


                    is NdviSyncResult.SessionsUpdated -> {

                        updateState {

                            it.copy(
                                syncingSelectedSession = false
                            )
                        }
                    }
                }
            }
    }


    // =========================================================
    // CONFIGURACIÓN VISUAL NDVI
    // =========================================================

    private fun loadVariableConfig(
        sessionId: String? = uiState.selectedSessionId
    ) {

        val cleanSessionId =
            sessionId
                ?.trim()
                .orEmpty()

        if (cleanSessionId.isEmpty()) {
            return
        }

        if (
            uiState.selectedSessionId != cleanSessionId
        ) {
            return
        }

        variableConfigJob?.cancel()

        val requestedSelectionVersion =
            selectionVersion

        variableConfigJob =
            viewModelScope.launch {

                updateState {
                    it.copy(
                        loadingVariableConfig = true,
                        variableConfigError = null
                    )
                }

                when (
                    val result =
                        repository.getVariableConfig(
                            sessionId = cleanSessionId,
                            dataCentralId = activeDataCentralId
                        )
                ) {

                    is NdviVariableConfigResult.Success -> {

                        if (
                            uiState.selectedSessionId != cleanSessionId ||
                            selectionVersion != requestedSelectionVersion
                        ) {
                            return@launch
                        }

                        updateState {
                            it.copy(
                                variableConfigJson = result.json,
                                loadingVariableConfig = false,
                                variableConfigError = null
                            )
                        }
                    }

                    is NdviVariableConfigResult.Error -> {

                        if (
                            uiState.selectedSessionId != cleanSessionId ||
                            selectionVersion != requestedSelectionVersion
                        ) {
                            return@launch
                        }

                        updateState {
                            it.copy(
                                variableConfigJson = null,
                                loadingVariableConfig = false,
                                variableConfigError = result.message
                            )
                        }
                    }
                }
            }
    }


    // =========================================================
    // CAMBIAR ÍNDICE VEGETATIVO
    // =========================================================

    /**
     * Ejemplo:
     *
     * NDVI
     *   ↓
     * usuario toca
     *   ↓
     * NDRE
     *
     * Todavía no cambiaremos colores ni contornos.
     * Eso vendrá con el mapa.
     */
    fun selectIndex(
        index: NdviIndex
    ) {

        if (
            uiState.selectedIndex == index
        ) {
            return
        }

        /**
         * No hacemos otra llamada al backend.
         * Los 12 índices ya viven en los puntos de Room y el
         * WebView recalcula la superficie localmente.
         */
        updateState {
            it.copy(
                selectedIndex = index
            )
        }
    }


    // =========================================================
    // REFRESCAR MANUALMENTE SESIÓN
    // =========================================================

    /**
     * Este método lo podremos conectar después
     * a un botón de "Sincronizar".
     */
    fun refreshSelectedSession() {

        val sessionId =
            uiState.selectedSessionId

        syncSelectedSession(
            sessionId = sessionId
        )

        loadVariableConfig(
            sessionId = sessionId
        )
    }


    // =========================================================
    // CERRAR SESIÓN SELECCIONADA
    // =========================================================

    fun clearSelectedSession() {

        selectionVersion += 1L


        selectedSessionJob?.cancel()

        selectedSessionJob = null


        selectedSessionSyncJob?.cancel()

        selectedSessionSyncJob = null


        variableConfigJob?.cancel()

        variableConfigJob = null


        updateState {

            it.copy(
                selectedSessionId = null,
                selectedSession = null,
                points = emptyList(),

                selectedIndex =
                NdviIndex.NDVI,

                syncingSelectedSession = false,

                variableConfigJson = null,
                loadingVariableConfig = false,
                variableConfigError = null,

                message = null,
                error = null
            )
        }
    }


    // =========================================================
    // CONSUMIR MENSAJES
    // =========================================================

    fun consumeMessage() {

        updateState {

            it.copy(
                message = null
            )
        }
    }


    fun consumeError() {

        updateState {

            it.copy(
                error = null
            )
        }
    }


    // =========================================================
    // MODIFICAR ESTADO
    // =========================================================

    private inline fun updateState(
        transform: (NdviUiState) -> NdviUiState
    ) {

        uiState =
            transform(
                uiState
            )
    }
}
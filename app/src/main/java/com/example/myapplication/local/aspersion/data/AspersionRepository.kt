package com.example.myapplication.local.aspersion.data

import android.content.Context
import androidx.room.withTransaction
import com.example.myapplication.local.aspersion.data.remote.AspersionApiService
import com.example.myapplication.local.aspersion.data.remote.AspersionPaginatedResponse
import com.example.myapplication.local.aspersion.data.remote.AspersionPointDto
import com.example.myapplication.local.aspersion.data.remote.AspersionSessionDto
import com.example.myapplication.local.aspersion.data.remote.AspersionStatsDto
import com.example.myapplication.local.aspersion.data.remote.AspersionVariableStatsDto
import com.example.myapplication.local.api.core.RetrofitClient
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAspersionPointEntity
import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import com.example.myapplication.local.entities.LocalAspersionStatsEntity
import com.example.myapplication.local.entities.LocalAspersionVariableStatEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import kotlin.math.sqrt

/**
 * Punto único de acceso al módulo de aspersión.
 *
 * Room es la fuente que observarán las pantallas. La API únicamente actualiza
 * ese caché mediante sincronizaciones completas y transaccionales.
 */
class AspersionRepository(
    context: Context,
    private val database: AppDatabase,
    private val api: AspersionApiService = RetrofitClient.crearServicioAutenticado(
        context = context.applicationContext,
        serviceClass = AspersionApiService::class.java
    ),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private companion object {
        const val SESSION_PAGE_SIZE = 100
        const val POINT_PAGE_SIZE = 2_000
        const val MAX_PAGES = 500
        const val MAX_ERROR_BODY_LENGTH = 500
        const val STATS_RETRY_ATTEMPTS = 3
        const val STATS_RETRY_DELAY_MS = 350L
    }

    private val sessionDao = database.localAspersionSessionDao()
    private val pointDao = database.localAspersionPointDao()
    private val statsDao = database.localAspersionStatsDao()
    private val variableStatsDao = database.localAspersionVariableStatDao()

    fun observeSessions(): Flow<List<LocalAspersionSessionEntity>> =
        sessionDao.observeAll()

    fun observeSession(sessionId: String): Flow<LocalAspersionSessionEntity?> =
        sessionDao.observeById(sessionId)

    fun observePoints(sessionId: String): Flow<List<LocalAspersionPointEntity>> =
        pointDao.observeBySession(sessionId)

    fun observeStats(sessionId: String): Flow<LocalAspersionStatsEntity?> =
        statsDao.observeBySession(sessionId)

    fun observeVariableStats(
        sessionId: String
    ): Flow<List<LocalAspersionVariableStatEntity>> =
        variableStatsDao.observeBySession(sessionId)

    suspend fun getFilteredLocalSessions(
        programId: String? = null,
        plotId: String? = null,
        importStatus: String? = null
    ): List<LocalAspersionSessionEntity> = withContext(Dispatchers.IO) {
        sessionDao.getFiltered(
            programId = programId,
            plotId = plotId,
            importStatus = importStatus
        )
    }

    suspend fun getLocalPointsByQualities(
        sessionId: String,
        qualities: List<String>
    ): List<LocalAspersionPointEntity> = withContext(Dispatchers.IO) {
        if (qualities.isEmpty()) {
            emptyList()
        } else {
            pointDao.getBySessionAndQualities(
                sessionId = sessionId,
                qualities = qualities
            )
        }
    }

    /**
     * Descarga todas las páginas del listado y conserva el estado offline que
     * ya tenga cada sesión.
     *
     * En una sincronización general también elimina sesiones que el backend ya
     * no devuelve. Así un cambio de asignación o permisos no deja mapas de otro
     * usuario disponibles offline. Los listados filtrados nunca eliminan datos.
     */
    suspend fun syncSessions(
        programId: String? = null,
        plotId: String? = null,
        dataCentralId: String? = null,
        producerId: String? = null,
        ranchId: String? = null,
        assignedToId: String? = null,
        status: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        importStatus: String? = null
    ): AspersionSyncResult = withContext(Dispatchers.IO) {
        try {
            val remoteSessions = downloadAllSessions(
                programId = programId,
                plotId = plotId,
                dataCentralId = dataCentralId,
                producerId = producerId,
                ranchId = ranchId,
                assignedToId = assignedToId,
                status = status,
                dateFrom = dateFrom,
                dateTo = dateTo,
                importStatus = importStatus
            )

            database.withTransaction {
                val existingSessions = sessionDao.getFiltered(
                    programId = null,
                    plotId = null,
                    importStatus = null
                )
                val existingById = existingSessions.associateBy { it.sessionId }
                val entities = remoteSessions.map { dto ->
                    dto.toLocalEntity(existing = existingById[dto.id.trim()])
                }
                if (entities.isNotEmpty()) {
                    sessionDao.upsertAll(entities)
                }

                val isScopeRefresh = programId == null &&
                        plotId == null &&
                        producerId == null &&
                        ranchId == null &&
                        status == null &&
                        dateFrom == null &&
                        dateTo == null &&
                        importStatus == null
                if (isScopeRefresh) {
                    val remoteIds = entities.map { it.sessionId }.toSet()
                    existingSessions
                        .asSequence()
                        .filter { existing ->
                            existing.belongsToAspersionSyncScope(
                                dataCentralId = dataCentralId,
                                assignedToId = assignedToId
                            )
                        }
                        .map { it.sessionId }
                        .filterNot(remoteIds::contains)
                        .forEach { staleId ->
                            sessionDao.deleteById(staleId)
                        }
                }
            }

            AspersionSyncResult.SessionsUpdated(remoteSessions.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AspersionSyncResult.Error(error.toUserMessage())
        }
    }

    /**
     * Sincroniza el detalle, todas las páginas de puntos y sus estadísticas.
     *
     * Ningún dato local se reemplaza hasta que:
     * - todas las respuestas fueron exitosas;
     * - cada punto tiene UUID y coordenadas válidas;
     * - no hay UUID duplicados;
     * - los conteos de sesión, puntos y estadísticas coinciden.
     */
    suspend fun syncCompleteSession(
        sessionId: String
    ): AspersionSyncResult = withContext(Dispatchers.IO) {
        val cleanSessionId = sessionId.trim()
        if (cleanSessionId.isEmpty()) {
            return@withContext AspersionSyncResult.Error(
                "Falta el UUID de la sesión de aspersión."
            )
        }

        try {
            val sessionDto = requireBody(
                response = api.obtenerSesion(cleanSessionId),
                resourceName = "El detalle de la sesión"
            )

            if (sessionDto.id.trim() != cleanSessionId) {
                throw AspersionSyncException(
                    "El servidor respondió el detalle de otra sesión."
                )
            }

            val remotePoints = downloadAllPoints(cleanSessionId)
            validateSessionPointCount(
                sessionDto = sessionDto,
                remotePointsCount = remotePoints.remoteCount
            )

            val cachedAt = nowMillis()
            val statsEntity: LocalAspersionStatsEntity?
            val variableEntities: List<LocalAspersionVariableStatEntity>

            if (remotePoints.remoteCount == 0) {
                /*
                 * /stats/ responde 404 mientras la sesión no tiene puntos.
                 * Una sesión vacía sí puede guardarse correctamente offline.
                 */
                statsEntity = null
                variableEntities = emptyList()
            } else {
                /*
                 * Las vistas de estadísticas pueden tardar unos segundos en
                 * refrescarse después de importar los puntos con Celery. Se
                 * reintenta el 404 y, si sigue pendiente o trae otro conteo,
                 * se calcula un resumen local sin perder la descarga válida.
                 */
                val statsDto = requestStatsWithRetry(
                    resourceName = "Las estadísticas de la sesión"
                ) {
                    api.obtenerEstadisticas(cleanSessionId)
                }
                val variableStatsDto = requestStatsWithRetry(
                    resourceName = "Las estadísticas de variables"
                ) {
                    api.obtenerEstadisticasVariables(cleanSessionId)
                }

                statsEntity = statsDto
                    ?.takeIf { dto ->
                        dto.pointsCount == null ||
                                dto.pointsCount == remotePoints.remoteCount
                    }
                    ?.toLocalEntity(
                        expectedSessionId = cleanSessionId,
                        cachedAt = cachedAt
                    )
                    ?: buildLocalAspersionStats(
                        sessionId = cleanSessionId,
                        plotId = sessionDto.plot,
                        points = remotePoints.points,
                        cachedAt = cachedAt
                    )

                variableEntities = variableStatsDto
                    ?.takeIf { dto ->
                        dto.pointsCount == remotePoints.remoteCount
                    }
                    ?.variables
                    ?.mapNotNull { variable ->
                        variable.toLocalEntityOrNull(
                            sessionId = cleanSessionId,
                            cachedAt = cachedAt
                        )
                    }
                    ?.associateBy(LocalAspersionVariableStatEntity::variableKey)
                    ?.values
                    ?.toList()
                    ?.takeIf(List<LocalAspersionVariableStatEntity>::isNotEmpty)
                    ?: buildLocalAspersionVariableStats(
                        sessionId = cleanSessionId,
                        points = remotePoints.points,
                        cachedAt = cachedAt
                    )
            }

            val existingSession = sessionDao.getById(cleanSessionId)
            val completeSession = sessionDto
                .toLocalEntity(existing = existingSession)
                .copy(
                    pointsCount = remotePoints.remoteCount,
                    downloadedPointsCount = remotePoints.points.size,
                    pointsDownloadComplete = true,
                    lastSyncedAt = cachedAt
                )

            database.withTransaction {
                /*
                 * Se inserta primero el encabezado porque puntos y estadísticas
                 * tienen una llave foránea hacia la sesión.
                 */
                sessionDao.upsert(completeSession)

                pointDao.deleteBySession(cleanSessionId)
                if (remotePoints.points.isNotEmpty()) {
                    pointDao.upsertAll(remotePoints.points)
                }

                statsDao.deleteBySession(cleanSessionId)
                if (statsEntity != null) {
                    statsDao.upsert(statsEntity)
                }

                variableStatsDao.deleteBySession(cleanSessionId)
                if (variableEntities.isNotEmpty()) {
                    variableStatsDao.upsertAll(variableEntities)
                }
            }

            AspersionSyncResult.SessionUpdated(
                sessionId = cleanSessionId,
                downloadedPoints = remotePoints.points.size,
                areaTotalHa = statsEntity?.areaTotalHa
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AspersionSyncResult.Error(error.toUserMessage())
        }
    }

    private suspend fun downloadAllSessions(
        programId: String?,
        plotId: String?,
        dataCentralId: String?,
        producerId: String?,
        ranchId: String?,
        assignedToId: String?,
        status: String?,
        dateFrom: String?,
        dateTo: String?,
        importStatus: String?
    ): List<AspersionSessionDto> {
        val sessionsById = LinkedHashMap<String, AspersionSessionDto>()
        var expectedCount: Int? = null
        var page = 1

        while (page <= MAX_PAGES) {
            val body = requireBody(
                response = api.listarSesiones(
                    program = programId,
                    plot = plotId,
                    dataCentral = dataCentralId,
                    producer = producerId,
                    ranch = ranchId,
                    assignedTo = assignedToId,
                    status = status,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    importStatus = importStatus,
                    page = page,
                    pageSize = SESSION_PAGE_SIZE
                ),
                resourceName = "La página $page de sesiones"
            )

            val totalCount = validateStableCount(
                currentExpected = expectedCount,
                received = body.count,
                resourceName = "sesiones"
            )
            expectedCount = totalCount

            body.results.forEach { session ->
                val id = session.id.trim()
                if (id.isEmpty()) {
                    throw AspersionSyncException(
                        "La página $page contiene una sesión sin UUID."
                    )
                }
                if (sessionsById.put(id, session) != null) {
                    throw AspersionSyncException(
                        "La sesión $id llegó duplicada durante la paginación."
                    )
                }
            }

            if (sessionsById.size == totalCount) {
                return sessionsById.values.toList()
            }
            if (sessionsById.size > totalCount) {
                throw AspersionSyncException(
                    "El servidor devolvió más sesiones que el total anunciado."
                )
            }
            if (body.results.isEmpty()) {
                throw AspersionSyncException(
                    "La paginación de sesiones terminó antes de descargar " +
                            "los $totalCount registros."
                )
            }

            page++
        }

        throw AspersionSyncException(
            "La descarga de sesiones excedió $MAX_PAGES páginas."
        )
    }

    private suspend fun downloadAllPoints(
        sessionId: String
    ): DownloadedAspersionPoints {
        val pointsById = LinkedHashMap<String, LocalAspersionPointEntity>()
        var expectedCount: Int? = null
        var page = 1

        while (page <= MAX_PAGES) {
            val body = requireBody(
                response = api.listarPuntos(
                    sessionHeader = sessionId,
                    page = page,
                    pageSize = POINT_PAGE_SIZE
                ),
                resourceName = "La página $page de puntos"
            )

            val totalCount = validateStableCount(
                currentExpected = expectedCount,
                received = body.count,
                resourceName = "puntos"
            )
            expectedCount = totalCount

            body.results.forEach { dto ->
                val point = dto.toLocalEntityOrNull(sessionId)
                    ?: throw AspersionSyncException(
                        "La página $page contiene un punto sin UUID, " +
                                "con coordenadas inválidas o de otra sesión."
                    )

                if (pointsById.put(point.pointId, point) != null) {
                    throw AspersionSyncException(
                        "El punto ${point.pointId} llegó duplicado durante la paginación."
                    )
                }
            }

            if (pointsById.size == totalCount) {
                return DownloadedAspersionPoints(
                    remoteCount = totalCount,
                    points = pointsById.values.toList()
                )
            }
            if (pointsById.size > totalCount) {
                throw AspersionSyncException(
                    "El servidor devolvió más puntos que el total anunciado."
                )
            }
            if (body.results.isEmpty()) {
                throw AspersionSyncException(
                    "La paginación de puntos terminó antes de descargar " +
                            "los $totalCount registros."
                )
            }

            page++
        }

        throw AspersionSyncException(
            "La descarga de puntos excedió $MAX_PAGES páginas."
        )
    }

    private fun validateSessionPointCount(
        sessionDto: AspersionSessionDto,
        remotePointsCount: Int
    ) {
        val sessionCount = sessionDto.pointsCount ?: return
        if (sessionCount != remotePointsCount) {
            throw AspersionSyncException(
                "El conteo cambió durante la descarga: la sesión anuncia " +
                        "$sessionCount puntos y el listado devuelve $remotePointsCount. " +
                        "Vuelve a sincronizar."
            )
        }
    }

    private suspend fun <T> requestStatsWithRetry(
        resourceName: String,
        request: suspend () -> Response<T>
    ): T? {
        repeat(STATS_RETRY_ATTEMPTS) { attempt ->
            val response = request()
            if (response.isSuccessful) {
                return response.body()
                    ?: throw AspersionSyncException(
                        "$resourceName respondió sin información."
                    )
            }

            if (response.code() != 404) {
                return requireBody(
                    response = response,
                    resourceName = resourceName
                )
            }

            if (attempt < STATS_RETRY_ATTEMPTS - 1) {
                delay(STATS_RETRY_DELAY_MS * (attempt + 1L))
            }
        }

        return null
    }

    private fun validateStableCount(
        currentExpected: Int?,
        received: Int,
        resourceName: String
    ): Int {
        if (received < 0) {
            throw AspersionSyncException(
                "El servidor devolvió un total inválido de $resourceName."
            )
        }
        if (currentExpected != null && currentExpected != received) {
            throw AspersionSyncException(
                "El total de $resourceName cambió durante la descarga. " +
                        "Vuelve a sincronizar."
            )
        }
        return received
    }

    private fun <T> requireBody(
        response: Response<T>,
        resourceName: String
    ): T {
        if (!response.isSuccessful) {
            val detail = runCatching {
                response.errorBody()
                    ?.string()
                    ?.trim()
                    ?.take(MAX_ERROR_BODY_LENGTH)
            }.getOrNull()

            throw AspersionSyncException(
                buildString {
                    append(resourceName)
                    append(" respondió HTTP ")
                    append(response.code())
                    if (!detail.isNullOrBlank()) {
                        append(": ")
                        append(detail)
                    }
                }
            )
        }

        return response.body()
            ?: throw AspersionSyncException(
                "$resourceName respondió sin información."
            )
    }

    private fun Exception.toUserMessage(): String = when (this) {
        is AspersionSyncException -> message.orEmpty()
        else -> "No se pudo sincronizar la aspersión: " +
                (message?.takeIf(String::isNotBlank) ?: javaClass.simpleName)
    }
}

internal fun LocalAspersionSessionEntity.belongsToAspersionSyncScope(
    dataCentralId: String?,
    assignedToId: String?
): Boolean {
    if (assignedToId != null) {
        return this.assignedToId == assignedToId
    }
    if (dataCentralId != null) {
        val storedCias = dataCentralIds
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        return storedCias.isEmpty() || dataCentralId in storedCias
    }
    return true
}

sealed class AspersionSyncResult {
    data class SessionsUpdated(
        val sessionsCount: Int
    ) : AspersionSyncResult()

    data class SessionUpdated(
        val sessionId: String,
        val downloadedPoints: Int,
        val areaTotalHa: Double?
    ) : AspersionSyncResult()

    data class Error(
        val message: String
    ) : AspersionSyncResult()
}

private data class DownloadedAspersionPoints(
    val remoteCount: Int,
    val points: List<LocalAspersionPointEntity>
)

private class AspersionSyncException(
    message: String
) : Exception(message)

internal fun buildLocalAspersionStats(
    sessionId: String,
    plotId: String?,
    points: List<LocalAspersionPointEntity>,
    cachedAt: Long
): LocalAspersionStatsEntity {
    val targetValues = points.mapNotNull { it.targetRateL.finiteOrNull() }
    val appliedValues = points.mapNotNull { it.appliedRateL.finiteOrNull() }
    val ratios = points.mapNotNull { point ->
        val target = point.targetRateL.finiteOrNull()
        val applied = point.appliedRateL.finiteOrNull()
        if (target == null || applied == null || target <= 0.0) {
            null
        } else {
            (applied / target) * 100.0
        }
    }

    val meanTarget = targetValues.averageOrNull()
    val meanApplied = appliedValues.averageOrNull()
    val ratioApplied = if (
        meanTarget != null && meanApplied != null && meanTarget > 0.0
    ) {
        meanApplied / meanTarget
    } else {
        null
    }

    fun percentage(predicate: (Double) -> Boolean): Double? {
        if (ratios.isEmpty()) return null
        return ratios.count(predicate) * 100.0 / ratios.size
    }

    return LocalAspersionStatsEntity(
        sessionId = sessionId,
        plotId = plotId?.trim()?.takeIf(String::isNotEmpty),
        pointsCount = points.size,
        areaTotalHa = points
            .mapNotNull { it.areaHa.finiteOrNull() }
            .sum()
            .takeIf { points.any { point -> point.areaHa.finiteOrNull() != null } },
        meanTargetL = meanTarget,
        meanAppliedL = meanApplied,
        ratioApplied = ratioApplied,
        pctBelow = percentage { it < 75.0 },
        pctInRange = percentage { it in 75.0..115.0 },
        pctAbove = percentage { it > 115.0 },
        lastRefresh = null,
        cachedAt = cachedAt
    )
}

internal fun buildLocalAspersionVariableStats(
    sessionId: String,
    points: List<LocalAspersionPointEntity>,
    cachedAt: Long
): List<LocalAspersionVariableStatEntity> {
    val variables = listOf(
        LocalVariableSource("speed_kmh", "Velocidad", LocalAspersionPointEntity::speedKmh),
        LocalVariableSource("target_rate_l", "Dosis objetivo", LocalAspersionPointEntity::targetRateL),
        LocalVariableSource("applied_rate_l", "Dosis aplicada", LocalAspersionPointEntity::appliedRateL),
        LocalVariableSource("boom_pressure_bar", "Presión", LocalAspersionPointEntity::boomPressureBar),
        LocalVariableSource("liquid_flow_ls", "Caudal", LocalAspersionPointEntity::liquidFlowLs),
        LocalVariableSource("production_hah", "Productividad", LocalAspersionPointEntity::productionHah)
    )

    return variables.mapNotNull { variable ->
        val values = points
            .mapNotNull { point -> variable.value(point).finiteOrNull() }
        if (values.isEmpty()) return@mapNotNull null

        val mean = values.average()
        val variance = values.sumOf { value ->
            val delta = value - mean
            delta * delta
        } / values.size

        LocalAspersionVariableStatEntity(
            sessionId = sessionId,
            variableKey = variable.key,
            label = variable.label,
            count = values.size,
            meanValue = mean,
            minValue = values.minOrNull(),
            maxValue = values.maxOrNull(),
            stddev = sqrt(variance),
            cachedAt = cachedAt
        )
    }
}

private data class LocalVariableSource(
    val key: String,
    val label: String,
    val value: (LocalAspersionPointEntity) -> Double?
)

private fun Double?.finiteOrNull(): Double? = this?.takeIf(Double::isFinite)

private fun List<Double>.averageOrNull(): Double? =
    takeIf(List<Double>::isNotEmpty)?.average()

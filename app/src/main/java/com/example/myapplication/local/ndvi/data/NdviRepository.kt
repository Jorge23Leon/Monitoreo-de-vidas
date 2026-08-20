package com.example.myapplication.local.ndvi.data

import android.content.Context
import androidx.room.withTransaction
import com.example.myapplication.local.api.core.RetrofitClient
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalNdviPointEntity
import com.example.myapplication.local.entities.LocalNdviSessionEntity
import com.example.myapplication.local.ndvi.data.remote.NdviApiService
import com.example.myapplication.local.ndvi.data.remote.NdviPointDto
import com.example.myapplication.local.ndvi.data.remote.NdviSessionDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response


/**
 * Repositorio principal del módulo NDVI.
 *
 * Responsabilidades:
 *
 * 1. Consultar Retrofit.
 * 2. Descargar sesiones.
 * 3. Descargar puntos paginados.
 * 4. Convertir DTO -> Entity mediante NdviMappers.
 * 5. Guardar los datos en Room.
 *
 * Las pantallas NO deberían hablar directamente
 * con Retrofit.
 *
 * La interfaz observará Room.
 */
class NdviRepository(

    context: Context,

    private val database: AppDatabase,

    private val api: NdviApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context.applicationContext,
            serviceClass = NdviApiService::class.java
        ),

    private val nowMillis: () -> Long =
        System::currentTimeMillis

) {


    private companion object {

        /**
         * Número de sesiones solicitadas
         * por página.
         */
        const val SESSION_PAGE_SIZE = 100

        /**
         * NDVI puede tener miles de puntos.
         *
         * El backend/web actual trabaja con
         * páginas grandes para este recurso.
         */
        const val POINT_PAGE_SIZE = 2_000

        /**
         * Protección para evitar un loop infinito
         * si el servidor devolviera una paginación
         * incorrecta.
         */
        const val MAX_PAGES = 500

        /**
         * No queremos meter respuestas enormes
         * del servidor dentro del mensaje de error.
         */
        const val MAX_ERROR_BODY_LENGTH = 500
    }


    // =========================================================
    // DAO
    // =========================================================

    private val sessionDao =
        database.localNdviSessionDao()

    private val pointDao =
        database.localNdviPointDao()


    // =========================================================
    // OBSERVAR ROOM
    // =========================================================

    /**
     * Room será la fuente de sesiones para la UI.
     *
     * Cuando una sincronización modifique Room,
     * este Flow emitirá nuevamente.
     */
    fun observeSessions():
            Flow<List<LocalNdviSessionEntity>> {

        return sessionDao.observeAllSessions()
    }


    /**
     * Observa los puntos pertenecientes
     * a una sesión NDVI.
     */
    fun observePoints(
        sessionId: String
    ): Flow<List<LocalNdviPointEntity>> {

        return pointDao.observePointsBySession(
            sessionId = sessionId
        )
    }


    // =========================================================
    // CONSULTAS LOCALES
    // =========================================================

    suspend fun getCachedSession(
        sessionId: String
    ): LocalNdviSessionEntity? {

        return withContext(Dispatchers.IO) {

            sessionDao.getSessionById(
                sessionId = sessionId.trim()
            )
        }
    }


    suspend fun getCachedPoints(
        sessionId: String
    ): List<LocalNdviPointEntity> {

        return withContext(Dispatchers.IO) {

            pointDao.getPointsBySession(
                sessionId = sessionId.trim()
            )
        }
    }


    // =========================================================
    // CONFIGURACIÓN VISUAL DE VARIABLES NDVI
    // =========================================================

    /**
     * Obtiene la configuración de colores/bandas aplicable
     * a una sesión NDVI dentro de la CIA activa.
     *
     * La respuesta se conserva como JSON porque el WebView
     * interpreta directamente las estrategias manual/quartile.
     */
    suspend fun getVariableConfig(
        sessionId: String,
        tenantId: String? = null,
        dataCentralId: String? = null
    ): NdviVariableConfigResult {

        return withContext(Dispatchers.IO) {

            try {

                val cleanSessionId =
                    sessionId
                        .trim()
                        .takeIf(String::isNotEmpty)
                        ?: throw NdviSyncException(
                            "La sesión NDVI no es válida."
                        )

                val body =
                    requireBody(
                        response =
                        api.obtenerConfiguracionVariables(
                            sessionId = cleanSessionId,
                            tenantId = tenantId.cleanNullable(),
                            dataCentralId = dataCentralId.cleanNullable()
                        ),
                        resourceName =
                        "La configuración visual NDVI"
                    )

                val json =
                    body
                        .toString()
                        .trim()

                if (
                    json.isEmpty() ||
                    json == "null"
                ) {

                    throw NdviSyncException(
                        "La configuración visual NDVI llegó vacía."
                    )
                }

                NdviVariableConfigResult.Success(
                    json = json
                )

            } catch (cancelled: CancellationException) {

                throw cancelled

            } catch (exception: Exception) {

                NdviVariableConfigResult.Error(
                    message = exception.toUserMessage()
                )
            }
        }
    }


    // =========================================================
    // SINCRONIZAR LISTA DE SESIONES
    // =========================================================

    /**
     * Descarga las sesiones NDVI disponibles.
     *
     * programId y plotId son opcionales.
     *
     * Importante:
     *
     * NO eliminamos aquí sesiones antiguas de Room.
     * Solamente insertamos/actualizamos las que
     * devuelve el backend.
     *
     * Así no destruimos caché offline por un filtro.
     */
    suspend fun syncSessions(
        programId: String? = null,
        plotId: String? = null
    ): NdviSyncResult {

        return withContext(Dispatchers.IO) {

            try {

                val remoteSessions =
                    downloadAllSessions(
                        programId = programId,
                        plotId = plotId
                    )

                val syncedAt = nowMillis()

                database.withTransaction {

                    remoteSessions.forEach { dto ->

                        upsertSession(
                            dto = dto,
                            syncedAt = syncedAt
                        )
                    }
                }

                NdviSyncResult.SessionsUpdated(
                    sessionsCount = remoteSessions.size
                )

            } catch (cancelled: CancellationException) {

                throw cancelled

            } catch (exception: Exception) {

                NdviSyncResult.Error(
                    message = exception.toUserMessage()
                )
            }
        }
    }


    // =========================================================
    // SINCRONIZAR UNA SESIÓN COMPLETA
    // =========================================================

    /**
     * Sincroniza:
     *
     * 1. Header de la sesión.
     * 2. Todos sus puntos.
     *
     * Solo cuando TODA la descarga termina
     * correctamente sustituimos el caché viejo.
     *
     * Esto evita:
     *
     * 3500 puntos guardados
     *       ↓
     * descarga falla en página 2
     *       ↓
     * mapa queda con 2000 puntos ❌
     */
    suspend fun syncSession(
        sessionId: String
    ): NdviSyncResult {

        return withContext(Dispatchers.IO) {

            try {

                val cleanSessionId =
                    sessionId
                        .trim()
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?: throw NdviSyncException(
                            "La sesión NDVI no es válida."
                        )


                // =============================================
                // 1. DESCARGAR HEADER
                // =============================================

                val sessionDto =
                    requireBody(
                        response = api.obtenerSesion(
                            id = cleanSessionId
                        ),
                        resourceName = "La sesión NDVI"
                    )


                // =============================================
                // 2. DESCARGAR TODOS LOS PUNTOS
                // =============================================

                val downloaded =
                    downloadAllPoints(
                        sessionId = cleanSessionId
                    )


                // =============================================
                // 3. DTO -> ROOM ENTITY
                // =============================================

                val localPoints =
                    downloaded.points
                        .toLocalEntities(
                            expectedSessionId =
                            cleanSessionId
                        )


                /**
                 * Esta validación es importante.
                 *
                 * Si backend dice 3500 puntos pero
                 * después del mapper solamente tenemos
                 * 3498, hay dos puntos inválidos.
                 *
                 * No marcamos esa descarga como completa.
                 */
                if (
                    localPoints.size !=
                    downloaded.remoteCount
                ) {

                    throw NdviSyncException(
                        "La sesión NDVI reporta " +
                                "${downloaded.remoteCount} puntos, " +
                                "pero solamente " +
                                "${localPoints.size} fueron válidos."
                    )
                }


                val syncedAt = nowMillis()


                // =============================================
                // 4. GUARDAR TODO EN UNA TRANSACCIÓN
                // =============================================

                database.withTransaction {

                    /**
                     * Primero dejamos garantizado
                     * que el padre exista.
                     */
                    upsertSession(
                        dto = sessionDto,
                        syncedAt = syncedAt
                    )


                    /**
                     * IMPORTANTE:
                     *
                     * Los puntos viejos solamente se borran
                     * después de haber descargado y validado
                     * completamente los nuevos.
                     */
                    pointDao.deletePointsBySession(
                        sessionId = cleanSessionId
                    )


                    if (localPoints.isNotEmpty()) {

                        pointDao.insertPoints(
                            points = localPoints
                        )
                    }


                    val realLocalCount =
                        pointDao.countPointsBySession(
                            sessionId = cleanSessionId
                        )


                    if (
                        realLocalCount !=
                        downloaded.remoteCount
                    ) {

                        /**
                         * Al lanzar excepción dentro de
                         * withTransaction, Room hace ROLLBACK.
                         *
                         * Por lo tanto no perdemos
                         * el caché anterior.
                         */
                        throw NdviSyncException(
                            "Room guardó $realLocalCount " +
                                    "de ${downloaded.remoteCount} " +
                                    "puntos NDVI."
                        )
                    }


                    sessionDao.updatePointsDownloadState(
                        sessionId = cleanSessionId,
                        downloadedPointsCount =
                        realLocalCount,
                        downloadComplete = true,
                        lastSyncedAt = syncedAt
                    )
                }


                NdviSyncResult.SessionUpdated(
                    sessionId = cleanSessionId,
                    downloadedPoints =
                    downloaded.remoteCount
                )


            } catch (cancelled: CancellationException) {

                throw cancelled

            } catch (exception: Exception) {

                NdviSyncResult.Error(
                    message = exception.toUserMessage()
                )
            }
        }
    }



    // =========================================================
    // CONTORNOS - ÍNDICES DISPONIBLES
    // =========================================================

    /**
     * Consulta qué índices tienen contornos disponibles
     * para una sesión NDVI.
     */
    suspend fun getContourIndices(
        sessionId: String
    ): NdviContourIndicesResult {

        return withContext(Dispatchers.IO) {

            try {

                val cleanSessionId =
                    sessionId
                        .trim()
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?: throw NdviSyncException(
                            "La sesión NDVI no es válida."
                        )


                val body =
                    requireBody(
                        response =
                        api.obtenerIndicesContorno(
                            sessionId = cleanSessionId
                        ),
                        resourceName =
                        "Los índices de contorno NDVI"
                    )


                // -------------------------------------------------
                // VALIDAR QUE LA RESPUESTA SEA DE ESTA SESIÓN
                // -------------------------------------------------

                val responseHeaderId =
                    body.headerId.trim()


                if (
                    responseHeaderId.isNotEmpty() &&
                    responseHeaderId != cleanSessionId
                ) {

                    throw NdviSyncException(
                        "Los contornos recibidos no pertenecen " +
                                "a la sesión NDVI seleccionada."
                    )
                }


                // -------------------------------------------------
                // NORMALIZAR ÍNDICES
                // -------------------------------------------------

                val indices =
                    body.indices
                        .mapNotNull { value ->

                            value
                                .cleanNullable()
                                ?.lowercase()
                        }
                        .distinct()


                // -------------------------------------------------
                // GUARDAR ESTADO DEL BACKEND
                // -------------------------------------------------

                body.contourStatus
                    .cleanNullable()
                    ?.let { status ->

                        sessionDao.updateContourStatus(
                            sessionId =
                            cleanSessionId,
                            contourStatus =
                            status
                        )
                    }


                NdviContourIndicesResult.Success(
                    sessionId =
                    cleanSessionId,
                    contourStatus =
                    body.contourStatus
                        .cleanNullable(),
                    indices =
                    indices
                )


            } catch (
                cancelled: CancellationException
            ) {

                throw cancelled


            } catch (
                exception: Exception
            ) {

                NdviContourIndicesResult.Error(
                    message =
                    exception.toUserMessage()
                )
            }
        }
    }


    // =========================================================
    // CONTORNOS - GEOJSON
    // =========================================================

    /**
     * Descarga el GeoJSON correspondiente a un índice.
     *
     * Posibles resultados:
     *
     * 200 -> contorno disponible
     * 202 -> backend todavía lo está generando
     * error -> no se destruye el mapa actual
     */
    suspend fun getContour(
        sessionId: String,
        indexKey: String,
        dataCentralId: String? = null
    ): NdviContourResult {

        return withContext(Dispatchers.IO) {

            try {

                // -------------------------------------------------
                // VALIDAR SESIÓN
                // -------------------------------------------------

                val cleanSessionId =
                    sessionId
                        .trim()
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?: throw NdviSyncException(
                            "La sesión NDVI no es válida."
                        )


                // -------------------------------------------------
                // VALIDAR ÍNDICE
                // -------------------------------------------------

                val cleanIndexKey =
                    indexKey
                        .trim()
                        .lowercase()
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?: throw NdviSyncException(
                            "El índice NDVI no es válido."
                        )


                // -------------------------------------------------
                // CONSULTAR BACKEND
                // -------------------------------------------------

                val response =
                    api.obtenerContornos(
                        sessionId =
                        cleanSessionId,
                        index =
                        cleanIndexKey,
                        dataCentralId =
                        dataCentralId
                            .cleanNullable()
                    )


                // -------------------------------------------------
                // HTTP 202 = TODAVÍA GENERANDO
                // -------------------------------------------------

                if (
                    response.code() == 202
                ) {

                    sessionDao.updateContourStatus(
                        sessionId =
                        cleanSessionId,
                        contourStatus =
                        "processing"
                    )


                    return@withContext NdviContourResult.Generating(
                        sessionId = cleanSessionId,
                        indexKey = cleanIndexKey
                    )
                }


                // -------------------------------------------------
                // HTTP 200 = GEOJSON
                // -------------------------------------------------

                val body =
                    requireBody(
                        response = response,
                        resourceName =
                        "El contorno $cleanIndexKey"
                    )


                val geoJson =
                    body
                        .toString()
                        .trim()


                if (
                    geoJson.isBlank() ||
                    geoJson == "null"
                ) {

                    throw NdviSyncException(
                        "El backend devolvió un contorno vacío."
                    )
                }


                NdviContourResult.Ready(
                    sessionId =
                    cleanSessionId,
                    indexKey =
                    cleanIndexKey,
                    geoJson =
                    geoJson
                )


            } catch (
                cancelled: CancellationException
            ) {

                throw cancelled


            } catch (
                exception: Exception
            ) {

                NdviContourResult.Error(
                    message =
                    exception.toUserMessage()
                )
            }
        }
    }


    // =========================================================
    // DESCARGAR SESIONES PAGINADAS
    // =========================================================

    private suspend fun downloadAllSessions(
        programId: String?,
        plotId: String?
    ): List<NdviSessionDto> {

        val result =
            mutableListOf<NdviSessionDto>()

        var page = 1

        var expectedCount: Int? = null


        while (page <= MAX_PAGES) {

            val body =
                requireBody(
                    response = api.listarSesiones(
                        program =
                        programId.cleanNullable(),

                        plot =
                        plotId.cleanNullable(),

                        page = page,

                        pageSize =
                        SESSION_PAGE_SIZE
                    ),
                    resourceName =
                    "Las sesiones NDVI"
                )


            expectedCount =
                validateStableCount(
                    currentExpected =
                    expectedCount,

                    received =
                    body.count,

                    resourceName =
                    "sesiones NDVI"
                )


            result.addAll(
                body.results
            )


            /**
             * Django manda next = null
             * cuando llegamos a la última página.
             */
            if (body.next == null) {
                break
            }


            page++
        }


        if (page > MAX_PAGES) {

            throw NdviSyncException(
                "La paginación de sesiones NDVI " +
                        "superó el límite permitido."
            )
        }


        val serverCount =
            expectedCount ?: 0


        if (result.size != serverCount) {

            throw NdviSyncException(
                "El servidor reportó $serverCount " +
                        "sesiones NDVI, pero se descargaron " +
                        "${result.size}."
            )
        }


        /**
         * Eliminamos duplicados defensivamente
         * utilizando el UUID de la sesión.
         */
        val uniqueSessions =
            result.distinctBy {
                it.id.trim()
            }


        if (
            uniqueSessions.size !=
            result.size
        ) {

            throw NdviSyncException(
                "El servidor devolvió sesiones NDVI duplicadas."
            )
        }


        return uniqueSessions
    }


    // =========================================================
    // DESCARGAR PUNTOS PAGINADOS
    // =========================================================

    private suspend fun downloadAllPoints(
        sessionId: String
    ): DownloadedNdviPoints {

        val result =
            mutableListOf<NdviPointDto>()

        var page = 1

        var expectedCount: Int? = null


        while (page <= MAX_PAGES) {

            val body =
                requireBody(
                    response =
                    api.listarPuntos(
                        sessionHeader =
                        sessionId,

                        page = page,

                        pageSize =
                        POINT_PAGE_SIZE
                    ),
                    resourceName =
                    "Los puntos NDVI"
                )


            expectedCount =
                validateStableCount(
                    currentExpected =
                    expectedCount,

                    received =
                    body.count,

                    resourceName =
                    "puntos NDVI"
                )


            result.addAll(
                body.results
            )


            if (body.next == null) {
                break
            }


            page++
        }


        if (page > MAX_PAGES) {

            throw NdviSyncException(
                "La paginación de puntos NDVI " +
                        "superó el límite permitido."
            )
        }


        val serverCount =
            expectedCount ?: 0


        if (result.size != serverCount) {

            throw NdviSyncException(
                "El servidor reportó $serverCount " +
                        "puntos NDVI, pero se descargaron " +
                        "${result.size}."
            )
        }


        /**
         * Un UUID de punto no debe repetirse.
         */
        val uniquePoints =
            result.distinctBy {
                it.id.trim()
            }


        if (
            uniquePoints.size !=
            result.size
        ) {

            throw NdviSyncException(
                "El servidor devolvió puntos NDVI duplicados."
            )
        }


        return DownloadedNdviPoints(
            remoteCount = serverCount,
            points = uniquePoints
        )
    }


    // =========================================================
    // INSERTAR / ACTUALIZAR SESIÓN
    // =========================================================

    /**
     * No utilizamos REPLACE.
     *
     * Primero intentamos insertar.
     *
     * Si ya existe:
     *
     * INSERT devuelve -1
     *       ↓
     * hacemos UPDATE
     *
     * Esto permite conservar correctamente
     * la relación padre -> puntos.
     */
    private suspend fun upsertSession(
        dto: NdviSessionDto,
        syncedAt: Long
    ) {

        val cleanId =
            dto.id
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }
                ?: throw NdviSyncException(
                    "El servidor devolvió una sesión NDVI sin ID."
                )


        val existing =
            sessionDao.getSessionById(
                sessionId = cleanId
            )


        val entity =
            dto.toLocalEntity(
                existing = existing,
                syncedAt = syncedAt
            )


        val insertedId =
            sessionDao.insertSession(
                session = entity
            )


        /**
         * Room devuelve -1 cuando IGNORE
         * encuentra una PK que ya existe.
         */
        if (insertedId == -1L) {

            val updatedRows =
                sessionDao.updateSession(
                    session = entity
                )


            if (updatedRows != 1) {

                throw NdviSyncException(
                    "No se pudo actualizar la sesión NDVI $cleanId."
                )
            }
        }
    }


    // =========================================================
    // VALIDAR PAGINACIÓN
    // =========================================================

    private fun validateStableCount(
        currentExpected: Int?,
        received: Int,
        resourceName: String
    ): Int {

        if (received < 0) {

            throw NdviSyncException(
                "El servidor devolvió un total inválido " +
                        "de $resourceName."
            )
        }


        if (
            currentExpected != null &&
            currentExpected != received
        ) {

            throw NdviSyncException(
                "El total de $resourceName cambió " +
                        "durante la descarga. " +
                        "Vuelve a sincronizar."
            )
        }


        return received
    }


    // =========================================================
    // VALIDAR RESPUESTA HTTP
    // =========================================================

    private fun <T> requireBody(
        response: Response<T>,
        resourceName: String
    ): T {

        if (!response.isSuccessful) {

            val detail =
                runCatching {

                    response
                        .errorBody()
                        ?.string()
                        ?.trim()
                        ?.take(
                            MAX_ERROR_BODY_LENGTH
                        )

                }.getOrNull()


            throw NdviSyncException(

                buildString {

                    append(
                        resourceName
                    )

                    append(
                        " respondió HTTP "
                    )

                    append(
                        response.code()
                    )


                    if (
                        !detail.isNullOrBlank()
                    ) {

                        append(": ")

                        append(detail)
                    }
                }
            )
        }


        return response.body()
            ?: throw NdviSyncException(
                "$resourceName respondió sin información."
            )
    }


    // =========================================================
    // MENSAJE PARA UI
    // =========================================================

    private fun Exception.toUserMessage():
            String {

        return when (this) {

            is NdviSyncException ->
                message.orEmpty()

            else ->
                "No se pudo sincronizar NDVI: " +
                        (
                                message
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: javaClass.simpleName
                                )
        }
    }
}


// =============================================================
// RESULTADOS DE SINCRONIZACIÓN
// =============================================================

sealed class NdviSyncResult {

    /**
     * Se actualizó la lista de sesiones.
     */
    data class SessionsUpdated(
        val sessionsCount: Int
    ) : NdviSyncResult()


    /**
     * Se descargó completamente una sesión
     * junto con sus puntos.
     */
    data class SessionUpdated(
        val sessionId: String,
        val downloadedPoints: Int
    ) : NdviSyncResult()


    /**
     * Ocurrió algún problema.
     */
    data class Error(
        val message: String
    ) : NdviSyncResult()
}



// =============================================================
// RESULTADO - ÍNDICES DE CONTORNO
// =============================================================

sealed class NdviContourIndicesResult {


    data class Success(
        val sessionId: String,
        val contourStatus: String?,
        val indices: List<String>
    ) : NdviContourIndicesResult()


    data class Error(
        val message: String
    ) : NdviContourIndicesResult()
}


// =============================================================
// RESULTADO - CONTORNO GEOJSON
// =============================================================

sealed class NdviContourResult {


    /**
     * El GeoJSON ya está disponible.
     */
    data class Ready(
        val sessionId: String,
        val indexKey: String,
        val geoJson: String
    ) : NdviContourResult()


    /**
     * El backend respondió HTTP 202.
     * Todavía está generando el contorno.
     */
    data class Generating(
        val sessionId: String,
        val indexKey: String
    ) : NdviContourResult()


    /**
     * No se pudo obtener el contorno.
     */
    data class Error(
        val message: String
    ) : NdviContourResult()
}


// =============================================================
// RESULTADO - CONFIGURACIÓN VISUAL NDVI
// =============================================================

sealed class NdviVariableConfigResult {

    data class Success(
        val json: String
    ) : NdviVariableConfigResult()

    data class Error(
        val message: String
    ) : NdviVariableConfigResult()
}


// =============================================================
// MODELOS INTERNOS
// =============================================================

private data class DownloadedNdviPoints(

    val remoteCount: Int,

    val points: List<NdviPointDto>
)


private class NdviSyncException(
    message: String
) : Exception(message)


// =============================================================
// HELPERS
// =============================================================

private fun String?.cleanNullable():
        String? {

    return this
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }
}
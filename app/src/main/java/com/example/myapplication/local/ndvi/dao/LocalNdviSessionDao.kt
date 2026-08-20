package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalNdviSessionEntity
import kotlinx.coroutines.flow.Flow


/**
 * Acceso local a las sesiones NDVI.
 *
 * Este DAO solamente trabaja contra Room.
 * No sabe nada de Retrofit ni del backend.
 */
@Dao
interface LocalNdviSessionDao {


    // =====================================================
    // OBSERVAR
    // =====================================================

    /**
     * Flow permite que la interfaz se actualice
     * automáticamente cuando Room cambie.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_sessions
        ORDER BY session_date DESC, created_at DESC
        """
    )
    fun observeAllSessions(): Flow<List<LocalNdviSessionEntity>>


    // =====================================================
    // CONSULTAR
    // =====================================================

    /**
     * Obtiene todas las sesiones una sola vez.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_sessions
        ORDER BY session_date DESC, created_at DESC
        """
    )
    suspend fun getAllSessions(): List<LocalNdviSessionEntity>


    /**
     * Busca una sesión por el UUID del backend.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_sessions
        WHERE session_id = :sessionId
        LIMIT 1
        """
    )
    suspend fun getSessionById(
        sessionId: String
    ): LocalNdviSessionEntity?


    /**
     * Obtiene las sesiones de un programa.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_sessions
        WHERE program_id = :programId
        ORDER BY session_date DESC
        """
    )
    suspend fun getSessionsByProgram(
        programId: String
    ): List<LocalNdviSessionEntity>


    /**
     * Obtiene las sesiones pertenecientes
     * a una parcela.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_sessions
        WHERE plot_id = :plotId
        ORDER BY session_date DESC
        """
    )
    suspend fun getSessionsByPlot(
        plotId: String
    ): List<LocalNdviSessionEntity>


    // =====================================================
    // GUARDAR
    // =====================================================

    /**
     * Intenta insertar una sesión.
     *
     * IGNORE es intencional.
     *
     * Si ya existe el mismo session_id,
     * NO elimina la fila anterior.
     *
     * Esto es importante porque los puntos NDVI
     * dependen de esta sesión mediante CASCADE.
     */
    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertSession(
        session: LocalNdviSessionEntity
    ): Long


    /**
     * Inserción masiva.
     *
     * Devuelve:
     *
     * ID válido  -> la fila fue insertada.
     * -1         -> la fila ya existía.
     */
    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertSessions(
        sessions: List<LocalNdviSessionEntity>
    ): List<Long>


    /**
     * Actualiza una sesión que ya existe.
     */
    @Update
    suspend fun updateSession(
        session: LocalNdviSessionEntity
    ): Int


    /**
     * Actualización masiva.
     */
    @Update
    suspend fun updateSessions(
        sessions: List<LocalNdviSessionEntity>
    ): Int


    // =====================================================
    // ESTADO DE DESCARGA OFFLINE
    // =====================================================

    /**
     * Actualiza únicamente la información local
     * relacionada con la descarga de puntos.
     */
    @Query(
        """
        UPDATE local_ndvi_sessions
        SET
            downloaded_points_count = :downloadedPointsCount,
            points_download_complete = :downloadComplete,
            last_synced_at = :lastSyncedAt
        WHERE session_id = :sessionId
        """
    )
    suspend fun updatePointsDownloadState(
        sessionId: String,
        downloadedPointsCount: Int,
        downloadComplete: Boolean,
        lastSyncedAt: Long
    ): Int


    /**
     * Permite actualizar el estado del contorno
     * sin reemplazar toda la sesión.
     */
    @Query(
        """
        UPDATE local_ndvi_sessions
        SET contour_status = :contourStatus
        WHERE session_id = :sessionId
        """
    )
    suspend fun updateContourStatus(
        sessionId: String,
        contourStatus: String?
    ): Int


    // =====================================================
    // ELIMINAR
    // =====================================================

    /**
     * Al eliminar una sesión,
     * sus puntos desaparecerán por CASCADE.
     */
    @Query(
        """
        DELETE FROM local_ndvi_sessions
        WHERE session_id = :sessionId
        """
    )
    suspend fun deleteSessionById(
        sessionId: String
    ): Int


    @Query(
        """
        DELETE FROM local_ndvi_sessions
        """
    )
    suspend fun deleteAllSessions(): Int
}
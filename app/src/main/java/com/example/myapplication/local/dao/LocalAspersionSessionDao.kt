package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAspersionSessionDao {

    @Upsert
    suspend fun upsert(session: LocalAspersionSessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<LocalAspersionSessionEntity>)

    @Query(
        """
        SELECT *
        FROM local_aspersion_sessions
        ORDER BY COALESCE(aspersion_date, est_start_date, created_at) DESC
        """
    )
    fun observeAll(): Flow<List<LocalAspersionSessionEntity>>

    @Query(
        """
        SELECT *
        FROM local_aspersion_sessions
        WHERE session_id = :sessionId
        LIMIT 1
        """
    )
    fun observeById(sessionId: String): Flow<LocalAspersionSessionEntity?>

    @Query(
        """
        SELECT *
        FROM local_aspersion_sessions
        WHERE session_id = :sessionId
        LIMIT 1
        """
    )
    suspend fun getById(sessionId: String): LocalAspersionSessionEntity?

    @Query(
        """
        SELECT *
        FROM local_aspersion_sessions
        WHERE (:programId IS NULL OR program_id = :programId)
          AND (:plotId IS NULL OR plot_id = :plotId)
          AND (:importStatus IS NULL OR import_status = :importStatus)
        ORDER BY COALESCE(aspersion_date, est_start_date, created_at) DESC
        """
    )
    suspend fun getFiltered(
        programId: String?,
        plotId: String?,
        importStatus: String?
    ): List<LocalAspersionSessionEntity>

    @Query(
        """
        UPDATE local_aspersion_sessions
        SET downloaded_points_count = :downloadedPointsCount,
            points_count = COALESCE(:remotePointsCount, points_count),
            points_download_complete = :complete,
            last_synced_at = :syncedAt
        WHERE session_id = :sessionId
        """
    )
    suspend fun updatePointsDownloadState(
        sessionId: String,
        downloadedPointsCount: Int,
        remotePointsCount: Int?,
        complete: Boolean,
        syncedAt: Long
    )

    @Query("DELETE FROM local_aspersion_sessions WHERE session_id = :sessionId")
    suspend fun deleteById(sessionId: String)
}

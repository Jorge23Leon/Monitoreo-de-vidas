package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.myapplication.local.entities.LocalAspersionPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAspersionPointDao {

    @Upsert
    suspend fun upsertAll(points: List<LocalAspersionPointEntity>)

    @Query(
        """
        SELECT *
        FROM local_aspersion_points
        WHERE session_id = :sessionId
        ORDER BY COALESCE(timestamp, created_at), point_id
        """
    )
    fun observeBySession(sessionId: String): Flow<List<LocalAspersionPointEntity>>

    @Query(
        """
        SELECT *
        FROM local_aspersion_points
        WHERE session_id = :sessionId
        ORDER BY COALESCE(timestamp, created_at), point_id
        """
    )
    suspend fun getBySession(sessionId: String): List<LocalAspersionPointEntity>

    @Query(
        """
        SELECT *
        FROM local_aspersion_points
        WHERE session_id = :sessionId
          AND rate_quality IN (:qualities)
        ORDER BY COALESCE(timestamp, created_at), point_id
        """
    )
    suspend fun getBySessionAndQualities(
        sessionId: String,
        qualities: List<String>
    ): List<LocalAspersionPointEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM local_aspersion_points
        WHERE session_id = :sessionId
        """
    )
    suspend fun countBySession(sessionId: String): Int

    @Query("DELETE FROM local_aspersion_points WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

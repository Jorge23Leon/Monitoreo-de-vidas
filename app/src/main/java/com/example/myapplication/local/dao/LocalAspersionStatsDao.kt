package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.myapplication.local.entities.LocalAspersionStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAspersionStatsDao {

    @Upsert
    suspend fun upsert(stats: LocalAspersionStatsEntity)

    @Query(
        """
        SELECT *
        FROM local_aspersion_stats
        WHERE session_id = :sessionId
        LIMIT 1
        """
    )
    fun observeBySession(sessionId: String): Flow<LocalAspersionStatsEntity?>

    @Query("DELETE FROM local_aspersion_stats WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

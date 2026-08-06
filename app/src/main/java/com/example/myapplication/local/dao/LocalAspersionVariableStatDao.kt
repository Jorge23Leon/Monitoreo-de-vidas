package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.myapplication.local.entities.LocalAspersionVariableStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAspersionVariableStatDao {

    @Upsert
    suspend fun upsertAll(stats: List<LocalAspersionVariableStatEntity>)

    @Query(
        """
        SELECT *
        FROM local_aspersion_variable_stats
        WHERE session_id = :sessionId
        ORDER BY variable_key
        """
    )
    fun observeBySession(
        sessionId: String
    ): Flow<List<LocalAspersionVariableStatEntity>>

    @Query(
        """
        SELECT *
        FROM local_aspersion_variable_stats
        WHERE session_id = :sessionId
          AND variable_key = :variableKey
        LIMIT 1
        """
    )
    suspend fun getBySessionAndKey(
        sessionId: String,
        variableKey: String
    ): LocalAspersionVariableStatEntity?

    @Query(
        "DELETE FROM local_aspersion_variable_stats WHERE session_id = :sessionId"
    )
    suspend fun deleteBySession(sessionId: String)
}

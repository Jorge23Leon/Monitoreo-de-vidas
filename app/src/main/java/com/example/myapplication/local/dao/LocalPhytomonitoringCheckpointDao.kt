package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity

@Dao
interface LocalPhytomonitoringCheckpointDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheckpoint(
        checkpoint: LocalPhytomonitoringCheckpointEntity
    ): Long

    @Query("""
        SELECT *
        FROM local_phytomonitoring_checkpoints
        WHERE idCheckpoint = :idCheckpoint
        LIMIT 1
    """)
    suspend fun getCheckpointById(
        idCheckpoint: Long
    ): LocalPhytomonitoringCheckpointEntity?

    @Query("""
        SELECT *
        FROM local_phytomonitoring_checkpoints
        WHERE idTargetPoint = :idTargetPoint
        ORDER BY captured_at DESC
    """)
    suspend fun getCheckpointsByTargetPoint(
        idTargetPoint: Long
    ): List<LocalPhytomonitoringCheckpointEntity>

    @Query("""
        SELECT *
        FROM local_phytomonitoring_checkpoints
        WHERE idHeader = :idHeader
        ORDER BY captured_at DESC
    """)
    suspend fun getCheckpointsByHeader(
        idHeader: Long
    ): List<LocalPhytomonitoringCheckpointEntity>

    @Query("""
        SELECT *
        FROM local_phytomonitoring_checkpoints
        WHERE idHeader = :idHeader
        AND idTargetPoint = :idTargetPoint
        ORDER BY captured_at DESC
    """)
    suspend fun getCheckpointsByHeaderAndTargetPoint(
        idHeader: Long,
        idTargetPoint: Long
    ): List<LocalPhytomonitoringCheckpointEntity>

    @Query("""
        SELECT COUNT(*)
        FROM local_phytomonitoring_checkpoints
        WHERE idTargetPoint = :idTargetPoint
    """)
    suspend fun countCheckpointsByTargetPoint(
        idTargetPoint: Long
    ): Int

    @Delete
    suspend fun deleteCheckpoint(
        checkpoint: LocalPhytomonitoringCheckpointEntity
    )
}
package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity

@Dao
interface LocalPhytomonitoringCheckpointDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheckpoint(
        checkpoint: LocalPhytomonitoringCheckpointEntity
    ): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpointFromApi(
        checkpoint: LocalPhytomonitoringCheckpointEntity
    ): Long

    @Update
    suspend fun updateCheckpoint(
        checkpoint: LocalPhytomonitoringCheckpointEntity
    )

    @Query("""
        SELECT *
        FROM local_phytomonitoring_checkpoints
        WHERE idHeader = :idHeader
          AND idTargetPoint = :idTargetPoint
          AND (
                (:idPhytosanitary IS NULL AND idPhytosanitary IS NULL)
                OR idPhytosanitary = :idPhytosanitary
              )
          AND COALESCE(stage, '') = COALESCE(:stage, '')
          AND COALESCE(qty, -9999) = COALESCE(:qty, -9999)
          AND COALESCE(captured_at, 0) = COALESCE(:capturedAt, 0)
        LIMIT 1
    """)
    suspend fun buscarCheckpointLocalMismaCaptura(
        idHeader: Long,
        idTargetPoint: Long,
        idPhytosanitary: Long?,
        stage: String?,
        qty: Int?,
        capturedAt: Long?
    ): LocalPhytomonitoringCheckpointEntity?

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
        WHERE ext_id = :extId
        LIMIT 1
    """)
    suspend fun getCheckpointByExtId(
        extId: String
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

    @Query("""
        SELECT *
        FROM local_phytomonitoring_checkpoints
        WHERE captured_by_user_id = :idUser
        ORDER BY captured_at DESC
    """)
    suspend fun getCheckpointsByUser(
        idUser: Long
    ): List<LocalPhytomonitoringCheckpointEntity>

    @Query("""
        SELECT COUNT(*)
        FROM local_phytomonitoring_checkpoints
        WHERE idHeader = :idHeader
          AND captured_by_user_id = :idUser
    """)
    suspend fun countCheckpointsByHeaderAndUser(
        idHeader: Long,
        idUser: Long
    ): Int

    @Delete
    suspend fun deleteCheckpoint(
        checkpoint: LocalPhytomonitoringCheckpointEntity
    )
}
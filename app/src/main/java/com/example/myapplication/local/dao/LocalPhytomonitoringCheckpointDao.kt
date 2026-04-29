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
    suspend fun insertCheckpoint(checkpoint: LocalPhytomonitoringCheckpointEntity): Long

    @Query("SELECT * FROM local_phytomonitoring_checkpoints WHERE idCheckpoint = :idCheckpoint LIMIT 1")
    suspend fun getCheckpointById(idCheckpoint: Long): LocalPhytomonitoringCheckpointEntity?

    @Query("""
        SELECT * FROM local_phytomonitoring_checkpoints
        WHERE idTargetPoint = :idTargetPoint
        ORDER BY createdAt DESC
    """)
    suspend fun getCheckpointsByTargetPoint(idTargetPoint: Long): List<LocalPhytomonitoringCheckpointEntity>

    @Query("""
        SELECT * FROM local_phytomonitoring_checkpoints
        WHERE idHeader = :idHeader
        ORDER BY createdAt DESC
    """)
    suspend fun getCheckpointsByHeader(idHeader: Long): List<LocalPhytomonitoringCheckpointEntity>

    @Delete
    suspend fun deleteCheckpoint(checkpoint: LocalPhytomonitoringCheckpointEntity)
}
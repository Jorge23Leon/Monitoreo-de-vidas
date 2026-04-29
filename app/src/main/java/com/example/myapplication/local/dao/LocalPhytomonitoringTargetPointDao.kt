package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity

@Dao
interface LocalPhytomonitoringTargetPointDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTargetPoint(targetPoint: LocalPhytomonitoringTargetPointEntity): Long

    @Query("SELECT * FROM local_phytomonitoring_target_points ORDER BY idTargetPoint ASC")
    suspend fun getAllTargetPoints(): List<LocalPhytomonitoringTargetPointEntity>

    @Query("SELECT * FROM local_phytomonitoring_target_points WHERE idTargetPoint = :idTargetPoint LIMIT 1")
    suspend fun getTargetPointById(idTargetPoint: Long): LocalPhytomonitoringTargetPointEntity?

    @Query("SELECT * FROM local_phytomonitoring_target_points WHERE idHeader = :idHeader ORDER BY idTargetPoint ASC")
    suspend fun getTargetPointsByHeader(idHeader: Long): List<LocalPhytomonitoringTargetPointEntity>

    @Query("SELECT * FROM local_phytomonitoring_target_points WHERE idLocalPlot = :idLocalPlot ORDER BY idTargetPoint ASC")
    suspend fun getTargetPointsByPlot(idLocalPlot: Long): List<LocalPhytomonitoringTargetPointEntity>
}
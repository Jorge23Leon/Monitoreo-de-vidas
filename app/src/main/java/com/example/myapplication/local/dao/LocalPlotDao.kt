package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalPlotEntity

@Dao
interface LocalPlotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlot(plot: LocalPlotEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlots(plots: List<LocalPlotEntity>)

    @Update
    suspend fun updatePlot(plot: LocalPlotEntity)

    @Delete
    suspend fun deletePlot(plot: LocalPlotEntity)

    @Query("SELECT * FROM local_plots ORDER BY idLocalPlot ASC")
    suspend fun getAllPlots(): List<LocalPlotEntity>

    @Query("SELECT * FROM local_plots WHERE idLocalPlot = :idLocalPlot LIMIT 1")
    suspend fun getPlotById(idLocalPlot: Long): LocalPlotEntity?

    @Query("SELECT * FROM local_plots WHERE idLocalRanch = :idLocalRanch ORDER BY code ASC")
    suspend fun getPlotsByRanch(idLocalRanch: Long): List<LocalPlotEntity>

    @Query("SELECT * FROM local_plots WHERE idCrop = :idCrop ORDER BY code ASC")
    suspend fun getPlotsByCrop(idCrop: Long): List<LocalPlotEntity>

    @Query("SELECT * FROM local_plots WHERE code = :code LIMIT 1")
    suspend fun getPlotByCode(code: String): LocalPlotEntity?

    @Query("SELECT COUNT(*) FROM local_plots")
    suspend fun countPlots(): Int

    @Query("DELETE FROM local_plots")
    suspend fun deleteAllPlots()
}
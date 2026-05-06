package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalProgramEntity

@Dao
interface LocalProgramDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProgram(program: LocalProgramEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPrograms(programs: List<LocalProgramEntity>)

    @Update
    suspend fun updateProgram(program: LocalProgramEntity)

    @Delete
    suspend fun deleteProgram(program: LocalProgramEntity)

    @Query("SELECT * FROM local_programs ORDER BY idProgram ASC")
    suspend fun getAllPrograms(): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idProgram = :idProgram LIMIT 1")
    suspend fun getProgramById(idProgram: Long): LocalProgramEntity?

    @Query("SELECT * FROM local_programs WHERE status = :status ORDER BY est_start_date ASC")
    suspend fun getProgramsByStatus(status: String): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idLocalAgroUnit = :idLocalAgroUnit ORDER BY est_start_date ASC")
    suspend fun getProgramsByAgroUnit(idLocalAgroUnit: Long): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idCrop = :idCrop ORDER BY est_start_date ASC")
    suspend fun getProgramsByCrop(idCrop: Long): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idLocalPlot = :idLocalPlot ORDER BY est_start_date ASC")
    suspend fun getProgramsByPlot(idLocalPlot: Long): List<LocalProgramEntity>

    @Query("""
        SELECT * FROM local_programs 
        WHERE idLocalAgroUnit = :idLocalAgroUnit 
        AND idCrop = :idCrop 
        AND idLocalPlot = :idLocalPlot 
        ORDER BY est_start_date ASC
    """)
    suspend fun getProgramsByAgroUnitCropAndPlot(
        idLocalAgroUnit: Long,
        idCrop: Long,
        idLocalPlot: Long
    ): List<LocalProgramEntity>

    @Query("SELECT COUNT(*) FROM local_programs")
    suspend fun countPrograms(): Int

    @Query("DELETE FROM local_programs")
    suspend fun deleteAllPrograms()
}
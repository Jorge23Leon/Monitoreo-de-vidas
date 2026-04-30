package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalProgramEntity

@Dao
interface LocalProgramDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProgram(program: LocalProgramEntity): Long

    @Query("SELECT * FROM local_programs ORDER BY idProgram ASC")
    suspend fun getAllPrograms(): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idProgram = :idProgram LIMIT 1")
    suspend fun getProgramById(idProgram: Long): LocalProgramEntity?


    @Query("SELECT * FROM local_programs WHERE status = :status ORDER BY estStartDate ASC")
    suspend fun getProgramsByStatus(status: String): List<LocalProgramEntity>
}
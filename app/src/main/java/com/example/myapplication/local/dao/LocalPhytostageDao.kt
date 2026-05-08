package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalPhytostageEntity

@Dao
interface LocalPhytostageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhytostage(stage: LocalPhytostageEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhytostages(stages: List<LocalPhytostageEntity>)

    @Update
    suspend fun updatePhytostage(stage: LocalPhytostageEntity)

    @Delete
    suspend fun deletePhytostage(stage: LocalPhytostageEntity)

    @Query("SELECT * FROM local_phytostages ORDER BY idLocalPhytostage ASC")
    suspend fun getAllPhytostages(): List<LocalPhytostageEntity>

    @Query("SELECT * FROM local_phytostages WHERE idLocalPhytostage = :idLocalPhytostage LIMIT 1")
    suspend fun getPhytostageById(idLocalPhytostage: Long): LocalPhytostageEntity?

    @Query("SELECT * FROM local_phytostages WHERE stage = :stage LIMIT 1")
    suspend fun getPhytostageByStage(stage: String): LocalPhytostageEntity?

    @Query("SELECT * FROM local_phytostages WHERE idPhytosanitary = :idPhytosanitary ORDER BY stage ASC")
    suspend fun getPhytostagesByPhytosanitary(idPhytosanitary: Long): List<LocalPhytostageEntity>

    @Query("""
        SELECT * FROM local_phytostages 
        WHERE idPhytosanitary = :idPhytosanitary 
        AND stage = :stage 
        LIMIT 1
    """)
    suspend fun getPhytostageByPhytosanitaryAndStage(
        idPhytosanitary: Long,
        stage: String
    ): LocalPhytostageEntity?

    @Query("SELECT COUNT(*) FROM local_phytostages")
    suspend fun countPhytostages(): Int

    @Query("DELETE FROM local_phytostages")
    suspend fun deleteAllPhytostages()

    @Query("""
    SELECT *
    FROM local_phytostages
    WHERE idPhytosanitary = :idPhytosanitary
    ORDER BY idLocalPhytostage ASC
""")
    suspend fun getStagesByPhytosanitary(
        idPhytosanitary: Long
    ): List<LocalPhytostageEntity>





}
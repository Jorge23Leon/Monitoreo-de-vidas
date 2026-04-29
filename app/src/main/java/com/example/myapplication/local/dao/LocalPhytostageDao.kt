package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalPhytostageEntity

@Dao
interface LocalPhytostageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhytostage(stage: LocalPhytostageEntity): Long

    @Query("SELECT * FROM local_phytostages ORDER BY idLocalPhytostage ASC")
    suspend fun getAllPhytostages(): List<LocalPhytostageEntity>

    @Query("SELECT * FROM local_phytostages WHERE idLocalPhytostage = :idLocalPhytostage LIMIT 1")
    suspend fun getPhytostageById(idLocalPhytostage: Long): LocalPhytostageEntity?

    @Query("""
        SELECT * FROM local_phytostages
        WHERE idPhytosanitary = :idPhytosanitary
        ORDER BY stage ASC
    """)
    suspend fun getPhytostagesByPhytosanitary(
        idPhytosanitary: Long
    ): List<LocalPhytostageEntity>
}
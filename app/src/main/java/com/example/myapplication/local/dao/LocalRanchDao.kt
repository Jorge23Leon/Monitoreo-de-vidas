package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalRanchEntity

@Dao
interface LocalRanchDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRanch(ranch: LocalRanchEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRanches(ranches: List<LocalRanchEntity>)

    @Update
    suspend fun updateRanch(ranch: LocalRanchEntity)

    @Delete
    suspend fun deleteRanch(ranch: LocalRanchEntity)

    @Query("SELECT * FROM local_ranches ORDER BY idLocalRanch ASC")
    suspend fun getAllRanches(): List<LocalRanchEntity>

    @Query("SELECT * FROM local_ranches WHERE idLocalRanch = :idLocalRanch LIMIT 1")
    suspend fun getRanchById(idLocalRanch: Long): LocalRanchEntity?

    @Query("SELECT * FROM local_ranches WHERE idLocalAgroUnit = :idLocalAgroUnit ORDER BY name ASC")
    suspend fun getRanchesByAgroUnit(idLocalAgroUnit: Long): List<LocalRanchEntity>

    @Query("SELECT * FROM local_ranches WHERE code = :code LIMIT 1")
    suspend fun getRanchByCode(code: String): LocalRanchEntity?

    @Query("SELECT COUNT(*) FROM local_ranches")
    suspend fun countRanches(): Int

    @Query("DELETE FROM local_ranches")
    suspend fun deleteAllRanches()
}
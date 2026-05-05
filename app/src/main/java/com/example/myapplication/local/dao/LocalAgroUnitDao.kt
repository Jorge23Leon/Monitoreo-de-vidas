package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalAgroUnitEntity

@Dao
interface LocalAgroUnitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAgroUnit(agroUnit: LocalAgroUnitEntity): Long

    @Query("SELECT * FROM local_agro_units ORDER BY idLocalAgroUnit ASC")
    suspend fun getAllAgroUnits(): List<LocalAgroUnitEntity>

    @Query("SELECT * FROM local_agro_units WHERE idLocalAgroUnit = :idLocalAgroUnit LIMIT 1")
    suspend fun getAgroUnitById(idLocalAgroUnit: Long): LocalAgroUnitEntity?

    @Query("SELECT * FROM local_agro_units WHERE slug = :slug LIMIT 1")
    suspend fun getAgroUnitBySlug(slug: String): LocalAgroUnitEntity?
}
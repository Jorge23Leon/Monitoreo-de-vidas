package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalCropCatalogEntity

@Dao
interface LocalCropCatalogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCrop(crop: LocalCropCatalogEntity): Long

    @Query("SELECT * FROM local_crop_catalog ORDER BY idCrop ASC")
    suspend fun getAllCrops(): List<LocalCropCatalogEntity>

    @Query("SELECT * FROM local_crop_catalog WHERE idCrop = :idCrop LIMIT 1")
    suspend fun getCropById(idCrop: Long): LocalCropCatalogEntity?

    @Query("SELECT * FROM local_crop_catalog WHERE code = :code LIMIT 1")
    suspend fun getCropByCode(code: String): LocalCropCatalogEntity?


}
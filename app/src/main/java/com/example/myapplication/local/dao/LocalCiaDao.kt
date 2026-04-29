package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalCiaEntity

@Dao
interface LocalCiaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCia(cia: LocalCiaEntity): Long

    @Query("SELECT * FROM local_cias ORDER BY idLocalCia ASC")
    suspend fun getAllCias(): List<LocalCiaEntity>

    @Query("SELECT * FROM local_cias WHERE idLocalCia = :idLocalCia LIMIT 1")
    suspend fun getCiaById(idLocalCia: Long): LocalCiaEntity?

    @Query("SELECT * FROM local_cias WHERE slug = :slug LIMIT 1")
    suspend fun getCiaBySlug(slug: String): LocalCiaEntity?
}
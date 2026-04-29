package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity

@Dao
interface LocalPhytomonitoringHeaderDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHeader(header: LocalPhytomonitoringHeaderEntity): Long


    @Query("SELECT * FROM local_phytomonitoring_headers ORDER BY idHeader ASC")
    suspend fun getAllHeaders(): List<LocalPhytomonitoringHeaderEntity>

    @Query("SELECT * FROM local_phytomonitoring_headers WHERE idHeader = :idHeader LIMIT 1")
    suspend fun getHeaderById(idHeader: Long): LocalPhytomonitoringHeaderEntity?

}
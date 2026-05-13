package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity

@Dao
interface LocalPhytosanitaryCatalogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhytosanitary(item: LocalPhytosanitaryCatalogEntity): Long

    @Query("""
        SELECT *
        FROM local_phytosanitary_catalog
        ORDER BY idPhytosanitary ASC
    """)
    suspend fun getAllPhytosanitary(): List<LocalPhytosanitaryCatalogEntity>

    @Query("""
        SELECT *
        FROM local_phytosanitary_catalog
        WHERE idPhytosanitary = :idPhytosanitary
        LIMIT 1
    """)
    suspend fun getPhytosanitaryById(
        idPhytosanitary: Long
    ): LocalPhytosanitaryCatalogEntity?

    @Query("""
        SELECT *
        FROM local_phytosanitary_catalog
        WHERE LOWER(TRIM(type)) = LOWER(TRIM(:type))
        ORDER BY name ASC
    """)
    suspend fun getPhytosanitaryByType(
        type: String
    ): List<LocalPhytosanitaryCatalogEntity>

    @Query("""
        SELECT *
        FROM local_phytosanitary_catalog
        WHERE name LIKE '%' || :name || '%'
        ORDER BY name ASC
    """)
    suspend fun searchPhytosanitaryByName(
        name: String
    ): List<LocalPhytosanitaryCatalogEntity>

    @Query("""
        SELECT *
        FROM local_phytosanitary_catalog
        WHERE idDefaultCrop = :idCrop
           OR idDefaultCrop IS NULL
        ORDER BY type ASC, name ASC
    """)
    suspend fun getCatalogoByCropOrGeneral(
        idCrop: Long
    ): List<LocalPhytosanitaryCatalogEntity>

    @Query("""
        SELECT *
        FROM local_phytosanitary_catalog
        ORDER BY type ASC, name ASC
    """)
    suspend fun getAllCatalogo(): List<LocalPhytosanitaryCatalogEntity>
}
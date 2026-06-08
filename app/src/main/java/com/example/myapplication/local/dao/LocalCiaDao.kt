package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaEntity


@Dao
interface LocalCiaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCia(cia: LocalCiaEntity): Long

    @Query("SELECT * FROM local_cias ORDER BY name ASC")
    suspend fun getAllCias(): List<LocalCiaEntity>

    @Query("SELECT * FROM local_cias WHERE idLocalCia = :idLocalCia LIMIT 1")
    suspend fun getCiaById(idLocalCia: Long): LocalCiaEntity?

    @Query("SELECT * FROM local_cias WHERE slug = :slug LIMIT 1")
    suspend fun getCiaBySlug(slug: String): LocalCiaEntity?

    @Query(
        """
        SELECT *
        FROM local_cias
        WHERE idParentCia = :idParentCia
        ORDER BY name ASC
        """
    )
    suspend fun getCiasByParentCia(idParentCia: Long): List<LocalCiaEntity>

    @Query(
        """
        SELECT au.*
        FROM local_agro_units AS au
        INNER JOIN local_cia_agro_units AS cau
            ON au.idLocalAgroUnit = cau.idLocalAgroUnit
        WHERE cau.idLocalCia = :idLocalCia
        ORDER BY au.commercial_name ASC
        """
    )
    suspend fun getProductoresByCia(idLocalCia: Long): List<LocalAgroUnitEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCiaApi(cia: LocalCiaEntity): Long

    @Query("""
    SELECT * 
    FROM local_cias 
    WHERE ext_id = :extId 
    LIMIT 1
""")
    suspend fun getCiaByExtId(extId: String): LocalCiaEntity?

    @Query("""
    UPDATE local_cias
    SET 
        ext_id = :extId,
        name = :name,
        slug = :slug,
        description = :description,
        idParentCia = :idParentCia
    WHERE idLocalCia = :idLocalCia
""")
    suspend fun updateCiaApiById(
        idLocalCia: Long,
        extId: String?,
        name: String,
        slug: String,
        description: String?,
        idParentCia: Long
    )
}

package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalParentCiaEntity

@Dao
interface LocalParentCiaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParentCia(parentCia: LocalParentCiaEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParentCias(parentCias: List<LocalParentCiaEntity>)

    @Query("""
        SELECT * 
        FROM local_parent_cias 
        ORDER BY name ASC
    """)
    suspend fun getAllParentCias(): List<LocalParentCiaEntity>

    @Query("""
        SELECT * 
        FROM local_parent_cias 
        ORDER BY name ASC
    """)
    suspend fun getAllParentCiasActivas(): List<LocalParentCiaEntity>

    @Query("""
        SELECT * 
        FROM local_parent_cias 
        WHERE idParentCia = :idParentCia 
        LIMIT 1
    """)
    suspend fun getParentCiaById(idParentCia: Long): LocalParentCiaEntity?

    @Query("""
        SELECT * 
        FROM local_parent_cias 
        WHERE slug = :slug 
        LIMIT 1
    """)
    suspend fun getParentCiaBySlug(slug: String): LocalParentCiaEntity?

    @Query("""
        SELECT COUNT(*) 
        FROM local_parent_cias
    """)
    suspend fun countParentCias(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParentCiaApi(parentCia: LocalParentCiaEntity): Long

    @Query("""
    SELECT * 
    FROM local_parent_cias 
    WHERE ext_id = :extId 
    LIMIT 1
""")
    suspend fun getParentCiaByExtId(extId: String): LocalParentCiaEntity?

    @Query("""
    UPDATE local_parent_cias
    SET 
        ext_id = :extId,
        name = :name,
        slug = :slug,
        description = :description,
        level = :level
    WHERE idParentCia = :idParentCia
""")
    suspend fun updateParentCiaApiById(
        idParentCia: Long,
        extId: String?,
        name: String,
        slug: String,
        description: String?,
        level: Int
    )




}
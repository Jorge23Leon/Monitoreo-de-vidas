package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaEntity

@Dao
interface LocalCiaDao {
    @Query(
        """
        SELECT c.*
        FROM local_cias AS c
        INNER JOIN user_local_cias AS uc
        ON c.idLocalCia = uc.idLocalCia
        WHERE uc.idUser = :idUser
        ORDER BY c.nombre ASC
        """
    )
    suspend fun obtenerCiasPorUsuario(idUser: Long): List<LocalCiaEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCia(cia: LocalCiaEntity): Long

    @Query("SELECT * FROM local_cias ORDER BY idLocalCia ASC")
    suspend fun getAllCias(): List<LocalCiaEntity>

    @Query("SELECT * FROM local_cias WHERE idLocalCia = :idLocalCia LIMIT 1")
    suspend fun getCiaById(idLocalCia: Long): LocalCiaEntity?

    @Query("SELECT * FROM local_cias WHERE slug = :slug LIMIT 1")
    suspend fun getCiaBySlug(slug: String): LocalCiaEntity?
    @Query("""
    SELECT au.*
    FROM local_agro_units au
    INNER JOIN local_cia_agro_units cau
        ON au.idLocalAgroUnit = cau.idLocalAgroUnit
    WHERE cau.idLocalCia = :idLocalCia
    ORDER BY au.commercial_name ASC
""")
    suspend fun getProductoresByCia(idLocalCia: Long): List<LocalAgroUnitEntity>
}
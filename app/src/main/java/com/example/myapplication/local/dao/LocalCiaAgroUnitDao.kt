package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaAgroUnitCrossRef
import com.example.myapplication.local.entities.LocalCiaEntity

@Dao
interface LocalCiaAgroUnitDao {

    @Query("SELECT * FROM local_cia_agro_units")
    suspend fun getAllAssignments(): List<LocalCiaAgroUnitCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun asignarProductorACia(asignacion: LocalCiaAgroUnitCrossRef)

    @Query("DELETE FROM local_cia_agro_units WHERE idLocalCia = :idLocalCia")
    suspend fun eliminarProductoresDeCia(idLocalCia: Long)

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
    suspend fun obtenerProductoresPorCia(idLocalCia: Long): List<LocalAgroUnitEntity>

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

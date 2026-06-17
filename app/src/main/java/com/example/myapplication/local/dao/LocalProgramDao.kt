package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalProgramEntity

@Dao
interface LocalProgramDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProgram(program: LocalProgramEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPrograms(programs: List<LocalProgramEntity>)

    @Update
    suspend fun updateProgram(program: LocalProgramEntity)

    @Delete
    suspend fun deleteProgram(program: LocalProgramEntity)

    @Query("SELECT * FROM local_programs ORDER BY idProgram ASC")
    suspend fun getAllPrograms(): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idProgram = :idProgram LIMIT 1")
    suspend fun getProgramById(idProgram: Long): LocalProgramEntity?

    @Query("SELECT * FROM local_programs WHERE status = :status ORDER BY est_start_date ASC")
    suspend fun getProgramsByStatus(status: String): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idLocalAgroUnit = :idLocalAgroUnit ORDER BY est_start_date ASC")
    suspend fun getProgramsByAgroUnit(idLocalAgroUnit: Long): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idCrop = :idCrop ORDER BY est_start_date ASC")
    suspend fun getProgramsByCrop(idCrop: Long): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idLocalPlot = :idLocalPlot ORDER BY est_start_date ASC")
    suspend fun getProgramsByPlot(idLocalPlot: Long): List<LocalProgramEntity>
    @Query("""
    SELECT *
    FROM local_programs
    WHERE ext_id = :extId
    LIMIT 1
""")
    suspend fun getProgramByExtId(extId: String): LocalProgramEntity?

    @Query("""
        SELECT * FROM local_programs 
        WHERE idLocalAgroUnit = :idLocalAgroUnit 
        AND idCrop = :idCrop 
        AND idLocalPlot = :idLocalPlot 
        ORDER BY est_start_date ASC
    """)
    suspend fun getProgramsByAgroUnitCropAndPlot(
        idLocalAgroUnit: Long,
        idCrop: Long,
        idLocalPlot: Long
    ): List<LocalProgramEntity>

    @Query("SELECT COUNT(*) FROM local_programs")
    suspend fun countPrograms(): Int

    @Query("DELETE FROM local_programs")
    suspend fun deleteAllPrograms()

    @Query("""
    SELECT DISTINCT p.*
    FROM local_programs p
    INNER JOIN local_cia_agro_units cau
        ON p.idLocalAgroUnit = cau.idLocalAgroUnit
    INNER JOIN local_ranches r
        ON r.idLocalRanch = p.idLocalRanch
       AND r.idLocalAgroUnit = cau.idLocalAgroUnit
    INNER JOIN local_plots pl
        ON pl.idLocalPlot = p.idLocalPlot
       AND pl.idLocalRanch = r.idLocalRanch
    WHERE cau.idLocalCia = :idLocalCia
    ORDER BY p.est_start_date DESC
""")
    suspend fun getProgramasByCia(idLocalCia: Long): List<LocalProgramEntity>


    @Query("""
    SELECT *
    FROM local_programs
    WHERE idLocalAgroUnit = :idProductor
    AND idLocalPlot = :idPlot
    ORDER BY est_start_date DESC
""")
    suspend fun getCiclosByProductorAndParcela(
        idProductor: Long,
        idPlot: Long
    ): List<LocalProgramEntity>


    @Query("""
    SELECT *
    FROM local_programs
    WHERE idProgram IN (:ids)
""")
    suspend fun getProgramasByIds(ids: List<Long>): List<LocalProgramEntity>
    @Query("""
    UPDATE local_programs
    SET status = CASE

        -- Si algún monitoreo del programa está en proceso,
        -- el programa completo está en proceso.
        WHEN EXISTS (
            SELECT 1
            FROM local_phytomonitoring_headers h
            WHERE h.idProgram = :idProgram
            AND LOWER(TRIM(h.status)) IN (
                'in_progress',
                'en proceso',
                'vigente'
            )
        ) THEN 'En proceso'

        -- Si todos los monitoreos del programa están completados,
        -- el programa completo queda completado.
        WHEN (
            SELECT COUNT(*)
            FROM local_phytomonitoring_headers h
            WHERE h.idProgram = :idProgram
        ) > 0
        AND NOT EXISTS (
            SELECT 1
            FROM local_phytomonitoring_headers h
            WHERE h.idProgram = :idProgram
            AND LOWER(TRIM(h.status)) NOT IN (
                'completed',
                'complete',
                'completado',
                'finalizado',
                'terminado',
                'cerrado'
            )
        ) THEN 'Completado'

        -- Si ya hay al menos un monitoreo terminado,
        -- pero no todos, el programa está en proceso.
        WHEN EXISTS (
            SELECT 1
            FROM local_phytomonitoring_headers h
            WHERE h.idProgram = :idProgram
            AND LOWER(TRIM(h.status)) IN (
                'completed',
                'complete',
                'completado',
                'finalizado',
                'terminado',
                'cerrado'
            )
        ) THEN 'En proceso'

        ELSE 'Pendiente'
    END
    WHERE idProgram = :idProgram
""")
    suspend fun recalcularEstadoDesdeHeaders(idProgram: Long)


}
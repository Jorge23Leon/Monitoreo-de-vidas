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

    @Query("SELECT * FROM local_programs WHERE ext_id = :extId LIMIT 1")
    suspend fun getProgramByExtId(extId: String): LocalProgramEntity?

    @Query("SELECT * FROM local_programs WHERE status = :status ORDER BY est_start_date ASC")
    suspend fun getProgramsByStatus(status: String): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idLocalAgroUnit = :idLocalAgroUnit ORDER BY est_start_date ASC")
    suspend fun getProgramsByAgroUnit(idLocalAgroUnit: Long): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idCrop = :idCrop ORDER BY est_start_date ASC")
    suspend fun getProgramsByCrop(idCrop: Long): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idLocalPlot = :idLocalPlot ORDER BY est_start_date ASC")
    suspend fun getProgramsByPlot(idLocalPlot: Long): List<LocalProgramEntity>

    @Query(
        """
        SELECT *
        FROM local_programs
        WHERE idLocalAgroUnit = :idLocalAgroUnit
          AND idCrop = :idCrop
          AND idLocalPlot = :idLocalPlot
        ORDER BY est_start_date ASC
        """
    )
    suspend fun getProgramsByAgroUnitCropAndPlot(
        idLocalAgroUnit: Long,
        idCrop: Long,
        idLocalPlot: Long
    ): List<LocalProgramEntity>

    @Query("SELECT COUNT(*) FROM local_programs")
    suspend fun countPrograms(): Int

    @Query("DELETE FROM local_programs")
    suspend fun deleteAllPrograms()

    /**
     * Fuente de verdad local para los filtros: ya NO se infiere la CIA por el productor.
     */
    @Query(
        """
        SELECT *
        FROM local_programs
        WHERE idLocalCia = :idLocalCia
        ORDER BY est_start_date DESC
        """
    )
    suspend fun getProgramasByCia(idLocalCia: Long): List<LocalProgramEntity>

    @Query(
        """
        SELECT *
        FROM local_programs
        WHERE idLocalCia = :idLocalCia
          AND idLocalAgroUnit = :idProductor
          AND idLocalPlot = :idPlot
        ORDER BY est_start_date DESC
        """
    )
    suspend fun getCiclosByProductorAndParcela(
        idLocalCia: Long,
        idProductor: Long,
        idPlot: Long
    ): List<LocalProgramEntity>

    @Query("SELECT * FROM local_programs WHERE idProgram IN (:ids)")
    suspend fun getProgramasByIds(ids: List<Long>): List<LocalProgramEntity>

    @Query(
        """
        UPDATE local_programs
        SET status = CASE
            WHEN EXISTS (
                SELECT 1 FROM local_phytomonitoring_headers h
                WHERE h.idProgram = :idProgram
                  AND LOWER(TRIM(h.status)) IN ('in_progress', 'en proceso', 'vigente')
            ) THEN 'En proceso'
            WHEN (
                SELECT COUNT(*) FROM local_phytomonitoring_headers h
                WHERE h.idProgram = :idProgram
            ) > 0
            AND NOT EXISTS (
                SELECT 1 FROM local_phytomonitoring_headers h
                WHERE h.idProgram = :idProgram
                  AND LOWER(TRIM(h.status)) NOT IN (
                    'completed', 'complete', 'completado', 'finalizado', 'terminado', 'cerrado'
                  )
            ) THEN 'Completado'
            WHEN EXISTS (
                SELECT 1 FROM local_phytomonitoring_headers h
                WHERE h.idProgram = :idProgram
                  AND LOWER(TRIM(h.status)) IN (
                    'completed', 'complete', 'completado', 'finalizado', 'terminado', 'cerrado'
                  )
            ) THEN 'En proceso'
            ELSE 'Pendiente'
        END
        WHERE idProgram = :idProgram
        """
    )
    suspend fun recalcularEstadoDesdeHeaders(idProgram: Long)
}

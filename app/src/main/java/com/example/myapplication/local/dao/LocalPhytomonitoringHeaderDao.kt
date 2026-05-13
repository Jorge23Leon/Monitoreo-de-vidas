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

    // =========================
    // INSERTAR
    // =========================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHeader(header: LocalPhytomonitoringHeaderEntity): Long


    // =========================
    // ACTUALIZAR / ELIMINAR
    // =========================

    @Update
    suspend fun updateHeader(header: LocalPhytomonitoringHeaderEntity)

    @Delete
    suspend fun deleteHeader(header: LocalPhytomonitoringHeaderEntity)


    // =========================
    // CONSULTAS GENERALES
    // =========================

    @Query("""
        SELECT *
        FROM local_phytomonitoring_headers
        ORDER BY idHeader ASC
    """)
    suspend fun getAllHeaders(): List<LocalPhytomonitoringHeaderEntity>

    @Query("""
        SELECT *
        FROM local_phytomonitoring_headers
        WHERE idHeader = :idHeader
        LIMIT 1
    """)
    suspend fun getHeaderById(
        idHeader: Long
    ): LocalPhytomonitoringHeaderEntity?

    @Query("""
        SELECT *
        FROM local_phytomonitoring_headers
        WHERE idProgram = :idProgram
        ORDER BY est_start_date DESC
    """)
    suspend fun getHeadersByProgram(
        idProgram: Long
    ): List<LocalPhytomonitoringHeaderEntity>

    @Query("""
        SELECT *
        FROM local_phytomonitoring_headers
        WHERE idLocalPlot = :idLocalPlot
        ORDER BY est_start_date DESC
    """)
    suspend fun getHeadersByPlot(
        idLocalPlot: Long
    ): List<LocalPhytomonitoringHeaderEntity>

    @Query("""
        SELECT *
        FROM local_phytomonitoring_headers
        WHERE status = :status
        ORDER BY est_start_date DESC
    """)
    suspend fun getHeadersByStatus(
        status: String
    ): List<LocalPhytomonitoringHeaderEntity>


    // =========================
    // FILTROS DE MONITOREO
    // =========================
    // Nota:
    // Se filtra por est_start_date tanto para fecha inicio como para fecha fin,
    // porque la pantalla busca monitoreos cuya fecha de inicio esté dentro del rango.

    @Query("""
        SELECT *
        FROM local_phytomonitoring_headers
        WHERE idProgram IN (:programIds)

        AND (:idProgram IS NULL OR idProgram = :idProgram)
        AND (:idPlot IS NULL OR idLocalPlot = :idPlot)

        AND (:startDate IS NULL OR est_start_date >= :startDate)
        AND (:endDate IS NULL OR est_start_date <= :endDate)

        AND status IN (:statuses)

        ORDER BY est_start_date DESC
    """)
    suspend fun filtrarHeadersMonitoreo(
        programIds: List<Long>,
        idProgram: Long?,
        idPlot: Long?,
        startDate: Long?,
        endDate: Long?,
        statuses: List<String>
    ): List<LocalPhytomonitoringHeaderEntity>

    @Query("""
    UPDATE local_phytomonitoring_headers
    SET 
        start_at = :startedAt,
        status = 'En proceso'
    WHERE idHeader = :idHeader
""")
    suspend fun iniciarMonitoreo(
        idHeader: Long,
        startedAt: Long
    )

    @Query("""
    UPDATE local_phytomonitoring_headers
    SET 
        finished_at = :finishedAt,
        status = 'Completado'
    WHERE idHeader = :idHeader
""")
    suspend fun finalizarMonitoreo(
        idHeader: Long,
        finishedAt: Long
    )

    @Query("""
    UPDATE local_phytomonitoring_headers
    SET 
        status = 'Cancelado'
    WHERE idHeader = :idHeader
""")
    suspend fun cancelarMonitoreo(
        idHeader: Long
    )

    @Query("""
    UPDATE local_phytomonitoring_headers
    SET 
        status = 'Pendiente',
        start_at = NULL,
        finished_at = NULL
    WHERE idHeader = :idHeader
""")
    suspend fun regresarAPendiente(
        idHeader: Long
    )}
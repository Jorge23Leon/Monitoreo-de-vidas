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

    /**
     * Cache disponible sin conexión. Se limita a 30 días, pero conserva trabajo
     * activo o pendiente de subir para que una limpieza nunca borre trabajo de campo.
     */
    @Query("""
        SELECT *
        FROM local_phytomonitoring_headers h
        WHERE h.est_start_date IS NULL
           OR h.est_start_date >= :fechaLimite
           OR h.sync_pending = 1
           OR LOWER(TRIM(h.status)) IN (
                'pendiente', 'pending',
                'en proceso', 'in_progress', 'vigente'
           )
           OR EXISTS (
                SELECT 1
                FROM local_phytomonitoring_target_points tp
                WHERE tp.idHeader = h.idHeader
                  AND (tp.ext_id IS NULL OR TRIM(tp.ext_id) = '')
           )
           OR EXISTS (
                SELECT 1
                FROM local_phytomonitoring_checkpoints cp
                WHERE cp.idHeader = h.idHeader
                  AND (
                       cp.ext_id IS NULL OR TRIM(cp.ext_id) = ''
                       OR LOWER(COALESCE(cp.photo_local_path, '')) LIKE '%pending%'
                  )
           )
        ORDER BY h.est_start_date DESC
    """)
    suspend fun getHeadersDisponiblesOffline(
        fechaLimite: Long
    ): List<LocalPhytomonitoringHeaderEntity>

    @Query("""
        SELECT h.idHeader
        FROM local_phytomonitoring_headers h
        INNER JOIN local_programs p ON p.idProgram = h.idProgram
        WHERE p.idLocalCia = :idLocalCia
          AND (
               h.sync_pending = 1
               OR LOWER(TRIM(h.status)) IN (
                    'pendiente', 'pending',
                    'en proceso', 'in_progress', 'vigente'
               )
               OR EXISTS (
                    SELECT 1
                    FROM local_phytomonitoring_target_points tp
                    WHERE tp.idHeader = h.idHeader
                      AND (tp.ext_id IS NULL OR TRIM(tp.ext_id) = '')
               )
               OR EXISTS (
                    SELECT 1
                    FROM local_phytomonitoring_checkpoints cp
                    WHERE cp.idHeader = h.idHeader
                      AND (
                           cp.ext_id IS NULL OR TRIM(cp.ext_id) = ''
                           OR LOWER(COALESCE(cp.photo_local_path, '')) LIKE '%pending%'
                      )
               )
          )
    """)
    suspend fun getIdsHeadersProtegidosPorCia(
        idLocalCia: Long
    ): List<Long>

    @Query("""
        SELECT h.idHeader
        FROM local_phytomonitoring_headers h
        INNER JOIN local_programs p ON p.idProgram = h.idProgram
        WHERE p.idLocalCia = :idLocalCia
          AND h.est_start_date IS NOT NULL
          AND h.est_start_date < :fechaLimite
          AND h.sync_pending = 0
          AND LOWER(TRIM(h.status)) IN (
               'completado', 'completed', 'finalizado', 'terminado', 'cerrado',
               'cancelado', 'cancelled', 'canceled'
          )
          AND NOT EXISTS (
               SELECT 1
               FROM local_phytomonitoring_target_points tp
               WHERE tp.idHeader = h.idHeader
                 AND (tp.ext_id IS NULL OR TRIM(tp.ext_id) = '')
          )
          AND NOT EXISTS (
               SELECT 1
               FROM local_phytomonitoring_checkpoints cp
               WHERE cp.idHeader = h.idHeader
                 AND (
                      cp.ext_id IS NULL OR TRIM(cp.ext_id) = ''
                      OR LOWER(COALESCE(cp.photo_local_path, '')) LIKE '%pending%'
                 )
          )
    """)
    suspend fun getIdsHeadersDepurablesPorCia(
        idLocalCia: Long,
        fechaLimite: Long
    ): List<Long>

    @Query("""
        DELETE FROM local_phytomonitoring_headers
        WHERE idHeader IN (:idsHeaders)
    """)
    suspend fun eliminarHeadersPorIds(idsHeaders: List<Long>): Int

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
    WHERE ext_id = :extId
    LIMIT 1
""")
    suspend fun getHeaderByExtId(extId: String): LocalPhytomonitoringHeaderEntity?

    @Query("""
        SELECT h.*
        FROM local_phytomonitoring_headers h
        INNER JOIN local_programs p ON p.idProgram = h.idProgram
        WHERE p.idLocalCia = :idLocalCia
          AND h.sync_pending = 1
        ORDER BY h.est_start_date ASC
    """)
    suspend fun getHeadersPendingSyncByCia(
        idLocalCia: Long
    ): List<LocalPhytomonitoringHeaderEntity>

    @Query("""
        UPDATE local_phytomonitoring_headers
        SET sync_pending = 0
        WHERE idHeader = :idHeader
    """)
    suspend fun marcarHeaderSincronizado(idHeader: Long)

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
            status = 'En proceso',
            sync_pending = 1
    WHERE idHeader = :idHeader
""")
    suspend fun iniciarMonitoreo(
        idHeader: Long,
        startedAt: Long
    )

    @Query("""
    UPDATE local_phytomonitoring_headers
        SET status = 'En proceso',
        start_at = CASE 
            WHEN start_at IS NULL THEN :now 
            ELSE start_at 
        END,
        finished_at = NULL,
        sync_pending = 1
    WHERE idHeader = :idHeader
      AND LOWER(TRIM(status)) IN ('pendiente', 'pending')
""")
    suspend fun iniciarMonitoreoSiEstaPendiente(
        idHeader: Long,
        now: Long
    )

    @Query("""
    UPDATE local_phytomonitoring_headers
    SET status = 'En proceso',
        finished_at = NULL,
        sync_pending = 1
    WHERE idHeader = :idHeader
""")
    suspend fun dejarMonitoreoEnProceso(
        idHeader: Long
    )

    @Query("""
    UPDATE local_phytomonitoring_headers
    SET status = 'Completado',
        finished_at = :finishedAt,
        sync_pending = 1
    WHERE idHeader = :idHeader
""")
    suspend fun finalizarMonitoreo(
        idHeader: Long,
        finishedAt: Long
    )

    @Query("""
    UPDATE local_phytomonitoring_headers
    SET 
        status = 'Cancelado',
        sync_pending = 1
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
        finished_at = NULL,
        sync_pending = 1
    WHERE idHeader = :idHeader
""")
    suspend fun regresarAPendiente(
        idHeader: Long
    )


    @Query("""
        UPDATE local_phytomonitoring_headers
        SET
            status = 'Cancelado',
            finished_at = :fechaCancelacion,
            additional_notes = :motivoCancelacion,
            sync_pending = 1
        WHERE idHeader = :idHeader
    """)
    suspend fun cancelarMonitoreoConMotivo(
        idHeader: Long,
        fechaCancelacion: Long,
        motivoCancelacion: String
    ): Int

}

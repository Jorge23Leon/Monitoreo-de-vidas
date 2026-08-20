package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


/**
 * Copia local de una sesión NDVI.
 *
 * Esta tabla permite consultar las sesiones descargadas
 * incluso cuando el dispositivo no tenga internet.
 */
@Entity(
    tableName = "local_ndvi_sessions",
    indices = [
        Index(value = ["program_id"]),
        Index(value = ["plot_id"]),
        Index(value = ["assigned_to_id"]),
        Index(value = ["session_date"]),
        Index(value = ["status"]),
        Index(value = ["import_status"])
    ]
)
data class LocalNdviSessionEntity(

    /**
     * Mismo UUID que usa Django.
     */
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "program_id")
    val programId: String? = null,

    @ColumnInfo(name = "plot_id")
    val plotId: String? = null,

    @ColumnInfo(name = "session_date")
    val sessionDate: String? = null,

    val status: String? = null,

    @ColumnInfo(name = "assigned_to_id")
    val assignedToId: String? = null,

    @ColumnInfo(name = "assigned_to_username")
    val assignedToUsername: String? = null,

    @ColumnInfo(name = "est_start_date")
    val estStartDate: String? = null,

    @ColumnInfo(name = "est_finish_date")
    val estFinishDate: String? = null,

    @ColumnInfo(name = "act_start_date")
    val actStartDate: String? = null,

    @ColumnInfo(name = "act_finish_date")
    val actFinishDate: String? = null,

    @ColumnInfo(name = "import_status")
    val importStatus: String? = null,

    /**
     * Guardaremos el JSON de errores como texto.
     */
    @ColumnInfo(name = "import_errors_json")
    val importErrorsJson: String? = null,

    @ColumnInfo(name = "imported_at")
    val importedAt: String? = null,

    @ColumnInfo(name = "points_count")
    val pointsCount: Int? = null,

    /**
     * Estado de generación de contornos.
     */
    @ColumnInfo(name = "contour_status")
    val contourStatus: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null,

    /**
     * Campos exclusivamente locales.
     *
     * Nos permiten saber si una sesión quedó
     * completamente descargada para usarla offline.
     */
    @ColumnInfo(
        name = "downloaded_points_count",
        defaultValue = "0"
    )
    val downloadedPointsCount: Int = 0,

    @ColumnInfo(
        name = "points_download_complete",
        defaultValue = "0"
    )
    val pointsDownloadComplete: Boolean = false,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null
)
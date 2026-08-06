package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Encabezado local de una sesión de aspersión.
 *
 * Los UUID se conservan como String porque son las llaves reales del backend.
 * Los campos de descarga son exclusivamente locales y permiten saber si el
 * mapa quedó completamente disponible sin conexión.
 */

@Entity(
    tableName = "local_aspersion_sessions",
    indices = [
        Index(value = ["program_id"]),
        Index(value = ["plot_id"]),
        Index(value = ["assigned_to_id"]),
        Index(value = ["aspersion_date"]),
        Index(value = ["status"]),
        Index(value = ["import_status"])
    ]
)
data class LocalAspersionSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "program_id")
    val programId: String? = null,

    @ColumnInfo(name = "program_name")
    val programName: String? = null,

    @ColumnInfo(name = "program_cycle")
    val programCycle: String? = null,

    @ColumnInfo(name = "plot_id")
    val plotId: String? = null,

    @ColumnInfo(name = "plot_name")
    val plotName: String? = null,

    @ColumnInfo(name = "plot_code")
    val plotCode: String? = null,

    @ColumnInfo(name = "ranch_id")
    val ranchId: String? = null,

    @ColumnInfo(name = "ranch_name")
    val ranchName: String? = null,

    @ColumnInfo(name = "producer_id")
    val producerId: String? = null,

    @ColumnInfo(name = "producer_name")
    val producerName: String? = null,

    /** UUID de las CIA/DataCentral relacionadas, separados por coma. */
    @ColumnInfo(name = "data_central_ids")
    val dataCentralIds: String? = null,

    @ColumnInfo(name = "evaluation_id")
    val evaluationId: String? = null,

    @ColumnInfo(name = "aspersion_date")
    val aspersionDate: String? = null,

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

    @ColumnInfo(name = "import_errors_json")
    val importErrorsJson: String? = null,

    @ColumnInfo(name = "imported_at")
    val importedAt: String? = null,

    @ColumnInfo(name = "last_template_id")
    val lastTemplateId: String? = null,

    @ColumnInfo(name = "points_count")
    val pointsCount: Int? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null,

    @ColumnInfo(name = "downloaded_points_count", defaultValue = "0")
    val downloadedPointsCount: Int = 0,

    @ColumnInfo(name = "points_download_complete", defaultValue = "0")
    val pointsDownloadComplete: Boolean = false,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null
)

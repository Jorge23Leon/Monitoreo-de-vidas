package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytomonitoring_headers",
    foreignKeys = [
        ForeignKey(
            entity = LocalProgramEntity::class,
            parentColumns = ["idProgram"],
            childColumns = ["idProgram"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalCropCatalogEntity::class,
            parentColumns = ["idCrop"],
            childColumns = ["idCrop"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LocalPlotEntity::class,
            parentColumns = ["idLocalPlot"],
            childColumns = ["idLocalPlot"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["idUser"],
            childColumns = ["assigned_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idProgram"]),
        Index(value = ["idCrop"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["status"]),
        Index(value = ["cycle"]),
        Index(value = ["assigned_user_id"])
    ]
)
data class LocalPhytomonitoringHeaderEntity(
    @PrimaryKey(autoGenerate = true)
    val idHeader: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val cycle: String,

    @ColumnInfo(name = "est_start_date")
    val estStartDate: Long? = null,

    @ColumnInfo(name = "est_finish_date")
    val estFinishDate: Long? = null,

    @ColumnInfo(name = "start_at")
    val startAt: Long? = null,

    @ColumnInfo(name = "finished_at")
    val finishedAt: Long? = null,
    @ColumnInfo(
        name = "additional_notes",
        defaultValue = ""
    )
    val additionalNotes: String = "",

    @ColumnInfo(
        name = "radius_tolerance",
        defaultValue = "50.0"
    )
    val radiusTolerance: Double = 50.0,

    val status: String = "Pendiente",

    val idProgram: Long,
    val idCrop: Long,
    val idLocalPlot: Long,

    @ColumnInfo(name = "assigned_user_id")
    val assignedUserId: Long? = null
)
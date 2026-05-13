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
            entity = LocalPlotEntity::class,
            parentColumns = ["idLocalPlot"],
            childColumns = ["idLocalPlot"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LocalCropCatalogEntity::class,
            parentColumns = ["idCrop"],
            childColumns = ["idCrop"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idProgram"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["idCrop"]),
        Index(value = ["cycle"]),
        Index(value = ["status"]),
        Index(value = ["est_start_date"]),
        Index(value = ["est_finish_date"])
    ]
)
data class LocalPhytomonitoringHeaderEntity(
    @PrimaryKey(autoGenerate = true)
    val idHeader: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val cycle: String,

    @ColumnInfo(name = "est_start_date")
    val estStartDate: Long,

    @ColumnInfo(name = "est_finish_date")
    val estFinishDate: Long,

    @ColumnInfo(name = "start_at")
    val startAt: Long? = null,

    @ColumnInfo(name = "finished_at")
    val finishedAt: Long? = null,

    /**
     * Valores esperados:
     * "Pendiente", "En proceso", "Cancelado", "Completado"
     */
    val status: String = "Pendiente",

    val idProgram: Long,

    val idLocalPlot: Long,

    val idCrop: Long
)
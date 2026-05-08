package com.example.myapplication.local.entities

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
        Index(value = ["ext_Id"], unique = true),
        Index(value = ["idProgram"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["idCrop"]),
        Index(value = ["status"]),
        Index(value = ["est_start_date"]),
        Index(value = ["est_finish_date"])
    ]
)
data class LocalPhytomonitoringHeaderEntity(
    @PrimaryKey(autoGenerate = true)
    val idHeader: Long = 0,

    val ext_Id: String? = null,

    val est_start_date: Long,
    val est_finish_date: Long,

    val started_at: Long? = null,
    val finished_at: Long? = null,

    val status: String = "PENDIENTE",

    val idProgram: Long,
    val idLocalPlot: Long,
    val idCrop: Long
)
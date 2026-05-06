package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_programs",
    foreignKeys = [
        ForeignKey(
            entity = LocalAgroUnitEntity::class,
            parentColumns = ["idLocalAgroUnit"],
            childColumns = ["idLocalAgroUnit"],
            onDelete = ForeignKey.RESTRICT
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
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["idLocalAgroUnit"]),
        Index(value = ["idCrop"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["status"])
    ]
)
data class LocalProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val idProgram: Long = 0,

    val ext_Id: String? = null,

    val cycle: String,
    val est_start_date: Long,
    val est_finish_date: Long,
    val act_start_date: Long? = null,
    val act_finish_date: Long? = null,
    val status: String,

    val idLocalAgroUnit: Long,
    val idCrop: Long,
    val idLocalPlot: Long
)
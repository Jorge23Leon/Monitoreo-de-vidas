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
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalRanchEntity::class,
            parentColumns = ["idLocalRanch"],
            childColumns = ["idLocalRanch"],
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
            childColumns = ["idUserAssigned"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["idLocalAgroUnit"]),
        Index(value = ["idLocalRanch"]),
        Index(value = ["idCrop"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["idUserAssigned"]),
        Index(value = ["status"])
    ]
)
data class LocalProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val idProgram: Long = 0,

    val ext_Id: String? = null,

    val cycle: String,

    val estStartDate: Long,
    val estFinishDate: Long,
    val actStartDate: Long? = null,
    val actFinishDate: Long? = null,

    val status: String,

    val idLocalAgroUnit: Long,
    val idLocalRanch: Long,
    val idCrop: Long,
    val idLocalPlot: Long,

    val idUserAssigned: Long? = null
)
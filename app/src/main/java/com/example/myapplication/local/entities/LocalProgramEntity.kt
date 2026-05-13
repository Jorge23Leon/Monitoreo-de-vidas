package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
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
            entity = LocalRanchEntity::class,
            parentColumns = ["idLocalRanch"],
            childColumns = ["idLocalRanch"],
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
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idLocalAgroUnit"]),
        Index(value = ["idLocalRanch"]),
        Index(value = ["idCrop"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["cycle"]),
        Index(value = ["status"]),
        Index(value = ["idLocalAgroUnit", "idLocalRanch", "idLocalPlot", "idCrop", "cycle"])
    ]
)
data class LocalProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val idProgram: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val cycle: String,

    @ColumnInfo(name = "est_start_date")
    val estStartDate: Long,

    @ColumnInfo(name = "est_finish_date")
    val estFinishDate: Long,

    @ColumnInfo(name = "act_start_date")
    val actStartDate: Long? = null,

    @ColumnInfo(name = "act_finish_date")
    val actFinishDate: Long? = null,

    /**
     * Valores esperados:
     * "Pendiente", "En proceso", "Cancelado", "Completado"
     */
    val status: String,

    // Productor
    val idLocalAgroUnit: Long,

    // Rancho
    val idLocalRanch: Long,

    // Cultivo
    val idCrop: Long,

    // Parcela
    val idLocalPlot: Long
)
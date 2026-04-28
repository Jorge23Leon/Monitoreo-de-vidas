package com.example.myapplication.local.entities
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_plots",
    foreignKeys = [
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
        )
    ],
    indices = [
        Index(value = ["idLocalRanch"]),
        Index(value = ["idCrop"]),
        Index(value = ["code"])
    ]
)
data class LocalPlotEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalPlot: Long = 0,
    val extId: String? = null,
    val code: String,
    val lat: Double,
    val lon: Double,
    val idLocalRanch: Long,
    val ext_IdLocalRanch: Long? = null,
    val idCrop: Long,
    val ext_IdCrop: Long? = null

)
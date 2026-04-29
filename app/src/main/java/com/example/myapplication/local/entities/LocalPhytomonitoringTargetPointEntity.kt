package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytomonitoring_target_points",
    foreignKeys = [
        ForeignKey(
            entity = LocalPhytomonitoringHeaderEntity::class,
            parentColumns = ["idHeader"],
            childColumns = ["idHeader"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalPlotEntity::class,
            parentColumns = ["idLocalPlot"],
            childColumns = ["idLocalPlot"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idHeader"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["status"])
    ]
)
data class LocalPhytomonitoringTargetPointEntity(
    @PrimaryKey(autoGenerate = true)
    val idTargetPoint: Long = 0,

    val ext_Id: String? = null,

    val radius_m: Int,
    val lat: Double,
    val lon: Double,
    val status: String,

    val idHeader: Long,
    val idLocalPlot: Long
)
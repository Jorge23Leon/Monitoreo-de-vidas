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
        )
    ],
    indices = [
        Index(value = ["idHeader"])
    ]
)
data class LocalPhytomonitoringTargetPointEntity(
    @PrimaryKey(autoGenerate = true)
    val idTargetPoint: Long = 0,

    val extId: String? = null,

    val radius: Int,
    val lat: Double,
    val lon: Double,
    val status: String,

    val idHeader: Long,
    val extidHeader: String? = null
)





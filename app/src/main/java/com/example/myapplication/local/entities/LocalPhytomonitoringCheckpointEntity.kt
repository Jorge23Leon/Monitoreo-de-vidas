package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytomonitoring_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = LocalPhytomonitoringTargetPointEntity::class,
            parentColumns = ["idTargetPoint"],
            childColumns = ["idTargetPoint"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalPhytomonitoringHeaderEntity::class,
            parentColumns = ["idHeader"],
            childColumns = ["idHeader"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalPhytosanitaryCatalogEntity::class,
            parentColumns = ["idPhytosanitary"],
            childColumns = ["idPhytosanitary"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["idTargetPoint"]),
        Index(value = ["idHeader"]),
        Index(value = ["idPhytosanitary"]),
        Index(value = ["createdAt"])
    ]
)
data class LocalPhytomonitoringCheckpointEntity(
    @PrimaryKey(autoGenerate = true)
    val idCheckpoint: Long = 0,

    val extId: String? = null,

    val lat: Double,
    val lon: Double,

    val qty: Int? = null,
    val presenceStatus: Int? = null,

    val stage: String? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val idTargetPoint: Long,
    val idHeader: Long,
    val idPhytosanitary: Long
)
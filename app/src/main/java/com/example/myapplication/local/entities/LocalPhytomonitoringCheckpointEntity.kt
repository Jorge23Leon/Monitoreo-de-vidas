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
            entity = LocalPhytosanitaryCatalogEntity::class,
            parentColumns = ["idPhytosanitary"],
            childColumns = ["idPhytosanitary"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LocalPhytostageEntity::class,
            parentColumns = ["idLocalPhytostage"],
            childColumns = ["idLocalPhytostage"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["idTargetPoint"]),
        Index(value = ["idPhytosanitary"]),
        Index(value = ["idLocalPhytostage"]),
        Index(value = ["createdAt"])
    ]
)
data class LocalPhytomonitoringCheckpointEntity(
    @PrimaryKey(autoGenerate = true)
    val idCheckpoint: Long = 0,

    val ex_tId: String? = null,

    val lat: Double,
    val lon: Double,

    val qty: Int? = null,
    val presence_Status: Int? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val idTargetPoint: Long,
    val idPhytosanitary: Long,
    val idLocalPhytostage: Long,
)


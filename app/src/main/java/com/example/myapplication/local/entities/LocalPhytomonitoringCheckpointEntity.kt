package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
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
        ),
        ForeignKey(
            entity = LocalPlotEntity::class,
            parentColumns = ["idLocalPlot"],
            childColumns = ["idLocalPlot"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["idUser"],
            childColumns = ["captured_by_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idTargetPoint"]),
        Index(value = ["idHeader"]),
        Index(value = ["idPhytosanitary"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["captured_at"]),
        Index(value = ["captured_by_user_id"])
    ]
)
data class LocalPhytomonitoringCheckpointEntity(
    @PrimaryKey(autoGenerate = true)
    val idCheckpoint: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val qty: Int? = null,

    // 0 = No presente, 1 = Presente
    @ColumnInfo(name = "presence_status")
    val presenceStatus: Int? = null,

    val stage: String? = null,
    val notes: String? = null,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long? = null,

    @ColumnInfo(name = "captured_by_user_id")
    val capturedByUserId: Long? = null,

    val idTargetPoint: Long,
    val idHeader: Long,
    val idPhytosanitary: Long,
    val idLocalPlot: Long
)

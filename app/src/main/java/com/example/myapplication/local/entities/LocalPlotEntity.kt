package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
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
            entity = UserEntity::class,
            parentColumns = ["idUser"],
            childColumns = ["assigned_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idLocalRanch"]),
        Index(value = ["assigned_user_id"])
    ]
)
data class LocalPlotEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalPlot: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val name: String,
    val code: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,

    val idLocalRanch: Long,

    @ColumnInfo(name = "assigned_user_id")
    val assignedUserId: Long? = null
)
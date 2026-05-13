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
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idLocalRanch"]),
        Index(value = ["idLocalRanch", "code"], unique = true)
    ]
)
data class LocalPlotEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalPlot: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val code: String,

    val description: String? = null,

    val lat: Double,

    val lon: Double,

    val idLocalRanch: Long
)
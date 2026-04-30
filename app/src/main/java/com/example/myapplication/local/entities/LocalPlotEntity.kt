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
        )
    ],
    indices = [
        Index(value = ["idLocalRanch"]),
        Index(value = ["code"])
    ]
)
data class LocalPlotEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalPlot: Long = 0,

    val ext_Id: String? = null,

    val code: String,
    val name: String,
    val description: String? = null,

    val idLocalRanch: Long
)
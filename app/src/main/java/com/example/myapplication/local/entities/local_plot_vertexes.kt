package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_plot_vertexes",
    foreignKeys = [
        ForeignKey(
            entity = LocalPlotEntity::class,
            parentColumns = ["idLocalPlot"],
            childColumns = ["idLocalPlot"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idLocalPlot"]),
        Index(value = ["idLocalPlot", "level"], unique = true)
    ]
)
data class LocalPlotVertexEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalPlotVertex: Long = 0,

    val extId: String? = null,
    // Orden del vértice dentro del polígono
    val level: Int,
    // Coordenadas del vértice
    val lat: Double,
    val lon: Double,

    val idLocalPlot: Long,

)
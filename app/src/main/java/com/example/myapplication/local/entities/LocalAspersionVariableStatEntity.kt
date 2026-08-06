package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Estadística de una variable disponible para colorear el mapa.
 *
 * La llave compuesta permite que cada sesión tenga velocidad, caudal, presión,
 * dosis aplicada y producto sin duplicar registros al sincronizar.
 */
@Entity(
    tableName = "local_aspersion_variable_stats",
    primaryKeys = ["session_id", "variable_key"],
    foreignKeys = [
        ForeignKey(
            entity = LocalAspersionSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["variable_key"])
    ]
)
data class LocalAspersionVariableStatEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "variable_key")
    val variableKey: String,

    val label: String,
    val count: Int = 0,

    @ColumnInfo(name = "mean_value")
    val meanValue: Double? = null,

    @ColumnInfo(name = "min_value")
    val minValue: Double? = null,

    @ColumnInfo(name = "max_value")
    val maxValue: Double? = null,

    val stddev: Double? = null,

    @ColumnInfo(name = "cached_at", defaultValue = "0")
    val cachedAt: Long = 0L
)

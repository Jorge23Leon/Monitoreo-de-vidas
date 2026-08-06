package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Resumen general precalculado de una sesión.
 *
 * session_id es a la vez llave primaria y relación uno a uno con la sesión.
 */
@Entity(
    tableName = "local_aspersion_stats",
    primaryKeys = ["session_id"],
    foreignKeys = [
        ForeignKey(
            entity = LocalAspersionSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plot_id"])
    ]
)
data class LocalAspersionStatsEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "plot_id")
    val plotId: String? = null,

    @ColumnInfo(name = "points_count")
    val pointsCount: Int? = null,

    @ColumnInfo(name = "area_total_ha")
    val areaTotalHa: Double? = null,

    @ColumnInfo(name = "mean_target_l")
    val meanTargetL: Double? = null,

    @ColumnInfo(name = "mean_applied_l")
    val meanAppliedL: Double? = null,

    @ColumnInfo(name = "ratio_applied")
    val ratioApplied: Double? = null,

    @ColumnInfo(name = "pct_below")
    val pctBelow: Double? = null,

    @ColumnInfo(name = "pct_in_range")
    val pctInRange: Double? = null,

    @ColumnInfo(name = "pct_above")
    val pctAbove: Double? = null,

    @ColumnInfo(name = "last_refresh")
    val lastRefresh: String? = null,

    @ColumnInfo(name = "cached_at", defaultValue = "0")
    val cachedAt: Long = 0L
)

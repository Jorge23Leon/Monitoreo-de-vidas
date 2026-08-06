package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Punto GPS importado de una sesión de aspersión.
 *
 * GeoJSON entrega las coordenadas en orden [longitud, latitud]. Se guardan en
 * columnas separadas para dibujarlas y filtrarlas sin volver a procesar JSON.
 */
@Entity(
    tableName = "local_aspersion_points",
    foreignKeys = [
        ForeignKey(
            entity = LocalAspersionSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["plot_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["rate_quality"])
    ]
)
data class LocalAspersionPointEntity(
    @PrimaryKey
    @ColumnInfo(name = "point_id")
    val pointId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "plot_id")
    val plotId: String? = null,

    @ColumnInfo(name = "geom_type")
    val geomType: String? = null,

    val longitude: Double,
    val latitude: Double,
    val timestamp: String? = null,

    @ColumnInfo(name = "pass_number")
    val passNumber: Int? = null,

    @ColumnInfo(name = "elevation_m")
    val elevationM: Double? = null,

    @ColumnInfo(name = "course_deg")
    val courseDeg: Double? = null,

    @ColumnInfo(name = "vehicle_heading")
    val vehicleHeading: Double? = null,

    @ColumnInfo(name = "distance_m")
    val distanceM: Double? = null,

    @ColumnInfo(name = "duration_s")
    val durationS: Int? = null,

    @ColumnInfo(name = "speed_kmh")
    val speedKmh: Double? = null,

    val satellites: Int? = null,

    @ColumnInfo(name = "gnss_hdop")
    val gnssHdop: Double? = null,

    @ColumnInfo(name = "gnss_vdop")
    val gnssVdop: Double? = null,

    @ColumnInfo(name = "gnss_pdop")
    val gnssPdop: Double? = null,

    @ColumnInfo(name = "is_diff_active")
    val isDiffActive: Boolean? = null,

    @ColumnInfo(name = "diff_mode")
    val diffMode: String? = null,

    @ColumnInfo(name = "xte_implement")
    val xteImplement: Double? = null,

    @ColumnInfo(name = "xte_vehicle")
    val xteVehicle: Double? = null,

    @ColumnInfo(name = "is_steering_active")
    val isSteeringActive: Boolean? = null,

    @ColumnInfo(name = "is_area_counting")
    val isAreaCounting: Boolean? = null,

    @ColumnInfo(name = "active_rows")
    val activeRows: Int? = null,

    @ColumnInfo(name = "app_status")
    val appStatus: String? = null,

    @ColumnInfo(name = "boom_width_m")
    val boomWidthM: Double? = null,

    @ColumnInfo(name = "liquid_flow_ls")
    val liquidFlowLs: Double? = null,

    @ColumnInfo(name = "boom_pressure_bar")
    val boomPressureBar: Double? = null,

    @ColumnInfo(name = "press_ag_kpa")
    val pressAgKpa: Double? = null,

    @ColumnInfo(name = "press_aux_kpa")
    val pressAuxKpa: Double? = null,

    @ColumnInfo(name = "production_hah")
    val productionHah: Double? = null,

    @ColumnInfo(name = "droplet_size")
    val dropletSize: String? = null,

    @ColumnInfo(name = "nozzle_color")
    val nozzleColor: String? = null,

    @ColumnInfo(name = "nozzle_capacity")
    val nozzleCapacity: Double? = null,

    @ColumnInfo(name = "nozzle_ref_pressure")
    val nozzleRefPressure: Double? = null,

    @ColumnInfo(name = "target_rate_l")
    val targetRateL: Double? = null,

    @ColumnInfo(name = "applied_rate_l")
    val appliedRateL: Double? = null,

    @ColumnInfo(name = "product_quantity")
    val productQuantity: Double? = null,

    @ColumnInfo(name = "area_ha")
    val areaHa: Double? = null,

    @ColumnInfo(name = "rate_quality")
    val rateQuality: String? = null,

    @ColumnInfo(name = "evaluation_id")
    val evaluationId: String? = null,

    @ColumnInfo(name = "extra_raw_json")
    val extraRawJson: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: String? = null
)

package com.example.myapplication.local.aspersion.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class AspersionPaginatedResponse<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)

data class AspersionAssignedUserDto(
    val id: String,
    val username: String
)

data class AspersionContextItemDto(
    val id: String?,
    val name: String?,
    val code: String? = null,
    val cycle: String? = null
)

data class AspersionDataCentralContextDto(
    val id: String?,
    val name: String?
)

data class AspersionSessionContextDto(
    val program: AspersionContextItemDto?,
    val plot: AspersionContextItemDto?,
    val ranch: AspersionContextItemDto?,
    val producer: AspersionContextItemDto?,
    @SerializedName("data_centrals")
    val dataCentrals: List<AspersionDataCentralContextDto> = emptyList()
)

data class AspersionSessionDto(
    val id: String,
    val program: String?,
    val plot: String?,
    val evaluation: String?,
    @SerializedName("aspersion_date")
    val aspersionDate: String?,
    val status: String?,
    @SerializedName("assigned_to")
    val assignedTo: AspersionAssignedUserDto?,
    val context: AspersionSessionContextDto? = null,
    @SerializedName("est_start_date")
    val estStartDate: String?,
    @SerializedName("est_finish_date")
    val estFinishDate: String?,
    @SerializedName("act_start_date")
    val actStartDate: String?,
    @SerializedName("act_finish_date")
    val actFinishDate: String?,
    @SerializedName("import_status")
    val importStatus: String?,
    @SerializedName("import_errors")
    val importErrors: JsonElement?,
    @SerializedName("imported_at")
    val importedAt: String?,
    @SerializedName("last_template")
    val lastTemplate: String?,
    @SerializedName("points_count")
    val pointsCount: Int?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)

data class AspersionStatsDto(
    @SerializedName("session_header")
    val sessionHeader: String,
    @SerializedName("plot_id")
    val plotId: String?,
    @SerializedName("points_count")
    val pointsCount: Int?,
    @SerializedName("area_total_ha")
    val areaTotalHa: String?,
    @SerializedName("mean_target_l")
    val meanTargetL: String?,
    @SerializedName("mean_applied_l")
    val meanAppliedL: String?,
    @SerializedName("ratio_applied")
    val ratioApplied: String?,
    @SerializedName("pct_below")
    val pctBelow: String?,
    @SerializedName("pct_in_range")
    val pctInRange: String?,
    @SerializedName("pct_above")
    val pctAbove: String?,
    @SerializedName("last_refresh")
    val lastRefresh: String?
)

data class AspersionVariableStatsDto(
    @SerializedName("points_count")
    val pointsCount: Int = 0,
    val variables: List<AspersionVariableDto> = emptyList()
)

data class AspersionVariableDto(
    val key: String,
    val label: String,
    val count: Int = 0,
    val mean: Double?,
    val min: Double?,
    val max: Double?,
    val stddev: Double?
)

data class AspersionGeomDto(
    val type: String?,
    val coordinates: List<Double>?
)

/**
 * Los DecimalField de Django llegan como texto. Se conservan como String en el
 * DTO y se convierten de forma segura al persistirlos en Room.
 */
data class AspersionPointDto(
    val id: String,
    @SerializedName("session_header")
    val sessionHeader: String?,
    val plot: String?,
    val geom: AspersionGeomDto?,
    val timestamp: String?,
    @SerializedName("pass_number")
    val passNumber: Int?,
    @SerializedName("elevation_m")
    val elevationM: String?,
    @SerializedName("course_deg")
    val courseDeg: String?,
    @SerializedName("vehicle_heading")
    val vehicleHeading: String?,
    @SerializedName("distance_m")
    val distanceM: String?,
    @SerializedName("duration_s")
    val durationS: Int?,
    @SerializedName("speed_kmh")
    val speedKmh: String?,
    val satellites: Int?,
    @SerializedName("gnss_hdop")
    val gnssHdop: String?,
    @SerializedName("gnss_vdop")
    val gnssVdop: String?,
    @SerializedName("gnss_pdop")
    val gnssPdop: String?,
    @SerializedName("is_diff_active")
    val isDiffActive: Boolean?,
    @SerializedName("diff_mode")
    val diffMode: String?,
    @SerializedName("xte_implement")
    val xteImplement: String?,
    @SerializedName("xte_vehicle")
    val xteVehicle: String?,
    @SerializedName("is_steering_active")
    val isSteeringActive: Boolean?,
    @SerializedName("is_area_counting")
    val isAreaCounting: Boolean?,
    @SerializedName("active_rows")
    val activeRows: Int?,
    @SerializedName("app_status")
    val appStatus: String?,
    @SerializedName("boom_width_m")
    val boomWidthM: String?,
    @SerializedName("liquid_flow_ls")
    val liquidFlowLs: String?,
    @SerializedName("boom_pressure_bar")
    val boomPressureBar: String?,
    @SerializedName("press_ag_kpa")
    val pressAgKpa: String?,
    @SerializedName("press_aux_kpa")
    val pressAuxKpa: String?,
    @SerializedName("production_hah")
    val productionHah: String?,
    @SerializedName("droplet_size")
    val dropletSize: String?,
    @SerializedName("nozzle_color")
    val nozzleColor: String?,
    @SerializedName("nozzle_capacity")
    val nozzleCapacity: String?,
    @SerializedName("nozzle_ref_pressure")
    val nozzleRefPressure: String?,
    @SerializedName("target_rate_l")
    val targetRateL: String?,
    @SerializedName("applied_rate_l")
    val appliedRateL: String?,
    @SerializedName("product_quantity")
    val productQuantity: String?,
    @SerializedName("area_ha")
    val areaHa: String?,
    @SerializedName("rate_quality")
    val rateQuality: String?,
    val evaluation: String?,
    @SerializedName("extra_raw")
    val extraRaw: JsonElement?,
    @SerializedName("created_at")
    val createdAt: String?
)

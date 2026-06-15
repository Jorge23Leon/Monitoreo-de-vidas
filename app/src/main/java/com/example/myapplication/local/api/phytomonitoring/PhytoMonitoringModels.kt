package com.example.myapplication.local.api.phytomonitoring

import com.google.gson.annotations.SerializedName

data class PhytoPaginatedResponse<T>(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)

data class PhytoHeaderApiItem(
    val id: String,
    val plot: String?,
    @SerializedName("field_task")
    val fieldTask: String?,
    val crop: Int?,
    @SerializedName("crop_variety")
    val cropVariety: Int?,
    @SerializedName("assigned_to")
    val assignedTo: String?,
    @SerializedName("estimated_start_date")
    val estimatedStartDate: String?,
    @SerializedName("estimated_end_date")
    val estimatedEndDate: String?,
    val status: String?,
    @SerializedName("started_at")
    val startedAt: String?,
    @SerializedName("finished_at")
    val finishedAt: String?,
    @SerializedName("strict_mode")
    val strictMode: Boolean?,
    @SerializedName("radius_tolerance")
    val radiusTolerance: Double?,
    @SerializedName("additional_notes")
    val additionalNotes: String?,
    @SerializedName("target_points")
    val targetPoints: List<PhytoTargetPointApiItem> = emptyList(),
    @SerializedName("created_at")
    val createdAt: String?
)

data class PhytoTargetPointApiItem(
    val id: String,
    val header: String?,
    val plot: String?,
    val geom: PhytoGeomApi?,
    @SerializedName("radius_m")
    val radiusM: Double?,
    val label: String?,
    val status: String?,
    val origin: String?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class PhytoGeomApi(
    val type: String?,
    val coordinates: List<Double>?
)
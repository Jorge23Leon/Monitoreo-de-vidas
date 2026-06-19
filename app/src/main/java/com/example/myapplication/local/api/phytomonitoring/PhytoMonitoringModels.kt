package com.example.myapplication.local.api.phytomonitoring

import com.google.gson.JsonElement
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



data class PhytoTargetPointCreateRequest(
    val header: String,
    val plot: String,
    val geom: PhytoGeomApi,

    @SerializedName("radius_m")
    val radiusM: Double? = null,

    val label: String? = null,
    val status: String? = null,
    val origin: String? = null
)

data class PhytoCheckpointCreateRequest(
    val header: String,
    val target: String,

    @SerializedName("phyto_issue")
    val phytoIssue: Int,

    val stage: String,

    @SerializedName("presence_status")
    val presenceStatus: String,

    val qty: Int,
    val geom: PhytoGeomApi,
    val notes: String? = null,

    @SerializedName("captured_at")
    val capturedAt: String? = null
)

data class PhytoHeaderPatchRequest(
    val status: String? = null,

    @SerializedName("started_at")
    val startedAt: String? = null,

    @SerializedName("finished_at")
    val finishedAt: String? = null,

    @SerializedName("additional_notes")
    val additionalNotes: String? = null
)

data class PhytoCheckpointApiItem(
    val id: String? = null,
    val header: JsonElement? = null,
    val plot: JsonElement? = null,

    @SerializedName("target_point")
    val targetPoint: JsonElement? = null,

    @SerializedName("target")
    val target: JsonElement? = null,

    val geom: PhytoGeomApi? = null,

    @SerializedName("phyto_issue")
    val phytoIssue: JsonElement? = null,

    @SerializedName("phyto_issue_id")
    val phytoIssueId: JsonElement? = null,

    @SerializedName("phytosanitary")
    val phytosanitary: JsonElement? = null,

    val stage: String? = null,

    @SerializedName("presence_status")
    val presenceStatus: JsonElement? = null,

    val qty: JsonElement? = null,
    val notes: String? = null,

    @SerializedName("captured_at")
    val capturedAt: String? = null,

    @SerializedName("captured_by")
    val capturedBy: JsonElement? = null,

    @SerializedName("captured_by_user")
    val capturedByUser: JsonElement? = null
)

data class PhytoCheckpointImportResponse(
    val created: Int? = null,

    @SerializedName("header_id")
    val headerId: String? = null,

    val detail: String? = null
)

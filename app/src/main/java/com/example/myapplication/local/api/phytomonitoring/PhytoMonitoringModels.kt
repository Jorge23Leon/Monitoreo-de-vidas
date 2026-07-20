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
    val targetPoints: List<PhytoTargetPointApiItem>? = null,
    @SerializedName("created_at")
    val createdAt: String?
)


/**
 * Cuerpo para POST /api/v1/monitoring/phyto/headers/create/.
 *
 * plot_id y field_task_id son UUID remotos. Se mandan los dos explícitamente
 * para que la sesión quede relacionada al Programa remoto y a su parcela.
 */
/**
 * Cuerpo real para POST /api/v1/monitoring/phyto/headers/create/.
 *
 * IMPORTANTE:
 * El backend actual NO recibe "monitoring_date".
 * Su serializer/modelo exige "estimated_start_date" y acepta
 * "estimated_end_date" de forma opcional.
 */
data class PhytoHeaderCreateRequest(
    @SerializedName("plot_id")
    val plotId: String? = null,

    @SerializedName("field_task_id")
    val fieldTaskId: String? = null,

    @SerializedName("estimated_start_date")
    val estimatedStartDate: String,

    @SerializedName("estimated_end_date")
    val estimatedEndDate: String? = null,

    @SerializedName("strict_mode")
    val strictMode: Boolean = true,

    /*
     * En Django el campo es IntegerField, por eso se manda un entero JSON
     * como 15 y no 15.0.
     */
    @SerializedName("radius_tolerance")
    val radiusTolerance: Int = 15
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
    val geom: PhytoGeomApi,

    /*
     * El backend hereda la parcela desde header.plot. Se conserva opcional para
     * compatibilidad con instalaciones antiguas, pero la app ya no la envía.
     */
    val plot: String? = null,

    @SerializedName("radius_m")
    val radiusM: Double? = null,

    val label: String? = null,
    val status: String? = null,
    val origin: String? = null
)

data class PhytoCheckpointCreateRequest(
    val header: String,
    val target: String,

    /*
     * En una captura "Sin plaga" se envía null:
     * el checkpoint conserva ubicación, fecha, qty=0 y presence_status=low.
     */
    @SerializedName("phyto_issue")
    val phytoIssue: Int? = null,

    /*
     * La etapa también es null cuando no existe una plaga/enfermedad detectada.
     */
    val stage: String? = null,

    @SerializedName("presence_status")
    val presenceStatus: String,

    val qty: Int,
    val geom: PhytoGeomApi,
    val notes: String? = null,

    /*
     * Es la llave con la que upload-photos/ empareja el ZIP con el checkpoint.
     * Debe viajar desde Room al crear el checkpoint por JSON.
     */
    @SerializedName("photo_ref")
    val photoRef: String? = null,

    @SerializedName("captured_at")
    val capturedAt: String? = null
)

data class PhytoCheckpointPatchRequest(
    val target: String? = null,

    @SerializedName("photo_ref")
    val photoRef: String? = null
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
    val capturedByUser: JsonElement? = null,

    // Referencia local del archivo que el backend usa para relacionar el ZIP.
    @SerializedName("photo_ref")
    val photoRef: String? = null,

    // Opcionales: el backend puede devolver la URL con cualquiera de estos nombres.
    @SerializedName("photo_url")
    val photoUrl: String? = null,

    @SerializedName("photo")
    val photo: String? = null
)

data class PhytoCheckpointImportResponse(
    val created: Int? = null,

    @SerializedName("header_id")
    val headerId: String? = null,

    val detail: String? = null
)


data class PhytoCheckpointPhotosUploadResponse(
    val matched: Int = 0,

    @SerializedName("unmatched_files")
    val unmatchedFiles: List<String> = emptyList(),

    @SerializedName("checkpoints_without_photo")
    val checkpointsWithoutPhoto: List<String> = emptyList(),

    val detail: String? = null
)

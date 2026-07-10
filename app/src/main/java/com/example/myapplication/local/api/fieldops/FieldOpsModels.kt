package com.example.myapplication.local.api.fieldops

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class FieldOpsPaginatedResponse<T>(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)

/**
 * Cuerpo para POST /api/v1/field_ops/tasks/create/.
 *
 * crop_id es el ID remoto del catálogo de cultivos; el idCrop de Room es local
 * y por eso NO se debe mandar directamente al backend.
 */
data class FieldTaskCreateRequest(
    @SerializedName("master_program")
    val masterProgram: String,

    val plot: String,

    @SerializedName("crop_id")
    val cropId: Int,

    /**
     * En el admin de Django esta columna suele mostrarse como Code.
     * Si el backend lo ignora, no afecta; si lo acepta, evita registros con Code "-".
     */
    @SerializedName("voucher_code")
    val voucherCode: String? = null,

    val title: String? = null,
    val cycle: String? = null,
    val status: String = "pending",

    @SerializedName("est_start_date")
    val estStartDate: String? = null,

    @SerializedName("est_finish_date")
    val estFinishDate: String? = null
)

/**
 * Cuerpo para PATCH /api/v1/field_ops/tasks/{id}/.
 * Se usa para reparar Programas que el backend creó/reutilizó sin título visible.
 */
data class FieldTaskPatchRequest(
    val title: String? = null,

    val cycle: String? = null,

    val status: String? = null,

    @SerializedName("est_start_date")
    val estStartDate: String? = null,

    @SerializedName("est_finish_date")
    val estFinishDate: String? = null,

    @SerializedName("voucher_code")
    val voucherCode: String? = null
)

data class FieldTaskApiItem(
    val id: String,

    @SerializedName("voucher_code")
    val voucherCode: String? = null,

    val title: String? = null,
    val cycle: String? = null,
    val status: String? = null,

    @SerializedName("status_display")
    val statusDisplay: String? = null,

    val crop: FieldCropApiItem? = null,

    @SerializedName("crop_variety")
    val cropVariety: FieldCropApiItem? = null,

    val individual: String? = null,

    @SerializedName("agro_unit")
    val agroUnit: String? = null,

    val plot: String? = null,

    @SerializedName("master_program")
    val masterProgram: String? = null,

    @SerializedName("est_start_date")
    val estStartDate: String? = null,

    @SerializedName("est_finish_date")
    val estFinishDate: String? = null,

    @SerializedName("actual_start_date")
    val actualStartDate: String? = null,

    @SerializedName("actual_finish_date")
    val actualFinishDate: String? = null,

    @SerializedName("location_url")
    val locationUrl: String? = null,

    @SerializedName("attachments_url")
    val attachmentsUrl: JsonElement? = null
)

data class FieldCropApiItem(
    val id: Int? = null,
    val name: String? = null,
    val code: String? = null,
    val variety: String? = null,
    val description: String? = null,
    val photo: String? = null,

    @SerializedName("additional_params")
    val additionalParams: JsonElement? = null,

    @SerializedName("attachments_url")
    val attachmentsUrl: JsonElement? = null
)

data class MasterProgramApiItem(
    val id: String,
    val title: String? = null,
    val code: String? = null,

    @SerializedName("agro_unit")
    val agroUnit: String? = null,

    val status: String? = null,

    @SerializedName("status_display")
    val statusDisplay: String? = null,

    @SerializedName("est_start_date")
    val estStartDate: String? = null,

    @SerializedName("est_finish_date")
    val estFinishDate: String? = null,

    @SerializedName("real_start_date")
    val realStartDate: String? = null,

    @SerializedName("real_finish_date")
    val realFinishDate: String? = null,

    val notes: String? = null
)

data class MasterProgramTreeApiItem(
    val id: String,
    val title: String? = null,
    val code: String? = null,

    @SerializedName("agro_unit")
    val agroUnit: String? = null,

    val status: String? = null,

    @SerializedName("status_display")
    val statusDisplay: String? = null,

    @SerializedName("est_start_date")
    val estStartDate: String? = null,

    @SerializedName("est_finish_date")
    val estFinishDate: String? = null,

    @SerializedName("real_start_date")
    val realStartDate: String? = null,

    @SerializedName("real_finish_date")
    val realFinishDate: String? = null,

    val notes: String? = null,

    val programas: List<FieldTaskTreeApiItem> = emptyList()
)

data class FieldTaskTreeApiItem(
    val id: String,
    val title: String? = null,

    @SerializedName("voucher_code")
    val voucherCode: String? = null,

    val cycle: String? = null,
    val plot: String? = null,
    val status: String? = null,

    @SerializedName("status_display")
    val statusDisplay: String? = null,

    @SerializedName("est_start_date")
    val estStartDate: String? = null,

    @SerializedName("est_finish_date")
    val estFinishDate: String? = null,

    @SerializedName("phyto_monitoring_headers")
    val phytoMonitoringHeaders: List<PhytoMonitoringHeaderTreeApiItem> = emptyList()
)

data class PhytoMonitoringHeaderTreeApiItem(
    val id: String,
    val type: String? = null,

    @SerializedName("session_date")
    val sessionDate: String? = null,

    @SerializedName("import_status")
    val importStatus: String? = null,

    val status: String? = null
)

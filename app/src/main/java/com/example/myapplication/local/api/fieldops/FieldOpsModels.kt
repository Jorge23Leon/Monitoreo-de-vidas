package com.example.myapplication.local.api.fieldops

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class FieldOpsPaginatedResponse<T>(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
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
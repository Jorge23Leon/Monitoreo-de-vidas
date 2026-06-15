package com.example.myapplication.local.api.agrocatalogs

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class AgroCatalogsPaginatedResponse<T>(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)

data class AgroCropApiItem(
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
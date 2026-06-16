package com.example.myapplication.local.api.agrocatalogs

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AgroCatalogsApiService {

    @GET("api/v1/agro-catalogs/crops/")
    suspend fun listarCultivos(
        @Query("page") page: Int? = null
    ): Response<AgroCatalogsPaginatedResponse<AgroCropApiItem>>

    @GET("api/v1/agro-catalogs/phytosanitary/")
    suspend fun listarCatalogoFitosanitario(
        @Query("page") page: Int? = null
    ): Response<JsonElement>
}
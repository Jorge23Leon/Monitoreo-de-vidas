package com.example.myapplication.local.api.geoassets

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GeoAssetsApiService {

    @GET("api/v1/geo_assets/ranches/")
    suspend fun listarRanchos(
        @Query("page") page: Int? = null
    ): Response<JsonElement>

    @GET("api/v1/geo_assets/plots/")
    suspend fun listarParcelas(
        @Query("page") page: Int? = null
    ): Response<JsonElement>
}
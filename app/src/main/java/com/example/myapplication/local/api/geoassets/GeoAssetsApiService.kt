package com.example.myapplication.local.api.geoassets

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET

interface GeoAssetsApiService {

    @GET("api/v1/geo_assets/ranches/")
    suspend fun listarRanchos(): Response<JsonElement>

    @GET("api/v1/geo_assets/plots/")
    suspend fun listarParcelas(): Response<JsonElement>
}
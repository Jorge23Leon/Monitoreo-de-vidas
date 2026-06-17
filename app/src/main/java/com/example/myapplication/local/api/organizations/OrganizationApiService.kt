package com.example.myapplication.local.api.organizations

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OrganizationApiService {

    @GET("api/v1/organizations/data-centrals-main/")
    suspend fun listarCiasPadre(
        @Query("page") page: Int? = null
    ): Response<JsonElement>

    @GET("api/v1/organizations/datacentrals/")
    suspend fun listarDataCentrals(
        @Query("page") page: Int? = null
    ): Response<JsonElement>

    @GET("api/v1/organizations/")
    suspend fun listarUnidadesAgroeconomicas(
        @Query("page") page: Int? = null
    ): Response<JsonElement>

    @GET("api/v1/organizations/datacentrals-assignments/")
    suspend fun listarAsignacionesDataCentralAgroUnit(
        @Query("page") page: Int? = null
    ): Response<JsonElement>
}
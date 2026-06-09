package com.example.myapplication.local.api.organizations

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET

interface OrganizationApiService {

    @GET("api/v1/organizations/data-centrals-main/")
    suspend fun listarCiasPadre(): Response<JsonElement>

    @GET("api/v1/organizations/datacentrals/")
    suspend fun listarDataCentrals(): Response<JsonElement>

    @GET("api/v1/organizations/")
    suspend fun listarUnidadesAgroeconomicas(): Response<JsonElement>

    @GET("api/v1/organizations/datacentrals-assignments/")
    suspend fun listarAsignacionesDataCentralAgroUnit(): Response<JsonElement>
}
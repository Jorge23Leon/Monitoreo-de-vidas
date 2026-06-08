package com.example.myapplication.local.api.organizations

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET

interface OrganizationApiService {

    @GET("api/v1/organizations/datacentrals/")
    suspend fun listarDataCentrals(): Response<JsonElement>
}
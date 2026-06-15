package com.example.myapplication.local.api.fieldops

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface FieldOpsApiService {

    @GET("api/v1/field_ops/tasks/")
    suspend fun listarProgramasCampo(
        @Query("datacentral") datacentral: String? = null,
        @Query("master_program") masterProgram: String? = null,
        @Query("plot") plot: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null
    ): Response<FieldOpsPaginatedResponse<FieldTaskApiItem>>
}
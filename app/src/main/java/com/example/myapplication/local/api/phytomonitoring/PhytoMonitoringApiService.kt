package com.example.myapplication.local.api.phytomonitoring

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Path

interface PhytoMonitoringApiService {

    @GET("api/v1/monitoring/phyto/headers/")
    suspend fun listarHeaders(
        @Query("assigned_to") assignedTo: String? = null,
        @Query("estimated_start_date") estimatedStartDate: String? = null,
        @Query("field_task") fieldTask: String? = null,
        @Query("plot") plot: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null
    ): Response<PhytoPaginatedResponse<PhytoHeaderApiItem>>

    @GET("api/v1/monitoring/phyto/target-points/")
    suspend fun listarTargetPoints(
        @Query("page") page: Int? = null
    ): Response<PhytoPaginatedResponse<PhytoTargetPointApiItem>>

    @PATCH("api/v1/monitoring/phyto/headers/{id}/update/")
    suspend fun actualizarHeader(
        @Path("id") id: String,
        @Body body: PhytoHeaderPatchRequest
    ): Response<PhytoHeaderApiItem>
}
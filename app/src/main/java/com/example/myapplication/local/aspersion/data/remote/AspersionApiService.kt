package com.example.myapplication.local.aspersion.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AspersionApiService {

    @GET("api/v1/monitoring/aspersion/headers/")
    suspend fun listarSesiones(
        @Query("program") program: String? = null,
        @Query("plot") plot: String? = null,
        @Query("data_central") dataCentral: String? = null,
        @Query("producer") producer: String? = null,
        @Query("ranch") ranch: String? = null,
        @Query("assigned_to") assignedTo: String? = null,
        @Query("status") status: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("import_status") importStatus: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100
    ): Response<AspersionPaginatedResponse<AspersionSessionDto>>

    @GET("api/v1/monitoring/aspersion/headers/{id}/")
    suspend fun obtenerSesion(
        @Path("id") id: String
    ): Response<AspersionSessionDto>

    @GET("api/v1/monitoring/aspersion/headers/{id}/stats/")
    suspend fun obtenerEstadisticas(
        @Path("id") id: String
    ): Response<AspersionStatsDto>

    @GET("api/v1/monitoring/aspersion/headers/{id}/variable-stats/")
    suspend fun obtenerEstadisticasVariables(
        @Path("id") id: String
    ): Response<AspersionVariableStatsDto>

    @GET("api/v1/monitoring/aspersion/points/")
    suspend fun listarPuntos(
        @Query("session_header") sessionHeader: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 2_000
    ): Response<AspersionPaginatedResponse<AspersionPointDto>>
}

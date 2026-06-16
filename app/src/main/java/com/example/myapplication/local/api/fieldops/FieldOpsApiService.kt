package com.example.myapplication.local.api.fieldops

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

interface FieldOpsApiService {

    @GET("api/v1/field_ops/tasks/")
    suspend fun listarProgramasCampo(
        @Query("datacentral") datacentral: String? = null,
        @Query("master_program") masterProgram: String? = null,
        @Query("plot") plot: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null
    ): Response<FieldOpsPaginatedResponse<FieldTaskApiItem>>
    @GET("api/v1/field_ops/master-programs/{id}/tree/")

    suspend fun obtenerArbolProgramaMaestro(
        @Path("id") id: String
    ): Response<MasterProgramTreeApiItem>

    @GET("api/v1/field_ops/master-programs/")
    suspend fun listarProgramasMaestros(
        @Query("datacentral") datacentral: String? = null,
        @Query("agro_unit") agroUnit: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null
    ): Response<FieldOpsPaginatedResponse<MasterProgramApiItem>>
}
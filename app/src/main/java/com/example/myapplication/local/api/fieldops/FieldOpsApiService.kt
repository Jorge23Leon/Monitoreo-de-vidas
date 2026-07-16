package com.example.myapplication.local.api.fieldops

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
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

    /**
     * Crea un Programa remoto bajo un MasterProgram.
     *
     * Importante:
     * - El backend espera crop_id, no crop.
     * - master_program y plot son UUID remotos.
     */
    @POST("api/v1/field_ops/tasks/create/")
    suspend fun crearProgramaCampo(
        @Body body: FieldTaskCreateRequest
    ): Response<FieldTaskApiItem>

    @PATCH("api/v1/field_ops/tasks/{id}/")
    suspend fun actualizarProgramaCampo(
        @Path("id") id: String,
        @Body body: FieldTaskPatchRequest
    ): Response<FieldTaskApiItem>

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

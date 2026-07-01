package com.example.myapplication.local.api.phytomonitoring

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("api/v1/monitoring/phyto/headers/{id}/")
    suspend fun obtenerHeaderDetalle(
        @Path("id") id: String
    ): Response<PhytoHeaderApiItem>

    @GET("api/v1/monitoring/phyto/target-points/")
    suspend fun listarTargetPoints(
        @Query("page") page: Int? = null
    ): Response<PhytoPaginatedResponse<PhytoTargetPointApiItem>>

    @GET("api/v1/monitoring/phyto/checkpoints/")
    suspend fun listarCheckpoints(
        @Query("header") header: String? = null,
        @Query("page") page: Int? = null
    ): Response<PhytoPaginatedResponse<PhytoCheckpointApiItem>>

    @POST("api/v1/monitoring/phyto/target-points/create/")
    suspend fun crearTargetPoint(
        @Body body: PhytoTargetPointCreateRequest
    ): Response<PhytoTargetPointApiItem>

    @POST("api/v1/monitoring/phyto/checkpoints/create/")
    suspend fun crearCheckpoint(
        @Body body: PhytoCheckpointCreateRequest
    ): Response<PhytoCheckpointApiItem>

    @PATCH("api/v1/monitoring/phyto/checkpoints/{id}/update/")
    suspend fun actualizarCheckpoint(
        @Path("id") id: String,
        @Body body: PhytoCheckpointPatchRequest
    ): Response<PhytoCheckpointApiItem>

    /**
     * Sube una foto directamente al campo `photo` de un checkpoint ya creado.
     * No usa photo_ref ni ZIP.
     */
    @Multipart
    @PATCH("api/v1/monitoring/phyto/checkpoints/{id}/update/")
    suspend fun subirFotoCheckpoint(
        @Path("id") id: String,
        @Part photo: MultipartBody.Part
    ): Response<PhytoCheckpointApiItem>

    @Multipart
    @POST("api/v1/monitoring/phyto/checkpoints/import/")
    suspend fun importarCheckpointsCsv(
        @Part("header") header: RequestBody,
        @Part csv_file: MultipartBody.Part
    ): Response<PhytoCheckpointImportResponse>

    /*
     * Déjalo por ahora, pero ya no se usará para las fotos nuevas.
     * La sincronización nueva utiliza subirFotoCheckpoint().
     */
    @Multipart
    @POST("api/v1/monitoring/phyto/checkpoints/upload-photos/")
    suspend fun subirFotosCheckpointsZip(
        @Part("header") header: RequestBody,
        @Part photosZip: MultipartBody.Part
    ): Response<PhytoCheckpointPhotosUploadResponse>

    @PATCH("api/v1/monitoring/phyto/headers/{id}/update/")
    suspend fun actualizarHeader(
        @Path("id") id: String,
        @Body body: PhytoHeaderPatchRequest
    ): Response<PhytoHeaderApiItem>
}
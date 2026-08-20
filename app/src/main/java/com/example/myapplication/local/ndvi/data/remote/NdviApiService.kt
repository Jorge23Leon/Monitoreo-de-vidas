package com.example.myapplication.local.ndvi.data.remote

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query


/**
 * Endpoints utilizados por el módulo NDVI.
 */
interface NdviApiService {


    // =========================================================
    // SESIONES
    // =========================================================

    @GET("api/v1/monitoring/ndvi/headers/")
    suspend fun listarSesiones(
        @Query("program")
        program: String? = null,

        @Query("plot")
        plot: String? = null,

        @Query("page")
        page: Int = 1,

        @Query("page_size")
        pageSize: Int = 100
    ): Response<NdviPaginatedResponse<NdviSessionDto>>


    @GET("api/v1/monitoring/ndvi/headers/{id}/")
    suspend fun obtenerSesion(
        @Path("id")
        id: String
    ): Response<NdviSessionDto>


    // =========================================================
    // PUNTOS
    // =========================================================

    @GET("api/v1/monitoring/ndvi/points/")
    suspend fun listarPuntos(
        @Query("session_header")
        sessionHeader: String,

        @Query("page")
        page: Int = 1,

        @Query("page_size")
        pageSize: Int = 2_000
    ): Response<NdviPaginatedResponse<NdviPointDto>>


    // =========================================================
    // ESTADÍSTICAS
    // =========================================================

    /**
     * Estadísticas calculadas por el backend.
     *
     * Incluye:
     * - count
     * - mean
     * - min
     * - max
     * - stddev
     */
    @GET(
        "api/v1/monitoring/ndvi/headers/{id}/variable-stats/"
    )
    suspend fun obtenerEstadisticasVariables(
        @Path("id")
        id: String
    ): Response<NdviVariableStatsDto>


    // =========================================================
    // CONTORNOS - ÍNDICES DISPONIBLES
    // =========================================================

    /**
     * Pregunta al backend cuáles índices ya tienen
     * contornos disponibles.
     *
     * Ejemplo:
     *
     * {
     *   "header_id": "...",
     *   "contour_status": "done",
     *   "indices": [
     *      "ndvi",
     *      "gndvi",
     *      "ndre"
     *   ]
     * }
     */
    @GET(
        "api/v1/monitoring/ndvi/headers/{id}/contours/indices/"
    )
    suspend fun obtenerIndicesContorno(
        @Path("id")
        sessionId: String
    ): Response<NdviContourIndicesDto>


    // =========================================================
    // CONTORNOS - GEOJSON
    // =========================================================

    /**
     * Descarga los polígonos/clases de un índice.
     *
     * Ejemplo:
     *
     * index = "ndvi"
     *
     * El resultado es GeoJSON.
     *
     * IMPORTANTE:
     * el servidor también puede responder HTTP 202
     * mientras genera el contorno.
     */
    @GET(
        "api/v1/monitoring/ndvi/headers/{id}/contours/"
    )
    suspend fun obtenerContornos(
        @Path("id")
        sessionId: String,

        @Query("index")
        index: String,

        /**
         * Algunas consultas pueden necesitar el
         * Data Central/CIA.
         *
         * Lo dejamos nullable porque el backend
         * permite llamadas donde no es necesario
         * enviarlo explícitamente.
         */
        @Query("dc")
        dataCentralId: String? = null
    ): Response<JsonElement>


    // =========================================================
    // CONFIGURACIÓN DE VARIABLES
    // =========================================================

    @GET(
        "api/v1/monitoring/ndvi/headers/{id}/variable-config/"
    )
    suspend fun obtenerConfiguracionVariables(
        @Path("id")
        sessionId: String,

        @Query("tenant")
        tenantId: String? = null,

        @Query("dc")
        dataCentralId: String? = null
    ): Response<JsonElement>
}
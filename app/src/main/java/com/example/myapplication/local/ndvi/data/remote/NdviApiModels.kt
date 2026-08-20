package com.example.myapplication.local.ndvi.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName


/**
 * Respuesta paginada estándar de Django REST Framework.
 *
 * Ejemplo:
 *
 * {
 *   "count": 2500,
 *   "next": "...",
 *   "previous": null,
 *   "results": [...]
 * }
 */
data class NdviPaginatedResponse<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)


/**
 * Usuario asignado a una sesión NDVI.
 */
data class NdviAssignedUserDto(
    val id: String? = null,
    val username: String? = null
)


/**
 * Geometría GeoJSON de un punto NDVI.
 *
 * GeoJSON utiliza:
 *
 * coordinates = [longitud, latitud]
 *
 * Ejemplo:
 *
 * [-101.4710, 20.8871]
 */
data class NdviGeomDto(
    val type: String? = null,
    val coordinates: List<Double> = emptyList()
)


/**
 * Encabezado de una sesión NDVI.
 *
 * Representa una carga/sesión NDVI perteneciente
 * a un Programa y una Parcela.
 */
data class NdviSessionDto(

    /**
     * UUID de la sesión NDVI.
     */
    val id: String,

    /**
     * UUID del programa.
     */
    val program: String? = null,

    /**
     * También puede venir program_id desde algunos
     * contratos/endpoints.
     */
    @SerializedName("program_id")
    val programId: String? = null,

    /**
     * UUID de la parcela.
     */
    val plot: String? = null,

    /**
     * Fecha correspondiente a la imagen NDVI.
     *
     * Ejemplo:
     * 2026-08-01
     */
    @SerializedName("session_date")
    val sessionDate: String? = null,

    @SerializedName("est_start_date")
    val estStartDate: String? = null,

    @SerializedName("est_finish_date")
    val estFinishDate: String? = null,

    @SerializedName("act_start_date")
    val actStartDate: String? = null,

    @SerializedName("act_finish_date")
    val actFinishDate: String? = null,

    /**
     * Estados:
     *
     * pending
     * in_progress
     * loaded
     * completed
     * cancelled
     */
    val status: String? = null,

    @SerializedName("assigned_to")
    val assignedTo: NdviAssignedUserDto? = null,

    @SerializedName("assigned_to_id")
    val assignedToId: String? = null,

    /**
     * Estado de importación del archivo NDVI.
     */
    @SerializedName("import_status")
    val importStatus: String? = null,

    /**
     * Los errores pueden venir como objeto/lista/null,
     * por eso se conserva como JsonElement.
     */
    @SerializedName("import_errors")
    val importErrors: JsonElement? = null,

    @SerializedName("imported_at")
    val importedAt: String? = null,

    /**
     * El OpenAPI actual lo expone serializado,
     * así que por ahora lo recibimos como String.
     *
     * Después lo convertiremos a Int de forma segura.
     */
    @SerializedName("points_count")
    val pointsCount: String? = null,

    /**
     * El frontend consulta este dato en el detalle
     * mientras Celery genera los contornos.
     *
     * Puede no venir en todos los listados.
     */
    @SerializedName("contour_status")
    val contourStatus: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
)


/**
 * Punto georreferenciado perteneciente
 * a una sesión NDVI.
 */
data class NdviPointDto(

    val id: String,

    /**
     * UUID de la sesión padre.
     */
    @SerializedName("session_header")
    val sessionHeader: String,

    /**
     * Coordenadas GeoJSON.
     */
    val geom: NdviGeomDto? = null,

    /**
     * ID original proveniente del proveedor.
     */
    @SerializedName("obj_id")
    val objId: Int? = null,

    /**
     * Información de origen del archivo.
     */
    @SerializedName("lot_label")
    val lotLabel: String? = null,

    @SerializedName("dataset_date")
    val datasetDate: String? = null,

    val product: String? = null,

    // =====================================================
    // ÍNDICES VEGETATIVOS
    // =====================================================

    val ndvi: Double? = null,

    @SerializedName("nir_vigor")
    val nirVigor: Double? = null,

    val osavi: Double? = null,

    val vari: Double? = null,

    @SerializedName("bare_soil_index")
    val bareSoilIndex: Double? = null,

    // =====================================================
    // CANALES DE IMAGEN
    // =====================================================

    @SerializedName("image_red")
    val imageRed: Double? = null,

    @SerializedName("image_green")
    val imageGreen: Double? = null,

    @SerializedName("image_blue")
    val imageBlue: Double? = null,

    // =====================================================
    // ÍNDICES ADICIONALES
    // =====================================================

    @SerializedName("red_edge")
    val redEdge: Double? = null,

    val swir: Double? = null,

    val ndre: Double? = null,

    val msavi2: Double? = null,

    val gndvi: Double? = null,

    val ndmi: Double? = null,

    val psri: Double? = null,

    @SerializedName("created_at")
    val createdAt: String? = null
)


/**
 * Estadística de una sola variable.
 *
 * Ejemplo:
 *
 * NDVI
 * count  = 3500
 * mean   = 0.65
 * min    = 0.12
 * max    = 0.91
 * stddev = 0.08
 */
data class NdviVariableStatDto(

    val key: String,

    val label: String,

    val count: Int = 0,

    val mean: Double? = null,

    val min: Double? = null,

    val max: Double? = null,

    val stddev: Double? = null
)


/**
 * Resumen estadístico de los índices de
 * una sesión NDVI.
 */
data class NdviVariableStatsDto(

    @SerializedName("header_id")
    val headerId: String,

    @SerializedName("points_count")
    val pointsCount: Int = 0,

    val variables: List<NdviVariableStatDto> = emptyList()
)


/**
 * Indica cuáles índices ya tienen
 * contornos generados por el backend.
 *
 * Ejemplo:
 *
 * {
 *   "header_id": "...",
 *   "contour_status": "done",
 *   "indices": ["ndvi", "ndre", "gndvi"]
 * }
 */
data class NdviContourIndicesDto(

    @SerializedName("header_id")
    val headerId: String,

    @SerializedName("contour_status")
    val contourStatus: String? = null,

    val indices: List<String> = emptyList()
)
package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


/**
 * Punto georreferenciado perteneciente a una sesión NDVI.
 *
 * Las coordenadas GeoJSON se transforman al guardar:
 *
 * coordinates[0] -> longitude
 * coordinates[1] -> latitude
 */
@Entity(
    tableName = "local_ndvi_points",
    foreignKeys = [
        ForeignKey(
            entity = LocalNdviSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["obj_id"]),
        Index(value = ["dataset_date"])
    ]
)
data class LocalNdviPointEntity(

    /**
     * UUID original del punto en Django.
     */
    @PrimaryKey
    @ColumnInfo(name = "point_id")
    val pointId: String,

    /**
     * Sesión a la que pertenece.
     */
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "geom_type")
    val geomType: String? = null,

    /**
     * GeoJSON:
     *
     * coordinates[0]
     */
    val longitude: Double,

    /**
     * GeoJSON:
     *
     * coordinates[1]
     */
    val latitude: Double,

    @ColumnInfo(name = "obj_id")
    val objId: Int? = null,

    @ColumnInfo(name = "lot_label")
    val lotLabel: String? = null,

    @ColumnInfo(name = "dataset_date")
    val datasetDate: String? = null,

    val product: String? = null,

    // =====================================================
    // ÍNDICES VEGETATIVOS
    // =====================================================

    val ndvi: Double? = null,

    @ColumnInfo(name = "nir_vigor")
    val nirVigor: Double? = null,

    val osavi: Double? = null,

    val vari: Double? = null,

    @ColumnInfo(name = "bare_soil_index")
    val bareSoilIndex: Double? = null,

    // =====================================================
    // CANALES DE IMAGEN
    // =====================================================

    @ColumnInfo(name = "image_red")
    val imageRed: Double? = null,

    @ColumnInfo(name = "image_green")
    val imageGreen: Double? = null,

    @ColumnInfo(name = "image_blue")
    val imageBlue: Double? = null,

    // =====================================================
    // ÍNDICES ADICIONALES
    // =====================================================

    @ColumnInfo(name = "red_edge")
    val redEdge: Double? = null,

    val swir: Double? = null,

    val ndre: Double? = null,

    val msavi2: Double? = null,

    val gndvi: Double? = null,

    val ndmi: Double? = null,

    val psri: Double? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: String? = null
)
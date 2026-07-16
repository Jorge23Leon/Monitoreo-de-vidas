package com.example.myapplication.local.api.geoassets

import com.google.gson.annotations.SerializedName

data class GeoPlotsResponse(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: GeoFeatureCollection? = null
)

data class GeoFeatureCollection(
    val type: String? = null,
    val features: List<GeoPlotFeature> = emptyList()
)

data class GeoPlotFeature(
    val id: String,
    val type: String? = null,
    val geometry: GeoPolygonGeometry? = null,
    val properties: GeoPlotProperties? = null
)

data class GeoPolygonGeometry(
    val type: String? = null,
    val coordinates: List<List<List<Double>>>? = null
)

data class GeoPlotProperties(
    val code: String? = null,
    val description: String? = null,
    val ranch: String? = null,
    val status: String? = null,
    val slug: String? = null,

    @SerializedName("total_area")
    val totalArea: String? = null,

    val comments: String? = null
)
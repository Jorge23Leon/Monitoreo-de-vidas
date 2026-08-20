package com.example.myapplication.local.ndvi.model

/**
 * Índices vegetativos que podrá visualizar el módulo NDVI.
 *
 * apiKey:
 * nombre exacto que utiliza el backend.
 *
 * label:
 * nombre amigable que verá el usuario en la app.
 */
enum class NdviIndex(
    val apiKey: String,
    val label: String
) {

    NDVI(
        apiKey = "ndvi",
        label = "NDVI"
    ),

    NIR_VIGOR(
        apiKey = "nir_vigor",
        label = "Vigor NIR"
    ),

    OSAVI(
        apiKey = "osavi",
        label = "OSAVI"
    ),

    VARI(
        apiKey = "vari",
        label = "VARI"
    ),

    BARE_SOIL_INDEX(
        apiKey = "bare_soil_index",
        label = "Suelo desnudo"
    ),

    RED_EDGE(
        apiKey = "red_edge",
        label = "Límite rojo"
    ),

    SWIR(
        apiKey = "swir",
        label = "SWIR"
    ),

    NDRE(
        apiKey = "ndre",
        label = "NDRE"
    ),

    MSAVI2(
        apiKey = "msavi2",
        label = "MSAVI2"
    ),

    GNDVI(
        apiKey = "gndvi",
        label = "GNDVI"
    ),

    NDMI(
        apiKey = "ndmi",
        label = "NDMI"
    ),

    PSRI(
        apiKey = "psri",
        label = "PSRI"
    );

    companion object {

        /**
         * Convierte el nombre recibido por API a nuestro enum.
         *
         * Ejemplo:
         * "ndvi" -> NdviIndex.NDVI
         * "gndvi" -> NdviIndex.GNDVI
         */
        fun fromApiKey(value: String?): NdviIndex? {
            val cleanValue = value
                ?.trim()
                ?.lowercase()
                ?: return null

            return entries.firstOrNull { index ->
                index.apiKey == cleanValue
            }
        }
    }
}
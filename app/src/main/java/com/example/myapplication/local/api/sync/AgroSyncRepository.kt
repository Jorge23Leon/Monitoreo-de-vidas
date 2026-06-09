package com.example.myapplication.local.api.sync

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient
import com.example.myapplication.local.api.geoassets.GeoAssetsApiService
import com.example.myapplication.local.api.organizations.OrganizationApiService
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaAgroUnitCrossRef
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale

class AgroSyncRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val organizationApi: OrganizationApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = OrganizationApiService::class.java
        )

    private val geoAssetsApi: GeoAssetsApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = GeoAssetsApiService::class.java
        )

    suspend fun sincronizarProductoresRanchosParcelas(
        idLocalCia: Long
    ): ResultadoAgroSync {
        return try {
            val ciaLocal = database.localCiaDao().getCiaById(idLocalCia)
                ?: return ResultadoAgroSync.Error("No se encontró la CIA local seleccionada")

            val ciaExtId = ciaLocal.extId

            if (ciaExtId.isNullOrBlank()) {
                return ResultadoAgroSync.Error(
                    "La CIA seleccionada no tiene extId. No se puede filtrar contra producción."
                )
            }

            val asignacionesResponse = organizationApi.listarAsignacionesDataCentralAgroUnit()

            if (!asignacionesResponse.isSuccessful) {
                return ResultadoAgroSync.Exito(
                    productores = 0,
                    ranchos = 0,
                    parcelas = 0
                )
            }

            val asignacionesJson = extraerLista(asignacionesResponse.body())

            val productoresPermitidosExtId = asignacionesJson.mapNotNull { item ->
                val dataCentralExtId = item.relacionIdOrNull(
                    "datacentral",
                    "datacentral_id",
                    "data_central",
                    "data_central_id",
                    "dataCentral",
                    "dataCentralId"
                )

                val agroUnitExtId = item.relacionIdOrNull(
                    "agro_unit",
                    "agroUnit",
                    "agro_unit_id",
                    "agroUnitId",
                    "organization",
                    "organization_id"
                )

                if (dataCentralExtId == ciaExtId && !agroUnitExtId.isNullOrBlank()) {
                    agroUnitExtId
                } else {
                    null
                }
            }.toSet()

            if (productoresPermitidosExtId.isEmpty()) {
                return ResultadoAgroSync.Exito(
                    productores = 0,
                    ranchos = 0,
                    parcelas = 0
                )
            }

            val productoresResponse = organizationApi.listarUnidadesAgroeconomicas()

            if (!productoresResponse.isSuccessful) {
                return ResultadoAgroSync.Error(
                    "Error productores: ${productoresResponse.code()} ${
                        productoresResponse.errorBody()?.string() ?: productoresResponse.message()
                    }"
                )
            }

            val ranchosResponse = geoAssetsApi.listarRanchos()

            if (!ranchosResponse.isSuccessful) {
                return ResultadoAgroSync.Error(
                    "Error ranchos: ${ranchosResponse.code()} ${
                        ranchosResponse.errorBody()?.string() ?: ranchosResponse.message()
                    }"
                )
            }

            val parcelasResponse = geoAssetsApi.listarParcelas()

            if (!parcelasResponse.isSuccessful) {
                return ResultadoAgroSync.Error(
                    "Error parcelas: ${parcelasResponse.code()} ${
                        parcelasResponse.errorBody()?.string() ?: parcelasResponse.message()
                    }"
                )
            }

            val productoresJson = extraerLista(productoresResponse.body())
            val ranchosJson = extraerLista(ranchosResponse.body())
            val parcelasJson = extraerLista(parcelasResponse.body())

            var productoresGuardados = 0
            var ranchosGuardados = 0
            var parcelasGuardadas = 0

            val productoresLocalesPorExtId = mutableMapOf<String, Long>()
            val ranchosLocalesPorExtId = mutableMapOf<String, Long>()

            productoresJson.forEach { item ->
                if (!esProductor(item)) return@forEach

                val extId = item.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                ) ?: return@forEach

                if (extId !in productoresPermitidosExtId) {
                    return@forEach
                }

                val nombre = item.stringOrNull(
                    "commercial_name",
                    "commercialName",
                    "name",
                    "business_name",
                    "legal_name",
                    "razon_social",
                    "razonSocial",
                    "slug"
                ) ?: return@forEach

                val slug = item.stringOrNull("slug") ?: crearSlug(nombre)

                val existente = database.localAgroUnitDao()
                    .getAllAgroUnits()
                    .firstOrNull { agro ->
                        agro.ext_Id == extId
                    } ?: database.localAgroUnitDao().getAgroUnitBySlug(slug)

                val idProductorLocal = if (existente != null) {
                    database.localAgroUnitDao().updateAgroUnit(
                        existente.copy(
                            ext_Id = extId,
                            commercial_name = nombre,
                            slug = slug
                        )
                    )

                    existente.idLocalAgroUnit
                } else {
                    database.localAgroUnitDao().insertAgroUnit(
                        LocalAgroUnitEntity(
                            ext_Id = extId,
                            commercial_name = nombre,
                            slug = slug
                        )
                    )
                }

                productoresLocalesPorExtId[extId] = idProductorLocal

                database.localCiaAgroUnitDao().asignarProductorACia(
                    LocalCiaAgroUnitCrossRef(
                        idLocalCia = idLocalCia,
                        idLocalAgroUnit = idProductorLocal,
                        extId = "cia_${idLocalCia}_agro_$extId"
                    )
                )

                productoresGuardados++
            }

            ranchosJson.forEach { item ->
                val extId = item.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                ) ?: return@forEach

                val productorExtId = item.relacionIdOrNull(
                    "agro_unit",
                    "agroUnit",
                    "agro_unit_id",
                    "agroUnitId",
                    "organization",
                    "organization_id",
                    "producer",
                    "producer_id",
                    "owner",
                    "owner_id"
                ) ?: return@forEach

                if (productorExtId !in productoresPermitidosExtId) {
                    return@forEach
                }

                val idProductorLocal = productoresLocalesPorExtId[productorExtId]
                    ?: return@forEach

                val nombre = item.stringOrNull(
                    "name",
                    "nombre",
                    "ranch_name",
                    "ranchName"
                ) ?: "Rancho"

                val code = item.stringOrNull(
                    "code",
                    "codigo",
                    "slug"
                ) ?: crearSlug(nombre)

                val lat = item.doubleOrNull("lat", "latitude")
                val lon = item.doubleOrNull("lon", "lng", "longitude")

                val existente = database.localRanchDao()
                    .getAllRanches()
                    .firstOrNull { rancho ->
                        rancho.extId == extId ||
                                (
                                        rancho.idLocalAgroUnit == idProductorLocal &&
                                                rancho.code == code
                                        )
                    }

                val idRanchoLocal = if (existente != null) {
                    database.localRanchDao().updateRanch(
                        existente.copy(
                            extId = extId,
                            name = nombre,
                            code = code,
                            lat = lat,
                            lon = lon,
                            idLocalAgroUnit = idProductorLocal
                        )
                    )

                    existente.idLocalRanch
                } else {
                    database.localRanchDao().insertRanch(
                        LocalRanchEntity(
                            extId = extId,
                            name = nombre,
                            code = code,
                            lat = lat,
                            lon = lon,
                            idLocalAgroUnit = idProductorLocal
                        )
                    )
                }

                ranchosLocalesPorExtId[extId] = idRanchoLocal
                ranchosGuardados++
            }

            parcelasJson.forEach { item ->
                val extId = item.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                ) ?: return@forEach

                val ranchoExtId = item.relacionIdOrNull(
                    "ranch",
                    "ranch_id",
                    "ranchId",
                    "local_ranch",
                    "localRanch",
                    "farm",
                    "farm_id"
                ) ?: return@forEach

                val idRanchoLocal = ranchosLocalesPorExtId[ranchoExtId]
                    ?: return@forEach

                val nombre = item.stringOrNull(
                    "name",
                    "nombre",
                    "plot_name",
                    "plotName"
                ) ?: "Parcela"

                val code = item.stringOrNull(
                    "code",
                    "codigo",
                    "slug"
                ) ?: crearSlug(nombre)

                val lat = item.doubleOrNull("lat", "latitude")
                val lon = item.doubleOrNull("lon", "lng", "longitude")

                val existente = database.localPlotDao()
                    .getAllPlots()
                    .firstOrNull { parcela ->
                        parcela.extId == extId ||
                                (
                                        parcela.idLocalRanch == idRanchoLocal &&
                                                parcela.code == code
                                        )
                    }

                if (existente != null) {
                    database.localPlotDao().updatePlot(
                        existente.copy(
                            extId = extId,
                            name = nombre,
                            code = code,
                            lat = lat,
                            lon = lon,
                            idLocalRanch = idRanchoLocal
                        )
                    )
                } else {
                    database.localPlotDao().insertPlot(
                        LocalPlotEntity(
                            extId = extId,
                            name = nombre,
                            code = code,
                            lat = lat,
                            lon = lon,
                            idLocalRanch = idRanchoLocal
                        )
                    )
                }

                parcelasGuardadas++
            }

            ResultadoAgroSync.Exito(
                productores = productoresGuardados,
                ranchos = ranchosGuardados,
                parcelas = parcelasGuardadas
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ResultadoAgroSync.Error("Error sincronizando filtros: ${e.message}")
        }
    }
    private fun normalizarItemApi(element: JsonElement): JsonObject? {
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject

        val properties = obj.getOrNull("properties")

        if (properties != null && properties.isJsonObject) {
            val salida = JsonObject()

            properties.asJsonObject.entrySet().forEach { entry ->
                salida.add(entry.key, entry.value)
            }

            obj.getOrNull("id")?.let { id ->
                if (!salida.has("id")) {
                    salida.add("id", id)
                }
            }

            val geometry = obj.getOrNull("geometry")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject

            val tipoGeometria = geometry
                ?.stringOrNull("type")
                ?.normalizarTexto()

            val coordinates = geometry?.getOrNull("coordinates")

            if (
                tipoGeometria == "point" &&
                coordinates != null &&
                coordinates.isJsonArray &&
                coordinates.asJsonArray.size() >= 2
            ) {
                val coords = coordinates.asJsonArray

                if (!salida.has("lon")) {
                    salida.addProperty("lon", coords[0].asDouble)
                }

                if (!salida.has("lat")) {
                    salida.addProperty("lat", coords[1].asDouble)
                }
            }

            return salida
        }

        return obj
    }

    private fun JsonObject.arrayOrNull(key: String): JsonArray? {
        val value = getOrNull(key) ?: return null

        return if (value.isJsonArray) {
            value.asJsonArray
        } else {
            null
        }
    }

    private fun extraerLista(root: JsonElement?): List<JsonObject> {
        if (root == null || root.isJsonNull) return emptyList()

        return when {
            root.isJsonArray -> {
                root.asJsonArray.mapNotNull { element ->
                    normalizarItemApi(element)
                }
            }

            root.isJsonObject -> {
                val obj = root.asJsonObject

                val featuresDirectos = obj.arrayOrNull("features")
                if (featuresDirectos != null) {
                    return featuresDirectos.mapNotNull { element ->
                        normalizarItemApi(element)
                    }
                }

                val posiblesContenedores = listOf("results", "data", "items")

                for (key in posiblesContenedores) {
                    val value = obj.getOrNull(key) ?: continue
                    val lista = extraerLista(value)

                    if (lista.isNotEmpty()) {
                        return lista
                    }
                }

                normalizarItemApi(obj)?.let { listOf(it) } ?: emptyList()
            }

            else -> emptyList()
        }
    }
    private fun esProductor(item: JsonObject): Boolean {
        val tipo = item.textoTipoOrNull(
            "type",
            "tipo",
            "agro_unit_type",
            "agroUnitType",
            "category",
            "categoria"
        )?.normalizarTexto().orEmpty()

        if (tipo.isBlank()) return true

        return tipo.contains("productor") ||
                tipo.contains("producer") ||
                tipo.contains("agrounit") ||
                tipo.contains("agro unit")
    }

    private fun perteneceACiaSiTieneRelacion(
        item: JsonObject,
        ciaExtId: String?
    ): Boolean {
        if (ciaExtId.isNullOrBlank()) return true

        val encontrados = mutableListOf<String>()

        item.relacionIdOrNull(
            "datacentral",
            "datacentral_id",
            "data_central",
            "data_central_id",
            "dataCentral",
            "dataCentralId",
            "cia",
            "cia_id"
        )?.let { encontrados.add(it) }

        val arrays = listOf(
            "datacentrals",
            "data_centrals",
            "cias"
        )

        arrays.forEach { key ->
            val value = item.getOrNull(key)

            if (value != null && value.isJsonArray) {
                value.asJsonArray.forEach { element ->
                    when {
                        element.isJsonPrimitive -> {
                            encontrados.add(element.asString)
                        }

                        element.isJsonObject -> {
                            element.asJsonObject.stringOrNull(
                                "id",
                                "uuid",
                                "ext_id",
                                "extId"
                            )?.let { encontrados.add(it) }
                        }
                    }
                }
            }
        }

        if (encontrados.isEmpty()) return true

        return encontrados.any { it == ciaExtId }
    }

    private fun crearSlug(valor: String): String {
        return valor
            .trim()
            .lowercase(Locale.getDefault())
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "sin-slug" }
    }
}

sealed class ResultadoAgroSync {
    data class Exito(
        val productores: Int,
        val ranchos: Int,
        val parcelas: Int
    ) : ResultadoAgroSync()

    data class Error(
        val mensaje: String
    ) : ResultadoAgroSync()
}

private fun JsonObject.getOrNull(key: String): JsonElement? {
    if (!has(key)) return null

    val value = get(key)
    if (value == null || value.isJsonNull) return null

    return value
}

private fun JsonObject.stringOrNull(vararg keys: String): String? {
    for (key in keys) {
        val value = getOrNull(key) ?: continue

        if (value.isJsonPrimitive) {
            val text = value.asString?.trim()
            if (!text.isNullOrBlank()) return text
        }

        if (value.isJsonObject) {
            val obj = value.asJsonObject
            val id = obj.stringOrNull(
                "id",
                "uuid",
                "ext_id",
                "extId"
            )
            if (!id.isNullOrBlank()) return id
        }
    }

    return null
}

private fun JsonObject.textoTipoOrNull(vararg keys: String): String? {
    for (key in keys) {
        val value = getOrNull(key) ?: continue

        if (value.isJsonPrimitive) {
            val text = value.asString?.trim()
            if (!text.isNullOrBlank()) return text
        }

        if (value.isJsonObject) {
            val obj = value.asJsonObject

            val text = obj.stringOrNull(
                "name",
                "nombre",
                "type",
                "tipo",
                "code",
                "slug"
            )

            if (!text.isNullOrBlank()) return text
        }
    }

    return null
}

private fun JsonObject.relacionIdOrNull(vararg keys: String): String? {
    for (key in keys) {
        val value = getOrNull(key) ?: continue

        when {
            value.isJsonPrimitive -> {
                val text = value.asString?.trim()
                if (!text.isNullOrBlank()) return text
            }

            value.isJsonObject -> {
                val obj = value.asJsonObject
                val id = obj.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                )
                if (!id.isNullOrBlank()) return id
            }
        }
    }

    return null
}

private fun JsonObject.doubleOrNull(vararg keys: String): Double? {
    for (key in keys) {
        val value = getOrNull(key) ?: continue

        if (value.isJsonPrimitive) {
            val text = value.asString?.trim()
            val number = text?.toDoubleOrNull()
            if (number != null) return number
        }
    }

    return null
}

private fun String.normalizarTexto(): String {
    return trim()
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("_", " ")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
}
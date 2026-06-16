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
                ) ?: item.stringOrNull("code", "codigo", "slug")
                ?: "Parcela"

                val code = item.stringOrNull(
                    "code",
                    "codigo",
                    "slug"
                ) ?: crearSlug(nombre)

                val centroide = centroideSimpleDesdeGeometry(item)

                val lat = item.doubleOrNull("lat", "latitude")
                    ?: centroide?.first

                val lon = item.doubleOrNull("lon", "lng", "longitude")
                    ?: centroide?.second

                val existente = database.localPlotDao()
                    .getAllPlots()
                    .firstOrNull { parcela ->
                        parcela.extId == extId ||
                                (
                                        parcela.idLocalRanch == idRanchoLocal &&
                                                parcela.code == code
                                        )
                    }

                val idParcelaLocal = if (existente != null) {
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

                    existente.idLocalPlot
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

                guardarVerticesParcelaDesdeGeometry(
                    item = item,
                    idLocalPlot = idParcelaLocal,
                    extIdParcela = extId
                )

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

            if (geometry != null && !salida.has("geometry")) {
                salida.add("geometry", geometry)
            }

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

                val lon = coords.elementOrNull(0).doubleValueOrNull()
                val lat = coords.elementOrNull(1).doubleValueOrNull()

                if (!salida.has("lon") && lon != null) {
                    salida.addProperty("lon", lon)
                }

                if (!salida.has("lat") && lat != null) {
                    salida.addProperty("lat", lat)
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

    private fun guardarVerticesParcelaDesdeGeometry(
        item: JsonObject,
        idLocalPlot: Long,
        extIdParcela: String
    ) {
        val anillo = extraerPrimerAnilloPoligono(item)

        if (anillo == null || anillo.size() < 3) {
            return
        }

        database.runInTransaction {
            val db = database.openHelper.writableDatabase

            db.execSQL(
                "DELETE FROM local_plot_vertexes WHERE idLocalPlot = ?",
                arrayOf(idLocalPlot)
            )

            anillo.forEachIndexed { index, punto ->
                if (!punto.isJsonArray) return@forEachIndexed

                val coordenada = punto.asJsonArray

                val lon = coordenada.elementOrNull(0).doubleValueOrNull()
                    ?: return@forEachIndexed

                val lat = coordenada.elementOrNull(1).doubleValueOrNull()
                    ?: return@forEachIndexed

                val level = index + 1
                val extIdVertice = "${extIdParcela}_v_$level"

                db.execSQL(
                    """
                    INSERT OR REPLACE INTO local_plot_vertexes
                    (ext_id, level, lat, lon, idLocalPlot)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        extIdVertice,
                        level,
                        lat,
                        lon,
                        idLocalPlot
                    )
                )
            }
        }
    }

    private fun extraerPrimerAnilloPoligono(
        item: JsonObject
    ): JsonArray? {
        val geometry = item.getOrNull("geometry")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null

        val tipo = geometry.stringOrNull("type")
            ?.normalizarTexto()
            ?: return null

        val coordinates = geometry.getOrNull("coordinates")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return null

        return when (tipo) {
            "polygon" -> {
                coordinates
                    .elementOrNull(0)
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
            }

            "multipolygon" -> {
                coordinates
                    .elementOrNull(0)
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.elementOrNull(0)
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
            }

            else -> null
        }
    }

    private fun centroideSimpleDesdeGeometry(
        item: JsonObject
    ): Pair<Double, Double>? {
        val anillo = extraerPrimerAnilloPoligono(item) ?: return null

        val puntos = anillo.mapNotNull { punto ->
            if (!punto.isJsonArray) return@mapNotNull null

            val coordenada = punto.asJsonArray

            val lon = coordenada.elementOrNull(0).doubleValueOrNull()
                ?: return@mapNotNull null

            val lat = coordenada.elementOrNull(1).doubleValueOrNull()
                ?: return@mapNotNull null

            lat to lon
        }

        if (puntos.isEmpty()) return null

        val latPromedio = puntos.map { it.first }.average()
        val lonPromedio = puntos.map { it.second }.average()

        return latPromedio to lonPromedio
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

        val number = value.doubleValueOrNull()
        if (number != null) return number
    }

    return null
}

private fun JsonArray.elementOrNull(index: Int): JsonElement? {
    return if (index >= 0 && index < size()) {
        get(index)
    } else {
        null
    }
}

private fun JsonElement?.doubleValueOrNull(): Double? {
    if (this == null || isJsonNull || !isJsonPrimitive) return null

    return runCatching {
        asDouble
    }.getOrNull()
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
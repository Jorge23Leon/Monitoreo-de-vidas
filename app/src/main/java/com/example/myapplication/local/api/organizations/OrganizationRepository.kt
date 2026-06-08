package com.example.myapplication.local.api.organizations

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

class OrganizationRepository(
    context: Context
) {
    private val organizationApiService: OrganizationApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = OrganizationApiService::class.java
        )

    suspend fun obtenerCiasPadreEHijas(): ResultadoCiasApi {
        return try {
            val response = organizationApiService.listarDataCentrals()

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoCiasApi.Error(
                    "No se pudieron cargar CIAS: ${response.code()} ${error ?: response.message()}"
                )
            }

            val root = response.body()
                ?: return ResultadoCiasApi.Error("El servidor respondió vacío en datacentrals")

            val items = extraerLista(root)

            val cias = items.mapNotNull { item ->
                mapearDataCentral(item)
            }

            ResultadoCiasApi.Exito(cias)
        } catch (e: Exception) {
            ResultadoCiasApi.Error(
                "Error conectando CIAS: ${e.message}"
            )
        }
    }

    private fun extraerLista(root: JsonElement): List<JsonObject> {
        return when {
            root.isJsonArray -> {
                root.asJsonArray.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject
                }
            }

            root.isJsonObject -> {
                val obj = root.asJsonObject

                val array = obj.arrayOrNull("results")
                    ?: obj.arrayOrNull("data")
                    ?: obj.arrayOrNull("items")

                array?.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject
                } ?: emptyList()
            }

            else -> emptyList()
        }
    }

    private fun mapearDataCentral(item: JsonObject): CiaHijaApiItem? {
        val mainObj = item.objOrNull("data_central_main")
            ?: item.objOrNull("dataCentralMain")
            ?: item.objOrNull("datacentralmain")
            ?: item.objOrNull("main")
            ?: item.objOrNull("parent")

        val parentExtId = mainObj?.stringOrNull("id")
            ?: item.stringOrNull(
                "data_central_main_id",
                "dataCentralMainId",
                "data_central_main"
            )
            ?: return null

        val parentName = mainObj?.stringOrNull("name")
            ?: item.stringOrNull(
                "data_central_main_name",
                "main_name",
                "parent_name"
            )
            ?: "CIA Padre"

        val parentSlug = mainObj?.stringOrNull("slug")
            ?: item.stringOrNull(
                "data_central_main_slug",
                "main_slug",
                "parent_slug"
            )
            ?: crearSlug(parentName)

        val parentDescription = mainObj?.stringOrNull("description")
            ?: item.stringOrNull("data_central_main_description")

        val childExtId = item.stringOrNull("id")
            ?: return null

        val childName = item.stringOrNull("name")
            ?: item.stringOrNull("slug")
            ?: "CIA Hija"

        val childSlug = item.stringOrNull("slug")
            ?: crearSlug(childName)

        val childDescription = item.stringOrNull("description")

        val parent = CiaPadreApiItem(
            extId = parentExtId,
            name = parentName,
            slug = parentSlug,
            description = parentDescription
        )

        return CiaHijaApiItem(
            extId = childExtId,
            name = childName,
            slug = childSlug,
            description = childDescription,
            parentExtId = parentExtId,
            parent = parent
        )
    }

    private fun crearSlug(valor: String): String {
        return valor.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "sin-slug" }
    }
}

private fun JsonObject.stringOrNull(vararg keys: String): String? {
    for (key in keys) {
        if (!has(key)) continue

        val value = get(key)
        if (value == null || value.isJsonNull) continue

        if (value.isJsonPrimitive) {
            val text = value.asString?.trim()
            if (!text.isNullOrBlank()) return text
        }
    }

    return null
}

private fun JsonObject.objOrNull(key: String): JsonObject? {
    if (!has(key)) return null

    val value = get(key)
    if (value == null || value.isJsonNull || !value.isJsonObject) return null

    return value.asJsonObject
}

private fun JsonObject.arrayOrNull(key: String): JsonArray? {
    if (!has(key)) return null

    val value = get(key)
    if (value == null || value.isJsonNull || !value.isJsonArray) return null

    return value.asJsonArray
}
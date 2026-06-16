package com.example.myapplication.local.api.agrocatalogs

import android.content.Context
import com.example.myapplication.local.api.core.ApiConfig
import com.example.myapplication.local.api.core.RetrofitClient
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale

class AgroCatalogsRepository(
    context: Context,
    private val database: AppDatabase? = null
) {
    private val api: AgroCatalogsApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = AgroCatalogsApiService::class.java
        )

    suspend fun obtenerTodosLosCultivos(): ResultadoAgroCatalogsApi {
        return try {
            val todos = mutableListOf<AgroCropApiItem>()
            var page = 1

            while (true) {
                val response = api.listarCultivos(page = page)

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    return ResultadoAgroCatalogsApi.Error(
                        "Error cultivos: ${response.code()} ${error ?: response.message()}"
                    )
                }

                val body = response.body()
                    ?: return ResultadoAgroCatalogsApi.Error("El servidor respondió vacío en cultivos")

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) break

                page++
            }

            ResultadoAgroCatalogsApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoAgroCatalogsApi.Error(
                "No se pudieron cargar cultivos: ${e.message}"
            )
        }
    }

    suspend fun sincronizarCatalogoFitosanitario(): ResultadoCatalogoFitoSync {
        val db = database
            ?: return ResultadoCatalogoFitoSync.Error(
                "AgroCatalogsRepository no tiene database para guardar catálogo fitosanitario"
            )

        return try {
            val response = api.listarCatalogoFitosanitario()

            if (!response.isSuccessful) {
                return ResultadoCatalogoFitoSync.Error(
                    "Error catálogo fitosanitario: ${response.code()} ${
                        response.errorBody()?.string() ?: response.message()
                    }"
                )
            }

            val items = extraerLista(response.body())

            var catalogoGuardado = 0
            var etapasGuardadas = 0

            items.forEach { item ->
                val extId = item.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                ) ?: return@forEach

                val nombre = item.stringOrNull(
                    "name",
                    "nombre",
                    "common_name",
                    "commonName",
                    "title"
                ) ?: return@forEach

                val tipoApi = item.stringOrNull(
                    "type",
                    "tipo",
                    "category",
                    "categoria"
                ).orEmpty()

                val tipoLocal = normalizarTipoFito(
                    tipoApi = tipoApi,
                    nombre = nombre
                )

                val foto = extraerFotoPrincipal(item)

                val descripcion = item.stringOrNull(
                    "description",
                    "descripcion",
                    "comments",
                    "notes"
                )

                val idLocalCatalogo = guardarCatalogo(
                    database = db,
                    extId = extId,
                    nombre = nombre,
                    tipo = tipoLocal,
                    foto = foto,
                    descripcion = descripcion
                )

                val etapas = extraerEtapasConFoto(
                    item = item,
                    tipoLocal = tipoLocal
                )

                etapas.forEach { etapa ->
                    guardarEtapa(
                        database = db,
                        idPhytosanitary = idLocalCatalogo,
                        etapa = etapa.nombre,
                        foto = etapa.foto
                    )

                    etapasGuardadas++
                }

                catalogoGuardado++
            }

            ResultadoCatalogoFitoSync.Exito(
                catalogo = catalogoGuardado,
                etapas = etapasGuardadas
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ResultadoCatalogoFitoSync.Error(
                "Error sincronizando catálogo fitosanitario: ${e.message}"
            )
        }
    }

    private suspend fun guardarCatalogo(
        database: AppDatabase,
        extId: String,
        nombre: String,
        tipo: String,
        foto: String?,
        descripcion: String?
    ): Long {
        val dao = database.localphytosanitarycatalogDao()

        val existente = dao.getAllCatalogo()
            .firstOrNull { item ->
                item.extId == extId ||
                        item.name.equals(nombre, ignoreCase = true)
            }

        val entidad = LocalPhytosanitaryCatalogEntity(
            idPhytosanitary = existente?.idPhytosanitary ?: 0L,
            extId = extId,
            name = nombre,
            type = tipo,
            minRefValue = existente?.minRefValue,
            maxRefValue = existente?.maxRefValue,
            description = descripcion,
            photo = foto,
            idDefaultCrop = existente?.idDefaultCrop
        )

        return if (existente != null) {
            dao.updatePhytosanitary(entidad)
            existente.idPhytosanitary
        } else {
            dao.insertPhytosanitary(entidad)
        }
    }

    private suspend fun guardarEtapa(
        database: AppDatabase,
        idPhytosanitary: Long,
        etapa: String,
        foto: String?
    ) {
        val dao = database.localphytostageDao()

        val existente = dao.getPhytostageByPhytosanitaryAndStage(
            idPhytosanitary = idPhytosanitary,
            stage = etapa
        )

        val extId = "fito_${idPhytosanitary}_${crearSlug(etapa)}"

        val entidad = LocalPhytostageEntity(
            idLocalPhytostage = existente?.idLocalPhytostage ?: 0L,
            ext_id = extId,
            stage = etapa,
            photo = foto,
            idPhytosanitary = idPhytosanitary
        )

        if (existente != null) {
            dao.updatePhytostage(entidad)
        } else {
            dao.insertPhytostage(entidad)
        }
    }

    private fun normalizarTipoFito(
        tipoApi: String,
        nombre: String
    ): String {
        val texto = "$tipoApi $nombre"
            .trim()
            .lowercase(Locale.getDefault())
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")

        return when {
            texto.contains("disease") ||
                    texto.contains("enfermedad") ||
                    texto.contains("roya") ||
                    texto.contains("antracnosis") ||
                    texto.contains("tizon") ||
                    texto.contains("tizón") ||
                    texto.contains("mosaico") ||
                    texto.contains("mancha") ||
                    texto.contains("moho") ||
                    texto.contains("virus") -> "Enfermedad"

            texto.contains("pest") ||
                    texto.contains("plaga") ||
                    texto.contains("mosca") ||
                    texto.contains("pulgon") ||
                    texto.contains("pulgón") ||
                    texto.contains("trips") ||
                    texto.contains("gusano") ||
                    texto.contains("conchuela") ||
                    texto.contains("minador") ||
                    texto.contains("araña") -> "Plaga"

            else -> "Plaga"
        }
    }

    private fun extraerEtapasConFoto(
        item: JsonObject,
        tipoLocal: String
    ): List<EtapaFitoApi> {
        val etapasApi = item.arrayOrNull(
            "stages",
            "etapas",
            "photos",
            "stage_photos",
            "development_stages"
        )?.mapNotNull { element ->
            when {
                element.isJsonPrimitive -> {
                    val nombre = runCatching { element.asString }.getOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    nombre?.let {
                        EtapaFitoApi(
                            nombre = it,
                            foto = null
                        )
                    }
                }

                element.isJsonObject -> {
                    val obj = element.asJsonObject

                    val nombre = obj.stringOrNull(
                        "stage",
                        "name",
                        "nombre",
                        "label",
                        "phase",
                        "fase"
                    )

                    if (nombre.isNullOrBlank()) {
                        null
                    } else {
                        EtapaFitoApi(
                            nombre = nombre,
                            foto = extraerFotoPrincipal(obj)
                        )
                    }
                }

                else -> null
            }
        }.orEmpty()

        if (etapasApi.isNotEmpty()) {
            return etapasApi.distinctBy { it.nombre.lowercase(Locale.getDefault()) }
        }

        return if (tipoLocal.equals("Enfermedad", ignoreCase = true)) {
            listOf(
                EtapaFitoApi("Inicio", null),
                EtapaFitoApi("Desarrollo", null),
                EtapaFitoApi("Avanzado", null),
                EtapaFitoApi("Terminal", null)
            )
        } else {
            listOf(
                EtapaFitoApi("Huevecillo", null),
                EtapaFitoApi("Larva/Joven", null),
                EtapaFitoApi("Pupa", null),
                EtapaFitoApi("Adulto", null),
                EtapaFitoApi("Adulto con alas", null)
            )
        }
    }

    private fun extraerFotoPrincipal(item: JsonObject): String? {
        val directa = item.stringOrNull(
            "photo",
            "image",
            "image_url",
            "photo_url",
            "attachment_url",
            "url"
        )

        return normalizarUrlApi(
            directa
                ?: buscarUrlEnJson(item.getOrNull("attachments_url"))
                ?: buscarUrlEnJson(item.getOrNull("attachment"))
                ?: buscarUrlEnJson(item.getOrNull("attachments"))
                ?: buscarUrlEnJson(item.getOrNull("media"))
                ?: buscarUrlEnJson(item.getOrNull("additional_params"))
        )
    }

    private fun buscarUrlEnJson(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        return when {
            element.isJsonPrimitive -> {
                val text = runCatching { element.asString }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                if (pareceUrlOPathImagen(text)) text else null
            }

            element.isJsonArray -> {
                element.asJsonArray.firstNotNullOfOrNull { child ->
                    buscarUrlEnJson(child)
                }
            }

            element.isJsonObject -> {
                val obj = element.asJsonObject

                obj.stringOrNull(
                    "url",
                    "file",
                    "image",
                    "photo",
                    "path",
                    "href",
                    "attachment_url",
                    "image_url",
                    "photo_url"
                )?.takeIf { pareceUrlOPathImagen(it) }
                    ?: obj.entrySet().firstNotNullOfOrNull { entry ->
                        buscarUrlEnJson(entry.value)
                    }
            }

            else -> null
        }
    }

    private fun pareceUrlOPathImagen(value: String?): Boolean {
        if (value.isNullOrBlank()) return false

        val text = value.lowercase(Locale.getDefault())

        return text.startsWith("http://") ||
                text.startsWith("https://") ||
                text.startsWith("/media/") ||
                text.startsWith("media/") ||
                text.endsWith(".jpg") ||
                text.endsWith(".jpeg") ||
                text.endsWith(".png") ||
                text.endsWith(".webp")
    }

    private fun normalizarUrlApi(value: String?): String? {
        val clean = value
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val base = ApiConfig.BASE_URL.trimEnd('/')

        return when {
            clean.startsWith("http://localhost:8500") ->
                clean.replace("http://localhost:8500", base)

            clean.startsWith("http://127.0.0.1:8500") ->
                clean.replace("http://127.0.0.1:8500", base)

            clean.startsWith("/") ->
                "$base$clean"

            clean.startsWith("media/") ->
                "$base/$clean"

            else -> clean
        }
    }

    private fun extraerLista(root: JsonElement?): List<JsonObject> {
        if (root == null || root.isJsonNull) return emptyList()

        return when {
            root.isJsonArray -> {
                root.asJsonArray.mapNotNull { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject
                }
            }

            root.isJsonObject -> {
                val obj = root.asJsonObject

                val results = obj.getOrNull("results")
                if (results != null) return extraerLista(results)

                val data = obj.getOrNull("data")
                if (data != null) return extraerLista(data)

                val items = obj.getOrNull("items")
                if (items != null) return extraerLista(items)

                listOf(obj)
            }

            else -> emptyList()
        }
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
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "sin_etapa" }
    }
}

data class EtapaFitoApi(
    val nombre: String,
    val foto: String?
)

sealed class ResultadoAgroCatalogsApi {
    data class Exito(
        val cultivos: List<AgroCropApiItem>
    ) : ResultadoAgroCatalogsApi()

    data class Error(
        val mensaje: String
    ) : ResultadoAgroCatalogsApi()
}

sealed class ResultadoCatalogoFitoSync {
    data class Exito(
        val catalogo: Int,
        val etapas: Int
    ) : ResultadoCatalogoFitoSync()

    data class Error(
        val mensaje: String
    ) : ResultadoCatalogoFitoSync()
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
            val text = runCatching { value.asString }.getOrNull()?.trim()
            if (!text.isNullOrBlank()) return text
        }

        if (value.isJsonObject) {
            val obj = value.asJsonObject

            val text = obj.stringOrNull(
                "url",
                "file",
                "image",
                "photo",
                "path",
                "href",
                "id",
                "uuid",
                "ext_id",
                "extId"
            )

            if (!text.isNullOrBlank()) return text
        }
    }

    return null
}

private fun JsonObject.arrayOrNull(vararg keys: String): JsonArray? {
    for (key in keys) {
        val value = getOrNull(key) ?: continue

        if (value.isJsonArray) {
            return value.asJsonArray
        }
    }

    return null
}
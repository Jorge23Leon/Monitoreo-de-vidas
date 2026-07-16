package com.example.myapplication.local.api.agrocatalogs

import android.content.Context
import android.util.Log
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import retrofit2.Response

class AgroCatalogsRepository(
    context: Context,
    private val database: AppDatabase? = null
) {
    private val api: AgroCatalogsApiService =
        com.example.myapplication.local.api.core.RetrofitClient.crearServicioAutenticado(
            context = context.applicationContext,
            serviceClass = AgroCatalogsApiService::class.java
        )

    companion object {
        private const val TAG = "CATALOGO_FOTOS"
        private const val TIMEOUT_DETALLE_MS = 8_000L
        private const val MAX_DETALLES_EN_PARALELO = 4
    }

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
                    ?: return ResultadoAgroCatalogsApi.Error(
                        "El servidor respondió vacío en cultivos"
                    )

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) break

                page++

                if (page > 200) {
                    return ResultadoAgroCatalogsApi.Error(
                        "Se detuvo cultivos porque superó 200 páginas"
                    )
                }
            }

            /*
             * La lista se consulta siempre para detectar cultivos nuevos, pero el
             * endpoint de detalle solo se solicita cuando el cultivo no existe localmente
             * o todavía no tiene fotografía. Así las siguientes sincronizaciones son
             * mucho más rápidas.
             */
            val existentesPorExtId = database
                ?.localCropCatalogDao()
                ?.getAllCrops()
                ?.mapNotNull { cultivo ->
                    cultivo.extId
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { extId -> extId to cultivo }
                }
                ?.toMap()
                .orEmpty()

            val completos = coroutineScope {
                val limite = Semaphore(MAX_DETALLES_EN_PARALELO)

                todos.map { cultivo ->
                    async(Dispatchers.IO) {
                        limite.acquire()
                        try {
                            val extId = cultivo.id
                                ?.toString()
                                ?.trim()
                                .orEmpty()

                            val existente = existentesPorExtId[extId]
                            val fotoLista = extraerFotoCultivo(cultivo)

                            val necesitaDetalle =
                                extId.isNotBlank() &&
                                        (
                                                existente == null ||
                                                        (
                                                                fotoLista.isNullOrBlank() &&
                                                                        existente.photo.isNullOrBlank()
                                                                )
                                                )

                            if (necesitaDetalle) {
                                completarCultivoConDetalle(cultivo)
                            } else {
                                cultivo.copy(
                                    photo = fotoLista
                                        ?.takeIf { it.isNotBlank() }
                                        ?: existente?.photo
                                )
                            }
                        } finally {
                            limite.release()
                        }
                    }
                }.awaitAll()
            }

            ResultadoAgroCatalogsApi.Exito(completos)
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando cultivos", e)
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
            /*
             * El listado completo es ligero y permite detectar registros nuevos.
             * Los detalles pesados solo se consultan para elementos nuevos o incompletos.
             */
            val items = cargarTodasLasPaginasJson("catálogo fitosanitario") { page ->
                api.listarCatalogoFitosanitario(page = page)
            }

            val daoCatalogo = db.localphytosanitarycatalogDao()
            val existentes = daoCatalogo.getAllCatalogo()
            val existentesPorExtId = existentes
                .mapNotNull { item ->
                    item.extId
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { extId -> extId to item }
                }
                .toMap()

            val detallesPorExtId = coroutineScope {
                val limite = Semaphore(MAX_DETALLES_EN_PARALELO)

                items.mapNotNull { itemLista ->
                    val extId = itemLista.stringOrNull(
                        "id",
                        "uuid",
                        "ext_id",
                        "extId"
                    ) ?: return@mapNotNull null

                    val existente = existentesPorExtId[extId]
                    val etapasExistentes = existente
                        ?.let { fito ->
                            db.localphytostageDao()
                                .getStagesByPhytosanitary(fito.idPhytosanitary)
                        }
                        .orEmpty()

                    val fotoLista = extraerFotoPrincipal(itemLista)
                    val cultivoLista = extraerExtIdCultivo(itemLista)

                    val necesitaDetalle =
                        existente == null ||
                                existente.idDefaultCrop == null ||
                                existente.photo.isNullOrBlank() ||
                                etapasExistentes.isEmpty() ||
                                (
                                        cultivoLista.isNullOrBlank() &&
                                                existente.idDefaultCrop == null
                                        ) ||
                                (
                                        fotoLista.isNullOrBlank() &&
                                                existente.photo.isNullOrBlank()
                                        )

                    if (!necesitaDetalle) {
                        return@mapNotNull null
                    }

                    async(Dispatchers.IO) {
                        limite.acquire()
                        try {
                            extId to obtenerDetalleFitosanitario(extId)
                        } finally {
                            limite.release()
                        }
                    }
                }.awaitAll()
                    .mapNotNull { (extId, detalle) ->
                        detalle?.let { extId to it }
                    }
                    .toMap()
            }

            var catalogoGuardado = 0
            var etapasGuardadas = 0
            var sinFoto = 0

            items.forEach { itemLista ->
                val extId = itemLista.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                ) ?: return@forEach

                val existente = existentesPorExtId[extId]
                val itemDetalle = detallesPorExtId[extId]
                val item = combinarObjetos(
                    base = itemLista,
                    detalle = itemDetalle
                )

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
                    ?.takeIf { it.isNotBlank() }
                    ?: existente?.photo

                if (foto.isNullOrBlank()) {
                    sinFoto++
                    Log.w(
                        TAG,
                        "SIN_FOTO_API fito='$nombre' id=$extId claves=${item.entrySet().joinToString { it.key }}"
                    )
                } else {
                    Log.d(TAG, "FOTO_FITO '$nombre' -> $foto")
                }

                val descripcion = item.stringOrNull(
                    "description",
                    "descripcion",
                    "comments",
                    "notes"
                ) ?: existente?.description

                val idDefaultCropLocal = resolverIdDefaultCropLocal(
                    database = db,
                    item = item
                ) ?: existente?.idDefaultCrop

                val idLocalCatalogo = guardarCatalogo(
                    database = db,
                    existente = existente,
                    extId = extId,
                    nombre = nombre,
                    tipo = tipoLocal,
                    foto = foto,
                    descripcion = descripcion,
                    idDefaultCrop = idDefaultCropLocal
                )

                /*
                 * No se recrean etapas en cada sincronización. Solo se procesan cuando
                 * el servidor realmente mandó etapas/fotos o cuando el registro es nuevo.
                 */
                val debeProcesarEtapas =
                    existente == null ||
                            itemDetalle != null ||
                            tieneInformacionEtapas(item)

                if (debeProcesarEtapas) {
                    val etapas = extraerEtapasConFoto(
                        item = item,
                        tipoLocal = tipoLocal
                    )

                    etapas.forEach { etapa ->
                        if (etapa.foto.isNullOrBlank()) {
                            Log.w(
                                TAG,
                                "SIN_FOTO_ETAPA fito='$nombre' etapa='${etapa.nombre}'"
                            )
                        } else {
                            Log.d(
                                TAG,
                                "FOTO_ETAPA fito='$nombre' etapa='${etapa.nombre}' -> ${etapa.foto}"
                            )
                        }

                        guardarEtapa(
                            database = db,
                            idPhytosanitary = idLocalCatalogo,
                            etapa = etapa.nombre,
                            foto = etapa.foto
                        )

                        etapasGuardadas++
                    }
                }

                catalogoGuardado++
            }

            ResultadoCatalogoFitoSync.Exito(
                catalogo = catalogoGuardado,
                etapas = etapasGuardadas,
                sinFoto = sinFoto
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando catálogo fitosanitario", e)
            ResultadoCatalogoFitoSync.Error(
                "Error sincronizando catálogo fitosanitario: ${e.message}"
            )
        }
    }

    private suspend fun completarCultivoConDetalle(
        cultivo: AgroCropApiItem
    ): AgroCropApiItem {
        val fotoLista = extraerFotoCultivo(cultivo)


        val idCultivoDetalle = cultivo.id
            ?.toString()
            ?.trim()
            .orEmpty()

        val detalle = if (idCultivoDetalle.isNotBlank()) {
            obtenerDetalleCultivo(idCultivoDetalle)
        } else {
            null
        }

        val fotoDetalle = detalle?.let(::extraerFotoPrincipal)

        val fotoFinal = fotoDetalle ?: fotoLista

        if (fotoFinal.isNullOrBlank()) {
            Log.w(
                TAG,
                "SIN_FOTO_API cultivo='${cultivo.name ?: cultivo.code ?: cultivo.id}'"
            )
        } else {
            Log.d(
                TAG,
                "FOTO_CULTIVO '${cultivo.name ?: cultivo.code ?: cultivo.id}' -> $fotoFinal"
            )
        }

        return cultivo.copy(photo = fotoFinal)
    }

    private fun extraerFotoCultivo(
        cultivo: AgroCropApiItem
    ): String? {
        return cultivo.photo
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: buscarUrlEnJson(cultivo.attachmentsUrl)
            ?: buscarUrlEnJson(cultivo.additionalParams)
    }

    private suspend fun obtenerDetalleCultivo(
        id: String
    ): JsonObject? {
        return obtenerDetalleJson(
            etiqueta = "cultivo $id",
            request = { api.obtenerCultivoDetalle(id) }
        )
    }

    private suspend fun obtenerDetalleFitosanitario(
        id: String
    ): JsonObject? {
        return obtenerDetalleJson(
            etiqueta = "fitosanitario $id",
            request = { api.obtenerFitosanitarioDetalle(id) }
        )
    }

    private suspend fun obtenerDetalleJson(
        etiqueta: String,
        request: suspend () -> Response<JsonElement>
    ): JsonObject? {
        return try {
            val response = withTimeoutOrNull(TIMEOUT_DETALLE_MS) {
                request()
            } ?: run {
                Log.w(TAG, "TIMEOUT detalle $etiqueta")
                return null
            }

            if (!response.isSuccessful) {
                Log.w(
                    TAG,
                    "HTTP_${response.code()} detalle $etiqueta: ${response.message()}"
                )
                return null
            }

            extraerObjetoPrincipal(response.body())
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando detalle $etiqueta: ${e.message}")
            null
        }
    }

    private suspend fun cargarTodasLasPaginasJson(
        nombre: String,
        request: suspend (Int) -> Response<JsonElement>
    ): List<JsonObject> {
        val todos = mutableListOf<JsonObject>()
        var page = 1

        while (true) {
            val response = request(page)

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                throw IllegalStateException(
                    "Error $nombre: ${response.code()} ${error ?: response.message()}"
                )
            }

            val root = response.body()
                ?: throw IllegalStateException("El servidor respondió vacío en $nombre")

            todos.addAll(extraerLista(root))

            if (!tienePaginaSiguiente(root)) {
                break
            }

            page++

            if (page > 200) {
                throw IllegalStateException(
                    "Se detuvo $nombre porque superó 200 páginas"
                )
            }
        }

        return todos
    }

    private fun tienePaginaSiguiente(root: JsonElement?): Boolean {
        if (root == null || !root.isJsonObject) return false

        val next = root.asJsonObject.get("next")
        if (next == null || next.isJsonNull || !next.isJsonPrimitive) return false

        return runCatching {
            next.asString.trim().isNotBlank()
        }.getOrDefault(false)
    }

    private suspend fun guardarCatalogo(
        database: AppDatabase,
        existente: LocalPhytosanitaryCatalogEntity?,
        extId: String,
        nombre: String,
        tipo: String,
        foto: String?,
        descripcion: String?,
        idDefaultCrop: Long?
    ): Long {
        val dao = database.localphytosanitarycatalogDao()

        val entidad = LocalPhytosanitaryCatalogEntity(
            idPhytosanitary = existente?.idPhytosanitary ?: 0L,
            extId = extId,
            name = nombre,
            type = tipo,
            minRefValue = existente?.minRefValue,
            maxRefValue = existente?.maxRefValue,
            description = descripcion ?: existente?.description,
            photo = foto?.takeIf { it.isNotBlank() } ?: existente?.photo,
            idDefaultCrop = idDefaultCrop ?: existente?.idDefaultCrop
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
            photo = foto?.takeIf { it.isNotBlank() } ?: existente?.photo,
            idPhytosanitary = idPhytosanitary
        )

        if (existente != null) {
            dao.updatePhytostage(entidad)
        } else {
            dao.insertPhytostage(entidad)
        }
    }

    private suspend fun resolverIdDefaultCropLocal(
        database: AppDatabase,
        item: JsonObject
    ): Long? {
        val cropDao = database.localCropCatalogDao()
        val extIdCultivo = extraerExtIdCultivo(item)

        if (!extIdCultivo.isNullOrBlank()) {
            cropDao.getCropByExtId(extIdCultivo)?.let { return it.idCrop }
        }

        val codigoCultivo = extraerCodigoCultivo(item)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (codigoCultivo != null) {
            cropDao.getCropByCode(codigoCultivo)?.let { return it.idCrop }
        }

        val nombreCultivo = extraerNombreCultivo(item)
            ?.substringBefore("(")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return nombreCultivo
            ?.let { cropDao.getCropByName(it) }
            ?.idCrop
    }

    private fun extraerExtIdCultivo(item: JsonObject): String? {
        val campos = listOf(
            "default_crop",
            "defaultCrop",
            "default_crop_id",
            "defaultCropId",
            "crop",
            "crop_id",
            "cropId"
        )

        campos.forEach { campo ->
            val valor = item.getOrNull(campo) ?: return@forEach

            if (valor.isJsonPrimitive) {
                return runCatching { valor.asString }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }

            if (valor.isJsonObject) {
                val objeto = valor.asJsonObject

                listOf("id", "uuid", "ext_id", "extId", "pk").forEach { clave ->
                    val candidato = objeto.getOrNull(clave)
                    if (candidato?.isJsonPrimitive == true) {
                        val texto = runCatching { candidato.asString }
                            .getOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }

                        if (texto != null) return texto
                    }
                }
            }
        }

        return null
    }

    private fun extraerNombreCultivo(item: JsonObject): String? {
        val campos = listOf(
            "default_crop",
            "defaultCrop",
            "crop"
        )

        campos.forEach { campo ->
            val valor = item.getOrNull(campo) ?: return@forEach

            if (valor.isJsonObject) {
                val objeto = valor.asJsonObject

                listOf("name", "nombre", "label", "title").forEach { clave ->
                    val candidato = objeto.getOrNull(clave)
                    if (candidato?.isJsonPrimitive == true) {
                        val texto = runCatching { candidato.asString }
                            .getOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }

                        if (texto != null) return texto
                    }
                }
            }
        }

        return null
    }

    private fun extraerCodigoCultivo(item: JsonObject): String? {
        val campos = listOf(
            "default_crop",
            "defaultCrop",
            "crop"
        )

        campos.forEach { campo ->
            val valor = item.getOrNull(campo) ?: return@forEach

            if (valor.isJsonObject) {
                val objeto = valor.asJsonObject

                listOf("code", "codigo", "crop_code").forEach { clave ->
                    val candidato = objeto.getOrNull(clave)
                    if (candidato?.isJsonPrimitive == true) {
                        val texto = runCatching { candidato.asString }
                            .getOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }

                        if (texto != null) return texto
                    }
                }
            }
        }

        return null
    }

    private fun tieneInformacionEtapas(item: JsonObject): Boolean {
        val campos = listOf(
            "stages",
            "etapas",
            "development_stages",
            "phases",
            "fases",
            "stage_photos",
            "photos",
            "images",
            "stage_images",
            "development_photos",
            "phase_photos"
        )

        return campos.any { campo ->
            val valor = item.getOrNull(campo)
            valor?.isJsonArray == true && valor.asJsonArray.size() > 0
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
                    texto.contains("virus") ||
                    texto.contains("carbon") ||
                    texto.contains("pudricion") ||
                    texto.contains("fusarium") ||
                    texto.contains("achaparramiento") ||
                    texto.contains("bacter") ||
                    texto.contains("mildiu") ||
                    texto.contains("cenicilla") -> "Enfermedad"

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
        /*
         * El backend puede enviar:
         * - stages: ["Inicio", "Desarrollo"]
         * - photos: [{stage: "Inicio", image_url: "..."}]
         * - stages: [{name: "Inicio", photo: "..."}]
         *
         * Antes se leía primero stages y se ignoraba photos. Aquí se combinan
         * por nombre de etapa y, como respaldo, por la posición de la lista.
         */
        val etapasRaw = item.arrayOrNull(
            "stages",
            "etapas",
            "development_stages",
            "phases",
            "fases"
        )

        val fotosRaw = item.arrayOrNull(
            "stage_photos",
            "photos",
            "images",
            "stage_images",
            "development_photos",
            "phase_photos"
        )

        val fotoPrincipal = extraerFotoPrincipal(item)
        val fotosPorNombre = linkedMapOf<String, String>()
        val fotosPorOrden = mutableListOf<String>()
        val nombresDesdeFotos = mutableListOf<String>()

        fotosRaw?.forEach { element ->
            when {
                element.isJsonPrimitive -> {
                    val foto = runCatching { element.asString }.getOrNull()
                        ?.trim()
                        ?.takeIf(::pareceUrlOPathImagen)

                    if (foto != null) {
                        fotosPorOrden.add(foto)
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
                        "fase",
                        "development_stage"
                    )
                    val foto = extraerFotoPrincipal(obj)

                    if (!nombre.isNullOrBlank() && !foto.isNullOrBlank()) {
                        fotosPorNombre[claveEtapa(nombre)] = foto
                        nombresDesdeFotos.add(nombre)
                    } else if (!foto.isNullOrBlank()) {
                        fotosPorOrden.add(foto)
                    }
                }
            }
        }

        val etapas = mutableListOf<EtapaFitoApi>()

        etapasRaw?.forEachIndexed { indice, element ->
            when {
                element.isJsonPrimitive -> {
                    val nombre = runCatching { element.asString }.getOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    if (nombre != null) {
                        etapas.add(
                            EtapaFitoApi(
                                nombre = nombre,
                                foto = fotosPorNombre[claveEtapa(nombre)]
                                    ?: fotosPorOrden.getOrNull(indice)
                                    ?: fotoPrincipal
                            )
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
                        "fase",
                        "development_stage"
                    )

                    if (!nombre.isNullOrBlank()) {
                        etapas.add(
                            EtapaFitoApi(
                                nombre = nombre,
                                foto = extraerFotoPrincipal(obj)
                                    ?: fotosPorNombre[claveEtapa(nombre)]
                                    ?: fotosPorOrden.getOrNull(indice)
                                    ?: fotoPrincipal
                            )
                        )
                    }
                }
            }
        }

        if (etapas.isEmpty()) {
            nombresDesdeFotos
                .distinctBy(::claveEtapa)
                .forEachIndexed { indice, nombre ->
                    etapas.add(
                        EtapaFitoApi(
                            nombre = nombre,
                            foto = fotosPorNombre[claveEtapa(nombre)]
                                ?: fotosPorOrden.getOrNull(indice)
                                ?: fotoPrincipal
                        )
                    )
                }
        }

        if (etapas.isNotEmpty()) {
            return etapas
                .distinctBy { claveEtapa(it.nombre) }
                .map { etapa ->
                    etapa.copy(foto = etapa.foto ?: fotoPrincipal)
                }
        }

        val nombresPorDefecto = if (
            tipoLocal.equals("Enfermedad", ignoreCase = true)
        ) {
            listOf("Inicio", "Desarrollo", "Avanzado", "Terminal")
        } else {
            listOf(
                "Huevecillo",
                "Larva/Joven",
                "Pupa",
                "Adulto",
                "Adulto con alas"
            )
        }

        return nombresPorDefecto.map { nombre ->
            EtapaFitoApi(
                nombre = nombre,
                foto = fotoPrincipal
            )
        }
    }

    private fun extraerFotoPrincipal(item: JsonObject): String? {
        val directa = item.stringOrNull(
            "download_url",
            "file_url",
            "image_url",
            "photo_url",
            "absolute_url",
            "content_url",
            "original_url",
            "source_url",
            "thumbnail_url",
            "attachment_url",
            "download",
            "photo",
            "image",
            "file",
            "thumbnail",
            "path",
            "url",
            "href",
            "resource_url",
            "detail_url",
            "api_url"
        )?.takeIf(::pareceUrlOPathImagen)

        return directa
            ?: buscarUrlEnJson(item.getOrNull("attachments_url"))
            ?: buscarUrlEnJson(item.getOrNull("attachment"))
            ?: buscarUrlEnJson(item.getOrNull("attachments"))
            ?: buscarUrlEnJson(item.getOrNull("media"))
            ?: buscarUrlEnJson(item.getOrNull("photos"))
            ?: buscarUrlEnJson(item.getOrNull("images"))
            ?: buscarUrlEnJson(item.getOrNull("additional_params"))
    }

    private fun buscarUrlEnJson(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        return when {
            element.isJsonPrimitive -> {
                runCatching { element.asString }.getOrNull()
                    ?.trim()
                    ?.takeIf(::pareceUrlOPathImagen)
            }

            element.isJsonArray -> {
                element.asJsonArray.firstNotNullOfOrNull(::buscarUrlEnJson)
            }

            element.isJsonObject -> {
                val obj = element.asJsonObject

                obj.stringOrNull(
                    "download_url",
                    "file_url",
                    "image_url",
                    "photo_url",
                    "absolute_url",
                    "content_url",
                    "original_url",
                    "source_url",
                    "thumbnail_url",
                    "attachment_url",
                    "download",
                    "file",
                    "image",
                    "photo",
                    "thumbnail",
                    "path",
                    "url",
                    "href",
                    "resource_url",
                    "detail_url",
                    "api_url"
                )?.takeIf(::pareceUrlOPathImagen)
                    ?: obj.entrySet().firstNotNullOfOrNull { entry ->
                        buscarUrlEnJson(entry.value)
                    }
            }

            else -> null
        }
    }

    private fun pareceUrlOPathImagen(value: String?): Boolean {
        if (value.isNullOrBlank()) return false

        val text = value.trim().lowercase(Locale.getDefault())

        return text.startsWith("http://") ||
                text.startsWith("https://") ||
                text.startsWith("/media/") ||
                text.startsWith("media/") ||
                text.startsWith("/uploads/") ||
                text.startsWith("uploads/") ||
                text.startsWith("/files/") ||
                text.startsWith("files/") ||
                text.startsWith("/api/v1/core/attachments/") ||
                text.startsWith("api/v1/core/attachments/") ||
                text.endsWith(".jpg") ||
                text.endsWith(".jpeg") ||
                text.endsWith(".png") ||
                text.endsWith(".webp") ||
                text.endsWith(".gif") ||
                text.endsWith(".avif")
    }

    private fun combinarObjetos(
        base: JsonObject,
        detalle: JsonObject?
    ): JsonObject {
        if (detalle == null) return base

        return JsonObject().apply {
            base.entrySet().forEach { entry ->
                add(entry.key, entry.value)
            }
            detalle.entrySet().forEach { entry ->
                add(entry.key, entry.value)
            }
        }
    }

    private fun extraerObjetoPrincipal(
        element: JsonElement?
    ): JsonObject? {
        if (element == null || element.isJsonNull) return null

        if (element.isJsonObject) {
            val obj = element.asJsonObject

            for (key in listOf("data", "result", "item", "detail")) {
                val nested = obj.getOrNull(key)
                val encontrado = nested?.let(::extraerObjetoPrincipal)
                if (encontrado != null) {
                    return encontrado
                }
            }

            val results = obj.getOrNull("results")
            if (results?.isJsonArray == true) {
                return results.asJsonArray
                    .firstOrNull { it.isJsonObject }
                    ?.asJsonObject
            }

            return obj
        }

        if (element.isJsonArray) {
            return element.asJsonArray
                .firstOrNull { it.isJsonObject }
                ?.asJsonObject
        }

        return null
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

    private fun claveEtapa(valor: String): String {
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
    }

    private fun crearSlug(valor: String): String {
        return claveEtapa(valor).ifBlank { "sin_etapa" }
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
        val etapas: Int,
        val sinFoto: Int = 0
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
                "download_url",
                "file_url",
                "image_url",
                "photo_url",
                "absolute_url",
                "content_url",
                "original_url",
                "source_url",
                "thumbnail_url",
                "attachment_url",
                "download",
                "file",
                "image",
                "photo",
                "thumbnail",
                "path",
                "url",
                "href",
                "resource_url",
                "detail_url",
                "api_url"
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

package com.example.myapplication.local.api.sync

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient
import com.example.myapplication.local.api.geoassets.GeoAssetsApiService
import com.example.myapplication.local.api.organizations.OrganizationApiService
import com.example.myapplication.local.api.fieldops.FieldOpsRepository
import com.example.myapplication.local.api.fieldops.ResultadoFieldOpsApi
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaAgroUnitCrossRef
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale
import retrofit2.Response
import android.util.Log

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

    private val fieldOpsRepository = FieldOpsRepository(context)

    suspend fun sincronizarProductoresRanchosParcelas(
        idLocalCia: Long
    ): ResultadoAgroSync {
        return try {
            val ciaLocal = database.localCiaDao().getCiaById(idLocalCia)
                ?: return ResultadoAgroSync.Error("No se encontró la CIA local seleccionada")

            val ciaExtId = ciaLocal.extId?.trim()

            if (ciaExtId.isNullOrBlank()) {
                return ResultadoAgroSync.Error(
                    "La CIA seleccionada no tiene extId. No se puede filtrar contra producción."
                )
            }

            /*
             * IMPORTANTE:
             * Con usuario normal, datacentrals-assignments puede venir vacío o dar 403.
             * Por eso NO debemos cortar aquí.
             */
            val productoresPermitidosExtId = obtenerProductoresPermitidosPorAsignaciones(
                ciaExtId = ciaExtId
            )

            val productoresJson = cargarProductoresVisiblesParaCia(
                ciaExtId = ciaExtId,
                productoresPermitidosExtId = productoresPermitidosExtId
            )

            if (productoresJson.isEmpty()) {
                val productoresLocalesActuales = database.localCiaAgroUnitDao()
                    .getProductoresByCia(idLocalCia)

                return ResultadoAgroSync.Exito(
                    productores = productoresLocalesActuales.size,
                    ranchos = 0,
                    parcelas = 0
                )
            }

            val ranchosJson = cargarTodasLasPaginasJson("ranchos") { page ->
                geoAssetsApi.listarRanchos(page = page)
            }

            val parcelasJson = cargarTodasLasPaginasJson("parcelas") { page ->
                geoAssetsApi.listarParcelas(page = page)
            }

            // Cuando el API no marca rancho/parcela con datacentral, el vínculo seguro
            // viene de los programas de la CIA: programa -> plot -> ranch.
            val parcelasPermitidasExtId = obtenerParcelasDesdeProgramasDeCia(ciaExtId)
            val ranchosPermitidosExtId = parcelasJson
                .asSequence()
                .filter { parcela ->
                    val extIdParcela = parcela.stringOrNull("id", "uuid", "ext_id", "extId")
                    extIdParcela != null && extIdParcela in parcelasPermitidasExtId
                }
                .mapNotNull { parcela ->
                    parcela.relacionIdOrNull(
                        "ranch", "ranch_id", "ranchId", "local_ranch", "localRanch", "farm", "farm_id"
                    )
                }
                .toSet()

            var productoresGuardados = 0
            var ranchosGuardados = 0
            var parcelasGuardadas = 0

            val productoresLocalesPorExtId = mutableMapOf<String, Long>()
            val ranchosLocalesPorExtId = mutableMapOf<String, Long>()

            /*
             * Solo limpiamos la relación CIA -> Productor cuando sí encontramos productores.
             * Así no borramos la cache si el usuario normal no tiene permiso temporalmente.
             */
            database.localCiaAgroUnitDao()
                .eliminarProductoresDeCia(idLocalCia)

            productoresJson.forEach { item ->
                if (!esProductor(item)) return@forEach

                val extId = item.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                ) ?: return@forEach

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
                    .getAgroUnitByExtId(extId)
                    ?: database.localAgroUnitDao().getAgroUnitBySlug(slug)

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

            val productoresFinalesExtId = productoresLocalesPorExtId.keys

            ranchosJson.forEach { item ->
                val extId = item.stringOrNull(
                    "id",
                    "uuid",
                    "ext_id",
                    "extId"
                ) ?: return@forEach

                val tieneRelacionDirectaConCia = perteneceACiaConRelacionExplicita(
                    item = item,
                    ciaExtId = ciaExtId
                )

                // Sin relación directa, solo aceptamos ranchos a los que llegue una
                // parcela usada por un programa de ESTA CIA.
                if (!tieneRelacionDirectaConCia && extId !in ranchosPermitidosExtId) {
                    return@forEach
                }

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

                if (productorExtId !in productoresFinalesExtId) {
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

                val tieneRelacionDirectaConCia = perteneceACiaConRelacionExplicita(
                    item = item,
                    ciaExtId = ciaExtId
                )

                // No basta con que el productor sea compartido: la parcela debe estar
                // en un programa de la CIA o traer una relación explícita a la CIA.
                if (!tieneRelacionDirectaConCia && extId !in parcelasPermitidasExtId) {
                    return@forEach
                }

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

                /*
                 * IMPORTANTE:
                 * Guarda los vértices del polígono de la parcela.
                 * Si no hacemos esto, el mapa carga pero no dibuja el polígono.
                 */
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
    private suspend fun obtenerProductoresPermitidosPorAsignaciones(
        ciaExtId: String
    ): Set<String> {
        val asignacionesJson = try {
            cargarTodasLasPaginasJson("asignaciones CIA-productor") { page ->
                organizationApi.listarAsignacionesDataCentralAgroUnit(page = page)
            }
        } catch (e: Exception) {
            Log.w(
                "AGRO_SYNC",
                "Asignaciones no disponibles para usuario actual en CIA $ciaExtId: ${e.message}"
            )
            emptyList()
        }

        return asignacionesJson.mapNotNull { item ->
            val dataCentralExtId = item.relacionIdOrNull(
                "datacentral",
                "datacentral_id",
                "data_central",
                "data_central_id",
                "dataCentral",
                "dataCentralId",
                "cia",
                "cia_id"
            )

            val agroUnitExtId = item.relacionIdOrNull(
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
            )

            if (dataCentralExtId == ciaExtId && !agroUnitExtId.isNullOrBlank()) {
                agroUnitExtId
            } else {
                null
            }
        }.toSet()
    }

    private suspend fun cargarProductoresVisiblesParaCia(
        ciaExtId: String,
        productoresPermitidosExtId: Set<String>
    ): List<JsonObject> {
        /*
         * Flujo seguro para NO contaminar la relación CIA -> Productor.
         * Nunca regresamos todos los productores visibles del token si no hay
         * una relación real con la CIA seleccionada.
         */

        val porDatacentral = cargarUnidadesAgroeconomicas(
            datacentral = ciaExtId,
            dataCentral = null
        )

        val porDataCentral = cargarUnidadesAgroeconomicas(
            datacentral = null,
            dataCentral = ciaExtId
        )

        val filtradosPorEndpoint = (porDatacentral + porDataCentral)
            .filter { item -> esProductor(item) }
            .distinctBy { item ->
                item.stringOrNull("id", "uuid", "ext_id", "extId") ?: item.toString()
            }

        val productoresDesdeProgramas = obtenerProductoresDesdeProgramasDeCia(ciaExtId)

        /*
         * 1) Si existen asignaciones CIA-productor, usamos solo esas.
         */
        if (productoresPermitidosExtId.isNotEmpty()) {
            val porAsignacionesEndpoint = filtradosPorEndpoint.filter { item ->
                val extId = item.stringOrNull("id", "uuid", "ext_id", "extId")
                extId != null && extId in productoresPermitidosExtId
            }

            if (porAsignacionesEndpoint.isNotEmpty()) {
                Log.d(
                    "AGRO_SYNC",
                    "Productores por asignaciones desde endpoint CIA $ciaExtId: ${porAsignacionesEndpoint.size}"
                )
                return porAsignacionesEndpoint
            }
        }

        /*
         * 2) Si hay programas de campo de esta CIA, usamos los productores
         *    que aparecen en esos programas.
         */
        if (productoresDesdeProgramas.isNotEmpty()) {
            val porProgramasEndpoint = filtradosPorEndpoint.filter { item ->
                val extId = item.stringOrNull("id", "uuid", "ext_id", "extId")
                extId != null && extId in productoresDesdeProgramas
            }

            if (porProgramasEndpoint.isNotEmpty()) {
                Log.d(
                    "AGRO_SYNC",
                    "Productores por programas desde endpoint CIA $ciaExtId: ${porProgramasEndpoint.size}"
                )
                return porProgramasEndpoint
            }
        }

        /*
         * 3) Si el endpoint filtrado sí respondió y trae productores,
         *    solo los aceptamos si tienen relación explícita con la CIA.
         */
        val conRelacionEndpoint = filtradosPorEndpoint.filter { item ->
            perteneceACiaConRelacionExplicita(
                item = item,
                ciaExtId = ciaExtId
            )
        }

        if (conRelacionEndpoint.isNotEmpty()) {
            Log.d(
                "AGRO_SYNC",
                "Productores por relación explícita desde endpoint CIA $ciaExtId: ${conRelacionEndpoint.size}"
            )
            return conRelacionEndpoint
        }

        /*
         * 4) Cargamos productores visibles generales solo para cruzarlos
         *    con asignaciones/programas/relación explícita.
         *    OJO: nunca se regresan completos.
         */
        val visiblesGenerales = cargarUnidadesAgroeconomicas(
            datacentral = null,
            dataCentral = null
        )
            .filter { item -> esProductor(item) }
            .distinctBy { item ->
                item.stringOrNull("id", "uuid", "ext_id", "extId") ?: item.toString()
            }

        if (productoresPermitidosExtId.isNotEmpty()) {
            val porAsignaciones = visiblesGenerales.filter { item ->
                val extId = item.stringOrNull("id", "uuid", "ext_id", "extId")
                extId != null && extId in productoresPermitidosExtId
            }

            if (porAsignaciones.isNotEmpty()) {
                Log.d(
                    "AGRO_SYNC",
                    "Productores por asignaciones CIA $ciaExtId: ${porAsignaciones.size}"
                )
                return porAsignaciones
            }
        }

        if (productoresDesdeProgramas.isNotEmpty()) {
            val porProgramas = visiblesGenerales.filter { item ->
                val extId = item.stringOrNull("id", "uuid", "ext_id", "extId")
                extId != null && extId in productoresDesdeProgramas
            }

            if (porProgramas.isNotEmpty()) {
                Log.d(
                    "AGRO_SYNC",
                    "Productores por programas CIA $ciaExtId: ${porProgramas.size}"
                )
                return porProgramas
            }
        }

        val conRelacionCia = visiblesGenerales.filter { item ->
            perteneceACiaConRelacionExplicita(
                item = item,
                ciaExtId = ciaExtId
            )
        }

        if (conRelacionCia.isNotEmpty()) {
            Log.d(
                "AGRO_SYNC",
                "Productores por relación explícita CIA $ciaExtId: ${conRelacionCia.size}"
            )
            return conRelacionCia
        }

        /*
         * CORRECCIÓN PRINCIPAL:
         * Antes aquí se hacía: return visiblesGenerales
         * Eso metía TODOS los productores visibles del usuario en la CIA seleccionada.
         */
        Log.w(
            "AGRO_SYNC",
            "No se encontró relación exacta CIA-productor para $ciaExtId. No se asignan productores globales."
        )

        return emptyList()
    }

    private suspend fun cargarUnidadesAgroeconomicas(
        datacentral: String?,
        dataCentral: String?
    ): List<JsonObject> {
        val todos = mutableListOf<JsonObject>()
        var page = 1

        while (true) {
            val response = try {
                organizationApi.listarUnidadesAgroeconomicas(
                    datacentral = datacentral,
                    dataCentral = dataCentral,
                    page = page
                )
            } catch (e: Exception) {
                Log.w(
                    "AGRO_SYNC",
                    "Error cargando organizaciones página $page: ${e.message}"
                )
                return todos
            }

            if (!response.isSuccessful) {
                Log.w(
                    "AGRO_SYNC",
                    "Organizaciones HTTP ${response.code()} página $page: ${
                        response.errorBody()?.string().orEmpty()
                    }"
                )
                return todos
            }

            val body = response.body()
            val pagina = extraerLista(body)

            todos.addAll(pagina)

            if (!tienePaginaSiguiente(body)) {
                break
            }

            page++

            if (page > 200) {
                Log.w(
                    "AGRO_SYNC",
                    "Se detuvo carga de organizaciones porque superó 200 páginas"
                )
                break
            }
        }

        return todos
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
                throw IllegalStateException("Se detuvo $nombre porque superó 200 páginas")
            }
        }

        return todos
    }

    private fun tienePaginaSiguiente(root: JsonElement?): Boolean {
        if (root == null || !root.isJsonObject) return false

        val next = root.asJsonObject.get("next")
        if (next == null || next.isJsonNull || !next.isJsonPrimitive) return false

        return runCatching { next.asString.trim().isNotBlank() }.getOrDefault(false)
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
                    // Aunque esté vacío, ya identificamos que es una respuesta contenedora.
                    // No se debe convertir el wrapper paginado en un "productor" falso.
                    return extraerLista(value)
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

    private suspend fun obtenerProductoresDesdeProgramasDeCia(
        ciaExtId: String
    ): Set<String> {
        return when (
            val resultado = fieldOpsRepository.obtenerTodosLosProgramasCampo(
                datacentral = ciaExtId
            )
        ) {
            is ResultadoFieldOpsApi.Exito -> {
                resultado.programas
                    .mapNotNull { programa ->
                        programa.agroUnit
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    }
                    .toSet()
            }

            is ResultadoFieldOpsApi.Error -> emptySet()
        }
    }

    /** UUIDs de parcelas utilizados por programas del DataCentral seleccionado. */
    private suspend fun obtenerParcelasDesdeProgramasDeCia(
        ciaExtId: String
    ): Set<String> {
        return when (
            val resultado = fieldOpsRepository.obtenerTodosLosProgramasCampo(
                datacentral = ciaExtId
            )
        ) {
            is ResultadoFieldOpsApi.Exito -> {
                resultado.programas
                    .mapNotNull { programa ->
                        programa.plot?.trim()?.takeIf { it.isNotBlank() }
                    }
                    .toSet()
            }
            is ResultadoFieldOpsApi.Error -> emptySet()
        }
    }

    private fun perteneceACiaConRelacionExplicita(
        item: JsonObject,
        ciaExtId: String
    ): Boolean {
        val encontrados = obtenerRelacionesCia(item)

        if (encontrados.isEmpty()) {
            return false
        }

        return encontrados.any { it == ciaExtId }
    }

    private fun obtenerRelacionesCia(
        item: JsonObject
    ): List<String> {
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
                            val text = runCatching { element.asString.trim() }.getOrNull()
                            if (!text.isNullOrBlank()) encontrados.add(text)
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

        return encontrados.distinct()
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
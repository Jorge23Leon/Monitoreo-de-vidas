package com.example.myapplication.local.api.monitoreosync

// CAMBIO PUNTOS/CSV: al sincronizar target points, se guarda el label de API
// para que tabla, mapa y CSV muestren el número real del punto.

import android.content.Context
import android.util.Log
import com.example.myapplication.local.api.agrocatalogs.AgroCatalogsRepository
import com.example.myapplication.local.api.agrocatalogs.AgroCropApiItem
import com.example.myapplication.local.api.agrocatalogs.ResultadoAgroCatalogsApi
import com.example.myapplication.local.api.fieldops.FieldOpsRepository
import com.example.myapplication.local.api.fieldops.FieldTaskApiItem
import com.example.myapplication.local.api.fieldops.ResultadoFieldOpsApi
import com.example.myapplication.local.api.phytomonitoring.PhytoHeaderApiItem
import com.example.myapplication.local.api.phytomonitoring.PhytoMonitoringRepository
import com.example.myapplication.local.api.phytomonitoring.PhytoTargetPointApiItem
import com.example.myapplication.local.api.phytomonitoring.ResultadoPhytoHeadersApi
import com.example.myapplication.local.api.phytomonitoring.ResultadoActualizarHeaderApi
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.myapplication.local.api.agrocatalogs.ResultadoCatalogoFitoSync
import com.example.myapplication.local.api.core.ApiConfig
import com.example.myapplication.local.api.core.ApiDateParser
import com.google.gson.JsonElement
import com.example.myapplication.local.common.ImageCache
import com.example.myapplication.local.monitoreo.media.PhytoMediaStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import androidx.room.withTransaction

class MonitoreoSyncRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        private const val PREFS_SYNC_INCREMENTAL = "sync_incremental"
        private const val KEY_ULTIMA_REVISION_CATALOGOS = "ultima_revision_catalogos"
        private const val INTERVALO_REVISION_CATALOGOS_MS = 24L * 60L * 60L * 1000L
        const val VENTANA_OFFLINE_MS = 30L * 24L * 60L * 60L * 1000L
    }

    private val agroCatalogsRepository = AgroCatalogsRepository(
        context = context,
        database = database
    )
    private val fieldOpsRepository = FieldOpsRepository(context)
    private val phytoMonitoringRepository = PhytoMonitoringRepository(context)

    /*
     * Las imágenes se descargan después de terminar la sincronización de datos.
     * Así el usuario puede continuar trabajando mientras la cache se completa.
     */
    private val imageCacheScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    /**
     * Sincroniza exclusivamente una CIA hija. El parámetro nunca puede ser nulo:
     * así se evita descargar programas globales y mezclarlos en Room.
     */
    suspend fun sincronizarMonitoreosFitosanitarios(
        idLocalCia: Long,
        actualizarCatalogos: Boolean = false,
        programasApiPrecargados: List<FieldTaskApiItem>? = null
    ): ResultadoMonitoreoSync {
        return try {
            val inicioSincronizacion = System.currentTimeMillis()

            var cultivosGuardados = 0
            var programasGuardados = 0
            var headersGuardados = 0
            var puntosGuardados = 0
            var catalogosConImagenesActualizados = false
            val advertencias = mutableListOf<String>()
            val headersOnline = mutableListOf<LocalPhytomonitoringHeaderEntity>()
            val extIdsHeadersPersistidos = linkedSetOf<String>()

            fun advertir(mensaje: String, error: Throwable? = null) {
                if (error == null) {
                    Log.w("SYNC_MON", mensaje)
                } else {
                    Log.e("SYNC_MON", mensaje, error)
                }
                if (advertencias.size < 8) advertencias.add(mensaje)
            }

            val ciaExtId = database.localCiaDao()
                .getCiaById(idLocalCia)
                ?.extId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return ResultadoMonitoreoSync.Error(
                    "La CIA seleccionada no tiene ext_id. No se puede sincronizar monitoreos."
                )

            /*
             * Antes de descargar headers desde API, reintentamos los cambios de estado
             * guardados localmente. Así una pausa/finalización hecha sin red no se pierde
             * cuando llega una respuesta vieja del servidor.
             */
            val extIdsConEstadoLocalSinEnviar = sincronizarEstadosLocalesAntesDeDescargar(
                idLocalCia = idLocalCia,
                onAdvertencia = { mensaje -> advertir(mensaje) }
            )

            /*
             * Cultivos y catálogo fitosanitario son catálogos globales. Descargar
             * todo en cada actualización era uno de los principales cuellos de
             * botella. Solo se cargan la primera vez o cuando se solicite
             * explícitamente actualizar catálogo.
             */
            val hayCultivosLocales = database.localCropCatalogDao()
                .getAllCrops()
                .isNotEmpty()

            val hayCatalogoFitoLocal = database.localphytosanitarycatalogDao()
                .getAllCatalogo()
                .isNotEmpty()

            val ultimaRevisionCatalogos = context.applicationContext
                .getSharedPreferences(PREFS_SYNC_INCREMENTAL, Context.MODE_PRIVATE)
                .getLong(KEY_ULTIMA_REVISION_CATALOGOS, 0L)

            val tocaRevisionPeriodicaCatalogos =
                System.currentTimeMillis() - ultimaRevisionCatalogos >=
                        INTERVALO_REVISION_CATALOGOS_MS

            val revisarCatalogos = actualizarCatalogos ||
                    !hayCultivosLocales ||
                    !hayCatalogoFitoLocal ||
                    tocaRevisionPeriodicaCatalogos

            if (revisarCatalogos) {
                val resultadoCultivos = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                    agroCatalogsRepository.obtenerTodosLosCultivos()
                } ?: return ResultadoMonitoreoSync.Error(
                    "Timeout cultivos: /api/v1/agro-catalogs/crops/ tardó más de 60 segundos"
                )

                val cultivosApi = when (resultadoCultivos) {
                    is ResultadoAgroCatalogsApi.Exito -> resultadoCultivos.cultivos
                    is ResultadoAgroCatalogsApi.Error -> {
                        return ResultadoMonitoreoSync.Error(resultadoCultivos.mensaje)
                    }
                }

                cultivosApi.forEach { cultivoApi ->
                    runCatching { guardarOCrearCultivo(cultivoApi) }
                        .onSuccess { id -> if (id != null) cultivosGuardados++ }
                        .onFailure { error ->
                            advertir(
                                "No se pudo guardar cultivo ${cultivoApi.id}: ${error.message}",
                                error
                            )
                        }
                }

                catalogosConImagenesActualizados = true
            }

            if (revisarCatalogos) {
                when (
                    val resultadoCatalogoFito = kotlinx.coroutines.withTimeoutOrNull(120_000L) {
                        agroCatalogsRepository.sincronizarCatalogoFitosanitario()
                    } ?: return ResultadoMonitoreoSync.Error(
                        "Timeout catálogo fitosanitario: /api/v1/agro-catalogs/phytosanitary/ tardó más de 120 segundos"
                    )
                ) {
                    is ResultadoCatalogoFitoSync.Exito -> {
                        catalogosConImagenesActualizados = true
                    }

                    is ResultadoCatalogoFitoSync.Error -> {
                        return ResultadoMonitoreoSync.Error(resultadoCatalogoFito.mensaje)
                    }
                }
            }

            if (revisarCatalogos) {
                context.applicationContext
                    .getSharedPreferences(PREFS_SYNC_INCREMENTAL, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_ULTIMA_REVISION_CATALOGOS, System.currentTimeMillis())
                    .apply()
            }

            val programasApi = if (programasApiPrecargados != null) {
                programasApiPrecargados
                    .filter { it.id.isNotBlank() }
                    .distinctBy { it.id }
            } else {
                val resultadoProgramas = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                    fieldOpsRepository.obtenerTodosLosProgramasCampo(datacentral = ciaExtId)
                } ?: return ResultadoMonitoreoSync.Error(
                    "Timeout programas: /api/v1/field_ops/tasks/ tardó más de 60 segundos"
                )

                when (resultadoProgramas) {
                    is ResultadoFieldOpsApi.Exito -> resultadoProgramas.programas
                        .filter { it.id.isNotBlank() }
                        .distinctBy { it.id }
                    is ResultadoFieldOpsApi.Error -> return ResultadoMonitoreoSync.Error(
                        resultadoProgramas.mensaje
                    )
                }
            }

            val idsProgramasCia = programasApi.map { it.id }.toSet()

            // Nunca se usa datacentral = null como respaldo. Eso era la fuga entre CIAs.
            database.withTransaction {
            programasApi.forEach { programaApi ->
                runCatching {
                    guardarOCrearPrograma(
                        programaApi = programaApi,
                        idLocalCia = idLocalCia
                    )
                }.onSuccess { idPrograma ->
                    if (idPrograma != null) {
                        programasGuardados++
                    } else {
                        advertir(
                            "Programa ${programaApi.id} omitido: no se pudo relacionar con productor, rancho, parcela o cultivo local."
                        )
                    }
                }.onFailure { error ->
                    advertir("Error guardando programa ${programaApi.id}: ${error.message}", error)
                }
            }
            }

            if (programasApi.isNotEmpty() && programasGuardados == 0) {
                return ResultadoMonitoreoSync.Error(
                    "La API devolvió ${programasApi.size} programas para la CIA, pero ninguno pudo guardarse. " +
                            "Primero revisa la sincronización de productores/ranchos/parcelas."
                )
            }

            val headersPorExtId = linkedMapOf<String, PhytoHeaderApiItem>()

            /*
             * La lista visible de headers se pagina una sola vez. Después se filtra
             * con los programas de la CIA antes de tocar Room. Antes se hacía una
             * petición completa por cada programa y la latencia crecía linealmente.
             */
            when (
                val resultadoHeaders = kotlinx.coroutines.withTimeoutOrNull(120_000L) {
                    phytoMonitoringRepository.obtenerTodosLosHeaders()
                }
            ) {
                null -> return ResultadoMonitoreoSync.Error(
                    "Timeout headers: el listado tardó más de 120 segundos"
                )

                is ResultadoPhytoHeadersApi.Error -> return ResultadoMonitoreoSync.Error(
                    resultadoHeaders.mensaje
                )

                is ResultadoPhytoHeadersApi.Exito -> {
                    resultadoHeaders.headers.forEach { header ->
                        val programaExtId = header.fieldTask?.trim()
                        if (
                            header.id.isNotBlank() &&
                            !programaExtId.isNullOrBlank() &&
                            programaExtId in idsProgramasCia
                        ) {
                            headersPorExtId[header.id] = header
                        }
                    }
                }
            }

            val fechaLimiteOffline = System.currentTimeMillis() - VENTANA_OFFLINE_MS
            val headerDao = database.localphytomonitoringheaderDao()
            val idsHeadersProtegidos = headerDao
                .getIdsHeadersProtegidosPorCia(idLocalCia)
                .toSet()

            database.withTransaction {
            headersPorExtId.values.forEach { headerApi ->
                runCatching {
                    val existente = headerDao.getHeaderByExtId(headerApi.id)
                    val conservarEstadoLocal =
                        headerApi.id in extIdsConEstadoLocalSinEnviar
                    val fechaReferencia = parseFechaApi(headerApi.estimatedStartDate)
                        ?: parseFechaApi(headerApi.startedAt)
                        ?: parseFechaApi(headerApi.createdAt)
                    val estadoRemoto = normalizarEstado(headerApi.status)
                    val esTrabajoActivo = estadoRemoto == "Pendiente" ||
                            estadoRemoto == "En proceso"
                    val debePersistirOffline = fechaReferencia == null ||
                            fechaReferencia >= fechaLimiteOffline ||
                            esTrabajoActivo ||
                            existente?.idHeader?.let { idHeader ->
                                idHeader in idsHeadersProtegidos
                            } == true

                    if (debePersistirOffline) {
                        val idHeaderLocal = guardarOCrearHeader(
                            headerApi = headerApi,
                            idLocalCiaEsperada = idLocalCia,
                            conservarEstadoLocal = conservarEstadoLocal
                        ) ?: return@runCatching null

                        extIdsHeadersPersistidos += headerApi.id
                        headerDao.getHeaderById(idHeaderLocal)
                    } else {
                        construirHeaderLocal(
                            headerApi = headerApi,
                            idLocalCiaEsperada = idLocalCia,
                            conservarEstadoLocal = false,
                            usarIdTemporal = true
                        )
                    }
                }.onSuccess { headerVisible ->
                    if (headerVisible == null) {
                        advertir("Header ${headerApi.id} omitido: no coincide con la CIA seleccionada o faltan relaciones locales.")
                    } else {
                        headersOnline += headerVisible

                        if (headerApi.id in extIdsHeadersPersistidos) {
                            headersGuardados++
                            headerApi.targetPoints.orEmpty().forEach { puntoApi ->
                                runCatching {
                                    guardarOCrearTargetPoint(
                                        puntoApi = puntoApi,
                                        headerExtIdFallback = headerApi.id,
                                        idHeaderLocalFallback = headerVisible.idHeader,
                                        idLocalCiaEsperada = idLocalCia
                                    )
                                }.onSuccess { idPunto ->
                                    if (idPunto != null) puntosGuardados++
                                }.onFailure { error ->
                                    advertir("Error guardando punto del header ${headerApi.id}: ${error.message}", error)
                                }
                            }
                        }
                    }
                }.onFailure { error ->
                    advertir("Error guardando header ${headerApi.id}: ${error.message}", error)
                }
            }
            }

            /*
             * target_points = [] significa que el monitoreo realmente no tiene
             * puntos. Solo null indica que el listado no incluyo ese campo. En ese
             * caso se pide el detalle de ese header; nunca se vuelve a descargar el
             * endpoint global de todos los puntos objetivo.
             */
            headersPorExtId.values
                .filter { header ->
                    header.id in extIdsHeadersPersistidos &&
                            header.targetPoints == null
                }
                .forEach { header ->
                    when (
                        val detalle = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                            phytoMonitoringRepository.obtenerHeaderDetalle(header.id)
                        }
                    ) {
                        null -> advertir("Timeout cargando detalle del header ${header.id}")
                        is ResultadoPhytoHeadersApi.Error -> advertir(detalle.mensaje)
                        is ResultadoPhytoHeadersApi.Exito -> {
                            detalle.headers.firstOrNull()
                                ?.targetPoints
                                .orEmpty()
                                .forEach { puntoApi ->
                                    runCatching {
                                        guardarOCrearTargetPoint(
                                            puntoApi = puntoApi,
                                            headerExtIdFallback = header.id,
                                            idHeaderLocalFallback = null,
                                            idLocalCiaEsperada = idLocalCia
                                        )
                                    }.onSuccess { idPunto ->
                                        if (idPunto != null) puntosGuardados++
                                    }.onFailure { error ->
                                        advertir(
                                            "Error guardando punto ${puntoApi.id}: ${error.message}",
                                            error
                                        )
                                    }
                                }
                        }
                    }
                }

            /*
             * La lista histórica completa vive solo en memoria mientras hay red.
             * Room conserva un mes y cualquier trabajo activo o sin confirmar.
             */
            val idsDepurables = headerDao.getIdsHeadersDepurablesPorCia(
                idLocalCia = idLocalCia,
                fechaLimite = fechaLimiteOffline
            )

            if (idsDepurables.isNotEmpty()) {
                idsDepurables.forEach { idHeader ->
                    PhytoMediaStorage.eliminarFotosConfirmadasDeHeader(
                        context = context.applicationContext,
                        idHeader = idHeader
                    )
                }
                headerDao.eliminarHeadersPorIds(idsDepurables)
                Log.d(
                    "SYNC_MON",
                    "Cache offline depurada: ${idsDepurables.size} headers anteriores a 30 días"
                )
            }

            /*
             * Las imágenes no bloquean la sincronización principal. Solo los datos
             * necesarios para trabajar quedan listos antes de mostrar "Sincronizado".
             * Las fotografías nuevas continúan descargándose en segundo plano.
             */
            if (catalogosConImagenesActualizados) {
                imageCacheScope.launch {
                    runCatching {
                        precachearImagenesOffline(forceRefresh = false)
                    }.onFailure { error ->
                        Log.w(
                            "SYNC_MON",
                            "No se pudo completar la cache de imágenes: ${error.message}",
                            error
                        )
                    }
                }
            }

            val imagenesCacheadas = 0

            Log.d(
                "SYNC_MON",
                "Sincronización de datos terminada en " +
                        "${System.currentTimeMillis() - inicioSincronizacion} ms"
            )

            ResultadoMonitoreoSync.Exito(
                cultivos = cultivosGuardados,
                programas = programasGuardados,
                headers = headersGuardados,
                targetPoints = puntosGuardados,
                imagenes = imagenesCacheadas,
                advertencias = advertencias.distinct(),
                headersOnline = headersOnline
                    .distinctBy { it.extId }
                    .sortedByDescending { it.estStartDate ?: 0L }
            )
        } catch (e: Exception) {
            Log.e("SYNC_MON", "Error sincronizando monitoreos", e)
            ResultadoMonitoreoSync.Error(
                "Error sincronizando monitoreos: ${e.message ?: "detalle no disponible"}"
            )
        }
    }

    /**
     * Materializa en Room un header histórico que hasta ahora solo vivía en la
     * lista online. Esto permite abrir su reporte sin conservar todo el histórico.
     */
    suspend fun prepararHeaderParaAbrir(
        headerVisible: LocalPhytomonitoringHeaderEntity
    ): ResultadoPrepararHeader {
        val extId = headerVisible.extId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return ResultadoPrepararHeader.Error(
                "El monitoreo no tiene identificador remoto."
            )

        database.localphytomonitoringheaderDao()
            .getHeaderByExtId(extId)
            ?.let { return ResultadoPrepararHeader.Exito(it) }

        val detalle = when (
            val resultado = phytoMonitoringRepository.obtenerHeaderDetalle(extId)
        ) {
            is ResultadoPhytoHeadersApi.Error -> {
                return ResultadoPrepararHeader.Error(resultado.mensaje)
            }

            is ResultadoPhytoHeadersApi.Exito -> resultado.headers.firstOrNull()
                ?: return ResultadoPrepararHeader.Error(
                    "La API no devolvió el monitoreo solicitado."
                )
        }

        val programaExtId = detalle.fieldTask
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return ResultadoPrepararHeader.Error(
                "El monitoreo no tiene programa relacionado."
            )

        val programa = database.localprogramDao().getProgramByExtId(programaExtId)
            ?: return ResultadoPrepararHeader.Error(
                "Primero sincroniza la información de la CIA de este monitoreo."
            )

        val idHeader = guardarOCrearHeader(
            headerApi = detalle,
            idLocalCiaEsperada = programa.idLocalCia,
            conservarEstadoLocal = false
        ) ?: return ResultadoPrepararHeader.Error(
            "No se pudo preparar el monitoreo para abrirlo."
        )

        detalle.targetPoints.orEmpty().forEach { puntoApi ->
            guardarOCrearTargetPoint(
                puntoApi = puntoApi,
                headerExtIdFallback = extId,
                idHeaderLocalFallback = idHeader,
                idLocalCiaEsperada = programa.idLocalCia
            )
        }

        val local = database.localphytomonitoringheaderDao().getHeaderById(idHeader)
            ?: return ResultadoPrepararHeader.Error(
                "El monitoreo se descargó, pero Room no pudo encontrarlo."
            )

        return ResultadoPrepararHeader.Exito(local)
    }


    private suspend fun guardarOCrearCultivo(
        cultivoApi: AgroCropApiItem
    ): Long? {
        val extId = cultivoApi.id?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val nombre = cultivoApi.name
            ?.takeIf { it.isNotBlank() }
            ?: cultivoApi.code
            ?: "Cultivo $extId"

        val fotoCultivoNueva = obtenerFotoCultivo(cultivoApi)

        val existente = database.localCropCatalogDao()
            .getCropByExtId(extId)

        /*
         * Los cultivos que llegan embebidos en FieldTask a veces no incluyen foto.
         * No se debe borrar la imagen completa que ya vino del catálogo/detalle.
         */
        val fotoCultivoFinal = fotoCultivoNueva
            ?.takeIf { it.isNotBlank() }
            ?: existente?.photo

        return if (existente != null) {
            val actualizado = existente.copy(
                extId = extId,
                name = nombre,
                variedad = cultivoApi.variety,
                code = cultivoApi.code,
                description = cultivoApi.description,
                photo = fotoCultivoFinal
            )

            if (actualizado != existente) {
                database.localCropCatalogDao().updateCrop(actualizado)
            }

            existente.idCrop
        } else {
            database.localCropCatalogDao().insertCrop(
                LocalCropCatalogEntity(
                    extId = extId,
                    name = nombre,
                    variedad = cultivoApi.variety,
                    code = cultivoApi.code,
                    description = cultivoApi.description,
                    photo = fotoCultivoFinal
                )
            )
        }
    }
    private fun obtenerFotoCultivo(
        cultivoApi: AgroCropApiItem
    ): String? {
        return normalizarUrlImagen(
            cultivoApi.photo
                ?: buscarUrlEnJson(cultivoApi.attachmentsUrl)
                ?: buscarUrlEnJson(cultivoApi.additionalParams)
        )
    }

    private fun buscarUrlEnJson(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        return when {
            element.isJsonPrimitive -> {
                val text = runCatching { element.asString }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                if (pareceImagen(text)) text else null
            }

            element.isJsonArray -> {
                element.asJsonArray.firstNotNullOfOrNull { child ->
                    buscarUrlEnJson(child)
                }
            }

            element.isJsonObject -> {
                val obj = element.asJsonObject

                val campos = listOf(
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

                campos.firstNotNullOfOrNull { key ->
                    val value = if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key) else null
                    buscarUrlEnJson(value)
                } ?: obj.entrySet().firstNotNullOfOrNull { entry ->
                    buscarUrlEnJson(entry.value)
                }
            }

            else -> null
        }
    }

    private fun pareceImagen(value: String?): Boolean {
        if (value.isNullOrBlank()) return false

        val text = value.lowercase(Locale.getDefault())

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
                text.endsWith(".webp")
    }

    private fun normalizarUrlImagen(value: String?): String? {
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

    private suspend fun guardarOCrearPrograma(
        programaApi: FieldTaskApiItem,
        idLocalCia: Long
    ): Long? {
        val extId = programaApi.id.takeIf { it.isNotBlank() } ?: return null

        val parcelaExtId = programaApi.plot?.takeIf { it.isNotBlank() } ?: run {
            Log.w("SYNC_MON", "Programa $extId sin plot")
            return null
        }

        val parcelaLocal = database.localPlotDao().getPlotByExtId(parcelaExtId) ?: run {
            Log.w("SYNC_MON", "Programa $extId: la parcela $parcelaExtId no existe localmente")
            return null
        }

        val ranchoLocal = database.localRanchDao().getRanchById(parcelaLocal.idLocalRanch) ?: run {
            Log.w("SYNC_MON", "Programa $extId: no existe rancho local de parcela $parcelaExtId")
            return null
        }

        val productorLocal = programaApi.agroUnit
            ?.takeIf { it.isNotBlank() }
            ?.let { database.localAgroUnitDao().getAgroUnitByExtId(it) }
            ?: database.localAgroUnitDao().getAgroUnitById(ranchoLocal.idLocalAgroUnit)
            ?: run {
                Log.w("SYNC_MON", "Programa $extId: no existe productor local")
                return null
            }

        val productorPerteneceACia = database.localCiaAgroUnitDao()
            .getProductoresByCia(idLocalCia)
            .any { it.idLocalAgroUnit == productorLocal.idLocalAgroUnit }

        if (!productorPerteneceACia) {
            Log.w("SYNC_MON", "Programa $extId omitido: productor fuera de CIA $idLocalCia")
            return null
        }

        val cropApi = programaApi.crop ?: programaApi.cropVariety ?: run {
            Log.w("SYNC_MON", "Programa $extId sin cultivo")
            return null
        }

        val cultivoExtId = cropApi.id?.toString()?.takeIf { it.isNotBlank() } ?: return null

        guardarOCrearCultivo(
            AgroCropApiItem(
                id = cropApi.id,
                name = cropApi.name,
                code = cropApi.code,
                variety = cropApi.variety,
                description = cropApi.description,
                photo = cropApi.photo,
                additionalParams = cropApi.additionalParams,
                attachmentsUrl = cropApi.attachmentsUrl
            )
        )

        val cultivoLocal = database.localCropCatalogDao().getCropByExtId(cultivoExtId) ?: return null
        val existente = database.localprogramDao().getProgramByExtId(extId)

        val programaNuevo = LocalProgramEntity(
            idProgram = existente?.idProgram ?: 0L,
            extId = extId,
            cycle = programaApi.cycle?.takeIf { it.isNotBlank() } ?: "Sin ciclo",
            estStartDate = parseFechaApi(programaApi.estStartDate) ?: 0L,
            estFinishDate = parseFechaApi(programaApi.estFinishDate) ?: 0L,
            actStartDate = parseFechaApi(programaApi.actualStartDate),
            actFinishDate = parseFechaApi(programaApi.actualFinishDate),
            status = normalizarEstado(programaApi.status),
            idLocalCia = idLocalCia,
            idLocalAgroUnit = productorLocal.idLocalAgroUnit,
            idLocalRanch = parcelaLocal.idLocalRanch,
            idCrop = cultivoLocal.idCrop,
            idLocalPlot = parcelaLocal.idLocalPlot
        )

        return if (existente != null) {
            if (programaNuevo != existente) {
                database.localprogramDao().updateProgram(programaNuevo)
            }
            existente.idProgram
        } else {
            database.localprogramDao().insertProgram(programaNuevo)
        }
    }


    private suspend fun guardarOCrearHeader(
        headerApi: PhytoHeaderApiItem,
        idLocalCiaEsperada: Long,
        conservarEstadoLocal: Boolean = false
    ): Long? {
        val headerNuevo = construirHeaderLocal(
            headerApi = headerApi,
            idLocalCiaEsperada = idLocalCiaEsperada,
            conservarEstadoLocal = conservarEstadoLocal,
            usarIdTemporal = false
        ) ?: return null

        val existente = database.localphytomonitoringheaderDao()
            .getHeaderByExtId(headerNuevo.extId.orEmpty())

        val idHeaderLocal = if (existente != null) {
            if (headerNuevo != existente) {
                database.localphytomonitoringheaderDao().updateHeader(headerNuevo)
            }
            existente.idHeader
        } else {
            database.localphytomonitoringheaderDao().insertHeader(headerNuevo)
        }

        database.localprogramDao().recalcularEstadoDesdeHeaders(headerNuevo.idProgram)
        return idHeaderLocal
    }

    private suspend fun construirHeaderLocal(
        headerApi: PhytoHeaderApiItem,
        idLocalCiaEsperada: Long,
        conservarEstadoLocal: Boolean,
        usarIdTemporal: Boolean
    ): LocalPhytomonitoringHeaderEntity? {
        val extId = headerApi.id.takeIf { it.isNotBlank() } ?: return null
        val programaExtId = headerApi.fieldTask?.takeIf { it.isNotBlank() } ?: return null
        val parcelaExtId = headerApi.plot?.takeIf { it.isNotBlank() } ?: return null

        val programaLocal = database.localprogramDao().getProgramByExtId(programaExtId) ?: return null
        if (programaLocal.idLocalCia != idLocalCiaEsperada) {
            Log.w("SYNC_MON", "Header $extId omitido: programa de otra CIA")
            return null
        }

        val parcelaLocal = database.localPlotDao().getPlotByExtId(parcelaExtId) ?: return null
        if (parcelaLocal.idLocalPlot != programaLocal.idLocalPlot) {
            Log.w("SYNC_MON", "Header $extId omitido: plot no coincide con su programa")
            return null
        }

        val cultivoLocal = headerApi.crop
            ?.toString()
            ?.let { database.localCropCatalogDao().getCropByExtId(it) }

        val usuarioAsignado = headerApi.assignedTo
            ?.takeIf { it.isNotBlank() }
            ?.let { database.userDao().getUserByExtId(it) }

        val existente = database.localphytomonitoringheaderDao().getHeaderByExtId(extId)
        return LocalPhytomonitoringHeaderEntity(
            idHeader = if (usarIdTemporal) {
                idTemporalDesdeExtId(extId)
            } else {
                existente?.idHeader ?: 0L
            },
            extId = extId,
            cycle = programaLocal.cycle,
            estStartDate = parseFechaApi(headerApi.estimatedStartDate),
            estFinishDate = parseFechaApi(headerApi.estimatedEndDate),
            // Cuando el PATCH no pudo salir, Room conserva el cambio local hasta reintentar.
            startAt = if (conservarEstadoLocal) {
                existente?.startAt ?: parseFechaApi(headerApi.startedAt)
            } else {
                parseFechaApi(headerApi.startedAt)
            },
            finishedAt = if (conservarEstadoLocal) {
                existente?.finishedAt ?: parseFechaApi(headerApi.finishedAt)
            } else {
                parseFechaApi(headerApi.finishedAt)
            },
            additionalNotes = if (conservarEstadoLocal) {
                existente?.additionalNotes ?: headerApi.additionalNotes.orEmpty()
            } else {
                headerApi.additionalNotes.orEmpty()
            },
            radiusTolerance = headerApi.radiusTolerance ?: 50.0,
            status = if (conservarEstadoLocal) {
                existente?.status ?: normalizarEstado(headerApi.status)
            } else {
                normalizarEstado(headerApi.status)
            },
            idProgram = programaLocal.idProgram,
            idCrop = cultivoLocal?.idCrop ?: programaLocal.idCrop,
            idLocalPlot = parcelaLocal.idLocalPlot,
            assignedUserId = usuarioAsignado?.idUser,
            syncPending = if (conservarEstadoLocal) {
                existente?.syncPending ?: true
            } else {
                false
            }
        )
    }

    private fun idTemporalDesdeExtId(extId: String): Long {
        var hash = -3750763034362895579L
        extId.forEach { caracter ->
            hash = hash xor caracter.code.toLong()
            hash *= 1099511628211L
        }

        val positivo = hash and Long.MAX_VALUE
        return -(positivo.coerceAtLeast(1L))
    }


    private suspend fun guardarOCrearTargetPoint(
        puntoApi: PhytoTargetPointApiItem,
        headerExtIdFallback: String?,
        idHeaderLocalFallback: Long?,
        idLocalCiaEsperada: Long
    ): Long? {
        val extId = puntoApi.id.takeIf { it.isNotBlank() } ?: return null
        val headerExtId = puntoApi.header?.takeIf { it.isNotBlank() } ?: headerExtIdFallback

        val headerLocal = headerExtId
            ?.takeIf { it.isNotBlank() }
            ?.let { database.localphytomonitoringheaderDao().getHeaderByExtId(it) }

        val idHeaderLocal = headerLocal?.idHeader ?: idHeaderLocalFallback ?: return null
        val headerSeguro = headerLocal
            ?: database.localphytomonitoringheaderDao().getHeaderById(idHeaderLocal)
            ?: return null

        val programa = database.localprogramDao().getProgramById(headerSeguro.idProgram) ?: return null
        if (programa.idLocalCia != idLocalCiaEsperada) {
            return null
        }

        val parcelaLocal = puntoApi.plot
            ?.takeIf { it.isNotBlank() }
            ?.let { database.localPlotDao().getPlotByExtId(it) }

        val idLocalPlot = parcelaLocal?.idLocalPlot ?: headerSeguro.idLocalPlot
        if (idLocalPlot != headerSeguro.idLocalPlot) {
            Log.w("SYNC_MON", "Target point $extId omitido: plot no coincide con header")
            return null
        }

        val coordenadas = extraerLatLon(puntoApi.geom?.coordinates) ?: return null
        val existente = database.LocalPhytomonitoringTargetPointDao().getTargetPointByExtId(extId)

        val etiquetaApi = puntoApi.label
            ?.trim()
            .orEmpty()
            .ifBlank { existente?.label.orEmpty() }

        val puntoNuevo = LocalPhytomonitoringTargetPointEntity(
            idTargetPoint = existente?.idTargetPoint ?: 0L,
            extId = extId,
            label = etiquetaApi,
            radiusM = (puntoApi.radiusM ?: 0.5).roundToInt().coerceAtLeast(1),
            lat = coordenadas.first,
            lon = coordenadas.second,
            status = normalizarEstado(puntoApi.status),
            idHeader = idHeaderLocal,
            idLocalPlot = idLocalPlot
        )

        return if (existente != null) {
            if (puntoNuevo != existente) {
                database.LocalPhytomonitoringTargetPointDao().updateTargetPoint(puntoNuevo)
            }
            existente.idTargetPoint
        } else {
            database.LocalPhytomonitoringTargetPointDao().insertTargetPoint(puntoNuevo)
        }
    }



    /**
     * Descarga cada imagen al almacenamiento privado para usarla sin conexión.
     * Room conserva la URL remota para poder volver a descargarla si el archivo
     * local se elimina, se daña o cambia el túnel del backend.
     */
    private suspend fun precachearImagenesOffline(
        forceRefresh: Boolean
    ): Int = coroutineScope {
        val fotos = buildList {
            database.localCropCatalogDao()
                .getAllCrops()
                .mapNotNullTo(this) { it.photo?.trim()?.takeIf(String::isNotBlank) }

            database.localphytosanitarycatalogDao()
                .getAllCatalogo()
                .mapNotNullTo(this) { it.photo?.trim()?.takeIf(String::isNotBlank) }

            database.localphytostageDao()
                .getAllPhytostages()
                .mapNotNullTo(this) { it.photo?.trim()?.takeIf(String::isNotBlank) }
        }.distinct()

        if (fotos.isEmpty()) {
            Log.d("SYNC_MON", "No hay imágenes de catálogo para precargar")
            return@coroutineScope 0
        }

        /*
         * Se descargan hasta cuatro imágenes en paralelo. Antes se hacía una por una,
         * por lo que una URL lenta o inválida podía detener toda la sincronización.
         */
        val limite = Semaphore(permits = 4)

        val resultados = fotos.map { fotoRemota ->
            async(Dispatchers.IO) {
                limite.acquire()
                try {
                    runCatching {
                        ImageCache.guardarEnCache(
                            context = context.applicationContext,
                            photo = fotoRemota,
                            forceRefresh = forceRefresh
                        )
                    }.onFailure { error ->
                        Log.w(
                            "SYNC_MON",
                            "No se pudo precargar imagen $fotoRemota: ${error.message}",
                            error
                        )
                    }.getOrNull() != null
                } finally {
                    limite.release()
                }
            }
        }.awaitAll()

        val cacheadas = resultados.count { it }

        Log.d(
            "SYNC_MON",
            "Imágenes disponibles en cache: $cacheadas de ${fotos.size}"
        )

        cacheadas
    }

    private suspend fun sincronizarEstadosLocalesAntesDeDescargar(
        idLocalCia: Long,
        onAdvertencia: (String) -> Unit
    ): Set<String> {
        val extIdsConError = linkedSetOf<String>()

        val headerDao = database.localphytomonitoringheaderDao()

        headerDao
            .getHeadersPendingSyncByCia(idLocalCia)
            .forEach { headerLocal ->
                val estadoApi = estadoApiDesdeLocal(headerLocal.status)
                    ?: return@forEach

                val extId = headerLocal.extId
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@forEach

                /*
                 * El backend bloquea CSV/ZIP/patches de checkpoint cuando el
                 * header ya está completed. Si aún falta crear targets, subir
                 * checkpoints o mover fotos pending, el cierre remoto se difiere
                 * hasta que ReporteMonitoreoScreen ejecute la sincronización completa.
                 */
                if (
                    estadoApi == "completed" &&
                    debeDiferirCierreRemoto(headerLocal)
                ) {
                    extIdsConError.add(extId)
                    onAdvertencia(
                        "El cierre de $extId se mantiene localmente hasta subir targets, capturas y evidencias."
                    )
                    return@forEach
                }

                when (
                    val resultado = phytoMonitoringRepository.actualizarHeaderServidor(
                        idHeaderExt = extId,
                        status = estadoApi,
                        startedAt = fechaApiUtc(headerLocal.startAt),
                        finishedAt = fechaApiUtc(headerLocal.finishedAt),
                        additionalNotes = headerLocal.additionalNotes
                    )
                ) {
                    is ResultadoActualizarHeaderApi.Exito -> {
                        headerDao.marcarHeaderSincronizado(headerLocal.idHeader)
                        Log.d("SYNC_MON", "Estado enviado para header $extId: $estadoApi")
                    }

                    is ResultadoActualizarHeaderApi.Error -> {
                        extIdsConError.add(extId)
                        onAdvertencia(
                            "El estado local de $extId no pudo enviarse y se conservará: ${resultado.mensaje}"
                        )
                    }
                }
            }

        return extIdsConError
    }

    private suspend fun debeDiferirCierreRemoto(
        headerLocal: LocalPhytomonitoringHeaderEntity
    ): Boolean {
        val checkpoints = database.localphytomonitoringcheckpointDao()
            .getCheckpointsByHeader(headerLocal.idHeader)

        // Captura local aún sin POST /checkpoints/create/.
        if (checkpoints.any { it.extId.isNullOrBlank() }) return true

        // Un checkpoint ya existente pero sin UUID de su target requiere PATCH
        // antes de cerrar el header remoto.
        val targetsPorId = database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointsByHeader(headerLocal.idHeader)
            .associateBy { it.idTargetPoint }

        if (
            checkpoints.any { checkpoint ->
                targetsPorId[checkpoint.idTargetPoint]
                    ?.extId
                    .isNullOrBlank()
            }
        ) {
            return true
        }

        // Fotos aún en pending deben subir antes del PATCH completed.
        return PhytoMediaStorage.contarFotosPendientes(
            context = context.applicationContext,
            idHeader = headerLocal.idHeader
        ) > 0
    }

    private fun estadoApiDesdeLocal(status: String): String? {
        return when (status.trim().lowercase(Locale.getDefault())) {
            "pendiente", "pending" -> "pending"
            "en proceso", "in_progress", "vigente" -> "in_progress"
            "completado", "completed", "finalizado", "terminado", "cerrado" -> "completed"
            "cancelado", "cancelled", "canceled" -> "cancelled"
            else -> null
        }
    }

    private fun fechaApiUtc(millis: Long?): String? {
        if (millis == null) return null

        return SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }

    private fun normalizarEstado(status: String?): String {
        return when (status?.trim()?.lowercase(Locale.getDefault())) {
            "pending", "pendiente" -> "Pendiente"
            "in_progress", "en proceso", "vigente" -> "En proceso"
            "completed", "complete", "completado", "finalizado", "terminado", "cerrado" -> "Completado"
            "cancelled", "canceled", "cancelado" -> "Cancelado"
            else -> "Pendiente"
        }
    }

    private fun parseFechaApi(fecha: String?): Long? {
        return ApiDateParser.parsearMillis(fecha)
    }

    private fun extraerLatLon(coordinates: List<Double>?): Pair<Double, Double>? {
        if (coordinates == null || coordinates.size < 2) return null

        val a = coordinates[0]
        val b = coordinates[1]

        return when {
            abs(a) > 90.0 && abs(b) <= 90.0 -> {
                // [lon, lat]
                b to a
            }

            abs(b) > 90.0 && abs(a) <= 90.0 -> {
                // [lat, lon]
                a to b
            }

            else -> {
                // Default GeoJSON: [lon, lat]
                b to a
            }
        }
    }
}

sealed class ResultadoMonitoreoSync {
    data class Exito(
        val cultivos: Int,
        val programas: Int,
        val headers: Int,
        val targetPoints: Int,
        val imagenes: Int = 0,
        val advertencias: List<String> = emptyList(),
        val headersOnline: List<LocalPhytomonitoringHeaderEntity> = emptyList()
    ) : ResultadoMonitoreoSync()

    data class Error(
        val mensaje: String
    ) : ResultadoMonitoreoSync()
}

sealed class ResultadoPrepararHeader {
    data class Exito(
        val header: LocalPhytomonitoringHeaderEntity
    ) : ResultadoPrepararHeader()

    data class Error(
        val mensaje: String
    ) : ResultadoPrepararHeader()
}

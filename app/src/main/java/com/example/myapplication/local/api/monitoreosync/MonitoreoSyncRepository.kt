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
import com.example.myapplication.local.api.phytomonitoring.ResultadoPhytoTargetPointsApi
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.myapplication.local.api.agrocatalogs.ResultadoCatalogoFitoSync
import com.example.myapplication.local.api.core.ApiConfig
import com.google.gson.JsonElement
import com.example.myapplication.local.common.ImageCache

class MonitoreoSyncRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val agroCatalogsRepository = AgroCatalogsRepository(
        context = context,
        database = database
    )
    private val fieldOpsRepository = FieldOpsRepository(context)
    private val phytoMonitoringRepository = PhytoMonitoringRepository(context)

    /**
     * Sincroniza exclusivamente una CIA hija. El parámetro nunca puede ser nulo:
     * así se evita descargar programas globales y mezclarlos en Room.
     */
    suspend fun sincronizarMonitoreosFitosanitarios(
        idLocalCia: Long
    ): ResultadoMonitoreoSync {
        return try {
            var cultivosGuardados = 0
            var programasGuardados = 0
            var headersGuardados = 0
            var puntosGuardados = 0
            val advertencias = mutableListOf<String>()

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

            val resultadoCultivos = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                agroCatalogsRepository.obtenerTodosLosCultivos()
            } ?: return ResultadoMonitoreoSync.Error(
                "Timeout cultivos: /api/v1/agro-catalogs/crops/ tardó más de 60 segundos"
            )

            val cultivosApi = when (resultadoCultivos) {
                is ResultadoAgroCatalogsApi.Exito -> resultadoCultivos.cultivos
                is ResultadoAgroCatalogsApi.Error -> return ResultadoMonitoreoSync.Error(resultadoCultivos.mensaje)
            }

            cultivosApi.forEach { cultivoApi ->
                runCatching { guardarOCrearCultivo(cultivoApi) }
                    .onSuccess { id -> if (id != null) cultivosGuardados++ }
                    .onFailure { error ->
                        advertir("No se pudo guardar cultivo ${cultivoApi.id}: ${error.message}", error)
                    }
            }

            when (
                val resultadoCatalogoFito = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                    agroCatalogsRepository.sincronizarCatalogoFitosanitario()
                } ?: return ResultadoMonitoreoSync.Error(
                    "Timeout catálogo fitosanitario: /api/v1/agro-catalogs/phytosanitary/ tardó más de 60 segundos"
                )
            ) {
                is ResultadoCatalogoFitoSync.Exito -> Unit
                is ResultadoCatalogoFitoSync.Error -> return ResultadoMonitoreoSync.Error(
                    resultadoCatalogoFito.mensaje
                )
            }

            val resultadoProgramas = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                fieldOpsRepository.obtenerTodosLosProgramasCampo(datacentral = ciaExtId)
            } ?: return ResultadoMonitoreoSync.Error(
                "Timeout programas: /api/v1/field_ops/tasks/ tardó más de 60 segundos"
            )

            val programasApi = when (resultadoProgramas) {
                is ResultadoFieldOpsApi.Exito -> resultadoProgramas.programas
                    .filter { it.id.isNotBlank() }
                    .distinctBy { it.id }
                is ResultadoFieldOpsApi.Error -> return ResultadoMonitoreoSync.Error(resultadoProgramas.mensaje)
            }

            val idsProgramasCia = programasApi.map { it.id }.toSet()

            // Nunca se usa datacentral = null como respaldo. Eso era la fuga entre CIAs.
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

            if (programasApi.isNotEmpty() && programasGuardados == 0) {
                return ResultadoMonitoreoSync.Error(
                    "La API devolvió ${programasApi.size} programas para la CIA, pero ninguno pudo guardarse. " +
                            "Primero revisa la sincronización de productores/ranchos/parcelas."
                )
            }

            // Los headers se piden por field_task para que el servidor no entregue otras CIAs.
            val headersPorExtId = linkedMapOf<String, PhytoHeaderApiItem>()
            idsProgramasCia.forEach { programaExtId ->
                when (
                    val resultadoHeaders = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                        phytoMonitoringRepository.obtenerTodosLosHeaders(fieldTask = programaExtId)
                    }
                ) {
                    null -> advertir("Timeout headers para programa $programaExtId")
                    is ResultadoPhytoHeadersApi.Error -> {
                        advertir("No se pudieron cargar headers de $programaExtId: ${resultadoHeaders.mensaje}")
                    }
                    is ResultadoPhytoHeadersApi.Exito -> {
                        resultadoHeaders.headers.forEach { header ->
                            if (header.id.isNotBlank() && header.fieldTask == programaExtId) {
                                headersPorExtId[header.id] = header
                            }
                        }
                    }
                }
            }

            headersPorExtId.values.forEach { headerApi ->
                runCatching {
                    guardarOCrearHeader(
                        headerApi = headerApi,
                        idLocalCiaEsperada = idLocalCia
                    )
                }.onSuccess { idHeaderLocal ->
                    if (idHeaderLocal == null) {
                        advertir("Header ${headerApi.id} omitido: no coincide con la CIA seleccionada o faltan relaciones locales.")
                    } else {
                        headersGuardados++
                        headerApi.targetPoints.forEach { puntoApi ->
                            runCatching {
                                guardarOCrearTargetPoint(
                                    puntoApi = puntoApi,
                                    headerExtIdFallback = headerApi.id,
                                    idHeaderLocalFallback = idHeaderLocal,
                                    idLocalCiaEsperada = idLocalCia
                                )
                            }.onSuccess { idPunto ->
                                if (idPunto != null) puntosGuardados++
                            }.onFailure { error ->
                                advertir("Error guardando punto del header ${headerApi.id}: ${error.message}", error)
                            }
                        }
                    }
                }.onFailure { error ->
                    advertir("Error guardando header ${headerApi.id}: ${error.message}", error)
                }
            }

            // Algunos servidores no incluyen target_points dentro del header. El endpoint es global,
            // pero guardarOCrearTargetPoint valida la CIA del programa antes de persistir cada fila.
            when (
                val resultadoTargetPoints = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                    phytoMonitoringRepository.obtenerTodosLosTargetPoints()
                }
            ) {
                null -> advertir("Timeout cargando puntos objetivo globales")
                is ResultadoPhytoTargetPointsApi.Error -> {
                    advertir("No se pudieron cargar puntos objetivo: ${resultadoTargetPoints.mensaje}")
                }
                is ResultadoPhytoTargetPointsApi.Exito -> {
                    resultadoTargetPoints.puntos.forEach { puntoApi ->
                        runCatching {
                            guardarOCrearTargetPoint(
                                puntoApi = puntoApi,
                                headerExtIdFallback = puntoApi.header,
                                idHeaderLocalFallback = null,
                                idLocalCiaEsperada = idLocalCia
                            )
                        }.onSuccess { idPunto ->
                            if (idPunto != null) puntosGuardados++
                        }.onFailure { error ->
                            advertir("Error guardando punto ${puntoApi.id}: ${error.message}", error)
                        }
                    }
                }
            }

            val imagenesCacheadas = precachearImagenesOffline()

            ResultadoMonitoreoSync.Exito(
                cultivos = cultivosGuardados,
                programas = programasGuardados,
                headers = headersGuardados,
                targetPoints = puntosGuardados,
                imagenes = imagenesCacheadas,
                advertencias = advertencias.distinct()
            )
        } catch (e: Exception) {
            Log.e("SYNC_MON", "Error sincronizando monitoreos", e)
            ResultadoMonitoreoSync.Error(
                "Error sincronizando monitoreos: ${e.message ?: "detalle no disponible"}"
            )
        }
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

        val fotoCultivo = obtenerFotoCultivo(cultivoApi)

        val existente = database.localCropCatalogDao()
            .getCropByExtId(extId)

        return if (existente != null) {
            database.localCropCatalogDao().updateCrop(
                existente.copy(
                    extId = extId,
                    name = nombre,
                    variedad = cultivoApi.variety,
                    code = cultivoApi.code,
                    description = cultivoApi.description,
                    photo = fotoCultivo
                )
            )

            existente.idCrop
        } else {
            database.localCropCatalogDao().insertCrop(
                LocalCropCatalogEntity(
                    extId = extId,
                    name = nombre,
                    variedad = cultivoApi.variety,
                    code = cultivoApi.code,
                    description = cultivoApi.description,
                    photo = fotoCultivo
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
                    "url",
                    "file",
                    "image",
                    "photo",
                    "path",
                    "href",
                    "attachment_url",
                    "image_url",
                    "photo_url"
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
            database.localprogramDao().updateProgram(programaNuevo)
            existente.idProgram
        } else {
            database.localprogramDao().insertProgram(programaNuevo)
        }
    }


    private suspend fun guardarOCrearHeader(
        headerApi: PhytoHeaderApiItem,
        idLocalCiaEsperada: Long
    ): Long? {
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
        val headerNuevo = LocalPhytomonitoringHeaderEntity(
            idHeader = existente?.idHeader ?: 0L,
            extId = extId,
            cycle = programaLocal.cycle,
            estStartDate = parseFechaApi(headerApi.estimatedStartDate),
            estFinishDate = parseFechaApi(headerApi.estimatedEndDate),
            startAt = parseFechaApi(headerApi.startedAt),
            finishedAt = parseFechaApi(headerApi.finishedAt),
            additionalNotes = headerApi.additionalNotes.orEmpty(),
            radiusTolerance = headerApi.radiusTolerance ?: 50.0,
            status = normalizarEstado(headerApi.status),
            idProgram = programaLocal.idProgram,
            idCrop = cultivoLocal?.idCrop ?: programaLocal.idCrop,
            idLocalPlot = parcelaLocal.idLocalPlot,
            assignedUserId = usuarioAsignado?.idUser
        )

        val idHeaderLocal = if (existente != null) {
            database.localphytomonitoringheaderDao().updateHeader(headerNuevo)
            existente.idHeader
        } else {
            database.localphytomonitoringheaderDao().insertHeader(headerNuevo)
        }

        database.localprogramDao().recalcularEstadoDesdeHeaders(programaLocal.idProgram)
        return idHeaderLocal
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
            database.LocalPhytomonitoringTargetPointDao().updateTargetPoint(puntoNuevo)
            existente.idTargetPoint
        } else {
            database.LocalPhytomonitoringTargetPointDao().insertTargetPoint(puntoNuevo)
        }
    }



    private suspend fun precachearImagenesOffline(): Int {
        val fotos = mutableSetOf<String>()

        database.localCropCatalogDao()
            .getAllCrops()
            .mapNotNull { it.photo?.takeIf { photo -> photo.isNotBlank() } }
            .forEach { fotos.add(it) }

        database.localphytosanitarycatalogDao()
            .getAllCatalogo()
            .mapNotNull { it.photo?.takeIf { photo -> photo.isNotBlank() } }
            .forEach { fotos.add(it) }

        database.localphytostageDao()
            .getAllPhytostages()
            .mapNotNull { it.photo?.takeIf { photo -> photo.isNotBlank() } }
            .forEach { fotos.add(it) }

        var guardadas = 0

        fotos.forEach { foto ->
            if (ImageCache.guardarEnCache(context, foto) != null) {
                guardadas++
            }
        }

        return guardadas
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
        if (fecha.isNullOrBlank()) return null

        val limpia = fecha.trim()

        val formatos = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )

        formatos.forEach { patron ->
            try {
                val sdf = SimpleDateFormat(patron, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                return sdf.parse(limpia)?.time
            } catch (_: Exception) {
            }
        }

        return null
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
        val advertencias: List<String> = emptyList()
    ) : ResultadoMonitoreoSync()

    data class Error(
        val mensaje: String
    ) : ResultadoMonitoreoSync()
}
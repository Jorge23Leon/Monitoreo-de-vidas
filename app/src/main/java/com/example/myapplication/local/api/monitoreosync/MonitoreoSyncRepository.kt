package com.example.myapplication.local.api.monitoreosync

import android.content.Context
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
import com.example.myapplication.local.entities.LocalCiaAgroUnitCrossRef

class MonitoreoSyncRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val agroCatalogsRepository = AgroCatalogsRepository(context)
    private val fieldOpsRepository = FieldOpsRepository(context)
    private val phytoMonitoringRepository = PhytoMonitoringRepository(context)

    suspend fun sincronizarMonitoreosFitosanitarios(
        idLocalCia: Long? = null
    ): ResultadoMonitoreoSync {
        return try {
            var cultivosGuardados = 0
            var programasGuardados = 0
            var headersGuardados = 0
            var puntosGuardados = 0

            val resultadoCultivos = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                agroCatalogsRepository.obtenerTodosLosCultivos()
            } ?: return ResultadoMonitoreoSync.Error(
                "Timeout cultivos: /api/v1/agro-catalogs/crops/ tardó más de 10 segundos"
            )

            val cultivosApi = when (resultadoCultivos) {
                is ResultadoAgroCatalogsApi.Exito -> resultadoCultivos.cultivos
                is ResultadoAgroCatalogsApi.Error -> return ResultadoMonitoreoSync.Error(resultadoCultivos.mensaje)
            }

            cultivosApi.forEach { cultivoApi ->
                try {
                    if (guardarOCrearCultivo(cultivoApi) != null) {
                        cultivosGuardados++
                    }
                } catch (_: Exception) {
                }
            }

            val ciaExtId = idLocalCia
                ?.let { id -> database.localCiaDao().getCiaById(id) }
                ?.extId
                ?.takeIf { it.isNotBlank() }

            val resultadoProgramas = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                fieldOpsRepository.obtenerTodosLosProgramasCampo(
                    datacentral = ciaExtId
                )
            } ?: return ResultadoMonitoreoSync.Error(
                "Timeout programas: /api/v1/field_ops/tasks/ tardó más de 10 segundos"
            )

            val programasApi = when (resultadoProgramas) {
                is ResultadoFieldOpsApi.Exito -> resultadoProgramas.programas
                is ResultadoFieldOpsApi.Error -> return ResultadoMonitoreoSync.Error(resultadoProgramas.mensaje)
            }

            programasApi.forEach { programaApi ->
                try {
                    if (
                        guardarOCrearPrograma(
                            programaApi = programaApi,
                            idLocalCia = idLocalCia
                        ) != null
                    ) {
                        programasGuardados++
                    }
                } catch (_: Exception) {
                }
            }

            val resultadoHeaders = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                phytoMonitoringRepository.obtenerTodosLosHeaders()
            } ?: return ResultadoMonitoreoSync.Error(
                "Timeout headers: /api/v1/monitoring/phyto/headers/ tardó más de 10 segundos"
            )

            val headersApi = when (resultadoHeaders) {
                is ResultadoPhytoHeadersApi.Exito -> resultadoHeaders.headers
                is ResultadoPhytoHeadersApi.Error -> return ResultadoMonitoreoSync.Error(resultadoHeaders.mensaje)
            }

            headersApi.forEach { headerApi ->
                try {
                    val idHeaderLocal = guardarOCrearHeader(headerApi)

                    if (idHeaderLocal != null) {
                        headersGuardados++

                        headerApi.targetPoints.forEach { puntoApi ->
                            try {
                                if (
                                    guardarOCrearTargetPoint(
                                        puntoApi = puntoApi,
                                        headerExtIdFallback = headerApi.id,
                                        idHeaderLocalFallback = idHeaderLocal
                                    ) != null
                                ) {
                                    puntosGuardados++
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            ResultadoMonitoreoSync.Exito(
                cultivos = cultivosGuardados,
                programas = programasGuardados,
                headers = headersGuardados,
                targetPoints = puntosGuardados
            )
        } catch (e: Exception) {
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
                    photo = cultivoApi.photo
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
                    photo = cultivoApi.photo
                )
            )
        }
    }

    private suspend fun guardarOCrearPrograma(
        programaApi: FieldTaskApiItem,
        idLocalCia: Long?
    ): Long? {
        val extId = programaApi.id.takeIf { it.isNotBlank() } ?: return null

        val parcelaExtId = programaApi.plot
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parcelaLocal = database.localPlotDao()
            .getPlotByExtId(parcelaExtId)
            ?: return null

        val ranchoLocal = database.localRanchDao()
            .getRanchById(parcelaLocal.idLocalRanch)
            ?: return null

        val productorExtId = programaApi.agroUnit
            ?.takeIf { it.isNotBlank() }

        val productorLocal = productorExtId
            ?.let { extProductor ->
                database.localAgroUnitDao().getAgroUnitByExtId(extProductor)
            }
            ?: database.localAgroUnitDao()
                .getAgroUnitById(ranchoLocal.idLocalAgroUnit)
            ?: return null

        val cropApiParaGuardar = programaApi.crop ?: programaApi.cropVariety

        val cultivoExtId = cropApiParaGuardar?.id?.toString()
            ?: return null

        cropApiParaGuardar.let { crop ->
            guardarOCrearCultivo(
                AgroCropApiItem(
                    id = crop.id,
                    name = crop.name,
                    code = crop.code,
                    variety = crop.variety,
                    description = crop.description,
                    photo = crop.photo,
                    additionalParams = crop.additionalParams,
                    attachmentsUrl = crop.attachmentsUrl
                )
            )
        }

        val cultivoLocal = database.localCropCatalogDao()
            .getCropByExtId(cultivoExtId)
            ?: return null

        if (idLocalCia != null && idLocalCia > 0L) {
            database.localCiaAgroUnitDao().asignarProductorACia(
                LocalCiaAgroUnitCrossRef(
                    idLocalCia = idLocalCia,
                    idLocalAgroUnit = productorLocal.idLocalAgroUnit,
                    extId = "sync_cia_${idLocalCia}_agro_${productorLocal.ext_Id ?: productorLocal.idLocalAgroUnit}"
                )
            )
        }

        val existente = database.localprogramDao()
            .getProgramByExtId(extId)

        val programaNuevo = LocalProgramEntity(
            idProgram = existente?.idProgram ?: 0L,
            extId = extId,
            cycle = programaApi.cycle?.takeIf { it.isNotBlank() } ?: "Sin ciclo",
            estStartDate = parseFechaApi(programaApi.estStartDate) ?: 0L,
            estFinishDate = parseFechaApi(programaApi.estFinishDate) ?: 0L,
            actStartDate = parseFechaApi(programaApi.actualStartDate),
            actFinishDate = parseFechaApi(programaApi.actualFinishDate),
            status = normalizarEstado(programaApi.status),
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
        headerApi: PhytoHeaderApiItem
    ): Long? {
        val extId = headerApi.id.takeIf { it.isNotBlank() } ?: return null

        val programaExtId = headerApi.fieldTask
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parcelaExtId = headerApi.plot
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val programaLocal = database.localprogramDao()
            .getProgramByExtId(programaExtId)
            ?: return null

        val parcelaLocal = database.localPlotDao()
            .getPlotByExtId(parcelaExtId)
            ?: return null

        val cultivoLocal = headerApi.crop
            ?.toString()
            ?.let { cropExtId ->
                database.localCropCatalogDao().getCropByExtId(cropExtId)
            }

        val usuarioAsignado = headerApi.assignedTo
            ?.takeIf { it.isNotBlank() }
            ?.let { assignedExtId ->
                database.userDao().getUserByExtId(assignedExtId)
            }

        val existente = database.localphytomonitoringheaderDao()
            .getHeaderByExtId(extId)

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

        return if (existente != null) {
            database.localphytomonitoringheaderDao().updateHeader(headerNuevo)
            existente.idHeader
        } else {
            database.localphytomonitoringheaderDao().insertHeader(headerNuevo)
        }
    }

    private suspend fun guardarOCrearTargetPoint(
        puntoApi: PhytoTargetPointApiItem,
        headerExtIdFallback: String?,
        idHeaderLocalFallback: Long?
    ): Long? {
        val extId = puntoApi.id.takeIf { it.isNotBlank() } ?: return null

        val headerExtId = puntoApi.header
            ?.takeIf { it.isNotBlank() }
            ?: headerExtIdFallback

        val headerLocal = if (!headerExtId.isNullOrBlank()) {
            database.localphytomonitoringheaderDao()
                .getHeaderByExtId(headerExtId)
        } else {
            null
        }

        val idHeaderLocal = headerLocal?.idHeader
            ?: idHeaderLocalFallback
            ?: return null

        val parcelaLocal = puntoApi.plot
            ?.takeIf { it.isNotBlank() }
            ?.let { plotExtId ->
                database.localPlotDao().getPlotByExtId(plotExtId)
            }

        val idLocalPlot = parcelaLocal?.idLocalPlot
            ?: headerLocal?.idLocalPlot
            ?: return null

        val coordenadas = extraerLatLon(puntoApi.geom?.coordinates)
            ?: return null

        val existente = database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointByExtId(extId)

        val puntoNuevo = LocalPhytomonitoringTargetPointEntity(
            idTargetPoint = existente?.idTargetPoint ?: 0L,
            extId = extId,
            radiusM = (puntoApi.radiusM ?: 0.5).roundToInt().coerceAtLeast(1),
            lat = coordenadas.first,
            lon = coordenadas.second,
            status = normalizarEstado(puntoApi.status),
            idHeader = idHeaderLocal,
            idLocalPlot = idLocalPlot
        )

        return if (existente != null) {
            database.LocalPhytomonitoringTargetPointDao()
                .updateTargetPoint(puntoNuevo)

            existente.idTargetPoint
        } else {
            database.LocalPhytomonitoringTargetPointDao()
                .insertTargetPoint(puntoNuevo)
        }
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
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
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

    /**
     * GeoJSON normalmente manda coordinates como [lon, lat].
     * Esta función intenta detectar si vienen invertidas.
     */
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
        val targetPoints: Int
    ) : ResultadoMonitoreoSync()

    data class Error(
        val mensaje: String
    ) : ResultadoMonitoreoSync()
}
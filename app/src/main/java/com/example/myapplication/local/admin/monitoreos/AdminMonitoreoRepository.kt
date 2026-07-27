package com.example.myapplication.local.admin.monitoreos

import android.content.Context
import com.example.myapplication.local.api.core.ApiDateParser
import com.example.myapplication.local.api.fieldops.FieldTaskApiItem
import com.example.myapplication.local.api.fieldops.MasterProgramApiItem
import com.example.myapplication.local.api.phytomonitoring.PhytoHeaderApiItem
import com.example.myapplication.local.api.phytomonitoring.PhytoHeaderCreateRequest
import com.example.myapplication.local.api.phytomonitoring.PhytoMonitoringRepository
import com.example.myapplication.local.api.phytomonitoring.ResultadoCrearPhytoHeaderApi
import com.example.myapplication.local.api.phytomonitoring.ResultadoPhytoHeadersApi
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.util.Locale

/**
 * Crea únicamente la sesión fitosanitaria (PhytoHeader).
 *
 * El Programa Maestro y el Subprograma (FieldTask) deben existir previamente
 * en Django. La app nunca crea ni modifica esos registros desde esta pantalla.
 */
class AdminMonitoreoRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val phytoMonitoringRepository =
        PhytoMonitoringRepository(context.applicationContext)

    suspend fun crearMonitoreo(
        idLocalCia: Long,
        productor: LocalAgroUnitEntity,
        rancho: LocalRanchEntity,
        parcela: LocalPlotEntity,
        cultivo: LocalCropCatalogEntity,
        programaMaestro: MasterProgramApiItem,
        subprograma: FieldTaskApiItem
    ): ResultadoCrearMonitoreoAdmin {
        val productorExtId = productor.ext_Id?.trim().orEmpty()
        val parcelaExtId = parcela.extId?.trim().orEmpty()
        val cultivoExtId = cultivo.extId?.trim().orEmpty()
        val programaMaestroExtId = programaMaestro.id.trim()
        val subprogramaExtId = subprograma.id.trim()

        when {
            idLocalCia <= 0L -> return ResultadoCrearMonitoreoAdmin.Error(
                "Selecciona una CIA válida."
            )

            productorExtId.isBlank() -> return ResultadoCrearMonitoreoAdmin.Error(
                "El productor no tiene UUID remoto. Sincroniza la información de la CIA."
            )

            parcelaExtId.isBlank() -> return ResultadoCrearMonitoreoAdmin.Error(
                "La parcela del subprograma no tiene UUID remoto. Sincroniza las parcelas."
            )

            cultivoExtId.toIntOrNull() == null -> return ResultadoCrearMonitoreoAdmin.Error(
                "El cultivo del subprograma no tiene ID remoto válido. Sincroniza los catálogos."
            )

            programaMaestroExtId.isBlank() -> return ResultadoCrearMonitoreoAdmin.Error(
                "Selecciona un programa válido."
            )

            subprogramaExtId.isBlank() -> return ResultadoCrearMonitoreoAdmin.Error(
                "Selecciona un subprograma válido."
            )
        }

        val productorDelPrograma = programaMaestro.agroUnit?.trim()
        if (!productorDelPrograma.isNullOrBlank() && productorDelPrograma != productorExtId) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "El programa seleccionado no pertenece al productor."
            )
        }

        val programaPadreDelSubprograma = subprograma.masterProgram?.trim()
        if (
            !programaPadreDelSubprograma.isNullOrBlank() &&
            programaPadreDelSubprograma != programaMaestroExtId
        ) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "El subprograma seleccionado no pertenece al programa."
            )
        }

        if (subprograma.plot?.trim() != parcelaExtId) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "La parcela seleccionada no coincide con la parcela del subprograma."
            )
        }

        if (parcela.idLocalRanch != rancho.idLocalRanch) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "La parcela del subprograma no pertenece al rancho mostrado."
            )
        }

        if (rancho.idLocalAgroUnit != productor.idLocalAgroUnit) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "El rancho del subprograma no pertenece al productor."
            )
        }

        val cultivoRemoto = subprograma.crop ?: subprograma.cropVariety
        val cultivoRemotoId = cultivoRemoto?.id
        if (cultivoRemotoId != null && cultivoRemotoId != cultivoExtId.toIntOrNull()) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "El cultivo mostrado no coincide con el cultivo del subprograma."
            )
        }

        val fechaInicio = subprograma.estStartDate
            ?.trim()
            ?.takeIf { it.length >= 10 }
            ?.take(10)
            ?: return ResultadoCrearMonitoreoAdmin.Error(
                "El subprograma no tiene fecha de inicio. Complétala en Django."
            )

        val fechaFin = subprograma.estFinishDate
            ?.trim()
            ?.takeIf { it.length >= 10 }
            ?.take(10)
            ?: return ResultadoCrearMonitoreoAdmin.Error(
                "El subprograma no tiene fecha final. Complétala en Django."
            )

        val fechaInicioMillis = ApiDateParser.parsearMillis(fechaInicio)
            ?: return ResultadoCrearMonitoreoAdmin.Error(
                "La fecha de inicio del subprograma no es válida."
            )

        val fechaFinMillis = ApiDateParser.parsearFinDeDiaMillis(fechaFin)
            ?: return ResultadoCrearMonitoreoAdmin.Error(
                "La fecha final del subprograma no es válida."
            )

        if (fechaInicio > fechaFin) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "Las fechas del subprograma son inválidas: el inicio es posterior al final."
            )
        }

        val headerRemoto = when (
            val headers = phytoMonitoringRepository.obtenerTodosLosHeaders(
                fieldTask = subprogramaExtId,
                plot = parcelaExtId
            )
        ) {
            is ResultadoPhytoHeadersApi.Error -> {
                return ResultadoCrearMonitoreoAdmin.Error(
                    "No se pudo revisar si el subprograma ya tiene monitoreo. ${headers.mensaje}"
                )
            }

            is ResultadoPhytoHeadersApi.Exito -> {
                headers.headers.firstOrNull { header ->
                    header.fieldTask?.trim() == subprogramaExtId &&
                            (header.plot.isNullOrBlank() || header.plot.trim() == parcelaExtId)
                } ?: when (
                    val creado = phytoMonitoringRepository.crearHeader(
                        PhytoHeaderCreateRequest(
                            plotId = parcelaExtId,
                            fieldTaskId = subprogramaExtId,
                            estimatedStartDate = fechaInicio,
                            estimatedEndDate = fechaFin,
                            strictMode = true,
                            radiusTolerance = RADIO_TOLERANCIA_METROS
                        )
                    )
                ) {
                    is ResultadoCrearPhytoHeaderApi.Exito -> creado.header
                    is ResultadoCrearPhytoHeaderApi.Error -> {
                        return ResultadoCrearMonitoreoAdmin.Error(
                            "El programa y subprograma ya existen, pero no se pudo crear " +
                                    "la sesión de monitoreo. ${creado.mensaje}"
                        )
                    }
                }
            }
        }

        val headerExtId = headerRemoto.id.trim()
        if (headerExtId.isBlank()) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "La sesión de monitoreo no regresó UUID."
            )
        }

        val ciclo = subprograma.cycle
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Sin ciclo"

        /*
         * Room conserva una copia del subprograma y de la sesión para trabajar
         * sin conexión. No se crea ningún programa remoto en este punto.
         */
        val idProgramLocal = guardarSubprogramaLocal(
            idLocalCia = idLocalCia,
            productor = productor,
            rancho = rancho,
            parcela = parcela,
            cultivo = cultivo,
            ciclo = ciclo,
            fechaInicioMillis = fechaInicioMillis,
            fechaFinMillis = fechaFinMillis,
            subprograma = subprograma
        )

        val idHeaderLocal = guardarHeaderLocal(
            idProgramLocal = idProgramLocal,
            parcela = parcela,
            cultivo = cultivo,
            ciclo = ciclo,
            fechaInicioMillis = fechaInicioMillis,
            fechaFinMillis = fechaFinMillis,
            headerRemoto = headerRemoto
        )

        return ResultadoCrearMonitoreoAdmin.Exito(
            idProgramLocal = idProgramLocal,
            idHeaderLocal = idHeaderLocal,
            programExtId = subprogramaExtId,
            headerExtId = headerExtId
        )
    }

    private suspend fun guardarSubprogramaLocal(
        idLocalCia: Long,
        productor: LocalAgroUnitEntity,
        rancho: LocalRanchEntity,
        parcela: LocalPlotEntity,
        cultivo: LocalCropCatalogEntity,
        ciclo: String,
        fechaInicioMillis: Long,
        fechaFinMillis: Long,
        subprograma: FieldTaskApiItem
    ): Long {
        val existente = database.localprogramDao()
            .getProgramByExtId(subprograma.id.trim())

        val nuevo = LocalProgramEntity(
            idProgram = existente?.idProgram ?: 0L,
            extId = subprograma.id.trim(),
            cycle = ciclo,
            estStartDate = ApiDateParser.parsearMillis(subprograma.estStartDate)
                ?: fechaInicioMillis,
            estFinishDate = ApiDateParser.parsearFinDeDiaMillis(subprograma.estFinishDate)
                ?: fechaFinMillis,
            actStartDate = ApiDateParser.parsearMillis(subprograma.actualStartDate),
            actFinishDate = ApiDateParser.parsearMillis(subprograma.actualFinishDate),
            status = normalizarEstadoLocal(subprograma.status),
            idLocalCia = idLocalCia,
            idLocalAgroUnit = productor.idLocalAgroUnit,
            idLocalRanch = rancho.idLocalRanch,
            idCrop = cultivo.idCrop,
            idLocalPlot = parcela.idLocalPlot
        )

        return if (existente == null) {
            database.localprogramDao().insertProgram(nuevo)
        } else {
            database.localprogramDao().updateProgram(nuevo)
            existente.idProgram
        }
    }

    private suspend fun guardarHeaderLocal(
        idProgramLocal: Long,
        parcela: LocalPlotEntity,
        cultivo: LocalCropCatalogEntity,
        ciclo: String,
        fechaInicioMillis: Long,
        fechaFinMillis: Long,
        headerRemoto: PhytoHeaderApiItem
    ): Long {
        val existente = database.localphytomonitoringheaderDao()
            .getHeaderByExtId(headerRemoto.id.trim())

        val assignedUserId = headerRemoto.assignedTo
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { extId ->
                database.userDao().getUserByExtId(extId)?.idUser
            } ?: parcela.assignedUserId

        val nuevo = LocalPhytomonitoringHeaderEntity(
            idHeader = existente?.idHeader ?: 0L,
            extId = headerRemoto.id.trim(),
            cycle = ciclo,
            estStartDate = ApiDateParser.parsearMillis(headerRemoto.estimatedStartDate)
                ?: fechaInicioMillis,
            estFinishDate = ApiDateParser.parsearFinDeDiaMillis(headerRemoto.estimatedEndDate)
                ?: fechaFinMillis,
            startAt = ApiDateParser.parsearMillis(headerRemoto.startedAt),
            finishedAt = ApiDateParser.parsearMillis(headerRemoto.finishedAt),
            additionalNotes = headerRemoto.additionalNotes.orEmpty(),
            radiusTolerance = headerRemoto.radiusTolerance
                ?: RADIO_TOLERANCIA_METROS.toDouble(),
            status = normalizarEstadoLocal(headerRemoto.status),
            idProgram = idProgramLocal,
            idCrop = cultivo.idCrop,
            idLocalPlot = parcela.idLocalPlot,
            assignedUserId = assignedUserId
        )

        return if (existente == null) {
            database.localphytomonitoringheaderDao().insertHeader(nuevo)
        } else {
            database.localphytomonitoringheaderDao().updateHeader(nuevo)
            existente.idHeader
        }
    }

    private fun normalizarEstadoLocal(status: String?): String {
        return when (status?.trim()?.lowercase(Locale.US)) {
            "pending", "pendiente" -> "Pendiente"
            "in_progress", "en proceso", "vigente" -> "En proceso"
            "completed", "completado", "finalizado" -> "Completado"
            "cancelled", "canceled", "cancelado" -> "Cancelado"
            else -> "Pendiente"
        }
    }

    private companion object {
        const val RADIO_TOLERANCIA_METROS = 15
    }
}

sealed class ResultadoCrearMonitoreoAdmin {
    data class Exito(
        val idProgramLocal: Long,
        val idHeaderLocal: Long,
        val programExtId: String,
        val headerExtId: String
    ) : ResultadoCrearMonitoreoAdmin()

    data class Error(
        val mensaje: String
    ) : ResultadoCrearMonitoreoAdmin()
}
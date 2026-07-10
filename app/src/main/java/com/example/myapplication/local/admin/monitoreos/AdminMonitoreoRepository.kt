package com.example.myapplication.local.admin.monitoreos

import android.content.Context
import com.example.myapplication.local.api.fieldops.FieldOpsRepository
import com.example.myapplication.local.api.fieldops.FieldTaskApiItem
import com.example.myapplication.local.api.fieldops.FieldTaskCreateRequest
import com.example.myapplication.local.api.fieldops.FieldTaskPatchRequest
import com.example.myapplication.local.api.fieldops.MasterProgramApiItem
import com.example.myapplication.local.api.fieldops.ResultadoCrearFieldTaskApi
import com.example.myapplication.local.api.fieldops.ResultadoFieldOpsApi
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Crea el monitoreo administrativo en este orden:
 *
 * 1) FieldTask remoto (Programa).
 * 2) PhytoHeader remoto, enlazado al FieldTask.
 * 3) Copia local de ambos objetos en Room, incluyendo los UUID extId.
 *
 * El repositorio busca primero un Programa/Header igual ya existente en servidor.
 * Esto evita duplicarlo si una petición anterior creó el registro pero la app no
 * alcanzó a recibir la respuesta o se cerró antes de guardar Room.
 */
class AdminMonitoreoRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val fieldOpsRepository = FieldOpsRepository(context.applicationContext)
    private val phytoMonitoringRepository = PhytoMonitoringRepository(context.applicationContext)

    suspend fun crearMonitoreo(
        idLocalCia: Long,
        productor: LocalAgroUnitEntity,
        rancho: LocalRanchEntity,
        parcela: LocalPlotEntity,
        cultivo: LocalCropCatalogEntity,
        programaMaestro: MasterProgramApiItem,
        ciclo: String,
        fechaInicioMillis: Long,
        fechaFinMillis: Long
    ): ResultadoCrearMonitoreoAdmin {
        val productorExtId = productor.ext_Id?.trim().orEmpty()
        val parcelaExtId = parcela.extId?.trim().orEmpty()
        val cultivoExtId = cultivo.extId?.trim().orEmpty()
        val masterProgramExtId = programaMaestro.id.trim()

        when {
            productorExtId.isBlank() -> return ResultadoCrearMonitoreoAdmin.Error(
                "El productor seleccionado no tiene UUID remoto. Sincroniza organizaciones antes de crear el monitoreo."
            )

            parcelaExtId.isBlank() -> return ResultadoCrearMonitoreoAdmin.Error(
                "La parcela seleccionada no tiene UUID remoto. Sincroniza parcelas antes de crear el monitoreo."
            )

            cultivoExtId.toIntOrNull() == null -> return ResultadoCrearMonitoreoAdmin.Error(
                "El cultivo seleccionado no tiene ID remoto válido. Actualiza el catálogo de cultivos."
            )

            masterProgramExtId.isBlank() -> return ResultadoCrearMonitoreoAdmin.Error(
                "Selecciona un programa maestro válido."
            )
        }

        /*
         * Defensa extra: el backend también valida esta relación, pero validarla aquí
         * evita una petición que terminaría en 400.
         */
        val productorDelMaster = programaMaestro.agroUnit?.trim()
        if (!productorDelMaster.isNullOrBlank() && productorDelMaster != productorExtId) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "El programa maestro elegido no pertenece al productor seleccionado."
            )
        }

        val fechaInicioIso = formatearIsoUtc(fechaInicioMillis)
        val fechaFinIso = formatearIsoUtc(fechaFinMillis)
        val fechaInicioSolicitada = formatearSoloFecha(fechaInicioMillis)
        val fechaFinSolicitada = formatearSoloFecha(fechaFinMillis)
        val tituloProgramaVisible = crearTituloProgramaVisible(
            ciclo = ciclo,
            parcela = parcela,
            cultivo = cultivo
        )
        val codigoProgramaVisible = crearCodigoProgramaVisible(
            ciclo = ciclo,
            parcela = parcela,
            fechaInicio = fechaInicioSolicitada
        )

        /*
         * Un intento anterior puede haber creado el Programa remoto y fallar después
         * al crear el Header. Django no permite otro Programa hijo que se traslape
         * para la misma parcela y Programa Maestro.
         *
         * Por eso, si ya existe uno para el mismo Master + Plot que se traslapa,
         * se reutiliza ese Programa y se continúa con la creación/recuperación del
         * Header. Nunca se intenta duplicarlo.
         */
        val programaRemotoBase = when (
            val buscado = fieldOpsRepository.obtenerTodosLosProgramasCampo(
                masterProgram = masterProgramExtId,
                plot = parcelaExtId
            )
        ) {
            is ResultadoFieldOpsApi.Error -> {
                return ResultadoCrearMonitoreoAdmin.Error(
                    "No se pudo revisar si ya existe el programa. ${buscado.mensaje}"
                )
            }

            is ResultadoFieldOpsApi.Exito -> {
                val programaExacto = buscado.programas.firstOrNull { item ->
                    coincideConMonitoreoSolicitado(
                        item = item,
                        masterProgramExtId = masterProgramExtId,
                        parcelaExtId = parcelaExtId,
                        ciclo = ciclo,
                        fechaInicio = fechaInicioSolicitada,
                        fechaFin = fechaFinSolicitada
                    )
                }

                val programaSolapado = buscado.programas
                    .filter { item ->
                        esProgramaActivoDelMismoMasterYParcela(
                            item = item,
                            masterProgramExtId = masterProgramExtId,
                            parcelaExtId = parcelaExtId
                        )
                    }
                    .filter { item ->
                        seTraslapanRangos(
                            inicioExistente = item.estStartDate?.take(10),
                            finExistente = item.estFinishDate?.take(10),
                            inicioSolicitado = fechaInicioSolicitada,
                            finSolicitado = fechaFinSolicitada
                        )
                    }
                    /*
                     * Si hay más de un registro histórico, se prefiere primero
                     * el que tenga el mismo cultivo; después el más reciente.
                     */
                    .sortedWith(
                        compareByDescending<FieldTaskApiItem> {
                            it.crop?.id == cultivoExtId.toIntOrNull()
                        }.thenByDescending {
                            it.estStartDate.orEmpty()
                        }
                    )
                    .firstOrNull()

                programaExacto
                    ?: programaSolapado
                    ?: when (
                        val creado = fieldOpsRepository.crearProgramaCampo(
                            FieldTaskCreateRequest(
                                masterProgram = masterProgramExtId,
                                plot = parcelaExtId,
                                cropId = cultivoExtId.toInt(),
                                title = tituloProgramaVisible,
                                cycle = ciclo,
                                status = "pending",
                                estStartDate = fechaInicioIso,
                                estFinishDate = fechaFinIso
                            )
                        )
                    ) {
                        is ResultadoCrearFieldTaskApi.Exito -> creado.programa
                        is ResultadoCrearFieldTaskApi.Error -> {
                            return ResultadoCrearMonitoreoAdmin.Error(creado.mensaje)
                        }
                    }
            }
        }

        val programaRemoto = when (
            val reparado = asegurarProgramaRemotoConDatosVisibles(
                programa = programaRemotoBase,
                tituloPrograma = tituloProgramaVisible,
                codigoPrograma = codigoProgramaVisible,
                ciclo = ciclo,
                fechaInicioIso = fechaInicioIso,
                fechaFinIso = fechaFinIso
            )
        ) {
            is ResultadoProgramaVisible.Exito -> reparado.programa
            is ResultadoProgramaVisible.Error -> return ResultadoCrearMonitoreoAdmin.Error(reparado.mensaje)
        }

        val programaExtId = programaRemoto.id.trim()
        if (programaExtId.isBlank()) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "El programa remoto no regresó UUID."
            )
        }

        val headerRemoto = when (
            val headers = phytoMonitoringRepository.obtenerTodosLosHeaders(
                fieldTask = programaExtId
            )
        ) {
            is ResultadoPhytoHeadersApi.Error -> {
                return ResultadoCrearMonitoreoAdmin.Error(
                    "El programa remoto se creó, pero no se pudo revisar la sesión fitosanitaria. " +
                            "Vuelve a intentarlo; la app reutilizará el mismo programa. ${headers.mensaje}"
                )
            }

            is ResultadoPhytoHeadersApi.Exito -> {
                headers.headers.firstOrNull { header ->
                    header.fieldTask?.trim() == programaExtId &&
                            (header.plot.isNullOrBlank() || header.plot.trim() == parcelaExtId)
                } ?: when (
                    val creado = phytoMonitoringRepository.crearHeader(
                        PhytoHeaderCreateRequest(
                            plotId = parcelaExtId,
                            fieldTaskId = programaExtId,

                            /*
                             * El backend real utiliza estimated_start_date y
                             * estimated_end_date. Swagger mostraba monitoring_date,
                             * pero ese campo NO existe en el serializer actual.
                             */
                            estimatedStartDate = fechaInicioSolicitada,
                            estimatedEndDate = fechaFinSolicitada,

                            /*
                             * Se conserva validación de ubicación en el backend.
                             * 15 m evita falsos rechazos por la precisión normal del GPS.
                             */
                            strictMode = true,
                            radiusTolerance = RADIO_TOLERANCIA_METROS
                        )
                    )
                ) {
                    is ResultadoCrearPhytoHeaderApi.Exito -> creado.header
                    is ResultadoCrearPhytoHeaderApi.Error -> {
                        return ResultadoCrearMonitoreoAdmin.Error(
                            "El programa remoto ya fue creado, pero la sesión fitosanitaria falló. " +
                                    "Vuelve a intentarlo; no se duplicará el programa. ${creado.mensaje}"
                        )
                    }
                }
            }
        }

        val headerExtId = headerRemoto.id.trim()
        if (headerExtId.isBlank()) {
            return ResultadoCrearMonitoreoAdmin.Error(
                "La sesión fitosanitaria no regresó UUID."
            )
        }

        /*
         * Solo cuando servidor confirmó Programa y Header se escriben en Room.
         * Así todo punto capturado después ya tendrá header.extId para sincronizar
         * Target Point -> Checkpoint -> Foto.
         */
        val idProgramLocal = guardarProgramaLocal(
            idLocalCia = idLocalCia,
            productor = productor,
            rancho = rancho,
            parcela = parcela,
            cultivo = cultivo,
            ciclo = ciclo,
            fechaInicioMillis = fechaInicioMillis,
            fechaFinMillis = fechaFinMillis,
            programaRemoto = programaRemoto
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
            programExtId = programaExtId,
            headerExtId = headerExtId
        )
    }

    /**
     * Si el backend creó o reutilizó un Programa sin título visible, se corrige
     * inmediatamente con PATCH. Así no vuelve a aparecer en Django como Title "-".
     */
    private suspend fun asegurarProgramaRemotoConDatosVisibles(
        programa: FieldTaskApiItem,
        tituloPrograma: String,
        codigoPrograma: String,
        ciclo: String,
        fechaInicioIso: String,
        fechaFinIso: String
    ): ResultadoProgramaVisible {
        val tituloActual = programa.title?.trim().orEmpty()
        val cicloActual = programa.cycle?.trim().orEmpty()
        val inicioActual = programa.estStartDate?.trim().orEmpty()
        val finActual = programa.estFinishDate?.trim().orEmpty()

        val necesitaTitulo = tituloActual.isBlank() || tituloActual == "-"
        val necesitaCiclo = cicloActual.isBlank()
        val necesitaFechas = inicioActual.isBlank() || finActual.isBlank()

        if (!necesitaTitulo && !necesitaCiclo && !necesitaFechas) {
            return ResultadoProgramaVisible.Exito(programa)
        }

        val patchPrincipal = FieldTaskPatchRequest(
            title = if (necesitaTitulo) tituloPrograma else null,
            cycle = if (necesitaCiclo) ciclo else null,
            status = programa.status?.takeIf { it.isNotBlank() } ?: "pending",
            estStartDate = if (necesitaFechas) fechaInicioIso else null,
            estFinishDate = if (necesitaFechas) fechaFinIso else null
        )

        val actualizado = when (
            val resultado = fieldOpsRepository.actualizarProgramaCampo(
                id = programa.id.trim(),
                body = patchPrincipal
            )
        ) {
            is ResultadoCrearFieldTaskApi.Exito -> resultado.programa
            is ResultadoCrearFieldTaskApi.Error -> return ResultadoProgramaVisible.Error(
                "El programa remoto existe, pero estaba sin título visible y no se pudo corregir. ${resultado.mensaje}"
            )
        }

        /*
         * Code/voucher_code es visual en el admin. Se intenta reparar solo después
         * de guardar el título. Si el backend no permite editarlo, no se bloquea
         * el monitoreo porque lo crítico es que Title/Cycle queden correctos.
         */
        if (actualizado.voucherCode.isNullOrBlank() || actualizado.voucherCode.trim() == "-") {
            runCatching {
                fieldOpsRepository.actualizarProgramaCampo(
                    id = actualizado.id.trim(),
                    body = FieldTaskPatchRequest(voucherCode = codigoPrograma)
                )
            }
        }

        val tituloFinal = actualizado.title?.trim().orEmpty()
        if (tituloFinal.isBlank() || tituloFinal == "-") {
            return ResultadoProgramaVisible.Error(
                "El backend respondió creado, pero no guardó el título del programa. Revisa el serializer de FieldTask para aceptar el campo title."
            )
        }

        return ResultadoProgramaVisible.Exito(actualizado)
    }

    private fun crearTituloProgramaVisible(
        ciclo: String,
        parcela: LocalPlotEntity,
        cultivo: LocalCropCatalogEntity
    ): String {
        val parcelaTexto = parcela.code
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: parcela.name.trim()

        return listOf(
            "Monitoreo",
            ciclo.trim(),
            parcelaTexto,
            cultivo.name.trim()
        )
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .take(120)
    }

    private fun crearCodigoProgramaVisible(
        ciclo: String,
        parcela: LocalPlotEntity,
        fechaInicio: String
    ): String {
        val parcelaTexto = parcela.code
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: parcela.name.trim()

        val base = listOf(
            "MON",
            limpiarCodigoPrograma(parcelaTexto),
            limpiarCodigoPrograma(ciclo),
            fechaInicio.replace("-", "")
        )
            .filter { it.isNotBlank() }
            .joinToString("-")
            .replace(Regex("-+"), "-")
            .trim('-')

        return base.take(48).ifBlank { "MON-${System.currentTimeMillis().toString().takeLast(8)}" }
    }

    private fun limpiarCodigoPrograma(valor: String): String {
        return valor
            .trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "-")
            .trim('-')
    }

    private suspend fun guardarProgramaLocal(
        idLocalCia: Long,
        productor: LocalAgroUnitEntity,
        rancho: LocalRanchEntity,
        parcela: LocalPlotEntity,
        cultivo: LocalCropCatalogEntity,
        ciclo: String,
        fechaInicioMillis: Long,
        fechaFinMillis: Long,
        programaRemoto: FieldTaskApiItem
    ): Long {
        val existente = database.localprogramDao()
            .getProgramByExtId(programaRemoto.id.trim())

        val nuevo = LocalProgramEntity(
            idProgram = existente?.idProgram ?: 0L,
            extId = programaRemoto.id.trim(),
            cycle = programaRemoto.cycle?.trim().takeUnless { it.isNullOrBlank() } ?: ciclo,
            /*
             * Cuando se reutiliza un Programa remoto, Room debe conservar sus
             * fechas reales y no las que el usuario intentó capturar después.
             */
            estStartDate = parseFechaApi(programaRemoto.estStartDate) ?: fechaInicioMillis,
            estFinishDate = parseFechaApi(programaRemoto.estFinishDate) ?: fechaFinMillis,
            actStartDate = parseFechaApi(programaRemoto.actualStartDate),
            actFinishDate = parseFechaApi(programaRemoto.actualFinishDate),
            status = normalizarEstadoLocal(programaRemoto.status),
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
            estStartDate = parseFechaApi(headerRemoto.estimatedStartDate) ?: fechaInicioMillis,
            estFinishDate = parseFechaApi(headerRemoto.estimatedEndDate) ?: fechaFinMillis,
            startAt = parseFechaApi(headerRemoto.startedAt),
            finishedAt = parseFechaApi(headerRemoto.finishedAt),
            additionalNotes = headerRemoto.additionalNotes.orEmpty(),
            radiusTolerance = headerRemoto.radiusTolerance ?: RADIO_TOLERANCIA_METROS.toDouble(),
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

    /**
     * Identifica exactamente el mismo Programa remoto. Es el primer candidato
     * para reintentos normales.
     */
    private fun coincideConMonitoreoSolicitado(
        item: FieldTaskApiItem,
        masterProgramExtId: String,
        parcelaExtId: String,
        ciclo: String,
        fechaInicio: String,
        fechaFin: String
    ): Boolean {
        return esProgramaActivoDelMismoMasterYParcela(
            item = item,
            masterProgramExtId = masterProgramExtId,
            parcelaExtId = parcelaExtId
        ) &&
                item.cycle?.trim().orEmpty().equals(ciclo.trim(), ignoreCase = true) &&
                item.estStartDate?.take(10) == fechaInicio &&
                item.estFinishDate?.take(10) == fechaFin
    }

    /**
     * Django rechaza Programas hijos superpuestos para el mismo Master + Plot.
     * Esta función permite detectar uno ya existente para reutilizarlo.
     */
    private fun esProgramaActivoDelMismoMasterYParcela(
        item: FieldTaskApiItem,
        masterProgramExtId: String,
        parcelaExtId: String
    ): Boolean {
        val estado = item.status?.trim()?.lowercase(Locale.US)
        val esCancelado = estado == "cancelled" ||
                estado == "cancelado" ||
                estado == "canceled"

        return !esCancelado &&
                item.masterProgram?.trim() == masterProgramExtId &&
                item.plot?.trim() == parcelaExtId
    }

    /**
     * Las fechas vienen como yyyy-MM-dd o ISO-8601. Al tomar los primeros diez
     * caracteres se comparan de forma segura como texto porque ese formato es
     * ordenable cronológicamente.
     */
    private fun seTraslapanRangos(
        inicioExistente: String?,
        finExistente: String?,
        inicioSolicitado: String,
        finSolicitado: String
    ): Boolean {
        val inicio = inicioExistente?.trim()?.takeIf { it.length >= 10 } ?: return false
        val fin = finExistente?.trim()?.takeIf { it.length >= 10 } ?: return false

        return inicio <= finSolicitado && fin >= inicioSolicitado
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

    /**
     * Convierte fechas ISO devueltas por Django a milisegundos de Room.
     * Acepta fecha simple, zona -06:00 y variantes con milisegundos.
     */
    private fun parseFechaApi(fecha: String?): Long? {
        val texto = fecha?.trim()?.takeIf { it.isNotBlank() } ?: return null

        val formatos = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )

        return formatos.firstNotNullOfOrNull { patron ->
            runCatching {
                SimpleDateFormat(patron, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(texto)?.time
            }.getOrNull()
        }
    }

    private fun formatearSoloFecha(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(millis))
    }

    private fun formatearIsoUtc(millis: Long): String {
        return SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }

    private companion object {
        const val RADIO_TOLERANCIA_METROS = 15
    }
}

private sealed class ResultadoProgramaVisible {
    data class Exito(val programa: FieldTaskApiItem) : ResultadoProgramaVisible()
    data class Error(val mensaje: String) : ResultadoProgramaVisible()
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

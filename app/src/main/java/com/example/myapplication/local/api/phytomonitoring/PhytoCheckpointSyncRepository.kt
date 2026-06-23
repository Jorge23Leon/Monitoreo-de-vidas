package com.example.myapplication.local.api.phytomonitoring

// CAMBIO PUNTOS/CSV: al crear o reconciliar puntos con API, se conserva el label
// real para no sustituirlo por el id local de Room.

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class PhytoCheckpointSyncRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val api: PhytoMonitoringApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = PhytoMonitoringApiService::class.java
        )

    private val preferencias = context.getSharedPreferences(
        "phyto_checkpoint_csv_sync",
        Context.MODE_PRIVATE
    )

    /**
     * Sube al servidor los checkpoints locales que todavía no tienen extId.
     * Si no hay Internet o el backend responde error, NO borra nada local.
     */
    suspend fun sincronizarHeaderCsv(
        headerLocal: LocalPhytomonitoringHeaderEntity
    ): ResultadoCheckpointSync = withContext(Dispatchers.IO) {
        val headerExtId = headerLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext ResultadoCheckpointSync.Error(
                "El monitoreo local no tiene ext_id del servidor; no se pueden subir capturas."
            )

        // Antes de subir intentamos reconciliar con lo que ya exista en API.
        // Esto evita duplicar capturas que antes pudieron haberse enviado por CSV.
        descargarCheckpointsHeaderDesdeApi(headerLocal)

        val checkpointsLocales = database.localphytomonitoringcheckpointDao()
            .getCheckpointsByHeader(headerLocal.idHeader)

        val pendientes = checkpointsLocales.filter { checkpoint ->
            checkpoint.extId.isNullOrBlank()
        }

        if (pendientes.isEmpty()) {
            sincronizarEstadoHeaderServidor(headerLocal)

            val descargados = descargarCheckpointsHeaderDesdeApi(headerLocal).cantidad
            return@withContext ResultadoCheckpointSync.Exito(
                subidos = 0,
                descargados = descargados,
                omitidos = 0,
                mensaje = "No había checkpoints nuevos por subir. Estado del monitoreo sincronizado."
            )
        }

        val puntosIniciales = database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointsByHeader(headerLocal.idHeader)
            .associateBy { it.idTargetPoint }
            .toMutableMap()

        val catalogo = database.localphytosanitarycatalogDao()
            .getAllCatalogo()
            .associateBy { it.idPhytosanitary }

        var subidos = 0
        var omitidos = 0
        val errores = mutableListOf<String>()

        for (checkpoint in pendientes) {
            val puntoLocal = puntosIniciales[checkpoint.idTargetPoint]
            val fitoLocal = catalogo[checkpoint.idPhytosanitary]

            if (puntoLocal == null) {
                omitidos++
                errores.add("checkpoint ${checkpoint.idCheckpoint}: no tiene punto local")
                continue
            }

            /*
             * Un punto SIN_PLAGA sí debe llegar a la API:
             * - qty = 0
             * - presence_status = low
             * - phyto_issue = null
             * - stage = null
             *
             * La tabla del backend permite phyto_issue y stage nulos.
             * No se requiere inventar una plaga para representar cero presencia.
             */
            val esSinPlaga = esCheckpointSinPlaga(fitoLocal, checkpoint)

            val phytoIssueId = if (esSinPlaga) {
                null
            } else {
                fitoLocal?.extId
                    ?.trim()
                    ?.toIntOrNull()
            }

            if (!esSinPlaga && phytoIssueId == null) {
                omitidos++
                errores.add("checkpoint ${checkpoint.idCheckpoint}: fitosanitario sin ext_id numérico")
                continue
            }

            val targetExtId = asegurarTargetPointEnApi(
                headerLocal = headerLocal,
                puntoLocal = puntoLocal
            )

            if (targetExtId == null) {
                omitidos++
                errores.add("checkpoint ${checkpoint.idCheckpoint}: no se pudo crear target point en API")
                continue
            }

            val checkpointCreado = subirCheckpointJson(
                headerExtId = headerExtId,
                targetExtId = targetExtId,
                checkpoint = checkpoint,
                puntoLocal = puntoLocal.copy(extId = targetExtId),
                phytoIssueId = phytoIssueId,
                esSinPlaga = esSinPlaga
            )

            if (checkpointCreado == null) {
                omitidos++
                errores.add("checkpoint ${checkpoint.idCheckpoint}: no se pudo crear checkpoint en API")
                continue
            }

            val checkpointExtId = checkpointCreado.id?.trim()?.takeIf { it.isNotBlank() }
            if (checkpointExtId != null) {
                database.localphytomonitoringcheckpointDao()
                    .updateCheckpoint(
                        checkpoint.copy(
                            extId = checkpointExtId,
                            idTargetPoint = puntoLocal.idTargetPoint
                        )
                    )
            }

            database.LocalPhytomonitoringTargetPointDao()
                .actualizarStatusPunto(
                    idTargetPoint = puntoLocal.idTargetPoint,
                    status = "Completado"
                )

            subidos++
        }

        sincronizarEstadoHeaderServidor(headerLocal)

        val descargados = descargarCheckpointsHeaderDesdeApi(headerLocal).cantidad

        if (subidos == 0 && errores.isNotEmpty()) {
            return@withContext ResultadoCheckpointSync.Error(
                "No se pudieron subir capturas. ${errores.take(3).joinToString(" | ")}"
            )
        }

        ResultadoCheckpointSync.Exito(
            subidos = subidos,
            descargados = descargados,
            omitidos = omitidos,
            mensaje = when {
                errores.isNotEmpty() -> {
                    "Checkpoints sincronizados parcialmente. ${errores.take(3).joinToString(" | ")}"
                }
                omitidos > 0 -> {
                    "Checkpoints sincronizados. Omitidos por datos incompletos: $omitidos."
                }
                else -> {
                    "Checkpoints sincronizados por JSON correctamente."
                }
            }
        )
    }

    private suspend fun sincronizarEstadoHeaderServidor(
        headerLocal: LocalPhytomonitoringHeaderEntity
    ): Boolean {
        val headerExtId = headerLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val statusApi = statusHeaderApi(headerLocal.status)

        val response = api.actualizarHeader(
            id = headerExtId,
            body = PhytoHeaderPatchRequest(
                status = statusApi,
                startedAt = headerLocal.startAt?.let { formatearIsoApi(it) },
                finishedAt = headerLocal.finishedAt?.let { formatearIsoApi(it) },
                additionalNotes = headerLocal.additionalNotes.takeIf { it.isNotBlank() }
            )
        )

        if (!response.isSuccessful) {
            android.util.Log.e(
                "SYNC_PHYTO_JSON",
                "Error actualizando header HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}"
            )
            return false
        }

        return true
    }

    private fun statusHeaderApi(statusLocal: String): String {
        val limpio = statusLocal
            .trim()
            .lowercase(Locale.getDefault())
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")

        return when (limpio) {
            "completado", "completed", "finalizado" -> "completed"
            "en proceso", "in_progress", "vigente" -> "in_progress"
            "cancelado", "cancelled" -> "cancelled"
            "pendiente", "pending" -> "pending"
            else -> limpio.ifBlank { "pending" }
        }
    }

    private fun esCheckpointSinPlaga(
        fitoLocal: LocalPhytosanitaryCatalogEntity?,
        checkpoint: LocalPhytomonitoringCheckpointEntity
    ): Boolean {
        val nombre = fitoLocal?.name.orEmpty()
        val tipo = fitoLocal?.type.orEmpty()
        val descripcion = fitoLocal?.description.orEmpty()
        val texto = "$nombre $tipo $descripcion"
            .trim()
            .uppercase(Locale.getDefault())
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")

        val esCatalogoSinPlaga = texto.contains("SIN_PLAGA") ||
                texto.contains("SIN PLAGA") ||
                texto.contains("NO PLAGA") ||
                texto.contains("AUSENTE")

        val stageLimpio = checkpoint.stage
            ?.trim()
            ?.lowercase(Locale.getDefault())
            .orEmpty()

        val sinEtapaReal = stageLimpio.isBlank() ||
                stageLimpio == "-" ||
                stageLimpio == "sin etapa"

        return esCatalogoSinPlaga &&
                (checkpoint.qty ?: 0) <= 0 &&
                sinEtapaReal
    }

    private suspend fun asegurarTargetPointEnApi(
        headerLocal: LocalPhytomonitoringHeaderEntity,
        puntoLocal: LocalPhytomonitoringTargetPointEntity
    ): String? {
        puntoLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val headerExtId = headerLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parcelaLocal = database.localPlotDao()
            .getPlotById(puntoLocal.idLocalPlot)
            ?: database.localPlotDao().getPlotById(headerLocal.idLocalPlot)
            ?: return null

        val plotExtId = parcelaLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val body = PhytoTargetPointCreateRequest(
            header = headerExtId,
            plot = plotExtId,
            geom = PhytoGeomApi(
                type = "Point",
                coordinates = listOf(puntoLocal.lon, puntoLocal.lat)
            ),
            radiusM = puntoLocal.radiusM.toDouble().coerceAtLeast(1.0),
            label = puntoLocal.label
                .trim()
                .ifBlank { "Punto ${puntoLocal.idTargetPoint}" }
        )

        val response = api.crearTargetPoint(body)

        if (!response.isSuccessful) {
            android.util.Log.e(
                "SYNC_PHYTO_JSON",
                "Error creando target point HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}"
            )
            return null
        }

        val creado = response.body() ?: return null
        val targetExtId = creado.id.trim().takeIf { it.isNotBlank() } ?: return null

        database.LocalPhytomonitoringTargetPointDao()
            .updateTargetPoint(
                puntoLocal.copy(
                    extId = targetExtId,
                    label = creado.label
                        ?.trim()
                        .orEmpty()
                        .ifBlank { puntoLocal.label },
                    status = "in_progress"
                )
            )

        return targetExtId
    }

    private suspend fun subirCheckpointJson(
        headerExtId: String,
        targetExtId: String,
        checkpoint: LocalPhytomonitoringCheckpointEntity,
        puntoLocal: LocalPhytomonitoringTargetPointEntity,
        phytoIssueId: Int?,
        esSinPlaga: Boolean
    ): PhytoCheckpointApiItem? {
        val stage = checkpoint.stage
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        /*
         * Para una captura normal la API requiere etapa y problema fitosanitario.
         * Para SIN_PLAGA ambos valores se dejan nulos para registrar presencia cero.
         */
        if (!esSinPlaga && (phytoIssueId == null || stage == null)) {
            return null
        }

        val qty = if (esSinPlaga) 0 else (checkpoint.qty ?: 0)

        val body = PhytoCheckpointCreateRequest(
            header = headerExtId,
            target = targetExtId,
            phytoIssue = phytoIssueId,
            stage = stage,
            presenceStatus = presenceStatusApi(checkpoint),
            qty = qty,
            geom = PhytoGeomApi(
                type = "Point",
                coordinates = listOf(puntoLocal.lon, puntoLocal.lat)
            ),
            notes = checkpoint.notes?.takeIf { it.isNotBlank() },
            capturedAt = formatearIsoApi(checkpoint.capturedAt ?: System.currentTimeMillis())
        )

        val response = api.crearCheckpoint(body)

        if (!response.isSuccessful) {
            android.util.Log.e(
                "SYNC_PHYTO_JSON",
                "Error creando checkpoint HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}"
            )
            return null
        }

        return response.body()
    }

    /**
     * Baja los checkpoints del servidor y los guarda en Room.
     * Esto permite que el reporte del admin muestre capturas hechas por otro usuario.
     */
    suspend fun descargarCheckpointsHeaderDesdeApi(
        headerLocal: LocalPhytomonitoringHeaderEntity
    ): ResultadoDescargaCheckpoints = withContext(Dispatchers.IO) {
        val headerExtId = headerLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext ResultadoDescargaCheckpoints(0)

        val items = mutableListOf<PhytoCheckpointApiItem>()
        var page = 1

        while (true) {
            val response = api.listarCheckpoints(
                header = headerExtId,
                page = page
            )

            if (!response.isSuccessful) {
                return@withContext ResultadoDescargaCheckpoints(items.size)
            }

            val body = response.body()
                ?: return@withContext ResultadoDescargaCheckpoints(items.size)

            items.addAll(body.results)

            if (body.next.isNullOrBlank()) {
                break
            }

            page++
        }

        val puntosHeader = database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointsByHeader(headerLocal.idHeader)
            .toMutableList()

        val catalogo = database.localphytosanitarycatalogDao()
            .getAllCatalogo()
            .toMutableList()

        var guardados = 0

        items
            .filter { item ->
                val itemHeader = extraerIdFlexible(item.header)
                itemHeader.isNullOrBlank() || itemHeader == headerExtId
            }
            .forEach { item ->
                val guardado = guardarCheckpointApi(
                    item = item,
                    headerLocal = headerLocal,
                    puntosHeader = puntosHeader,
                    catalogo = catalogo
                )

                if (guardado) {
                    guardados++
                }
            }

        ResultadoDescargaCheckpoints(guardados)
    }

    private suspend fun guardarCheckpointApi(
        item: PhytoCheckpointApiItem,
        headerLocal: LocalPhytomonitoringHeaderEntity,
        puntosHeader: MutableList<LocalPhytomonitoringTargetPointEntity>,
        catalogo: MutableList<LocalPhytosanitaryCatalogEntity>
    ): Boolean {
        val extId = item.id?.trim()?.takeIf { it.isNotBlank() } ?: return false

        val targetExtId = extraerIdFlexible(item.targetPoint)
            ?: extraerIdFlexible(item.target)

        val coords = extraerLatLon(item.geom?.coordinates)

        val targetLocal = buscarTargetLocal(
            targetExtId = targetExtId,
            coords = coords,
            puntosHeader = puntosHeader
        ) ?: crearTargetLocalDesdeCheckpoint(
            headerLocal = headerLocal,
            coords = coords,
            targetExtId = targetExtId
        )?.also { nuevoPunto ->
            puntosHeader.add(nuevoPunto)
        } ?: return false

        val phytoExtId = extraerIdFlexible(item.phytoIssueId)
            ?: extraerIdFlexible(item.phytoIssue)
            ?: extraerIdFlexible(item.phytosanitary)

        val qtyApi = extraerEnteroFlexible(item.qty)

        /*
         * Cuando la API devuelve phyto_issue=null y qty=0,
         * corresponde al registro "Sin plaga". Se conserva en Room
         * usando el catálogo local especial, sin inventar un ID remoto.
         */
        val fitoLocal = if (phytoExtId.isNullOrBlank() && (qtyApi ?: 0) <= 0) {
            obtenerOCrearCatalogoSinPlagaLocal(
                headerLocal = headerLocal,
                catalogo = catalogo
            )
        } else {
            val phytoExtIdSeguro = phytoExtId ?: return false

            catalogo.firstOrNull { fito ->
                fito.extId?.trim() == phytoExtIdSeguro
            } ?: crearCatalogoFitoFallback(
                item = item,
                phytoExtId = phytoExtIdSeguro,
                headerLocal = headerLocal
            )?.also { nuevoFito ->
                catalogo.add(nuevoFito)
            }
        } ?: return false

        val capturedByExt = extraerIdFlexible(item.capturedBy)
            ?: extraerIdFlexible(item.capturedByUser)

        val usuarioLocal = capturedByExt?.let { ext ->
            runCatching { database.userDao().getUserByExtId(ext) }.getOrNull()
        }

        val qty = qtyApi
        val capturedAt = parseFechaApi(item.capturedAt)

        val nuevo = LocalPhytomonitoringCheckpointEntity(
            extId = extId,
            qty = qty,
            presenceStatus = presenceStatusLocal(item.presenceStatus, qty),
            stage = item.stage,
            notes = item.notes,
            capturedAt = capturedAt,
            capturedByUserId = usuarioLocal?.idUser,
            idTargetPoint = targetLocal.idTargetPoint,
            idHeader = headerLocal.idHeader,
            idPhytosanitary = fitoLocal.idPhytosanitary,
            idLocalPlot = targetLocal.idLocalPlot
        )

        val existentePorExtId = database.localphytomonitoringcheckpointDao()
            .getCheckpointByExtId(extId)

        if (existentePorExtId != null) {
            database.localphytomonitoringcheckpointDao()
                .updateCheckpoint(nuevo.copy(idCheckpoint = existentePorExtId.idCheckpoint))
        } else {
            val mismaCapturaLocalExacta = database.localphytomonitoringcheckpointDao()
                .buscarCheckpointLocalMismaCaptura(
                    idHeader = nuevo.idHeader,
                    idTargetPoint = nuevo.idTargetPoint,
                    idPhytosanitary = nuevo.idPhytosanitary,
                    stage = nuevo.stage,
                    qty = nuevo.qty,
                    capturedAt = nuevo.capturedAt
                )

            val mismaCapturaLocalFlexible = mismaCapturaLocalExacta
                ?: buscarCheckpointLocalPendienteSimilar(nuevo)

            if (mismaCapturaLocalFlexible != null && mismaCapturaLocalFlexible.extId.isNullOrBlank()) {
                // El import CSV no regresa los IDs creados. Cuando descargamos desde API,
                // reconciliamos la fila del servidor con la captura local pendiente para
                // no duplicarla en el reporte. No dependemos de timestamp exacto porque
                // el backend puede redondear/convertir zona horaria.
                database.localphytomonitoringcheckpointDao()
                    .updateCheckpoint(nuevo.copy(idCheckpoint = mismaCapturaLocalFlexible.idCheckpoint))
            } else {
                database.localphytomonitoringcheckpointDao()
                    .upsertCheckpointFromApi(nuevo)
            }
        }

        database.LocalPhytomonitoringTargetPointDao()
            .actualizarStatusPunto(
                idTargetPoint = targetLocal.idTargetPoint,
                status = "Completado"
            )

        return true
    }

    private suspend fun buscarCheckpointLocalPendienteSimilar(
        nuevo: LocalPhytomonitoringCheckpointEntity
    ): LocalPhytomonitoringCheckpointEntity? {
        val capturasPunto = database.localphytomonitoringcheckpointDao()
            .getCheckpointsByHeaderAndTargetPoint(
                idHeader = nuevo.idHeader,
                idTargetPoint = nuevo.idTargetPoint
            )

        val nuevoTiempo = nuevo.capturedAt

        return capturasPunto
            .filter { local ->
                local.extId.isNullOrBlank() &&
                        local.idPhytosanitary == nuevo.idPhytosanitary &&
                        local.stage.orEmpty() == nuevo.stage.orEmpty() &&
                        local.qty == nuevo.qty
            }
            .minByOrNull { local ->
                val localTiempo = local.capturedAt
                if (nuevoTiempo != null && localTiempo != null) {
                    kotlin.math.abs(localTiempo - nuevoTiempo)
                } else {
                    Long.MAX_VALUE
                }
            }
            ?.takeIf { local ->
                val localTiempo = local.capturedAt
                nuevoTiempo == null || localTiempo == null ||
                        kotlin.math.abs(localTiempo - nuevoTiempo) <= 5 * 60 * 1000L
            }
    }

    private suspend fun crearTargetLocalDesdeCheckpoint(
        headerLocal: LocalPhytomonitoringHeaderEntity,
        coords: Pair<Double, Double>?,
        targetExtId: String?
    ): LocalPhytomonitoringTargetPointEntity? {
        if (coords == null) return null

        val existentePorExtId = targetExtId
            ?.takeIf { it.isNotBlank() }
            ?.let { ext ->
                runCatching {
                    database.LocalPhytomonitoringTargetPointDao()
                        .getTargetPointByExtId(ext)
                }.getOrNull()
            }

        if (existentePorExtId != null) {
            return existentePorExtId
        }

        val existentePorCoordenadas = database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointsByHeader(headerLocal.idHeader)
            .firstOrNull { punto ->
                abs(punto.lat - coords.first) < 0.0002 &&
                        abs(punto.lon - coords.second) < 0.0002
            }

        if (existentePorCoordenadas != null) {
            return existentePorCoordenadas
        }

        val nuevo = LocalPhytomonitoringTargetPointEntity(
            extId = targetExtId?.takeIf { it.isNotBlank() },
            radiusM = headerLocal.radiusTolerance.toInt().coerceAtLeast(1),
            lat = coords.first,
            lon = coords.second,
            status = "Completado",
            idHeader = headerLocal.idHeader,
            idLocalPlot = headerLocal.idLocalPlot
        )

        val idNuevo = database.LocalPhytomonitoringTargetPointDao()
            .insertTargetPoint(nuevo)

        return database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointById(idNuevo)
    }

    private suspend fun obtenerOCrearCatalogoSinPlagaLocal(
        headerLocal: LocalPhytomonitoringHeaderEntity,
        catalogo: MutableList<LocalPhytosanitaryCatalogEntity>
    ): LocalPhytosanitaryCatalogEntity? {
        val existente = catalogo.firstOrNull { fito ->
            val nombre = fito.name.trim()
            val tipo = fito.type.trim()

            nombre.equals("Sin plaga", ignoreCase = true) ||
                    tipo.equals("SIN_PLAGA", ignoreCase = true)
        }

        if (existente != null) {
            return existente
        }

        val nuevo = LocalPhytosanitaryCatalogEntity(
            extId = null,
            name = "Sin plaga",
            type = "SIN_PLAGA",
            minRefValue = 0,
            maxRefValue = 0,
            description = "Punto revisado sin presencia de plagas o enfermedades.",
            photo = null,
            idDefaultCrop = headerLocal.idCrop
        )

        val idNuevo = runCatching {
            database.localphytosanitarycatalogDao().insertPhytosanitary(nuevo)
        }.getOrNull()

        val creado = idNuevo?.let { id ->
            database.localphytosanitarycatalogDao().getPhytosanitaryById(id)
        } ?: database.localphytosanitarycatalogDao()
            .getAllCatalogo()
            .firstOrNull { fito ->
                fito.name.equals("Sin plaga", ignoreCase = true) ||
                        fito.type.equals("SIN_PLAGA", ignoreCase = true)
            }

        creado?.let { catalogo.add(it) }
        return creado
    }

    private suspend fun crearCatalogoFitoFallback(
        item: PhytoCheckpointApiItem,
        phytoExtId: String,
        headerLocal: LocalPhytomonitoringHeaderEntity
    ): LocalPhytosanitaryCatalogEntity? {
        val nombre = extraerCampoTextoFlexible(
            item.phytoIssueId,
            "name",
            "nombre",
            "label",
            "display",
            "description"
        ) ?: extraerCampoTextoFlexible(
            item.phytoIssue,
            "name",
            "nombre",
            "label",
            "display",
            "description"
        ) ?: extraerCampoTextoFlexible(
            item.phytosanitary,
            "name",
            "nombre",
            "label",
            "display",
            "description"
        ) ?: "Fitosanitario API $phytoExtId"

        val tipo = extraerCampoTextoFlexible(
            item.phytoIssue,
            "type",
            "tipo",
            "category"
        ) ?: extraerCampoTextoFlexible(
            item.phytosanitary,
            "type",
            "tipo",
            "category"
        ) ?: "API"

        val nuevo = LocalPhytosanitaryCatalogEntity(
            extId = phytoExtId,
            name = nombre,
            type = tipo,
            minRefValue = null,
            maxRefValue = null,
            description = "Creado automáticamente al descargar checkpoints desde API.",
            photo = null,
            idDefaultCrop = headerLocal.idCrop
        )

        val idNuevo = runCatching {
            database.localphytosanitarycatalogDao().insertPhytosanitary(nuevo)
        }.getOrNull() ?: return database.localphytosanitarycatalogDao()
            .getAllCatalogo()
            .firstOrNull { fito -> fito.extId?.trim() == phytoExtId }

        return database.localphytosanitarycatalogDao()
            .getPhytosanitaryById(idNuevo)
    }

    private fun construirCsvImportacion(
        checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
        puntos: Map<Long, LocalPhytomonitoringTargetPointEntity>,
        catalogoPorIdLocal: Map<Long, LocalPhytosanitaryCatalogEntity>
    ): CsvImportacion {
        val sb = StringBuilder()
        sb.appendLine("geom_lon,geom_lat,captured_at,phyto_issue_id,stage,presence_status,qty,notes")

        var filasValidas = 0
        var filasOmitidas = 0

        checkpoints.forEach { checkpoint ->
            val punto = puntos[checkpoint.idTargetPoint]
            val phytoIssueId = catalogoPorIdLocal[checkpoint.idPhytosanitary]
                ?.extId
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            if (punto == null || phytoIssueId == null) {
                filasOmitidas++
                return@forEach
            }

            filasValidas++

            val capturedAt = formatearIsoApi(checkpoint.capturedAt ?: System.currentTimeMillis())
            val presenceStatus = presenceStatusApi(checkpoint)
            val qty = checkpoint.qty ?: 0

            sb.appendLine(
                listOf(
                    punto.lon.toString(),
                    punto.lat.toString(),
                    capturedAt,
                    phytoIssueId,
                    checkpoint.stage.orEmpty(),
                    presenceStatus,
                    qty.toString(),
                    checkpoint.notes.orEmpty()
                ).joinToString(",") { escaparCsv(it) }
            )
        }

        return CsvImportacion(
            contenido = sb.toString(),
            filasValidas = filasValidas,
            filasOmitidas = filasOmitidas
        )
    }

    private fun buscarTargetLocal(
        targetExtId: String?,
        coords: Pair<Double, Double>?,
        puntosHeader: List<LocalPhytomonitoringTargetPointEntity>
    ): LocalPhytomonitoringTargetPointEntity? {
        if (!targetExtId.isNullOrBlank()) {
            puntosHeader.firstOrNull { it.extId == targetExtId }?.let { return it }
        }

        if (coords != null) {
            return puntosHeader.minByOrNull { punto ->
                abs(punto.lat - coords.first) + abs(punto.lon - coords.second)
            }?.takeIf { punto ->
                abs(punto.lat - coords.first) < 0.0002 &&
                        abs(punto.lon - coords.second) < 0.0002
            }
        }

        return null
    }

    private fun presenceStatusApi(checkpoint: LocalPhytomonitoringCheckpointEntity): String {
        val qty = checkpoint.qty ?: 0

        return when {
            checkpoint.presenceStatus == 0 -> "low"
            qty >= 10 -> "critical"
            qty > 0 -> "warning"
            checkpoint.presenceStatus == 1 -> "warning"
            else -> "low"
        }
    }

    private fun presenceStatusLocal(value: JsonElement?, qty: Int?): Int {
        val texto = extraerIdFlexible(value)?.lowercase(Locale.getDefault()).orEmpty()

        return when {
            qty == 0 -> 0
            texto == "0" || texto == "none" || texto == "absent" || texto == "ausente" || texto == "no" -> 0
            else -> 1
        }
    }

    private fun extraerIdFlexible(value: JsonElement?): String? {
        if (value == null || value.isJsonNull) return null

        return runCatching {
            when {
                value.isJsonPrimitive -> value.asString.trim().takeIf { it.isNotBlank() }

                value.isJsonObject -> {
                    val obj = value.asJsonObject
                    listOf("id", "uuid", "pk", "value")
                        .firstNotNullOfOrNull { key ->
                            obj.get(key)?.let { extraerIdFlexible(it) }
                        }
                }

                value.isJsonArray -> {
                    value.asJsonArray.firstOrNull()?.let { extraerIdFlexible(it) }
                }

                else -> null
            }
        }.getOrNull()
    }

    private fun extraerCampoTextoFlexible(value: JsonElement?, vararg keys: String): String? {
        if (value == null || value.isJsonNull) return null

        return runCatching {
            when {
                value.isJsonPrimitive -> value.asString.trim().takeIf { it.isNotBlank() }

                value.isJsonObject -> {
                    val obj = value.asJsonObject

                    keys.firstNotNullOfOrNull { key ->
                        obj.get(key)?.let { child ->
                            extraerCampoTextoFlexible(child)
                        }
                    }
                }

                value.isJsonArray -> {
                    value.asJsonArray.firstOrNull()?.let { child ->
                        extraerCampoTextoFlexible(child, *keys)
                    }
                }

                else -> null
            }
        }.getOrNull()
    }

    private fun extraerEnteroFlexible(value: JsonElement?): Int? {
        if (value == null || value.isJsonNull) return null

        return runCatching {
            when {
                value.isJsonPrimitive -> {
                    val primitive = value.asJsonPrimitive
                    if (primitive.isNumber) {
                        primitive.asDouble.toInt()
                    } else {
                        primitive.asString.trim().toDoubleOrNull()?.toInt()
                            ?: primitive.asString.trim().toIntOrNull()
                    }
                }

                value.isJsonObject -> {
                    val obj = value.asJsonObject
                    listOf("qty", "count", "value")
                        .firstNotNullOfOrNull { key ->
                            extraerEnteroFlexible(obj.get(key))
                        }
                }

                else -> null
            }
        }.getOrNull()
    }

    private fun extraerLatLon(coordinates: List<Double>?): Pair<Double, Double>? {
        if (coordinates == null || coordinates.size < 2) return null

        val a = coordinates[0]
        val b = coordinates[1]

        return when {
            abs(a) > 90.0 && abs(b) <= 90.0 -> b to a
            abs(b) > 90.0 && abs(a) <= 90.0 -> a to b
            else -> b to a
        }
    }

    private fun formatearIsoApi(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timeMillis))
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
            runCatching {
                SimpleDateFormat(patron, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(limpia)?.time
            }.getOrNull()?.let { return it }
        }

        return null
    }

    private fun escaparCsv(valor: String): String {
        val requiereComillas = valor.contains(",") ||
                valor.contains("\"") ||
                valor.contains("\n") ||
                valor.contains("\r")

        val limpio = valor.replace("\"", "\"\"")

        return if (requiereComillas) {
            "\"$limpio\""
        } else {
            limpio
        }
    }

    private fun claveSync(headerExtId: String, idCheckpointLocal: Long): String {
        return "header_${headerExtId}_checkpoint_$idCheckpointLocal"
    }
}

private data class CsvImportacion(
    val contenido: String,
    val filasValidas: Int,
    val filasOmitidas: Int
)

data class ResultadoDescargaCheckpoints(
    val cantidad: Int
)

sealed class ResultadoCheckpointSync {
    data class Exito(
        val subidos: Int,
        val descargados: Int,
        val omitidos: Int,
        val mensaje: String
    ) : ResultadoCheckpointSync()

    data class Error(
        val mensaje: String
    ) : ResultadoCheckpointSync()
}

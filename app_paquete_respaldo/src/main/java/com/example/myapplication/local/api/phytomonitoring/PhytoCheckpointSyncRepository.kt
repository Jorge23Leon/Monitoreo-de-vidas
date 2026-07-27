package com.example.myapplication.local.api.phytomonitoring

// CAMBIO PUNTOS/CSV: al crear o reconciliar puntos con API, se conserva el label
// real para no sustituirlo por el id local de Room.

import android.content.Context
import com.example.myapplication.local.api.core.ApiConfig
import com.example.myapplication.local.api.core.ApiDateParser
import com.example.myapplication.local.api.core.RetrofitClient
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.monitoreo.media.PhytoMediaStorage
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
    private companion object {
        const val MAX_PAGINAS = 500
    }
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
    /**
     * Sincronización móvil oficial en dos pasos:
     * 1) genera/importa CSV de checkpoints;
     * 2) sube un ZIP con las evidencias fotográficas.
     *
     * No se borran datos locales si falla cualquier paso. Las fotos solo pasan de
     * pending a uploaded cuando el backend confirma que encontró su checkpoint.
     */
    /**
     * Sincronización móvil oficial.
     *
     * Cada captura local se envía por JSON para conservar la relación real:
     * Header -> TargetPoint -> Checkpoint. El CSV no conserva el UUID del target,
     * por eso queda únicamente como compatibilidad histórica y no se usa aquí.
     */
    suspend fun sincronizarHeaderCsv(
        headerLocal: LocalPhytomonitoringHeaderEntity
    ): ResultadoCheckpointSync = withContext(Dispatchers.IO) {
        val headerExtId = headerLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext ResultadoCheckpointSync.Error(
                "El monitoreo local no tiene ext_id del servidor; no se pueden sincronizar capturas."
            )

        val descargaInicial = descargarCheckpointsHeaderDesdeApi(headerLocal)
        if (descargaInicial.error != null) {
            return@withContext ResultadoCheckpointSync.Error(
                "No se pudo consultar la sesión antes de sincronizar. ${descargaInicial.error}"
            )
        }

        val checkpointDao = database.localphytomonitoringcheckpointDao()

        /*
         * Primero recuperamos las referencias de fotos antiguas (h7_p..., h10_p...).
         * Así el PATCH que sigue ya manda photo_ref al servidor antes de crear el ZIP.
         */
        val nombresFotosPendientes = PhytoMediaStorage
            .listarNombresFotosPendientes(
                context = context,
                idHeader = headerLocal.idHeader
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val checkpointsLocales = reconciliarFotosLegacyPendientes(
            idHeaderLocal = headerLocal.idHeader,
            checkpointsOriginales = checkpointDao.getCheckpointsByHeader(headerLocal.idHeader),
            nombresPendientes = nombresFotosPendientes
        )

        val puntos = database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointsByHeader(headerLocal.idHeader)
            .associateBy { it.idTargetPoint }

        val idsPuntosQueNecesitanVinculoRemoto = puntos.values
            .filter { it.extId.isNullOrBlank() }
            .map { it.idTargetPoint }
            .toSet()

        val catalogo = database.localphytosanitarycatalogDao()
            .getAllCatalogo()
            .associateBy { it.idPhytosanitary }

        val mensajes = mutableListOf<String>()
        var creadosJson = 0
        var omitidos = 0
        var capturasPendientes = false

        /*
         * Asegura TODOS los targets locales del header, incluso si todavía no
         * tienen checkpoint. Así el contador Targets del backend no queda en 0.
         */
        val targetExtPorPunto = mutableMapOf<Long, String>()
        puntos.values.forEach { punto ->
            val targetExtId = asegurarTargetPointEnApi(
                headerLocal = headerLocal,
                puntoLocal = punto
            )

            if (targetExtId.isNullOrBlank()) {
                capturasPendientes = true
                mensajes += "No se pudo crear/vincular el target del punto ${punto.label.ifBlank { punto.idTargetPoint.toString() }}."
            } else {
                targetExtPorPunto[punto.idTargetPoint] = targetExtId
            }
        }

        /*
         * Repara capturas creadas antes con CSV: ya existen en servidor, pero
         * quedaron sin target porque CSV solo conserva pcp_oid local.
         */
        checkpointsLocales
            .filter { checkpoint ->
                if (checkpoint.extId.isNullOrBlank()) return@filter false

                val necesitaTarget = checkpoint.idTargetPoint in
                        idsPuntosQueNecesitanVinculoRemoto
                val photoRef = obtenerPhotoRefLocal(checkpoint)
                val necesitaPhotoRef = !photoRef.isNullOrBlank() &&
                        photoRef in nombresFotosPendientes

                necesitaTarget || necesitaPhotoRef
            }
            .forEach { checkpoint ->
                val targetExtId = targetExtPorPunto[checkpoint.idTargetPoint] ?: return@forEach

                val vinculado = vincularCheckpointExistenteConTarget(
                    checkpointExtId = checkpoint.extId!!.trim(),
                    targetExtId = targetExtId,
                    photoRef = obtenerPhotoRefLocal(checkpoint)
                )

                if (!vinculado) {
                    /*
                     * No se puede cerrar el header todavía: esa captura antigua
                     * seguiría sin relación Checkpoint -> Target en el servidor.
                     */
                    capturasPendientes = true
                    mensajes += "No se pudo vincular al target la captura ${checkpoint.extId}."
                }
            }

        checkpointsLocales
            .filter { it.extId.isNullOrBlank() }
            .forEach { checkpoint ->
                val punto = puntos[checkpoint.idTargetPoint]
                if (punto == null) {
                    omitidos++
                    capturasPendientes = true
                    mensajes += "Checkpoint ${checkpoint.idCheckpoint}: no tiene punto local."
                    return@forEach
                }

                val fito = checkpoint.idPhytosanitary?.let { catalogo[it] }
                val esSinPlaga = esCheckpointSinPlaga(fito, checkpoint)
                val esEnfermedad = esCheckpointEnfermedad(fito)

                val phytoIssueId = if (esSinPlaga) {
                    null
                } else {
                    fito?.extId
                        ?.trim()
                        ?.toIntOrNull()
                }

                val stage = checkpoint.stage
                    ?.trim()
                    .orEmpty()

                if (!esSinPlaga && phytoIssueId == null) {
                    omitidos++
                    capturasPendientes = true
                    mensajes += "Checkpoint ${checkpoint.idCheckpoint}: fitosanitario sin ext_id numérico."
                    return@forEach
                }

                val enfermedadNoPresente = esEnfermedad && checkpoint.presenceStatus == 0

                /*
                 * La enfermedad no presente se sincroniza sin etapa.
                 * Cuando está presente, debe llevar una de las tres fases permitidas.
                 * Las plagas conservan su requisito normal de etapa.
                 */
                if (!esSinPlaga && !enfermedadNoPresente && stage.isBlank()) {
                    omitidos++
                    capturasPendientes = true
                    mensajes += if (esEnfermedad) {
                        "Checkpoint ${checkpoint.idCheckpoint}: enfermedad presente sin fase."
                    } else {
                        "Checkpoint ${checkpoint.idCheckpoint}: falta etapa."
                    }
                    return@forEach
                }

                val targetExtId = targetExtPorPunto[checkpoint.idTargetPoint]
                if (targetExtId.isNullOrBlank()) {
                    omitidos++
                    capturasPendientes = true
                    mensajes += "Checkpoint ${checkpoint.idCheckpoint}: el target aún no existe en el servidor."
                    return@forEach
                }

                val creado = subirCheckpointJson(
                    headerExtId = headerExtId,
                    targetExtId = targetExtId,
                    checkpoint = checkpoint,
                    puntoLocal = punto,
                    phytoIssueId = phytoIssueId,
                    esSinPlaga = esSinPlaga,
                    esEnfermedad = esEnfermedad
                )

                val checkpointExtId = creado?.id
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                if (checkpointExtId == null) {
                    omitidos++
                    capturasPendientes = true
                    mensajes += "Checkpoint ${checkpoint.idCheckpoint}: el servidor no confirmó la captura."
                    return@forEach
                }

                val photoRefCalculada = obtenerPhotoRefLocal(checkpoint)

                checkpointDao.updateCheckpoint(
                    checkpoint.copy(
                        extId = checkpointExtId,
                        photoRef = creado.photoRef
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: checkpoint.photoRef
                            ?: photoRefCalculada,
                        photoUrl = normalizarUrlMedia(
                            creado.photoUrl ?: creado.photo
                        ) ?: checkpoint.photoUrl
                    )
                )

                creadosJson++
            }

        val descargadosFinales = descargaInicial.cantidad + creadosJson

        val fotos = subirFotosPendientes(
            headerExtId = headerExtId,
            idHeaderLocal = headerLocal.idHeader
        )

        var fotosPendientes = capturasPendientes
        when (fotos) {
            is ResultadoFotosZip.Error -> {
                fotosPendientes = true
                mensajes += fotos.mensaje
            }

            is ResultadoFotosZip.Exito -> {
                if (fotos.movidas > 0) {
                    mensajes += "${fotos.movidas} foto(s) confirmada(s) por el servidor."
                }
                if (fotos.noEmparejadas.isNotEmpty()) {
                    fotosPendientes = true
                    mensajes += "Fotos sin checkpoint en servidor: ${fotos.noEmparejadas.joinToString()}"
                }
                if (fotos.checkpointsSinFoto.isNotEmpty()) {
                    fotosPendientes = true
                    mensajes += "Checkpoints sin evidencia en servidor: ${fotos.checkpointsSinFoto.joinToString()}"
                }
                if (fotos.pendientesSinCheckpoint.isNotEmpty()) {
                    fotosPendientes = true
                    mensajes += "Fotos locales sin checkpoint confirmado: ${fotos.pendientesSinCheckpoint.joinToString()}"
                }
                if (fotos.pendientesSinConfirmar.isNotEmpty()) {
                    fotosPendientes = true
                    mensajes += "El servidor no confirmó estas fotos; se conservaron para reintentar: ${fotos.pendientesSinConfirmar.joinToString()}"
                }
            }
        }

        /*
         * El header se actualiza hasta que checkpoints y fotos están listos.
         * Así no se bloquea el ZIP por dejar la sesión completed antes de tiempo.
         */
        if (!fotosPendientes) {
            val estadoEnviado = sincronizarEstadoHeaderServidor(headerLocal)
            if (!estadoEnviado) {
                fotosPendientes = true
                mensajes += "Capturas enviadas, pero no se pudo actualizar el estado del monitoreo."
            }
        }

        ResultadoCheckpointSync.Exito(
            subidos = creadosJson,
            descargados = descargadosFinales,
            omitidos = omitidos,
            fotosPendientes = fotosPendientes,
            mensaje = buildString {
                when {
                    creadosJson > 0 -> append("Capturas y targets sincronizados correctamente.")
                    checkpointsLocales.none { it.extId.isNullOrBlank() } -> append("No había capturas nuevas; se revisaron targets y evidencias.")
                    else -> append("Sincronización terminada.")
                }
                if (mensajes.isNotEmpty()) append(" ${mensajes.distinct().joinToString(" | ")}")
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

        database.localphytomonitoringheaderDao()
            .marcarHeaderSincronizado(headerLocal.idHeader)

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

    private fun esCheckpointEnfermedad(
        fitoLocal: LocalPhytosanitaryCatalogEntity?
    ): Boolean {
        return fitoLocal?.type
            ?.trim()
            ?.lowercase(Locale.getDefault())
            ?.contains("enfermedad") == true
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

        val body = PhytoTargetPointCreateRequest(
            header = headerExtId,
            geom = PhytoGeomApi(
                type = "Point",
                coordinates = listOf(puntoLocal.lon, puntoLocal.lat)
            ),
            radiusM = puntoLocal.radiusM.toDouble().coerceAtLeast(1.0),
            label = puntoLocal.label
                .trim()
                .ifBlank { "Punto ${puntoLocal.idTargetPoint}" },
            status = "pending"
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
                    status = puntoLocal.status
                )
            )

        return targetExtId
    }

    private suspend fun vincularCheckpointExistenteConTarget(
        checkpointExtId: String,
        targetExtId: String,
        photoRef: String?
    ): Boolean {
        return try {
            val response = api.actualizarCheckpoint(
                id = checkpointExtId,
                body = PhytoCheckpointPatchRequest(
                    target = targetExtId,
                    photoRef = photoRef
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                )
            )

            if (!response.isSuccessful) {
                android.util.Log.w(
                    "SYNC_PHYTO_JSON",
                    "No se pudo reparar checkpoint $checkpointExtId. " +
                            "HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}"
                )
                false
            } else {
                true
            }
        } catch (e: Exception) {
            android.util.Log.w(
                "SYNC_PHYTO_JSON",
                "Error reparando checkpoint $checkpointExtId: ${e.message}",
                e
            )
            false
        }
    }
    private fun obtenerPhotoRefLocal(
        checkpoint: LocalPhytomonitoringCheckpointEntity
    ): String? {
        checkpoint.photoRef
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val capturedAt = checkpoint.capturedAt ?: return null

        return PhytoMediaStorage.buscarFotoPendiente(
            context = context,
            idHeader = checkpoint.idHeader,
            idTargetPoint = checkpoint.idTargetPoint,
            capturedAt = capturedAt
        )?.name
    }

    private suspend fun subirCheckpointJson(
        headerExtId: String,
        targetExtId: String,
        checkpoint: LocalPhytomonitoringCheckpointEntity,
        puntoLocal: LocalPhytomonitoringTargetPointEntity,
        phytoIssueId: Int?,
        esSinPlaga: Boolean,
        esEnfermedad: Boolean
    ): PhytoCheckpointApiItem? {
        val enfermedadNoPresente = esEnfermedad && checkpoint.presenceStatus == 0

        val stage = if (esSinPlaga || enfermedadNoPresente) {
            null
        } else {
            normalizarEtapaParaServidor(checkpoint.stage)
        }

        /*
         * Sin plaga no lleva fitosanitario ni etapa.
         * Una enfermedad No presente conserva su fitosanitario, pero no lleva etapa.
         * Cualquier plaga o enfermedad presente requiere etapa.
         */
        if (!esSinPlaga && phytoIssueId == null) {
            return null
        }

        if (!esSinPlaga && !enfermedadNoPresente && stage == null) {
            return null
        }

        val qty = if (esSinPlaga || enfermedadNoPresente) {
            0
        } else {
            checkpoint.qty ?: 0
        }

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
            photoRef = obtenerPhotoRefLocal(checkpoint),
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
     *
     * Importante: HTTP 403/404/500 y respuestas vacías se reportan como error.
     * Nunca se convierten en cantidad 0 porque eso daba falsos "Sincronizado".
     */
    suspend fun descargarCheckpointsHeaderDesdeApi(
        headerLocal: LocalPhytomonitoringHeaderEntity
    ): ResultadoDescargaCheckpoints = withContext(Dispatchers.IO) {
        val headerExtId = headerLocal.extId?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext ResultadoDescargaCheckpoints(
                cantidad = 0,
                error = "El header local no tiene ext_id."
            )

        val items = mutableListOf<PhytoCheckpointApiItem>()
        var page = 1

        while (true) {
            val response = try {
                api.listarCheckpoints(
                    header = headerExtId,
                    page = page
                )
            } catch (e: Exception) {
                return@withContext ResultadoDescargaCheckpoints(
                    cantidad = items.size,
                    error = "No se pudieron descargar checkpoints: ${e.message ?: e.javaClass.simpleName}."
                )
            }

            if (!response.isSuccessful) {
                val detail = response.errorBody()?.string().orEmpty()
                    .replace(Regex("\\s+"), " ")
                    .take(300)
                return@withContext ResultadoDescargaCheckpoints(
                    cantidad = items.size,
                    error = "GET checkpoints HTTP ${response.code()}: $detail"
                )
            }

            val body = response.body()
                ?: return@withContext ResultadoDescargaCheckpoints(
                    cantidad = items.size,
                    error = "El servidor respondió vacío al consultar checkpoints."
                )

            items.addAll(body.results)

            if (body.next.isNullOrBlank()) break
            if (body.results.isEmpty() || page >= MAX_PAGINAS) {
                return@withContext ResultadoDescargaCheckpoints(
                    cantidad = items.size,
                    error = "La paginación de checkpoints es inválida o excede $MAX_PAGINAS páginas."
                )
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

                if (guardado) guardados++
            }

        ResultadoDescargaCheckpoints(cantidad = guardados)
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
        val photoRefApi = item.photoRef
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val photoUrlApi = normalizarUrlMedia(item.photoUrl ?: item.photo)

        val existentePorExtId = database.localphytomonitoringcheckpointDao()
            .getCheckpointByExtId(extId)

        val nuevo = LocalPhytomonitoringCheckpointEntity(
            extId = extId,
            qty = qty,
            /*
             * Las plagas se registran por cantidad; por eso localmente pueden
             * venir con presenceStatus=null. Normalizamos a 1 cuando qty > 0
             * para que Room y el semáforo las reconozcan como presentes.
             * Las enfermedades conservan 0/1 porque sí manejan "No presente".
             */
            presenceStatus = when {
                esCheckpointEnfermedad(fitoLocal) -> {
                    presenceStatusLocal(item.presenceStatus, qty)
                }

                (qty ?: 0) > 0 -> 1
                else -> null
            },
            stage = item.stage,
            notes = item.notes,
            photoRef = photoRefApi ?: existentePorExtId?.photoRef,
            photoLocalPath = existentePorExtId?.photoLocalPath,
            photoUrl = photoUrlApi ?: existentePorExtId?.photoUrl,
            capturedAt = capturedAt,
            capturedByUserId = usuarioLocal?.idUser,
            idTargetPoint = targetLocal.idTargetPoint,
            idHeader = headerLocal.idHeader,
            idPhytosanitary = fitoLocal.idPhytosanitary,
            idLocalPlot = targetLocal.idLocalPlot
        )

        if (existentePorExtId != null) {
            val actualizado = nuevo.copy(idCheckpoint = existentePorExtId.idCheckpoint)
            if (actualizado != existentePorExtId) {
                database.localphytomonitoringcheckpointDao()
                    .updateCheckpoint(actualizado)
            }
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
                // El import CSV no regresa los IDs creados. Al descargar se conserva la
                // referencia y la ruta local que ya tenía la captura offline.
                val actualizado = nuevo.copy(
                    idCheckpoint = mismaCapturaLocalFlexible.idCheckpoint,
                    photoRef = photoRefApi ?: mismaCapturaLocalFlexible.photoRef,
                    photoLocalPath = mismaCapturaLocalFlexible.photoLocalPath,
                    photoUrl = photoUrlApi ?: mismaCapturaLocalFlexible.photoUrl
                )
                if (actualizado != mismaCapturaLocalFlexible) {
                    database.localphytomonitoringcheckpointDao()
                        .updateCheckpoint(actualizado)
                }
            } else {
                database.localphytomonitoringcheckpointDao()
                    .upsertCheckpointFromApi(nuevo)
            }
        }
        if (!targetLocal.status.equals("Completado", ignoreCase = true)) {
            database.LocalPhytomonitoringTargetPointDao()
                .actualizarStatusPunto(
                    idTargetPoint = targetLocal.idTargetPoint,
                    status = "Completado"
                )

            val indiceTarget = puntosHeader.indexOfFirst {
                it.idTargetPoint == targetLocal.idTargetPoint
            }
            if (indiceTarget >= 0) {
                puntosHeader[indiceTarget] = targetLocal.copy(status = "Completado")
            }
        }

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
    private fun normalizarEtapaParaServidor(etapa: String?): String? {
        return when (etapa?.trim()?.lowercase()) {
            null, "" -> null

            "huevecillo", "huevesillo" -> "huevesillo"
            "larva", "larva / joven" -> "larva"
            "ninfa", "ninfa / joven" -> "ninfa"
            "pupa" -> "pupa"
            "adulto" -> "adulto"
            "adulto con alas" -> "adulto_alas"

            "inicio" -> "inicio"
            "desarrollo" -> "desarrollo"
            "avanzado", "avanzado / crítico", "avanzado / critico" -> "avanzado"
            "terminal", "terminal / podrido" -> "terminal"

            else -> etapa.trim().lowercase()
        }
    }

    private fun construirCsvImportacion(
        checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
        puntos: Map<Long, LocalPhytomonitoringTargetPointEntity>,
        catalogoPorIdLocal: Map<Long, LocalPhytosanitaryCatalogEntity>
    ): CsvImportacion {
        val sb = StringBuilder()
        sb.appendLine(
            "geom_lon,geom_lat,captured_at,phyto_issue_id,stage,presence_status,qty,pcp_oid,photo,notes"
        )

        var filasValidas = 0
        var filasOmitidas = 0
        val mensajes = mutableListOf<String>()

        checkpoints.forEach { checkpoint ->
            val punto = puntos[checkpoint.idTargetPoint]
            val fito = checkpoint.idPhytosanitary?.let { catalogoPorIdLocal[it] }
            val esSinPlaga = esCheckpointSinPlaga(fito, checkpoint)

            /*
             * Contrato actual del endpoint CSV:
             * phyto_issue_id y stage son obligatorios. Por eso "Sin plaga" y
             * registros sin etapa se mantienen locales hasta que backend permita
             * valores nulos para esas dos columnas.
             */
            if (esSinPlaga) {
                filasOmitidas++
                mensajes += "Punto ${checkpoint.idTargetPoint}: 'Sin plaga' requiere ajuste del endpoint CSV (phyto_issue_id/stage obligatorios)."
                return@forEach
            }

            if (punto == null) {
                filasOmitidas++
                mensajes += "Checkpoint ${checkpoint.idCheckpoint}: no tiene punto local."
                return@forEach
            }

            val phytoIssueId = fito?.extId
                ?.trim()
                ?.toIntOrNull()

            if (phytoIssueId == null) {
                filasOmitidas++
                mensajes += "Checkpoint ${checkpoint.idCheckpoint}: fitosanitario sin ext_id numérico."
                return@forEach
            }

            val stage = checkpoint.stage?.trim().orEmpty()
            if (stage.isBlank()) {
                filasOmitidas++
                mensajes += "Checkpoint ${checkpoint.idCheckpoint}: falta etapa; el endpoint CSV la exige."
                return@forEach
            }

            val capturedAtMillis = checkpoint.capturedAt
            if (capturedAtMillis == null) {
                filasOmitidas++
                mensajes += "Checkpoint ${checkpoint.idCheckpoint}: falta fecha/hora de captura."
                return@forEach
            }

            // Room es la fuente de la relación checkpoint ↔ evidencia.
            // El respaldo por carpeta mantiene compatibilidad con registros viejos.
            val photoName = checkpoint.photoRef
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: PhytoMediaStorage.buscarFotoPendiente(
                    context = context,
                    idHeader = checkpoint.idHeader,
                    idTargetPoint = checkpoint.idTargetPoint,
                    capturedAt = capturedAtMillis
                )?.name.orEmpty()

            sb.appendLine(
                listOf(
                    punto.lon.toString(),
                    punto.lat.toString(),
                    formatearIsoApi(capturedAtMillis),
                    phytoIssueId.toString(),
                    stage,
                    presenceStatusApi(checkpoint),
                    (checkpoint.qty ?: 0).toString(),
                    checkpoint.idTargetPoint.toString(),
                    photoName,
                    checkpoint.notes.orEmpty()
                ).joinToString(",") { escaparCsv(it) }
            )

            filasValidas++
        }

        return CsvImportacion(
            contenido = sb.toString(),
            filasValidas = filasValidas,
            filasOmitidas = filasOmitidas,
            mensajesOmitidos = mensajes
        )
    }

    private suspend fun importarCsv(
        headerExtId: String,
        contenidoCsv: String
    ): ResultadoImportacionCsv {
        val directory = File(context.cacheDir, "phyto_sync_csv").apply { mkdirs() }
        val csvFile = File.createTempFile("phyto_${headerExtId.take(8)}_", ".csv", directory)

        return try {
            csvFile.writeText(contenidoCsv, Charsets.UTF_8)

            val headerBody = headerExtId.toRequestBody("text/plain".toMediaType())
            val csvBody = csvFile.asRequestBody("text/csv; charset=utf-8".toMediaType())
            val csvPart = MultipartBody.Part.createFormData(
                "csv_file",
                "checkpoints_${System.currentTimeMillis()}.csv",
                csvBody
            )

            val response = api.importarCheckpointsCsv(
                header = headerBody,
                csv_file = csvPart
            )

            if (!response.isSuccessful) {
                val detail = response.errorBody()?.string().orEmpty()
                return ResultadoImportacionCsv.Error(
                    when (response.code()) {
                        403 -> "El servidor rechazó el CSV (403). Verifica que la sesión siga activa y que el usuario tenga rol Técnico o superior."
                        409 -> "La sesión está cerrada en el servidor (409). Debe estar pending o in_progress para importar capturas."
                        else -> "Error importando CSV HTTP ${response.code()}: $detail"
                    }
                )
            }

            val body = response.body()
                ?: return ResultadoImportacionCsv.Error("El servidor respondió sin detalle al importar el CSV.")

            ResultadoImportacionCsv.Exito(body.created ?: 0)
        } catch (e: Exception) {
            ResultadoImportacionCsv.Error("No se pudo enviar el CSV: ${e.message}")
        } finally {
            csvFile.delete()
        }
    }

    /**
     * Deja apuntando a la ruta real de uploaded incluso para evidencias antiguas
     * cuyo capturedAt ya no coincide exactamente con el nombre del archivo.
     */
    private suspend fun actualizarRutasLocalesFotosConfirmadas(
        idHeaderLocal: Long,
        fileNames: Set<String>
    ) {
        if (fileNames.isEmpty()) return

        val dao = database.localphytomonitoringcheckpointDao()
        dao.getCheckpointsByHeader(idHeaderLocal)
            .filter { checkpoint ->
                checkpoint.photoRef?.trim() in fileNames
            }
            .forEach { checkpoint ->
                val photoRef = checkpoint.photoRef?.trim().orEmpty()
                val file = PhytoMediaStorage.buscarFotoLocalPorNombre(
                    context = context,
                    idHeader = checkpoint.idHeader,
                    fileName = photoRef
                )

                if (file != null && checkpoint.photoLocalPath != file.absolutePath) {
                    dao.updateCheckpoint(
                        checkpoint.copy(photoLocalPath = file.absolutePath)
                    )
                }
            }
    }

    private data class ReferenciaFotoLocal(
        val idHeader: Long,
        val idTargetPoint: Long,
        val capturedAt: Long
    )

    /**
     * La nomenclatura de evidencia es h{header}_p{punto}_{fecha}.jpg.
     * Se usa solo con archivos propios de la app y nunca con rutas del servidor.
     */
    private fun parsearReferenciaFotoLocal(nombre: String): ReferenciaFotoLocal? {
        val match = Regex(
            pattern = "^h(\\d+)_p(\\d+)_(\\d+)\\.[A-Za-z0-9]+$",
            option = RegexOption.IGNORE_CASE
        ).matchEntire(nombre.trim()) ?: return null

        val idHeader = match.groupValues[1].toLongOrNull() ?: return null
        val idTargetPoint = match.groupValues[2].toLongOrNull() ?: return null
        val capturedAt = match.groupValues[3].toLongOrNull() ?: return null

        return ReferenciaFotoLocal(
            idHeader = idHeader,
            idTargetPoint = idTargetPoint,
            capturedAt = capturedAt
        )
    }

    /**
     * Repara evidencias creadas por versiones anteriores que guardaron el JPG en
     * pending, pero no persistieron photoRef en Room. Primero exige misma sesión,
     * mismo punto y misma fecha. Como respaldo seguro, usa el punto completo solo
     * cuando todos sus checkpoints sin evidencia pertenecen a una única captura.
     */
    private suspend fun reconciliarFotosLegacyPendientes(
        idHeaderLocal: Long,
        checkpointsOriginales: List<LocalPhytomonitoringCheckpointEntity>,
        nombresPendientes: Set<String>
    ): List<LocalPhytomonitoringCheckpointEntity> {
        if (nombresPendientes.isEmpty()) return checkpointsOriginales

        val dao = database.localphytomonitoringcheckpointDao()
        val reemplazos = mutableMapOf<Long, LocalPhytomonitoringCheckpointEntity>()
        val toleranciaMilis = 2_000L

        nombresPendientes.forEach { photoRef ->
            val clave = parsearReferenciaFotoLocal(photoRef) ?: return@forEach
            if (clave.idHeader != idHeaderLocal) return@forEach

            val archivo = PhytoMediaStorage.buscarFotoPendientePorNombre(
                context = context,
                idHeader = idHeaderLocal,
                fileName = photoRef
            ) ?: return@forEach

            val mismoPunto = checkpointsOriginales.filter { checkpoint ->
                checkpoint.idHeader == idHeaderLocal &&
                        checkpoint.idTargetPoint == clave.idTargetPoint
            }

            if (mismoPunto.isEmpty()) return@forEach

            val porFecha = mismoPunto.filter { checkpoint ->
                val capturedAt = checkpoint.capturedAt ?: return@filter false
                abs(capturedAt - clave.capturedAt) <= toleranciaMilis
            }

            /*
             * Si la API histórica alteró captured_at al reconciliar, solamente se
             * toma el punto completo cuando hay una sola captura sin photoRef. Así
             * jamás se asigna una foto a una visita distinta del mismo punto.
             */
            val grupoCandidato = if (porFecha.isNotEmpty()) {
                porFecha
            } else {
                val sinReferencia = mismoPunto.filter { checkpoint ->
                    checkpoint.photoRef.isNullOrBlank()
                }

                val instantes = sinReferencia
                    .mapNotNull { it.capturedAt }
                    .distinct()

                if (sinReferencia.isNotEmpty() && instantes.size == 1) {
                    sinReferencia
                } else {
                    emptyList()
                }
            }

            if (grupoCandidato.isEmpty()) return@forEach

            /* Si algún checkpoint ya tiene otra foto, el caso es ambiguo. */
            if (grupoCandidato.any { checkpoint ->
                    val existente = checkpoint.photoRef?.trim().orEmpty()
                    existente.isNotBlank() && existente != photoRef
                }
            ) {
                return@forEach
            }

            grupoCandidato.forEach { checkpoint ->
                if (
                    checkpoint.photoRef?.trim() != photoRef ||
                    checkpoint.photoLocalPath != archivo.absolutePath
                ) {
                    val actualizado = checkpoint.copy(
                        photoRef = photoRef,
                        photoLocalPath = archivo.absolutePath
                    )
                    dao.updateCheckpoint(actualizado)
                    reemplazos[checkpoint.idCheckpoint] = actualizado
                }
            }
        }

        return checkpointsOriginales.map { checkpoint ->
            reemplazos[checkpoint.idCheckpoint] ?: checkpoint
        }
    }

    /**
     * Flujo oficial de evidencias:
     * 1) valida/repara photo_ref en Room;
     * 2) escribe photo_ref en cada checkpoint remoto;
     * 3) sube UN ZIP por header;
     * 4) mueve a uploaded solo los nombres confirmados por backend.
     *
     * No usa PATCH multipart por checkpoint porque una misma evidencia puede estar
     * asociada a varias fases del mismo punto.
     */
    private suspend fun subirFotosPendientes(
        headerExtId: String,
        idHeaderLocal: Long
    ): ResultadoFotosZip {
        val checkpointDao = database.localphytomonitoringcheckpointDao()

        val nombresPendientes = PhytoMediaStorage
            .listarNombresFotosPendientes(
                context = context,
                idHeader = idHeaderLocal
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        if (nombresPendientes.isEmpty()) {
            return ResultadoFotosZip.Exito(movidas = 0)
        }

        /*
         * Defensa adicional: si esta función se reutiliza desde otro flujo, vuelve
         * a ejecutar la reconciliación de fotos legacy antes de depender de photoRef.
         */
        val checkpoints = reconciliarFotosLegacyPendientes(
            idHeaderLocal = idHeaderLocal,
            checkpointsOriginales = checkpointDao.getCheckpointsByHeader(idHeaderLocal),
            nombresPendientes = nombresPendientes
        )

        val referenciasParaZip = linkedSetOf<String>()
        val pendientesSinCheckpoint = linkedSetOf<String>()
        val pendientesSinConfirmar = linkedSetOf<String>()

        for (photoRef in nombresPendientes) {
            val grupo = checkpoints.filter { checkpoint ->
                checkpoint.photoRef?.trim() == photoRef
            }

            if (grupo.isEmpty() || grupo.any { it.extId.isNullOrBlank() }) {
                pendientesSinCheckpoint += photoRef
                continue
            }

            val archivo = PhytoMediaStorage.buscarFotoPendientePorNombre(
                context = context,
                idHeader = idHeaderLocal,
                fileName = photoRef
            )

            if (archivo == null || !archivo.exists() || archivo.length() <= 0L) {
                pendientesSinConfirmar += photoRef
                continue
            }

            /*
             * upload-photos/ empareja por photo_ref. Se manda antes el PATCH JSON,
             * incluso para checkpoints creados por una versión anterior.
             */
            var referenciaConfirmadaEnServidor = true

            for (checkpoint in grupo) {
                val checkpointExtId = checkpoint.extId
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                if (checkpointExtId == null) {
                    referenciaConfirmadaEnServidor = false
                    break
                }

                val response = try {
                    api.actualizarCheckpoint(
                        id = checkpointExtId,
                        body = PhytoCheckpointPatchRequest(
                            photoRef = photoRef
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "SYNC_PHYTO_FOTO",
                        "Error de red al guardar photo_ref=$photoRef en checkpoint=$checkpointExtId",
                        e
                    )
                    referenciaConfirmadaEnServidor = false
                    break
                }

                if (!response.isSuccessful) {
                    android.util.Log.e(
                        "SYNC_PHYTO_FOTO",
                        "Error HTTP ${response.code()} al guardar photo_ref=$photoRef " +
                                "en checkpoint=$checkpointExtId: ${response.errorBody()?.string().orEmpty()}"
                    )
                    referenciaConfirmadaEnServidor = false
                    break
                }
            }

            if (referenciaConfirmadaEnServidor) {
                referenciasParaZip += photoRef
            } else {
                pendientesSinConfirmar += photoRef
            }
        }

        val zip = try {
            PhytoMediaStorage.crearZipPendiente(
                context = context,
                idHeader = idHeaderLocal,
                fileNamesPermitidos = referenciasParaZip
            )
        } catch (e: Exception) {
            return ResultadoFotosZip.Error(
                "No se pudo crear el ZIP de evidencias: ${e.message ?: e.javaClass.simpleName}"
            )
        }

        if (zip == null) {
            return ResultadoFotosZip.Exito(
                movidas = 0,
                pendientesSinCheckpoint = pendientesSinCheckpoint.toList(),
                pendientesSinConfirmar = pendientesSinConfirmar.toList()
            )
        }

        return try {
            val headerBody = headerExtId.toRequestBody("text/plain".toMediaType())
            val zipBody = zip.file.asRequestBody("application/zip".toMediaType())
            val zipPart = MultipartBody.Part.createFormData(
                "photos_zip",
                "phyto_header_${idHeaderLocal}.zip",
                zipBody
            )

            val response = api.subirFotosCheckpointsZip(
                header = headerBody,
                photosZip = zipPart
            )

            if (!response.isSuccessful) {
                val detail = response.errorBody()?.string().orEmpty()
                return ResultadoFotosZip.Error(
                    when (response.code()) {
                        400 -> "El servidor rechazó el ZIP de evidencias (400): $detail"
                        403 -> "No se pueden subir fotos: el usuario no tiene permiso para esta sesión (403)."
                        404 -> "No se pueden subir fotos: la sesión no existe o está fuera del alcance del usuario (404)."
                        409 -> "No se pueden subir fotos: la sesión ya está cerrada (409)."
                        else -> "Error subiendo ZIP HTTP ${response.code()}: $detail"
                    }
                )
            }

            val body = response.body()
                ?: return ResultadoFotosZip.Error(
                    "El servidor respondió sin detalle al subir el ZIP."
                )

            val noEmparejadas = body.unmatchedFiles
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            /*
             * El backend devuelve estas referencias cuando sí existía photo_ref,
             * pero no terminó con archivo `photo` guardado. Por seguridad no se
             * mueven a uploaded y se reintentan después.
             */
            val checkpointsSinFoto = body.checkpointsWithoutPhoto
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            val candidatas = zip.fileNames
            val confirmadasPorNombre = candidatas -
                    noEmparejadas -
                    (checkpointsSinFoto intersect candidatas)

            /*
             * `matched` puede contar checkpoints, no solo archivos. Aun así, si
             * viene en cero no movemos nada: evita perder fotos ante una respuesta
             * incompleta del backend.
             */
            val confirmadas = if (body.matched > 0) {
                confirmadasPorNombre
            } else {
                emptySet()
            }

            val pendientesDeEstaVuelta = candidatas - confirmadas

            val movidas = if (confirmadas.isNotEmpty()) {
                PhytoMediaStorage.confirmarFotosSubidas(
                    context = context,
                    idHeader = idHeaderLocal,
                    fileNames = confirmadas
                )
            } else {
                0
            }

            if (confirmadas.isNotEmpty()) {
                actualizarRutasLocalesFotosConfirmadas(
                    idHeaderLocal = idHeaderLocal,
                    fileNames = confirmadas
                )
            }

            ResultadoFotosZip.Exito(
                movidas = movidas,
                noEmparejadas = noEmparejadas.toList(),
                checkpointsSinFoto = checkpointsSinFoto.toList(),
                pendientesSinCheckpoint = pendientesSinCheckpoint.toList(),
                pendientesSinConfirmar = (
                        pendientesSinConfirmar +
                                (pendientesDeEstaVuelta - noEmparejadas - checkpointsSinFoto)
                        ).distinct()
            )
        } catch (e: Exception) {
            ResultadoFotosZip.Error(
                "No se pudo enviar el ZIP de evidencias: ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            PhytoMediaStorage.eliminarZipTemporal(zip)
        }
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

    private fun normalizarUrlMedia(valor: String?): String? {
        val limpio = valor
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val base = ApiConfig.BASE_URL.trimEnd('/')

        return when {
            limpio.startsWith("http://localhost:8500") ->
                limpio.replace("http://localhost:8500", base)

            limpio.startsWith("http://127.0.0.1:8500") ->
                limpio.replace("http://127.0.0.1:8500", base)

            limpio.startsWith("/") -> "$base$limpio"
            limpio.startsWith("media/") -> "$base/$limpio"
            limpio.startsWith("phyto_checkpoints/") -> "$base/media/$limpio"
            else -> limpio
        }
    }

    private fun formatearIsoApi(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timeMillis))
    }

    private fun parseFechaApi(fecha: String?): Long? {
        return ApiDateParser.parsearMillis(fecha)
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
    val filasOmitidas: Int,
    val mensajesOmitidos: List<String>
)

private sealed class ResultadoImportacionCsv {
    data class Exito(val created: Int) : ResultadoImportacionCsv()
    data class Error(val mensaje: String) : ResultadoImportacionCsv()
}

private sealed class ResultadoFotosZip {
    data class Exito(
        val movidas: Int,
        val noEmparejadas: List<String> = emptyList(),
        val checkpointsSinFoto: List<String> = emptyList(),
        val pendientesSinCheckpoint: List<String> = emptyList(),
        val pendientesSinConfirmar: List<String> = emptyList()
    ) : ResultadoFotosZip()

    data class Error(val mensaje: String) : ResultadoFotosZip()
}

data class ResultadoDescargaCheckpoints(
    val cantidad: Int,
    val error: String? = null
) {
    val fueExitosa: Boolean
        get() = error == null
}

sealed class ResultadoCheckpointSync {
    data class Exito(
        val subidos: Int,
        val descargados: Int,
        val omitidos: Int,
        val mensaje: String,
        val fotosPendientes: Boolean = false
    ) : ResultadoCheckpointSync()

    data class Error(
        val mensaje: String
    ) : ResultadoCheckpointSync()
}

package com.example.myapplication.local.api.phytomonitoring

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
                "El monitoreo local no tiene ext_id del servidor; no se puede importar CSV."
            )

        val checkpointsLocales = database.localphytomonitoringcheckpointDao()
            .getCheckpointsByHeader(headerLocal.idHeader)

        val pendientes = checkpointsLocales.filter { checkpoint ->
            checkpoint.extId.isNullOrBlank() &&
                    !preferencias.getBoolean(claveSync(headerExtId, checkpoint.idCheckpoint), false)
        }

        if (pendientes.isEmpty()) {
            val descargados = descargarCheckpointsHeaderDesdeApi(headerLocal).cantidad
            return@withContext ResultadoCheckpointSync.Exito(
                subidos = 0,
                descargados = descargados,
                omitidos = 0,
                mensaje = "No había checkpoints nuevos por subir."
            )
        }

        val puntos = database.LocalPhytomonitoringTargetPointDao()
            .getTargetPointsByHeader(headerLocal.idHeader)
            .associateBy { it.idTargetPoint }

        val catalogo = database.localphytosanitarycatalogDao()
            .getAllCatalogo()
            .associateBy { it.idPhytosanitary }

        val csv = construirCsvImportacion(
            checkpoints = pendientes,
            puntos = puntos,
            catalogoPorIdLocal = catalogo
        )

        if (csv.filasValidas == 0) {
            val descargados = descargarCheckpointsHeaderDesdeApi(headerLocal).cantidad

            return@withContext ResultadoCheckpointSync.Exito(
                subidos = 0,
                descargados = descargados,
                omitidos = csv.filasOmitidas,
                mensaje = "No había capturas con phyto_issue_id para subir. Se conservaron locales y se descargó lo disponible desde API."
            )
        }

        val archivo = File.createTempFile(
            "phyto_checkpoints_${headerLocal.idHeader}_",
            ".csv",
            context.cacheDir
        )
        archivo.writeText(csv.contenido, Charsets.UTF_8)

        val headerBody = headerExtId.toRequestBody("text/plain".toMediaType())
        val fileBody = archivo.asRequestBody("text/csv".toMediaType())
        val csvPart = MultipartBody.Part.createFormData(
            name = "csv_file",
            filename = archivo.name,
            body = fileBody
        )

        val response = api.importarCheckpointsCsv(
            header = headerBody,
            csv_file = csvPart
        )

        archivo.delete()

        if (!response.isSuccessful) {
            // Aunque falle la subida, intentamos bajar lo que ya exista en servidor
            // para que Ver reporte no se quede en ceros.
            descargarCheckpointsHeaderDesdeApi(headerLocal)

            return@withContext ResultadoCheckpointSync.Error(
                "Error subiendo CSV: HTTP ${response.code()} ${response.errorBody()?.string().orEmpty()}"
            )
        }

        preferencias.edit().apply {
            pendientes.forEach { checkpoint ->
                putBoolean(claveSync(headerExtId, checkpoint.idCheckpoint), true)
            }
        }.apply()

        val descargados = descargarCheckpointsHeaderDesdeApi(headerLocal).cantidad

        ResultadoCheckpointSync.Exito(
            subidos = response.body()?.created ?: csv.filasValidas,
            descargados = descargados,
            omitidos = csv.filasOmitidas,
            mensaje = response.body()?.detail ?: "CSV sincronizado correctamente."
        )
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
            ?: return false

        val fitoLocal = catalogo.firstOrNull { fito ->
            fito.extId?.trim() == phytoExtId
        } ?: crearCatalogoFitoFallback(
            item = item,
            phytoExtId = phytoExtId,
            headerLocal = headerLocal
        )?.also { nuevoFito ->
            catalogo.add(nuevoFito)
        } ?: return false

        val capturedByExt = extraerIdFlexible(item.capturedBy)
            ?: extraerIdFlexible(item.capturedByUser)

        val usuarioLocal = capturedByExt?.let { ext ->
            runCatching { database.userDao().getUserByExtId(ext) }.getOrNull()
        }

        val qty = extraerEnteroFlexible(item.qty)
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
            val mismaCapturaLocal = database.localphytomonitoringcheckpointDao()
                .buscarCheckpointLocalMismaCaptura(
                    idHeader = nuevo.idHeader,
                    idTargetPoint = nuevo.idTargetPoint,
                    idPhytosanitary = nuevo.idPhytosanitary,
                    stage = nuevo.stage,
                    qty = nuevo.qty,
                    capturedAt = nuevo.capturedAt
                )

            if (mismaCapturaLocal != null && mismaCapturaLocal.extId.isNullOrBlank()) {
                database.localphytomonitoringcheckpointDao()
                    .updateCheckpoint(nuevo.copy(idCheckpoint = mismaCapturaLocal.idCheckpoint))
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
            qty >= 10 -> "critical"
            qty > 0 -> "warning"
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
                    if (primitive.isNumber) primitive.asInt else primitive.asString.toIntOrNull()
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

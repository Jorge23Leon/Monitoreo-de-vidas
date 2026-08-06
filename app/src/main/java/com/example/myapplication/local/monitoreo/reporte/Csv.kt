package com.example.myapplication.local.monitoreo.reporte

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.monitoreo.media.PhytoMediaStorage
import java.io.File
import java.io.OutputStreamWriter
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class ArchivoCsvReporteUi(
    val nombreArchivo: String,
    val ubicacionVisible: String,
    val uri: Uri
)

/**
 * Descarga un CSV técnico con los mismos datos que se mandan al endpoint
 * POST /api/v1/monitoring/phyto/checkpoints/create/
 */
internal fun descargarCsvReporteUi(
    context: Context,
    header: LocalPhytomonitoringHeaderEntity,
    productor: String,
    rancho: String,
    parcela: String,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    catalogo: List<LocalPhytosanitaryCatalogEntity>
): ArchivoCsvReporteUi {
    val nombreArchivo = crearNombreArchivoCsvReporteUi(
        productor = productor,
        rancho = rancho,
        parcela = parcela,
        fecha = System.currentTimeMillis()
    )

    val contenido = crearContenidoCsvCheckpointApiUi(
        context = context,
        header = header,
        checkpoints = checkpoints,
        puntos = puntos,
        catalogo = catalogo
    )

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            guardarCsvEnDescargasMediaStore(
                context = context,
                nombreArchivo = nombreArchivo,
                contenido = contenido
            )
        }.getOrElse {
            guardarCsvEnDescargasLegacy(
                context = context,
                nombreArchivo = nombreArchivo,
                contenido = contenido
            )
        }
    } else {
        guardarCsvEnDescargasLegacy(
            context = context,
            nombreArchivo = nombreArchivo,
            contenido = contenido
        )
    }
}

private fun crearNombreArchivoCsvReporteUi(
    productor: String,
    rancho: String,
    parcela: String,
    fecha: Long
): String {
    val fechaTexto = SimpleDateFormat(
        "dd_MM_yyyy",
        Locale("es", "MX")
    ).format(Date(fecha))

    val nombreBase = listOf(productor, rancho, parcela, fechaTexto)
        .joinToString("_") { limpiarParteNombreArchivoCsvUi(it) }
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "CHECKPOINTS_$fechaTexto" }

    return "${nombreBase}_CHECKPOINTS.csv"
}

private fun limpiarParteNombreArchivoCsvUi(
    valor: String
): String {
    val sinAcentos = Normalizer.normalize(
        valor.trim(),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "")

    return sinAcentos
        .uppercase(Locale("es", "MX"))
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
        .ifBlank { "SIN_DATO" }
}

/**
 * Refleja el contenido enviado al endpoint crearCheckpoint:
 *
 * header
 * target
 * phyto_issue
 * stage
 * presence_status
 * qty
 * geom.coordinates = [lon, lat]
 * notes
 * photo_ref
 * captured_at
 */
private fun crearContenidoCsvCheckpointApiUi(
    context: Context,
    header: LocalPhytomonitoringHeaderEntity,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    catalogo: List<LocalPhytosanitaryCatalogEntity>
): String {
    val headerExtId = header.extId
        ?.trim()
        .orEmpty()

    val puntosPorId = puntos.associateBy { it.idTargetPoint }
    val catalogoPorId = catalogo.associateBy { it.idPhytosanitary }

    return buildString {
        // UTF-8 para que Excel muestre acentos correctamente.
        append('\uFEFF')

        appendLine(
            "header,target,phyto_issue_id,stage,presence_status," +
                    "qty,geom_lon,geom_lat,notes,photo_ref,captured_at"
        )

        checkpoints
            .sortedWith(
                compareBy<LocalPhytomonitoringCheckpointEntity> {
                    it.capturedAt ?: 0L
                }.thenBy {
                    it.idCheckpoint
                }
            )
            .forEach { checkpoint ->
                val punto = puntosPorId[checkpoint.idTargetPoint]
                    ?: return@forEach

                val targetExtId = punto.extId
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@forEach

                val fito = checkpoint.idPhytosanitary?.let {
                    catalogoPorId[it]
                }

                val esSinPlaga = esCheckpointSinPlagaCsv(
                    fito = fito,
                    checkpoint = checkpoint
                )

                val esEnfermedad = esCheckpointEnfermedadCsv(fito)

                val enfermedadNoPresente = esEnfermedad &&
                        checkpoint.presenceStatus == 0

                val phytoIssue = if (esSinPlaga) {
                    ""
                } else {
                    fito?.extId
                        ?.trim()
                        ?.toIntOrNull()
                        ?.toString()
                        ?: return@forEach
                }

                val stage = if (esSinPlaga || enfermedadNoPresente) {
                    ""
                } else {
                    checkpoint.stage
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: return@forEach
                }

                val qty = if (esSinPlaga || enfermedadNoPresente) {
                    0
                } else {
                    checkpoint.qty ?: 0
                }

                val photoRef = obtenerPhotoRefCsv(
                    context = context,
                    checkpoint = checkpoint
                ).orEmpty()

                val capturedAt = checkpoint.capturedAt
                    ?: System.currentTimeMillis()

                appendLine(
                    listOf(
                        headerExtId,
                        targetExtId,
                        phytoIssue,
                        stage,
                        presenceStatusApiCsv(checkpoint),
                        qty.toString(),
                        punto.lon.toString(),
                        punto.lat.toString(),
                        checkpoint.notes?.takeIf { it.isNotBlank() }.orEmpty(),
                        photoRef,
                        formatearIsoApiCsv(capturedAt)
                    ).joinToString(",") { valor ->
                        escaparCsvUi(valor)
                    }
                )
            }
    }
}

private fun esCheckpointSinPlagaCsv(
    fito: LocalPhytosanitaryCatalogEntity?,
    checkpoint: LocalPhytomonitoringCheckpointEntity
): Boolean {
    val texto = listOf(
        fito?.name.orEmpty(),
        fito?.type.orEmpty(),
        fito?.description.orEmpty()
    ).joinToString(" ")
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

    val sinEtapa = stageLimpio.isBlank() ||
            stageLimpio == "-" ||
            stageLimpio == "sin etapa"

    return esCatalogoSinPlaga &&
            (checkpoint.qty ?: 0) <= 0 &&
            sinEtapa
}

private fun esCheckpointEnfermedadCsv(
    fito: LocalPhytosanitaryCatalogEntity?
): Boolean {
    return fito?.type
        ?.trim()
        ?.lowercase(Locale.getDefault())
        ?.contains("enfermedad") == true
}

private fun presenceStatusApiCsv(
    checkpoint: LocalPhytomonitoringCheckpointEntity
): String {
    val qty = checkpoint.qty ?: 0

    return when {
        checkpoint.presenceStatus == 0 -> "low"
        qty >= 10 -> "critical"
        qty > 0 -> "warning"
        checkpoint.presenceStatus == 1 -> "warning"
        else -> "low"
    }
}

private fun obtenerPhotoRefCsv(
    context: Context,
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

private fun formatearIsoApiCsv(
    timeMillis: Long
): String {
    return SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timeMillis))
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun guardarCsvEnDescargasMediaStore(
    context: Context,
    nombreArchivo: String,
    contenido: String
): ArchivoCsvReporteUi {
    val resolver = context.contentResolver

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            "${Environment.DIRECTORY_DOWNLOADS}/Monitoreos"
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection = MediaStore.Downloads.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY
    )

    val uri = resolver.insert(collection, values)
        ?: throw IllegalStateException(
            "No se pudo crear el archivo CSV en Descargas"
        )

    try {
        resolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write(contenido)
            }
        } ?: throw IllegalStateException(
            "No se pudo abrir el archivo CSV"
        )

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)

        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }

    return ArchivoCsvReporteUi(
        nombreArchivo = nombreArchivo,
        ubicacionVisible = "Descargas/Monitoreos/$nombreArchivo",
        uri = uri
    )
}

private fun guardarCsvEnDescargasLegacy(
    context: Context,
    nombreArchivo: String,
    contenido: String
): ArchivoCsvReporteUi {
    val resultado = try {
        val carpeta = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ),
            "Monitoreos"
        )

        if (!carpeta.exists()) {
            carpeta.mkdirs()
        }

        val archivo = File(carpeta, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)

        archivo to "Descargas/Monitoreos/$nombreArchivo"
    } catch (_: Exception) {
        val carpetaApp = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir,
            "Monitoreos"
        )

        if (!carpetaApp.exists()) {
            carpetaApp.mkdirs()
        }

        val archivo = File(carpetaApp, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)

        archivo to "Documentos de la app/Monitoreos/$nombreArchivo"
    }

    val archivo = resultado.first
    val ubicacion = resultado.second

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        archivo
    )

    return ArchivoCsvReporteUi(
        nombreArchivo = nombreArchivo,
        ubicacionVisible = ubicacion,
        uri = uri
    )
}

private fun escaparCsvUi(
    valor: String
): String {
    val limpio = valor.replace("\"", "\"\"")
    return "\"$limpio\""
}

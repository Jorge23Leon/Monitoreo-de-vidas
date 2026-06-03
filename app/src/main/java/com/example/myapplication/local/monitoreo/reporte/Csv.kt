package com.example.myapplication.local.monitoreo.reporte

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import java.io.File
import java.io.OutputStreamWriter
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun descargarCsvReporteUi(
    context: Context,
    header: LocalPhytomonitoringHeaderEntity,
    nombreCia: String,
    productor: String,
    rancho: String,
    parcela: String,
    cultivo: String,
    filas: List<FilaReporteCapturaUi>
): String {
    val nombreArchivo = crearNombreArchivoCsvReporteUi(
        productor = productor,
        rancho = rancho,
        parcela = parcela,
        fecha = System.currentTimeMillis()
    )

    val contenido = crearContenidoCsvReporteUi(
        header = header,
        nombreCia = nombreCia,
        productor = productor,
        rancho = rancho,
        parcela = parcela,
        cultivo = cultivo,
        filas = filas
    )

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        guardarCsvEnDescargasMediaStore(
            context = context,
            nombreArchivo = nombreArchivo,
            contenido = contenido
        )
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
    val fechaTexto = SimpleDateFormat("dd_MM_yyyy", Locale("es", "MX"))
        .format(Date(fecha))

    val nombreBase = listOf(productor, rancho, parcela, fechaTexto)
        .joinToString("_") { limpiarParteNombreArchivoCsvUi(it) }
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "REPORTE_MONITOREO_$fechaTexto" }

    return "$nombreBase.csv"
}

private fun limpiarParteNombreArchivoCsvUi(valor: String): String {
    val sinAcentos = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    return sinAcentos
        .uppercase(Locale("es", "MX"))
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
        .ifBlank { "SIN_DATO" }
}

private fun crearContenidoCsvReporteUi(
    header: LocalPhytomonitoringHeaderEntity,
    nombreCia: String,
    productor: String,
    rancho: String,
    parcela: String,
    cultivo: String,
    filas: List<FilaReporteCapturaUi>
): String {
    return buildString {
        append('\uFEFF')
        appendLine("sep=,")
        appendLine("Monitoreo  de plagas y enfermedades en parcelas")
        appendLine("field,value")

        appendLine(listOf("idHeader", header.idHeader.toString()).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("name", nombreCia).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("commercial_name", productor).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("ranch_name", rancho).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("plot_name", parcela).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("crop_name", cultivo).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("status", header.status).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("est_start_date", formatearFechaReporteUi(header.estStartDate)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("start_at", formatearFechaOpcionalReporteUi(header.startAt)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("finished_at", formatearFechaOpcionalReporteUi(header.finishedAt)).joinToString(",") { escaparCsvUi(it) })

        appendLine()
        appendLine("Monitoreos ")
        appendLine("point_number,lat,lon,name,type,stage,qty,captured_at,notes")

        filas.forEach { fila ->
            appendLine(
                listOf(
                    fila.numeroPunto.toString(),
                    fila.lat?.toString() ?: "",
                    fila.lon?.toString() ?: "",
                    fila.plagaEnfermedad,
                    fila.tipo,
                    fila.fase,
                    fila.cantidad.toString(),
                    fila.fechaCaptura,
                    fila.notas
                ).joinToString(",") { escaparCsvUi(it) }
            )
        }
    }
}

private fun guardarCsvEnDescargasMediaStore(
    context: Context,
    nombreArchivo: String,
    contenido: String
): String {
    val resolver = context.contentResolver

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Monitoreos")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values)
        ?: throw IllegalStateException("No se pudo crear el archivo CSV en Descargas")

    try {
        resolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write(contenido)
            }
        } ?: throw IllegalStateException("No se pudo abrir el archivo CSV")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }

    return "Descargas/Monitoreos/$nombreArchivo"
}

private fun guardarCsvEnDescargasLegacy(
    context: Context,
    nombreArchivo: String,
    contenido: String
): String {
    return try {
        val carpeta = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Monitoreos"
        )
        if (!carpeta.exists()) carpeta.mkdirs()

        val archivo = File(carpeta, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)
        archivo.absolutePath
    } catch (_: Exception) {
        val carpetaApp = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
            "Monitoreos"
        )
        if (!carpetaApp.exists()) carpetaApp.mkdirs()

        val archivo = File(carpetaApp, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)
        archivo.absolutePath
    }
}

private fun escaparCsvUi(valor: String): String {
    val limpio = valor.replace("\"", "\"\"")
    return "\"$limpio\""
}
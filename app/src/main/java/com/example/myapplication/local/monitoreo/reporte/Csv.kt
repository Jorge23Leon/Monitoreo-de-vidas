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
    val nombreArchivo = "reporte_monitoreo_${header.idHeader}_${System.currentTimeMillis()}.csv"
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
        appendLine("REPORTE DE MONITOREO FITOSANITARIO")
        appendLine("Campo,Valor")
        appendLine(listOf("ID Monitoreo", header.idHeader.toString()).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("CIA", nombreCia).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Productor", productor).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Rancho", rancho).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Parcela", parcela).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Cultivo", cultivo).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Estado", textoEstadoReporteUi(header.status)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Fecha programada", formatearFechaReporteUi(header.estStartDate)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Inicio real", formatearFechaOpcionalReporteUi(header.startAt)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Finalizado", formatearFechaOpcionalReporteUi(header.finishedAt)).joinToString(",") { escaparCsvUi(it) })

        appendLine()
        appendLine("Punto,Latitud,Longitud,Coordenadas,Plaga o Enfermedad,Tipo,Fase,Cantidad,Fecha de captura,Notas")

        filas.forEach { fila ->
            appendLine(
                listOf(
                    fila.numeroPunto.toString(),
                    fila.lat?.toString() ?: "",
                    fila.lon?.toString() ?: "",
                    fila.coordenadas,
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
        val carpeta = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!carpeta.exists()) carpeta.mkdirs()

        val archivo = File(carpeta, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)
        archivo.absolutePath
    } catch (_: Exception) {
        val carpetaApp = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
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
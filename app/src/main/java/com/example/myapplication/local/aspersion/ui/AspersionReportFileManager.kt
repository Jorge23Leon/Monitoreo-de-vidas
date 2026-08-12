package com.example.myapplication.local.aspersion.report

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

object AspersionReportFileManager {

    private const val CHANNEL_ID = "reportes_aspersion"
    private const val CHANNEL_NAME = "Reportes de aspersión"

    fun saveForViewing(
        context: Context,
        bytes: ByteArray,
        fileName: String
    ): Uri {
        require(bytes.isNotEmpty()) {
            "El PDF está vacío."
        }

        val safeName = normalizePdfFileName(fileName)

        val directory = File(
            context.cacheDir,
            "aspersion_reports"
        ).apply {
            mkdirs()
        }

        val pdfFile = File(directory, safeName)
        pdfFile.writeBytes(bytes)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
    }

    fun saveToDownloads(
        context: Context,
        bytes: ByteArray,
        fileName: String
    ): Uri {
        require(bytes.isNotEmpty()) {
            "El PDF está vacío."
        }

        val resolver = context.contentResolver
        val safeName = normalizePdfFileName(fileName)

        val values = ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                safeName
            )
            put(
                MediaStore.Downloads.MIME_TYPE,
                "application/pdf"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "Download/Reportes CIAgro"
                )
                put(
                    MediaStore.Downloads.IS_PENDING,
                    1
                )
            }
        }

        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error(
            "No se pudo crear el PDF en la carpeta Descargas."
        )

        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error(
                "No se pudo abrir el archivo PDF para escritura."
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completedValues = ContentValues().apply {
                    put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                    )
                }

                resolver.update(
                    uri,
                    completedValues,
                    null,
                    null
                )
            }

            showDownloadNotification(
                context = context,
                pdfUri = uri,
                fileName = safeName
            )

            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun openReadOnlyPdf(
        context: Context,
        pdfUri: Uri
    ) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            clipData = ClipData.newRawUri(
                "Reporte de aspersión",
                pdfUri
            )
        }

        val chooserIntent = Intent.createChooser(
            viewIntent,
            "Abrir reporte PDF con"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(chooserIntent)
        } catch (error: android.content.ActivityNotFoundException) {
            throw IllegalStateException(
                "No hay una aplicación instalada para abrir archivos PDF.",
                error
            )
        }
    }

    private fun showDownloadNotification(
        context: Context,
        pdfUri: Uri,
        fileName: String
    ) {
        createNotificationChannel(context)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                pdfUri,
                "application/pdf"
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            clipData = ClipData.newRawUri(
                "Reporte descargado",
                pdfUri
            )
        }

        val chooserIntent = Intent.createChooser(
            viewIntent,
            "Abrir reporte PDF con"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            pdfUri.toString().hashCode(),
            chooserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.stat_sys_download_done
            )
            .setContentTitle(
                "Reporte descargado"
            )
            .setContentText(
                "$fileName guardado en Descargas"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$fileName se guardó en " +
                            "Descargas/Reportes CIAgro. " +
                            "Toca para abrirlo con otra aplicación."
                )
            )
            .setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(
                NotificationCompat.CATEGORY_STATUS
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Abrir PDF",
                pendingIntent
            )
            .build()

        NotificationManagerCompat
            .from(context)
            .notify(
                pdfUri.toString().hashCode(),
                notification
            )
    }

    private fun createNotificationChannel(
        context: Context
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                "Avisos de descarga de reportes PDF de aspersión."
        }

        context
            .getSystemService(
                NotificationManager::class.java
            )
            .createNotificationChannel(channel)
    }

    private fun normalizePdfFileName(
        fileName: String
    ): String {
        val cleanName = fileName
            .trim()
            .ifBlank {
                "reporte-aspersion.pdf"
            }
            .replace(
                Regex("""[\\/:*?"<>|]"""),
                "-"
            )

        return if (
            cleanName.endsWith(
                ".pdf",
                ignoreCase = true
            )
        ) {
            cleanName
        } else {
            "$cleanName.pdf"
        }
    }
}
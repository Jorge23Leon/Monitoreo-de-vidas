package com.example.monitoreodeplagas

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.OutputStreamWriter

class MainActivity : FlutterActivity() {
    companion object {
        private const val DOWNLOADS_CHANNEL = "ciagro/downloads"
        private const val NOTIFICATION_CHANNEL_ID = "ciagro_downloads"
        private const val NOTIFICATION_PERMISSION_REQUEST = 7016
    }

    private var pendingNotification: Pair<String, String>? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            DOWNLOADS_CHANNEL
        ).setMethodCallHandler { call, result ->
            if (call.method != "saveCsv") {
                result.notImplemented()
                return@setMethodCallHandler
            }

            val fileName = call.argument<String>("fileName")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "reporte_monitoreo.csv"
            val content = call.argument<String>("content") ?: ""

            try {
                val location = saveCsvToDownloads(fileName, content)
                notifyDownload(fileName, location)
                result.success(
                    mapOf(
                        "fileName" to fileName,
                        "location" to location
                    )
                )
            } catch (error: Exception) {
                result.error(
                    "CSV_SAVE_ERROR",
                    error.message ?: "No se pudo guardar el CSV.",
                    null
                )
            }
        }
    }

    private fun saveCsvToDownloads(fileName: String, content: String): String {
        val safeName = fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .let { if (it.lowercase().endsWith(".csv")) it else "$it.csv" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
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
                ?: error("Android no pudo crear el archivo en Descargas.")

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write(content)
                        writer.flush()
                    }
                } ?: error("No se pudo abrir el archivo CSV para escritura.")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }

            return "Descargas/Monitoreos/$safeName"
        }

        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val folder = File(downloads, "Monitoreos").apply { mkdirs() }
        val file = File(folder, safeName)
        file.writeText(content, Charsets.UTF_8)
        return "Descargas/Monitoreos/$safeName"
    }

    private fun notifyDownload(fileName: String, location: String) {
        createNotificationChannel()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotification = fileName to location
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
            Toast.makeText(
                this,
                "CSV descargado en $location",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        postDownloadNotification(fileName, location)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Descargas de CIAGRO",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos cuando un reporte CSV termina de descargarse"
            }
        )
    }

    private fun postDownloadNotification(fileName: String, location: String) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Reporte CSV descargado")
            .setContentText(fileName)
            .setStyle(
                Notification.BigTextStyle()
                    .bigText("$fileName se guardó correctamente en $location")
            )
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
            notification
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return

        val pending = pendingNotification
        pendingNotification = null
        if (pending == null) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            postDownloadNotification(pending.first, pending.second)
        } else {
            Toast.makeText(
                this,
                "CSV descargado en ${pending.second}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

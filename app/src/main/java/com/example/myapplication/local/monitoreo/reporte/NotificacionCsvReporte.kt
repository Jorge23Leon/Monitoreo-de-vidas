package com.example.myapplication.local.monitoreo.reporte

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * CAMBIO CSV:
 * La notificación abre el CSV exacto que acaba de descargarse.
 */
internal object NotificacionCsvReporte {

    private const val CANAL_ID = "descargas_reportes_csv"
    private const val CANAL_NOMBRE = "Descargas de reportes"
    private const val NOTIFICACION_ID = 8301

    fun tienePermisoNotificaciones(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun mostrar(
        context: Context,
        archivo: ArchivoCsvReporteUi
    ) {
        if (!tienePermisoNotificaciones(context)) return

        crearCanalSiHaceFalta(context)

        val intentAbrirCsv = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(archivo.uri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(archivo.nombreArchivo, archivo.uri)
        }

        val chooser = Intent.createChooser(
            intentAbrirCsv,
            "Abrir archivo CSV"
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            archivo.uri.toString().hashCode(),
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CANAL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notificacion = builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("CSV descargado correctamente")
            .setContentText("Toca para abrir: ${archivo.nombreArchivo}")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "Archivo: ${archivo.nombreArchivo}\n" +
                            "Ubicación: ${archivo.ubicacionVisible}"
                )
            )
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Abrir CSV",
                pendingIntent
            )
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationManager.notify(NOTIFICACION_ID, notificacion)
    }

    private fun crearCanalSiHaceFalta(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val canal = NotificationChannel(
            CANAL_ID,
            CANAL_NOMBRE,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos al descargar reportes CSV."
        }

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationManager.createNotificationChannel(canal)
    }
}

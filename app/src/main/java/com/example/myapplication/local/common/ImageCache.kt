package com.example.myapplication.local.common

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.myapplication.local.api.core.ApiConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ImageCache {

    fun cargarBitmap(context: Context, photo: String?): ImageBitmap? {
        val photoFinal = normalizarPhotoUrl(photo ?: return null)

        return try {
            when {
                photoFinal.startsWith("http://") || photoFinal.startsWith("https://") -> {
                    val archivoCache = archivoCacheParaUrl(context, photoFinal)

                    if (archivoCache.exists() && archivoCache.length() > 0L) {
                        BitmapFactory.decodeFile(archivoCache.absolutePath)?.asImageBitmap()
                    } else {
                        descargarImagen(context, photoFinal)
                        if (archivoCache.exists() && archivoCache.length() > 0L) {
                            BitmapFactory.decodeFile(archivoCache.absolutePath)?.asImageBitmap()
                        } else {
                            null
                        }
                    }
                }

                photoFinal.startsWith("file://") -> {
                    BitmapFactory.decodeFile(Uri.parse(photoFinal).path)?.asImageBitmap()
                }

                else -> {
                    context.contentResolver.openInputStream(Uri.parse(photoFinal)).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun guardarEnCache(context: Context, photo: String?): File? {
        val photoFinal = normalizarPhotoUrl(photo ?: return null)

        if (!photoFinal.startsWith("http://") && !photoFinal.startsWith("https://")) {
            return null
        }

        val archivoCache = archivoCacheParaUrl(context, photoFinal)

        if (archivoCache.exists() && archivoCache.length() > 0L) {
            return archivoCache
        }

        return descargarImagen(context, photoFinal)
    }

    private fun descargarImagen(context: Context, url: String): File? {
        val archivoCache = archivoCacheParaUrl(context, url)
        archivoCache.parentFile?.mkdirs()

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }

            connection.inputStream.use { input ->
                archivoCache.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            connection.disconnect()

            if (archivoCache.exists() && archivoCache.length() > 0L) {
                archivoCache
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun archivoCacheParaUrl(context: Context, url: String): File {
        val nombre = sha256(url) + extensionSegura(url)
        return File(File(context.filesDir, "offline_images"), nombre)
    }

    private fun extensionSegura(url: String): String {
        val limpia = url.substringBefore("?").lowercase()
        return when {
            limpia.endsWith(".jpg") -> ".jpg"
            limpia.endsWith(".jpeg") -> ".jpeg"
            limpia.endsWith(".png") -> ".png"
            limpia.endsWith(".webp") -> ".webp"
            else -> ".img"
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun normalizarPhotoUrl(photo: String): String {
        val clean = photo.trim().trim('"')
        val base = ApiConfig.BASE_URL.trimEnd('/')

        return when {
            clean.startsWith("http://localhost:8500") ->
                clean.replace("http://localhost:8500", base)

            clean.startsWith("http://127.0.0.1:8500") ->
                clean.replace("http://127.0.0.1:8500", base)

            clean.startsWith("/") ->
                "$base$clean"

            clean.startsWith("media/") ->
                "$base/$clean"

            else -> clean
        }
    }
}

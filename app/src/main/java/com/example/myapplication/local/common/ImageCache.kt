package com.example.myapplication.local.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.myapplication.local.api.core.ApiConfig
import com.example.myapplication.local.api.core.TokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Descarga, valida y conserva las imágenes de catálogo en el almacenamiento privado
 * de la app. Las fotos se descargan con JWT porque algunos servidores protegen
 * también las rutas de media/attachments.
 */
object ImageCache {

    private const val TAG = "IMAGE_CACHE"
    private const val NOMBRE_CARPETA = "catalogo_imagenes"

    suspend fun cargarBitmap(
        context: Context,
        photo: String?
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val ruta = resolverParaPersistir(context.applicationContext, photo)
            ?: return@withContext null

        val bitmap = cargarBitmapDesdeRuta(
            context = context.applicationContext,
            ruta = ruta
        )

        bitmap?.asImageBitmap()
    }

    /**
     * Resuelve una URL/ruta a una ruta lista para guardar en Room:
     * - URL remota descargada correctamente -> ruta absoluta del archivo local.
     * - URL remota que no pudo descargarse -> conserva la URL para reintentar.
     * - URI local/content/file -> se conserva tal cual.
     */
    suspend fun resolverParaPersistir(
        context: Context,
        photo: String?
    ): String? = withContext(Dispatchers.IO) {
        val normalizada = normalizarPhotoUrl(context.applicationContext, photo)
            ?: return@withContext null

        if (esUrlRemota(normalizada)) {
            guardarEnCache(context.applicationContext, normalizada)?.absolutePath
                ?: normalizada
        } else {
            normalizada
        }
    }

    suspend fun guardarEnCache(
        context: Context,
        photo: String?
    ): File? = withContext(Dispatchers.IO) {
        val ruta = normalizarPhotoUrl(context.applicationContext, photo)
            ?: return@withContext null

        if (!esUrlRemota(ruta)) {
            return@withContext archivoLocalExistente(context.applicationContext, ruta)
        }

        val carpeta = File(context.filesDir, NOMBRE_CARPETA).apply { mkdirs() }
        val destino = File(carpeta, "${sha256(ruta)}.img")

        if (archivoEsImagenValida(destino)) {
            return@withContext destino
        }

        destino.delete()
        val temporal = File(carpeta, "${destino.name}.part")
        temporal.delete()

        var conexion: HttpURLConnection? = null

        try {
            conexion = (URL(ruta).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                )
                setRequestProperty("User-Agent", "CIAgro-Android/1.0")

                /*
                 * Cuando la imagen llega desde /api/v1/core/attachments/ o un
                 * media protegido, sin este token el backend responde 401/403.
                 */
                TokenStorage(context.applicationContext)
                    .obtenerAccessToken()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token ->
                        setRequestProperty("Authorization", "Bearer $token")
                    }
            }

            val codigo = conexion.responseCode

            if (codigo !in 200..299) {
                Log.w(TAG, "HTTP_$codigo al descargar imagen: $ruta")
                return@withContext null
            }

            val contentType = conexion.contentType
                ?.substringBefore(";")
                ?.trim()
                ?.lowercase()

            conexion.inputStream.use { entrada ->
                FileOutputStream(temporal).use { salida ->
                    entrada.copyTo(salida)
                    salida.fd.sync()
                }
            }

            /*
             * Aunque el servidor responda 200, puede mandar HTML de login/error.
             * BitmapFactory evita guardar esa respuesta como si fuera una imagen.
             */
            if (!archivoEsImagenValida(temporal)) {
                Log.w(
                    TAG,
                    "RESPUESTA_NO_IMAGEN contentType=${contentType ?: "-"} url=$ruta"
                )
                temporal.delete()
                return@withContext null
            }

            if (!temporal.renameTo(destino)) {
                temporal.copyTo(destino, overwrite = true)
                temporal.delete()
            }

            Log.d(TAG, "IMAGEN_CACHEADA ${destino.name} <- $ruta")
            destino
        } catch (e: Exception) {
            Log.w(TAG, "ERROR_DESCARGANDO_IMAGEN $ruta: ${e.message}", e)
            temporal.delete()
            null
        } finally {
            conexion?.disconnect()
        }
    }

    fun esUrlRemota(valor: String?): Boolean {
        val texto = valor?.trim()?.lowercase().orEmpty()
        return texto.startsWith("http://") || texto.startsWith("https://")
    }

    private fun cargarBitmapDesdeRuta(
        context: Context,
        ruta: String
    ): Bitmap? {
        return try {
            when {
                ruta.startsWith("content://", ignoreCase = true) -> {
                    context.contentResolver.openInputStream(Uri.parse(ruta))
                        ?.use { BitmapFactory.decodeStream(it) }
                }

                ruta.startsWith("file://", ignoreCase = true) -> {
                    val path = Uri.parse(ruta).path ?: return null
                    BitmapFactory.decodeFile(path)
                }

                esRutaArchivoAbsoluta(ruta) -> {
                    BitmapFactory.decodeFile(ruta)
                }

                else -> {
                    /*
                     * Una URL remota que no se logró cachear no se decodifica
                     * como URI: se volverá a intentar al siguiente render/sync.
                     */
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo decodificar imagen local: $ruta", e)
            null
        }
    }

    private fun archivoLocalExistente(
        context: Context,
        ruta: String
    ): File? {
        return when {
            ruta.startsWith("file://", ignoreCase = true) -> {
                Uri.parse(ruta).path
                    ?.let(::File)
                    ?.takeIf(::archivoEsImagenValida)
            }

            esRutaArchivoAbsoluta(ruta) -> {
                File(ruta).takeIf(::archivoEsImagenValida)
            }

            else -> null
        }
    }

    private fun archivoEsImagenValida(archivo: File): Boolean {
        if (!archivo.exists() || archivo.length() <= 0L) return false

        return runCatching {
            BitmapFactory.decodeFile(archivo.absolutePath) != null
        }.getOrDefault(false)
    }

    /**
     * Convierte rutas relativas y hosts temporales del backend al BASE_URL actual.
     * Ejemplos:
     * - /media/x.jpg                       -> https://tunel-actual/media/x.jpg
     * - http://ciagro-web:8500/media/x.jpg -> https://tunel-actual/media/x.jpg
     * - https://tunel-viejo.trycloudflare.com/media/x.jpg -> tunel actual
     */
    private fun normalizarPhotoUrl(
        context: Context,
        photo: String?
    ): String? {
        val clean = photo
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (
            clean.startsWith("content://", ignoreCase = true) ||
            clean.startsWith("file://", ignoreCase = true) ||
            esRutaArchivoAbsoluta(clean)
        ) {
            return clean
        }

        val base = baseActual()

        if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
        ) {
            val uri = Uri.parse(clean)
            val host = uri.host.orEmpty().lowercase()

            return if (debeReemplazarHost(host)) {
                construirConBaseActual(base, uri)
            } else {
                clean
            }
        }

        return when {
            clean.startsWith("/") -> "$base$clean"
            else -> "$base/$clean"
        }
    }

    private fun baseActual(): String {
        return ApiConfig.BASE_URL
            .trim()
            .trimEnd('/')
    }

    private fun construirConBaseActual(
        base: String,
        uri: Uri
    ): String {
        val path = uri.encodedPath
            ?.takeIf { it.isNotBlank() }
            ?: "/"

        val query = uri.encodedQuery
            ?.takeIf { it.isNotBlank() }
            ?.let { "?$it" }
            .orEmpty()

        return "$base$path$query"
    }

    private fun debeReemplazarHost(host: String): Boolean {
        if (host.isBlank()) return true

        if (
            host == "localhost" ||
            host == "0.0.0.0" ||
            host == "ciagro-web" ||
            host == "web" ||
            host.endsWith(".trycloudflare.com")
        ) {
            return true
        }

        if (
            host.startsWith("127.") ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("172.16.") ||
            host.startsWith("172.17.") ||
            host.startsWith("172.18.") ||
            host.startsWith("172.19.") ||
            host.startsWith("172.20.") ||
            host.startsWith("172.21.") ||
            host.startsWith("172.22.") ||
            host.startsWith("172.23.") ||
            host.startsWith("172.24.") ||
            host.startsWith("172.25.") ||
            host.startsWith("172.26.") ||
            host.startsWith("172.27.") ||
            host.startsWith("172.28.") ||
            host.startsWith("172.29.") ||
            host.startsWith("172.30.") ||
            host.startsWith("172.31.")
        ) {
            return true
        }

        return false
    }

    private fun esRutaArchivoAbsoluta(valor: String): Boolean {
        return valor.startsWith("/data/") ||
                valor.startsWith("/storage/") ||
                valor.startsWith("/sdcard/")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}

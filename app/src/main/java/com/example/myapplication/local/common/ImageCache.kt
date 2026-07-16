package com.example.myapplication.local.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.myapplication.local.api.core.ApiConfig
import com.example.myapplication.local.api.core.RetrofitClient
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Descarga, valida y conserva imágenes de catálogos en el almacenamiento privado.
 *
 * Las descargas usan el mismo OkHttp autenticado que Retrofit. Por eso incluyen
 * JWT y pueden renovar automáticamente el access token ante una respuesta 401.
 * También resuelve endpoints de attachments que primero devuelven JSON y después
 * indican la URL real del archivo.
 */
object ImageCache {

    private const val TAG = "IMAGE_CACHE"
    private const val NOMBRE_CARPETA = "catalogo_imagenes"
    private const val MAX_REDIRECCIONES_JSON = 4
    private const val MAX_JSON_BYTES = 2L * 1024L * 1024L

    @Volatile
    private var clienteAutenticado: OkHttpClient? = null

    private fun obtenerCliente(context: Context): OkHttpClient {
        return clienteAutenticado ?: synchronized(this) {
            clienteAutenticado ?: RetrofitClient
                .crearClienteImagenesAutenticado(context.applicationContext)
                .also { clienteAutenticado = it }
        }
    }

    suspend fun cargarBitmap(
        context: Context,
        photo: String?
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val ruta = resolverParaPersistir(
            context = context.applicationContext,
            photo = photo
        ) ?: return@withContext null

        cargarBitmapDesdeRuta(
            context = context.applicationContext,
            ruta = ruta
        )?.asImageBitmap()
    }

    /**
     * Devuelve una ruta utilizable por la interfaz:
     * - si la imagen remota se descargó, devuelve la ruta absoluta cacheada;
     * - si no se pudo descargar, conserva la URL para poder reintentar después;
     * - si ya es una URI/ruta local válida, la conserva.
     *
     * Esta función no debe utilizarse para reemplazar permanentemente la URL
     * remota guardada en Room. Room debe conservar la URL original.
     */
    suspend fun resolverParaPersistir(
        context: Context,
        photo: String?
    ): String? = withContext(Dispatchers.IO) {
        val normalizada = normalizarPhotoUrl(photo)
            ?: return@withContext null

        if (esUrlRemota(normalizada)) {
            guardarEnCache(
                context = context.applicationContext,
                photo = normalizada,
                forceRefresh = false
            )?.absolutePath ?: normalizada
        } else {
            normalizada
        }
    }

    /**
     * Descarga una imagen y la deja en cache. Si la URL devuelve JSON, busca una
     * URL real dentro de campos como file_url, download_url, file, image o photo.
     */
    suspend fun guardarEnCache(
        context: Context,
        photo: String?,
        forceRefresh: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val rutaInicial = normalizarPhotoUrl(photo)
            ?: return@withContext null

        if (!esUrlRemota(rutaInicial)) {
            return@withContext archivoLocalExistente(rutaInicial)
        }

        val carpeta = File(appContext.filesDir, NOMBRE_CARPETA).apply { mkdirs() }
        val destino = File(carpeta, "${sha256(rutaInicial)}.img")

        if (!forceRefresh && archivoEsImagenValida(destino)) {
            return@withContext destino
        }

        destino.delete()
        val temporal = File(carpeta, "${destino.name}.part")
        temporal.delete()

        val cliente = obtenerCliente(appContext)
        val visitadas = linkedSetOf<String>()

        try {
            val descargada = descargarImagenRecursiva(
                context = appContext,
                cliente = cliente,
                url = rutaInicial,
                temporal = temporal,
                visitadas = visitadas,
                profundidad = 0
            )

            if (!descargada || !archivoEsImagenValida(temporal)) {
                temporal.delete()
                return@withContext null
            }

            if (!temporal.renameTo(destino)) {
                temporal.copyTo(destino, overwrite = true)
                temporal.delete()
            }

            Log.d(TAG, "IMAGEN_CACHEADA ${destino.name} <- $rutaInicial")
            destino
        } catch (e: Exception) {
            Log.w(
                TAG,
                "ERROR_DESCARGANDO_IMAGEN $rutaInicial: ${e.message}",
                e
            )
            temporal.delete()
            null
        }
    }

    private fun descargarImagenRecursiva(
        context: Context,
        cliente: OkHttpClient,
        url: String,
        temporal: File,
        visitadas: MutableSet<String>,
        profundidad: Int
    ): Boolean {
        if (profundidad > MAX_REDIRECCIONES_JSON) {
            Log.w(TAG, "DEMASIADAS_REDIRECCIONES_JSON url=$url")
            return false
        }

        val normalizada = normalizarPhotoUrl(url) ?: return false

        if (!visitadas.add(normalizada)) {
            Log.w(TAG, "CICLO_URL_IMAGEN url=$normalizada")
            return false
        }

        val request = Request.Builder()
            .url(normalizada)
            .get()
            .header(
                "Accept",
                "image/avif,image/webp,image/apng,image/jpeg,image/png,image/*," +
                        "application/json,*/*;q=0.8"
            )
            .header("User-Agent", "CIAgro-Android/1.0")
            .build()

        return cliente.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(
                    TAG,
                    "HTTP_${response.code} al descargar imagen: $normalizada"
                )
                return@use false
            }

            procesarRespuesta(
                context = context,
                cliente = cliente,
                response = response,
                urlActual = normalizada,
                temporal = temporal,
                visitadas = visitadas,
                profundidad = profundidad
            )
        }
    }

    private fun procesarRespuesta(
        context: Context,
        cliente: OkHttpClient,
        response: Response,
        urlActual: String,
        temporal: File,
        visitadas: MutableSet<String>,
        profundidad: Int
    ): Boolean {
        val body = response.body ?: run {
            Log.w(TAG, "RESPUESTA_SIN_BODY url=$urlActual")
            return false
        }

        val contentType = body.contentType()
            ?.toString()
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (esContenidoJson(contentType)) {
            val json = runCatching { body.string() }
                .getOrElse { error ->
                    Log.w(TAG, "JSON_ATTACHMENT_ILEGIBLE url=$urlActual", error)
                    return false
                }

            return resolverJsonYDescargar(
                context = context,
                cliente = cliente,
                json = json,
                urlActual = urlActual,
                temporal = temporal,
                visitadas = visitadas,
                profundidad = profundidad
            )
        }

        temporal.delete()
        body.byteStream().use { entrada ->
            FileOutputStream(temporal).use { salida ->
                entrada.copyTo(salida)
                salida.fd.sync()
            }
        }

        if (archivoEsImagenValida(temporal)) {
            return true
        }

        /*
         * Algunos servidores envían JSON con content-type text/plain o
         * application/octet-stream. Si el archivo descargado no es imagen,
         * intentamos interpretarlo como JSON antes de descartarlo.
         */
        val jsonAlternativo = temporal
            .takeIf { it.exists() && it.length() in 1..MAX_JSON_BYTES }
            ?.let { archivo ->
                runCatching { archivo.readText(Charsets.UTF_8) }.getOrNull()
            }
            ?.trim()
            ?.takeIf { texto ->
                texto.startsWith("{") || texto.startsWith("[")
            }

        temporal.delete()

        if (jsonAlternativo != null) {
            return resolverJsonYDescargar(
                context = context,
                cliente = cliente,
                json = jsonAlternativo,
                urlActual = urlActual,
                temporal = temporal,
                visitadas = visitadas,
                profundidad = profundidad
            )
        }

        Log.w(
            TAG,
            "RESPUESTA_NO_IMAGEN contentType=${contentType.ifBlank { "-" }} " +
                    "url=$urlActual"
        )
        return false
    }

    private fun resolverJsonYDescargar(
        context: Context,
        cliente: OkHttpClient,
        json: String,
        urlActual: String,
        temporal: File,
        visitadas: MutableSet<String>,
        profundidad: Int
    ): Boolean {
        val root = runCatching { JsonParser.parseString(json) }
            .getOrElse { error ->
                Log.w(TAG, "JSON_ATTACHMENT_INVALIDO url=$urlActual", error)
                return false
            }

        val candidata = extraerUrlImagenDesdeJson(root)
            ?.let(::normalizarPhotoUrl)
            ?.takeIf { it != urlActual }

        if (candidata.isNullOrBlank()) {
            Log.w(
                TAG,
                "JSON_SIN_URL_IMAGEN url=$urlActual json=${json.take(300)}"
            )
            return false
        }

        Log.d(TAG, "ATTACHMENT_RESUELTO $urlActual -> $candidata")

        return descargarImagenRecursiva(
            context = context,
            cliente = cliente,
            url = candidata,
            temporal = temporal,
            visitadas = visitadas,
            profundidad = profundidad + 1
        )
    }

    private fun extraerUrlImagenDesdeJson(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        return when {
            element.isJsonPrimitive -> {
                runCatching { element.asString }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf(::pareceUrlOPathDescargable)
            }

            element.isJsonArray -> {
                element.asJsonArray.firstNotNullOfOrNull { child ->
                    extraerUrlImagenDesdeJson(child)
                }
            }

            element.isJsonObject -> {
                val objeto = element.asJsonObject
                val camposPrioritarios = listOf(
                    "download_url",
                    "file_url",
                    "image_url",
                    "photo_url",
                    "absolute_url",
                    "content_url",
                    "original_url",
                    "source_url",
                    "thumbnail_url",
                    "attachment_url",
                    "download",
                    "file",
                    "image",
                    "photo",
                    "thumbnail",
                    "path",
                    "url",
                    "href"
                )

                camposPrioritarios.firstNotNullOfOrNull { campo ->
                    objeto.get(campo)?.let(::extraerUrlImagenDesdeJson)
                } ?: objeto.entrySet().firstNotNullOfOrNull { entry ->
                    extraerUrlImagenDesdeJson(entry.value)
                }
            }

            else -> null
        }
    }

    private fun pareceUrlOPathDescargable(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val text = value.trim().lowercase()

        return text.startsWith("http://") ||
                text.startsWith("https://") ||
                text.startsWith("/media/") ||
                text.startsWith("media/") ||
                text.startsWith("/uploads/") ||
                text.startsWith("uploads/") ||
                text.startsWith("/files/") ||
                text.startsWith("files/") ||
                text.startsWith("/api/v1/core/attachments/") ||
                text.startsWith("api/v1/core/attachments/") ||
                text.endsWith(".jpg") ||
                text.endsWith(".jpeg") ||
                text.endsWith(".png") ||
                text.endsWith(".webp") ||
                text.endsWith(".gif") ||
                text.endsWith(".avif")
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

                esRutaArchivoAbsoluta(ruta) -> BitmapFactory.decodeFile(ruta)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo decodificar imagen local: $ruta", e)
            null
        }
    }

    private fun archivoLocalExistente(ruta: String): File? {
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

    private fun esContenidoJson(contentType: String): Boolean {
        return contentType.contains("application/json") ||
                contentType.contains("text/json") ||
                contentType.contains("application/problem+json") ||
                contentType.endsWith("+json")
    }

    /**
     * Convierte rutas relativas y hosts internos/temporales al BASE_URL actual.
     */
    private fun normalizarPhotoUrl(photo: String?): String? {
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

        if (
            clean.startsWith("http://", ignoreCase = true) ||
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
        return ApiConfig.BASE_URL.trim().trimEnd('/')
    }

    private fun construirConBaseActual(base: String, uri: Uri): String {
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
            host == "host.docker.internal" ||
            host.endsWith(".trycloudflare.com")
        ) {
            return true
        }

        if (
            host.startsWith("127.") ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            (host.startsWith("172.") && esHostPrivado172(host))
        ) {
            return true
        }

        return false
    }

    private fun esHostPrivado172(host: String): Boolean {
        val segundoOcteto = host
            .substringAfter("172.", missingDelimiterValue = "")
            .substringBefore('.')
            .toIntOrNull()
            ?: return false

        return segundoOcteto in 16..31
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

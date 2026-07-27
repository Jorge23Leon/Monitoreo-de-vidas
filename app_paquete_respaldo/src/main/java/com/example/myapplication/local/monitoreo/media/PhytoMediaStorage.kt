package com.example.myapplication.local.monitoreo.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.os.Build
import androidx.core.content.FileProvider
import com.example.myapplication.local.api.core.ApiConfig
import com.example.myapplication.local.api.core.RetrofitClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

object PhytoMediaStorage {

    private const val ROOT_DIRECTORY = "phyto_media"
    private const val PENDING_DIRECTORY = "pending"
    private const val UPLOADED_DIRECTORY = "uploaded"
    private const val CAMERA_TEMP_DIRECTORY = "phyto_camera"
    private const val ZIP_TEMP_DIRECTORY = "phyto_sync_zip"
    private const val TAG = "PHYTO_MEDIA"

    /** 340 KB reales, no 340,000 bytes. */
    const val MAX_IMAGE_BYTES: Long = 340L * 1024L

    // Se toma/lee con buena calidad y solo se reduce si es indispensable.
    private const val INITIAL_MAX_SIDE = 2048
    private const val MIN_MAX_SIDE = 240
    private const val INITIAL_JPEG_QUALITY = 100
    private const val MIN_JPEG_QUALITY = 10
    private const val MAX_REMOTE_CACHE_BYTES: Long = 5L * 1024L * 1024L

    data class ZipPendiente(
        val file: File,
        val fileNames: Set<String>
    )
    private data class BitmapDecodificado(
        val bitmap: Bitmap,
        val orientacionExifYaAplicada: Boolean
    )

    /** Archivo temporal usado por CameraX antes de normalizar la evidencia. */
    fun crearArchivoTemporalCamara(context: Context): File {
        val directory = File(context.cacheDir, CAMERA_TEMP_DIRECTORY).apply { mkdirs() }
        return File.createTempFile("phyto_", ".jpg", directory)
    }

    /** Compatibilidad con llamados anteriores que requieren FileProvider. */
    fun crearUriTemporalCamara(context: Context): Uri? = runCatching {
        val file = crearArchivoTemporalCamara(context)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()

    /**
     * Guarda una foto tomada o seleccionada.
     *
     * Acepta formatos de imagen que Android pueda leer y los convierte a JPG
     * correctamente orientado, con fondo blanco si la entrada tiene transparencia,
     * y con tamaño final máximo de 340 KB.
     */
    fun guardarFotoPendiente(
        context: Context,
        sourceUri: Uri,
        idHeader: Long,
        idTargetPoint: Long,
        capturedAt: Long
    ): File {
        val directory = carpetaCaptura(
            context = context,
            estado = PENDING_DIRECTORY,
            idHeader = idHeader,
            idTargetPoint = idTargetPoint,
            capturedAt = capturedAt
        ).apply { mkdirs() }

        val destination = File(
            directory,
            nombreFoto(idHeader, idTargetPoint, capturedAt, "jpg")
        )
        val temporary = File.createTempFile("evidencia_", ".jpg", directory)

        try {
            comprimirComoJpegMaximo340Kb(
                context = context,
                sourceUri = sourceUri,
                destination = temporary
            )

            require(temporary.exists() && temporary.length() in 1..MAX_IMAGE_BYTES) {
                "La foto no se pudo preparar con el límite de 340 KB."
            }

// No borres la evidencia anterior hasta tener la nueva lista.
            directory.listFiles()
                ?.filter { it.isFile && it != temporary }
                ?.forEach { it.delete() }

            if (destination.exists()) destination.delete()
            val replaced = temporary.renameTo(destination)
            if (!replaced) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }

            require(destination.exists() && destination.length() in 1..MAX_IMAGE_BYTES) {
                "La foto no se pudo guardar localmente con el límite de 340 KB."
            }

            return destination
        } catch (error: Throwable) {
            temporary.delete()
            throw IllegalStateException(
                "No se pudo preparar la evidencia fotográfica: ${error.message ?: "formato no compatible"}",
                error
            )
        }
    }

    /** Solo busca la foto pendiente; es la que debe coincidir con photo_ref del CSV. */
    fun buscarFotoPendiente(
        context: Context,
        idHeader: Long,
        idTargetPoint: Long,
        capturedAt: Long?
    ): File? {
        if (capturedAt == null) return null

        return listarFotosEnCarpeta(
            carpetaCaptura(
                context = context,
                estado = PENDING_DIRECTORY,
                idHeader = idHeader,
                idTargetPoint = idTargetPoint,
                capturedAt = capturedAt
            )
        ).firstOrNull()
    }

    /** Busca una evidencia pendiente por su nombre, sin depender de capturedAt. */
    fun buscarFotoPendientePorNombre(
        context: Context,
        idHeader: Long,
        fileName: String
    ): File? {
        val nombre = fileName.trim()
            .takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') && !it.contains("..") }
            ?: return null

        val root = carpetaHeader(context, PENDING_DIRECTORY, idHeader)
        if (!root.exists()) return null

        return root.walkTopDown()
            .firstOrNull { file ->
                file.isFile &&
                        file.name == nombre &&
                        file.length() > 0L &&
                        esExtensionImagen(file.extension)
            }
    }

    /** Busca una evidencia local, primero pending y después uploaded, por nombre. */
    fun buscarFotoLocalPorNombre(
        context: Context,
        idHeader: Long,
        fileName: String
    ): File? {
        val nombre = fileName.trim()
            .takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') && !it.contains("..") }
            ?: return null

        return listOf(PENDING_DIRECTORY, UPLOADED_DIRECTORY)
            .asSequence()
            .map { estado -> carpetaHeader(context, estado, idHeader) }
            .filter { root -> root.exists() }
            .flatMap { root -> root.walkTopDown() }
            .firstOrNull { file ->
                file.isFile &&
                        file.name == nombre &&
                        file.length() > 0L &&
                        esExtensionImagen(file.extension)
            }
    }

    /** Busca primero pendiente y después confirmada/subida. */
    fun buscarFotoLocal(
        context: Context,
        idHeader: Long,
        idTargetPoint: Long,
        capturedAt: Long?
    ): File? {
        if (capturedAt == null) return null

        return listOf(PENDING_DIRECTORY, UPLOADED_DIRECTORY)
            .asSequence()
            .map { estado ->
                carpetaCaptura(
                    context = context,
                    estado = estado,
                    idHeader = idHeader,
                    idTargetPoint = idTargetPoint,
                    capturedAt = capturedAt
                )
            }
            .flatMap { folder -> listarFotosEnCarpeta(folder).asSequence() }
            .firstOrNull()
    }

    /**
     * Crea un ZIP con evidencias pendientes. Cuando [fileNamesPermitidos] no es null,
     * solo agrega los nombres ya reconciliados contra checkpoints del servidor.
     */
    fun crearZipPendiente(
        context: Context,
        idHeader: Long,
        fileNamesPermitidos: Set<String>? = null
    ): ZipPendiente? {
        val permitidos = fileNamesPermitidos
            ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            ?.toSet()

        val root = carpetaHeader(context, PENDING_DIRECTORY, idHeader)
        if (!root.exists()) return null

        val photos = root.walkTopDown()
            .filter { file ->
                file.isFile &&
                        file.length() > 0L &&
                        esExtensionImagen(file.extension) &&
                        (permitidos == null || file.name in permitidos)
            }
            .toList()

        if (photos.isEmpty()) return null

        val duplicateNames = photos.groupBy { it.name }
            .filterValues { it.size > 1 }
            .keys

        require(duplicateNames.isEmpty()) {
            "Hay nombres de foto duplicados en evidencias pendientes: ${duplicateNames.joinToString()}"
        }

        val zipDirectory = File(context.cacheDir, ZIP_TEMP_DIRECTORY).apply { mkdirs() }
        val zipFile = File.createTempFile("phyto_header_${idHeader}_", ".zip", zipDirectory)

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            photos.forEach { photo ->
                zip.putNextEntry(ZipEntry(photo.name))
                FileInputStream(photo).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }

        require(zipFile.exists() && zipFile.length() > 0L) {
            "No se pudo crear el ZIP de evidencias."
        }

        return ZipPendiente(
            file = zipFile,
            fileNames = photos.map { it.name }.toSet()
        )
    }

    fun listarNombresFotosPendientes(
        context: Context,
        idHeader: Long
    ): Set<String> {
        val root = carpetaHeader(context, PENDING_DIRECTORY, idHeader)
        if (!root.exists()) return emptySet()

        return root.walkTopDown()
            .filter { file ->
                file.isFile && file.length() > 0L && esExtensionImagen(file.extension)
            }
            .map { it.name }
            .toSet()
    }

    /** Descarga una evidencia confirmada y la deja disponible offline. */
    fun descargarFotoRemotaComoUploaded(
        context: Context,
        photoUrl: String,
        idHeader: Long,
        idTargetPoint: Long,
        capturedAt: Long,
        photoRef: String?
    ): File {
        val urlTexto = normalizarUrlRemota(photoUrl)
            ?: error("La URL de la evidencia no es válida.")

        val nombre = photoRef
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') && !it.contains("..") }
            ?: nombreFoto(idHeader, idTargetPoint, capturedAt, "jpg")

        val directory = carpetaCaptura(
            context = context,
            estado = UPLOADED_DIRECTORY,
            idHeader = idHeader,
            idTargetPoint = idTargetPoint,
            capturedAt = capturedAt
        ).apply { mkdirs() }

        val destination = File(directory, nombre)
        if (archivoEsImagenValida(destination)) return destination
        destination.delete()

        val temporary = File.createTempFile("remote_", ".tmp", directory)
        var response: okhttp3.Response? = null

        try {
            val request = Request.Builder()
                .url(urlTexto)
                .get()
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header("User-Agent", "CIAgro-Android/1.0")
                .build()

            response = RetrofitClient
                .crearClienteImagenesAutenticado(context.applicationContext)
                .newCall(request)
                .execute()

            val status = response.code
            require(status in 200..299) {
                "No se pudo descargar la evidencia. HTTP $status"
            }

            val body = response.body
                ?: error("El servidor respondió sin contenido de imagen.")
            val contentLength = body.contentLength()
            require(contentLength <= 0L || contentLength <= MAX_REMOTE_CACHE_BYTES) {
                "La evidencia supera el límite local de ${MAX_REMOTE_CACHE_BYTES / 1024 / 1024} MB."
            }

            val contentType = body.contentType()
                ?.toString()
                ?.lowercase(Locale.ROOT)

            body.byteStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    copiarConLimite(input, output, MAX_REMOTE_CACHE_BYTES)
                    output.fd.sync()
                }
            }

            require(archivoEsImagenValida(temporary)) {
                "El servidor respondió con contenido que no es una imagen${contentType?.let { " ($it)" }.orEmpty()}."
            }

            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }

            require(archivoEsImagenValida(destination)) {
                "No se pudo guardar la evidencia descargada."
            }

            Log.d(TAG, "EVIDENCIA_CACHEADA ${destination.name} <- $urlTexto")
            return destination
        } finally {
            response?.close()
            if (temporary.exists()) temporary.delete()
        }
    }

    /**
     * Corrige rutas relativas y hosts temporales de desarrollo al túnel actual.
     * Así una foto guardada con un trycloudflare antiguo sigue siendo descargable.
     */
    private fun normalizarUrlRemota(valor: String?): String? {
        val clean = valor
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val base = ApiConfig.BASE_URL.trim().trimEnd('/')

        if (!clean.startsWith("http://", true) && !clean.startsWith("https://", true)) {
            val relative = clean.trimStart('/')
            return when {
                relative.startsWith("media/", ignoreCase = true) -> "$base/$relative"
                relative.startsWith("phyto_checkpoints/", ignoreCase = true) -> "$base/media/$relative"
                clean.startsWith("/") -> "$base/$relative"
                else -> "$base/$relative"
            }
        }

        val uri = Uri.parse(clean)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)

        val reemplazarHost =
            host.isBlank() ||
                    host == "localhost" ||
                    host == "0.0.0.0" ||
                    host == "web" ||
                    host == "ciagro-web" ||
                    host.endsWith(".trycloudflare.com") ||
                    host.startsWith("127.") ||
                    host.startsWith("10.") ||
                    host.startsWith("192.168.") ||
                    (host.startsWith("172.") && esHostPrivado172(host))

        if (!reemplazarHost) return clean

        val path = uri.encodedPath?.takeIf { it.isNotBlank() } ?: "/"
        val query = uri.encodedQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        return "$base$path$query"
    }

    private fun esHostPrivado172(host: String): Boolean {
        val segundoOcteto = host
            .substringAfter("172.", missingDelimiterValue = "")
            .substringBefore('.')
            .toIntOrNull()
            ?: return false
        return segundoOcteto in 16..31
    }

    private fun archivoEsImagenValida(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false

        return runCatching {
            val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opciones)
            opciones.outWidth > 0 && opciones.outHeight > 0
        }.getOrDefault(false)
    }

    /** Mueve únicamente los archivos que el backend confirmó. */
    fun confirmarFotosSubidas(
        context: Context,
        idHeader: Long,
        fileNames: Set<String>
    ): Int {
        if (fileNames.isEmpty()) return 0

        val pendingRoot = carpetaHeader(context, PENDING_DIRECTORY, idHeader)
        val uploadedRoot = carpetaHeader(context, UPLOADED_DIRECTORY, idHeader)
        var moved = 0

        pendingRoot.walkTopDown()
            .filter { file -> file.isFile && file.name in fileNames }
            .toList()
            .forEach { source ->
                val relative = source.relativeTo(pendingRoot)
                val destination = File(uploadedRoot, relative.path).apply {
                    parentFile?.mkdirs()
                }

                source.copyTo(destination, overwrite = true)
                if (destination.exists() && destination.length() == source.length()) {
                    source.delete()
                    moved++
                }
            }

        limpiarDirectoriosVacios(pendingRoot)
        return moved
    }

    fun contarFotosPendientes(context: Context, idHeader: Long): Int {
        val root = carpetaHeader(context, PENDING_DIRECTORY, idHeader)
        if (!root.exists()) return 0

        return root.walkTopDown().count { file ->
            file.isFile && file.length() > 0L && esExtensionImagen(file.extension)
        }
    }

    /**
     * Libera únicamente evidencias que ya estaban en uploaded. La carpeta pending
     * jamás se toca desde la depuración mensual.
     */
    fun eliminarFotosConfirmadasDeHeader(
        context: Context,
        idHeader: Long
    ): Boolean {
        if (idHeader <= 0L) return false

        val uploadedRoot = carpetaHeader(context, UPLOADED_DIRECTORY, idHeader)
        if (!uploadedRoot.exists()) return true

        val eliminado = runCatching {
            uploadedRoot.deleteRecursively()
        }.getOrDefault(false)

        if (eliminado) {
            limpiarDirectoriosVacios(
                File(context.filesDir, "$ROOT_DIRECTORY/$UPLOADED_DIRECTORY")
            )
        }

        return eliminado
    }

    fun eliminarZipTemporal(zip: ZipPendiente?) {
        runCatching { zip?.file?.delete() }
    }

    fun nombreFoto(
        idHeader: Long,
        idTargetPoint: Long,
        capturedAt: Long,
        extension: String = "jpg"
    ): String = "h${idHeader}_p${idTargetPoint}_${capturedAt}.${extension.lowercase(Locale.ROOT)}"

    fun limpiarTemporalesCamara(context: Context) {
        File(context.cacheDir, CAMERA_TEMP_DIRECTORY)
            .listFiles()
            ?.forEach { it.delete() }
    }

    /**
     * Convierte cualquier entrada decodificable por Android a JPG orientado y <=340 KB.
     */
    private fun comprimirComoJpegMaximo340Kb(
        context: Context,
        sourceUri: Uri,
        destination: File
    ) {
        val decodificado = decodificarBitmapCompatible(
            context = context,
            sourceUri = sourceUri,
            maxSide = INITIAL_MAX_SIDE
        )

        var bitmap = decodificado.bitmap

        try {
            /*
             * ImageDecoder ya respeta EXIF.
             * BitmapFactory no, por eso solo aplicamos EXIF cuando hubo fallback.
             */
            if (!decodificado.orientacionExifYaAplicada) {
                bitmap = aplicarOrientacionExif(context, sourceUri, bitmap)
            }

            bitmap = aplicarFondoBlancoSiTieneTransparencia(bitmap)

            repeat(14) {
                val bytes = comprimirConLaMejorCalidadPosible(bitmap)
                if (bytes != null) {
                    FileOutputStream(destination).use { output ->
                        output.write(bytes)
                        output.flush()
                        output.fd.sync()
                    }
                    return
                }

                val ladoActual = max(bitmap.width, bitmap.height)
                if (ladoActual <= MIN_MAX_SIDE) {
                    error("No se pudo reducir la imagen a 340 KB sin dañarla demasiado.")
                }

                val nuevoLado = (ladoActual * 0.80f)
                    .toInt()
                    .coerceAtLeast(MIN_MAX_SIDE)

                val escala = nuevoLado.toFloat() / ladoActual.toFloat()
                val nuevoAncho = max(1, (bitmap.width * escala).toInt())
                val nuevoAlto = max(1, (bitmap.height * escala).toInt())

                val reducido = Bitmap.createScaledBitmap(bitmap, nuevoAncho, nuevoAlto, true)
                if (reducido !== bitmap) bitmap.recycle()
                bitmap = reducido
            }

            error("No se pudo preparar la imagen a máximo 340 KB.")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Busca la calidad más alta posible que todavía cumpla el límite. */
    private fun comprimirConLaMejorCalidadPosible(bitmap: Bitmap): ByteArray? {
        var inferior = MIN_JPEG_QUALITY
        var superior = INITIAL_JPEG_QUALITY
        var mejor: ByteArray? = null

        while (inferior <= superior) {
            val calidad = (inferior + superior) / 2
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, output)) {
                    "No se pudo convertir la imagen a JPG."
                }
                output.toByteArray()
            }

            if (bytes.size.toLong() <= MAX_IMAGE_BYTES) {
                mejor = bytes
                inferior = calidad + 1
            } else {
                superior = calidad - 1
            }
        }

        return mejor
    }

    /**
     * ImageDecoder amplía soporte en Android 9+ (por ejemplo HEIC/HEIF en equipos
     * compatibles). Si falla, BitmapFactory intenta leer JPEG/PNG/WEBP/BMP/GIF estático.
     */
    private fun decodificarBitmapCompatible(
        context: Context,
        sourceUri: Uri,
        maxSide: Int
    ): BitmapDecodificado {
        val decodedByImageDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val source = when (sourceUri.scheme?.lowercase(Locale.ROOT)) {
                    "file" -> ImageDecoder.createSource(
                        File(requireNotNull(sourceUri.path))
                    )

                    else -> ImageDecoder.createSource(
                        context.contentResolver,
                        sourceUri
                    )
                }

                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        } else {
            null
        }

        if (decodedByImageDecoder != null) {
            return BitmapDecodificado(
                bitmap = escalarBitmapSiEsNecesario(
                    decodedByImageDecoder,
                    maxSide
                ),
                orientacionExifYaAplicada = true
            )
        }

        return BitmapDecodificado(
            bitmap = escalarBitmapSiEsNecesario(
                decodificarConBitmapFactory(context, sourceUri),
                maxSide
            ),
            orientacionExifYaAplicada = false
        )
    }

    private fun decodificarConBitmapFactory(context: Context, sourceUri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrirEntrada(context, sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: error("No se pudo leer la imagen seleccionada.")

        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "El formato no se pudo leer. Usa una imagen estándar: JPG, PNG, WEBP, HEIC/HEIF, BMP o GIF."
        }

        val sample = calcularInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxSide = INITIAL_MAX_SIDE
        )

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return abrirEntrada(context, sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("No se pudo decodificar la imagen seleccionada.")
    }

    private fun escalarBitmapSiEsNecesario(bitmap: Bitmap, maxSide: Int): Bitmap {
        val ladoActual = max(bitmap.width, bitmap.height)
        if (ladoActual <= maxSide) return bitmap

        val scale = maxSide.toFloat() / ladoActual.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).toInt()),
            max(1, (bitmap.height * scale).toInt()),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun calcularInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxSide || height / (sample * 2) >= maxSide) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    /** Normaliza transparencia de PNG/WEBP/GIF sobre blanco porque JPG no tiene alpha. */
    private fun aplicarFondoBlancoSiTieneTransparencia(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap

        val prepared = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(prepared).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        bitmap.recycle()
        return prepared
    }

    /** Corrige EXIF de imágenes verticales/horizontales antes de guardarlas. */
    private fun aplicarOrientacionExif(
        context: Context,
        sourceUri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        val orientation = runCatching {
            when (sourceUri.scheme?.lowercase(Locale.ROOT)) {
                "file" -> ExifInterface(requireNotNull(sourceUri.path)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                else -> context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { descriptor ->
                    ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }

        if (matrix.isIdentity) return bitmap

        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun carpetaHeader(context: Context, estado: String, idHeader: Long): File {
        return File(context.filesDir, "$ROOT_DIRECTORY/$estado/header_$idHeader")
    }

    private fun carpetaCaptura(
        context: Context,
        estado: String,
        idHeader: Long,
        idTargetPoint: Long,
        capturedAt: Long
    ): File {
        return File(
            context.filesDir,
            "$ROOT_DIRECTORY/$estado/header_$idHeader/punto_$idTargetPoint/captura_$capturedAt"
        )
    }

    private fun listarFotosEnCarpeta(folder: File): List<File> {
        return folder.listFiles()
            ?.filter { file -> file.isFile && file.length() > 0L && esExtensionImagen(file.extension) }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    private fun copiarConLimite(input: InputStream, output: FileOutputStream, limite: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val leidos = input.read(buffer)
            if (leidos <= 0) break

            total += leidos
            require(total <= limite) {
                "La evidencia supera el límite local de ${limite / 1024 / 1024} MB."
            }
            output.write(buffer, 0, leidos)
        }
    }

    private fun limpiarDirectoriosVacios(root: File) {
        if (!root.exists()) return

        root.walkBottomUp()
            .filter { it.isDirectory && it.listFiles().isNullOrEmpty() }
            .forEach { it.delete() }
    }

    private fun abrirEntrada(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> uri.path?.let { path -> FileInputStream(File(path)) }
            else -> uri.path
                ?.let(::File)
                ?.takeIf { it.exists() }
                ?.let(::FileInputStream)
        }
    }

    private fun esExtensionImagen(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in setOf(
            "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif", "avif"
        )
    }
}

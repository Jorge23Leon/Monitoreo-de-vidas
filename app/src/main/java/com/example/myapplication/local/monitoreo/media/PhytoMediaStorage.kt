package com.example.myapplication.local.monitoreo.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Evidencias locales de monitoreo fitosanitario.
 *
 * No usa Room ni crea tablas: la relación se obtiene por
 * idHeader + idTargetPoint + capturedAt.
 *
 * Estructura:
 * files/phyto_media/pending/header_{id}/punto_{id}/captura_{timestamp}/archivo.ext
 */
object PhytoMediaStorage {

    private const val ROOT_DIRECTORY = "phyto_media"
    private const val PENDING_DIRECTORY = "pending"
    private const val UPLOADED_DIRECTORY = "uploaded"
    private const val CAMERA_TEMP_DIRECTORY = "phyto_camera"

    fun crearUriTemporalCamara(context: Context): Uri? = runCatching {
        val directory = File(context.cacheDir, CAMERA_TEMP_DIRECTORY).apply { mkdirs() }
        val file = File.createTempFile("phyto_", ".jpg", directory)

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()

    /** Copia una imagen elegida/tomada a la carpeta privada del registro. */
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

        // Esta primera versión permite una sola foto por captura.
        directory.listFiles()
            ?.filter { it.isFile }
            ?.forEach { it.delete() }

        val extension = extensionSegura(context, sourceUri)
        val destination = File(
            directory,
            nombreFoto(idHeader, idTargetPoint, capturedAt, extension)
        )

        val input = requireNotNull(abrirEntrada(context, sourceUri)) {
            "No se pudo leer la imagen seleccionada."
        }

        input.use { stream ->
            FileOutputStream(destination).use { output ->
                stream.copyTo(output)
                output.fd.sync()
            }
        }

        require(destination.exists() && destination.length() > 0L) {
            "La foto no se pudo guardar localmente."
        }

        return destination
    }

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
            .flatMap { folder -> folder.listFiles()?.asSequence() ?: emptySequence() }
            .firstOrNull { file ->
                file.isFile && file.length() > 0L && esExtensionImagen(file.extension)
            }
    }

    fun nombreFoto(
        idHeader: Long,
        idTargetPoint: Long,
        capturedAt: Long,
        extension: String = "jpg"
    ): String {
        return "h${idHeader}_p${idTargetPoint}_${capturedAt}.${extension.lowercase()}"
    }

    fun limpiarTemporalesCamara(context: Context) {
        File(context.cacheDir, CAMERA_TEMP_DIRECTORY)
            .listFiles()
            ?.forEach { it.delete() }
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

    private fun abrirEntrada(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> uri.path?.let { path -> FileInputStream(File(path)) }
            else -> uri.path
                ?.let(::File)
                ?.takeIf { it.exists() }
                ?.let { file -> FileInputStream(file) }
        }
    }

    private fun extensionSegura(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()

        return when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("heic") || mimeType.contains("heif") -> "jpg"
            else -> "jpg"
        }
    }

    private fun esExtensionImagen(extension: String): Boolean {
        return extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
    }
}

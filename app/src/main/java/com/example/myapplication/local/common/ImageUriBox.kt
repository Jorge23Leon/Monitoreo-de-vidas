package com.example.myapplication.local.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImageUriBox(
    photo: String?,
    fallbackIcon: String = "🌱",
    sizeDp: Int = 72,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var bitmap by remember(photo) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(photo) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val rutaLocal = ImageCache.resolverParaPersistir(
                    context = context.applicationContext,
                    photo = photo
                ) ?: return@withContext null

                cargarImagenConOrientacion(
                    context = context.applicationContext,
                    ruta = rutaLocal
                )
            } catch (e: Exception) {
                Log.w(
                    "IMAGE_UI",
                    "No se pudo mostrar la imagen: ${photo ?: "sin URL"}",
                    e
                )
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFD9E7D1),
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val imagen = bitmap

        if (imagen != null) {
            Image(
                bitmap = imagen,
                contentDescription = "Imagen",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = fallbackIcon,
                fontSize = (sizeDp / 2).sp
            )
        }
    }
}

private fun cargarImagenConOrientacion(
    context: Context,
    ruta: String
): ImageBitmap? {
    if (
        ruta.startsWith("http://", ignoreCase = true) ||
        ruta.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }

    val bitmapOriginal = when {
        ruta.startsWith("content://", ignoreCase = true) -> {
            context.contentResolver
                .openInputStream(Uri.parse(ruta))
                ?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
        }

        ruta.startsWith("file://", ignoreCase = true) -> {
            val path = Uri.parse(ruta).path ?: return null
            BitmapFactory.decodeFile(path)
        }

        File(ruta).exists() -> {
            BitmapFactory.decodeFile(ruta)
        }

        else -> null
    } ?: return null

    val orientacion = obtenerOrientacionExif(
        context = context,
        ruta = ruta
    )

    return corregirOrientacion(
        bitmap = bitmapOriginal,
        orientacion = orientacion
    ).asImageBitmap()
}

private fun obtenerOrientacionExif(
    context: Context,
    ruta: String
): Int {
    return runCatching {
        when {
            ruta.startsWith("content://", ignoreCase = true) -> {
                context.contentResolver
                    .openInputStream(Uri.parse(ruta))
                    ?.use { stream ->
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    }
                    ?: ExifInterface.ORIENTATION_NORMAL
            }

            ruta.startsWith("file://", ignoreCase = true) -> {
                val path = Uri.parse(ruta).path
                    ?: return@runCatching ExifInterface.ORIENTATION_NORMAL

                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }

            File(ruta).exists() -> {
                ExifInterface(ruta).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }

            else -> ExifInterface.ORIENTATION_NORMAL
        }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
}

private fun corregirOrientacion(
    bitmap: Bitmap,
    orientacion: Int
): Bitmap {
    val matrix = Matrix()

    when (orientacion) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)

        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }

        else -> return bitmap
    }

    return runCatching {
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }.getOrDefault(bitmap)
}
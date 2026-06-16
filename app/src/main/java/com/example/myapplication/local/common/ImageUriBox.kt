package com.example.myapplication.local.common

import android.graphics.BitmapFactory
import android.net.Uri
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
import com.example.myapplication.local.api.core.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
        bitmap = if (photo.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val photoFinal = normalizarPhotoUrl(photo)

                    when {
                        photoFinal.startsWith("http://") ||
                                photoFinal.startsWith("https://") -> {
                            val connection = URL(photoFinal).openConnection() as HttpURLConnection
                            connection.connectTimeout = 8000
                            connection.readTimeout = 8000
                            connection.instanceFollowRedirects = true

                            connection.inputStream.use { stream ->
                                BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
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
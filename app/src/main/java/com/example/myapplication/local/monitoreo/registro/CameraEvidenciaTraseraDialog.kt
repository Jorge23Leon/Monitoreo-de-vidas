package com.example.myapplication.local.monitoreo.registro

import android.net.Uri
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.myapplication.local.monitoreo.media.PhytoMediaStorage

/**
 * Cámara propia de la app. Se vincula exclusivamente a [CameraSelector.DEFAULT_BACK_CAMERA],
 * por lo que no abre la cámara frontal ni la última cámara usada por otra aplicación.
 */
@Composable
internal fun CameraEvidenciaTraseraDialog(
    onCancelar: () -> Unit,
    onFotoTomada: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var errorCamara by remember { mutableStateOf<String?>(null) }
    var capturando by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView) {
        var activo = true
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener(
            {
                if (!activo) return@addListener

                try {
                    val provider = future.get()
                    if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        errorCamara = "Este dispositivo no tiene cámara trasera disponible."
                        return@addListener
                    }

                    val preview = Preview.Builder().build().also { useCase ->
                        useCase.surfaceProvider = previewView.surfaceProvider
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(Size(1600, 1200))
                        .setJpegQuality(92)
                        .build()

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )

                    cameraProvider = provider
                    imageCapture = capture
                    errorCamara = null
                } catch (error: Throwable) {
                    errorCamara = "No se pudo iniciar la cámara trasera: ${error.message ?: "error desconocido"}"
                }
            },
            executor
        )

        onDispose {
            activo = false
            imageCapture = null
            cameraProvider?.unbindAll()
        }
    }

    fun tomarFoto() {
        val capture = imageCapture
        if (capture == null) {
            errorCamara = "La cámara todavía se está preparando."
            return
        }

        val temporal = runCatching {
            PhytoMediaStorage.crearArchivoTemporalCamara(context.applicationContext)
        }.getOrElse { error ->
            errorCamara = "No se pudo preparar la evidencia: ${error.message}"
            return
        }

        capturando = true
        errorCamara = null

        val outputOptions = ImageCapture.OutputFileOptions.Builder(temporal).build()
        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturando = false
                    onFotoTomada(Uri.fromFile(temporal).toString())
                }

                override fun onError(exception: ImageCaptureException) {
                    capturando = false
                    temporal.delete()
                    errorCamara = "No se pudo tomar la foto: ${exception.message ?: exception.imageCaptureError}"
                }
            }
        )
    }

    Dialog(
        onDismissRequest = {
            if (!capturando) onCancelar()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Evidencia fotográfica",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Cámara trasera · La foto se guardará en JPG con máximo 300 KB",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp
                )
            }

            errorCamara?.let { mensaje ->
                Text(
                    text = mensaje,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .background(Color(0xFF7F1D1D).copy(alpha = 0.88f))
                        .padding(14.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCancelar,
                    enabled = !capturando,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF374151)
                    )
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = ::tomarFoto,
                    enabled = !capturando && imageCapture != null,
                    modifier = Modifier.size(width = 142.dp, height = 54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0B6B20)
                    )
                ) {
                    Text(
                        text = if (capturando) "Guardando..." else "📷 Capturar",
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

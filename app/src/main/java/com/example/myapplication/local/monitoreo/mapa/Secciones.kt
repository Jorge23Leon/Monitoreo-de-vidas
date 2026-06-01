package com.example.myapplication.local.monitoreo.mapa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HORAS_LIMITE_MONITOREO = 24L
internal const val DURACION_MONITOREO_MS = HORAS_LIMITE_MONITOREO * 60L * 60L * 1000L

internal enum class AccionDialogoMapa {
    REGRESAR,
    PAUSAR,
    TERMINAR
}

internal fun esMonitoreoPausadoMapa(
    status: String,
    additionalNotes: String
): Boolean {
    val estado = status.trim().lowercase(Locale.getDefault())

    return (estado == "pendiente" || estado == "pending") &&
            additionalNotes.trim().startsWith("PAUSADO", ignoreCase = true)
}

internal fun esEstadoCerradoMapa(status: String): Boolean {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "completed",
        "completado",
        "finalizado",
        "terminado",
        "cerrado",
        "cancelado",
        "cancelled",
        "canceled" -> true

        else -> false
    }
}

internal fun textoEstadoMapa(
    status: String,
    additionalNotes: String = ""
): String {
    if (esMonitoreoPausadoMapa(status, additionalNotes)) return "Pausado"

    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado", "terminado", "cerrado" -> "Completado"
        "cancelado", "cancelled", "canceled" -> "Cancelado"
        else -> status.ifBlank { "Pendiente" }
    }
}

internal fun textoTiempoRestanteMonitoreo(
    startAt: Long?,
    ahoraMs: Long
): String {
    if (startAt == null) return "Aún no inicia"

    val venceMs = startAt + DURACION_MONITOREO_MS
    val restanteMs = venceMs - ahoraMs

    if (restanteMs <= 0L) return "Tiempo agotado"

    val totalMinutos = restanteMs / 60_000L
    val horas = totalMinutos / 60L
    val minutos = totalMinutos % 60L

    return if (horas > 0) {
        "${horas}h ${minutos}min"
    } else {
        "${minutos}min"
    }
}

private fun colorEstadoMapa(
    status: String,
    additionalNotes: String = ""
): Color {
    if (esMonitoreoPausadoMapa(status, additionalNotes)) return Color(0xFFD66B00)

    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> Color(0xFF9A6A00)
        "in_progress", "en proceso", "vigente" -> Color(0xFF255EA8)
        "completed", "completado", "finalizado", "terminado", "cerrado" -> Color(0xFF1F6D2A)
        "cancelado", "cancelled", "canceled" -> Color(0xFFB3261E)
        else -> Color(0xFF303030)
    }
}

private fun colorFondoEstadoMapa(
    status: String,
    additionalNotes: String = ""
): Color {
    if (esMonitoreoPausadoMapa(status, additionalNotes)) return Color(0xFFFFF0D6)

    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> Color(0xFFFFF4CC)
        "in_progress", "en proceso", "vigente" -> Color(0xFFE2F0FF)
        "completed", "completado", "finalizado", "terminado", "cerrado" -> Color(0xFFE4F4DF)
        "cancelado", "cancelled", "canceled" -> Color(0xFFFFE1E1)
        else -> Color(0xFFEDEDED)
    }
}

internal fun formatoFechaMapa(fecha: Long?): String {
    if (fecha == null) return "-"

    return try {
        SimpleDateFormat("dd MMM yyyy - HH:mm", Locale("es", "MX"))
            .format(Date(fecha))
            .replace(".", "")
    } catch (_: Exception) {
        "-"
    }
}

@Composable
internal fun BarraMapaMonitoreo(
    nombreMonitoreo: String,
    status: String,
    additionalNotes: String,
    startAt: Long?,
    ahoraMs: Long,
    onRegresarClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRegresarClick,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFD0D7DE)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "‹",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF173B1A)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Monitoreo - $nombreMonitoreo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D2430),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = textoEstadoMapa(status, additionalNotes),
                        modifier = Modifier
                            .background(
                                color = colorFondoEstadoMapa(status, additionalNotes),
                                shape = RoundedCornerShape(30.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        color = colorEstadoMapa(status, additionalNotes),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = "Mapa del monitoreo",
                        fontSize = 12.sp,
                        color = Color(0xFF6E7580),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Tiempo restante: ${textoTiempoRestanteMonitoreo(startAt, ahoraMs)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD66B00),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun ResumenMapaMonitoreo(
    totalVertices: Int,
    totalPuntos: Int,
    totalCapturas: Int
) {
    val porcentaje = if (totalPuntos > 0) {
        (totalCapturas * 100) / totalPuntos
    } else {
        0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "Resumen del monitoreo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1D2430)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricaMapaCard(
                    icono = "▧",
                    label = "Vértices",
                    value = totalVertices.toString(),
                    modifier = Modifier.weight(1f)
                )

                MetricaMapaCard(
                    icono = "⌖",
                    label = "Puntos",
                    value = totalPuntos.toString(),
                    modifier = Modifier.weight(1f)
                )

                MetricaMapaCard(
                    icono = "✓",
                    label = "Capturados",
                    value = "$totalCapturas ($porcentaje%)",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricaMapaCard(
    icono: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFFF8FAF7), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE3EAE0), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icono,
            fontSize = 22.sp,
            color = Color(0xFF0B6B20),
            fontWeight = FontWeight.Black
        )

        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF6E7580),
            maxLines = 1
        )

        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF1D2430),
            maxLines = 1
        )
    }
}
@Composable
internal fun AccionesMapaMonitoreo(
    totalPuntos: Int,
    puntosCapturados: Int,
    estaPausado: Boolean,
    finalizandoMonitoreo: Boolean,
    onPausarClick: () -> Unit,
    onTerminarClick: () -> Unit,
    onContinuarClick: () -> Unit
) {
    val puedeAccionar = puntosCapturados > 0

    if (estaPausado) {
        Button(
            onClick = onContinuarClick,
            enabled = !finalizandoMonitoreo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD96B00),
                disabledContainerColor = Color(0xFFBDBDBD)
            )
        ) {
            Text(
                text = if (finalizandoMonitoreo) {
                    "Procesando..."
                } else {
                    "▶  Continuar monitoreo"
                },
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 16.sp
            )
        }

        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Acciones del monitoreo",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1D2430)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPausarClick,
                    enabled = !finalizandoMonitoreo && puedeAccionar,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFD96B00)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White
                    )
                ) {
                    Text(
                        text = "Ⅱ  Pausar",
                        color = Color(0xFFD96B00),
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onTerminarClick,
                    enabled = !finalizandoMonitoreo && puedeAccionar,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF006B2A),
                        disabledContainerColor = Color(0xFFBDBDBD)
                    )
                ) {
                    Text(
                        text = "✓  Terminar",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }

            if (!puedeAccionar && totalPuntos > 0) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Primero captura mínimo 1 punto para pausar o terminar el monitoreo.",
                    fontSize = 12.sp,
                    color = Color(0xFF6E7580),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
@Composable
internal fun DialogoAccionMapa(
    accion: AccionDialogoMapa,
    totalPuntos: Int,
    puntosCapturados: Int,
    finalizandoMonitoreo: Boolean,
    onDismiss: () -> Unit,
    onPausarConfirmado: () -> Unit,
    onTerminarConfirmado: () -> Unit
) {
    val puntosFaltantes = (totalPuntos - puntosCapturados).coerceAtLeast(0)
    val sinCapturas = puntosCapturados <= 0

    val esTerminar = accion == AccionDialogoMapa.TERMINAR ||
            (accion == AccionDialogoMapa.REGRESAR && totalPuntos > 0 && puntosFaltantes == 0)

    val titulo = when {
        sinCapturas -> "Captura requerida"
        esTerminar -> "Terminar monitoreo"
        else -> "Pausar monitoreo"
    }

    val colorPrincipal = when {
        sinCapturas -> Color(0xFFB3261E)
        esTerminar -> Color(0xFF1F6D2A)
        else -> Color(0xFFD96B00)
    }

    val icono = when {
        sinCapturas -> "!"
        esTerminar -> "✓"
        else -> "Ⅱ"
    }

    val mensaje = when {
        sinCapturas -> {
            "Mínimo necesitas capturar 1 punto para pausar o terminar el monitoreo."
        }

        esTerminar && puntosFaltantes > 0 -> {
            "Has capturado $puntosCapturados de $totalPuntos puntos. Aún faltan $puntosFaltantes. ¿Seguro que quieres terminar el monitoreo? Después ya no podrás capturar más puntos."
        }

        esTerminar -> {
            "Ya capturaste todos los puntos. ¿Quieres terminar el monitoreo? Después ya no podrás capturar más puntos."
        }

        else -> {
            "Has capturado $puntosCapturados de $totalPuntos puntos. Aún faltan $puntosFaltantes. ¿Quieres pausar el monitoreo? El tiempo seguirá corriendo desde el primer inicio del monitoreo."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = titulo,
                fontWeight = FontWeight.Black,
                color = colorPrincipal
            )
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colorPrincipal.copy(alpha = 0.13f))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icono,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = colorPrincipal
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = mensaje,
                    fontSize = 14.sp,
                    color = Color(0xFF3F4650),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (sinCapturas) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Aceptar",
                        color = colorPrincipal,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                TextButton(
                    enabled = !finalizandoMonitoreo,
                    onClick = {
                        if (esTerminar) {
                            onTerminarConfirmado()
                        } else {
                            onPausarConfirmado()
                        }
                    }
                ) {
                    Text(
                        text = if (finalizandoMonitoreo) {
                            "Procesando..."
                        } else if (esTerminar) {
                            "Sí, terminar"
                        } else {
                            "Sí, pausar"
                        },
                        color = colorPrincipal,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        },
        dismissButton = {
            if (!sinCapturas) {
                TextButton(
                    enabled = !finalizandoMonitoreo,
                    onClick = onDismiss
                ) {
                    Text("Cancelar")
                }
            }
        }
    )
}
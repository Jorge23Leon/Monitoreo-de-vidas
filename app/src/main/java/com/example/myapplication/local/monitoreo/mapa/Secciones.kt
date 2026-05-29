package com.example.myapplication.local.monitoreo.mapa

import android.app.AlertDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BarraMapaMonitoreo(nombreMonitoreo: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F6F6))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mapa del monitoreo",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = nombreMonitoreo,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ResumenMapaMonitoreo(
    totalVertices: Int,
    totalPuntos: Int,
    totalCapturas: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(72.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F6F6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Resumen", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(
                text = "Vértices: $totalVertices  |  Puntos: $totalPuntos  |  Capturas: $totalCapturas",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A4A4A),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun BotonTerminarMonitoreo(
    database: AppDatabase,
    header: LocalPhytomonitoringHeaderEntity,
    totalPuntos: Int,
    puntosCapturados: Int,
    finalizandoMonitoreo: Boolean,
    onFinalizandoChange: (Boolean) -> Unit,
    onMonitoreoActualizado: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val puntosFaltantes = totalPuntos - puntosCapturados

    fun dejarEnProceso() {
        onFinalizandoChange(true)
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.localphytomonitoringheaderDao().dejarMonitoreoEnProceso(idHeader = header.idHeader)
                }
                Toast.makeText(context, "Monitoreo guardado en proceso", Toast.LENGTH_SHORT).show()
                onMonitoreoActualizado("En proceso")
            } catch (e: Exception) {
                Toast.makeText(context, "Error al dejar en proceso: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                onFinalizandoChange(false)
            }
        }
    }

    fun terminarMonitoreo() {
        onFinalizandoChange(true)
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.localphytomonitoringheaderDao().finalizarMonitoreo(
                        idHeader = header.idHeader,
                        finishedAt = System.currentTimeMillis()
                    )
                }
                Toast.makeText(context, "Monitoreo terminado correctamente", Toast.LENGTH_SHORT).show()
                onMonitoreoActualizado("Completado")
            } catch (e: Exception) {
                Toast.makeText(context, "Error al terminar monitoreo: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                onFinalizandoChange(false)
            }
        }
    }

    Button(
        onClick = {
            if (finalizandoMonitoreo) return@Button

            if (totalPuntos <= 0) {
                AlertDialog.Builder(context)
                    .setTitle("Sin puntos")
                    .setMessage("Este monitoreo no tiene puntos asignados.")
                    .setPositiveButton("Aceptar") { dialog, _ -> dialog.dismiss() }
                    .show()
                return@Button
            }

            if (puntosFaltantes > 0) {
                AlertDialog.Builder(context)
                    .setTitle("Faltan puntos por monitorear")
                    .setMessage(
                        "Llevas $puntosCapturados de $totalPuntos puntos capturados.\n\n" +
                                "Aún faltan $puntosFaltantes puntos.\n\n" +
                                "Puedes dejar el monitoreo en proceso para continuarlo después, o terminarlo de todos modos."
                    )
                    .setNegativeButton("Dejar en proceso") { dialog, _ -> dialog.dismiss(); dejarEnProceso() }
                    .setPositiveButton("Terminar monitoreo") { dialog, _ -> dialog.dismiss(); terminarMonitoreo() }
                    .show()
            } else {
                AlertDialog.Builder(context)
                    .setTitle("Terminar monitoreo")
                    .setMessage("Todos los puntos ya fueron capturados.\n\n¿Deseas terminar el monitoreo?")
                    .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton("Terminar") { dialog, _ -> dialog.dismiss(); terminarMonitoreo() }
                    .show()
            }
        },
        enabled = !finalizandoMonitoreo,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            disabledContainerColor = Color(0xFF9E9E9E)
        )
    ) {
        Text(
            text = if (finalizandoMonitoreo) "Procesando..." else "✅ Terminar monitoreo",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

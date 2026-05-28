package com.example.myapplication.local.admin.agricola

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DialogParcelaGestion(
    guardando: Boolean,
    parcela: LocalPlotEntity?,
    ranchos: List<LocalRanchEntity>,
    productores: List<LocalAgroUnitEntity>,
    onDismiss: () -> Unit,
    onMensaje: (String) -> Unit,
    onGuardar: (LocalRanchEntity?, String, String, String, String, List<ImportedPolygonPoint>) -> Unit
) {
    var ranchoSeleccionado by remember(parcela?.idLocalPlot, ranchos) {
        mutableStateOf(ranchos.firstOrNull { it.idLocalRanch == parcela?.idLocalRanch } ?: ranchos.firstOrNull())
    }
    var codigo by remember(parcela?.idLocalPlot) { mutableStateOf(parcela?.code ?: "") }
    var descripcion by remember(parcela?.idLocalPlot) { mutableStateOf(parcela?.name ?: "") }
    var lat by remember(parcela?.idLocalPlot) { mutableStateOf(parcela?.lat?.toString() ?: "") }
    var lon by remember(parcela?.idLocalPlot) { mutableStateOf(parcela?.lon?.toString() ?: "") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var puntosPoligono by remember(parcela?.idLocalPlot) { mutableStateOf<List<ImportedPolygonPoint>>(emptyList()) }
    var cargandoPoligono by remember(parcela?.idLocalPlot) { mutableStateOf(false) }
    var nombreArchivoPoligono by remember(parcela?.idLocalPlot) { mutableStateOf<String?>(null) }
    var errorPoligono by remember(parcela?.idLocalPlot) { mutableStateOf<String?>(null) }

    val launcherPoligono = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            cargandoPoligono = true
            errorPoligono = null
            try {
                val resultado = withContext(Dispatchers.IO) {
                    parsePolygonFileFromUri(context = context, uri = uri)
                }
                puntosPoligono = resultado
                nombreArchivoPoligono = uri.lastPathSegment ?: "Archivo seleccionado"
                onMensaje("Coordenadas cargadas: ${resultado.size} vértices")
            } catch (e: Exception) {
                e.printStackTrace()
                puntosPoligono = emptyList()
                errorPoligono = e.message ?: "No se pudo leer el archivo"
                onMensaje("No se pudieron leer las coordenadas: ${e.message}")
            } finally {
                cargandoPoligono = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!guardando && !cargandoPoligono) onDismiss()
        },
        confirmButton = {
            GestionDialogConfirmButton(
                text = when {
                    puntosPoligono.size >= 3 && parcela == null -> "Guardar parcela y coordenadas"
                    puntosPoligono.size >= 3 -> "Actualizar parcela y coordenadas"
                    parcela == null -> "Guardar"
                    else -> "Actualizar"
                },
                guardando = guardando || cargandoPoligono,
                enabled = ranchoSeleccionado != null && codigo.trim().isNotBlank() && !cargandoPoligono,
                onClick = { onGuardar(ranchoSeleccionado, codigo, descripcion, lat, lon, puntosPoligono) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando && !cargandoPoligono) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = { GestionDialogTitle(if (parcela == null) "Agregar parcela" else "Editar parcela") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogInfoGestion(
                    "Selecciona el rancho y registra la información de la parcela. " +
                            "También puedes cargar aquí el CSV/Excel/Notas de coordenadas para guardar todo en un solo paso."
                )

                Spacer(modifier = Modifier.height(12.dp))
                SelectorGestionField(
                    etiqueta = "Rancho",
                    valor = ranchoSeleccionado?.let { rancho ->
                        val productor = productores.firstOrNull { it.idLocalAgroUnit == rancho.idLocalAgroUnit }
                        "${rancho.name} (${productor?.commercial_name ?: "Productor no encontrado"})"
                    } ?: "Seleccionar rancho",
                    opciones = ranchos,
                    textoOpcion = { rancho ->
                        val productor = productores.firstOrNull { it.idLocalAgroUnit == rancho.idLocalAgroUnit }
                        "${rancho.name} (${productor?.commercial_name ?: "Productor no encontrado"})"
                    },
                    habilitado = !guardando && !cargandoPoligono && ranchos.isNotEmpty(),
                    onSeleccionar = { ranchoSeleccionado = it }
                )

                Spacer(modifier = Modifier.height(8.dp))
                GestionTextField(codigo, { codigo = it }, "Código de parcela", "Ejemplo: CIEM", guardando || cargandoPoligono)
                Spacer(modifier = Modifier.height(8.dp))
                GestionTextField(descripcion, { descripcion = it }, "Nombre de parcela", "Ejemplo: Parcela CIEM", guardando || cargandoPoligono)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GestionDecimalField(lat, { lat = it }, "Latitud", guardando || cargandoPoligono, Modifier.weight(1f), obligatorio = false)
                    GestionDecimalField(lon, { lon = it }, "Longitud", guardando || cargandoPoligono, Modifier.weight(1f), obligatorio = false)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE0E8DC))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Coordenadas de la parcela",
                    color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Carga el archivo de coordenadas de esta parcela. Si lo haces aquí, se guardará junto con la parcela.",
                    color = Color(0xFF5E6D58),
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { launcherPoligono.launch(arrayOf("*/*")) },
                    enabled = !guardando && !cargandoPoligono,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    if (cargandoPoligono) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Leyendo coordenadas")
                    } else {
                        Text(if (puntosPoligono.isEmpty()) "Cargar coordenadas CSV/Excel/Notas" else "Cambiar coordenadas")
                    }
                }

                if (!nombreArchivoPoligono.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Archivo: $nombreArchivoPoligono",
                        color = Color(0xFF263B1E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                if (!errorPoligono.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorPoligono ?: "",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (puntosPoligono.size >= 3) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PolygonPreviewCardGestion(
                        puntos = puntosPoligono,
                        altura = 210
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Listo: al guardar se registrarán ${puntosPoligono.size} coordenadas y se calculará el centro de la parcela.",
                        color = Color(0xFF5E6D58),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

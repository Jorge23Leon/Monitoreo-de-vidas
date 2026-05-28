package com.example.myapplication.local.admin.monitoreos

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AdminMonitoreoLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color(0xFF2E7D32))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Cargando productores y cultivos...",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E4D22)
            )
        }
    }
}

@Composable
fun AdminMonitorHeroCard(
    nombreCia: String,
    avance: Float,
    onBackClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1B5E20),
                            Color(0xFF43A047)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🗺️ Administrar monitoreos",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 21.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Crea programas, encabezados y puntos objetivo con la base actual de MyApplication.",
                        color = Color(0xFFE8F5E9),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Volver")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CIA: $nombreCia",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { avance.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
fun AdminMonitorSectionCard(
    numero: String,
    titulo: String,
    subtitulo: String,
    contenido: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = numero,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        text = subtitulo,
                        fontSize = 12.sp,
                        color = Color(0xFF5E6D58),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            contenido()
        }
    }
}

@Composable
fun <T> AdminSelectorField(
    etiqueta: String,
    valor: String,
    opciones: List<T>,
    textoOpcion: (T) -> String,
    habilitado: Boolean,
    onSeleccionar: (T) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = etiqueta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E4D22),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (habilitado) Color(0xFFB6C8AA) else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(if (habilitado) Color.White else Color(0xFFF5F5F5))
                    .clickable(enabled = habilitado) { abierto = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valor,
                    color = if (habilitado) Color(0xFF263B1E) else Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "⌄", color = Color(0xFF2E7D32), fontWeight = FontWeight.Black)
            }

            DropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false },
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                if (opciones.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin opciones disponibles") },
                        onClick = { abierto = false }
                    )
                } else {
                    opciones.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(textoOpcion(opcion)) },
                            onClick = {
                                abierto = false
                                onSeleccionar(opcion)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDatePickerField(
    etiqueta: String,
    valor: String,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = etiqueta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E4D22),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (habilitado) Color(0xFFB6C8AA) else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(if (habilitado) Color.White else Color(0xFFF5F5F5))
                .clickable(enabled = habilitado) { onClick() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = valor.ifBlank { "Seleccionar fecha" },
                color = if (habilitado) Color(0xFF263B1E) else Color.Gray,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            Text(text = "📅", fontSize = 20.sp)
        }
    }
}

@Composable
fun PolygonStatusCard(vertices: List<LocalPlotVertexEntity>) {
    val listo = vertices.size >= 3

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (listo) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = if (listo) "✅" else "⚠️", fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (listo) "Polígono listo" else "Faltan vértices",
                    fontWeight = FontWeight.Black,
                    color = if (listo) Color(0xFF1B5E20) else Color(0xFFE65100)
                )
                Text(
                    text = if (listo) {
                        "La parcela tiene ${vertices.size} vértices. Se pueden generar puntos dentro del lote."
                    } else {
                        "La parcela necesita mínimo 3 vértices en LocalPlotVertexEntity."
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF4E5D45),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun AdminResumenMonitoreo(
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?,
    cultivo: LocalCropCatalogEntity?,
    ciclo: String,
    fechaInicio: String,
    fechaFin: String,
    radioTexto: String,
    cantidadPuntosTexto: String,
    totalVertices: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Resumen antes de guardar",
                fontWeight = FontWeight.Black,
                color = Color(0xFF1B5E20),
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFD9E7D1))
            Spacer(modifier = Modifier.height(8.dp))

            ResumenRow("Productor", productor?.commercial_name ?: "Pendiente")
            ResumenRow("Rancho", rancho?.name ?: "Pendiente")
            ResumenRow("Parcela", parcela?.nombreMostrarAdmin() ?: "Pendiente")
            ResumenRow("Cultivo", cultivo?.name ?: "Pendiente")
            ResumenRow("Ciclo", ciclo.ifBlank { "Pendiente" })
            ResumenRow("Fechas", "${fechaInicio.ifBlank { "Inicio" }} → ${fechaFin.ifBlank { "Fin" }}")
            ResumenRow("Puntos", "${cantidadPuntosTexto.ifBlank { "0" }} puntos / radio ${radioTexto.ifBlank { "0" }} m")
            ResumenRow("Vértices", "$totalVertices registrados")

            AnimatedVisibility(visible = totalVertices < 3) {
                Text(
                    text = "No se podrá guardar hasta que la parcela tenga polígono completo.",
                    color = Color(0xFFE65100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ResumenRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF4E5D45),
            modifier = Modifier.width(82.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF263B1E),
            modifier = Modifier.weight(1f)
        )
    }
}

fun mostrarDatePickerNativoAdmin(
    context: Context,
    fechaActualMillis: Long?,
    onSeleccionar: (Long) -> Unit
) {
    val calendar = Calendar.getInstance()

    if (fechaActualMillis != null) {
        calendar.timeInMillis = fechaActualMillis
    }

    val anio = calendar.get(Calendar.YEAR)
    val mes = calendar.get(Calendar.MONTH)
    val dia = calendar.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val seleccion = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            onSeleccionar(seleccion.timeInMillis)
        },
        anio,
        mes,
        dia
    ).show()
}

fun LocalPlotEntity.nombreMostrarAdmin(): String {
    val codigo = code?.takeIf { it.isNotBlank() }
    return when {
        codigo == null -> name
        codigo == name -> name
        else -> "$codigo - $name"
    }
}

fun formatearFechaAdmin(fechaMillis: Long?): String {
    if (fechaMillis == null) return ""

    return SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        .format(Date(normalizarFechaAdmin(fechaMillis, finDelDia = false)))
}

fun normalizarFechaAdmin(fechaMillis: Long, finDelDia: Boolean): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = fechaMillis

    if (finDelDia) {
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
    } else {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    return calendar.timeInMillis
}

fun normalizarCicloMonitoreoAdmin(texto: String): String {
    return texto
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9 ._\\-/]"), "")
        .take(80)
}

fun esRolTecnicoAdminMonitoreo(rol: String): Boolean {
    val limpio = rol
        .trim()
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(".", "")
        .replace("_", " ")
        .replace(Regex("\\s+"), " ")

    return limpio == "tecnico" || limpio == "tecnicos"
}

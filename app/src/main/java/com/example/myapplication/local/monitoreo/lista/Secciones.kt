package com.example.myapplication.local.monitoreo.lista

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.ImageUriBox

@Composable
internal fun EncabezadoListaMonitoreos(
    titulo: String,
    subtitulo: String,
    rolEtiqueta: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "▣", fontSize = 22.sp, color = Color(0xFF0B6B20))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = titulo, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF173B1A))
                    Text(text = subtitulo, fontSize = 12.sp, color = Color(0xFF5E6B5B))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = rolEtiqueta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0B6B20),
                modifier = Modifier
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(30.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
internal fun CajaAvisoMonitoreos(
    icono: String,
    textoTitulo: String,
    textoDescripcion: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icono, fontSize = 22.sp, color = Color(0xFF0B6B20))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = textoTitulo, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173B1A))
                Text(text = textoDescripcion, fontSize = 12.sp, color = Color(0xFF4E5D45))
            }
        }
    }
}

@Composable
internal fun TarjetaMonitoreoUsuario(
    parcelaNombre: String,
    productorNombre: String?,
    ranchoNombre: String?,
    ciclo: String,
    codigo: String,
    fotoCultivo: String?,
    nombreCultivo: String?,
    status: String,
    fechaInicio: Long?,
    fechaFin: Long?,
    puedeAbrir: Boolean,
    soloConsulta: Boolean,
    onAbrirClick: () -> Unit,
    onReporteClick: () -> Unit
) {
    val monitoreoCerrado = esEstadoCerradoLista(status)

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFDCE8D8), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { if (puedeAbrir) onAbrirClick() else onReporteClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImageUriBox(
                    photo = fotoCultivo,
                    fallbackIcon = iconoCultivoFallbackLista(nombreCultivo),
                    sizeDp = 82,
                    modifier = Modifier
                        .size(width = 92.dp, height = 82.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorFondoImagenLista(status))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EstadoChip(status = status)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = formatearFechaResumen(fechaInicio), fontSize = 10.sp, color = Color.DarkGray)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = parcelaNombre,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF173B1A),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DatoTarjetaUsuario(label = "Ciclo", value = ciclo, modifier = Modifier.weight(1f))
                        DatoTarjetaUsuario(label = "Código", value = codigo, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "⌂ ${ranchoNombre ?: "Sin rancho"}  ·  ${productorNombre ?: "Sin productor"}",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )

                    Text(
                        text = "📅 ${formatearFechaCorta(fechaInicio)}  al  ${formatearFechaCorta(fechaFin)}",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (puedeAbrir && !soloConsulta && !monitoreoCerrado) {
                Button(
                    onClick = onAbrirClick,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B6B20))
                ) {
                    Text(text = "Abrir monitoreo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onReporteClick,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (monitoreoCerrado) "Ver reporte" else "Ver información",
                        color = Color(0xFF0B6B20),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DatoTarjetaUsuario(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 4.dp)) {
        Text(text = label, fontSize = 9.sp, color = Color.Gray)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
    }
}

@Composable
private fun EstadoChip(status: String) {
    Text(
        text = textoEstadoLista(status),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = colorEstadoLista(status),
        modifier = Modifier
            .background(colorFondoEstadoLista(status), RoundedCornerShape(30.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        textAlign = TextAlign.Center
    )
}

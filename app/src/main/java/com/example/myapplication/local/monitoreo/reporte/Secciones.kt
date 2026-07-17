package com.example.myapplication.local.monitoreo.reporte

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.ImageUriBox

@Composable
internal fun PantallaEstadoReporte(
    titulo: String,
    mensaje: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = titulo,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = mensaje,
            color = Color.DarkGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
internal fun HeroReporteCard(
    idHeader: Long,
    estado: String,
    ciclo: String,
    nombreCia: String,
    productor: String,
    rancho: String,
    parcela: String,
    cultivo: String,
    fotoCultivo: String?,
    fechaProgramada: String,
    inicioReal: FechaHoraCompactaReporteUi,
    finReal: FechaHoraCompactaReporteUi,
    porcentajeAvance: Int,
    contenidoExpandido: Boolean,
    onToggleContenido: () -> Unit,
    detalleExpandido: Boolean,
    onToggleDetalle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0E4224),
                                Color(0xFF165E31),
                                Color(0xFF23793F)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reporte fitosanitario",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )

                        Text(
                            text = "Monitoreo #$idHeader",
                            color = Color.White.copy(alpha = 0.74f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ChipEstadoReporte(
                        texto = if (estado.startsWith("✓")) estado else "✓ $estado",
                        colorFondo = Color.White.copy(alpha = 0.95f),
                        colorTexto = Color(0xFF145A2D)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    ImageUriBox(
                        photo = fotoCultivo,
                        fallbackIcon = "🌱",
                        sizeDp = 82
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PRODUCTOR",
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = productor,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(7.dp))

                        Text(
                            text = "CULTIVO",
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = cultivo,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(13.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DatoUbicacionHeroReporte(
                        icono = "⌂",
                        titulo = "RANCHO",
                        valor = rancho,
                        modifier = Modifier.weight(1f)
                    )

                    DatoUbicacionHeroReporte(
                        icono = "⌖",
                        titulo = "PARCELA",
                        valor = parcela,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleContenido() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Información del monitoreo",
                            color = Color(0xFF2E3630),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (contenidoExpandido) "Toca para contraer" else "Toca para ver fechas y avance",
                            color = Color(0xFF788079),
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = if (contenidoExpandido) "⌃" else "⌄",
                        color = Color(0xFF445047),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                if (contenidoExpandido) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FechaBlancaHeroReporte(
                                marcador = "I",
                                titulo = "INICIO REAL",
                                dato = inicioReal,
                                modifier = Modifier.weight(1f)
                            )

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(68.dp)
                                    .background(Color(0xFFE5EAE4))
                            )

                            FechaBlancaHeroReporte(
                                marcador = "F",
                                titulo = "FIN REAL",
                                dato = finReal,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFE5EAE4))
                        )

                        Spacer(modifier = Modifier.height(11.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AVANCE DEL MONITOREO",
                                color = Color(0xFF3E4A40),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )

                            Text(
                                text = "$porcentajeAvance%",
                                color = Color(0xFF145A2D),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(7.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .background(Color(0xFFE3EEE3), RoundedCornerShape(20.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(porcentajeAvance.coerceIn(0, 100) / 100f)
                                    .height(7.dp)
                                    .background(Color(0xFF1C9B3D), RoundedCornerShape(20.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFE5EAE4))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleDetalle() }
                                .padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Text(
                                    text = "☷",
                                    color = Color(0xFF145A2D),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Column {
                                    Text(
                                        text = "Más información",
                                        color = Color(0xFF2E3630),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = "CIA, ciclo y fecha programada",
                                        color = Color(0xFF788079),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Text(
                                text = if (detalleExpandido) "⌃" else "⌄",
                                color = Color(0xFF445047),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        if (detalleExpandido) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF4F8F3), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                RowInfoHeroReporte("CIA", nombreCia)
                                RowInfoHeroReporte("Ciclo", ciclo)
                                RowInfoHeroReporte("Programado", fechaProgramada)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatoUbicacionHeroReporte(
    icono: String,
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(15.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = icono,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                color = Color.White.copy(alpha = 0.67f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = valor,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FechaBlancaHeroReporte(
    marcador: String,
    titulo: String,
    dato: FechaHoraCompactaReporteUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFFE8F5E9), RoundedCornerShape(50.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = marcador,
                color = Color(0xFF145A2D),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                color = Color(0xFF4C5650),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )

            Text(
                text = dato.fecha,
                color = Color(0xFF202522),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (dato.hora.isNotBlank()) {
                Text(
                    text = dato.hora,
                    color = Color(0xFF145A2D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RowInfoHeroReporte(titulo: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            color = Color(0xFF5C675F),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = valor,
            color = Color(0xFF213226),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.weight(1.45f)
        )
    }
}


@Composable
internal fun TarjetaResumenReporte(
    titulo: String,
    valor: String,
    detalle: String,
    icono: String = "◎",
    colorAcento: Color = Color(0xFF1B5E20),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(colorAcento.copy(alpha = 0.11f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icono,
                    color = colorAcento,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = valor,
                color = Color(0xFF123D1F),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = titulo,
                color = Color(0xFF303832),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Text(
                text = detalle,
                color = colorAcento,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun ChipEstadoReporte(
    texto: String,
    colorFondo: Color,
    colorTexto: Color
) {
    Box(
        modifier = Modifier
            .background(colorFondo, RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = colorTexto,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
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
    inicioReal: String,
    finalizado: String,
    porcentajeAvance: Int,
    detalleExpandido: Boolean,
    onToggleDetalle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF123D1F),
                            Color(0xFF2E7D32),
                            Color(0xFF7CB342)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reporte fitosanitario",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            text = "Monitoreo #$idHeader",
                            color = Color(0xFFE8F5E9),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    ChipEstadoReporte(
                        texto = estado,
                        colorFondo = Color.White.copy(alpha = 0.92f),
                        colorTexto = Color(0xFF1B5E20)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImageUriBox(
                        photo = fotoCultivo,
                        fallbackIcon = "🌱",
                        sizeDp = 76
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Productor",
                            color = Color(0xFFE8F5E9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = productor,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Cultivo: $cultivo",
                            color = Color(0xFFE8F5E9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DatoPrincipalHeroReporte(
                        titulo = "Rancho",
                        valor = rancho,
                        modifier = Modifier.weight(1f)
                    )

                    DatoPrincipalHeroReporte(
                        titulo = "Parcela",
                        valor = parcela,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .clickable { onToggleDetalle() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Datos adicionales",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )

                            Text(
                                text = if (detalleExpandido) {
                                    "Toca para ocultar CIA, ciclo y fechas"
                                } else {
                                    "Toca para ver CIA, ciclo, cultivo y fechas"
                                },
                                color = Color(0xFFE8F5E9),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        ChipEstadoReporte(
                            texto = if (detalleExpandido) "Ocultar ▲" else "Ver más ▼",
                            colorFondo = Color.White.copy(alpha = 0.92f),
                            colorTexto = Color(0xFF1B5E20)
                        )
                    }

                    if (detalleExpandido) {
                        Spacer(modifier = Modifier.height(10.dp))
                        RowInfoHeroReporte("CIA", nombreCia)
                        RowInfoHeroReporte("Ciclo", ciclo)
                        RowInfoHeroReporte("Cultivo", cultivo)
                        RowInfoHeroReporte("Estado", estado)
                        RowInfoHeroReporte("Fecha programada", fechaProgramada)
                        RowInfoHeroReporte("Inicio real", inicioReal)
                        RowInfoHeroReporte("Finalizado", finalizado)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Avance del monitoreo",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "$porcentajeAvance%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(porcentajeAvance.coerceIn(0, 100) / 100f)
                                .height(10.dp)
                                .background(Color.White, RoundedCornerShape(20.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatoPrincipalHeroReporte(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = titulo,
            color = Color(0xFFE8F5E9),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Text(
            text = valor,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun RowInfoHeroReporte(titulo: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            color = Color(0xFFE8F5E9),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = valor,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.weight(1.35f)
        )
    }
}

@Composable
internal fun TarjetaResumenReporte(
    titulo: String,
    valor: String,
    detalle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titulo,
                color = Color(0xFF5F6F64),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Text(
                text = valor,
                color = Color(0xFF123D1F),
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = detalle,
                color = Color(0xFF6D6D6D),
                fontSize = 10.sp,
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

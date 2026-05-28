package com.example.myapplication.local.cia

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity

@Composable
fun SelectorCiaHija(
    esSupervisor: Boolean,
    parentCiaSeleccionada: LocalParentCiaEntity?,
    cias: List<LocalCiaEntity>,
    ciaSeleccionada: LocalCiaEntity?,
    onCiaChange: (LocalCiaEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFDDE8D6),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFCF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = if (esSupervisor) {
                    "1. Selecciona una CIA hija asignada"
                } else {
                    "2. Selecciona una CIA hija"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF253A25)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                esSupervisor && cias.isEmpty() -> {
                    CajaMensajeSeleccion(texto = "No tienes CIAS hijas asignadas.")
                }

                esSupervisor -> {
                    ListaCiasHijasCard(
                        iconoEncabezado = "🌱",
                        tituloEncabezado = "CIAS hijas disponibles",
                        cias = cias,
                        ciaSeleccionada = ciaSeleccionada,
                        onCiaChange = onCiaChange
                    )
                }

                parentCiaSeleccionada == null -> {
                    CajaMensajeSeleccion(texto = "Selecciona una CIA padre para mostrar sus CIAS hijas.")
                }

                cias.isEmpty() -> {
                    CajaMensajeSeleccion(texto = "Esta CIA padre no tiene CIAS hijas asignadas.")
                }

                else -> {
                    ListaCiasHijasCard(
                        iconoEncabezado = "🏢",
                        tituloEncabezado = parentCiaSeleccionada.name,
                        mostrarIconoExpandido = true,
                        cias = cias,
                        ciaSeleccionada = ciaSeleccionada,
                        onCiaChange = onCiaChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ListaCiasHijasCard(
    iconoEncabezado: String,
    tituloEncabezado: String,
    mostrarIconoExpandido: Boolean = false,
    cias: List<LocalCiaEntity>,
    ciaSeleccionada: LocalCiaEntity?,
    onCiaChange: (LocalCiaEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEAF5E4))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = iconoEncabezado, fontSize = 22.sp)

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = tituloEncabezado,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E4A27),
                    modifier = Modifier.weight(1f)
                )

                if (mostrarIconoExpandido) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(
                                width = 2.dp,
                                color = Color(0xFF4A8F2A),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4A8F2A)
                        )
                    }
                }
            }

            cias.forEachIndexed { index, cia ->
                CiaHijaTreeItem(
                    cia = cia,
                    seleccionada = ciaSeleccionada?.idLocalCia == cia.idLocalCia,
                    esUltima = index == cias.lastIndex,
                    onClick = { onCiaChange(cia) }
                )
            }
        }
    }
}

@Composable
private fun CiaHijaTreeItem(
    cia: LocalCiaEntity,
    seleccionada: Boolean,
    esUltima: Boolean,
    onClick: () -> Unit
) {
    val fondo = if (seleccionada) {
        Color(0xFFEAF5E4)
    } else {
        Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (esUltima) 21.dp else 42.dp)
                    .background(Color(0xFFD2DCCB))
                    .align(Alignment.TopCenter)
            )

            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(1.dp)
                    .background(Color(0xFFD2DCCB))
                    .align(Alignment.CenterEnd)
            )
        }

        Text(text = "🌱", fontSize = 20.sp)

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cia.nombre,
                fontSize = 15.sp,
                fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.SemiBold,
                color = Color(0xFF263D24)
            )

            Text(
                text = cia.description ?: "CIA hija disponible",
                fontSize = 11.sp,
                color = Color(0xFF777777),
                maxLines = 1
            )
        }

        RadioVisualSeleccionado(seleccionada = seleccionada)
    }
}

@Composable
private fun RadioVisualSeleccionado(
    seleccionada: Boolean
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(
                width = 2.dp,
                color = if (seleccionada) Color(0xFF2F7D20) else Color(0xFF6EA560),
                shape = CircleShape
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (seleccionada) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(0xFF2F7D20), CircleShape)
            )
        }
    }
}

@Composable
private fun CajaMensajeSeleccion(
    texto: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = texto,
            modifier = Modifier.padding(14.dp),
            fontSize = 13.sp,
            color = Color(0xFF4E5D45),
            textAlign = TextAlign.Center
        )
    }
}

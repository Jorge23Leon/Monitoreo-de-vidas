package com.example.myapplication.local.monitoreo.registro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.ImageUriBox
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity

@Composable
internal fun RegistroPuntoSuperiorCard(
    punto: LocalPhytomonitoringTargetPointEntity,
    numeroPunto: Int,
    nombreCultivo: String,
    fotoCultivo: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAF0FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⌖",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF174EA6)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Punto ${numeroPunto.toString().padStart(2, '0')}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D2430),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "▧  Parcela: ${punto.idLocalPlot}",
                    fontSize = 14.sp,
                    color = Color(0xFF4F5663),
                    maxLines = 1
                )

                Text(
                    text = "⌁  Cultivo: $nombreCultivo",
                    fontSize = 14.sp,
                    color = Color(0xFF4F5663),
                    maxLines = 1
                )
            }

            ImageUriBox(
                photo = fotoCultivo,
                fallbackIcon = "🌽",
                sizeDp = 110,
                modifier = Modifier
                    .width(126.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
internal fun ElementoSeleccionadoCard(
    fito: LocalPhytosanitaryCatalogEntity?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF1D7C8), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = if (fito == null) "Elemento seleccionado" else "Elemento seleccionado",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF743B1F)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (fito == null) {
                Text(
                    text = "Selecciona una plaga o enfermedad del catálogo para capturar cantidades.",
                    fontSize = 13.sp,
                    color = Color(0xFF6D6D6D)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImageUriBox(
                        photo = fito.photo,
                        fallbackIcon = iconoTipoFitoRegistro(fito.type),
                        sizeDp = 72,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fito.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1D2430),
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(7.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TipoFitoChip(type = fito.type)
                            EstadoActivoChip()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TipoFitoChip(type: String?) {
    Text(
        text = "${iconoTipoFitoRegistro(type)}  ${textoTipoFitoRegistro(type)}",
        modifier = Modifier
            .background(colorChipFondoTipoRegistro(type), RoundedCornerShape(12.dp))
            .border(1.dp, colorChipTextoTipoRegistro(type).copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = colorChipTextoTipoRegistro(type),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black
    )
}

@Composable
internal fun EstadoActivoChip() {
    Text(
        text = "✓  Activo",
        modifier = Modifier
            .background(Color(0xFFE7F4E3), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = Color(0xFF2E7D32),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black
    )
}

@Composable
internal fun SectionTitle(
    title: String,
    actionText: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF1D2430)
        )

        if (actionText != null) {
            Text(
                text = actionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF174EA6)
            )
        }
    }
}

@Composable
internal fun InfoBox(
    text: String,
    isError: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0xFFFFEBEE) else Color(0xFFF1F8EF)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            fontSize = 13.sp,
            color = if (isError) Color(0xFFC62828) else Color(0xFF4F4F4F),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ObservacionesBox(
    value: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            if (value.isBlank()) {
                Text(
                    text = "Escribe aquí tus observaciones (opcional)",
                    fontSize = 14.sp,
                    color = Color(0xFF8C939D)
                )
            }

            BasicTextField(
                value = value,
                onValueChange = { nuevoTexto ->
                    if (nuevoTexto.length <= 300) onValueChange(nuevoTexto)
                },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = Color.Black
                ),
                modifier = Modifier.fillMaxSize()
            )

            Text(
                text = "${value.length}/300",
                fontSize = 12.sp,
                color = Color(0xFF6E7580),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
internal fun BarraAccionesRegistro(
    finalizando: Boolean,
    modifier: Modifier = Modifier,
    onSinPlagaClick: () -> Unit,
    onGuardarClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onSinPlagaClick,
                enabled = !finalizando,
                modifier = Modifier
                    .weight(0.82f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFB8C1CC)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color(0xFFF0F0F0)
                )
            ) {
                Text(
                    text = if (finalizando) "..." else "⊘  Sin plaga",
                    color = Color(0xFF1D2430),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onGuardarClick,
                enabled = !finalizando,
                modifier = Modifier
                    .weight(1.18f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D47C5),
                    disabledContainerColor = Color(0xFF9E9E9E)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = if (finalizando) "Guardando..." else "▣  Guardar registro",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

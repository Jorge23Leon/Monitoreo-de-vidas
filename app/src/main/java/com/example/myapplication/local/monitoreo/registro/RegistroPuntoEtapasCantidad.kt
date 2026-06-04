package com.example.myapplication.local.monitoreo.registro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.myapplication.local.common.ImageUriBox
import com.example.myapplication.local.entities.LocalPhytostageEntity

@Composable
internal fun EtapasCantidadCard(
    etapas: List<EtapaCantidadUi>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Etapas y cantidad",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D2430)
                )

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = "ⓘ",
                    fontSize = 15.sp,
                    color = Color(0xFF6E7580)
                )
            }

            etapas.forEach { etapaUi ->
                EtapaRowModerna(
                    etapa = etapaUi.etapa,
                    cantidad = etapaUi.cantidad,
                    onMenos = etapaUi.onMenos,
                    onMas = etapaUi.onMas
                )
            }
        }
    }
}

@Composable
private fun EtapaRowModerna(
    etapa: LocalPhytostageEntity,
    cantidad: Int,
    onMenos: () -> Unit,
    onMas: () -> Unit
) {
    val nombreEtapa = etapa.stage

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ImageUriBox(
            photo = etapa.photo,
            fallbackIcon = iconoEtapaRegistro(nombreEtapa),
            sizeDp = 42,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorIconoEtapaRegistro(nombreEtapa))
        )

        Text(
            text = nombreEtapa,
            fontSize = 18.sp,
            color = Color(0xFF1D2430),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            CircleCounterButtonModerno(
                text = "−",
                enabled = cantidad > 0,
                onClick = onMenos
            )

            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 42.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cantidad.toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D2430),
                    textAlign = TextAlign.Center
                )
            }

            CircleCounterButtonModerno(
                text = "+",
                enabled = true,
                onClick = onMas
            )
        }
    }
}

@Composable
private fun CircleCounterButtonModerno(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            disabledContainerColor = Color(0xFFF4F4F4)
        ),
        border = BorderStroke(1.dp, if (enabled) Color(0xFFD9DEE6) else Color(0xFFE5E5E5)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color(0xFF26313D) else Color(0xFFB0B0B0),
            textAlign = TextAlign.Center
        )
    }
}

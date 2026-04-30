package com.example.myapplication.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InformacionScreen(
    onCloseClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InfoHeader(
                title = "Información",
                subtitle = "Conoce el objetivo de la aplicación y el enfoque tecnológico de Grupo GPA."
            )

            Spacer(modifier = Modifier.height(18.dp))

            InfoCard(
                title = "Descripción de la app",
                icon = "🌱",
                text = "Esta aplicación está diseñada para apoyar el monitoreo agrícola de plagas y enfermedades en cultivos. Permite registrar información en campo, consultar puntos de monitoreo, capturar datos técnicos y organizar evidencia para facilitar el seguimiento del cultivo."
            )

            InfoCard(
                title = "Objetivo de la aplicación",
                icon = "🎯",
                text = "El objetivo principal es apoyar a técnicos y productores en la identificación, registro y seguimiento de plagas y enfermedades, ayudando a mejorar la toma de decisiones mediante información ordenada, precisa y disponible para consulta."
            )

            InfoCard(
                title = "Información de Grupo GPA",
                icon = "🏭",
                text = "Grupo GPA es una empresa mexicana con sede en León, Guanajuato. Se enfoca en investigación, desarrollo, innovación, producción y comercialización, con especialidad en la industria metalmecánica y en la fabricación de maquinaria de automatización."
            )

            InfoCard(
                title = "Experiencia e innovación",
                icon = "⚙️",
                text = "GPA ha evolucionado desde soluciones de automatización hacia la fabricación de máquinas y componentes complejos. Su trabajo se relaciona con sectores como la industria automotriz, ferroviaria, aeroespacial, agroindustrial y metalmecánica."
            )

            InfoCard(
                title = "Relación con el sector agroindustrial",
                icon = "🚜",
                text = "Dentro del enfoque agroindustrial, la digitalización permite fortalecer el registro de datos en campo, el seguimiento de cultivos y el análisis técnico para mejorar procesos agrícolas. Esta aplicación forma parte de esa visión: usar tecnología para organizar información y apoyar decisiones en campo."
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Redes",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F331F)
            )

            Spacer(modifier = Modifier.height(16.dp))

            RedesRow()

            Spacer(modifier = Modifier.height(32.dp))
        }

        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .background(Color(0xFFD7193F), CircleShape)
        ) {
            Text(
                text = "✕",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoHeader(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2F6B35),
                            Color(0xFF9BCB72)
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: String,
    text: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    fontSize = 26.sp
                )

                Spacer(modifier = Modifier.padding(horizontal = 5.dp))

                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F331F)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = text,
                fontSize = 14.sp,
                color = Color(0xFF333333),
                lineHeight = 20.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun RedesRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RedSocialItem("f", "Facebook", Color(0xFF1877F2))
        RedSocialItem("◎", "Instagram", Color(0xFFE4405F))
        RedSocialItem("▶", "YouTube", Color(0xFFFF0000))
    }
}

@Composable
private fun RedSocialItem(
    icon: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(color, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Black
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InformacionScreenPreview() {
    MaterialTheme {
        InformacionScreen(onCloseClick = {})
    }
}
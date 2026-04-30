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
fun ContactoScreen(
    onCloseClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactHeader()

            Spacer(modifier = Modifier.height(18.dp))

            ContactCard(
                icon = "🛠️",
                title = "Soporte técnico",
                content = "Aquí podemos brindarte soporte técnico relacionado con el uso de la aplicación, errores de acceso, fallas en el registro de monitoreos o dudas sobre la captura de información."
            )

            ContactCard(
                icon = "📧",
                title = "Correo de soporte",
                content = "soporte@tierrainteligente.com"
            )

            ContactCard(
                icon = "☎️",
                title = "Teléfono Grupo GPA",
                content = "477 772 6065"
            )

            ContactCard(
                icon = "📍",
                title = "Sucursal matriz",
                content = "Camino a Rancho Alegre #110-A, Col. Villas de San Juan Segunda Sección, C.P. 37295, León, Guanajuato."
            )

            ContactCard(
                icon = "💬",
                title = "Recomendación al solicitar ayuda",
                content = "Cuando reportes un problema, incluye tu usuario, el tipo de error, la pantalla donde ocurrió y una breve descripción de lo que estabas realizando. Esto ayuda a dar seguimiento más rápido."
            )

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Redes",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F331F)
            )

            Spacer(modifier = Modifier.height(16.dp))

            ContactRedesRow()

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
private fun ContactHeader() {
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
                            Color(0xFF263238),
                            Color(0xFF66BB6A)
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Contacto y soporte",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Comunícate con el equipo de soporte si necesitas ayuda con el uso de la aplicación.",
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun ContactCard(
    icon: String,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = icon,
                fontSize = 27.sp
            )

            Spacer(modifier = Modifier.padding(horizontal = 7.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F331F)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = content,
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun ContactRedesRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ContactRedSocialItem("f", "Facebook", Color(0xFF1877F2))
        ContactRedSocialItem("◎", "Instagram", Color(0xFFE4405F))
        ContactRedSocialItem("▶", "YouTube", Color(0xFFFF0000))
    }
}

@Composable
private fun ContactRedSocialItem(
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
fun ContactoScreenPreview() {
    MaterialTheme {
        ContactoScreen(onCloseClick = {})
    }
}
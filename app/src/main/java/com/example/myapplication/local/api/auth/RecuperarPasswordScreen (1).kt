package com.example.myapplication.local.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun RecuperarPasswordScreen(
    onEnviarSolicitudClick: (String) -> Unit,
    onVolverLoginClick: () -> Unit
) {
    var correo by remember { mutableStateOf("") }
    var errorCorreo by remember { mutableStateOf("") }

    fun solicitarRecuperacion() {
        val correoLimpio = correo.trim()

        when {
            correoLimpio.isBlank() -> {
                errorCorreo = "Escribe tu correo electrónico."
            }

            !Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches() -> {
                errorCorreo = "Ingresa un correo electrónico válido."
            }

            else -> {
                errorCorreo = ""
                onEnviarSolicitudClick(correoLimpio)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF4F8F0),
                        Color(0xFFE8EFE2)
                    )
                )
            )
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Recuperar contraseña",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F331F),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Escribe el correo asociado a tu cuenta. Se abrirá tu aplicación de correo para enviar la solicitud al equipo de soporte.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF6B6B6B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = "Correo electrónico",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = {
                        correo = it
                        errorCorreo = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = {
                        Text("ejemplo@correo.com")
                    }
                )

                if (errorCorreo.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = errorCorreo,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 13.sp,
                        color = Color(0xFFB3261E)
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                Button(
                    onClick = { solicitarRecuperacion() },
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(50.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB7D99A)
                    )
                ) {
                    Text(
                        text = "Solicitar recuperación",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF36512D)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = onVolverLoginClick
                ) {
                    Text(
                        text = "← Volver a iniciar sesión",
                        fontSize = 14.sp,
                        color = Color(0xFFD65A5A)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecuperarPasswordScreenPreview() {
    MaterialTheme {
        RecuperarPasswordScreen(
            onEnviarSolicitudClick = {},
            onVolverLoginClick = {}
        )
    }
}

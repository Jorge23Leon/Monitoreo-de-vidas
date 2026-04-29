package com.example.myapplication.local

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegistroUsuarioScreen(
    onRegisterClick: (
        firstName: String,
        lastname: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        role: String
    ) -> Unit,
    onBackToLoginClick: () -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastname by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val role = "Técnico"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFEFEF)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Registro de usuario",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Ingresa tus datos para crear tu cuenta",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Nombre",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Apellido",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = lastname,
                    onValueChange = { lastname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Usuario",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Correo",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Contraseña",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Confirmar contraseña",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = { showPassword = !showPassword }
                        ) {
                            Text(
                                text = if (showPassword) "Ocultar" else "Ver",
                                fontSize = 12.sp
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        onRegisterClick(
                            firstName.trim(),
                            lastname.trim(),
                            username.trim(),
                            email.trim(),
                            password.trim(),
                            confirmPassword.trim(),
                            role
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(48.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC5E1A5)
                    )
                ) {
                    Text(
                        text = "Registrarse",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5D6D4E)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onBackToLoginClick
                ) {
                    Text(
                        text = "¿Ya tienes cuenta? Iniciar sesión",
                        fontSize = 13.sp,
                        color = Color(0xFFE57373)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegistroUsuarioScreenPreview() {
    MaterialTheme {
        RegistroUsuarioScreen(
            onRegisterClick = { _, _, _, _, _, _, _ -> },
            onBackToLoginClick = {}
        )
    }
}
package com.example.myapplication.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun LoginScreen(
    onLoginClick: (username: String, password: String, role: String) -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var roleSelected by remember { mutableStateOf("Técnico") }
    var expandedRole by remember { mutableStateOf(false) }

    val roles = listOf("Admin", "Técnico")

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "☰",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Inicio de sesión, por favor",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF666666)
                    )

                    Text(
                        text = "ingresar tu contraseña.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF666666)
                    )

                    Spacer(modifier = Modifier.height(42.dp))

                    Text(
                        text = "Usuario",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
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

                    Spacer(modifier = Modifier.height(22.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { expandedRole = true },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.width(130.dp)
                        ) {
                            Text(
                                text = roleSelected,
                                fontSize = 13.sp,
                                color = Color.Black
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "›",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        DropdownMenu(
                            expanded = expandedRole,
                            onDismissRequest = { expandedRole = false }
                        ) {
                            roles.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role) },
                                    onClick = {
                                        roleSelected = role
                                        expandedRole = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Contraseña",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
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

                    Spacer(modifier = Modifier.height(20.dp))

                    TextButton(
                        onClick = onForgotPasswordClick
                    ) {
                        Text(
                            text = "Olvidé mi contraseña",
                            fontSize = 13.sp,
                            color = Color(0xFFE57373)
                        )
                    }

                    TextButton(
                        onClick = onRegisterClick
                    ) {
                        Text(
                            text = "¿No tienes cuenta? Regístrate",
                            fontSize = 13.sp,
                            color = Color(0xFFE57373)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            onLoginClick(
                                username.trim(),
                                password.trim(),
                                roleSelected
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(48.dp),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC5E1A5)
                        )
                    ) {
                        Text(
                            text = "Ingresar",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D6D4E)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreen(
            onLoginClick = { _, _, _ -> },
            onRegisterClick = {},
            onForgotPasswordClick = {},
            onMenuClick = {}
        )
    }
}
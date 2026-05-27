package com.example.myapplication.local.info


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp

import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PerfilUsuarioScreen(
    database: AppDatabase,
    idUser: Long,
    nombreUsuario: String,
    rolUsuario: String = "",
    onBackClick: () -> Unit,
    onPerfilActualizado: (UserEntity) -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var usuarioActual by remember {
        mutableStateOf<UserEntity?>(null)
    }

    var firstName by remember {
        mutableStateOf("")
    }

    var lastName by remember {
        mutableStateOf("")
    }

    var username by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var role by remember {
        mutableStateOf("")
    }

    var cargando by remember {
        mutableStateOf(true)
    }

    var guardando by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(idUser) {
        cargando = true
        error = null

        try {
            val usuarioDb = withContext(Dispatchers.IO) {
                database.userDao().getUserById(idUser)
            }

            if (usuarioDb == null) {
                error = "No se encontró el usuario."
            } else {
                usuarioActual = usuarioDb
                firstName = usuarioDb.firstName
                lastName = usuarioDb.lastName ?: ""
                username = usuarioDb.username
                email = usuarioDb.email
                password = usuarioDb.password
                role = rolUsuario
            }
        } catch (e: Exception) {
            error = "Error al cargar usuario: ${e.message}"
        } finally {
            cargando = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F8F1))
    ) {
        EncabezadoApp(
            nombreUsuario = nombreUsuario,
            rolUsuario = rolUsuario,
            onPerfilClick = onPerfilClick,
            onMonitoreosClick = onMonitoreosClick,
            onAdminClick = onAdminClick
        )

        when {
            cargando -> {
                EstadoPerfilUsuario(
                    texto = "Cargando perfil..."
                )
            }

            error != null -> {
                EstadoPerfilUsuario(
                    texto = error ?: "Error desconocido",
                    esError = true
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Perfil del usuario",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF123D1F),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Edita tus datos personales. El rol está protegido.",
                        fontSize = 12.sp,
                        color = Color(0xFF607064),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = {
                                    firstName = it
                                },
                                label = {
                                    Text("Nombre")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = {
                                    lastName = it
                                },
                                label = {
                                    Text("Apellido")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = it
                                },
                                label = {
                                    Text("Usuario")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                },
                                label = {
                                    Text("Correo")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                },
                                label = {
                                    Text("Password")
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE8F5E9)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Rol protegido",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF607064)
                                    )

                                    Text(
                                        text = role,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1B5E20)
                                    )

                                    Text(
                                        text = "Este campo no se puede modificar desde el perfil.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF607064)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (guardando) return@Button

                            val usuarioBase = usuarioActual

                            val firstNameLimpio = firstName.trim()
                            val lastNameLimpio = lastName.trim()
                            val usernameLimpio = username.trim()
                            val emailLimpio = email.trim()
                            val passwordLimpio = password.trim()

                            if (usuarioBase == null) {
                                Toast.makeText(
                                    context,
                                    "No hay usuario cargado",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            if (
                                firstNameLimpio.isBlank() ||
                                lastNameLimpio.isBlank() ||
                                usernameLimpio.isBlank() ||
                                emailLimpio.isBlank() ||
                                passwordLimpio.isBlank()
                            ) {
                                Toast.makeText(
                                    context,
                                    "Completa todos los campos",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            guardando = true

                            coroutineScope.launch {
                                try {
                                    val existeUsername = withContext(Dispatchers.IO) {
                                        database.userDao().existeUsernameEnOtroUsuario(
                                            username = usernameLimpio,
                                            idUser = idUser
                                        )
                                    }

                                    if (existeUsername > 0) {
                                        Toast.makeText(
                                            context,
                                            "Ese usuario ya existe",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }

                                    val existeEmail = withContext(Dispatchers.IO) {
                                        database.userDao().existeEmailEnOtroUsuario(
                                            email = emailLimpio,
                                            idUser = idUser
                                        )
                                    }

                                    if (existeEmail > 0) {
                                        Toast.makeText(
                                            context,
                                            "Ese correo ya existe",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }

                                    val filas = withContext(Dispatchers.IO) {
                                        database.userDao().actualizarPerfilUsuarioSinRol(
                                            idUser = idUser,
                                            firstName = firstNameLimpio,
                                            lastName = lastNameLimpio,
                                            username = usernameLimpio,
                                            email = emailLimpio,
                                            password = passwordLimpio
                                        )
                                    }

                                    if (filas > 0) {
                                        val usuarioActualizado = withContext(Dispatchers.IO) {
                                            database.userDao().getUserById(idUser)
                                        }

                                        if (usuarioActualizado != null) {
                                            usuarioActual = usuarioActualizado
                                            onPerfilActualizado(usuarioActualizado)
                                        }

                                        Toast.makeText(
                                            context,
                                            "Perfil actualizado correctamente",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        onBackClick()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "No se actualizó ningún usuario",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error al actualizar: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    guardando = false
                                }
                            }
                        },
                        enabled = !guardando,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B5E20),
                            disabledContainerColor = Color(0xFF9E9E9E)
                        )
                    ) {
                        Text(
                            text = if (guardando) {
                                "Guardando..."
                            } else {
                                "Guardar cambios"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onBackClick,
                        enabled = !guardando,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E0)
                        )
                    ) {
                        Text(
                            text = "Cancelar",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EstadoPerfilUsuario(
    texto: String,
    esError: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = texto,
            color = if (esError) Color(0xFFB00020) else Color(0xFF1B5E20),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

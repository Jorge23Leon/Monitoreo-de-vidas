package com.example.myapplication.local

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PantallaActual {
    LOGIN,
    REGISTRO,
    INFORMACION,
    CONTACTO
}

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(applicationContext)

        setContent {
            MaterialTheme {

                val coroutineScope = rememberCoroutineScope()

                var pantallaActual by rememberSaveable {
                    mutableStateOf(PantallaActual.LOGIN)
                }

                var cargando by remember {
                    mutableStateOf(false)
                }

                LaunchedEffect(Unit) {
                    insertarUsuariosDePruebaSeguro()
                }

                when (pantallaActual) {

                    PantallaActual.LOGIN -> {
                        LoginScreen(
                            onLoginClick = { usernameInput, passwordInput, roleInput ->

                                val username = usernameInput.trim()
                                val password = passwordInput.trim()
                                val role = roleInput.trim()

                                if (username.isBlank() || password.isBlank()) {
                                    mostrarMensaje("Ingresa usuario y contraseña")
                                } else if (!cargando) {

                                    cargando = true

                                    coroutineScope.launch {
                                        try {
                                            val user = withContext(Dispatchers.IO) {
                                                database.userDao().login(
                                                    username = username,
                                                    password = password,
                                                    role = role
                                                )
                                            }

                                            if (user != null) {
                                                mostrarMensaje("Bienvenido ${user.first_name}")

                                                when (user.role) {
                                                    "Admin" -> {
                                                        mostrarMensaje("Entrando como administrador")

                                                        /*
                                                         Aquí después puedes mandar a tu pantalla de administrador.
                                                         Ejemplo:
                                                         pantallaActual = PantallaActual.ADMIN
                                                        */
                                                    }

                                                    "Técnico" -> {
                                                        mostrarMensaje("Entrando como técnico")

                                                        /*
                                                         Aquí después puedes mandar a tu pantalla de técnico.
                                                         Ejemplo:
                                                         pantallaActual = PantallaActual.TECNICO
                                                        */
                                                    }

                                                    else -> {
                                                        mostrarMensaje("Rol no reconocido")
                                                    }
                                                }

                                            } else {
                                                mostrarMensaje("Usuario, contraseña o rol incorrecto")
                                            }

                                        } catch (e: Exception) {
                                            mostrarMensaje("Error al iniciar sesión: ${e.message}")
                                        } finally {
                                            cargando = false
                                        }
                                    }
                                }
                            },

                            onRegisterClick = {
                                pantallaActual = PantallaActual.REGISTRO
                            },

                            onForgotPasswordClick = {
                                mostrarMensaje("Aquí irá la recuperación de contraseña")
                            },

                            onInformationClick = {
                                pantallaActual = PantallaActual.INFORMACION
                            },

                            onContactClick = {
                                pantallaActual = PantallaActual.CONTACTO
                            }
                        )
                    }

                    PantallaActual.REGISTRO -> {
                        RegistroUsuarioScreen(
                            onRegisterClick = { firstNameInput, lastnameInput, usernameInput, emailInput, passwordInput, confirmPasswordInput, roleInput ->

                                val firstName = firstNameInput.trim()
                                val lastname = lastnameInput.trim()
                                val username = usernameInput.trim()
                                val email = emailInput.trim()
                                val password = passwordInput.trim()
                                val confirmPassword = confirmPasswordInput.trim()
                                val role = roleInput.trim()

                                if (
                                    firstName.isBlank() ||
                                    username.isBlank() ||
                                    email.isBlank() ||
                                    password.isBlank() ||
                                    confirmPassword.isBlank()
                                ) {
                                    mostrarMensaje("Completa todos los campos obligatorios")
                                } else if (password != confirmPassword) {
                                    mostrarMensaje("Las contraseñas no coinciden")
                                } else if (!cargando) {

                                    cargando = true

                                    coroutineScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                database.userDao().insertUser(
                                                    UserEntity(
                                                        first_name = firstName,
                                                        lastname = lastname.ifBlank { null },
                                                        username = username,
                                                        email = email,
                                                        password = password,
                                                        role = role
                                                    )
                                                )
                                            }

                                            mostrarMensaje("Usuario registrado correctamente")
                                            pantallaActual = PantallaActual.LOGIN

                                        } catch (e: Exception) {
                                            mostrarMensaje("No se pudo registrar. Revisa si el usuario o correo ya existen")
                                        } finally {
                                            cargando = false
                                        }
                                    }
                                }
                            },

                            onBackToLoginClick = {
                                pantallaActual = PantallaActual.LOGIN
                            }
                        )
                    }

                    PantallaActual.INFORMACION -> {
                        InformacionScreen(
                            onCloseClick = {
                                pantallaActual = PantallaActual.LOGIN
                            }
                        )
                    }

                    PantallaActual.CONTACTO -> {
                        ContactoScreen(
                            onCloseClick = {
                                pantallaActual = PantallaActual.LOGIN
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun insertarUsuariosDePruebaSeguro() {
        withContext(Dispatchers.IO) {
            try {
                val totalUsuarios = database.userDao().countUsers()

                if (totalUsuarios == 0) {
                    database.userDao().insertUser(
                        UserEntity(
                            first_name = "Jorge",
                            lastname = "Sandoval",
                            username = "jorge",
                            email = "jorge@test.com",
                            password = "1234",
                            role = "Admin"
                        )
                    )

                    database.userDao().insertUser(
                        UserEntity(
                            first_name = "Técnico",
                            lastname = "Prueba",
                            username = "tecnico",
                            email = "tecnico@test.com",
                            password = "1234",
                            role = "Técnico"
                        )
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(
            this@MainActivity,
            mensaje,
            Toast.LENGTH_SHORT
        ).show()
    }
}
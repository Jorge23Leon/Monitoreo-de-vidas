package com.example.myapplication.local

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PantallaActual {
    LOGIN,
    REGISTRO
}

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(this)

        insertarUsuariosDePrueba()

        setContent {
            MaterialTheme {

                var pantallaActual by remember {
                    mutableStateOf(PantallaActual.LOGIN)
                }

                when (pantallaActual) {

                    PantallaActual.LOGIN -> {
                        LoginScreen(
                            onLoginClick = { username, password, role ->

                                if (username.isBlank() || password.isBlank()) {
                                    Toast.makeText(
                                        this,
                                        "Ingresa usuario y contraseña",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@LoginScreen
                                }

                                lifecycleScope.launch {
                                    val user = withContext(Dispatchers.IO) {
                                        database.userDao().login(
                                            username = username,
                                            password = password,
                                            role = role
                                        )
                                    }

                                    if (user != null) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Bienvenido ${user.first_name}",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        when (user.role) {
                                            "Admin" -> {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Entrando como administrador",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                // Aquí después mandas a la pantalla Admin
                                            }

                                            "Técnico" -> {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Entrando como técnico",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                // Aquí después mandas a la pantalla Técnico
                                            }

                                            else -> {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Rol no reconocido",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }

                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Usuario, contraseña o rol incorrecto",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },

                            onRegisterClick = {
                                pantallaActual = PantallaActual.REGISTRO
                            },

                            onForgotPasswordClick = {
                                Toast.makeText(
                                    this,
                                    "Aquí irá la recuperación de contraseña",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },

                            onMenuClick = {
                                Toast.makeText(
                                    this,
                                    "Menú de opciones",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    PantallaActual.REGISTRO -> {
                        RegistroUsuarioScreen(
                            onRegisterClick = { firstName, lastname, username, email, password, confirmPassword, role ->

                                if (
                                    firstName.isBlank() ||
                                    username.isBlank() ||
                                    email.isBlank() ||
                                    password.isBlank() ||
                                    confirmPassword.isBlank()
                                ) {
                                    Toast.makeText(
                                        this,
                                        "Completa todos los campos obligatorios",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@RegistroUsuarioScreen
                                }

                                if (password != confirmPassword) {
                                    Toast.makeText(
                                        this,
                                        "Las contraseñas no coinciden",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@RegistroUsuarioScreen
                                }

                                lifecycleScope.launch {
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

                                        Toast.makeText(
                                            this@MainActivity,
                                            "Usuario registrado correctamente",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        pantallaActual = PantallaActual.LOGIN

                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "No se pudo registrar. Revisa si el usuario o correo ya existen",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },

                            onBackToLoginClick = {
                                pantallaActual = PantallaActual.LOGIN
                            }
                        )
                    }
                }
            }
        }
    }

    private fun insertarUsuariosDePrueba() {
        lifecycleScope.launch(Dispatchers.IO) {
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
        }
    }
}
package com.example.myapplication.local.core

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.local.entities.AppDatabase
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(applicationContext)

        setContent {
            MaterialTheme {
                val mainViewModel: MainViewModel = viewModel()
                val uiState = mainViewModel.uiState
                var mostrarDialogCerrarSesionBack by remember {
                    mutableStateOf(false)
                }

                LaunchedEffect(uiState.mensaje) {
                    uiState.mensaje?.let { mensaje ->
                        Toast.makeText(
                            this@MainActivity,
                            mensaje,
                            Toast.LENGTH_SHORT
                        ).show()

                        mainViewModel.limpiarMensaje()
                    }
                }

                BackHandler(
                    enabled = uiState.pantallaActual != PantallaActual.LOGIN
                ) {
                    when (uiState.pantallaActual) {
                        PantallaActual.SELECCION_PARENT_CIA,
                        PantallaActual.SELECCION_CIA -> {
                            mostrarDialogCerrarSesionBack = true
                        }

                        PantallaActual.LISTA_MONITOREOS -> {
                            val sesion = uiState.usuarioSesion

                            if (sesion != null && (sesion.esTecnico || sesion.esInvitado)) {
                                mostrarDialogCerrarSesionBack = true
                            } else {
                                mainViewModel.manejarBack()
                            }
                        }

                        else -> {
                            mainViewModel.manejarBack()
                        }
                    }
                }
                if (mostrarDialogCerrarSesionBack) {
                    AlertDialog(
                        onDismissRequest = {
                            mostrarDialogCerrarSesionBack = false
                        },
                        title = {
                            Text(
                                text = "Cerrar sesión",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "¿Estás seguro de que quieres cerrar sesión?"
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    mostrarDialogCerrarSesionBack = false
                                    mainViewModel.cerrarSesion()
                                }
                            ) {
                                Text(
                                    text = "Sí, cerrar sesión",
                                    color = Color(0xFFB3261E),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    mostrarDialogCerrarSesionBack = false
                                }
                            ) {
                                Text(
                                    text = "Cancelar",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    )
                }

                MainNavegacion(
                    database = database,
                    mainViewModel = mainViewModel,
                    uiState = uiState
                )
            }
        }
    }
}
package com.example.myapplication.local.core

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.local.aspersion.ui.AspersionViewModel
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.ndvi.ui.NdviViewModel


class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        database =
            AppDatabase.getDatabase(
                applicationContext
            )


        setContent {

            MaterialTheme {


                // =================================================
                // VIEWMODELS
                // =================================================

                val mainViewModel:
                        MainViewModel =
                    viewModel()


                val aspersionViewModel:
                        AspersionViewModel =
                    viewModel()


                /**
                 * ViewModel independiente para NDVI.
                 *
                 * Así NDVI no aumenta el tamaño ni
                 * la responsabilidad de MainViewModel.
                 */
                val ndviViewModel:
                        NdviViewModel =
                    viewModel()


                val uiState =
                    mainViewModel.uiState


                // =================================================
                // SALIR DE LA APP
                // =================================================

                var mostrarDialogSalirApp
                        by remember {

                            mutableStateOf(false)
                        }


                // =================================================
                // MENSAJES GENERALES
                // =================================================

                LaunchedEffect(
                    uiState.mensaje
                ) {

                    uiState.mensaje
                        ?.let { mensaje ->

                            Toast.makeText(
                                this@MainActivity,
                                mensaje,
                                Toast.LENGTH_SHORT
                            ).show()


                            mainViewModel
                                .limpiarMensaje()
                        }
                }


                // =================================================
                // BOTÓN ATRÁS DE ANDROID
                // =================================================

                BackHandler(

                    enabled =
                    uiState.pantallaActual !=
                            PantallaActual.LOGIN &&

                            uiState.pantallaActual !=
                            PantallaActual.CARGANDO_SESION

                ) {

                    when (
                        uiState.pantallaActual
                    ) {

                        PantallaActual.SELECCION_PARENT_CIA,
                        PantallaActual.SELECCION_CIA,
                        PantallaActual.MODULOS_TRABAJO -> {

                            mostrarDialogSalirApp =
                                true
                        }


                        PantallaActual.RECUPERAR_PASSWORD -> {

                            mainViewModel.irA(
                                PantallaActual.LOGIN
                            )
                        }


                        PantallaActual.LISTA_MONITOREOS -> {

                            mainViewModel
                                .manejarBack()
                        }


                        PantallaActual.NDVI_LISTA -> {
                            ndviViewModel.clearSelectedSession()
                            mainViewModel.abrirModulosTrabajo()
                        }

                        PantallaActual.NDVI_MAPA -> {

                            mainViewModel.irA(
                                PantallaActual.NDVI_DETALLE
                            )
                        }


                        PantallaActual.NDVI_DETALLE -> {
                            ndviViewModel.clearSelectedSession()
                            mainViewModel.irA(
                                PantallaActual.NDVI_LISTA
                            )
                        }


                        else -> {

                            mainViewModel
                                .manejarBack()
                        }
                    }
                }


                // =================================================
                // DIÁLOGO SALIR
                // =================================================

                if (
                    mostrarDialogSalirApp
                ) {

                    AlertDialog(

                        onDismissRequest = {

                            mostrarDialogSalirApp =
                                false
                        },


                        title = {

                            Text(
                                text =
                                "Salir de la app",
                                fontWeight =
                                FontWeight.Bold
                            )
                        },


                        text = {

                            Text(
                                text =
                                "¿Estás seguro de que quieres salir de la app?"
                            )
                        },


                        confirmButton = {

                            TextButton(

                                onClick = {

                                    mostrarDialogSalirApp =
                                        false

                                    finish()
                                }

                            ) {

                                Text(
                                    text =
                                    "Sí, salir",
                                    color =
                                    Color(
                                        0xFFB3261E
                                    ),
                                    fontWeight =
                                    FontWeight.Bold
                                )
                            }
                        },


                        dismissButton = {

                            TextButton(

                                onClick = {

                                    mostrarDialogSalirApp =
                                        false
                                }

                            ) {

                                Text(
                                    text =
                                    "Cancelar",
                                    fontWeight =
                                    FontWeight.Bold
                                )
                            }
                        }
                    )
                }


                // =================================================
                // NAVEGACIÓN PRINCIPAL
                // =================================================

                MainNavegacion(

                    database =
                    database,

                    mainViewModel =
                    mainViewModel,

                    aspersionViewModel =
                    aspersionViewModel,

                    ndviViewModel =
                    ndviViewModel,

                    uiState =
                    uiState
                )
            }
        }
    }
}
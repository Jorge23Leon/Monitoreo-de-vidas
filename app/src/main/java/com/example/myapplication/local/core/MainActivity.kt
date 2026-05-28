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

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(applicationContext)

        setContent {
            MaterialTheme {
                val mainViewModel: MainViewModel = viewModel()
                val uiState = mainViewModel.uiState

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
                    mainViewModel.manejarBack()
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

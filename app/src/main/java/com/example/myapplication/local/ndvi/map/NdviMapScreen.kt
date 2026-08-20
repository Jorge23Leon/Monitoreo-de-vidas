package com.example.myapplication.local.ndvi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.ndvi.map.NdviMapCanvas
import com.example.myapplication.local.ndvi.model.NdviIndex


private val NdviMapGreen = Color(0xFF176B2C)
private val NdviMapGreenLight = Color(0xFFE8F5E9)
private val NdviMapBackground = Color(0xFFF7FAF5)
private val NdviMapAmberBackground = Color(0xFFFFF8E1)
private val NdviMapAmberText = Color(0xFF8D6E00)


@Composable
fun NdviMapScreen(
    uiState: NdviUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onIndexSelected: (NdviIndex) -> Unit
) {

    val session = uiState.selectedSession

    if (session == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NdviMapBackground),
            contentAlignment = Alignment.Center
        ) {
            TextButton(onClick = onBackClick) {
                Text("Regresar")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NdviMapBackground)
            .navigationBarsPadding()
    ) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = Color.White,
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBackClick) {
                        Text(
                            text = "←",
                            fontSize = 24.sp,
                            color = NdviMapGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Mapa NDVI",
                            color = NdviMapGreen,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Superficie interpolada con los puntos de la sesión",
                            color = Color(0xFF6D756F),
                            fontSize = 10.sp
                        )
                    }

                    TextButton(
                        onClick = onRefreshClick,
                        enabled = !uiState.syncingSelectedSession
                    ) {
                        if (uiState.syncingSelectedSession) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(6.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "⟳",
                                color = NdviMapGreen,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapInfoChip("${uiState.points.size} puntos")
                    MapInfoChip(uiState.selectedIndex.label)
                    MapInfoChip("Interpolación local")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 9.dp, bottom = 10.dp)
        ) {
            Text(
                text = "Índice vegetativo",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = NdviMapGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item { Spacer(Modifier.padding(start = 7.dp)) }

                items(
                    items = uiState.availableIndices,
                    key = { it.apiKey }
                ) { index ->
                    val selected = index == uiState.selectedIndex

                    FilterChip(
                        selected = selected,
                        onClick = { onIndexSelected(index) },
                        label = {
                            Text(
                                text = index.label,
                                fontWeight = if (selected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NdviMapGreenLight,
                            selectedLabelColor = NdviMapGreen
                        )
                    )
                }

                item { Spacer(Modifier.padding(end = 7.dp)) }
            }
        }

        if (uiState.loadingVariableConfig) {
            ConfigBanner(
                text = "Cargando bandas y colores de la CIA...",
                loading = true
            )
        } else if (!uiState.variableConfigError.isNullOrBlank()) {
            ConfigBanner(
                text = "No se pudo leer la configuración de colores. " +
                        "El mapa usa clasificación automática por cuantiles.",
                loading = false
            )
        }

        uiState.message
            ?.takeIf(String::isNotBlank)
            ?.let { message ->
                MapMessageBanner(message)
            }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 8.dp
                ),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            when {
                uiState.points.isEmpty() && uiState.syncingSelectedSession -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Descargando puntos NDVI...",
                                color = Color(0xFF657067)
                            )
                        }
                    }
                }

                uiState.points.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sin puntos NDVI",
                            color = NdviMapGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                else -> {
                    NdviMapCanvas(
                        points = uiState.points,
                        index = uiState.selectedIndex,
                        variableConfigJson = uiState.variableConfigJson,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}


@Composable
private fun MapInfoChip(
    label: String
) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = NdviMapGreenLight
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 5.dp
            ),
            color = NdviMapGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun ConfigBanner(
    text: String,
    loading: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = NdviMapAmberBackground
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            Text(
                text = text,
                color = NdviMapAmberText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


@Composable
private fun MapMessageBanner(
    text: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = NdviMapGreenLight
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            color = NdviMapGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

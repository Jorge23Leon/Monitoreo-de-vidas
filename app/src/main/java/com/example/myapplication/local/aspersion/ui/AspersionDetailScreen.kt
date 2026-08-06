package com.example.myapplication.local.aspersion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.aspersion.map.AspersionMapCanvas
import com.example.myapplication.local.common.EncabezadoApp

@Composable
fun AspersionDetailScreen(
    state: AspersionUiState,
    nombreUsuario: String,
    rolUsuario: String,
    onLayerSelected: (AspersionLayer) -> Unit,
    onToggleBucket: (String) -> Unit,
    onSyncSession: () -> Unit,
    onOpenFullMap: () -> Unit,
    onBack: () -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)?,
    onCerrarSesionClick: () -> Unit
) {
    val visiblePoints = remember(
        state.points,
        state.selectedLayer,
        state.legendItems,
        state.visibleBucketKeys
    ) {
        visibleAspersionPoints(
            points = state.points,
            layer = state.selectedLayer,
            legendItems = state.legendItems,
            visibleBucketKeys = state.visibleBucketKeys
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AspersionBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        EncabezadoApp(
            nombreUsuario = nombreUsuario,
            rolUsuario = rolUsuario,
            onPerfilClick = onPerfilClick,
            onMonitoreosClick = onMonitoreosClick,
            onAdminClick = onAdminClick,
            onCambiarCiaClick = onCambiarCiaClick,
            onCerrarSesionClick = onCerrarSesionClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("‹ Sesiones")
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = state.selectedPlotName
                        ?: state.selectedSession?.let { session ->
                            sessionTitle(session, state.plotNamesById)
                        }
                        ?: "Detalle de aspersión",
                    color = AspersionGreenDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.selectedProgramName
                        ?: "Consulta de aplicación",
                    color = Color(0xFF68736B),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onSyncSession,
                enabled = !state.syncingSelectedSession,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AspersionGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.syncingSelectedSession) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar aspersión"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(9.dp))

        if (state.selectedSession == null && state.syncingSelectedSession) {
            AspersionLoadingCard(
                message = "Descargando puntos y estadísticas…"
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AspersionSummaryGrid(state)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mapa de aplicación",
                                    color = AspersionText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${visiblePoints.size} de ${state.points.size} puntos visibles",
                                    color = Color(0xFF6C766F),
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = onOpenFullMap,
                                enabled = state.points.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AspersionBlue
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Pantalla completa",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        AspersionLayerSelector(
                            selectedLayer = state.selectedLayer,
                            onLayerSelected = onLayerSelected
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(365.dp)
                                .background(
                                    color = Color(0xFFDDE7D7),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            AspersionMapCanvas(
                                points = visiblePoints,
                                vertices = state.plotVertices,
                                layer = state.selectedLayer,
                                legendItems = state.legendItems,
                                plotName = state.selectedPlotName ?: "Parcela",
                                modifier = Modifier.fillMaxSize()
                            )

                            if (state.syncingSelectedSession) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color.White
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.height(22.dp),
                                                strokeWidth = 2.dp,
                                                color = AspersionGreen
                                            )
                                            Text(
                                                text = "  Actualizando mapa…",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Toca una categoría para mostrarla u ocultarla.",
                            modifier = Modifier.padding(top = 7.dp),
                            color = Color(0xFF6C766F),
                            fontSize = 10.sp
                        )

                        AspersionLegend(
                            items = state.legendItems,
                            visibleKeys = state.visibleBucketKeys,
                            onToggle = onToggleBucket,
                            compact = true
                        )
                    }
                }

                AspersionRangeSummary(state)

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun AspersionSummaryGrid(
    state: AspersionUiState
) {
    val stats = state.stats

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspersionMetricCard(
                title = "SUPERFICIE",
                value = "${formatNumber(stats?.areaTotalHa, 4)} ha",
                subtitle = "${stats?.pointsCount ?: state.points.size} puntos",
                accent = AspersionGreen,
                modifier = Modifier.weight(1f)
            )
            AspersionMetricCard(
                title = "OBJETIVO PROMEDIO",
                value = "${formatNumber(stats?.meanTargetL, 1)} L/ha",
                subtitle = "Dosis programada",
                accent = AspersionBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspersionMetricCard(
                title = "APLICACIÓN REAL",
                value = "${formatNumber(stats?.meanAppliedL, 1)} L/ha",
                subtitle = stats?.ratioApplied?.let { ratio ->
                    "${formatNumber((ratio - 1.0) * 100.0, 2)}% contra el objetivo"
                } ?: "Sin comparación",
                accent = Color(0xFF7A4EB3),
                modifier = Modifier.weight(1f)
            )
            AspersionMetricCard(
                title = "DENTRO DEL RANGO",
                value = "${formatNumber(stats?.pctInRange, 2)}%",
                subtitle = "Bajo ${formatNumber(stats?.pctBelow, 1)}% · " +
                        "Sobre ${formatNumber(stats?.pctAbove, 1)}%",
                accent = Color(0xFF4B8E12),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AspersionRangeSummary(
    state: AspersionUiState
) {
    val expectedKeys = when (state.selectedLayer) {
        AspersionLayer.APPLICATION -> setOf("application", "applied_rate_l")
        AspersionLayer.SPEED -> setOf("speed", "speed_kmh")
        AspersionLayer.TARGET_RATE -> setOf("target_rate", "target_rate_l")
        AspersionLayer.BOOM_PRESSURE -> setOf("boom_pressure", "boom_pressure_bar")
        AspersionLayer.LIQUID_FLOW -> setOf("liquid_flow", "liquid_flow_ls")
        AspersionLayer.RATE_QUALITY -> setOf("rate_quality")
        AspersionLayer.PRODUCTIVITY -> setOf("production", "production_hah")
    }

    val variable = state.variableStats.firstOrNull { item ->
        item.variableKey in expectedKeys
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F5ED))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = "Resumen de ${state.selectedLayer.label}",
                color = AspersionGreenDark,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(5.dp))

            if (variable == null) {
                Text(
                    text = "El mapa calcula sus rangos con los puntos guardados. " +
                            "La estadística resumida no está disponible para esta variable.",
                    color = Color(0xFF667168),
                    fontSize = 11.sp
                )
            } else {
                Text(
                    text = "Promedio ${formatNumber(variable.meanValue, 3)} · " +
                            "Mínimo ${formatNumber(variable.minValue, 3)} · " +
                            "Máximo ${formatNumber(variable.maxValue, 3)} · " +
                            "Desv. ${formatNumber(variable.stddev, 3)}",
                    color = Color(0xFF526057),
                    fontSize = 11.sp
                )
            }
        }
    }
}
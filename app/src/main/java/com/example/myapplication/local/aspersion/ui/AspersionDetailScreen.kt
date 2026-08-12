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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Divider
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
    onRefreshReport: () -> Unit,
    onViewReport: () -> Unit,
    onDownloadReport: () -> Unit,
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

                AspersionReportCard(
                    state = state,
                    onRefreshReport = onRefreshReport,
                    onViewReport = onViewReport,
                    onDownloadReport = onDownloadReport
                )

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun AspersionReportCard(
    state: AspersionUiState,
    onRefreshReport: () -> Unit,
    onViewReport: () -> Unit,
    onDownloadReport: () -> Unit
) {
    val report = state.sessionReport

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = "Reporte de sesión",
                        color = AspersionText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Consulta y descarga el PDF sin modificarlo.",
                        color = Color(0xFF6C766F),
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = onRefreshReport,
                    enabled = !state.loadingSessionReport &&
                            !state.downloadingSessionReport,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.loadingSessionReport) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(17.dp),
                            strokeWidth = 2.dp,
                            color = AspersionGreen
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar reporte"
                        )
                    }
                }
            }

            Divider(color = Color(0xFFE7EBE8))

            when {
                state.loadingSessionReport -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = AspersionGreen
                        )
                        Text(
                            text = "  Consultando reporte…",
                            color = Color(0xFF59655D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                state.sessionReportError != null -> {
                    Text(
                        text = state.sessionReportError,
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp
                    )
                }

                report == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFF6F8F6),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(13.dp)
                    ) {
                        Text(
                            text = "Reporte aún no disponible",
                            color = AspersionGreenDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Cuando el reporte se genere desde el sistema web, " +
                                    "aparecerá aquí para consulta y descarga.",
                            color = Color(0xFF667168),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFF3F8F4),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = report.activityLabel,
                            color = AspersionGreenDark,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Estado: ${report.statusDisplay}",
                            color = Color(0xFF526057),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        report.reportDate?.takeIf(String::isNotBlank)?.let { date ->
                            Text(
                                text = "Fecha del reporte: $date",
                                color = Color(0xFF667168),
                                fontSize = 11.sp
                            )
                        }
                        if (!report.hasMapSnapshot) {
                            Text(
                                text = "El servidor puede impedir el PDF hasta que el reporte " +
                                        "tenga la captura del mapa.",
                                color = Color(0xFF9A6200),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onViewReport,
                            enabled = !state.downloadingSessionReport,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AspersionBlue
                            ),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            if (state.downloadingSessionReport) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(17.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                            Text(
                                text = "  Ver PDF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onDownloadReport,
                            enabled = !state.downloadingSessionReport,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = AspersionGreenDark
                            )
                            Text(
                                text = "  Descargar",
                                color = AspersionGreenDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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
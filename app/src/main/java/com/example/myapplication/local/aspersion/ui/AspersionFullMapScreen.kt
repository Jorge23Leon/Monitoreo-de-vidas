package com.example.myapplication.local.aspersion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.aspersion.map.AspersionMapCanvas

@Composable
fun AspersionFullMapScreen(
    state: AspersionUiState,
    onLayerSelected: (AspersionLayer) -> Unit,
    onToggleBucket: (String) -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    onToggleFilters: () -> Unit,
    onBack: () -> Unit
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

    val visibleArea = remember(visiblePoints) {
        visiblePoints.sumOf { point ->
            point.areaHa ?: 0.0
        }
    }
    val visiblePercent = remember(visiblePoints.size, state.points.size) {
        if (state.points.isEmpty()) {
            0.0
        } else {
            visiblePoints.size.toDouble() * 100.0 / state.points.size.toDouble()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111A14))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE9EEE9),
                    contentColor = AspersionText
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "‹ Volver",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = state.selectedPlotName ?: "Mapa de aspersión",
                    color = AspersionGreenDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.selectedLayer.label} · " +
                            "${visiblePoints.size}/${state.points.size} puntos · " +
                            "${formatNumber(visiblePercent, 1)}% visible · " +
                            "${formatNumber(visibleArea, 4)} ha",
                    color = Color(0xFF657068),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onToggleFilters,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AspersionGreen
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (state.filtersExpanded) {
                        "Ocultar filtros"
                    } else {
                        "Mostrar filtros"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        if (state.filtersExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp)
            ) {
                AspersionLayerSelector(
                    selectedLayer = state.selectedLayer,
                    onLayerSelected = onLayerSelected
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AspersionMapCanvas(
                points = visiblePoints,
                vertices = state.plotVertices,
                layer = state.selectedLayer,
                legendItems = state.legendItems,
                plotName = state.selectedPlotName ?: "Parcela",
                modifier = Modifier.fillMaxSize()
            )

            if (!state.filtersExpanded) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.92f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "${state.selectedLayer.label} · " +
                                "${visiblePoints.size} puntos · " +
                                "${formatNumber(visiblePercent, 1)}% visible",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = AspersionGreenDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (state.filtersExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Toca un rango para mostrarlo u ocultarlo",
                        color = Color(0xFF68736B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row {
                        Text(
                            text = "Mostrar todo",
                            modifier = Modifier
                                .clickable(onClick = onShowAll)
                                .background(
                                    AspersionGreen.copy(alpha = 0.10f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            color = AspersionGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Quitar todos",
                            modifier = Modifier
                                .clickable(onClick = onHideAll)
                                .background(
                                    Color(0xFFB3261E).copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            color = Color(0xFFB3261E),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                AspersionLegend(
                    items = state.legendItems,
                    visibleKeys = state.visibleBucketKeys,
                    onToggle = onToggleBucket,
                    compact = false
                )
            }
        }
    }
}

package com.example.myapplication.local.ndvi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalNdviPointEntity
import com.example.myapplication.local.ndvi.map.NdviMapCanvas
import com.example.myapplication.local.ndvi.model.NdviIndex
import java.text.NumberFormat
import java.util.Locale


private val NdviDetailGreen = Color(0xFF2E7D32)
private val NdviDetailGreenDark = Color(0xFF195C25)
private val NdviDetailGreenSoft = Color(0xFFE8F4E9)
private val NdviDetailBackground = Color(0xFFF4F8F3)
private val NdviDetailTextSecondary = Color(0xFF667068)


/**
 * Pantalla de detalle de UNA sesión NDVI.
 *
 * Aquí mostramos:
 *
 * - selector de índice
 * - estadísticas básicas
 * - botón para abrir el mapa
 */
@Composable
fun NdviDetailScreen(
    uiState: NdviUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onIndexSelected: (NdviIndex) -> Unit,
    onMapClick: () -> Unit
) {

    if (uiState.selectedSession == null) {

        NdviDetailLoading(
            onBackClick = onBackClick,
            syncing = uiState.syncingSelectedSession
        )

        return
    }


    /**
     * Calculamos las estadísticas únicamente
     * del índice actualmente seleccionado.
     */
    val stats = remember(
        uiState.points,
        uiState.selectedIndex
    ) {

        calculateNdviStats(
            points = uiState.points,
            index = uiState.selectedIndex
        )
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF9FCF8),
                        NdviDetailBackground
                    )
                )
            )
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            // =================================================
            // ENCABEZADO
            // =================================================

            NdviDetailHeader(
                syncing = uiState.syncingSelectedSession,
                onBackClick = onBackClick,
                onRefreshClick = onRefreshClick
            )


            // =================================================
            // CONTENIDO
            // =================================================

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = 14.dp
                    )
            ) {


                // =============================================
                // MENSAJES
                // =============================================

                if (!uiState.message.isNullOrBlank()) {

                    Spacer(
                        modifier =
                        Modifier.height(12.dp)
                    )

                    NdviDetailMessage(
                        text = uiState.message,
                        error = false
                    )
                }


                if (!uiState.error.isNullOrBlank()) {

                    Spacer(
                        modifier =
                        Modifier.height(12.dp)
                    )

                    NdviDetailMessage(
                        text = uiState.error,
                        error = true
                    )
                }


                // =============================================
                // SELECTOR DE ÍNDICE
                // =============================================

                Spacer(
                    modifier = Modifier.height(6.dp)
                )


                Text(
                    text = "Índice vegetativo",
                    color = NdviDetailGreenDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )


                Spacer(
                    modifier =
                    Modifier.height(4.dp)
                )


                Text(
                    text =
                    "Selecciona la variable que deseas analizar.",
                    color =
                    NdviDetailTextSecondary,
                    fontSize = 13.sp
                )


                Spacer(
                    modifier =
                    Modifier.height(12.dp)
                )


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                    horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
                ) {

                    uiState.availableIndices
                        .forEach { index ->

                            NdviIndexChip(
                                index = index,
                                selected =
                                uiState.selectedIndex ==
                                        index,
                                onClick = {
                                    onIndexSelected(
                                        index
                                    )
                                }
                            )
                        }
                }


                // =============================================
                // ÍNDICE SELECCIONADO
                // =============================================

                Spacer(
                    modifier =
                    Modifier.height(20.dp)
                )


                Card(
                    modifier =
                    Modifier.fillMaxWidth(),
                    shape =
                    RoundedCornerShape(20.dp),
                    colors =
                    CardDefaults.cardColors(
                        containerColor =
                        Color.White
                    ),
                    elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {

                        Text(
                            text =
                            uiState
                                .selectedIndex
                                .label,
                            color =
                            NdviDetailGreenDark,
                            fontSize = 22.sp,
                            fontWeight =
                            FontWeight.Black
                        )


                        Text(
                            text =
                            "Resumen de los puntos disponibles",
                            color =
                            NdviDetailTextSecondary,
                            fontSize = 12.sp
                        )


                        Spacer(
                            modifier =
                            Modifier.height(17.dp)
                        )


                        if (stats.count == 0) {

                            Text(
                                text =
                                "No hay valores disponibles para este índice.",
                                color =
                                NdviDetailTextSecondary,
                                fontSize = 13.sp
                            )

                        } else {

                            Row(
                                modifier =
                                Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                            ) {

                                NdviStatCard(
                                    modifier =
                                    Modifier.weight(1f),
                                    title = "Promedio",
                                    value =
                                    formatIndexValue(
                                        stats.mean
                                    )
                                )


                                NdviStatCard(
                                    modifier =
                                    Modifier.weight(1f),
                                    title = "Mínimo",
                                    value =
                                    formatIndexValue(
                                        stats.min
                                    )
                                )
                            }


                            Spacer(
                                modifier =
                                Modifier.height(8.dp)
                            )


                            Row(
                                modifier =
                                Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                            ) {

                                NdviStatCard(
                                    modifier =
                                    Modifier.weight(1f),
                                    title = "Máximo",
                                    value =
                                    formatIndexValue(
                                        stats.max
                                    )
                                )


                                NdviStatCard(
                                    modifier =
                                    Modifier.weight(1f),
                                    title = "Valores",
                                    value =
                                    NumberFormat
                                        .getIntegerInstance()
                                        .format(
                                            stats.count
                                        )
                                )
                            }
                        }
                    }
                }


                // =============================================
                // MAPA - VISTA PREVIA
                // =============================================

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 3.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Mapa ${uiState.selectedIndex.label}",
                                    color = NdviDetailGreenDark,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Text(
                                    text = if (uiState.points.isNotEmpty()) {
                                        "${formatPointCount(uiState.points.size)} puntos · Interpolación local"
                                    } else {
                                        "Sin puntos disponibles"
                                    },
                                    color = NdviDetailTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Surface(
                                modifier = Modifier.clickable(
                                    enabled = uiState.points.isNotEmpty(),
                                    onClick = onMapClick
                                ),
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.points.isNotEmpty()) {
                                    NdviDetailGreen
                                } else {
                                    Color(0xFFAAB5AB)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 9.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Map,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Text(
                                        text = "Pantalla completa",
                                        modifier = Modifier.padding(start = 6.dp),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(365.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFDDE7D7)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 0.dp
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    uiState.points.isEmpty() && uiState.syncingSelectedSession -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = NdviDetailGreen,
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "Descargando puntos NDVI...",
                                                color = NdviDetailTextSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    uiState.points.isEmpty() -> {
                                        Text(
                                            text = "Mapa sin puntos",
                                            color = NdviDetailGreenDark,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    else -> {
                                        NdviMapCanvas(
                                            points = uiState.points,
                                            index = uiState.selectedIndex,
                                            variableConfigJson = uiState.variableConfigJson,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (uiState.syncingSelectedSession) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Color.Black.copy(alpha = 0.16f)
                                                    ),
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
                                                            modifier = Modifier.size(22.dp),
                                                            strokeWidth = 2.dp,
                                                            color = NdviDetailGreen
                                                        )
                                                        Text(
                                                            text = "  Actualizando mapa...",
                                                            color = NdviDetailGreenDark,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "El mapa cambia automáticamente al seleccionar NDVI, OSAVI, VARI u otro índice.",
                            modifier = Modifier.padding(top = 7.dp),
                            color = NdviDetailTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }


                Spacer(
                    modifier =
                    Modifier.height(24.dp)
                )
            }
        }
    }
}


// =============================================================
// HEADER
// =============================================================

@Composable
private fun NdviDetailHeader(
    syncing: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit
) {

    Surface(
        color = Color.White,
        shadowElevation = 3.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 7.dp
                ),
            verticalAlignment =
            Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBackClick
            ) {

                Icon(
                    imageVector =
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Regresar",
                    tint = NdviDetailGreenDark
                )
            }


            Column(
                modifier =
                Modifier.weight(1f)
            ) {

                Text(
                    text = "Detalle NDVI",
                    color =
                    NdviDetailGreenDark,
                    fontSize = 20.sp,
                    fontWeight =
                    FontWeight.Black
                )

                Text(
                    text =
                    "Índices vegetativos",
                    color =
                    NdviDetailTextSecondary,
                    fontSize = 11.sp
                )
            }


            if (syncing) {

                CircularProgressIndicator(
                    modifier =
                    Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = NdviDetailGreen
                )

            } else {

                IconButton(
                    onClick =
                    onRefreshClick
                ) {

                    Icon(
                        imageVector =
                        Icons.Rounded.Refresh,
                        contentDescription =
                        "Actualizar NDVI",
                        tint =
                        NdviDetailGreenDark
                    )
                }
            }
        }
    }
}


// =============================================================
// CHIP DE ÍNDICE
// =============================================================

@Composable
private fun NdviIndexChip(
    index: NdviIndex,
    selected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        modifier =
        Modifier.clickable(
            onClick = onClick
        ),
        shape =
        RoundedCornerShape(50.dp),
        color =
        if (selected) {
            NdviDetailGreen
        } else {
            Color.White
        },
        shadowElevation =
        if (selected) {
            2.dp
        } else {
            1.dp
        }
    ) {

        Text(
            text = index.label,
            modifier =
            Modifier.padding(
                horizontal = 14.dp,
                vertical = 9.dp
            ),
            color =
            if (selected) {
                Color.White
            } else {
                NdviDetailGreenDark
            },
            fontSize = 12.sp,
            fontWeight =
            if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            }
        )
    }
}


// =============================================================
// TARJETA ESTADÍSTICA
// =============================================================

@Composable
private fun NdviStatCard(
    modifier: Modifier,
    title: String,
    value: String
) {

    Surface(
        modifier = modifier,
        shape =
        RoundedCornerShape(14.dp),
        color =
        NdviDetailGreenSoft
    ) {

        Column(
            modifier =
            Modifier.padding(12.dp)
        ) {

            Text(
                text = title,
                color =
                NdviDetailTextSecondary,
                fontSize = 11.sp
            )


            Spacer(
                modifier =
                Modifier.height(4.dp)
            )


            Text(
                text = value,
                color =
                NdviDetailGreenDark,
                fontSize = 18.sp,
                fontWeight =
                FontWeight.Bold
            )
        }
    }
}


// =============================================================
// MENSAJE
// =============================================================

@Composable
private fun NdviDetailMessage(
    text: String,
    error: Boolean
) {

    Surface(
        modifier =
        Modifier.fillMaxWidth(),
        shape =
        RoundedCornerShape(12.dp),
        color =
        if (error) {
            Color(0xFFFFECE9)
        } else {
            NdviDetailGreenSoft
        }
    ) {

        Text(
            text = text,
            modifier =
            Modifier.padding(12.dp),
            color =
            if (error) {
                Color(0xFF98362F)
            } else {
                NdviDetailGreenDark
            },
            fontSize = 12.sp,
            fontWeight =
            FontWeight.Medium
        )
    }
}


// =============================================================
// CARGANDO
// =============================================================

@Composable
private fun NdviDetailLoading(
    onBackClick: () -> Unit,
    syncing: Boolean
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                NdviDetailBackground
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White
                )
                .padding(
                    horizontal = 8.dp,
                    vertical = 7.dp
                ),
            verticalAlignment =
            Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBackClick
            ) {

                Icon(
                    imageVector =
                    Icons.Rounded.ArrowBack,
                    contentDescription =
                    "Regresar",
                    tint =
                    NdviDetailGreenDark
                )
            }


            Text(
                text = "Detalle NDVI",
                color =
                NdviDetailGreenDark,
                fontSize = 20.sp,
                fontWeight =
                FontWeight.Bold
            )
        }


        Box(
            modifier =
            Modifier.fillMaxSize(),
            contentAlignment =
            Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                Alignment.CenterHorizontally
            ) {

                if (syncing) {

                    CircularProgressIndicator(
                        color =
                        NdviDetailGreen
                    )


                    Spacer(
                        modifier =
                        Modifier.height(14.dp)
                    )


                    Text(
                        text =
                        "Descargando datos NDVI...",
                        color =
                        NdviDetailTextSecondary
                    )

                } else {

                    Icon(
                        imageVector =
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint =
                        NdviDetailGreen,
                        modifier =
                        Modifier.size(40.dp)
                    )


                    Spacer(
                        modifier =
                        Modifier.height(12.dp)
                    )


                    Text(
                        text =
                        "No hay datos NDVI seleccionados.",
                        color =
                        NdviDetailTextSecondary
                    )
                }
            }
        }
    }
}


// =============================================================
// ESTADÍSTICAS
// =============================================================

private data class NdviBasicStats(
    val count: Int = 0,
    val mean: Double? = null,
    val min: Double? = null,
    val max: Double? = null
)


private fun calculateNdviStats(
    points: List<LocalNdviPointEntity>,
    index: NdviIndex
): NdviBasicStats {

    val values =
        points
            .mapNotNull { point ->

                point
                    .valueForIndex(
                        index
                    )
                    ?.takeIf {
                        it.isFinite()
                    }
            }


    if (values.isEmpty()) {

        return NdviBasicStats()
    }


    return NdviBasicStats(
        count = values.size,
        mean = values.average(),
        min = values.minOrNull(),
        max = values.maxOrNull()
    )
}


/**
 * Aquí conectamos NdviIndex con la columna
 * correspondiente de LocalNdviPointEntity.
 */
private fun LocalNdviPointEntity.valueForIndex(
    index: NdviIndex
): Double? {

    return when (index) {

        NdviIndex.NDVI ->
            ndvi

        NdviIndex.NIR_VIGOR ->
            nirVigor

        NdviIndex.OSAVI ->
            osavi

        NdviIndex.VARI ->
            vari

        NdviIndex.BARE_SOIL_INDEX ->
            bareSoilIndex

        NdviIndex.RED_EDGE ->
            redEdge

        NdviIndex.SWIR ->
            swir

        NdviIndex.NDRE ->
            ndre

        NdviIndex.MSAVI2 ->
            msavi2

        NdviIndex.GNDVI ->
            gndvi

        NdviIndex.NDMI ->
            ndmi

        NdviIndex.PSRI ->
            psri
    }
}


// =============================================================
// FORMATOS
// =============================================================

private fun formatIndexValue(
    value: Double?
): String {

    if (value == null) {
        return "—"
    }


    return String.format(
        Locale.US,
        "%.4f",
        value
    )
}


private fun formatPointCount(
    count: Int
): String {

    return NumberFormat
        .getIntegerInstance()
        .format(count)
}

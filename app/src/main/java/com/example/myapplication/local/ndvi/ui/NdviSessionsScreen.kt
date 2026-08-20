package com.example.myapplication.local.ndvi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.LocalNdviSessionEntity
import java.text.NumberFormat


private val NdviGreen = Color(0xFF176B3A)
private val NdviGreenDark = Color(0xFF0F4D2C)
private val NdviBackground = Color(0xFFF5F7F2)
private val NdviBlue = Color(0xFF2166D1)
private val NdviText = Color(0xFF26332B)
private val NdviTextSecondary = Color(0xFF667168)
private val NdviGreenSoft = Color(0xFFE8F4E9)


/**
 * Lista NDVI con la misma estructura visual que Aspersión:
 *
 * EncabezadoApp
 * título + actualizar
 * filtros contraíbles
 * lista de sesiones
 */
@Composable
fun NdviSessionsScreen(
    state: NdviUiState,
    nombreUsuario: String,
    rolUsuario: String,
    onOpenSession: (String) -> Unit,
    onSync: () -> Unit,
    onProducerFilterChange: (String?) -> Unit,
    onRanchFilterChange: (String?) -> Unit,
    onPlotFilterChange: (String?) -> Unit,
    onStartDateFilterChange: (Long?) -> Unit,
    onEndDateFilterChange: (Long?) -> Unit,
    onClearFilters: () -> Unit,
    onToggleFilters: () -> Unit,
    onBackToModules: () -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)?,
    onCerrarSesionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NdviBackground)
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp
            )
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

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Sesiones NDVI",
                    color = NdviGreenDark,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "${state.sessions.size} de ${state.totalSessionsBeforeFilters} " +
                            "sesiones · toca una para ver el análisis",
                    color = NdviTextSecondary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onSync,
                enabled = !state.syncingSessions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NdviGreen
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.syncingSessions) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualizar sesiones",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = if (state.syncingSessions) {
                        "Actualizando"
                    } else {
                        "Actualizar"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        NdviSessionFiltersPanel(
            state = state,
            onProducerChange = onProducerFilterChange,
            onRanchChange = onRanchFilterChange,
            onPlotChange = onPlotFilterChange,
            onStartDateChange = onStartDateFilterChange,
            onEndDateChange = onEndDateFilterChange,
            onClear = onClearFilters,
            onToggle = onToggleFilters
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (!state.message.isNullOrBlank()) {
            NdviMessageCard(
                text = state.message,
                error = false
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!state.error.isNullOrBlank()) {
            NdviMessageCard(
                text = state.error,
                error = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            state.sessions.isEmpty() && state.syncingSessions -> {
                NdviLoadingCard(
                    message = "Consultando sesiones NDVI…"
                )
            }

            state.sessions.isEmpty() -> {
                NdviEmptySessionsCard(
                    hasFilters = state.filters.isActive,
                    onSync = onSync,
                    onBackToModules = onBackToModules
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.sessions,
                        key = LocalNdviSessionEntity::sessionId
                    ) { session ->
                        NdviSessionCard(
                            session = session,
                            context = state.sessionContextsById[
                                session.sessionId
                            ],
                            onClick = {
                                onOpenSession(
                                    session.sessionId
                                )
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}


@Composable
private fun NdviSessionCard(
    session: LocalNdviSessionEntity,
    context: NdviSessionContext?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            NdviBlue.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SatelliteAlt,
                        contentDescription = null,
                        tint = NdviBlue,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = context?.plotName
                            ?.takeIf(String::isNotBlank)
                            ?: buildPlotLabel(session.plotId),
                        color = NdviText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = context?.programName
                            ?.takeIf(String::isNotBlank)
                            ?: buildProgramLabel(session.programId),
                        color = NdviTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF87918A)
                )
            }

            val locationText = listOfNotNull(
                context?.producerName
                    ?.takeIf(String::isNotBlank),
                context?.ranchName
                    ?.takeIf(String::isNotBlank)
            ).joinToString(" · ")

            if (locationText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = locationText,
                    color = NdviTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = NdviGreen,
                        modifier = Modifier.size(17.dp)
                    )

                    Text(
                        text = session.sessionDate
                            ?.takeIf(String::isNotBlank)
                            ?.take(10)
                            ?: "Fecha no disponible",
                        modifier = Modifier.padding(start = 6.dp),
                        color = NdviText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                NdviStatusChip(
                    session = session
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildPointsLabel(
                        session.pointsCount
                    ),
                    color = NdviTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (
                            session.pointsDownloadComplete
                        ) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.CloudDownload
                        },
                        contentDescription = null,
                        tint = if (
                            session.pointsDownloadComplete
                        ) {
                            NdviGreen
                        } else {
                            Color(0xFF7B8580)
                        },
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = if (
                            session.pointsDownloadComplete
                        ) {
                            "Offline listo"
                        } else {
                            "${session.downloadedPointsCount} descargados"
                        },
                        color = NdviTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


@Composable
private fun NdviStatusChip(
    session: LocalNdviSessionEntity
) {
    val raw = session.status
        ?.takeIf(String::isNotBlank)
        ?: session.importStatus
            ?.takeIf(String::isNotBlank)
        ?: "Disponible"

    val normalized = raw.trim().lowercase()

    val (label, color) = when (normalized) {
        "completed", "complete", "completado", "done", "loaded", "cargado" ->
            "Disponible" to NdviGreen

        "processing", "procesando", "in_progress", "en proceso" ->
            "Procesando" to Color(0xFFE69A00)

        "error" ->
            "Error" to Color(0xFFB3261E)

        else ->
            raw.replaceFirstChar { it.uppercase() } to NdviGreen
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 5.dp
            ),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun NdviLoadingCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = NdviGreen,
                strokeWidth = 2.dp
            )
            Text(
                text = message,
                color = NdviTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}


@Composable
private fun NdviEmptySessionsCard(
    hasFilters: Boolean,
    onSync: () -> Unit,
    onBackToModules: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SatelliteAlt,
                contentDescription = null,
                tint = NdviGreen,
                modifier = Modifier.size(38.dp)
            )

            Text(
                text = if (hasFilters) {
                    "No hay sesiones con esos filtros"
                } else {
                    "No hay sesiones NDVI disponibles"
                },
                color = NdviGreenDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = if (hasFilters) {
                    "Prueba limpiando o cambiando los filtros."
                } else {
                    "Actualiza para consultar la información del servidor."
                },
                color = NdviTextSecondary,
                fontSize = 12.sp
            )

            Button(
                onClick = onSync,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NdviGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Actualizar",
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = onBackToModules
            ) {
                Text(
                    text = "Volver a módulos",
                    color = NdviGreenDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Composable
private fun NdviMessageCard(
    text: String,
    error: Boolean
) {
    val color = if (error) {
        Color(0xFFB3261E)
    } else {
        NdviGreen
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(10.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


private fun buildPlotLabel(
    plotId: String?
): String {
    return plotId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { id ->
            "Parcela ${id.takeLast(6)}"
        }
        ?: "Parcela NDVI"
}


private fun buildProgramLabel(
    programId: String?
): String {
    return programId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { id ->
            "Programa ${id.takeLast(6)}"
        }
        ?: "Programa productivo"
}


private fun buildPointsLabel(
    points: Int?
): String {
    val count = points ?: 0

    return NumberFormat
        .getIntegerInstance()
        .format(count) +
            if (count == 1) {
                " punto"
            } else {
                " puntos"
            }
}

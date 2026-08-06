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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun AspersionSessionsScreen(
    state: AspersionUiState,
    nombreUsuario: String,
    rolUsuario: String,
    onOpenSession: (String) -> Unit,
    onSync: () -> Unit,
    onSearchChange: (String) -> Unit,
    onProducerFilterChange: (String?) -> Unit,
    onRanchFilterChange: (String?) -> Unit,
    onPlotFilterChange: (String?) -> Unit,
    onProgramFilterChange: (String?) -> Unit,
    onStartDateFilterChange: (Long?) -> Unit,
    onEndDateFilterChange: (Long?) -> Unit,
    onStatusFilterChange: (String?) -> Unit,
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

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Programas de aspersión",
                    color = AspersionGreenDark,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${state.programs.size} de ${state.totalProgramsBeforeFilters} " +
                            "subprogramas · ${state.programs.sumOf { it.sessions.size }} sesiones",
                    color = Color(0xFF667168),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onSync,
                enabled = !state.syncingSessions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AspersionGreen
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
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar sesiones",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = if (state.syncingSessions) "Actualizando" else "Actualizar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AspersionSessionFiltersPanel(
            state = state,
            onSearchChange = onSearchChange,
            onProducerChange = onProducerFilterChange,
            onRanchChange = onRanchFilterChange,
            onPlotChange = onPlotFilterChange,
            onProgramChange = onProgramFilterChange,
            onStartDateChange = onStartDateFilterChange,
            onEndDateChange = onEndDateFilterChange,
            onStatusChange = onStatusFilterChange,
            onClear = onClearFilters,
            onToggle = onToggleFilters
        )

        Spacer(modifier = Modifier.height(10.dp))

        when {
            state.programs.isEmpty() && state.syncingSessions -> {
                AspersionLoadingCard(
                    message = "Consultando programas y sesiones de aspersión…"
                )
            }

            state.programs.isEmpty() -> {
                EmptyAspersionSessionsCard(
                    hasSearch = state.filters.isActive,
                    onSync = onSync,
                    onBackToModules = onBackToModules
                )
            }

            else -> {
                val programsByMaster = state.programs.groupBy { program ->
                    program.masterProgramId ?: "local"
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    programsByMaster.forEach { (masterId, programs) ->
                        item(key = "master:$masterId") {
                            AspersionMasterHeader(programs.first())
                        }
                        items(
                            items = programs,
                            key = AspersionProgramItem::programId
                        ) { program ->
                            AspersionProgramCard(
                                program = program,
                                onOpenSession = onOpenSession
                            )
                        }
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
private fun AspersionMasterHeader(program: AspersionProgramItem) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            text = program.masterProgramName
                ?.takeIf(String::isNotBlank)
                ?: "Programa productivo",
            color = AspersionGreenDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Subprogramas",
            color = Color(0xFF68736B),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun AspersionProgramCard(
    program: AspersionProgramItem,
    onOpenSession: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AspersionBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🚜",
                        fontSize = 24.sp
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = program.programName,
                        color = AspersionText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    program.masterProgramName
                        ?.takeIf(String::isNotBlank)
                        ?.let { masterName ->
                            Text(
                                text = masterName,
                                color = Color(0xFF637067),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                }

                program.status?.let { status ->
                    StatusPill(
                        text = aspersionStatusLabel(status),
                        color = AspersionGreen
                    )
                }
            }

            val hierarchy = listOfNotNull(
                program.context.producerName,
                program.context.ranchName,
                program.plotName ?: program.context.plotName
            ).joinToString(" · ")
            if (hierarchy.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = hierarchy,
                    color = Color(0xFF768079),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val plannedDates = formatAspersionProgramRange(
                program.estStartDate,
                program.estFinishDate
            )
            if (plannedDates.isNotBlank()) {
                Spacer(modifier = Modifier.height(7.dp))
                StatusPill(
                    text = plannedDates,
                    color = Color(0xFF7C5C16)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (program.sessions.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF4D8)
                ) {
                    Text(
                        text = "Sin sesión de aspersión",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Color(0xFF8A5A00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                program.sessions.forEachIndexed { index, sessionItem ->
                    if (index > 0) Spacer(modifier = Modifier.height(7.dp))
                    AspersionProgramSessionRow(
                        sessionItem = sessionItem,
                        onClick = { onOpenSession(sessionItem.sessionId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AspersionProgramSessionRow(
    sessionItem: AspersionProgramSessionItem,
    onClick: () -> Unit
) {
    val cached = sessionItem.cachedSession
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = AspersionBlue.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aspersión · ${formatAspersionDate(sessionItem.aspersionDate)}",
                    color = AspersionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                val details = buildList {
                    val points = cached?.pointsCount ?: cached?.downloadedPointsCount
                    if (points != null) add("$points puntos")
                    sessionItem.importStatus
                        ?.takeIf(String::isNotBlank)
                        ?.let { add(aspersionStatusLabel(it)) }
                }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        color = Color(0xFF637067),
                        fontSize = 10.sp
                    )
                }
                if (cached?.pointsDownloadComplete == true) {
                    Text(
                        text = "✓ Disponible sin conexión · ${formatSyncTime(cached.lastSyncedAt)}",
                        color = AspersionGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = "›",
                color = AspersionBlue,
                fontSize = 30.sp
            )
        }
    }
}

private fun formatAspersionProgramRange(
    start: String?,
    end: String?
): String {
    val startText = start?.let(::formatAspersionDate).orEmpty()
    val endText = end?.let(::formatAspersionDate).orEmpty()
    return when {
        startText.isNotBlank() && endText.isNotBlank() -> "$startText — $endText"
        startText.isNotBlank() -> "Desde $startText"
        endText.isNotBlank() -> "Hasta $endText"
        else -> ""
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyAspersionSessionsCard(
    hasSearch: Boolean,
    onSync: () -> Unit,
    onBackToModules: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasSearch) "No encontramos coincidencias" else "No hay subprogramas disponibles",
                color = AspersionText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasSearch) {
                    "Prueba con otro productor, rancho, parcela o intervalo de fechas."
                } else {
                    "Actualiza para consultar la estructura de la CIA seleccionada."
                },
                color = Color(0xFF667168),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBackToModules,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE5EBE6),
                        contentColor = AspersionText
                    )
                ) {
                    Text("Módulos")
                }
                Button(
                    onClick = onSync,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AspersionGreen
                    )
                ) {
                    Text("Actualizar")
                }
            }
        }
    }
}

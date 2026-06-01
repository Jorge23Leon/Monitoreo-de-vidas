package com.example.myapplication.local.monitoreo.filtros

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.ImageUriBox
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale

@Composable
internal fun FiltrosPrincipalesCard(
    filtrosExpandidos: Boolean,
    onExpandChange: () -> Unit,
    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    productorSeleccionado: LocalAgroUnitEntity?,
    ranchoSeleccionado: LocalRanchEntity?,
    parcelaSeleccionada: LocalPlotEntity?,
    onProductorChange: (LocalAgroUnitEntity) -> Unit,
    onRanchoChange: (LocalRanchEntity?) -> Unit,
    onParcelaChange: (LocalPlotEntity?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtros de consulta",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF163D1D),
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onExpandChange,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (filtrosExpandidos) "−" else "+",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E6B2E)
                    )
                }
            }

            if (filtrosExpandidos) {
                Spacer(modifier = Modifier.height(10.dp))

                SelectorFiltro(
                    label = "Productor",
                    selected = productorSeleccionado,
                    items = productores,
                    itemText = { it.commercial_name },
                    emptyText = "Selecciona productor",
                    enabled = productores.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    onSelected = onProductorChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SelectorFiltroConTodos(
                        label = "Rancho",
                        selected = ranchoSeleccionado,
                        allText = "Todos",
                        items = ranchos,
                        itemText = { it.name },
                        enabled = productorSeleccionado != null,
                        modifier = Modifier.weight(1f),
                        onSelected = { rancho ->
                            onRanchoChange(rancho)
                        }
                    )

                    SelectorFiltroConTodos(
                        label = "Parcela",
                        selected = parcelaSeleccionada,
                        allText = "Todas",
                        items = parcelas,
                        itemText = { obtenerNombreParcelaFiltro(it) },
                        enabled = productorSeleccionado != null && ranchoSeleccionado != null,
                        modifier = Modifier.weight(1f),
                        onSelected = { parcela ->
                            onParcelaChange(parcela)
                        }
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = resumenFiltrosPrincipales(
                        productor = productorSeleccionado,
                        rancho = ranchoSeleccionado,
                        parcela = parcelaSeleccionada
                    ),
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
internal fun FiltrosSecundarios(
    cicloFiltro: LocalProgramEntity?,
    programas: List<LocalProgramEntity>,
    fechaInicioTexto: String,
    fechaFinTexto: String,
    estadoFiltro: String,
    onCicloChange: (LocalProgramEntity?) -> Unit,
    onFechaInicioChange: (String) -> Unit,
    onFechaFinChange: (String) -> Unit,
    onEstadoChange: (String) -> Unit,
    onLimpiarClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectorFiltroOpcionalPrograma(
                    icono = "↻",
                    label = "Ciclo",
                    selected = cicloFiltro,
                    items = programas,
                    modifier = Modifier.width(142.dp),
                    onSelected = onCicloChange
                )

                SelectorFechaFiltro(
                    icono = "📅",
                    label = "Inicio",
                    value = fechaInicioTexto,
                    modifier = Modifier.width(142.dp),
                    onValueChange = onFechaInicioChange
                )

                SelectorFechaFiltro(
                    icono = "📅",
                    label = "Fin",
                    value = fechaFinTexto,
                    modifier = Modifier.width(142.dp),
                    onValueChange = onFechaFinChange
                )

                SelectorEstadoFiltro(
                    icono = "⚑",
                    label = "Estado",
                    selected = estadoFiltro,
                    modifier = Modifier.width(142.dp),
                    onSelected = onEstadoChange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onLimpiarClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "↻  Limpiar filtros",
                    color = Color(0xFF1F6D2A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
internal fun EstadoVacioSeleccionProductor() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(125.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF7EA)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🌱", fontSize = 48.sp)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Selecciona un productor",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF173B1A)
        )

        Text(
            text = "Los monitoreos aparecerán aquí automáticamente.",
            fontSize = 13.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
internal fun EstadoVacioSinResultados() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sin monitoreos",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF173B1A)
            )

            Text(
                text = "No hay monitoreos con los filtros actuales.",
                fontSize = 12.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )
        }
    }
}
@Composable
internal fun TarjetaMonitoreoConsulta(
    header: LocalPhytomonitoringHeaderEntity,
    parcelaNombre: String,
    productorNombre: String,
    ranchoNombre: String?,
    ciclo: String,
    codigo: String,
    fotoCultivo: String?,
    nombreCultivo: String?,
    mostrarAbrir: Boolean,
    mostrarReporte: Boolean,
    puedeCancelar: Boolean = false,
    onAbrirClick: () -> Unit,
    onReporteClick: () -> Unit,
    onCancelarClick: () -> Unit = {}
) {
    var menuAbierto by remember {
        mutableStateOf(false)
    }

    val cerrado = esEstadoCerradoFiltro(header.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E8DD), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (puedeCancelar && !cerrado) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            menuAbierto = true
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .background(
                                color = Color(0xFFF1F1F1),
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = "⋮",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF111111)
                        )
                    }

                    DropdownMenu(
                        expanded = menuAbierto,
                        onDismissRequest = {
                            menuAbierto = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Cancelar monitoreo",
                                    color = Color(0xFFB3261E),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            onClick = {
                                menuAbierto = false
                                onCancelarClick()
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImageUriBox(
                        photo = fotoCultivo,
                        fallbackIcon = iconoCultivoFallbackFiltro(nombreCultivo),
                        sizeDp = 82,
                        modifier = Modifier
                            .size(width = 92.dp, height = 82.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(colorFondoImagen(header.status))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            ChipEstadoFiltro(status = header.status)

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = formatoFechaCorta(header.estStartDate),
                                fontSize = 10.sp,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(
                                    end = if (puedeCancelar && !cerrado) 64.dp else 0.dp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = parcelaNombre,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF173B1A),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            DatoTarjeta(
                                label = "Ciclo",
                                value = ciclo,
                                modifier = Modifier.weight(1f)
                            )

                            DatoTarjeta(
                                label = "Código",
                                value = codigo,
                                modifier = Modifier.weight(1f)
                            )

                            DatoTarjeta(
                                label = "Parcela",
                                value = parcelaNombre,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "⌂  ${ranchoNombre ?: "Sin rancho"}  ·  $productorNombre",
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            maxLines = 1
                        )
                    }
                }

                if (mostrarAbrir || mostrarReporte) {
                    Spacer(modifier = Modifier.height(10.dp))

                    if (mostrarAbrir) {
                        Button(
                            onClick = onAbrirClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0B6B20)
                            )
                        ) {
                            Text(
                                text = "Abrir monitoreo",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onReporteClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Ver reporte",
                                color = Color(0xFF0B6B20),
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
private fun DatoTarjeta(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(end = 4.dp)) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.Gray
        )

        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun ChipEstadoFiltro(status: String) {
    val normalizado = status.trim().lowercase(Locale.getDefault())

    val colorFondo = when {
        normalizado.contains("complet") || normalizado.contains("final") -> Color(0xFFE5F4DF)
        normalizado.contains("proceso") || normalizado.contains("progress") -> Color(0xFFE7F0FF)
        normalizado.contains("cancel") -> Color(0xFFFFE1E1)
        else -> Color(0xFFFFF4CC)
    }

    val colorTexto = when {
        normalizado.contains("complet") || normalizado.contains("final") -> Color(0xFF1F6D2A)
        normalizado.contains("proceso") || normalizado.contains("progress") -> Color(0xFF255EA8)
        normalizado.contains("cancel") -> Color(0xFFB3261E)
        else -> Color(0xFF9A6A00)
    }

    Text(
        text = textoEstadoFiltro(status),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = colorTexto,
        modifier = Modifier
            .background(colorFondo, RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}
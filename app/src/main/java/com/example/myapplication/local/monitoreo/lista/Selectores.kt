package com.example.myapplication.local.monitoreo.lista

import android.app.DatePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun SelectorParcelaOpcionalMonitoreo(
    parcelaSeleccionada: LocalPlotEntity?,
    parcelas: List<LocalPlotEntity>,
    onParcelaSeleccionada: (LocalPlotEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val textoParcela = parcelaSeleccionada?.let { obtenerNombreParcelaLista(it) } ?: "Todas las parcelas"

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(text = "▦", fontSize = 16.sp, color = Color(0xFF0B6B20))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Parcela", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5F6F5A))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = textoParcela,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(text = "⌄", fontSize = 18.sp, color = Color.Black)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todas las parcelas") },
                onClick = {
                    onParcelaSeleccionada(null)
                    expanded = false
                }
            )

            parcelas.forEach { parcela ->
                DropdownMenuItem(
                    text = { Text(obtenerNombreParcelaLista(parcela)) },
                    onClick = {
                        onParcelaSeleccionada(parcela)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun PanelFiltrosListaCompacto(
    filtrosExpandidos: Boolean,
    onExpandChange: () -> Unit,
    mostrarFiltroParcela: Boolean,
    parcelaSeleccionada: LocalPlotEntity?,
    parcelas: List<LocalPlotEntity>,
    onParcelaSeleccionada: (LocalPlotEntity?) -> Unit,
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
    val filtrosActivos = listOf(
        mostrarFiltroParcela && parcelaSeleccionada != null,
        cicloFiltro != null,
        fechaInicioTexto.isNotBlank(),
        fechaFinTexto.isNotBlank(),
        !estadoFiltro.equals("Todos", ignoreCase = true)
    ).count { it }

    val resumen = buildList {
        if (mostrarFiltroParcela) {
            parcelaSeleccionada?.let {
                add("Parcela: ${obtenerNombreParcelaLista(it)}")
            }
        }

        cicloFiltro?.let {
            add("Ciclo: ${textoCicloLista(it.cycle)}")
        }

        fechaInicioTexto.takeIf { it.isNotBlank() }
            ?.let { add("Desde: $it") }

        fechaFinTexto.takeIf { it.isNotBlank() }
            ?.let { add("Hasta: $it") }

        estadoFiltro.takeIf { !it.equals("Todos", ignoreCase = true) }
            ?.let { add("Estado: $it") }
    }.joinToString(" · ").ifBlank { "Todos los monitoreos" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandChange),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "≡",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B6B20)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Filtros",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF173B1A)
                        )

                        if (filtrosActivos > 0) {
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "$filtrosActivos aplicados",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0B6B20),
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = resumen,
                        fontSize = 11.sp,
                        color = Color(0xFF667064),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(onClick = onExpandChange) {
                    Text(
                        text = if (filtrosExpandidos) "Cerrar" else "Editar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B6B20)
                    )
                }

                Text(
                    text = if (filtrosExpandidos) "⌃" else "⌄",
                    fontSize = 17.sp,
                    color = Color(0xFF173B1A)
                )
            }

            if (filtrosExpandidos) {
                Spacer(modifier = Modifier.height(11.dp))

                if (mostrarFiltroParcela) {
                    SelectorParcelaOpcionalMonitoreo(
                        parcelaSeleccionada = parcelaSeleccionada,
                        parcelas = parcelas,
                        onParcelaSeleccionada = onParcelaSeleccionada,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(9.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FiltroCompactoCiclo(
                        cicloFiltro = cicloFiltro,
                        programas = programas,
                        onSelected = onCicloChange
                    )

                    FiltroCompactoFecha(
                        titulo = "Inicio",
                        value = fechaInicioTexto,
                        onValueChange = onFechaInicioChange
                    )

                    FiltroCompactoFecha(
                        titulo = "Fin",
                        value = fechaFinTexto,
                        onValueChange = onFechaFinChange
                    )

                    FiltroCompactoEstado(
                        estadoFiltro = estadoFiltro,
                        onSelected = onEstadoChange
                    )
                }

                if (filtrosActivos > 0) {
                    TextButton(
                        onClick = onLimpiarClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "↻ Limpiar filtros",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0B6B20)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltroCompactoCiclo(
    cicloFiltro: LocalProgramEntity?,
    programas: List<LocalProgramEntity>,
    onSelected: (LocalProgramEntity?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.width(130.dp).height(54.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(text = "↻", fontSize = 18.sp, color = Color.Black)
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Ciclo", fontSize = 10.sp, color = Color.DarkGray, maxLines = 1)
                Text(
                    text = cicloFiltro?.let { textoCicloLista(it.cycle) } ?: "Todos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1
                )
            }
            Text(text = "⌄", fontSize = 14.sp, color = Color.Black)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            programas.forEach { programa ->
                DropdownMenuItem(
                    text = { Text(textoCicloLista(programa.cycle)) },
                    onClick = {
                        onSelected(programa)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FiltroCompactoFecha(
    titulo: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val formatoFecha = remember {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply { isLenient = false }
    }

    fun abrirCalendario() {
        val calendario = Calendar.getInstance()
        parseFechaInicioLista(value)?.let { calendario.timeInMillis = it }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val fechaSeleccionada = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onValueChange(formatoFecha.format(fechaSeleccionada.time))
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    OutlinedButton(
        onClick = { abrirCalendario() },
        modifier = Modifier.width(130.dp).height(54.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(text = "📅", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titulo, fontSize = 10.sp, color = Color.DarkGray, maxLines = 1)
            Text(
                text = value.ifBlank { "Fecha" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (value.isBlank()) Color.Gray else Color.Black,
                maxLines = 1
            )
        }
        Text(text = "⌄", fontSize = 14.sp, color = Color.Black)
    }
}

@Composable
private fun FiltroCompactoEstado(
    estadoFiltro: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val estados = listOf("Todos", "Pendiente", "Pausado", "En proceso", "Completado", "Cancelado")

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.width(130.dp).height(54.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(text = "⚑", fontSize = 17.sp, color = Color.Black)
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Estado", fontSize = 10.sp, color = Color.DarkGray, maxLines = 1)
                Text(
                    text = estadoFiltro,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1
                )
            }
            Text(text = "⌄", fontSize = 14.sp, color = Color.Black)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            estados.forEach { estado ->
                DropdownMenuItem(
                    text = { Text(estado) },
                    onClick = {
                        onSelected(estado)
                        expanded = false
                    }
                )
            }
        }
    }
}

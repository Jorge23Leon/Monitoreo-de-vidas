package com.example.myapplication.local

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MonitoreoListaScreen(
    nombreUsuario: String,
    busquedaFueConSaltoFiltros: Boolean,

    monitoreos: List<LocalPhytomonitoringHeaderEntity>,
    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    programas: List<LocalProgramEntity>,

    onAbrirMapaClick: (LocalPhytomonitoringHeaderEntity) -> Unit
) {
    val productoresMap = remember(productores) {
        productores.associateBy { it.idLocalAgroUnit }
    }

    val ranchosMap = remember(ranchos) {
        ranchos.associateBy { it.idLocalRanch }
    }

    val parcelasMap = remember(parcelas) {
        parcelas.associateBy { it.idLocalPlot }
    }

    val programasMap = remember(programas) {
        programas.associateBy { it.idProgram }
    }

    var cicloFiltro by remember {
        mutableStateOf<LocalProgramEntity?>(null)
    }

    var fechaInicioTexto by remember {
        mutableStateOf("")
    }

    var fechaFinTexto by remember {
        mutableStateOf("")
    }

    var estadoFiltro by remember {
        mutableStateOf("Todos")
    }

    var monitoreoInfoSeleccionado by remember {
        mutableStateOf<LocalPhytomonitoringHeaderEntity?>(null)
    }

    val fechaInicioMillis = parseFechaInicioLista(fechaInicioTexto)
    val fechaFinMillis = parseFechaFinLista(fechaFinTexto)

    val monitoreosFiltrados = monitoreos.filter { header ->
        val cumpleCiclo = cicloFiltro == null || header.idProgram == cicloFiltro?.idProgram

        val cumpleFechaInicio = fechaInicioMillis == null ||
                header.estStartDate >= fechaInicioMillis

        val cumpleFechaFin = fechaFinMillis == null ||
                header.estStartDate <= fechaFinMillis

        val cumpleEstado = when (estadoFiltro) {
            "Pendiente" -> header.status.lowercase() == "pending" ||
                    header.status.lowercase() == "pendiente"

            "En proceso" -> header.status.lowercase() == "in_progress" ||
                    header.status.lowercase() == "en proceso" ||
                    header.status.lowercase() == "vigente"

            "Completado" -> header.status.lowercase() == "completed" ||
                    header.status.lowercase() == "completado" ||
                    header.status.lowercase() == "finalizado"

            else -> true
        }

        cumpleCiclo && cumpleFechaInicio && cumpleFechaFin && cumpleEstado
    }

    val primerHeader = monitoreos.firstOrNull()
    val primerPrograma = primerHeader?.let { programasMap[it.idProgram] }
    val primeraParcela = primerHeader?.let { parcelasMap[it.idLocalPlot] }
    val primerRancho = primeraParcela?.let { ranchosMap[it.idLocalRanch] }
    val primerProductor = primerPrograma?.let { productoresMap[it.idLocalAgroUnit] }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            EncabezadoApp(
                nombreUsuario = nombreUsuario
            )

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF8FAF7)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    if (!busquedaFueConSaltoFiltros) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DatoUbicacionMini(
                                titulo = "Productor",
                                valor = primerProductor?.commercial_name ?: "-",
                                modifier = Modifier.weight(1f)
                            )

                            DatoUbicacionMini(
                                titulo = "Rancho",
                                valor = primerRancho?.name ?: "-",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        DatoUbicacionMini(
                            titulo = "Parcela",
                            valor = primeraParcela?.code ?: "-",
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Mostrando monitoreos disponibles",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FiltrosCompactosBarra(
                cicloFiltro = cicloFiltro,
                programas = programas,
                fechaInicioTexto = fechaInicioTexto,
                fechaFinTexto = fechaFinTexto,
                estadoFiltro = estadoFiltro,
                onCicloChange = { cicloFiltro = it },
                onFechaInicioChange = { fechaInicioTexto = it },
                onFechaFinChange = { fechaFinTexto = it },
                onEstadoChange = { estadoFiltro = it },
                onLimpiarClick = {
                    cicloFiltro = null
                    fechaInicioTexto = ""
                    fechaFinTexto = ""
                    estadoFiltro = "Todos"
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Monitoreos disponibles: ${monitoreosFiltrados.size}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color.Black,
                        shape = RoundedCornerShape(4.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HeaderTabla("Ciclo", Modifier.weight(1.2f))
                        HeaderTabla("Estado", Modifier.weight(1f))
                        HeaderTabla("Inicio", Modifier.weight(1f))
                        HeaderTabla("Fin", Modifier.weight(1f))
                        HeaderTabla("Info.", Modifier.weight(0.8f))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (monitoreosFiltrados.isEmpty()) {
                        Text(
                            text = "No se encontraron monitoreos con esos filtros.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        monitoreosFiltrados.forEach { header ->

                            val programa = programasMap[header.idProgram]
                            val nombreMonitoreo = programa?.cycle ?: "M-${header.idHeader}"

                            FilaMonitoreo(
                                nombreMonitoreo = nombreMonitoreo,
                                status = header.status,
                                fechaInicio = header.estStartDate,
                                fechaFin = header.estFinishDate,
                                onAbrirMapaClick = {
                                    onAbrirMapaClick(header)
                                },
                                onInfoClick = {
                                    monitoreoInfoSeleccionado = header
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        val headerDetalle = monitoreoInfoSeleccionado

        if (headerDetalle != null) {
            val programa = programasMap[headerDetalle.idProgram]
            val parcela = parcelasMap[headerDetalle.idLocalPlot]
            val rancho = parcela?.let { ranchosMap[it.idLocalRanch] }
            val productor = programa?.let { productoresMap[it.idLocalAgroUnit] }

            DetalleMonitoreoDialog(
                header = headerDetalle,
                programa = programa,
                productor = productor,
                rancho = rancho,
                parcela = parcela,
                onDismiss = {
                    monitoreoInfoSeleccionado = null
                }
            )
        }
    }
}

@Composable
private fun FiltrosCompactosBarra(
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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

            Button(
                onClick = onLimpiarClick,
                modifier = Modifier
                    .width(92.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE9E0FA)
                ),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    text = "Limpiar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5E35B1),
                    textAlign = TextAlign.Center
                )
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
            modifier = Modifier
                .width(130.dp)
                .height(56.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "↻",
                fontSize = 18.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Ciclo",
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    maxLines = 1
                )

                Text(
                    text = cicloFiltro?.cycle ?: "Todos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1
                )
            }

            Text(
                text = "⌄",
                fontSize = 14.sp,
                color = Color.Black
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )

            programas.forEach { programa ->
                DropdownMenuItem(
                    text = { Text(programa.cycle) },
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
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply {
            isLenient = false
        }
    }

    fun abrirCalendario() {
        val calendario = Calendar.getInstance()

        if (value.isNotBlank()) {
            try {
                val fechaGuardada = formatoFecha.parse(value)
                if (fechaGuardada != null) {
                    calendario.time = fechaGuardada
                }
            } catch (_: Exception) {
            }
        }

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
        modifier = Modifier
            .width(128.dp)
            .height(56.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            text = "📅",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.width(6.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = titulo,
                fontSize = 10.sp,
                color = Color.DarkGray,
                maxLines = 1
            )

            Text(
                text = value.ifBlank { "Fecha" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (value.isBlank()) Color.Gray else Color.Black,
                maxLines = 1
            )
        }

        Text(
            text = "⌄",
            fontSize = 14.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun FiltroCompactoEstado(
    estadoFiltro: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val estados = listOf(
        "Todos",
        "Pendiente",
        "En proceso",
        "Completado"
    )

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .width(130.dp)
                .height(56.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "⚑",
                fontSize = 17.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Estado",
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    maxLines = 1
                )

                Text(
                    text = estadoFiltro,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1
                )
            }

            Text(
                text = "⌄",
                fontSize = 14.sp,
                color = Color.Black
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
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

@Composable
private fun DatoUbicacionMini(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titulo,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5F6F5A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = valor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeaderTabla(
    texto: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = texto,
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FilaMonitoreo(
    nombreMonitoreo: String,
    status: String,
    fechaInicio: Long,
    fechaFin: Long,
    onAbrirMapaClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onAbrirMapaClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = nombreMonitoreo,
            modifier = Modifier.weight(1.2f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Text(
            text = textoEstado(status),
            modifier = Modifier.weight(1f),
            fontSize = 10.sp,
            color = colorEstado(status),
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Text(
            text = formatearFechaCorta(fechaInicio),
            modifier = Modifier.weight(1f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = formatearFechaCorta(fechaFin),
            modifier = Modifier.weight(1f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onInfoClick,
            modifier = Modifier
                .weight(0.8f)
                .height(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7ED957)
            ),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "Info",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DetalleMonitoreoDialog(
    header: LocalPhytomonitoringHeaderEntity,
    programa: LocalProgramEntity?,
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Información del monitoreo",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                DatoDetalle("Monitoreo:", programa?.cycle ?: "Sin ciclo")
                DatoDetalle("Estado:", textoEstado(header.status))
                DatoDetalle("Productor:", productor?.commercial_name ?: "-")
                DatoDetalle("Rancho:", rancho?.name ?: "-")
                DatoDetalle("Parcela:", parcela?.code ?: "-")
                DatoDetalle("Fecha programada:", formatearFechaCompleta(header.estStartDate))
                DatoDetalle("Fecha fin estimada:", formatearFechaCompleta(header.estFinishDate))
                DatoDetalle("Iniciado:", formatearFechaOpcional(header.startAt))
                DatoDetalle("Finalizado:", formatearFechaOpcional(header.finishedAt))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun DatoDetalle(
    titulo: String,
    valor: String
) {
    Column(
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text(
            text = titulo,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.Black
        )

        Text(
            text = valor,
            fontSize = 12.sp,
            color = Color.DarkGray
        )
    }
}

private fun textoEstado(status: String): String {
    return when (status.lowercase()) {
        "pending" -> "Pendiente"
        "in_progress" -> "En proceso"
        "completed" -> "Completado"
        "cancelled" -> "Cancelado"
        "pendiente" -> "Pendiente"
        "en proceso" -> "En proceso"
        "vigente" -> "En proceso"
        "completado" -> "Completado"
        "finalizado" -> "Completado"
        "cancelado" -> "Cancelado"
        else -> status
    }
}

private fun colorEstado(status: String): Color {
    return when (status.lowercase()) {
        "pending", "pendiente" -> Color(0xFFC9B800)
        "in_progress", "en proceso", "vigente" -> Color(0xFF4CAF50)
        "completed", "completado", "finalizado" -> Color(0xFF1976D2)
        "cancelled", "cancelado" -> Color(0xFFE53935)
        else -> Color.Black
    }
}

private fun formatearFechaCorta(fecha: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}

private fun formatearFechaCompleta(fecha: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}

private fun formatearFechaOpcional(fecha: Long?): String {
    if (fecha == null) return "No registrado"

    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}

private fun parseFechaInicioLista(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null

    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.isLenient = false
        sdf.parse(fechaTexto)?.time
    } catch (e: Exception) {
        null
    }
}

private fun parseFechaFinLista(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null

    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.isLenient = false

        val fecha = sdf.parse(fechaTexto) ?: return null

        val calendar = Calendar.getInstance()
        calendar.time = fecha
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)

        calendar.timeInMillis

    } catch (e: Exception) {
        null
    }
}
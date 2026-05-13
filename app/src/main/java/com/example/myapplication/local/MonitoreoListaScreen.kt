package com.example.myapplication.local

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    nombreCia: String,
    busquedaFueConSaltoFiltros: Boolean,

    monitoreos: List<LocalPhytomonitoringHeaderEntity>,
    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    programas: List<LocalProgramEntity>,

    onBackClick: () -> Unit,
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

        val cumpleFechaInicio = fechaInicioMillis == null || header.estStartDate >= fechaInicioMillis
        val cumpleFechaFin = fechaFinMillis == null || header.estStartDate <= fechaFinMillis

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

            HeaderLista(
                nombreUsuario = nombreUsuario,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "Información general",
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0E7E7))
                            .padding(10.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    TextoInfo(
                        titulo = "CIA:",
                        valor = nombreCia
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!busquedaFueConSaltoFiltros) {
                        TextoInfo(
                            titulo = "Productor:",
                            valor = primerProductor?.commercial_name ?: "-"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextoInfo(
                            titulo = "Rancho:",
                            valor = primerRancho?.name ?: "-"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextoInfo(
                            titulo = "Parcela:",
                            valor = primeraParcela?.code ?: "-"
                        )
                    } else {
                        Text(
                            text = "Se muestran monitoreos disponibles sin aplicar productor, rancho o parcela.",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Filtros de la lista",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SelectorFiltroLista(
                        label = "Ciclo",
                        selected = cicloFiltro,
                        items = programas,
                        itemText = { it.cycle },
                        enabled = programas.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        onSelected = { cicloFiltro = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CampoFechaLista(
                            label = "Fecha inicio",
                            value = fechaInicioTexto,
                            onValueChange = { fechaInicioTexto = it },
                            modifier = Modifier.weight(1f)
                        )

                        CampoFechaLista(
                            label = "Fecha fin",
                            value = fechaFinTexto,
                            onValueChange = { fechaFinTexto = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Estado",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EstadoBotonFiltro(
                            texto = "Todos",
                            seleccionado = estadoFiltro == "Todos",
                            modifier = Modifier.weight(1f),
                            onClick = { estadoFiltro = "Todos" }
                        )

                        EstadoBotonFiltro(
                            texto = "Pendiente",
                            seleccionado = estadoFiltro == "Pendiente",
                            modifier = Modifier.weight(1f),
                            onClick = { estadoFiltro = "Pendiente" }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EstadoBotonFiltro(
                            texto = "En proceso",
                            seleccionado = estadoFiltro == "En proceso",
                            modifier = Modifier.weight(1f),
                            onClick = { estadoFiltro = "En proceso" }
                        )

                        EstadoBotonFiltro(
                            texto = "Completado",
                            seleccionado = estadoFiltro == "Completado",
                            modifier = Modifier.weight(1f),
                            onClick = { estadoFiltro = "Completado" }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            cicloFiltro = null
                            fechaInicioTexto = ""
                            fechaFinTexto = ""
                            estadoFiltro = "Todos"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp)
                    ) {
                        Text(
                            text = "Saltar filtros",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )

                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

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
                nombreCia = nombreCia,
                onDismiss = {
                    monitoreoInfoSeleccionado = null
                }
            )
        }
    }
}

@Composable
private fun HeaderLista(
    nombreUsuario: String,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52AFC4)),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("↩", color = Color.White, fontSize = 22.sp)
        }

        Text(
            text = "☰",
            modifier = Modifier.align(Alignment.TopEnd),
            fontSize = 28.sp,
            color = Color.Black
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(22.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🟩", fontSize = 34.sp)

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "TIERRA",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )

                    Text(
                        text = "INTELIGENTE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Bienvenido $nombreUsuario",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF666666)
            )
        }
    }

    Spacer(modifier = Modifier.height(125.dp))
}

@Composable
private fun TextoInfo(
    titulo: String,
    valor: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = titulo,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = valor,
            fontSize = 12.sp,
            color = Color.DarkGray
        )
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
private fun <T> SelectorFiltroLista(
    label: String,
    selected: T?,
    items: List<T>,
    itemText: (T) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Black,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Box {
            OutlinedButton(
                onClick = {
                    if (enabled) expanded = true
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = selected?.let(itemText) ?: "Todos",
                    modifier = Modifier.weight(1f),
                    color = Color.Black,
                    maxLines = 1
                )

                Text(
                    text = "➜",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (items.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin datos") },
                        onClick = { expanded = false }
                    )
                } else {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(itemText(item)) },
                            onClick = {
                                onSelected(item)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoFechaLista(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
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

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Black,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        OutlinedButton(
            onClick = {
                abrirCalendario()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = value.ifBlank { "Fecha" },
                color = if (value.isBlank()) Color.Gray else Color.Black,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )

            Text(
                text = "📅",
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EstadoBotonFiltro(
    texto: String,
    seleccionado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (seleccionado) {
                Color(0xFFE9E0FA)
            } else {
                Color(0xFFF6EEEE)
            }
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = texto,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DetalleMonitoreoDialog(
    header: LocalPhytomonitoringHeaderEntity,
    programa: LocalProgramEntity?,
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?,
    nombreCia: String,
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
                DatoDetalle("CIA:", nombreCia)
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
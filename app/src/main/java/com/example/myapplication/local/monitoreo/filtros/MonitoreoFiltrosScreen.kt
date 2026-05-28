package com.example.myapplication.local.monitoreo.filtros

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.common.ImageUriBox
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MonitoreoFiltrosScreen(
    nombreUsuario: String,
    rolUsuario: String = "",
    nombreCia: String,

    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,

    productorSeleccionado: LocalAgroUnitEntity?,
    ranchoSeleccionado: LocalRanchEntity?,
    parcelaSeleccionada: LocalPlotEntity?,

    monitoreos: List<LocalPhytomonitoringHeaderEntity> = emptyList(),
    productoresResultado: List<LocalAgroUnitEntity> = emptyList(),
    ranchosResultado: List<LocalRanchEntity> = emptyList(),
    parcelasResultado: List<LocalPlotEntity> = emptyList(),
    programasResultado: List<LocalProgramEntity> = emptyList(),
    cultivosResultado: List<LocalCropCatalogEntity> = emptyList(),

    onProductorChange: (LocalAgroUnitEntity) -> Unit,
    onRanchoChange: (LocalRanchEntity?) -> Unit,
    onParcelaChange: (LocalPlotEntity?) -> Unit,

    onAbrirMapaClick: (LocalPhytomonitoringHeaderEntity) -> Unit = {},
    onAbrirReporteClick: (LocalPhytomonitoringHeaderEntity) -> Unit = {},
    onPerfilClick: () -> Unit = {},
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val rolNormalizado = remember(rolUsuario) { normalizarRolFiltro(rolUsuario) }
    val esAdmin = rolNormalizado == "admin"
    val soloConsulta = rolNormalizado == "gerente" || rolNormalizado == "supervisor"

    val productoresMap = remember(productoresResultado) {
        productoresResultado.associateBy { it.idLocalAgroUnit }
    }
    val ranchosMap = remember(ranchosResultado) {
        ranchosResultado.associateBy { it.idLocalRanch }
    }
    val parcelasMap = remember(parcelasResultado) {
        parcelasResultado.associateBy { it.idLocalPlot }
    }
    val programasMap = remember(programasResultado) {
        programasResultado.associateBy { it.idProgram }
    }
    val cultivosMap = remember(cultivosResultado) {
        cultivosResultado.associateBy { it.idCrop }
    }

    var filtrosExpandidos by remember { mutableStateOf(true) }

    key(
        productorSeleccionado?.idLocalAgroUnit,
        ranchoSeleccionado?.idLocalRanch,
        parcelaSeleccionada?.idLocalPlot
    ) {
        var cicloFiltro by remember { mutableStateOf<LocalProgramEntity?>(null) }
        var fechaInicioTexto by remember { mutableStateOf("") }
        var fechaFinTexto by remember { mutableStateOf("") }
        var estadoFiltro by remember { mutableStateOf("Todos") }

        val programasDisponibles = remember(monitoreos, programasResultado) {
            val idsProgramas = monitoreos.map { it.idProgram }.distinct().toSet()
            programasResultado
                .filter { it.idProgram in idsProgramas }
                .distinctBy { it.idProgram }
                .sortedByDescending { it.estStartDate }
        }

        val fechaInicioMillis = parseFechaInicioFiltro(fechaInicioTexto)
        val fechaFinMillis = parseFechaFinFiltro(fechaFinTexto)

        val monitoreosFiltrados = remember(
            monitoreos,
            cicloFiltro,
            fechaInicioMillis,
            fechaFinMillis,
            estadoFiltro
        ) {
            monitoreos.filter { header ->
                val cumpleCiclo = cicloFiltro == null || header.idProgram == cicloFiltro?.idProgram
                val inicioHeader = header.estStartDate ?: 0L
                val cumpleFechaInicio = fechaInicioMillis == null || inicioHeader >= fechaInicioMillis
                val cumpleFechaFin = fechaFinMillis == null || inicioHeader <= fechaFinMillis
                val estado = header.status.trim().lowercase(Locale.getDefault())

                val cumpleEstado = when (estadoFiltro) {
                    "Pendiente" -> estado == "pending" || estado == "pendiente"
                    "En proceso" -> estado == "in_progress" || estado == "en proceso" || estado == "vigente"
                    "Completado" -> estado == "completed" || estado == "completado" || estado == "finalizado"
                    "Cancelado" -> estado == "cancelled" || estado == "cancelado" || estado == "canceled"
                    else -> true
                }

                cumpleCiclo && cumpleFechaInicio && cumpleFechaFin && cumpleEstado
            }
        }

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
                    nombreUsuario = nombreUsuario,
                    rolUsuario = rolUsuario,
                    onPerfilClick = onPerfilClick,
                    onMonitoreosClick = onMonitoreosClick,
                    onAdminClick = onAdminClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CIA seleccionada: $nombreCia",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                FiltrosPrincipalesCard(
                    filtrosExpandidos = filtrosExpandidos,
                    onExpandChange = { filtrosExpandidos = !filtrosExpandidos },
                    productores = productores,
                    ranchos = ranchos,
                    parcelas = parcelas,
                    productorSeleccionado = productorSeleccionado,
                    ranchoSeleccionado = ranchoSeleccionado,
                    parcelaSeleccionada = parcelaSeleccionada,
                    onProductorChange = onProductorChange,
                    onRanchoChange = onRanchoChange,
                    onParcelaChange = onParcelaChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (productorSeleccionado != null) {
                    FiltrosSecundarios(
                        cicloFiltro = cicloFiltro,
                        programas = programasDisponibles,
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

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (productorSeleccionado == null) {
                    EstadoVacioSeleccionProductor()
                } else {
                    Text(
                        text = "Monitoreos disponibles: ${monitoreosFiltrados.size}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF173B1A),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (monitoreosFiltrados.isEmpty()) {
                        EstadoVacioSinResultados()
                    } else {
                        monitoreosFiltrados.forEach { header ->
                            val programa = programasMap[header.idProgram]
                            val parcela = parcelasMap[header.idLocalPlot]
                            val rancho = parcela?.let { ranchosMap[it.idLocalRanch] }
                            val productor = programa?.let { productoresMap[it.idLocalAgroUnit] }
                            val cultivo = cultivosMap[header.idCrop] ?: programa?.let { cultivosMap[it.idCrop] }
                            val cerrado = esEstadoCerradoFiltro(header.status)

                            TarjetaMonitoreoConsulta(
                                header = header,
                                parcelaNombre = parcela?.let { obtenerNombreParcelaFiltro(it) } ?: "Parcela ${header.idLocalPlot}",
                                productorNombre = productor?.commercial_name ?: productorSeleccionado.commercial_name,
                                ranchoNombre = rancho?.name,
                                ciclo = programa?.cycle ?: header.cycle,
                                codigo = header.extId ?: programa?.extId ?: "MON-${header.idHeader}",
                                fotoCultivo = cultivo?.photo,
                                nombreCultivo = cultivo?.name,
                                mostrarAbrir = esAdmin && !cerrado,
                                mostrarReporte = soloConsulta || esAdmin || cerrado,
                                onAbrirClick = {
                                    if (soloConsulta || cerrado) {
                                        onAbrirReporteClick(header)
                                    } else {
                                        onAbrirMapaClick(header)
                                    }
                                },
                                onReporteClick = { onAbrirReporteClick(header) }
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FiltrosPrincipalesCard(
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
private fun FiltrosSecundarios(
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
private fun <T> SelectorFiltro(
    label: String,
    selected: T?,
    items: List<T>,
    itemText: (T) -> String,
    emptyText: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Box {
            OutlinedButton(
                onClick = { if (enabled) expanded = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = selected?.let(itemText) ?: emptyText,
                    modifier = Modifier.weight(1f),
                    color = if (selected == null) Color.Gray else Color.Black,
                    maxLines = 1,
                    fontSize = 12.sp,
                    fontWeight = if (selected == null) FontWeight.Normal else FontWeight.Bold
                )

                Text(text = "⌄", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun <T> SelectorFiltroConTodos(
    label: String,
    selected: T?,
    allText: String,
    items: List<T>,
    itemText: (T) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Box {
            OutlinedButton(
                onClick = { if (enabled) expanded = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = selected?.let(itemText) ?: allText,
                    modifier = Modifier.weight(1f),
                    color = if (enabled) Color.Black else Color.Gray,
                    maxLines = 1,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(text = "⌄", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(allText) },
                    onClick = {
                        onSelected(null)
                        expanded = false
                    }
                )

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

@Composable
private fun SelectorFiltroOpcionalPrograma(
    icono: String,
    label: String,
    selected: LocalProgramEntity?,
    items: List<LocalProgramEntity>,
    modifier: Modifier = Modifier,
    onSelected: (LocalProgramEntity?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(text = icono, fontSize = 17.sp)
            Spacer(modifier = Modifier.width(7.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 10.sp, color = Color.DarkGray, maxLines = 1)
                Text(
                    text = selected?.cycle ?: "Todos",
                    fontSize = 12.sp,
                    color = Color.Black,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(text = "⌄", fontSize = 13.sp, color = Color.Black)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            items.forEach { programa ->
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
private fun SelectorFechaFiltro(
    icono: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val formato = remember {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply { isLenient = false }
    }

    fun abrirCalendario() {
        val calendar = Calendar.getInstance()
        parseFechaInicioFiltro(value)?.let { calendar.timeInMillis = it }

        DatePickerDialog(
            context,
            { _, year, month, day ->
                val fecha = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onValueChange(formato.format(fecha.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    OutlinedButton(
        onClick = { abrirCalendario() },
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(text = icono, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(7.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 10.sp, color = Color.DarkGray, maxLines = 1)
            Text(
                text = value.ifBlank { "Fecha" },
                color = if (value.isBlank()) Color.Gray else Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(text = "⌄", fontSize = 13.sp, color = Color.Black)
    }
}

@Composable
private fun SelectorEstadoFiltro(
    icono: String,
    label: String,
    selected: String,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val estados = listOf("Todos", "Pendiente", "En proceso", "Completado", "Cancelado")

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(text = icono, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(7.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 10.sp, color = Color.DarkGray, maxLines = 1)
                Text(
                    text = selected,
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(text = "⌄", color = Color.Black, fontSize = 13.sp)
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

@Composable
private fun EstadoVacioSeleccionProductor() {
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
private fun EstadoVacioSinResultados() {
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
            Text(text = "Sin monitoreos", fontWeight = FontWeight.Bold, color = Color(0xFF173B1A))
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
private fun TarjetaMonitoreoConsulta(
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
    onAbrirClick: () -> Unit,
    onReporteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E8DD), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (mostrarAbrir) onAbrirClick() else if (mostrarReporte) onReporteClick()
                    },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChipEstadoFiltro(status = header.status)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formatoFechaCorta(header.estStartDate),
                            fontSize = 10.sp,
                            color = Color.DarkGray
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
                        DatoTarjeta(label = "Ciclo", value = ciclo, modifier = Modifier.weight(1f))
                        DatoTarjeta(label = "Código", value = codigo, modifier = Modifier.weight(1f))
                        DatoTarjeta(label = "Parcela", value = parcelaNombre, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "⌂  ${ranchoNombre ?: "Sin rancho"}  ·  $productorNombre",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "›", fontSize = 26.sp, color = Color(0xFF173B1A), fontWeight = FontWeight.Bold)
            }

            if (mostrarAbrir || mostrarReporte) {
                Spacer(modifier = Modifier.height(10.dp))
                if (mostrarAbrir) {
                    Button(
                        onClick = onAbrirClick,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B6B20))
                    ) {
                        Text("Abrir monitoreo", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onReporteClick,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ver reporte", color = Color(0xFF0B6B20), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DatoTarjeta(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 4.dp)) {
        Text(text = label, fontSize = 9.sp, color = Color.Gray)
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

private fun resumenFiltrosPrincipales(
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?
): String {
    return "Productor: ${productor?.commercial_name ?: "Sin seleccionar"}  |  " +
            "Rancho: ${rancho?.name ?: "Todos"}  |  " +
            "Parcela: ${parcela?.let { obtenerNombreParcelaFiltro(it) } ?: "Todas"}"
}

private fun obtenerNombreParcelaFiltro(parcela: LocalPlotEntity): String {
    return parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
}

private fun textoEstadoFiltro(status: String): String {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado" -> "Completado"
        "cancelled", "cancelado", "canceled" -> "Cancelado"
        else -> status.ifBlank { "Pendiente" }
    }
}

private fun iconoCultivoFallbackFiltro(nombreCultivo: String?): String {
    val cultivo = nombreCultivo
        ?.trim()
        ?.lowercase(Locale.getDefault())
        ?: return "🌱"

    return when {
        cultivo.contains("maiz") || cultivo.contains("maíz") -> "🌽"
        cultivo.contains("papa") -> "🥔"
        cultivo.contains("trigo") -> "🌾"
        cultivo.contains("cebolla") -> "🧅"
        cultivo.contains("chile") -> "🌶️"
        cultivo.contains("tomate") || cultivo.contains("jitomate") -> "🍅"
        cultivo.contains("lechuga") -> "🥬"
        cultivo.contains("zanahoria") -> "🥕"
        else -> "🌱"
    }
}

private fun colorFondoImagen(status: String): Color {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "completed", "completado", "finalizado" -> Color(0xFFE5F4DF)
        "cancelled", "cancelado", "canceled" -> Color(0xFFFFE1E1)
        "in_progress", "en proceso", "vigente" -> Color(0xFFE7F0FF)
        else -> Color(0xFFFFF4CC)
    }
}

private fun normalizarRolFiltro(rol: String): String {
    val limpio = rol
        .trim()
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(".", "")
        .replace("_", " ")
        .replace(Regex("\\s+"), " ")

    return when (limpio) {
        "super admin", "admin", "administrador" -> "admin"
        "gerente" -> "gerente"
        "ingy supervision", "ing y supervision", "supervisor" -> "supervisor"
        "tecnico", "tecnicos", "técnico", "técnicos" -> "tecnico"
        "invitado" -> "invitado"
        else -> limpio
    }
}

private fun formatoFechaCorta(fecha: Long?): String {
    if (fecha == null) return "-"
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
        sdf.format(Date(fecha)).replace(".", "")
    } catch (e: Exception) {
        "-"
    }
}

private fun parseFechaInicioFiltro(texto: String): Long? {
    if (texto.isBlank()) return null
    return parseFechaFiltroFlexible(texto)
}

private fun parseFechaFinFiltro(texto: String): Long? {
    if (texto.isBlank()) return null
    val time = parseFechaFiltroFlexible(texto) ?: return null
    return Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

private fun parseFechaFiltroFlexible(texto: String): Long? {
    val formatos = listOf("dd-MM-yyyy", "dd/MM/yyyy")
    formatos.forEach { patron ->
        try {
            val sdf = SimpleDateFormat(patron, Locale.getDefault()).apply { isLenient = false }
            return sdf.parse(texto)?.time
        } catch (_: Exception) {
        }
    }
    return null
}

private fun esEstadoCerradoFiltro(status: String): Boolean {
    return when (status.trim().lowercase(Locale.getDefault())) {
        "completed",
        "completado",
        "finalizado",
        "terminado",
        "cerrado",
        "cancelado",
        "cancelled",
        "canceled" -> true
        else -> false
    }
}

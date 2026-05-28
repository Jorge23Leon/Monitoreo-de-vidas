package com.example.myapplication.local.monitoreo.lista

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
fun MonitoreoListaScreen(
    nombreUsuario: String,
    rolUsuario: String = "",
    busquedaFueConSaltoFiltros: Boolean,

    monitoreos: List<LocalPhytomonitoringHeaderEntity>,
    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    programas: List<LocalProgramEntity>,
    cultivos: List<LocalCropCatalogEntity> = emptyList(),

    onAbrirMapaClick: (LocalPhytomonitoringHeaderEntity) -> Unit,
    onAbrirReporteClick: (LocalPhytomonitoringHeaderEntity) -> Unit,
    onPerfilClick: () -> Unit = {},
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val rolNormalizado = remember(rolUsuario) { normalizarRolLista(rolUsuario) }
    val esAdmin = rolNormalizado == "admin"
    val esGerente = rolNormalizado == "gerente"
    val esSupervisor = rolNormalizado == "supervisor"
    val esTecnico = rolNormalizado == "tecnico"
    val esInvitado = rolNormalizado == "invitado"
    val soloConsulta = esGerente || esSupervisor

    val productoresMap = remember(productores) { productores.associateBy { it.idLocalAgroUnit } }
    val ranchosMap = remember(ranchos) { ranchos.associateBy { it.idLocalRanch } }
    val parcelasMap = remember(parcelas) { parcelas.associateBy { it.idLocalPlot } }
    val programasMap = remember(programas) { programas.associateBy { it.idProgram } }
    val cultivosMap = remember(cultivos) { cultivos.associateBy { it.idCrop } }

    val idsParcelasConMonitoreo = remember(monitoreos) {
        monitoreos.map { it.idLocalPlot }.distinct().toSet()
    }

    val parcelasDisponibles = remember(parcelas, idsParcelasConMonitoreo) {
        parcelas
            .filter { it.idLocalPlot in idsParcelasConMonitoreo }
            .distinctBy { it.idLocalPlot }
            .sortedBy { obtenerNombreParcelaLista(it) }
    }

    var idParcelaSeleccionada by remember(parcelasDisponibles, rolNormalizado) {
        mutableStateOf<Long?>(null)
    }

    val parcelaSeleccionada = remember(parcelasDisponibles, idParcelaSeleccionada) {
        idParcelaSeleccionada?.let { id -> parcelasDisponibles.firstOrNull { it.idLocalPlot == id } }
    }

    val monitoreosPorParcela = remember(monitoreos, idParcelaSeleccionada) {
        idParcelaSeleccionada?.let { id -> monitoreos.filter { it.idLocalPlot == id } } ?: monitoreos
    }

    val idsProgramasPorParcela = remember(monitoreosPorParcela) {
        monitoreosPorParcela.map { it.idProgram }.distinct().toSet()
    }

    val programasPorParcela = remember(programas, idsProgramasPorParcela) {
        programas
            .filter { it.idProgram in idsProgramasPorParcela }
            .distinctBy { it.idProgram }
            .sortedByDescending { it.estStartDate }
    }

    var cicloFiltro by remember(idParcelaSeleccionada) { mutableStateOf<LocalProgramEntity?>(null) }
    var fechaInicioTexto by remember { mutableStateOf("") }
    var fechaFinTexto by remember { mutableStateOf("") }
    var estadoFiltro by remember { mutableStateOf("Todos") }

    val fechaInicioMillis = parseFechaInicioLista(fechaInicioTexto)
    val fechaFinMillis = parseFechaFinLista(fechaFinTexto)

    val monitoreosFiltrados = remember(
        monitoreosPorParcela,
        cicloFiltro,
        fechaInicioMillis,
        fechaFinMillis,
        estadoFiltro
    ) {
        monitoreosPorParcela.filter { header ->
            val cumpleCiclo = cicloFiltro == null || header.idProgram == cicloFiltro?.idProgram
            val inicioHeader = header.estStartDate ?: 0L
            val cumpleFechaInicio = fechaInicioMillis == null || inicioHeader >= fechaInicioMillis
            val cumpleFechaFin = fechaFinMillis == null || inicioHeader <= fechaFinMillis
            val estado = header.status.lowercase(Locale.getDefault()).trim()
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

    val tituloPantalla = when {
        esInvitado -> "Mis monitoreos"
        esTecnico -> "Mis parcelas asignadas"
        soloConsulta -> "Monitoreos para consulta"
        else -> "Monitoreos"
    }

    val subtituloPantalla = when {
        esInvitado -> "Consulta los monitoreos asignados a tu usuario."
        esTecnico -> "Abre y captura los monitoreos de tus parcelas asignadas."
        esGerente -> "Consulta los monitoreos de tus CIAS asignadas."
        esSupervisor -> "Revisa información de los monitoreos de tus CIAS hijas."
        else -> "Accede, revisa y administra los monitoreos disponibles."
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

            Spacer(modifier = Modifier.height(18.dp))

            EncabezadoListaMonitoreos(
                titulo = tituloPantalla,
                subtitulo = subtituloPantalla,
                rolEtiqueta = when {
                    esInvitado -> "Rol: Invitado"
                    esTecnico -> "Rol: Técnico"
                    esGerente -> "Rol: Gerente"
                    esSupervisor -> "Rol: Supervisor"
                    esAdmin -> "Rol: Administrador"
                    else -> rolUsuario
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (esInvitado) {
                CajaAvisoMonitoreos(
                    icono = "🔒",
                    textoTitulo = "Usuario invitado",
                    textoDescripcion = "Solo puedes ver monitoreos asignados o consultables para tu usuario."
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (esTecnico && parcelasDisponibles.isNotEmpty()) {
                SelectorParcelaOpcionalMonitoreo(
                    parcelaSeleccionada = parcelaSeleccionada,
                    parcelas = parcelasDisponibles,
                    onParcelaSeleccionada = { parcela ->
                        idParcelaSeleccionada = parcela?.idLocalPlot
                        cicloFiltro = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            FiltrosCompactosBarra(
                cicloFiltro = cicloFiltro,
                programas = programasPorParcela,
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

            Text(
                text = if (esInvitado) {
                    "Monitoreos asignados: ${monitoreosFiltrados.size}"
                } else {
                    "Monitoreos disponibles: ${monitoreosFiltrados.size}"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF173B1A),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (monitoreosFiltrados.isEmpty()) {
                CajaAvisoMonitoreos(
                    icono = "ⓘ",
                    textoTitulo = "Sin monitoreos",
                    textoDescripcion = if (esInvitado) {
                        "No tienes monitoreos asignados con los filtros actuales."
                    } else {
                        "No se encontraron monitoreos con la selección actual."
                    }
                )
            } else {
                monitoreosFiltrados.forEach { header ->
                    val programa = programasMap[header.idProgram]
                    val parcela = parcelasMap[header.idLocalPlot]
                    val rancho = parcela?.let { ranchosMap[it.idLocalRanch] }
                    val productor = programa?.let { productoresMap[it.idLocalAgroUnit] }
                    val cultivo = cultivosMap[header.idCrop] ?: programa?.let { cultivosMap[it.idCrop] }
                    val monitoreoCerrado = esEstadoCerradoLista(header.status)

                    TarjetaMonitoreoUsuario(
                        parcelaNombre = parcela?.let { obtenerNombreParcelaLista(it) } ?: "Parcela ${header.idLocalPlot}",
                        productorNombre = productor?.commercial_name,
                        ranchoNombre = rancho?.name,
                        ciclo = programa?.cycle ?: header.cycle,
                        codigo = header.extId ?: programa?.extId ?: "MON-${header.idHeader}",
                        fotoCultivo = cultivo?.photo,
                        nombreCultivo = cultivo?.name,
                        status = header.status,
                        fechaInicio = header.estStartDate,
                        fechaFin = header.estFinishDate,
                        puedeAbrir = (esAdmin || esTecnico || esInvitado) && !monitoreoCerrado,
                        soloConsulta = soloConsulta,
                        onAbrirClick = {
                            if (soloConsulta || monitoreoCerrado) {
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EncabezadoListaMonitoreos(
    titulo: String,
    subtitulo: String,
    rolEtiqueta: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "▣", fontSize = 22.sp, color = Color(0xFF0B6B20))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF173B1A)
                    )

                    Text(
                        text = subtitulo,
                        fontSize = 12.sp,
                        color = Color(0xFF5E6B5B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = rolEtiqueta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0B6B20),
                modifier = Modifier
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(30.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CajaAvisoMonitoreos(
    icono: String,
    textoTitulo: String,
    textoDescripcion: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icono, fontSize = 22.sp, color = Color(0xFF0B6B20))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = textoTitulo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF173B1A)
                )
                Text(
                    text = textoDescripcion,
                    fontSize = 12.sp,
                    color = Color(0xFF4E5D45)
                )
            }
        }
    }
}

@Composable
private fun SelectorParcelaOpcionalMonitoreo(
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
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(text = "▦", fontSize = 16.sp, color = Color(0xFF0B6B20))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Parcela",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5F6F5A)
            )
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

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onLimpiarClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "↻  Limpiar filtros",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F6D2A),
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
                .width(142.dp)
                .height(62.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(text = "↻", fontSize = 18.sp, color = Color.Black)
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Ciclo", fontSize = 10.sp, color = Color.DarkGray, maxLines = 1)
                Text(
                    text = cicloFiltro?.cycle ?: "Todos",
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
        modifier = Modifier
            .width(142.dp)
            .height(62.dp),
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
    val estados = listOf("Todos", "Pendiente", "En proceso", "Completado", "Cancelado")

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .width(142.dp)
                .height(62.dp),
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

@Composable
private fun TarjetaMonitoreoUsuario(
    parcelaNombre: String,
    productorNombre: String?,
    ranchoNombre: String?,
    ciclo: String,
    codigo: String,
    fotoCultivo: String?,
    nombreCultivo: String?,
    status: String,
    fechaInicio: Long?,
    fechaFin: Long?,
    puedeAbrir: Boolean,
    soloConsulta: Boolean,
    onAbrirClick: () -> Unit,
    onReporteClick: () -> Unit
) {
    val monitoreoCerrado = esEstadoCerradoLista(status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFDCE8D8), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
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
                    .clickable {
                        if (puedeAbrir) onAbrirClick() else onReporteClick()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImageUriBox(
                    photo = fotoCultivo,
                    fallbackIcon = iconoCultivoFallbackLista(nombreCultivo),
                    sizeDp = 82,
                    modifier = Modifier
                        .size(width = 92.dp, height = 82.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorFondoImagenLista(status))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EstadoChip(status = status)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formatearFechaResumen(fechaInicio),
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = parcelaNombre,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF173B1A),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DatoTarjetaUsuario(label = "Ciclo", value = ciclo, modifier = Modifier.weight(1f))
                        DatoTarjetaUsuario(label = "Código", value = codigo, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "⌂ ${ranchoNombre ?: "Sin rancho"}  ·  ${productorNombre ?: "Sin productor"}",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )

                    Text(
                        text = "📅 ${formatearFechaCorta(fechaInicio)}  al  ${formatearFechaCorta(fechaFin)}",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (puedeAbrir && !soloConsulta && !monitoreoCerrado) {
                Button(
                    onClick = onAbrirClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B6B20))
                ) {
                    Text(
                        text = "Abrir monitoreo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
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
                        text = if (monitoreoCerrado) "Ver reporte" else "Ver información",
                        color = Color(0xFF0B6B20),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DatoTarjetaUsuario(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 4.dp)) {
        Text(text = label, fontSize = 9.sp, color = Color.Gray)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun EstadoChip(status: String) {
    Text(
        text = textoEstado(status),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = colorEstado(status),
        modifier = Modifier
            .background(colorFondoEstado(status), RoundedCornerShape(30.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        textAlign = TextAlign.Center
    )
}

private fun obtenerNombreParcelaLista(parcela: LocalPlotEntity): String {
    return parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
}

private fun textoEstado(status: String): String {
    return when (status.lowercase(Locale.getDefault()).trim()) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado" -> "Completado"
        "cancelled", "cancelado", "canceled" -> "Cancelado"
        else -> status.ifBlank { "Pendiente" }
    }
}

private fun colorEstado(status: String): Color {
    return when (status.lowercase(Locale.getDefault()).trim()) {
        "pending", "pendiente" -> Color(0xFF9A6A00)
        "in_progress", "en proceso", "vigente" -> Color(0xFF255EA8)
        "completed", "completado", "finalizado" -> Color(0xFF1F6D2A)
        "cancelled", "cancelado", "canceled" -> Color(0xFFB3261E)
        else -> Color.Black
    }
}

private fun colorFondoEstado(status: String): Color {
    return when (status.lowercase(Locale.getDefault()).trim()) {
        "pending", "pendiente" -> Color(0xFFFFF4CC)
        "in_progress", "en proceso", "vigente" -> Color(0xFFE2F0FF)
        "completed", "completado", "finalizado" -> Color(0xFFE4F4DF)
        "cancelled", "cancelado", "canceled" -> Color(0xFFFFE1E1)
        else -> Color(0xFFEDEDED)
    }
}

private fun colorFondoImagenLista(status: String): Color {
    return when (status.lowercase(Locale.getDefault()).trim()) {
        "completed", "completado", "finalizado" -> Color(0xFFE5F4DF)
        "cancelled", "cancelado", "canceled" -> Color(0xFFFFE1E1)
        "in_progress", "en proceso", "vigente" -> Color(0xFFE7F0FF)
        else -> Color(0xFFFFF4CC)
    }
}

private fun iconoCultivoFallbackLista(nombreCultivo: String?): String {
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

private fun normalizarRolLista(rol: String): String {
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

private fun formatearFechaCorta(fecha: Long?): String {
    if (fecha == null) return "-"
    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.format(Date(fecha))
    } catch (e: Exception) {
        "-"
    }
}

private fun formatearFechaResumen(fecha: Long?): String {
    if (fecha == null) return "-"
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
        sdf.format(Date(fecha)).replace(".", "")
    } catch (e: Exception) {
        "-"
    }
}

private fun parseFechaInicioLista(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null
    return parseFechaListaFlexible(fechaTexto)
}

private fun parseFechaFinLista(fechaTexto: String): Long? {
    if (fechaTexto.isBlank()) return null
    val time = parseFechaListaFlexible(fechaTexto) ?: return null
    return Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

private fun parseFechaListaFlexible(texto: String): Long? {
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

private fun esEstadoCerradoLista(status: String): Boolean {
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

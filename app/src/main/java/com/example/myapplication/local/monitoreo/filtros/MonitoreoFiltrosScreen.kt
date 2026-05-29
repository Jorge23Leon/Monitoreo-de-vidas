package com.example.myapplication.local.monitoreo.filtros

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.core.normalizarRolVm
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity

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
    val rolNormalizado = remember(rolUsuario) {
        normalizarRolVm(rolUsuario)
    }

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
            val idsProgramas = monitoreos
                .map { it.idProgram }
                .distinct()
                .toSet()

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
                val cumpleEstado = cumpleEstadoFiltro(header.status, estadoFiltro)

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
                                parcelaNombre = parcela?.let {
                                    obtenerNombreParcelaFiltro(it)
                                } ?: "Parcela ${header.idLocalPlot}",
                                productorNombre = productor?.commercial_name
                                    ?: productorSeleccionado.commercial_name,
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
                                onReporteClick = {
                                    onAbrirReporteClick(header)
                                }
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
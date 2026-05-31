package com.example.myapplication.local.monitoreo.lista

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

@Suppress("UNUSED_PARAMETER")
@Composable
fun MonitoreoListaScreen(
    nombreUsuario: String,
    rolUsuario: String = "",
    busquedaFueConSaltoFiltros: Boolean,
    monitoreos: List<LocalPhytomonitoringHeaderEntity>,
    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    onCerrarSesionClick: () -> Unit,
    programas: List<LocalProgramEntity>,
    cultivos: List<LocalCropCatalogEntity> = emptyList(),
    onAbrirMapaClick: (LocalPhytomonitoringHeaderEntity) -> Unit,
    onAbrirReporteClick: (LocalPhytomonitoringHeaderEntity) -> Unit,
    onPerfilClick: () -> Unit = {},
    onCambiarCiaClick: (() -> Unit)? = null,
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val rolNormalizado = remember(rolUsuario) { normalizarRolVm(rolUsuario) }
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
            val cumpleEstado = cumpleEstadoLista(header.status, estadoFiltro)
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
                onAdminClick = onAdminClick,
                onCambiarCiaClick = onCambiarCiaClick,
                onCerrarSesionClick = onCerrarSesionClick
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
                text = if (esInvitado) "Monitoreos asignados: ${monitoreosFiltrados.size}"
                else "Monitoreos disponibles: ${monitoreosFiltrados.size}",
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
                    textoDescripcion = if (esInvitado) "No tienes monitoreos asignados con los filtros actuales."
                    else "No se encontraron monitoreos con la selección actual."
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
                            if (soloConsulta || monitoreoCerrado) onAbrirReporteClick(header) else onAbrirMapaClick(header)
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

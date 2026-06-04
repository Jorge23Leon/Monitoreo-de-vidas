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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
    onCerrarSesionClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)? = null,
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {},

    monitoreos: List<LocalPhytomonitoringHeaderEntity> = emptyList(),
    productoresResultado: List<LocalAgroUnitEntity> = emptyList(),
    ranchosResultado: List<LocalRanchEntity> = emptyList(),
    parcelasResultado: List<LocalPlotEntity> = emptyList(),
    programasResultado: List<LocalProgramEntity> = emptyList(),
    cultivosResultado: List<LocalCropCatalogEntity> = emptyList(),

    onProductorChange: (LocalAgroUnitEntity?) -> Unit,
    onRanchoChange: (LocalRanchEntity?) -> Unit,
    onParcelaChange: (LocalPlotEntity?) -> Unit,

    onAbrirMapaClick: (LocalPhytomonitoringHeaderEntity) -> Unit = {},
    onAbrirReporteClick: (LocalPhytomonitoringHeaderEntity) -> Unit = {},
    onCancelarMonitoreoClick: (LocalPhytomonitoringHeaderEntity, String) -> Unit = { _, _ -> },
    onPerfilClick: () -> Unit = {}
) {
    val rolNormalizado = remember(rolUsuario) {
        normalizarRolVm(rolUsuario)
    }

    val rolParaPermisos = remember(rolUsuario, rolNormalizado) {
        "$rolUsuario $rolNormalizado"
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
    }

    val esAdmin = rolParaPermisos.contains("admin") ||
            rolParaPermisos.contains("administrador")

    val esTecnico = rolParaPermisos.contains("tecnico")
    val esGerente = rolParaPermisos.contains("gerente")
    val esSupervisor = rolParaPermisos.contains("supervisor") ||
            rolParaPermisos.contains("supervision")

    val soloConsulta = esGerente || esSupervisor

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

    var filtrosExpandidos by remember {
        mutableStateOf(true)
    }
    var monitoreoCanceladoParaVer by remember {
        mutableStateOf<LocalPhytomonitoringHeaderEntity?>(null)
    }

    var monitoreoParaIniciar by remember {
        mutableStateOf<LocalPhytomonitoringHeaderEntity?>(null)
    }

    var monitoreoParaCancelar by remember {
        mutableStateOf<LocalPhytomonitoringHeaderEntity?>(null)
    }

    var motivoCancelacion by remember {
        mutableStateOf("")
    }

    var errorMotivoCancelacion by remember {
        mutableStateOf<String?>(null)
    }

    if (monitoreoParaCancelar != null) {
        AlertDialog(
            onDismissRequest = {
                monitoreoParaCancelar = null
                motivoCancelacion = ""
                errorMotivoCancelacion = null
            },
            title = {
                Text(
                    text = "Cancelar monitoreo",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB3261E)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Indica el motivo de cancelación. Esta nota se guardará en el monitoreo.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = motivoCancelacion,
                        onValueChange = { texto ->
                            motivoCancelacion = texto
                            errorMotivoCancelacion = null
                        },
                        label = {
                            Text("Motivo de cancelación")
                        },
                        minLines = 3,
                        isError = errorMotivoCancelacion != null,
                        supportingText = {
                            errorMotivoCancelacion?.let { mensaje ->
                                Text(
                                    text = mensaje,
                                    color = Color(0xFFB3261E)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val header = monitoreoParaCancelar
                        val motivoLimpio = motivoCancelacion.trim()

                        if (header == null) {
                            monitoreoParaCancelar = null
                            return@TextButton
                        }

                        if (motivoLimpio.isBlank()) {
                            errorMotivoCancelacion = "El motivo es obligatorio"
                            return@TextButton
                        }

                        onCancelarMonitoreoClick(header, motivoLimpio)

                        monitoreoParaCancelar = null
                        motivoCancelacion = ""
                        errorMotivoCancelacion = null
                    }
                ) {
                    Text(
                        text = "Confirmar cancelación",
                        color = Color(0xFFB3261E),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        monitoreoParaCancelar = null
                        motivoCancelacion = ""
                        errorMotivoCancelacion = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    monitoreoCanceladoParaVer?.let { headerCancelado ->
        AlertDialog(
            onDismissRequest = {
                monitoreoCanceladoParaVer = null
            },
            title = {
                Text(
                    text = "Motivo de cancelación",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB3261E)
                )
            },
            text = {
                Text(
                    text = headerCancelado.additionalNotes.ifBlank {
                        "Este monitoreo fue cancelado, pero no tiene motivo registrado."
                    },
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        monitoreoCanceladoParaVer = null
                    }
                ) {
                    Text(
                        text = "Aceptar",
                        color = Color(0xFFB3261E),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    if (monitoreoParaIniciar != null) {
        AlertDialog(
            onDismissRequest = {
                monitoreoParaIniciar = null
            },
            title = {
                Text(
                    text = "Iniciar monitoreo",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF173B1A)
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro que quieres iniciar este monitoreo?\n\nAl continuar se abrirá el mapa del monitoreo.",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val header = monitoreoParaIniciar

                        if (header != null) {
                            onAbrirMapaClick(header)
                        }

                        monitoreoParaIniciar = null
                    }
                ) {
                    Text(
                        text = "Sí, iniciar",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B6B20)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        monitoreoParaIniciar = null
                    }
                ) {
                    Text("No")
                }
            }
        )
    }

    key(
        productorSeleccionado?.idLocalAgroUnit,
        ranchoSeleccionado?.idLocalRanch,
        parcelaSeleccionada?.idLocalPlot
    ) {
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
                val cumpleCiclo = cicloFiltro == null ||
                        header.idProgram == cicloFiltro?.idProgram

                val inicioHeader = header.estStartDate ?: 0L

                val cumpleFechaInicio = fechaInicioMillis == null ||
                        inicioHeader >= fechaInicioMillis

                val cumpleFechaFin = fechaFinMillis == null ||
                        inicioHeader <= fechaFinMillis

                val cumpleEstado = cumpleEstadoFiltro(
                    status = header.status,
                    estadoFiltro = estadoFiltro
                )

                cumpleCiclo &&
                        cumpleFechaInicio &&
                        cumpleFechaFin &&
                        cumpleEstado
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
                    onAdminClick = onAdminClick,
                    onCambiarCiaClick = onCambiarCiaClick,
                    onCerrarSesionClick = onCerrarSesionClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = nombreCia,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                FiltrosPrincipalesCard(
                    filtrosExpandidos = filtrosExpandidos,
                    onExpandChange = {
                        filtrosExpandidos = !filtrosExpandidos
                    },
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

                FiltrosSecundarios(
                    cicloFiltro = cicloFiltro,
                    programas = programasDisponibles,
                    fechaInicioTexto = fechaInicioTexto,
                    fechaFinTexto = fechaFinTexto,
                    estadoFiltro = estadoFiltro,
                    onCicloChange = {
                        cicloFiltro = it
                    },
                    onFechaInicioChange = {
                        fechaInicioTexto = it
                    },
                    onFechaFinChange = {
                        fechaFinTexto = it
                    },
                    onEstadoChange = {
                        estadoFiltro = it
                    },
                    onLimpiarClick = {
                        cicloFiltro = null
                        fechaInicioTexto = ""
                        fechaFinTexto = ""
                        estadoFiltro = "Todos"
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                        val rancho = parcela?.let {
                            ranchosMap[it.idLocalRanch]
                        }
                        val productor = programa?.let {
                            productoresMap[it.idLocalAgroUnit]
                        }
                        val cultivo = cultivosMap[header.idCrop] ?: programa?.let {
                            cultivosMap[it.idCrop]
                        }

                        val cerrado = esEstadoCerradoFiltro(header.status)


                        val estadoNormalizado = header.status
                            .trim()
                            .lowercase(Locale.getDefault())

                        val cancelado = estadoNormalizado in listOf(
                            "cancelado",
                            "cancelled",
                            "canceled"
                        )

                        val estadoPendiente = estadoNormalizado == "pendiente" ||
                                estadoNormalizado == "pending"

                        val monitoreoYaIniciado = header.startAt != null ||
                                estadoNormalizado.contains("proceso") ||
                                estadoNormalizado.contains("progress") ||
                                header.additionalNotes.startsWith("PAUSADO", ignoreCase = true)

                        TarjetaMonitoreoConsulta(
                            header = header,
                            parcelaNombre = parcela?.let {
                                obtenerNombreParcelaFiltro(it)
                            } ?: "Parcela ${header.idLocalPlot}",
                            productorNombre = productor?.commercial_name
                                ?: productorSeleccionado?.commercial_name
                                ?: "Sin productor",
                            ranchoNombre = rancho?.name,
                            ciclo = programa?.cycle ?: header.cycle,
                            codigo = header.extId ?: programa?.extId ?: "MON-${header.idHeader}",
                            fotoCultivo = cultivo?.photo,
                            nombreCultivo = cultivo?.name,

                            mostrarAbrir = esAdmin && !cerrado && !cancelado,
                            mostrarReporte = (soloConsulta || esAdmin || cerrado) && !cancelado,

                            puedeCancelar = (esAdmin || esGerente || esSupervisor) &&
                                    estadoPendiente &&
                                    !monitoreoYaIniciado &&
                                    !cerrado &&
                                    !cancelado,

                            estaCancelado = cancelado,

                            onAbrirClick = {
                                if (cancelado) {
                                    return@TarjetaMonitoreoConsulta
                                }

                                if (soloConsulta || cerrado) {
                                    onAbrirReporteClick(header)
                                } else {
                                    onAbrirMapaClick(header)
                                }
                            },

                            onReporteClick = {
                                if (!cancelado) {
                                    onAbrirReporteClick(header)
                                }
                            },

                            onCancelarClick = {
                                monitoreoParaCancelar = header
                                motivoCancelacion = ""
                                errorMotivoCancelacion = null
                            },

                            onVerMotivoCancelacionClick = {
                                monitoreoCanceladoParaVer = header
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
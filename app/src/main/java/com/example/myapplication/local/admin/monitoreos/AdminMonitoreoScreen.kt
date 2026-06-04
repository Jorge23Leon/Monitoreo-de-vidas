package com.example.myapplication.local.admin.monitoreos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminMonitoreoScreen(
    database: AppDatabase,
    nombreUsuario: String,
    rolUsuario: String,
    idLocalCia: Long?,
    nombreCia: String,
    idUsuarioActual: Long,
    onBackClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit,
    onMensaje: (String) -> Unit,
    onCambiarCiaClick: (() -> Unit)? = null,
    onCerrarSesionClick: () -> Unit,
    onMonitoreoCreado: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var cargandoInicial by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }

    var productores by remember { mutableStateOf<List<LocalAgroUnitEntity>>(emptyList()) }
    var ranchos by remember { mutableStateOf<List<LocalRanchEntity>>(emptyList()) }
    var parcelas by remember { mutableStateOf<List<LocalPlotEntity>>(emptyList()) }
    var cultivos by remember { mutableStateOf<List<LocalCropCatalogEntity>>(emptyList()) }
    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }

    var productorSeleccionado by remember { mutableStateOf<LocalAgroUnitEntity?>(null) }
    var ranchoSeleccionado by remember { mutableStateOf<LocalRanchEntity?>(null) }
    var parcelaSeleccionada by remember { mutableStateOf<LocalPlotEntity?>(null) }
    var cultivoSeleccionado by remember { mutableStateOf<LocalCropCatalogEntity?>(null) }

    var ciclo by remember { mutableStateOf("") }
    var fechaInicioMillis by remember { mutableStateOf<Long?>(null) }
    var fechaFinMillis by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(idLocalCia) {
        cargandoInicial = true
        try {
            val idCia = idLocalCia
            if (idCia == null || idCia <= 0L) {
                productores = emptyList()
                cultivos = emptyList()
                onMensaje("Selecciona una CIA antes de crear monitoreos")
            } else {
                val datos = withContext(Dispatchers.IO) {
                    val productoresCia = database.localCiaAgroUnitDao().getProductoresByCia(idCia)
                    val cultivosDb = database.localCropCatalogDao().getAllCrops()
                    productoresCia to cultivosDb
                }

                productores = datos.first
                cultivos = datos.second
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onMensaje("Error al cargar datos: ${e.message}")
        } finally {
            cargandoInicial = false
        }
    }

    fun limpiarDependenciasDesdeProductor() {
        ranchoSeleccionado = null
        parcelaSeleccionada = null
        ranchos = emptyList()
        parcelas = emptyList()
        vertices = emptyList()
    }

    fun limpiarDependenciasDesdeRancho() {
        parcelaSeleccionada = null
        parcelas = emptyList()
        vertices = emptyList()
    }

    fun cargarRanchos(productor: LocalAgroUnitEntity) {
        coroutineScope.launch {
            try {
                ranchos = withContext(Dispatchers.IO) {
                    database.localRanchDao().getRanchosByProductor(productor.idLocalAgroUnit)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar ranchos: ${e.message}")
            }
        }
    }

    fun cargarParcelas(rancho: LocalRanchEntity) {
        coroutineScope.launch {
            try {
                parcelas = withContext(Dispatchers.IO) {
                    if (esRolTecnicoAdminMonitoreo(rolUsuario)) {
                        database.localPlotDao().getParcelasByRanchoAndUser(
                            idRanch = rancho.idLocalRanch,
                            idUser = idUsuarioActual
                        )
                    } else {
                        database.localPlotDao().getParcelasByRancho(rancho.idLocalRanch)
                    }
                }

                if (parcelas.isEmpty() && esRolTecnicoAdminMonitoreo(rolUsuario)) {
                    onMensaje("No tienes parcelas asignadas en este rancho")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar parcelas: ${e.message}")
            }
        }
    }

    fun cargarVertices(parcela: LocalPlotEntity) {
        coroutineScope.launch {
            try {
                vertices = withContext(Dispatchers.IO) {
                    database.LocalPlotVertexDao().getVerticesByPlot(parcela.idLocalPlot)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar vértices: ${e.message}")
            }
        }
    }

    fun reiniciarFormulario() {
        ciclo = ""
        fechaInicioMillis = null
        fechaFinMillis = null
        productorSeleccionado = null
        ranchoSeleccionado = null
        parcelaSeleccionada = null
        cultivoSeleccionado = null
        ranchos = emptyList()
        parcelas = emptyList()
        vertices = emptyList()
    }

    fun validarYGuardar() {
        val idCia = idLocalCia
        val productor = productorSeleccionado
        val rancho = ranchoSeleccionado
        val parcela = parcelaSeleccionada
        val cultivo = cultivoSeleccionado
        val inicioMillis = fechaInicioMillis?.let { normalizarFechaAdmin(it, finDelDia = false) }
        val finMillis = fechaFinMillis?.let { normalizarFechaAdmin(it, finDelDia = true) }
        val cicloLimpio = normalizarCicloMonitoreoAdmin(ciclo)

        when {
            idCia == null || idCia <= 0L -> onMensaje("Selecciona una CIA primero")
            productor == null -> onMensaje("Selecciona un productor")
            rancho == null -> onMensaje("Selecciona un rancho")
            parcela == null -> onMensaje("Selecciona una parcela")
            cultivo == null -> onMensaje("Selecciona un cultivo")
            cicloLimpio.isBlank() -> onMensaje("Escribe el ciclo del monitoreo")
            inicioMillis == null -> onMensaje("Selecciona la fecha de inicio")
            finMillis == null -> onMensaje("Selecciona la fecha fin")
            inicioMillis > finMillis -> onMensaje("La fecha de inicio no puede ser mayor que la fecha fin")
            vertices.size < 3 -> onMensaje("La parcela necesita al menos 3 vértices para mostrar el mapa")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        withContext(Dispatchers.IO) {
                            val idProgram = database.localprogramDao().insertProgram(
                                LocalProgramEntity(
                                    cycle = cicloLimpio,
                                    estStartDate = inicioMillis,
                                    estFinishDate = finMillis,
                                    actStartDate = null,
                                    actFinishDate = null,
                                    status = "Pendiente",
                                    idLocalAgroUnit = productor.idLocalAgroUnit,
                                    idLocalRanch = rancho.idLocalRanch,
                                    idCrop = cultivo.idCrop,
                                    idLocalPlot = parcela.idLocalPlot
                                )
                            )

                            database.localphytomonitoringheaderDao().insertHeader(
                                LocalPhytomonitoringHeaderEntity(
                                    cycle = cicloLimpio,
                                    estStartDate = inicioMillis,
                                    estFinishDate = finMillis,
                                    startAt = null,
                                    finishedAt = null,
                                    status = "Pendiente",
                                    idProgram = idProgram,
                                    idLocalPlot = parcela.idLocalPlot,
                                    idCrop = cultivo.idCrop,
                                    assignedUserId = parcela.assignedUserId
                                )
                            )
                        }

                        onMensaje("Monitoreo creado. Los puntos se registrarán en campo")
                        reiniciarFormulario()
                        onMonitoreoCreado()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMensaje("No se pudo crear el monitoreo: ${e.message}")
                    } finally {
                        guardando = false
                    }
                }
            }
        }
    }

    val avanceFormulario = listOf(
        productorSeleccionado != null,
        ranchoSeleccionado != null,
        parcelaSeleccionada != null,
        cultivoSeleccionado != null,
        ciclo.isNotBlank(),
        fechaInicioMillis != null && fechaFinMillis != null,
        vertices.size >= 3
    ).count { it } / 7f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            AdminMonitorHeroCard(
                nombreCia = nombreCia,
                avance = avanceFormulario,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (cargandoInicial) {
                AdminMonitoreoLoadingCard()
            } else {
                FormularioMonitoreoAdmin(
                    guardando = guardando,
                    productores = productores,
                    ranchos = ranchos,
                    parcelas = parcelas,
                    cultivos = cultivos,
                    vertices = vertices,
                    productorSeleccionado = productorSeleccionado,
                    ranchoSeleccionado = ranchoSeleccionado,
                    parcelaSeleccionada = parcelaSeleccionada,
                    cultivoSeleccionado = cultivoSeleccionado,
                    ciclo = ciclo,
                    fechaInicioMillis = fechaInicioMillis,
                    fechaFinMillis = fechaFinMillis,
                    onProductorSeleccionado = { productor ->
                        productorSeleccionado = productor
                        limpiarDependenciasDesdeProductor()
                        cargarRanchos(productor)
                    },
                    onRanchoSeleccionado = { rancho ->
                        ranchoSeleccionado = rancho
                        limpiarDependenciasDesdeRancho()
                        cargarParcelas(rancho)
                    },
                    onParcelaSeleccionada = { parcela ->
                        parcelaSeleccionada = parcela
                        vertices = emptyList()
                        cargarVertices(parcela)
                    },
                    onCultivoSeleccionado = { cultivo ->
                        cultivoSeleccionado = cultivo
                    },
                    onCicloChange = { ciclo = it },
                    onFechaInicioChange = { fechaInicioMillis = it },
                    onFechaFinChange = { fechaFinMillis = it },
                    onGuardarClick = { validarYGuardar() }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

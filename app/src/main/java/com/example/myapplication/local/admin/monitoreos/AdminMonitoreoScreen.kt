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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.local.api.core.ApiDateParser
import com.example.myapplication.local.api.fieldops.FieldOpsRepository
import com.example.myapplication.local.api.fieldops.FieldTaskApiItem
import com.example.myapplication.local.api.fieldops.MasterProgramApiItem
import com.example.myapplication.local.api.fieldops.ResultadoFieldOpsApi
import com.example.myapplication.local.api.fieldops.ResultadoMasterProgramsApi
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Pantalla de creación administrativa.
 *
 * El Programa Maestro y el Subprograma ya existen en Django. Esta pantalla
 * únicamente selecciona esa estructura, hereda parcela/cultivo/ciclo/fechas
 * y crea el PhytoHeader que se utilizará para capturar checkpoints.
 */
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fieldOpsRepository = remember {
        FieldOpsRepository(context.applicationContext)
    }
    val adminMonitoreoRepository = remember(database) {
        AdminMonitoreoRepository(
            context = context.applicationContext,
            database = database
        )
    }

    var cargandoInicial by remember { mutableStateOf(true) }
    var cargandoProgramas by remember { mutableStateOf(false) }
    var cargandoSubprogramas by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    var productores by remember { mutableStateOf<List<LocalAgroUnitEntity>>(emptyList()) }
    var programas by remember { mutableStateOf<List<MasterProgramApiItem>>(emptyList()) }
    var subprogramas by remember { mutableStateOf<List<SubprogramaAdminItem>>(emptyList()) }
    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }

    var productorSeleccionado by remember { mutableStateOf<LocalAgroUnitEntity?>(null) }
    var programaSeleccionado by remember { mutableStateOf<MasterProgramApiItem?>(null) }
    var subprogramaSeleccionado by remember { mutableStateOf<SubprogramaAdminItem?>(null) }

    LaunchedEffect(idLocalCia) {
        cargandoInicial = true
        try {
            val idCia = idLocalCia
            if (idCia == null || idCia <= 0L) {
                productores = emptyList()
                onMensaje("Selecciona una CIA antes de crear monitoreos")
            } else {
                productores = withContext(Dispatchers.IO) {
                    database.localCiaAgroUnitDao()
                        .getProductoresByCia(idCia)
                        .sortedBy { it.commercial_name.lowercase(Locale.getDefault()) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            productores = emptyList()
            onMensaje("Error al cargar productores: ${e.message}")
        } finally {
            cargandoInicial = false
        }
    }

    fun limpiarDesdeProductor() {
        programaSeleccionado = null
        subprogramaSeleccionado = null
        programas = emptyList()
        subprogramas = emptyList()
        vertices = emptyList()
        cargandoProgramas = false
        cargandoSubprogramas = false
    }

    fun limpiarDesdePrograma() {
        subprogramaSeleccionado = null
        subprogramas = emptyList()
        vertices = emptyList()
        cargandoSubprogramas = false
    }

    fun cargarProgramas(productor: LocalAgroUnitEntity) {
        val productorExtId = productor.ext_Id?.trim().orEmpty()

        if (productorExtId.isBlank()) {
            programas = emptyList()
            onMensaje("El productor no tiene UUID remoto. Sincroniza la información de la CIA.")
            return
        }

        coroutineScope.launch {
            cargandoProgramas = true
            try {
                when (
                    val resultado = withContext(Dispatchers.IO) {
                        fieldOpsRepository.obtenerTodosLosProgramasMaestros(
                            agroUnit = productorExtId
                        )
                    }
                ) {
                    is ResultadoMasterProgramsApi.Exito -> {
                        if (
                            productorSeleccionado?.idLocalAgroUnit !=
                            productor.idLocalAgroUnit
                        ) {
                            return@launch
                        }

                        programas = resultado.programasMaestros
                            .filterNot(::estaCerradoProgramaMaestro)
                            .sortedWith(
                                compareBy<MasterProgramApiItem> {
                                    it.estStartDate.orEmpty()
                                }.thenBy {
                                    it.title.orEmpty()
                                }
                            )

                        if (programas.isEmpty()) {
                            onMensaje(
                                "No hay programas activos para ${productor.commercial_name}"
                            )
                        }
                    }

                    is ResultadoMasterProgramsApi.Error -> {
                        if (
                            productorSeleccionado?.idLocalAgroUnit ==
                            productor.idLocalAgroUnit
                        ) {
                            programas = emptyList()
                            onMensaje(resultado.mensaje)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (
                    productorSeleccionado?.idLocalAgroUnit ==
                    productor.idLocalAgroUnit
                ) {
                    programas = emptyList()
                    onMensaje("No se pudieron cargar los programas: ${e.message}")
                }
            } finally {
                if (
                    productorSeleccionado?.idLocalAgroUnit ==
                    productor.idLocalAgroUnit
                ) {
                    cargandoProgramas = false
                }
            }
        }
    }

    fun cargarSubprogramas(
        productor: LocalAgroUnitEntity,
        programa: MasterProgramApiItem
    ) {
        val programaExtId = programa.id.trim()
        if (programaExtId.isBlank()) {
            onMensaje("El programa seleccionado no tiene UUID remoto.")
            return
        }

        coroutineScope.launch {
            cargandoSubprogramas = true
            try {
                when (
                    val resultado = withContext(Dispatchers.IO) {
                        fieldOpsRepository.obtenerTodosLosProgramasCampo(
                            masterProgram = programaExtId
                        )
                    }
                ) {
                    is ResultadoFieldOpsApi.Exito -> {
                        val programasActivos = resultado.programas
                            .filterNot(::estaCerradoSubprograma)

                        val disponibles = withContext(Dispatchers.IO) {
                            programasActivos.mapNotNull { subprograma ->
                                resolverSubprogramaLocal(
                                    database = database,
                                    productor = productor,
                                    subprograma = subprograma,
                                    rolUsuario = rolUsuario,
                                    idUsuarioActual = idUsuarioActual
                                )
                            }
                        }
                            .distinctBy { it.programa.id.trim() }
                            .sortedBy {
                                textoSubprogramaAdmin(
                                    programa = it.programa,
                                    parcela = it.parcela
                                ).lowercase(Locale.getDefault())
                            }

                        if (
                            programaSeleccionado?.id?.trim() != programaExtId ||
                            productorSeleccionado?.idLocalAgroUnit !=
                            productor.idLocalAgroUnit
                        ) {
                            return@launch
                        }

                        subprogramas = disponibles

                        when {
                            disponibles.isEmpty() -> {
                                val omitidos = programasActivos.size
                                onMensaje(
                                    if (omitidos > 0) {
                                        "El programa tiene subprogramas, pero ninguno está completo " +
                                                "o disponible para este usuario. Sincroniza parcelas " +
                                                "y cultivos, y revisa fechas y asignaciones en Django."
                                    } else {
                                        "Este programa no tiene subprogramas activos."
                                    }
                                )
                            }

                            disponibles.size == 1 -> {
                                val unico = disponibles.first()
                                val verticesUnico = withContext(Dispatchers.IO) {
                                    database.LocalPlotVertexDao()
                                        .getVerticesByPlot(unico.parcela.idLocalPlot)
                                }

                                if (
                                    programaSeleccionado?.id?.trim() == programaExtId &&
                                    productorSeleccionado?.idLocalAgroUnit ==
                                    productor.idLocalAgroUnit
                                ) {
                                    subprogramaSeleccionado = unico
                                    vertices = verticesUnico
                                }
                            }
                        }
                    }

                    is ResultadoFieldOpsApi.Error -> {
                        if (programaSeleccionado?.id?.trim() == programaExtId) {
                            subprogramas = emptyList()
                            onMensaje(resultado.mensaje)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (programaSeleccionado?.id?.trim() == programaExtId) {
                    subprogramas = emptyList()
                    onMensaje("No se pudieron cargar los subprogramas: ${e.message}")
                }
            } finally {
                if (programaSeleccionado?.id?.trim() == programaExtId) {
                    cargandoSubprogramas = false
                }
            }
        }
    }

    fun seleccionarSubprograma(item: SubprogramaAdminItem) {
        subprogramaSeleccionado = item
        vertices = emptyList()

        coroutineScope.launch {
            try {
                val verticesCargados = withContext(Dispatchers.IO) {
                    database.LocalPlotVertexDao()
                        .getVerticesByPlot(item.parcela.idLocalPlot)
                }

                if (subprogramaSeleccionado?.programa?.id == item.programa.id) {
                    vertices = verticesCargados
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar el polígono de la parcela: ${e.message}")
            }
        }
    }

    fun reiniciarFormulario() {
        productorSeleccionado = null
        programaSeleccionado = null
        subprogramaSeleccionado = null
        programas = emptyList()
        subprogramas = emptyList()
        vertices = emptyList()
        cargandoProgramas = false
        cargandoSubprogramas = false
    }

    fun validarYGuardar() {
        val idCia = idLocalCia
        val productor = productorSeleccionado
        val programa = programaSeleccionado
        val detalle = subprogramaSeleccionado

        when {
            idCia == null || idCia <= 0L -> onMensaje("Selecciona una CIA primero")
            productor == null -> onMensaje("Selecciona un productor")
            programa == null -> onMensaje("Selecciona un programa")
            cargandoSubprogramas -> onMensaje("Espera a que terminen de cargar los subprogramas")
            detalle == null -> onMensaje("Selecciona un subprograma existente")
            vertices.size < 3 -> onMensaje(
                "La parcela del subprograma necesita al menos 3 vértices para mostrar el mapa"
            )
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        onMensaje("Creando sesión de monitoreo...")

                        val resultado = withContext(Dispatchers.IO) {
                            adminMonitoreoRepository.crearMonitoreo(
                                idLocalCia = idCia,
                                productor = productor,
                                rancho = detalle.rancho,
                                parcela = detalle.parcela,
                                cultivo = detalle.cultivo,
                                programaMaestro = programa,
                                subprograma = detalle.programa
                            )
                        }

                        when (resultado) {
                            is ResultadoCrearMonitoreoAdmin.Exito -> {
                                onMensaje(
                                    "Monitoreo listo. Se reutilizaron el programa y " +
                                            "subprograma existentes; solo se creó la sesión."
                                )
                                reiniciarFormulario()
                                onMonitoreoCreado()
                            }

                            is ResultadoCrearMonitoreoAdmin.Error -> {
                                onMensaje(resultado.mensaje)
                            }
                        }
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

    val detalle = subprogramaSeleccionado
    val avanceFormulario = listOf(
        productorSeleccionado != null,
        programaSeleccionado != null,
        detalle != null,
        detalle?.let {
            it.rancho.idLocalRanch > 0L &&
                    it.parcela.idLocalPlot > 0L &&
                    it.cultivo.idCrop > 0L
        } == true,
        detalle?.let { it.fechaInicioMillis <= it.fechaFinMillis } == true,
        vertices.size >= 3
    ).count { it } / 6f

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
                    cargandoProgramas = cargandoProgramas,
                    cargandoSubprogramas = cargandoSubprogramas,
                    productores = productores,
                    programas = programas,
                    subprogramas = subprogramas,
                    vertices = vertices,
                    productorSeleccionado = productorSeleccionado,
                    programaSeleccionado = programaSeleccionado,
                    subprogramaSeleccionado = subprogramaSeleccionado,
                    onProductorSeleccionado = { productor ->
                        productorSeleccionado = productor
                        limpiarDesdeProductor()
                        cargarProgramas(productor)
                    },
                    onProgramaSeleccionado = { programa ->
                        programaSeleccionado = programa
                        limpiarDesdePrograma()
                        productorSeleccionado?.let { productor ->
                            cargarSubprogramas(
                                productor = productor,
                                programa = programa
                            )
                        }
                    },
                    onSubprogramaSeleccionado = ::seleccionarSubprograma,
                    onGuardarClick = ::validarYGuardar
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private suspend fun resolverSubprogramaLocal(
    database: AppDatabase,
    productor: LocalAgroUnitEntity,
    subprograma: FieldTaskApiItem,
    rolUsuario: String,
    idUsuarioActual: Long
): SubprogramaAdminItem? {
    val subprogramaExtId = subprograma.id.trim().takeIf { it.isNotBlank() }
        ?: return null
    val parcelaExtId = subprograma.plot?.trim()?.takeIf { it.isNotBlank() }
        ?: return null

    val parcela = database.localPlotDao().getPlotByExtId(parcelaExtId)
        ?: return null
    val rancho = database.localRanchDao().getRanchById(parcela.idLocalRanch)
        ?: return null

    if (rancho.idLocalAgroUnit != productor.idLocalAgroUnit) {
        return null
    }

    if (
        esRolTecnicoAdminMonitoreo(rolUsuario) &&
        parcela.assignedUserId != idUsuarioActual
    ) {
        return null
    }

    val programaLocal = database.localprogramDao()
        .getProgramByExtId(subprogramaExtId)

    val cultivoApiId = (subprograma.crop ?: subprograma.cropVariety)
        ?.id
        ?.toString()

    val cultivo = cultivoApiId
        ?.let { database.localCropCatalogDao().getCropByExtId(it) }
        ?: programaLocal
            ?.let { database.localCropCatalogDao().getCropById(it.idCrop) }
        ?: return null

    val fechaInicioMillis = ApiDateParser.parsearMillis(subprograma.estStartDate)
        ?: return null
    val fechaFinMillis = ApiDateParser.parsearFinDeDiaMillis(subprograma.estFinishDate)
        ?: return null

    if (fechaInicioMillis > fechaFinMillis) {
        return null
    }

    return SubprogramaAdminItem(
        programa = subprograma,
        rancho = rancho,
        parcela = parcela,
        cultivo = cultivo,
        fechaInicioMillis = fechaInicioMillis,
        fechaFinMillis = fechaFinMillis
    )
}

private fun estaCerradoProgramaMaestro(programa: MasterProgramApiItem): Boolean {
    return estaCerrado(programa.status)
}

private fun estaCerradoSubprograma(programa: FieldTaskApiItem): Boolean {
    return estaCerrado(programa.status)
}

private fun estaCerrado(status: String?): Boolean {
    return status
        ?.trim()
        ?.lowercase(Locale.getDefault()) in setOf(
        "completed",
        "completado",
        "finalizado",
        "cancelled",
        "cancelado",
        "canceled"
    )
}
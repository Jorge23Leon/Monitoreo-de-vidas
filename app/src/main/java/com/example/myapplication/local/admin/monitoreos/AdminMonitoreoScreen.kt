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
import com.example.myapplication.local.api.fieldops.FieldOpsRepository
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
 * El flujo es remoto primero:
 * MasterProgram -> FieldTask (Programa) -> PhytoHeader (Sesión).
 * Solo después se insertan las copias locales con sus UUID extId.
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
    var cargandoProgramasMaestros by remember { mutableStateOf(false) }
    var cargandoCiclos by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    var productores by remember { mutableStateOf<List<LocalAgroUnitEntity>>(emptyList()) }
    var programasMaestros by remember { mutableStateOf<List<MasterProgramApiItem>>(emptyList()) }

    /*
     * Los ciclos ya no se escriben a mano. Se descargan de los Programas
     * existentes del Programa Maestro seleccionado, por lo que se manda a
     * Django exactamente el formato que ya reconoce (ej. Primavera-2026).
     */
    var ciclosDisponibles by remember { mutableStateOf<List<String>>(emptyList()) }

    var ranchos by remember { mutableStateOf<List<LocalRanchEntity>>(emptyList()) }
    var parcelas by remember { mutableStateOf<List<LocalPlotEntity>>(emptyList()) }
    var cultivos by remember { mutableStateOf<List<LocalCropCatalogEntity>>(emptyList()) }
    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }

    var productorSeleccionado by remember { mutableStateOf<LocalAgroUnitEntity?>(null) }
    var programaMaestroSeleccionado by remember { mutableStateOf<MasterProgramApiItem?>(null) }
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
                    val productoresCia = database.localCiaAgroUnitDao()
                        .getProductoresByCia(idCia)

                    /*
                     * Solo se muestran cultivos sincronizados desde la API:
                     * para crear un FieldTask se requiere crop_id remoto.
                     */
                    val cultivosDb = database.localCropCatalogDao()
                        .getAllCrops()
                        .filter { cultivo ->
                            cultivo.extId?.trim()?.toIntOrNull() != null
                        }

                    productoresCia to cultivosDb
                }

                productores = datos.first.sortedBy { it.commercial_name.lowercase(Locale.getDefault()) }
                cultivos = datos.second.sortedBy { it.name.lowercase(Locale.getDefault()) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onMensaje("Error al cargar datos: ${e.message}")
        } finally {
            cargandoInicial = false
        }
    }

    fun limpiarDependenciasDesdeProductor() {
        programaMaestroSeleccionado = null
        programasMaestros = emptyList()
        ciclosDisponibles = emptyList()
        ciclo = ""
        cargandoCiclos = false
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

    fun cargarProgramasMaestros(productor: LocalAgroUnitEntity) {
        val productorExtId = productor.ext_Id?.trim().orEmpty()

        if (productorExtId.isBlank()) {
            programasMaestros = emptyList()
            onMensaje("El productor no tiene UUID remoto. Sincroniza la información de la CIA.")
            return
        }

        coroutineScope.launch {
            cargandoProgramasMaestros = true
            try {
                when (
                    val resultado = withContext(Dispatchers.IO) {
                        fieldOpsRepository.obtenerTodosLosProgramasMaestros(
                            agroUnit = productorExtId
                        )
                    }
                ) {
                    is ResultadoMasterProgramsApi.Exito -> {
                        programasMaestros = resultado.programasMaestros
                            .filter { programa ->
                                val status = programa.status
                                    ?.trim()
                                    ?.lowercase(Locale.getDefault())

                                status !in setOf(
                                    "completed",
                                    "completado",
                                    "cancelled",
                                    "cancelado",
                                    "canceled"
                                )
                            }
                            .sortedWith(
                                compareBy<MasterProgramApiItem> {
                                    it.estStartDate.orEmpty()
                                }.thenBy {
                                    it.title.orEmpty()
                                }
                            )

                        if (programasMaestros.isEmpty()) {
                            onMensaje("No hay programas maestros activos para ${productor.commercial_name}")
                        }
                    }

                    is ResultadoMasterProgramsApi.Error -> {
                        programasMaestros = emptyList()
                        onMensaje(resultado.mensaje)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                programasMaestros = emptyList()
                onMensaje("No se pudieron cargar programas maestros: ${e.message}")
            } finally {
                cargandoProgramasMaestros = false
            }
        }
    }



    /**
     * Descarga los ciclos de los Programas hijos que ya existen en el
     * Programa Maestro seleccionado. El usuario no captura texto libre:
     * selecciona un ciclo que el backend ya reconoce como válido.
     */
    fun cargarCiclosDisponibles(programaMaestro: MasterProgramApiItem) {
        val masterProgramExtId = programaMaestro.id.trim()

        ciclo = ""
        ciclosDisponibles = emptyList()

        if (masterProgramExtId.isBlank()) {
            onMensaje("El programa maestro seleccionado no tiene UUID remoto.")
            return
        }

        coroutineScope.launch {
            cargandoCiclos = true
            try {
                when (
                    val resultado = withContext(Dispatchers.IO) {
                        /*
                         * No se filtra por parcela: el ciclo pertenece al Programa
                         * Maestro y puede haberse usado previamente en otra parcela.
                         */
                        fieldOpsRepository.obtenerTodosLosProgramasCampo(
                            masterProgram = masterProgramExtId
                        )
                    }
                ) {
                    is ResultadoFieldOpsApi.Exito -> {
                        /*
                         * Se muestran solo formatos que el backend acepta:
                         * Primavera-2026 o Primavera-Verano-2026.
                         * Así un ciclo histórico mal escrito no vuelve a provocar 400.
                         */
                        val ciclos = resultado.programas
                            .filterNot { programa ->
                                programa.status
                                    ?.trim()
                                    ?.lowercase(Locale.getDefault()) in setOf(
                                    "cancelled",
                                    "cancelado",
                                    "canceled"
                                )
                            }
                            .mapNotNull { programa ->
                                programa.cycle
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            }
                            .filter { cicloServidor ->
                                esCicloValidoServidor(cicloServidor)
                            }
                            .distinctBy { it.lowercase(Locale.getDefault()) }
                            .sortedBy { it.lowercase(Locale.getDefault()) }

                        /*
                         * Ignora una respuesta tardía si el usuario ya seleccionó
                         * otro Programa Maestro antes de que terminara el GET.
                         */
                        if (programaMaestroSeleccionado?.id?.trim() != masterProgramExtId) {
                            return@launch
                        }

                        ciclosDisponibles = ciclos

                        when {
                            ciclos.isEmpty() -> {
                                onMensaje(
                                    "No hay ciclos válidos disponibles para este programa maestro. " +
                                            "Primero debe existir al menos un programa con ciclo válido en Django."
                                )
                            }

                            ciclos.size == 1 -> {
                                // Si solo hay uno, se asigna automáticamente.
                                ciclo = ciclos.first()
                            }
                        }
                    }

                    is ResultadoFieldOpsApi.Error -> {
                        if (programaMaestroSeleccionado?.id?.trim() == masterProgramExtId) {
                            ciclosDisponibles = emptyList()
                            onMensaje("No se pudieron cargar los ciclos. ${resultado.mensaje}")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (programaMaestroSeleccionado?.id?.trim() == masterProgramExtId) {
                    ciclosDisponibles = emptyList()
                    onMensaje("No se pudieron cargar los ciclos: ${e.message}")
                }
            } finally {
                if (programaMaestroSeleccionado?.id?.trim() == masterProgramExtId) {
                    cargandoCiclos = false
                }
            }
        }
    }

    fun cargarRanchos(productor: LocalAgroUnitEntity) {
        coroutineScope.launch {
            try {
                ranchos = withContext(Dispatchers.IO) {
                    database.localRanchDao()
                        .getRanchosByProductor(productor.idLocalAgroUnit)
                        .sortedBy { it.name.lowercase(Locale.getDefault()) }
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
                    val resultado = if (esRolTecnicoAdminMonitoreo(rolUsuario)) {
                        database.localPlotDao().getParcelasByRanchoAndUser(
                            idRanch = rancho.idLocalRanch,
                            idUser = idUsuarioActual
                        )
                    } else {
                        database.localPlotDao().getParcelasByRancho(rancho.idLocalRanch)
                    }

                    /*
                     * El endpoint necesita plot UUID. Se quitan parcelas creadas
                     * solo localmente para no terminar en un POST 400.
                     */
                    resultado
                        .filter { it.extId?.trim()?.isNotBlank() == true }
                        .sortedBy { it.nombreMostrarAdmin().lowercase(Locale.getDefault()) }
                }

                if (parcelas.isEmpty()) {
                    onMensaje("No hay parcelas sincronizadas disponibles en este rancho")
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
                    database.LocalPlotVertexDao()
                        .getVerticesByPlot(parcela.idLocalPlot)
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
        programaMaestroSeleccionado = null
        ranchoSeleccionado = null
        parcelaSeleccionada = null
        cultivoSeleccionado = null
        programasMaestros = emptyList()
        ciclosDisponibles = emptyList()
        cargandoCiclos = false
        ranchos = emptyList()
        parcelas = emptyList()
        vertices = emptyList()
    }

    fun validarYGuardar() {
        val idCia = idLocalCia
        val productor = productorSeleccionado
        val programaMaestro = programaMaestroSeleccionado
        val rancho = ranchoSeleccionado
        val parcela = parcelaSeleccionada
        val cultivo = cultivoSeleccionado
        val inicioMillis = fechaInicioMillis?.let { normalizarFechaAdmin(it, finDelDia = false) }
        val finMillis = fechaFinMillis?.let { normalizarFechaAdmin(it, finDelDia = true) }
        /*
         * Conserva exactamente el texto de la API. No se normaliza ni se
         * modifica, porque Django valida el formato con guiones.
         */
        val cicloLimpio = ciclosDisponibles
            .firstOrNull { disponible ->
                disponible.equals(ciclo.trim(), ignoreCase = true)
            }
            ?.trim()

        when {
            idCia == null || idCia <= 0L -> onMensaje("Selecciona una CIA primero")
            productor == null -> onMensaje("Selecciona un productor")
            programaMaestro == null -> onMensaje("Selecciona un programa maestro")
            rancho == null -> onMensaje("Selecciona un rancho")
            parcela == null -> onMensaje("Selecciona una parcela")
            cultivo == null -> onMensaje("Selecciona un cultivo")
            cargandoCiclos -> onMensaje("Espera a que terminen de cargar los ciclos")
            ciclosDisponibles.isEmpty() -> onMensaje(
                "Selecciona un programa maestro que tenga ciclos válidos disponibles."
            )
            cicloLimpio.isNullOrBlank() -> onMensaje(
                "Selecciona un ciclo disponible de la lista."
            )
            inicioMillis == null -> onMensaje("Selecciona la fecha de inicio")
            finMillis == null -> onMensaje("Selecciona la fecha fin")
            inicioMillis > finMillis -> onMensaje("La fecha de inicio no puede ser mayor que la fecha fin")
            vertices.size < 3 -> onMensaje("La parcela necesita al menos 3 vértices para mostrar el mapa")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        onMensaje("Creando programa remoto...")

                        val resultado = withContext(Dispatchers.IO) {
                            adminMonitoreoRepository.crearMonitoreo(
                                idLocalCia = idCia,
                                productor = productor,
                                rancho = rancho,
                                parcela = parcela,
                                cultivo = cultivo,
                                programaMaestro = programaMaestro,
                                ciclo = cicloLimpio!!,
                                fechaInicioMillis = inicioMillis,
                                fechaFinMillis = finMillis
                            )
                        }

                        when (resultado) {
                            is ResultadoCrearMonitoreoAdmin.Exito -> {
                                onMensaje(
                                    "Monitoreo creado en servidor. " +
                                            "Programa y sesión listos para capturar checkpoints."
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

    val avanceFormulario = listOf(
        productorSeleccionado != null,
        programaMaestroSeleccionado != null,
        ranchoSeleccionado != null,
        parcelaSeleccionada != null,
        cultivoSeleccionado != null,
        ciclo.isNotBlank(),
        fechaInicioMillis != null && fechaFinMillis != null,
        vertices.size >= 3
    ).count { it } / 8f

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
                    cargandoProgramasMaestros = cargandoProgramasMaestros,
                    cargandoCiclos = cargandoCiclos,
                    productores = productores,
                    programasMaestros = programasMaestros,
                    ciclosDisponibles = ciclosDisponibles,
                    ranchos = ranchos,
                    parcelas = parcelas,
                    cultivos = cultivos,
                    vertices = vertices,
                    productorSeleccionado = productorSeleccionado,
                    programaMaestroSeleccionado = programaMaestroSeleccionado,
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
                        cargarProgramasMaestros(productor)
                    },
                    onProgramaMaestroSeleccionado = { programa ->
                        programaMaestroSeleccionado = programa
                        cargarCiclosDisponibles(programa)
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
                    onCicloSeleccionado = { ciclo = it },
                    onFechaInicioChange = { fechaInicioMillis = it },
                    onFechaFinChange = { fechaFinMillis = it },
                    onGuardarClick = { validarYGuardar() }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
/**
 * Valida los ciclos aceptados por Django.
 *
 * Ejemplos válidos:
 * - Primavera-2026
 * - Primavera-Verano-2026
 *
 * Se declara fuera de AdminMonitoreoScreen porque las funciones locales de
 * Kotlin deben estar declaradas ANTES de usarse dentro de una lambda.
 */
private fun esCicloValidoServidor(ciclo: String): Boolean {
    val patron = Regex(
        """^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+(?:-[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+){0,2}-20(?:0\d|[1-4]\d|50)$"""
    )

    return patron.matches(ciclo.trim())
}

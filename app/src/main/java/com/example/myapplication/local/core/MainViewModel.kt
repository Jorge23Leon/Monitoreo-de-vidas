package com.example.myapplication.local.core

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import com.example.myapplication.local.entities.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MainUiState(
    val pantallaActual: PantallaActual = PantallaActual.LOGIN,
    val cargando: Boolean = false,

    val idUsuarioActual: Long = 0L,
    val nombreUsuarioActual: String = "",
    val rolUsuarioActual: String = "",

    val busquedaFueConSaltoFiltros: Boolean = false,

    val ciasUsuario: List<LocalCiaEntity> = emptyList(),
    val ciaSeleccionada: LocalCiaEntity? = null,
    val seleccionarPreferente: Boolean = false,

    val productores: List<LocalAgroUnitEntity> = emptyList(),
    val ranchos: List<LocalRanchEntity> = emptyList(),
    val parcelas: List<LocalPlotEntity> = emptyList(),
    val ciclos: List<LocalProgramEntity> = emptyList(),

    val productorSeleccionado: LocalAgroUnitEntity? = null,
    val ranchoSeleccionado: LocalRanchEntity? = null,
    val parcelaSeleccionada: LocalPlotEntity? = null,
    val cicloSeleccionado: LocalProgramEntity? = null,

    val fechaInicioTexto: String = "",
    val fechaFinTexto: String = "",
    val finalizadosChecked: Boolean = false,
    val vigentesChecked: Boolean = true,
    val canceladosChecked: Boolean = false,

    val monitoreosEncontrados: List<LocalPhytomonitoringHeaderEntity> = emptyList(),
    val productoresResultado: List<LocalAgroUnitEntity> = emptyList(),
    val ranchosResultado: List<LocalRanchEntity> = emptyList(),
    val parcelasResultado: List<LocalPlotEntity> = emptyList(),
    val programasResultado: List<LocalProgramEntity> = emptyList(),

    val monitoreoSeleccionadoParaMapa: LocalPhytomonitoringHeaderEntity? = null,
    val monitoreoSeleccionadoParaReporte: LocalPhytomonitoringHeaderEntity? = null,
    val puntoSeleccionadoParaRegistro: LocalPhytomonitoringTargetPointEntity? = null,

    val mensaje: String? = null
)

private data class MainResultadoMonitoreoTemp(
    val headers: List<LocalPhytomonitoringHeaderEntity>,
    val productores: List<LocalAgroUnitEntity>,
    val ranchos: List<LocalRanchEntity>,
    val parcelas: List<LocalPlotEntity>,
    val programas: List<LocalProgramEntity>
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getDatabase(application.applicationContext)

    var uiState by mutableStateOf(MainUiState())
        private set

    init {
        insertarUsuarioAdminSeguro()
    }

    private fun actualizarEstado(transform: (MainUiState) -> MainUiState) {
        uiState = transform(uiState)
    }

    private fun mostrarMensaje(mensaje: String) {
        actualizarEstado { it.copy(mensaje = mensaje) }
    }

    fun limpiarMensaje() {
        actualizarEstado { it.copy(mensaje = null) }
    }

    fun irA(pantalla: PantallaActual) {
        actualizarEstado { it.copy(pantallaActual = pantalla) }
    }

    fun limpiarFiltros() {
        actualizarEstado {
            it.copy(
                productores = emptyList(),
                ranchos = emptyList(),
                parcelas = emptyList(),
                ciclos = emptyList(),

                productorSeleccionado = null,
                ranchoSeleccionado = null,
                parcelaSeleccionada = null,
                cicloSeleccionado = null,

                fechaInicioTexto = "",
                fechaFinTexto = "",

                finalizadosChecked = false,
                vigentesChecked = true,
                canceladosChecked = false,

                busquedaFueConSaltoFiltros = false,

                monitoreoSeleccionadoParaMapa = null,
                monitoreoSeleccionadoParaReporte = null,
                puntoSeleccionadoParaRegistro = null,

                monitoreosEncontrados = emptyList(),
                productoresResultado = emptyList(),
                ranchosResultado = emptyList(),
                parcelasResultado = emptyList(),
                programasResultado = emptyList()
            )
        }
    }

    fun onLoginClick(
        usernameInput: String,
        passwordInput: String,
        roleInput: String
    ) {
        val username = usernameInput.trim()
        val password = passwordInput.trim()
        val role = roleInput.trim()

        if (username.isBlank() || password.isBlank()) {
            mostrarMensaje("Ingresa usuario y contraseña")
            return
        }

        if (uiState.cargando) return

        actualizarEstado { it.copy(cargando = true) }

        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val user = database.userDao().login(
                        username = username,
                        password = password,
                        role = role
                    )

                    if (user == null) {
                        null
                    } else {
                        val listaCias = database.localCiaDao()
                            .obtenerCiasPorUsuario(user.idUser)

                        user to listaCias
                    }
                }

                if (resultado == null) {
                    mostrarMensaje("Usuario, contraseña o rol incorrecto")
                } else {
                    val user = resultado.first
                    val listaCias = resultado.second
                    val idCiaPreferente = obtenerCiaPreferente(user.idUser)

                    val ciaPreferente = if (idCiaPreferente != 0L) {
                        listaCias.firstOrNull { cia ->
                            cia.idLocalCia == idCiaPreferente
                        } ?: listaCias.firstOrNull()
                    } else {
                        listaCias.firstOrNull()
                    }

                    actualizarEstado {
                        it.copy(
                            idUsuarioActual = user.idUser,
                            nombreUsuarioActual = user.firstName,
                            rolUsuarioActual = user.role,
                            ciasUsuario = listaCias,
                            ciaSeleccionada = ciaPreferente,
                            pantallaActual = PantallaActual.SELECCION_CIA
                        )
                    }

                    mostrarMensaje("Bienvenido ${user.firstName}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al iniciar sesión: ${e.message}")
            } finally {
                actualizarEstado { it.copy(cargando = false) }
            }
        }
    }

    fun registrarUsuario(
        firstNameInput: String,
        lastnameInput: String,
        usernameInput: String,
        emailInput: String,
        passwordInput: String,
        confirmPasswordInput: String,
        roleInput: String
    ) {
        val firstName = firstNameInput.trim()
        val lastname = lastnameInput.trim()
        val username = usernameInput.trim()
        val email = emailInput.trim()
        val password = passwordInput.trim()
        val confirmPassword = confirmPasswordInput.trim()
        val role = roleInput.trim()

        when {
            firstName.isBlank() ||
                    username.isBlank() ||
                    email.isBlank() ||
                    password.isBlank() ||
                    confirmPassword.isBlank() -> {
                mostrarMensaje("Completa todos los campos obligatorios")
            }

            password != confirmPassword -> {
                mostrarMensaje("Las contraseñas no coinciden")
            }

            uiState.cargando -> Unit

            else -> {
                actualizarEstado { it.copy(cargando = true) }

                viewModelScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            database.userDao().insertUser(
                                UserEntity(
                                    firstName = firstName,
                                    lastName = lastname.ifBlank { null },
                                    username = username,
                                    email = email,
                                    password = password,
                                    role = role
                                )
                            )
                        }

                        mostrarMensaje("Usuario registrado correctamente")
                        irA(PantallaActual.LOGIN)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mostrarMensaje("No se pudo registrar. Revisa si el usuario o correo ya existen")
                    } finally {
                        actualizarEstado { it.copy(cargando = false) }
                    }
                }
            }
        }
    }

    fun onPreferenteChange(seleccionado: Boolean) {
        actualizarEstado { it.copy(seleccionarPreferente = seleccionado) }
    }

    fun onCiaChange(cia: LocalCiaEntity) {
        actualizarEstado { it.copy(ciaSeleccionada = cia) }
    }

    fun seleccionarCiaActual() {
        val estado = uiState
        val cia = estado.ciaSeleccionada

        if (cia == null) {
            mostrarMensaje("Este usuario no tiene CIAS asignadas")
            return
        }

        if (estado.seleccionarPreferente) {
            guardarCiaPreferente(
                idUser = estado.idUsuarioActual,
                idLocalCia = cia.idLocalCia
            )
        }

        limpiarFiltros()
        cargarProductores(cia.idLocalCia)

        mostrarMensaje("CIA seleccionada: ${cia.nombre}")
        irA(PantallaActual.FILTROS_MONITOREO)
    }

    fun cargarProductores(idLocalCia: Long) {
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    database.localCiaAgroUnitDao()
                        .getProductoresByCia(idLocalCia)
                }

                actualizarEstado { it.copy(productores = lista) }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar productores: ${e.message}")
            }
        }
    }

    fun onProductorChange(productor: LocalAgroUnitEntity) {
        actualizarEstado {
            it.copy(
                productorSeleccionado = productor,
                ranchoSeleccionado = null,
                parcelaSeleccionada = null,
                cicloSeleccionado = null,
                ranchos = emptyList(),
                parcelas = emptyList(),
                ciclos = emptyList()
            )
        }

        cargarRanchos(productor.idLocalAgroUnit)
    }

    fun cargarRanchos(idProductor: Long) {
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    database.localRanchDao()
                        .getRanchosByProductor(idProductor)
                }

                actualizarEstado { it.copy(ranchos = lista) }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar ranchos: ${e.message}")
            }
        }
    }

    fun onRanchoChange(rancho: LocalRanchEntity) {
        actualizarEstado {
            it.copy(
                ranchoSeleccionado = rancho,
                parcelaSeleccionada = null,
                cicloSeleccionado = null,
                parcelas = emptyList(),
                ciclos = emptyList()
            )
        }

        cargarParcelas(rancho.idLocalRanch)
    }

    fun cargarParcelas(idRanch: Long) {
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    database.localPlotDao()
                        .getParcelasByRancho(idRanch)
                }

                actualizarEstado { it.copy(parcelas = lista) }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar parcelas: ${e.message}")
            }
        }
    }

    fun onParcelaChange(parcela: LocalPlotEntity) {
        val productor = uiState.productorSeleccionado

        actualizarEstado {
            it.copy(
                parcelaSeleccionada = parcela,
                cicloSeleccionado = null,
                ciclos = emptyList()
            )
        }

        if (productor != null) {
            cargarCiclos(
                idProductor = productor.idLocalAgroUnit,
                idPlot = parcela.idLocalPlot
            )
        }
    }

    fun cargarCiclos(idProductor: Long, idPlot: Long) {
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    database.localprogramDao()
                        .getCiclosByProductorAndParcela(
                            idProductor = idProductor,
                            idPlot = idPlot
                        )
                }

                actualizarEstado { it.copy(ciclos = lista) }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar ciclos: ${e.message}")
            }
        }
    }

    private fun filtrosCompletos(): Boolean {
        val estado = uiState

        if (estado.productorSeleccionado == null) {
            mostrarMensaje("Selecciona un productor")
            return false
        }

        if (estado.ranchoSeleccionado == null) {
            mostrarMensaje("Selecciona un rancho")
            return false
        }

        if (estado.parcelaSeleccionada == null) {
            mostrarMensaje("Selecciona una parcela")
            return false
        }

        return true
    }

    fun buscarMonitoreos(saltarFiltros: Boolean) {
        val estado = uiState
        val cia = estado.ciaSeleccionada

        if (cia == null) {
            mostrarMensaje("Selecciona una CIA primero")
            return
        }

        actualizarEstado {
            it.copy(busquedaFueConSaltoFiltros = saltarFiltros)
        }

        if (!saltarFiltros && !filtrosCompletos()) {
            return
        }

        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val estadoActual = uiState

                    val programasCia = database.localprogramDao()
                        .getProgramasByCia(cia.idLocalCia)

                    var programasFiltrados = programasCia

                    if (!saltarFiltros) {
                        estadoActual.productorSeleccionado?.let { productor ->
                            programasFiltrados = programasFiltrados.filter { programa ->
                                programa.idLocalAgroUnit == productor.idLocalAgroUnit
                            }
                        }

                        estadoActual.ranchoSeleccionado?.let { rancho ->
                            val parcelasDelRancho = database.localPlotDao()
                                .getParcelasByRancho(rancho.idLocalRanch)

                            val idsParcelasRancho = parcelasDelRancho.map { parcela ->
                                parcela.idLocalPlot
                            }

                            programasFiltrados = programasFiltrados.filter { programa ->
                                programa.idLocalPlot in idsParcelasRancho
                            }
                        }

                        estadoActual.parcelaSeleccionada?.let { parcela ->
                            programasFiltrados = programasFiltrados.filter { programa ->
                                programa.idLocalPlot == parcela.idLocalPlot
                            }
                        }
                    }

                    val idsProgramas = programasFiltrados.map { programa ->
                        programa.idProgram
                    }

                    val headers = if (idsProgramas.isEmpty()) {
                        emptyList()
                    } else {
                        database.localphytomonitoringheaderDao()
                            .filtrarHeadersMonitoreo(
                                programIds = idsProgramas,
                                idProgram = null,
                                idPlot = if (saltarFiltros) {
                                    null
                                } else {
                                    estadoActual.parcelaSeleccionada?.idLocalPlot
                                },
                                startDate = parseFechaInicio(estadoActual.fechaInicioTexto),
                                endDate = parseFechaFin(estadoActual.fechaFinTexto),
                                statuses = obtenerEstadosSeleccionados(saltarFiltros)
                            )
                    }

                    val idsProgramasResultado = headers.map { header ->
                        header.idProgram
                    }.distinct()

                    val idsParcelasResultado = headers.map { header ->
                        header.idLocalPlot
                    }.distinct()

                    val programasRel = if (idsProgramasResultado.isEmpty()) {
                        emptyList()
                    } else {
                        database.localprogramDao()
                            .getProgramasByIds(idsProgramasResultado)
                    }

                    val parcelasRel = if (idsParcelasResultado.isEmpty()) {
                        emptyList()
                    } else {
                        database.localPlotDao()
                            .getParcelasByIds(idsParcelasResultado)
                    }

                    val idsRanchosResultado = parcelasRel.map { parcela ->
                        parcela.idLocalRanch
                    }.distinct()

                    val ranchosRel = if (idsRanchosResultado.isEmpty()) {
                        emptyList()
                    } else {
                        database.localRanchDao()
                            .getRanchosByIds(idsRanchosResultado)
                    }

                    val idsProductoresResultado = programasRel.map { programa ->
                        programa.idLocalAgroUnit
                    }.distinct()

                    val productoresRel = if (idsProductoresResultado.isEmpty()) {
                        emptyList()
                    } else {
                        database.localAgroUnitDao()
                            .getProductoresByIds(idsProductoresResultado)
                    }

                    MainResultadoMonitoreoTemp(
                        headers = headers,
                        productores = productoresRel,
                        ranchos = ranchosRel,
                        parcelas = parcelasRel,
                        programas = programasRel
                    )
                }

                actualizarEstado {
                    it.copy(
                        monitoreosEncontrados = resultado.headers,
                        productoresResultado = resultado.productores,
                        ranchosResultado = resultado.ranchos,
                        parcelasResultado = resultado.parcelas,
                        programasResultado = resultado.programas,
                        pantallaActual = PantallaActual.LISTA_MONITOREOS
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al buscar monitoreos: ${e.message}")
            }
        }
    }

    fun abrirReporte(header: LocalPhytomonitoringHeaderEntity) {
        actualizarEstado {
            it.copy(
                monitoreoSeleccionadoParaReporte = header,
                monitoreoSeleccionadoParaMapa = null,
                puntoSeleccionadoParaRegistro = null,
                pantallaActual = PantallaActual.REPORTE_MONITOREO
            )
        }
    }

    fun abrirMapa(header: LocalPhytomonitoringHeaderEntity) {
        actualizarEstado {
            it.copy(
                monitoreoSeleccionadoParaMapa = header,
                monitoreoSeleccionadoParaReporte = null,
                puntoSeleccionadoParaRegistro = null,
                pantallaActual = PantallaActual.MAPA_MONITOREO
            )
        }
    }

    fun abrirPuntoParaRegistro(idTargetPoint: Long) {
        viewModelScope.launch {
            try {
                val punto = withContext(Dispatchers.IO) {
                    database.LocalPhytomonitoringTargetPointDao()
                        .getTargetPointById(idTargetPoint)
                }

                if (punto == null) {
                    mostrarMensaje("No se encontró el punto seleccionado")
                } else {
                    actualizarEstado {
                        it.copy(
                            puntoSeleccionadoParaRegistro = punto,
                            pantallaActual = PantallaActual.REGISTRO_PUNTO_MONITOREO
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al abrir punto: ${e.message}")
            }
        }
    }

    fun cancelarRegistroPunto() {
        actualizarEstado {
            it.copy(
                puntoSeleccionadoParaRegistro = null,
                pantallaActual = PantallaActual.MAPA_MONITOREO
            )
        }
    }

    fun onPuntoGuardado() {
        actualizarEstado {
            it.copy(
                puntoSeleccionadoParaRegistro = null,
                pantallaActual = PantallaActual.MAPA_MONITOREO
            )
        }
    }

    fun onMonitoreoActualizado(nuevoEstado: String) {
        mostrarMensaje(
            if (nuevoEstado == "Completado") {
                "Monitoreo terminado correctamente"
            } else {
                "Monitoreo guardado en proceso"
            }
        )

        actualizarEstado {
            it.copy(
                monitoreoSeleccionadoParaMapa = null,
                puntoSeleccionadoParaRegistro = null
            )
        }

        buscarMonitoreos(
            saltarFiltros = uiState.busquedaFueConSaltoFiltros
        )
    }

    fun abrirPanelAdministrador() {
        if (esRolAdministradorVm(uiState.rolUsuarioActual)) {
            irA(PantallaActual.ADMIN_HOME)
        } else {
            mostrarMensaje("Acceso permitido solo para administrador")
        }
    }

    fun abrirMonitoreosDesdeEncabezado() {
        irA(
            if (uiState.monitoreosEncontrados.isNotEmpty()) {
                PantallaActual.LISTA_MONITOREOS
            } else {
                PantallaActual.FILTROS_MONITOREO
            }
        )
    }

    fun onMonitoreoCreadoDesdeAdmin() {
        uiState.ciaSeleccionada?.let { cia ->
            cargarProductores(cia.idLocalCia)
        }

        irA(PantallaActual.ADMIN_HOME)
    }

    fun onPerfilActualizado(usuario: UserEntity) {
        actualizarEstado {
            it.copy(
                idUsuarioActual = usuario.idUser,
                nombreUsuarioActual = usuario.firstName,
                rolUsuarioActual = usuario.role
            )
        }

        mostrarMensaje("Perfil actualizado")
    }

    fun cerrarSesion() {
        uiState = MainUiState(
            pantallaActual = PantallaActual.LOGIN,
            mensaje = "Sesión cerrada"
        )
    }

    fun manejarBack() {
        when (uiState.pantallaActual) {
            PantallaActual.REGISTRO,
            PantallaActual.INFORMACION,
            PantallaActual.CONTACTO -> {
                irA(PantallaActual.LOGIN)
            }

            PantallaActual.SELECCION_CIA -> {
                cerrarSesion()
            }

            PantallaActual.FILTROS_MONITOREO -> {
                irA(PantallaActual.SELECCION_CIA)
            }

            PantallaActual.LISTA_MONITOREOS -> {
                irA(PantallaActual.FILTROS_MONITOREO)
            }

            PantallaActual.MAPA_MONITOREO -> {
                irA(PantallaActual.LISTA_MONITOREOS)
            }

            PantallaActual.REGISTRO_PUNTO_MONITOREO -> {
                irA(PantallaActual.MAPA_MONITOREO)
            }

            PantallaActual.REPORTE_MONITOREO -> {
                irA(PantallaActual.LISTA_MONITOREOS)
            }

            PantallaActual.PERFIL_USUARIO -> {
                irA(
                    if (uiState.ciaSeleccionada != null) {
                        PantallaActual.FILTROS_MONITOREO
                    } else {
                        PantallaActual.SELECCION_CIA
                    }
                )
            }

            PantallaActual.ADMIN_HOME -> {
                irA(
                    if (uiState.ciaSeleccionada != null) {
                        PantallaActual.FILTROS_MONITOREO
                    } else {
                        PantallaActual.SELECCION_CIA
                    }
                )
            }

            PantallaActual.ADMIN_MONITOREOS,
            PantallaActual.ADMIN_CATALOGOS,
            PantallaActual.ADMIN_GESTION_AGRICOLA -> {
                irA(PantallaActual.ADMIN_HOME)
            }

            else -> Unit
        }
    }

    private fun insertarUsuarioAdminSeguro() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val totalUsuarios = database.userDao().countUsers()

                if (totalUsuarios == 0) {
                    database.userDao().insertUser(
                        UserEntity(
                            firstName = "Jorge",
                            lastName = "Sandoval",
                            username = "jorge",
                            email = "jorge@test.com",
                            password = "1234",
                            role = "Admin"
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun guardarCiaPreferente(idUser: Long, idLocalCia: Long) {
        val prefs = getApplication<Application>()
            .getSharedPreferences("preferencias_app", android.content.Context.MODE_PRIVATE)

        prefs.edit()
            .putLong("cia_preferente_usuario_$idUser", idLocalCia)
            .apply()
    }

    private fun obtenerCiaPreferente(idUser: Long): Long {
        val prefs = getApplication<Application>()
            .getSharedPreferences("preferencias_app", android.content.Context.MODE_PRIVATE)

        return prefs.getLong("cia_preferente_usuario_$idUser", 0L)
    }

    private fun obtenerEstadosSeleccionados(saltarFiltros: Boolean): List<String> {
        /*
         * Por ahora se conserva la lógica anterior para no cambiar el comportamiento:
         * se aceptan los estados en español e inglés.
         * Después podemos hacer que respete finalizadosChecked/vigentesChecked/canceladosChecked.
         */
        return listOf(
            "Pendiente",
            "En proceso",
            "Completado",
            "Cancelado",

            "pending",
            "in_progress",
            "completed",
            "cancelled",

            "pendiente",
            "en proceso",
            "completado",
            "cancelado",

            "vigente",
            "finalizado"
        )
    }

    private fun parseFechaInicio(fechaTexto: String): Long? {
        if (fechaTexto.isBlank()) return null

        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(fechaTexto)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun parseFechaFin(fechaTexto: String): Long? {
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
}

fun esRolAdministradorVm(rol: String): Boolean {
    return rol.trim().equals("admin", ignoreCase = true) ||
            rol.trim().equals("administrador", ignoreCase = true)
}

fun esEstadoFinalizadoVm(status: String): Boolean {
    return when (status.lowercase().trim()) {
        "completed",
        "completado",
        "finalizado" -> true
        else -> false
    }
}

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
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRoleEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import com.example.myapplication.local.entities.UserEntity
import com.example.myapplication.local.models.UsuarioSesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getDatabase(application.applicationContext)

    var uiState by mutableStateOf(MainUiState())
        private set

    init {
        insertarDatosInicialesSeguros()
    }

    private fun actualizarEstado(transform: (MainUiState) -> MainUiState) {
        uiState = transform(uiState)
    }

    private fun mostrarMensaje(mensaje: String) {
        actualizarEstado { it.copy(mensaje = mensaje) }
    }



    private suspend fun cargarCultivosRelacionados(
        headers: List<LocalPhytomonitoringHeaderEntity>,
        programas: List<LocalProgramEntity>
    ): List<LocalCropCatalogEntity> {
        val idsCultivos = (headers.map { it.idCrop } + programas.map { it.idCrop })
            .distinct()
            .toSet()

        if (idsCultivos.isEmpty()) return emptyList()

        return database.localCropCatalogDao()
            .getAllCrops()
            .filter { cultivo -> cultivo.idCrop in idsCultivos }
            .sortedBy { cultivo -> cultivo.name }
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

                finalizadosChecked = true,
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
                programasResultado = emptyList(),
                cultivosResultado = emptyList()
            )
        }
    }

    private fun normalizarRolParaDb(rol: String): String {
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

    private fun nombreRolRealParaDb(rol: String): String {
        return when (normalizarRolParaDb(rol)) {
            "admin" -> "SUPER ADMIN"
            "gerente" -> "GERENTE"
            "supervisor" -> "ING.Y SUPERVISION"
            "tecnico" -> "TECNICO"
            "invitado" -> "INVITADO"
            else -> rol.trim()
        }
    }

    private fun obtenerPantallaInicialPorRol(usuarioSesion: UsuarioSesion): PantallaActual {
        return when (normalizarRolParaDb(usuarioSesion.roleName)) {
            // Técnico e invitado no pasan por selección de CIA ni filtros.
            // Entran directo a sus monitoreos asignados/capturados.
            "tecnico",
            "invitado" -> PantallaActual.LISTA_MONITOREOS

            "admin",
            "gerente",
            "supervisor" -> PantallaActual.SELECCION_CIA

            else -> PantallaActual.LOGIN
        }
    }

    fun onLoginClick(
        usernameInput: String,
        passwordInput: String
    ) {
        val username = usernameInput.trim()
        val password = passwordInput.trim()

        if (username.isBlank() || password.isBlank()) {
            mostrarMensaje("Ingresa usuario y contraseña")
            return
        }

        if (uiState.cargando) return

        actualizarEstado { it.copy(cargando = true) }

        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val sesion = database.userDao().loginConRol(
                        username = username,
                        password = password
                    )

                    if (sesion == null) {
                        null
                    } else {
                        val parentCias = if (sesion.esAdmin) {
                            database.localParentCiaDao().getAllParentCiasActivas()
                        } else {
                            database.userLocalParentCiaDao().getParentCiasByUser(sesion.idUser)
                        }

                        val ciasHijasUsuario = if (sesion.esSupervisor || sesion.esTecnico) {
                            database.userLocalParentCiaDao().getCiasHijasByUser(sesion.idUser)
                        } else {
                            emptyList()
                        }

                        Triple(sesion, parentCias, ciasHijasUsuario)
                    }
                }

                if (resultado == null) {
                    mostrarMensaje("Usuario o contraseña incorrectos")
                } else {
                    val sesion = resultado.first
                    val parentCias = resultado.second
                    val ciasHijasUsuario = resultado.third

                    actualizarEstado {
                        it.copy(
                            usuarioSesion = sesion,
                            idUsuarioActual = sesion.idUser,
                            nombreUsuarioActual = sesion.firstName,
                            rolUsuarioActual = sesion.roleName,
                            nivelRolUsuarioActual = sesion.level,

                            parentCiasUsuario = parentCias,
                            parentCiaSeleccionada = null,
                            ciasUsuario = if (sesion.esSupervisor || sesion.esTecnico) ciasHijasUsuario else emptyList(),
                            ciaSeleccionada = null,
                            productores = emptyList(),
                            ranchos = emptyList(),
                            parcelas = emptyList(),
                            ciclos = emptyList(),

                            productorSeleccionado = null,
                            ranchoSeleccionado = null,
                            parcelaSeleccionada = null,
                            cicloSeleccionado = null,

                            monitoreosEncontrados = emptyList(),
                            productoresResultado = emptyList(),
                            ranchosResultado = emptyList(),
                            parcelasResultado = emptyList(),
                            programasResultado = emptyList(),
                            cultivosResultado = emptyList(),

                            pantallaActual = obtenerPantallaInicialPorRol(sesion)
                        )
                    }

                    if (sesion.esTecnico || sesion.esInvitado) {
                        mostrarMensaje("Bienvenido ${sesion.firstName}. Cargando tus monitoreos asignados")
                        cargarMonitoreosDirectoPorUsuario(sesion)
                    } else {
                        when {
                            sesion.esSupervisor && ciasHijasUsuario.isEmpty() -> {
                                mostrarMensaje("Bienvenido ${sesion.firstName}. No tienes CIAS hijas asignadas")
                            }

                            sesion.esSupervisor -> {
                                mostrarMensaje("Bienvenido ${sesion.firstName}. Selecciona una CIA hija")
                            }

                            parentCias.isEmpty() -> {
                                mostrarMensaje("Bienvenido ${sesion.firstName}. No tienes CIAS padre asignadas")
                            }

                            else -> {
                                mostrarMensaje("Bienvenido ${sesion.firstName} - Rol: ${sesion.roleName}")
                            }
                        }
                    }
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
        val roleNameDb = nombreRolRealParaDb(roleInput)

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
                            insertarRolesInicialesSiNoExisten()

                            val rol = database.localRoleDao().getRoleByName(roleNameDb)
                                ?: throw IllegalStateException("No existe el rol $roleNameDb")

                            database.userDao().insertUser(
                                UserEntity(
                                    firstName = firstName,
                                    lastName = lastname.ifBlank { null },
                                    username = username,
                                    email = email,
                                    password = password,
                                    idRole = rol.idRole
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

    fun onParentCiaChange(parentCia: LocalParentCiaEntity) {
        actualizarEstado {
            it.copy(
                parentCiaSeleccionada = parentCia,

                ciasUsuario = emptyList(),
                ciaSeleccionada = null,

                productores = emptyList(),
                ranchos = emptyList(),
                parcelas = emptyList(),
                ciclos = emptyList(),

                productorSeleccionado = null,
                ranchoSeleccionado = null,
                parcelaSeleccionada = null,
                cicloSeleccionado = null,

                monitoreosEncontrados = emptyList(),
                productoresResultado = emptyList(),
                ranchosResultado = emptyList(),
                parcelasResultado = emptyList(),
                programasResultado = emptyList(),
                cultivosResultado = emptyList()
            )
        }

        cargarCiasHijasDeParent(parentCia)
    }

    private fun cargarCiasHijasDeParent(parentCia: LocalParentCiaEntity) {
        val sesion = uiState.usuarioSesion

        if (sesion == null) {
            mostrarMensaje("No hay sesión activa")
            irA(PantallaActual.LOGIN)
            return
        }

        viewModelScope.launch {
            try {
                val ciasHijas = withContext(Dispatchers.IO) {
                    database.localCiaDao()
                        .getCiasByParentCia(parentCia.idParentCia)
                }

                actualizarEstado { estadoActual ->
                    if (estadoActual.parentCiaSeleccionada?.idParentCia != parentCia.idParentCia) {
                        estadoActual
                    } else {
                        estadoActual.copy(
                            ciasUsuario = ciasHijas,

                            // IMPORTANTE:
                            // Cuando cambias de CIA padre, tampoco seleccionamos CIA hija automática.
                            ciaSeleccionada = null,

                            productores = emptyList(),
                            ranchos = emptyList(),
                            parcelas = emptyList(),
                            ciclos = emptyList(),

                            productorSeleccionado = null,
                            ranchoSeleccionado = null,
                            parcelaSeleccionada = null,
                            cicloSeleccionado = null,

                            monitoreosEncontrados = emptyList(),
                            productoresResultado = emptyList(),
                            ranchosResultado = emptyList(),
                            parcelasResultado = emptyList(),
                            programasResultado = emptyList(),
                            cultivosResultado = emptyList()
                        )
                    }
                }

                if (ciasHijas.isEmpty()) {
                    mostrarMensaje("Esta CIA padre no tiene CIAS hijas asignadas")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar CIAS hijas: ${e.message}")
            }
        }
    }

    fun seleccionarParentCiaActual() {
        val parentCia = uiState.parentCiaSeleccionada

        if (parentCia == null) {
            mostrarMensaje("Selecciona una CIA padre")
            return
        }

        cargarCiasHijasDeParent(parentCia)
        irA(PantallaActual.SELECCION_CIA)
    }

    fun onCiaChange(cia: LocalCiaEntity) {
        actualizarEstado { it.copy(ciaSeleccionada = cia) }
    }

    private fun abrirFiltrosMonitoreoConCia(cia: LocalCiaEntity) {
        limpiarFiltros()

        actualizarEstado {
            it.copy(
                ciaSeleccionada = cia,
                pantallaActual = PantallaActual.FILTROS_MONITOREO
            )
        }

        cargarProductores(cia.idLocalCia)
    }

    fun seleccionarCiaActual() {
        val estado = uiState
        val cia = estado.ciaSeleccionada

        if (cia == null) {
            mostrarMensaje("Este usuario no tiene CIAS hijas asignadas")
            return
        }

        if (estado.seleccionarPreferente) {
            guardarCiaPreferente(
                idUser = estado.idUsuarioActual,
                idLocalCia = cia.idLocalCia
            )
        }

        abrirFiltrosMonitoreoConCia(cia)

        mostrarMensaje("CIA seleccionada: ${cia.nombre}")
    }

    fun cargarProductores(idLocalCia: Long) {
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    database.localCiaAgroUnitDao()
                        .getProductoresByCia(idLocalCia)
                }

                actualizarEstado {
                    it.copy(
                        productores = lista,
                        productorSeleccionado = null,

                        ranchos = emptyList(),
                        ranchoSeleccionado = null,

                        parcelas = emptyList(),
                        parcelaSeleccionada = null,

                        ciclos = emptyList(),
                        cicloSeleccionado = null
                    )
                }

                if (lista.isEmpty()) {
                    mostrarMensaje("La CIA seleccionada no tiene productores asignados")
                }
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
        cargarMonitoreosPorFiltrosProgresivos()
    }

    fun cargarRanchos(idProductor: Long) {
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    database.localRanchDao()
                        .getRanchosByProductor(idProductor)
                }

                actualizarEstado {
                    it.copy(
                        ranchos = lista,
                        ranchoSeleccionado = null,
                        parcelas = emptyList(),
                        parcelaSeleccionada = null,
                        ciclos = emptyList(),
                        cicloSeleccionado = null
                    )
                }

                if (lista.isEmpty()) {
                    mostrarMensaje("El productor seleccionado no tiene ranchos registrados")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar ranchos: ${e.message}")
            }
        }
    }

    fun onRanchoChange(rancho: LocalRanchEntity?) {
        if (rancho == null) {
            actualizarEstado {
                it.copy(
                    ranchoSeleccionado = null,
                    parcelaSeleccionada = null,
                    cicloSeleccionado = null,
                    parcelas = emptyList(),
                    ciclos = emptyList()
                )
            }
            cargarMonitoreosPorFiltrosProgresivos()
            return
        }

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
        cargarMonitoreosPorFiltrosProgresivos()
    }

    fun cargarParcelas(idRanch: Long) {
        viewModelScope.launch {
            try {
                val sesion = uiState.usuarioSesion

                val lista = withContext(Dispatchers.IO) {
                    if (sesion != null && (sesion.esTecnico || sesion.esInvitado)) {
                        database.localPlotDao()
                            .getParcelasByRanchoAndUser(
                                idRanch = idRanch,
                                idUser = sesion.idUser
                            )
                    } else {
                        database.localPlotDao()
                            .getParcelasByRancho(idRanch)
                    }
                }

                actualizarEstado {
                    it.copy(
                        parcelas = lista,
                        parcelaSeleccionada = null,
                        ciclos = emptyList(),
                        cicloSeleccionado = null
                    )
                }

                if (lista.isEmpty()) {
                    val mensaje = if (sesion != null && (sesion.esTecnico || sesion.esInvitado)) {
                        "No tienes parcelas asignadas en este rancho"
                    } else {
                        "El rancho seleccionado no tiene parcelas registradas"
                    }
                    mostrarMensaje(mensaje)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar parcelas: ${e.message}")
            }
        }
    }

    fun onParcelaChange(parcela: LocalPlotEntity?) {
        val productor = uiState.productorSeleccionado

        if (parcela == null) {
            actualizarEstado {
                it.copy(
                    parcelaSeleccionada = null,
                    cicloSeleccionado = null,
                    ciclos = emptyList()
                )
            }
            cargarMonitoreosPorFiltrosProgresivos()
            return
        }

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

        cargarMonitoreosPorFiltrosProgresivos()
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

                actualizarEstado {
                    it.copy(
                        ciclos = lista,
                        cicloSeleccionado = null
                    )
                }
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
                    val sesion = estadoActual.usuarioSesion

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
                            val parcelasDelRancho = if (sesion != null && (sesion.esTecnico || sesion.esInvitado)) {
                                database.localPlotDao().getParcelasByRanchoAndUser(
                                    idRanch = rancho.idLocalRanch,
                                    idUser = sesion.idUser
                                )
                            } else {
                                database.localPlotDao().getParcelasByRancho(rancho.idLocalRanch)
                            }

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

                    val headersBase = if (idsProgramas.isEmpty()) {
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
                                startDate = parseFechaInicioVm(estadoActual.fechaInicioTexto),
                                endDate = parseFechaFinVm(estadoActual.fechaFinTexto),
                                statuses = obtenerEstadosSeleccionadosVm(
                                    saltarFiltros = saltarFiltros,
                                    rolUsuarioActual = estadoActual.rolUsuarioActual,
                                    vigentesChecked = estadoActual.vigentesChecked,
                                    finalizadosChecked = estadoActual.finalizadosChecked,
                                    canceladosChecked = estadoActual.canceladosChecked
                                )
                            )
                    }

                    val idsParcelasBase = headersBase.map { header ->
                        header.idLocalPlot
                    }.distinct()

                    val parcelasBase = if (idsParcelasBase.isEmpty()) {
                        emptyList()
                    } else {
                        database.localPlotDao().getParcelasByIds(idsParcelasBase)
                    }

                    val parcelasBaseMap = parcelasBase.associateBy { parcela ->
                        parcela.idLocalPlot
                    }

                    val headersFiltradosPorRol = when {
                        sesion == null -> emptyList()

                        sesion.esAdmin || sesion.esGerente || sesion.esSupervisor -> {
                            headersBase
                        }

                        sesion.esTecnico -> {
                            headersBase.filter { header ->
                                val parcelaHeader = parcelasBaseMap[header.idLocalPlot]
                                header.assignedUserId == sesion.idUser ||
                                        parcelaHeader?.assignedUserId == sesion.idUser
                            }
                        }

                        sesion.esInvitado -> {
                            headersBase.filter { header ->
                                val capturasUsuario = database.localphytomonitoringcheckpointDao()
                                    .countCheckpointsByHeaderAndUser(
                                        idHeader = header.idHeader,
                                        idUser = sesion.idUser
                                    )

                                header.assignedUserId == sesion.idUser || capturasUsuario > 0
                            }
                        }

                        else -> emptyList()
                    }

                    val idsProgramasResultado = headersFiltradosPorRol.map { header ->
                        header.idProgram
                    }.distinct()

                    val idsParcelasResultado = headersFiltradosPorRol.map { header ->
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

                    val cultivosRel = cargarCultivosRelacionados(
                        headers = headersFiltradosPorRol,
                        programas = programasRel
                    )

                    MainResultadoMonitoreoTemp(
                        headers = headersFiltradosPorRol,
                        productores = productoresRel,
                        ranchos = ranchosRel,
                        parcelas = parcelasRel,
                        programas = programasRel,
                        cultivos = cultivosRel
                    )
                }

                actualizarEstado {
                    it.copy(
                        monitoreosEncontrados = resultado.headers,
                        productoresResultado = resultado.productores,
                        ranchosResultado = resultado.ranchos,
                        parcelasResultado = resultado.parcelas,
                        programasResultado = resultado.programas,
                        cultivosResultado = resultado.cultivos,
                        pantallaActual = PantallaActual.LISTA_MONITOREOS
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al buscar monitoreos: ${e.message}")
            }
        }
    }


    fun cargarMonitoreosPorFiltrosProgresivos() {
        val estado = uiState
        val sesion = estado.usuarioSesion
        val cia = estado.ciaSeleccionada

        if (sesion == null || cia == null) {
            actualizarEstado {
                it.copy(
                    monitoreosEncontrados = emptyList(),
                    productoresResultado = emptyList(),
                    ranchosResultado = emptyList(),
                    parcelasResultado = emptyList(),
                    programasResultado = emptyList(),
                    cultivosResultado = emptyList()
                )
            }
            return
        }

        // Este flujo progresivo aplica solo para administrador, gerente y supervisor.
        // Técnico e invitado conservan su flujo limitado/asignado.
        if (!(sesion.esAdmin || sesion.esGerente || sesion.esSupervisor)) {
            return
        }

        val productor = estado.productorSeleccionado
        if (productor == null) {
            actualizarEstado {
                it.copy(
                    monitoreosEncontrados = emptyList(),
                    productoresResultado = emptyList(),
                    ranchosResultado = emptyList(),
                    parcelasResultado = emptyList(),
                    programasResultado = emptyList(),
                    cultivosResultado = emptyList()
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val estadoActual = uiState
                    val productorActual = estadoActual.productorSeleccionado
                    val ranchoActual = estadoActual.ranchoSeleccionado
                    val parcelaActual = estadoActual.parcelaSeleccionada

                    if (productorActual == null) {
                        MainResultadoMonitoreoTemp(
                            headers = emptyList(),
                            productores = emptyList(),
                            ranchos = emptyList(),
                            parcelas = emptyList(),
                            programas = emptyList(),
                            cultivos = emptyList()
                        )
                    } else {
                        val programasCia = database.localprogramDao()
                            .getProgramasByCia(cia.idLocalCia)

                        var programasFiltrados = programasCia.filter { programa ->
                            programa.idLocalAgroUnit == productorActual.idLocalAgroUnit
                        }

                        ranchoActual?.let { rancho ->
                            programasFiltrados = programasFiltrados.filter { programa ->
                                programa.idLocalRanch == rancho.idLocalRanch
                            }
                        }

                        parcelaActual?.let { parcela ->
                            programasFiltrados = programasFiltrados.filter { programa ->
                                programa.idLocalPlot == parcela.idLocalPlot
                            }
                        }

                        val idsProgramas = programasFiltrados
                            .map { programa -> programa.idProgram }
                            .distinct()

                        val headers = if (idsProgramas.isEmpty()) {
                            emptyList()
                        } else {
                            database.localphytomonitoringheaderDao()
                                .filtrarHeadersMonitoreo(
                                    programIds = idsProgramas,
                                    idProgram = null,
                                    idPlot = parcelaActual?.idLocalPlot,
                                    startDate = null,
                                    endDate = null,
                                    statuses = listOf(
                                        "Pendiente",
                                        "pending",
                                        "En proceso",
                                        "in_progress",
                                        "vigente",
                                        "Completado",
                                        "completed",
                                        "finalizado",
                                        "Cancelado",
                                        "cancelled"
                                    )
                                )
                        }

                        val idsProgramasResultado = headers
                            .map { header -> header.idProgram }
                            .distinct()

                        val idsParcelasResultado = headers
                            .map { header -> header.idLocalPlot }
                            .distinct()

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

                        val idsRanchosResultado = parcelasRel
                            .map { parcela -> parcela.idLocalRanch }
                            .distinct()

                        val ranchosRel = if (idsRanchosResultado.isEmpty()) {
                            emptyList()
                        } else {
                            database.localRanchDao()
                                .getRanchosByIds(idsRanchosResultado)
                        }

                        val idsProductoresResultado = programasRel
                            .map { programa -> programa.idLocalAgroUnit }
                            .distinct()

                        val productoresRel = if (idsProductoresResultado.isEmpty()) {
                            emptyList()
                        } else {
                            database.localAgroUnitDao()
                                .getProductoresByIds(idsProductoresResultado)
                        }

                        val cultivosRel = cargarCultivosRelacionados(
                            headers = headers,
                            programas = programasRel
                        )

                        MainResultadoMonitoreoTemp(
                            headers = headers,
                            productores = productoresRel,
                            ranchos = ranchosRel,
                            parcelas = parcelasRel,
                            programas = programasRel,
                            cultivos = cultivosRel
                        )
                    }
                }

                actualizarEstado {
                    it.copy(
                        busquedaFueConSaltoFiltros = false,
                        monitoreosEncontrados = resultado.headers,
                        productoresResultado = resultado.productores,
                        ranchosResultado = resultado.ranchos,
                        parcelasResultado = resultado.parcelas,
                        programasResultado = resultado.programas,
                        cultivosResultado = resultado.cultivos
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar monitoreos: ${e.message}")
            }
        }
    }

    private fun cargarMonitoreosDirectoPorUsuario(sesion: UsuarioSesion) {
        viewModelScope.launch {
            try {
                actualizarEstado {
                    it.copy(
                        cargando = true,

                        // Aunque se saltan los filtros, dejamos este valor en false
                        // para que MonitoreoListaScreen muestre Productor, Rancho y Parcela.
                        busquedaFueConSaltoFiltros = false,

                        productorSeleccionado = null,
                        ranchoSeleccionado = null,
                        parcelaSeleccionada = null,
                        cicloSeleccionado = null,
                        productores = emptyList(),
                        ranchos = emptyList(),
                        parcelas = emptyList(),
                        ciclos = emptyList()
                    )
                }

                val resultado = withContext(Dispatchers.IO) {
                    val ciasPermitidasTecnico = if (sesion.esTecnico) {
                        database.userLocalParentCiaDao().getCiasHijasByUser(sesion.idUser)
                    } else {
                        emptyList()
                    }

                    val idsProgramasPermitidosTecnico = if (sesion.esTecnico) {
                        ciasPermitidasTecnico
                            .flatMap { cia ->
                                database.localprogramDao()
                                    .getProgramasByCia(cia.idLocalCia)
                            }
                            .map { programa -> programa.idProgram }
                            .distinct()
                            .toSet()
                    } else {
                        emptySet()
                    }

                    val headersTodos = database.localphytomonitoringheaderDao()
                        .getAllHeaders()

                    val idsParcelasTodos = headersTodos
                        .map { header -> header.idLocalPlot }
                        .distinct()

                    val parcelasTodos = if (idsParcelasTodos.isEmpty()) {
                        emptyList()
                    } else {
                        database.localPlotDao()
                            .getParcelasByIds(idsParcelasTodos)
                    }

                    val parcelasTodosMap = parcelasTodos.associateBy { parcela ->
                        parcela.idLocalPlot
                    }

                    val headersUsuario = headersTodos
                        .filter { header ->
                            val parcelaHeader = parcelasTodosMap[header.idLocalPlot]

                            /*
                             * Para técnico:
                             * - Si tiene CIAS hijas cargadas, se limita a esas CIAS.
                             * - Si NO tiene CIAS hijas cargadas, no se bloquean sus monitoreos
                             *   asignados directamente, sus parcelas asignadas o sus capturas.
                             *
                             * Esto corrige el caso donde la consulta SQL sí muestra monitoreos
                             * del técnico, pero la app quedaba en cero porque no encontraba
                             * registros en user_local_parent_cias.
                             */
                            val headerPerteneceACiaPermitida = !sesion.esTecnico ||
                                    idsProgramasPermitidosTecnico.isEmpty() ||
                                    header.idProgram in idsProgramasPermitidosTecnico

                            val capturasUsuario = database.localphytomonitoringcheckpointDao()
                                .countCheckpointsByHeaderAndUser(
                                    idHeader = header.idHeader,
                                    idUser = sesion.idUser
                                )

                            val monitoreoAsignadoAlUsuario = header.assignedUserId == sesion.idUser
                            val parcelaAsignadaAlUsuario = parcelaHeader?.assignedUserId == sesion.idUser
                            val usuarioTieneCapturas = capturasUsuario > 0

                            when {
                                // Técnico: ve solo lo relacionado con su usuario.
                                // Además, si tiene CIAS cargadas, se respeta ese límite.
                                sesion.esTecnico -> {
                                    headerPerteneceACiaPermitida &&
                                            (
                                                    monitoreoAsignadoAlUsuario ||
                                                            parcelaAsignadaAlUsuario ||
                                                            usuarioTieneCapturas
                                                    )
                                }

                                // Invitado: solo consulta monitoreos asignados o donde ya tenga captura.
                                // No usa filtro de parcelas en la interfaz.
                                sesion.esInvitado -> {
                                    monitoreoAsignadoAlUsuario || usuarioTieneCapturas
                                }

                                else -> false
                            }
                        }
                        .sortedByDescending { header -> header.estStartDate ?: 0L }

                    val idsProgramasResultado = headersUsuario
                        .map { header -> header.idProgram }
                        .distinct()

                    val idsParcelasResultado = headersUsuario
                        .map { header -> header.idLocalPlot }
                        .distinct()

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

                    val idsRanchosResultado = parcelasRel
                        .map { parcela -> parcela.idLocalRanch }
                        .distinct()

                    val ranchosRel = if (idsRanchosResultado.isEmpty()) {
                        emptyList()
                    } else {
                        database.localRanchDao()
                            .getRanchosByIds(idsRanchosResultado)
                    }

                    val idsProductoresResultado = programasRel
                        .map { programa -> programa.idLocalAgroUnit }
                        .distinct()

                    val productoresRel = if (idsProductoresResultado.isEmpty()) {
                        emptyList()
                    } else {
                        database.localAgroUnitDao()
                            .getProductoresByIds(idsProductoresResultado)
                    }

                    val cultivosRel = cargarCultivosRelacionados(
                        headers = headersUsuario,
                        programas = programasRel
                    )

                    MainResultadoMonitoreoTemp(
                        headers = headersUsuario,
                        productores = productoresRel,
                        ranchos = ranchosRel,
                        parcelas = parcelasRel,
                        programas = programasRel,
                        cultivos = cultivosRel
                    )
                }

                actualizarEstado {
                    it.copy(
                        monitoreosEncontrados = resultado.headers,
                        productoresResultado = resultado.productores,
                        ranchosResultado = resultado.ranchos,
                        parcelasResultado = resultado.parcelas,
                        programasResultado = resultado.programas,
                        cultivosResultado = resultado.cultivos,

                        // Esto hace que en la interfaz aparezca Productor/Rancho/Parcela.
                        busquedaFueConSaltoFiltros = false,

                        pantallaActual = PantallaActual.LISTA_MONITOREOS
                    )
                }

                if (resultado.headers.isEmpty()) {
                    val mensaje = if (sesion.esTecnico) {
                        "No tienes monitoreos asignados dentro de tus CIAS cargadas"
                    } else {
                        "No tienes monitoreos asignados o capturados"
                    }
                    mostrarMensaje(mensaje)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar tus monitoreos: ${e.message}")
            } finally {
                actualizarEstado { it.copy(cargando = false) }
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

    private suspend fun obtenerHeaderFrescoSeguro(
        header: LocalPhytomonitoringHeaderEntity
    ): LocalPhytomonitoringHeaderEntity {
        return withContext(Dispatchers.IO) {
            database.localphytomonitoringheaderDao()
                .getAllHeaders()
                .firstOrNull { headerDb ->
                    headerDb.idHeader == header.idHeader
                } ?: header
        }
    }

    private fun actualizarHeaderEnLista(
        headerActualizado: LocalPhytomonitoringHeaderEntity
    ) {
        actualizarEstado { estado ->
            estado.copy(
                monitoreosEncontrados = estado.monitoreosEncontrados.map { header ->
                    if (header.idHeader == headerActualizado.idHeader) {
                        headerActualizado
                    } else {
                        header
                    }
                },
                monitoreoSeleccionadoParaMapa =
                if (estado.monitoreoSeleccionadoParaMapa?.idHeader == headerActualizado.idHeader) {
                    headerActualizado
                } else {
                    estado.monitoreoSeleccionadoParaMapa
                },
                monitoreoSeleccionadoParaReporte =
                if (estado.monitoreoSeleccionadoParaReporte?.idHeader == headerActualizado.idHeader) {
                    headerActualizado
                } else {
                    estado.monitoreoSeleccionadoParaReporte
                }
            )
        }
    }

    private fun mandarAReportePorMonitoreoCerrado(
        header: LocalPhytomonitoringHeaderEntity,
        mensaje: String
    ) {
        actualizarEstado {
            it.copy(
                monitoreoSeleccionadoParaReporte = header,
                monitoreoSeleccionadoParaMapa = null,
                puntoSeleccionadoParaRegistro = null,
                pantallaActual = PantallaActual.REPORTE_MONITOREO
            )
        }

        mostrarMensaje(mensaje)
    }

    fun abrirMapa(header: LocalPhytomonitoringHeaderEntity) {
        val sesion = uiState.usuarioSesion

        if (sesion == null) {
            mostrarMensaje("No hay sesión activa")
            irA(PantallaActual.LOGIN)
            return
        }

        if (uiState.cargando) return

        viewModelScope.launch {
            try {
                actualizarEstado { it.copy(cargando = true) }

                /*
                 * IMPORTANTE:
                 * No confiamos solo en el header que viene de la lista, porque puede
                 * estar viejo en memoria. Primero se consulta el estado fresco en Room.
                 */
                val headerActualizado = obtenerHeaderFrescoSeguro(header)
                actualizarHeaderEnLista(headerActualizado)

                if (esEstadoCerradoVm(headerActualizado.status)) {
                    mandarAReportePorMonitoreoCerrado(
                        header = headerActualizado,
                        mensaje = "Este monitoreo ya está cerrado. Solo puedes ver el reporte."
                    )
                    return@launch
                }

                if (sesion.esGerente || sesion.esSupervisor) {
                    mandarAReportePorMonitoreoCerrado(
                        header = headerActualizado,
                        mensaje = "Tu rol solo puede consultar la información del monitoreo."
                    )
                    return@launch
                }

                actualizarEstado {
                    it.copy(
                        monitoreoSeleccionadoParaMapa = headerActualizado,
                        monitoreoSeleccionadoParaReporte = null,
                        puntoSeleccionadoParaRegistro = null,
                        pantallaActual = PantallaActual.MAPA_MONITOREO
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al abrir monitoreo: ${e.message}")
            } finally {
                actualizarEstado { it.copy(cargando = false) }
            }
        }
    }

    fun abrirPuntoParaRegistro(idTargetPoint: Long) {
        val headerActual = uiState.monitoreoSeleccionadoParaMapa
        val sesion = uiState.usuarioSesion

        if (sesion == null) {
            mostrarMensaje("No hay sesión activa")
            irA(PantallaActual.LOGIN)
            return
        }

        if (headerActual == null) {
            mostrarMensaje("No hay monitoreo seleccionado")
            irA(PantallaActual.LISTA_MONITOREOS)
            return
        }

        if (uiState.cargando) return

        viewModelScope.launch {
            try {
                actualizarEstado { it.copy(cargando = true) }


                val headerActualizado = obtenerHeaderFrescoSeguro(headerActual)
                actualizarHeaderEnLista(headerActualizado)

                if (esEstadoCerradoVm(headerActualizado.status)) {
                    mandarAReportePorMonitoreoCerrado(
                        header = headerActualizado,
                        mensaje = "Este monitoreo ya está cerrado. No se pueden registrar más puntos."
                    )
                    return@launch
                }

                if (sesion.esGerente || sesion.esSupervisor) {
                    mandarAReportePorMonitoreoCerrado(
                        header = headerActualizado,
                        mensaje = "Tu rol solo puede consultar monitoreos."
                    )
                    return@launch
                }

                val punto = withContext(Dispatchers.IO) {
                    database.LocalPhytomonitoringTargetPointDao()
                        .getTargetPointById(idTargetPoint)
                }

                if (punto == null) {
                    mostrarMensaje("No se encontró el punto seleccionado")
                } else {
                    actualizarEstado {
                        it.copy(
                            monitoreoSeleccionadoParaMapa = headerActualizado,
                            puntoSeleccionadoParaRegistro = punto,
                            pantallaActual = PantallaActual.REGISTRO_PUNTO_MONITOREO
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al abrir punto: ${e.message}")
            } finally {
                actualizarEstado { it.copy(cargando = false) }
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
        val esCompletado = esEstadoFinalizadoVm(nuevoEstado)

        mostrarMensaje(
            if (esCompletado) {
                "Monitoreo terminado correctamente"
            } else {
                "Monitoreo guardado en proceso"
            }
        )

        val sesion = uiState.usuarioSesion

        actualizarEstado {
            it.copy(
                monitoreoSeleccionadoParaMapa = null,
                puntoSeleccionadoParaRegistro = null,

                /*
                 * Si se terminó un monitoreo, activamos finalizados para que
                 * no desaparezca al regresar a la lista.
                 */
                finalizadosChecked = if (esCompletado) true else it.finalizadosChecked
            )
        }

        if (sesion != null && (sesion.esTecnico || sesion.esInvitado)) {
            cargarMonitoreosDirectoPorUsuario(sesion)
        } else {
            buscarMonitoreos(
                saltarFiltros = uiState.busquedaFueConSaltoFiltros
            )
        }
    }

    fun abrirPanelAdministrador() {
        if (esRolAdministradorVm(uiState.rolUsuarioActual)) {
            irA(PantallaActual.ADMIN_HOME)
        } else {
            mostrarMensaje("Tu rol no tiene acceso al panel administrador")
        }
    }

    fun abrirMonitoreosDesdeEncabezado() {
        val estado = uiState
        val sesion = estado.usuarioSesion

        // Técnico e invitado usan su pantalla propia de monitoreos asignados.
        // Admin, gerente y supervisor usan la pantalla nueva de filtros progresivos.
        if (sesion != null && (sesion.esTecnico || sesion.esInvitado)) {
            cargarMonitoreosDirectoPorUsuario(sesion)
            return
        }

        val ciaActual = estado.ciaSeleccionada

        if (ciaActual != null) {
            abrirFiltrosMonitoreoConCia(ciaActual)
            return
        }

        if (estado.ciasUsuario.isNotEmpty() || estado.parentCiasUsuario.isNotEmpty()) {
            irA(PantallaActual.SELECCION_CIA)
            return
        }

        mostrarMensaje("No hay CIAS asignadas para mostrar monitoreos")
        irA(PantallaActual.SELECCION_CIA)
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
                nombreUsuarioActual = usuario.firstName
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

            PantallaActual.SELECCION_PARENT_CIA,
            PantallaActual.SELECCION_CIA -> {
                cerrarSesion()
            }

            PantallaActual.FILTROS_MONITOREO -> {
                irA(PantallaActual.SELECCION_CIA)
            }

            PantallaActual.LISTA_MONITOREOS -> {
                val sesion = uiState.usuarioSesion
                when {
                    sesion != null && (sesion.esTecnico || sesion.esInvitado) -> {
                        cerrarSesion()
                    }

                    uiState.ciaSeleccionada != null -> {
                        irA(PantallaActual.FILTROS_MONITOREO)
                    }

                    uiState.parentCiaSeleccionada != null -> {
                        irA(PantallaActual.SELECCION_CIA)
                    }

                    else -> {
                        irA(PantallaActual.SELECCION_CIA)
                    }
                }
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
                val sesion = uiState.usuarioSesion
                when {
                    sesion != null && (sesion.esTecnico || sesion.esInvitado) -> cargarMonitoreosDirectoPorUsuario(sesion)
                    uiState.ciaSeleccionada != null -> abrirFiltrosMonitoreoConCia(uiState.ciaSeleccionada!!)
                    uiState.parentCiaSeleccionada != null -> irA(PantallaActual.SELECCION_PARENT_CIA)
                    else -> irA(PantallaActual.LOGIN)
                }
            }

            PantallaActual.ADMIN_HOME -> {
                when {
                    uiState.ciaSeleccionada != null -> abrirFiltrosMonitoreoConCia(uiState.ciaSeleccionada!!)
                    uiState.parentCiaSeleccionada != null -> irA(PantallaActual.SELECCION_CIA)
                    else -> irA(PantallaActual.SELECCION_PARENT_CIA)
                }
            }

            PantallaActual.ADMIN_MONITOREOS,
            PantallaActual.ADMIN_CATALOGOS,
            PantallaActual.ADMIN_GESTION_AGRICOLA -> {
                irA(PantallaActual.ADMIN_HOME)
            }

            else -> Unit
        }
    }

    private suspend fun insertarRolesInicialesSiNoExisten() {
        val rolesBase = listOf(
            LocalRoleEntity(roleName = "INVITADO", level = 1),
            LocalRoleEntity(roleName = "TECNICO", level = 2),
            LocalRoleEntity(roleName = "ING.Y SUPERVISION", level = 3),
            LocalRoleEntity(roleName = "GERENTE", level = 4),
            LocalRoleEntity(roleName = "SUPER ADMIN", level = 5)
        )

        database.localRoleDao().insertRoles(rolesBase)
    }

    private fun insertarDatosInicialesSeguros() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                insertarRolesInicialesSiNoExisten()

                val rolAdmin = database.localRoleDao().getRoleByName("SUPER ADMIN")
                    ?: return@launch

                val totalUsuarios = database.userDao().countUsers()

                if (totalUsuarios == 0) {
                    database.userDao().insertUser(
                        UserEntity(
                            firstName = "Jorge",
                            lastName = "Sandoval",
                            username = "jorge",
                            email = "jorge@test.com",
                            password = "1234",
                            idRole = rolAdmin.idRole
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


}

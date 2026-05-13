package com.example.myapplication.local

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
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
import androidx.activity.compose.BackHandler

enum class PantallaActual {
    LOGIN,
    REGISTRO,
    INFORMACION,
    CONTACTO,
    SELECCION_CIA,
    FILTROS_MONITOREO,
    LISTA_MONITOREOS,
    MAPA_MONITOREO
}

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(applicationContext)

        setContent {
            MaterialTheme {

                val coroutineScope = rememberCoroutineScope()

                var pantallaActual by rememberSaveable {
                    mutableStateOf(PantallaActual.LOGIN)
                }

                var cargando by remember {
                    mutableStateOf(false)
                }

                var idUsuarioActual by rememberSaveable {
                    mutableStateOf(0L)
                }

                var nombreUsuarioActual by rememberSaveable {
                    mutableStateOf("")
                }

                var rolUsuarioActual by rememberSaveable {
                    mutableStateOf("")
                }

                var busquedaFueConSaltoFiltros by rememberSaveable {
                    mutableStateOf(false)
                }

                var ciasUsuario by remember {
                    mutableStateOf<List<LocalCiaEntity>>(emptyList())
                }

                var ciaSeleccionada by remember {
                    mutableStateOf<LocalCiaEntity?>(null)
                }

                var seleccionarPreferente by rememberSaveable {
                    mutableStateOf(false)
                }

                var productores by remember {
                    mutableStateOf<List<LocalAgroUnitEntity>>(emptyList())
                }

                var ranchos by remember {
                    mutableStateOf<List<LocalRanchEntity>>(emptyList())
                }

                var parcelas by remember {
                    mutableStateOf<List<LocalPlotEntity>>(emptyList())
                }

                var ciclos by remember {
                    mutableStateOf<List<LocalProgramEntity>>(emptyList())
                }

                var productorSeleccionado by remember {
                    mutableStateOf<LocalAgroUnitEntity?>(null)
                }

                var ranchoSeleccionado by remember {
                    mutableStateOf<LocalRanchEntity?>(null)
                }

                var parcelaSeleccionada by remember {
                    mutableStateOf<LocalPlotEntity?>(null)
                }

                var cicloSeleccionado by remember {
                    mutableStateOf<LocalProgramEntity?>(null)
                }

                var fechaInicioTexto by rememberSaveable {
                    mutableStateOf("")
                }

                var fechaFinTexto by rememberSaveable {
                    mutableStateOf("")
                }

                var finalizadosChecked by rememberSaveable {
                    mutableStateOf(false)
                }

                var vigentesChecked by rememberSaveable {
                    mutableStateOf(true)
                }

                var canceladosChecked by rememberSaveable {
                    mutableStateOf(false)
                }

                var monitoreosEncontrados by remember {
                    mutableStateOf<List<LocalPhytomonitoringHeaderEntity>>(emptyList())
                }

                var productoresResultado by remember {
                    mutableStateOf<List<LocalAgroUnitEntity>>(emptyList())
                }

                var ranchosResultado by remember {
                    mutableStateOf<List<LocalRanchEntity>>(emptyList())
                }

                var parcelasResultado by remember {
                    mutableStateOf<List<LocalPlotEntity>>(emptyList())
                }

                var programasResultado by remember {
                    mutableStateOf<List<LocalProgramEntity>>(emptyList())
                }

                var monitoreoSeleccionadoParaMapa by remember {
                    mutableStateOf<LocalPhytomonitoringHeaderEntity?>(null)
                }

                fun limpiarFiltros() {
                    productores = emptyList()
                    ranchos = emptyList()
                    parcelas = emptyList()
                    ciclos = emptyList()

                    productorSeleccionado = null
                    ranchoSeleccionado = null
                    parcelaSeleccionada = null
                    cicloSeleccionado = null

                    fechaInicioTexto = ""
                    fechaFinTexto = ""

                    finalizadosChecked = false
                    vigentesChecked = true
                    canceladosChecked = false

                    busquedaFueConSaltoFiltros = false
                    monitoreoSeleccionadoParaMapa = null

                    monitoreosEncontrados = emptyList()
                    productoresResultado = emptyList()
                    ranchosResultado = emptyList()
                    parcelasResultado = emptyList()
                    programasResultado = emptyList()
                }

                fun filtrosCompletos(): Boolean {
                    if (productorSeleccionado == null) {
                        mostrarMensaje("Selecciona un productor")
                        return false
                    }

                    if (ranchoSeleccionado == null) {
                        mostrarMensaje("Selecciona un rancho")
                        return false
                    }

                    if (parcelaSeleccionada == null) {
                        mostrarMensaje("Selecciona una parcela")
                        return false
                    }

                    return true
                }
                fun cargarProductores(idLocalCia: Long) {
                    coroutineScope.launch {
                        try {
                            productores = withContext(Dispatchers.IO) {
                                database.localCiaAgroUnitDao()
                                    .getProductoresByCia(idLocalCia)
                            }
                        } catch (e: Exception) {
                            mostrarMensaje("Error al cargar productores: ${e.message}")
                        }
                    }
                }

                fun cargarRanchos(idProductor: Long) {
                    coroutineScope.launch {
                        try {
                            ranchos = withContext(Dispatchers.IO) {
                                database.localRanchDao()
                                    .getRanchosByProductor(idProductor)
                            }
                        } catch (e: Exception) {
                            mostrarMensaje("Error al cargar ranchos: ${e.message}")
                        }
                    }
                }

                fun cargarParcelas(idRanch: Long) {
                    coroutineScope.launch {
                        try {
                            parcelas = withContext(Dispatchers.IO) {
                                database.localPlotDao()
                                    .getParcelasByRancho(idRanch)
                            }
                        } catch (e: Exception) {
                            mostrarMensaje("Error al cargar parcelas: ${e.message}")
                        }
                    }
                }

                fun cargarCiclos(idProductor: Long, idPlot: Long) {
                    coroutineScope.launch {
                        try {
                            ciclos = withContext(Dispatchers.IO) {
                                database.localprogramDao()
                                    .getCiclosByProductorAndParcela(
                                        idProductor = idProductor,
                                        idPlot = idPlot
                                    )
                            }
                        } catch (e: Exception) {
                            mostrarMensaje("Error al cargar ciclos: ${e.message}")
                        }
                    }
                }

                fun obtenerEstadosSeleccionados(saltarFiltros: Boolean): List<String> {
                    if (saltarFiltros) {
                        return listOf("pending", "in_progress")
                    }

                    return when {
                        finalizadosChecked -> listOf("completed")
                        vigentesChecked -> listOf("pending", "in_progress")
                        canceladosChecked -> listOf("cancelled")
                        else -> emptyList()
                    }
                }

                fun buscarMonitoreos(saltarFiltros: Boolean) {
                    val cia = ciaSeleccionada

                    if (cia == null) {
                        mostrarMensaje("Selecciona una CIA primero")
                        return
                    }

                    busquedaFueConSaltoFiltros = saltarFiltros

                    if (!saltarFiltros && !filtrosCompletos()) {
                        return
                    }

                    coroutineScope.launch {
                        try {
                            val resultado = withContext(Dispatchers.IO) {

                                val programasCia = database.localprogramDao()
                                    .getProgramasByCia(cia.idLocalCia)

                                var programasFiltrados = programasCia

                                if (!saltarFiltros) {
                                    productorSeleccionado?.let { productor ->
                                        programasFiltrados = programasFiltrados.filter { programa ->
                                            programa.idLocalAgroUnit == productor.idLocalAgroUnit
                                        }
                                    }

                                    ranchoSeleccionado?.let { rancho ->
                                        val parcelasDelRancho = database.localPlotDao()
                                            .getParcelasByRancho(rancho.idLocalRanch)

                                        val idsParcelasRancho = parcelasDelRancho.map { parcela ->
                                            parcela.idLocalPlot
                                        }

                                        programasFiltrados = programasFiltrados.filter { programa ->
                                            programa.idLocalPlot in idsParcelasRancho
                                        }
                                    }

                                    parcelaSeleccionada?.let { parcela ->
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
                                            idPlot = if (saltarFiltros) null else parcelaSeleccionada?.idLocalPlot,
                                            startDate = null,
                                            endDate = null,
                                            statuses = listOf(
                                                "pending",
                                                "in_progress",
                                                "completed"
                                            )
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

                                ResultadoMonitoreoTemp(
                                    headers = headers,
                                    productores = productoresRel,
                                    ranchos = ranchosRel,
                                    parcelas = parcelasRel,
                                    programas = programasRel
                                )
                            }

                            monitoreosEncontrados = resultado.headers
                            productoresResultado = resultado.productores
                            ranchosResultado = resultado.ranchos
                            parcelasResultado = resultado.parcelas
                            programasResultado = resultado.programas

                            pantallaActual = PantallaActual.LISTA_MONITOREOS

                        } catch (e: Exception) {
                            mostrarMensaje("Error al buscar monitoreos: ${e.message}")
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    insertarUsuarioAdminSeguro()
                }
                BackHandler(
                    enabled = pantallaActual != PantallaActual.LOGIN
                ) {
                    when (pantallaActual) {

                        PantallaActual.REGISTRO,
                        PantallaActual.INFORMACION,
                        PantallaActual.CONTACTO -> {
                            pantallaActual = PantallaActual.LOGIN
                        }

                        PantallaActual.SELECCION_CIA -> {
                            cerrarSesion(
                                onLimpiarEstados = {
                                    idUsuarioActual = 0L
                                    nombreUsuarioActual = ""
                                    rolUsuarioActual = ""
                                    ciasUsuario = emptyList()
                                    ciaSeleccionada = null
                                    seleccionarPreferente = false
                                    limpiarFiltros()
                                    pantallaActual = PantallaActual.LOGIN
                                }
                            )
                        }

                        PantallaActual.FILTROS_MONITOREO -> {
                            pantallaActual = PantallaActual.SELECCION_CIA
                        }

                        PantallaActual.LISTA_MONITOREOS -> {
                            pantallaActual = PantallaActual.FILTROS_MONITOREO
                        }

                        PantallaActual.MAPA_MONITOREO -> {
                            pantallaActual = PantallaActual.LISTA_MONITOREOS
                        }

                        else -> Unit
                    }
                }

                when (pantallaActual) {

                    PantallaActual.LOGIN -> {
                        LoginScreen(
                            onLoginClick = { usernameInput, passwordInput, roleInput ->

                                val username = usernameInput.trim()
                                val password = passwordInput.trim()
                                val role = roleInput.trim()

                                if (username.isBlank() || password.isBlank()) {
                                    mostrarMensaje("Ingresa usuario y contraseña")
                                } else if (!cargando) {

                                    cargando = true

                                    coroutineScope.launch {
                                        try {
                                            val user = withContext(Dispatchers.IO) {
                                                database.userDao().login(
                                                    username = username,
                                                    password = password,
                                                    role = role
                                                )
                                            }

                                            if (user != null) {

                                                val listaCias = withContext(Dispatchers.IO) {
                                                    database.localCiaDao()
                                                        .obtenerCiasPorUsuario(user.idUser)
                                                }

                                                idUsuarioActual = user.idUser
                                                nombreUsuarioActual = user.firstName
                                                rolUsuarioActual = user.role
                                                ciasUsuario = listaCias

                                                val idCiaPreferente = obtenerCiaPreferente(user.idUser)

                                                ciaSeleccionada = if (idCiaPreferente != 0L) {
                                                    listaCias.firstOrNull { cia ->
                                                        cia.idLocalCia == idCiaPreferente
                                                    } ?: listaCias.firstOrNull()
                                                } else {
                                                    listaCias.firstOrNull()
                                                }

                                                mostrarMensaje("Bienvenido ${user.firstName}")
                                                pantallaActual = PantallaActual.SELECCION_CIA

                                            } else {
                                                mostrarMensaje("Usuario, contraseña o rol incorrecto")
                                            }

                                        } catch (e: Exception) {
                                            mostrarMensaje("Error al iniciar sesión: ${e.message}")
                                        } finally {
                                            cargando = false
                                        }
                                    }
                                }
                            },

                            onRegisterClick = {
                                pantallaActual = PantallaActual.REGISTRO
                            },

                            onForgotPasswordClick = {
                                mostrarMensaje("Aquí irá la recuperación de contraseña")
                            },

                            onInformationClick = {
                                pantallaActual = PantallaActual.INFORMACION
                            },

                            onContactClick = {
                                pantallaActual = PantallaActual.CONTACTO
                            }
                        )
                    }

                    PantallaActual.REGISTRO -> {
                        RegistroUsuarioScreen(
                            onRegisterClick = { firstNameInput, lastnameInput, usernameInput, emailInput, passwordInput, confirmPasswordInput, roleInput ->

                                val firstName = firstNameInput.trim()
                                val lastname = lastnameInput.trim()
                                val username = usernameInput.trim()
                                val email = emailInput.trim()
                                val password = passwordInput.trim()
                                val confirmPassword = confirmPasswordInput.trim()
                                val role = roleInput.trim()

                                if (
                                    firstName.isBlank() ||
                                    username.isBlank() ||
                                    email.isBlank() ||
                                    password.isBlank() ||
                                    confirmPassword.isBlank()
                                ) {
                                    mostrarMensaje("Completa todos los campos obligatorios")
                                } else if (password != confirmPassword) {
                                    mostrarMensaje("Las contraseñas no coinciden")
                                } else if (!cargando) {

                                    cargando = true

                                    coroutineScope.launch {
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
                                            pantallaActual = PantallaActual.LOGIN

                                        } catch (e: Exception) {
                                            mostrarMensaje(
                                                "No se pudo registrar. Revisa si el usuario o correo ya existen"
                                            )
                                        } finally {
                                            cargando = false
                                        }
                                    }
                                }
                            },

                            onBackToLoginClick = {
                                pantallaActual = PantallaActual.LOGIN
                            }
                        )
                    }

                    PantallaActual.INFORMACION -> {
                        InformacionScreen(
                            onCloseClick = {
                                pantallaActual = PantallaActual.LOGIN
                            }
                        )
                    }

                    PantallaActual.CONTACTO -> {
                        ContactoScreen(
                            onCloseClick = {
                                pantallaActual = PantallaActual.LOGIN
                            }
                        )
                    }

                    PantallaActual.SELECCION_CIA -> {
                        SeleccionCiaScreen(
                            nombreUsuario = nombreUsuarioActual,
                            cias = ciasUsuario,
                            ciaSeleccionada = ciaSeleccionada,
                            seleccionarPreferente = seleccionarPreferente,

                            onPreferenteChange = { seleccionado ->
                                seleccionarPreferente = seleccionado
                            },

                            onCiaChange = { cia ->
                                ciaSeleccionada = cia
                            },

                            onSeleccionarClick = {
                                val cia = ciaSeleccionada

                                if (cia == null) {
                                    mostrarMensaje("Este usuario no tiene CIAS asignadas")
                                } else {

                                    if (seleccionarPreferente) {
                                        guardarCiaPreferente(
                                            idUser = idUsuarioActual,
                                            idLocalCia = cia.idLocalCia
                                        )
                                    }

                                    limpiarFiltros()
                                    cargarProductores(cia.idLocalCia)

                                    mostrarMensaje("CIA seleccionada: ${cia.nombre}")
                                    pantallaActual = PantallaActual.FILTROS_MONITOREO
                                }
                            },

                            onCerrarSesionClick = {
                                cerrarSesion(
                                    onLimpiarEstados = {
                                        idUsuarioActual = 0L
                                        nombreUsuarioActual = ""
                                        rolUsuarioActual = ""
                                        ciasUsuario = emptyList()
                                        ciaSeleccionada = null
                                        seleccionarPreferente = false
                                        limpiarFiltros()
                                        pantallaActual = PantallaActual.LOGIN
                                    }
                                )
                            }
                        )
                    }

                    PantallaActual.FILTROS_MONITOREO -> {
                        MonitoreoFiltrosScreen(
                            nombreUsuario = nombreUsuarioActual,
                            nombreCia = ciaSeleccionada?.nombre ?: "Sin CIA",

                            productores = productores,
                            ranchos = ranchos,
                            parcelas = parcelas,

                            productorSeleccionado = productorSeleccionado,
                            ranchoSeleccionado = ranchoSeleccionado,
                            parcelaSeleccionada = parcelaSeleccionada,

                            onProductorChange = { productor ->
                                productorSeleccionado = productor

                                ranchoSeleccionado = null
                                parcelaSeleccionada = null
                                cicloSeleccionado = null

                                ranchos = emptyList()
                                parcelas = emptyList()
                                ciclos = emptyList()

                                cargarRanchos(productor.idLocalAgroUnit)
                            },

                            onRanchoChange = { rancho ->
                                ranchoSeleccionado = rancho

                                parcelaSeleccionada = null
                                cicloSeleccionado = null

                                parcelas = emptyList()
                                ciclos = emptyList()

                                cargarParcelas(rancho.idLocalRanch)
                            },

                            onParcelaChange = { parcela ->
                                parcelaSeleccionada = parcela

                                cicloSeleccionado = null
                                ciclos = emptyList()
                            },

                            onBuscarClick = {
                                buscarMonitoreos(saltarFiltros = false)
                            }
                        )
                    }
                    PantallaActual.LISTA_MONITOREOS -> {
                        MonitoreoListaScreen(
                            nombreUsuario = nombreUsuarioActual,
                            nombreCia = ciaSeleccionada?.nombre ?: "Sin CIA",
                            busquedaFueConSaltoFiltros = busquedaFueConSaltoFiltros,

                            monitoreos = monitoreosEncontrados,
                            productores = productoresResultado,
                            ranchos = ranchosResultado,
                            parcelas = parcelasResultado,
                            programas = programasResultado,

                            onBackClick = {
                                pantallaActual = PantallaActual.FILTROS_MONITOREO
                            },

                            onAbrirMapaClick = { header ->
                                monitoreoSeleccionadoParaMapa = header
                                pantallaActual = PantallaActual.MAPA_MONITOREO
                            }
                        )
                    }

                    PantallaActual.MAPA_MONITOREO -> {
                        val header = monitoreoSeleccionadoParaMapa

                        if (header == null) {
                            LaunchedEffect(Unit) {
                                mostrarMensaje("No se seleccionó ningún monitoreo")
                                pantallaActual = PantallaActual.LISTA_MONITOREOS
                            }
                        } else {
                            val nombreMonitoreo = programasResultado
                                .firstOrNull { programa ->
                                    programa.idProgram == header.idProgram
                                }
                                ?.cycle ?: "Monitoreo ${header.idHeader}"

                            MonitoreoMapaScreen(
                                database = database,
                                header = header,
                                nombreMonitoreo = nombreMonitoreo,
                                onBackClick = {
                                    pantallaActual = PantallaActual.LISTA_MONITOREOS
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun insertarUsuarioAdminSeguro() {
        withContext(Dispatchers.IO) {
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
        val prefs = getSharedPreferences("preferencias_app", MODE_PRIVATE)

        prefs.edit()
            .putLong("cia_preferente_usuario_$idUser", idLocalCia)
            .apply()
    }

    private fun obtenerCiaPreferente(idUser: Long): Long {
        val prefs = getSharedPreferences("preferencias_app", MODE_PRIVATE)

        return prefs.getLong("cia_preferente_usuario_$idUser", 0L)
    }

    private fun cerrarSesion(onLimpiarEstados: () -> Unit) {
        onLimpiarEstados()
        mostrarMensaje("Sesión cerrada")
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(
            this@MainActivity,
            mensaje,
            Toast.LENGTH_SHORT
        ).show()
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

private data class ResultadoMonitoreoTemp(
    val headers: List<LocalPhytomonitoringHeaderEntity>,
    val productores: List<LocalAgroUnitEntity>,
    val ranchos: List<LocalRanchEntity>,
    val parcelas: List<LocalPlotEntity>,
    val programas: List<LocalProgramEntity>
)
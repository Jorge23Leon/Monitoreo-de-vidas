package com.example.myapplication.local.core



import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRoleEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import com.example.myapplication.local.entities.UserEntity
import com.example.myapplication.local.models.UsuarioSesion
import com.example.myapplication.local.api.sync.AgroSyncRepository
import com.example.myapplication.local.api.sync.ResultadoAgroSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.myapplication.local.security.PasswordHasher
import com.example.myapplication.local.api.auth.AuthRepository
import com.example.myapplication.local.api.auth.ResultadoLoginApi
import com.example.myapplication.local.api.auth.ResultadoRefreshApi
import com.example.myapplication.local.api.core.TokenStorage
import com.example.myapplication.local.api.users.ResultadoUsuarioMeApi
import com.example.myapplication.local.api.users.UserRepository
import com.example.myapplication.local.api.users.UsuarioMeResponse
import com.example.myapplication.local.api.auth.ResultadoLogoutApi
import com.example.myapplication.local.api.auth.ResultadoSignupApi
import com.example.myapplication.local.api.organizations.CiaHijaApiItem
import com.example.myapplication.local.api.organizations.CiaPadreApiItem
import com.example.myapplication.local.api.organizations.OrganizationRepository
import com.example.myapplication.local.api.organizations.ResultadoCiasApi
import com.example.myapplication.local.entities.UserLocalParentCiaCrossRef
import com.example.myapplication.local.entities.UserLocalCiaCrossRef
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.myapplication.local.api.monitoreosync.MonitoreoSyncRepository
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import com.example.myapplication.local.api.monitoreosync.ResultadoMonitoreoSync
import com.example.myapplication.local.api.monitoreosync.ResultadoPrepararHeader
import com.example.myapplication.local.api.phytomonitoring.PhytoMonitoringRepository
import com.example.myapplication.local.api.phytomonitoring.ResultadoActualizarHeaderApi




private data class MainSesionRestauradaTemp(
    val sesion: UsuarioSesion,
    val parentCias: List<LocalParentCiaEntity>,
    val ciasHijasUsuario: List<LocalCiaEntity>,
    val parentCiaGuardada: LocalParentCiaEntity?,
    val ciasHijasGuardadas: List<LocalCiaEntity>,
    val ciaGuardada: LocalCiaEntity?
)

private data class MainLoginTemp(
    val sesion: UsuarioSesion,
    val parentCias: List<LocalParentCiaEntity>,
    val ciasHijasUsuario: List<LocalCiaEntity>,
    val parentCiaPreferente: LocalParentCiaEntity?,
    val ciasHijasPreferente: List<LocalCiaEntity>,
    val ciaPreferente: LocalCiaEntity?
)

private data class MainCatalogosFiltrosTemp(
    val productores: List<LocalAgroUnitEntity>,
    val productorRestaurado: LocalAgroUnitEntity?,
    val ranchos: List<LocalRanchEntity>,
    val parcelas: List<LocalPlotEntity>
)

private data class MainHeadersFuenteTemp(
    val headers: List<LocalPhytomonitoringHeaderEntity>,
    val desdeApi: Boolean
)
private sealed class MainLoginServidorTemp {
    data class Exito(
        val datos: MainLoginTemp,
        val access: String?,
        val refresh: String,
        val mensajeSync: String? = null
    ) : MainLoginServidorTemp()

    data class Error(
        val mensaje: String
    ) : MainLoginServidorTemp()
}


class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getDatabase(application.applicationContext)
    private val authRepository = AuthRepository(application.applicationContext)
    private val tokenStorage = TokenStorage(application.applicationContext)
    private val userRepository = UserRepository(application.applicationContext)
    private val organizationRepository = OrganizationRepository(application.applicationContext)

    private val agroSyncRepository = AgroSyncRepository(
        context = application.applicationContext,
        database = database
    )
    private val monitoreoSyncRepository = MonitoreoSyncRepository(
        context = application.applicationContext,
        database = database
    )
    private val headersOnlinePorCia = mutableMapOf<Long, List<LocalPhytomonitoringHeaderEntity>>()
    private val ciasConHeadersOnline = mutableSetOf<Long>()
    private val connectivityManager = application.applicationContext.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as ConnectivityManager
    @Volatile
    private var ultimaConexionValidada = hayConexionInternet()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val disponible = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) && networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            manejarCambioConexion(disponible)
        }

        override fun onLost(network: Network) {
            manejarCambioConexion(hayConexionInternet())
        }
    }
    private val phytoMonitoringRepository = PhytoMonitoringRepository(
        context = application.applicationContext
    )



    var uiState by mutableStateOf(MainUiState())
        private set

    /*
     * Credenciales mostradas únicamente en la pantalla de Login cuando
     * la persona marcó “Recordar usuario y contraseña”.
     */
    var usernameRecordadoLogin by mutableStateOf("")
        private set

    var passwordRecordadaLogin by mutableStateOf("")
        private set

    var recordarCredencialesLogin by mutableStateOf(false)
        private set

    /*
     * La lista usa la respuesta temporal de la API cuando hay red y la ventana
     * protegida de Room cuando no la hay. Estas variables controlan el botón
     * manual de actualización; no bloquean el login ni la navegación.
     */
    var sincronizandoMonitoreos by mutableStateOf(false)
        private set

    var ultimaSincronizacionMonitoreosMillis by mutableStateOf<Long?>(null)
        private set

    private var trabajoSincronizacionMonitoreos: Job? = null

    init {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }.onFailure { error ->
            Log.w("MAIN_VM", "No se pudo observar el cambio de red", error)
        }

        ultimaSincronizacionMonitoreosMillis = obtenerPrefsSincronizacion()
            .getLong("ultima_sync_monitoreos_global", 0L)
            .takeIf { it > 0L }

        cargarCredencialesRecordadasEnLogin()
        prepararSeguridadLocal()
        cargarSesionGuardadaAlIniciar()
    }

    override fun onCleared() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        super.onCleared()
    }

    private fun manejarCambioConexion(disponible: Boolean) {
        if (disponible == ultimaConexionValidada) return
        ultimaConexionValidada = disponible

        viewModelScope.launch {
            val sesion = uiState.usuarioSesion ?: return@launch
            val estaEnConsulta = uiState.pantallaActual == PantallaActual.LISTA_MONITOREOS ||
                    uiState.pantallaActual == PantallaActual.FILTROS_MONITOREO

            if (!estaEnConsulta) return@launch

            if (disponible) {
                mostrarMensaje(
                    "Internet disponible. Toca Sincronizar para consultar el historial completo."
                )
                if (sesion.esTecnico || sesion.esInvitado) {
                    cargarMonitoreosDirectoPorUsuario(
                        sesion = sesion,
                        intentarSincronizacionInicial = false
                    )
                } else {
                    cargarMonitoreosPorFiltrosProgresivos()
                }
            } else if (sesion.esTecnico || sesion.esInvitado) {
                mostrarMensaje(
                    "Sin internet: se muestra el último mes, los 5 monitoreos más recientes y cualquier trabajo pendiente de enviar."
                )
                cargarMonitoreosDirectoPorUsuario(
                    sesion = sesion,
                    intentarSincronizacionInicial = false
                )
            } else {
                mostrarMensaje(
                    "Sin internet: se muestra el último mes, los 5 monitoreos más recientes y cualquier trabajo pendiente de enviar."
                )
                cargarMonitoreosPorFiltrosProgresivos()
            }
        }
    }

    private fun actualizarEstado(transform: (MainUiState) -> MainUiState) {
        uiState = transform(uiState)
    }
    private fun hayConexionInternet(): Boolean {
        val context = getApplication<Application>().applicationContext

        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }


    private fun mostrarMensaje(mensaje: String) {
        val context = getApplication<Application>().applicationContext

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                context,
                mensaje,
                android.widget.Toast.LENGTH_LONG
            ).show()

            actualizarEstado { it.copy(mensaje = mensaje) }
        }
    }
    private fun obtenerPrefsSesion() =
        getApplication<Application>().getSharedPreferences(
            "sesion_app",
            android.content.Context.MODE_PRIVATE
        )

    private fun obtenerPrefsSincronizacion() =
        getApplication<Application>().getSharedPreferences(
            "cache_sincronizacion_monitoreos",
            Context.MODE_PRIVATE
        )

    val textoUltimaSincronizacionMonitoreos: String?
        get() = ultimaSincronizacionMonitoreosMillis?.let { millis ->
            val formato = java.text.SimpleDateFormat(
                "dd MMM, HH:mm",
                Locale("es", "MX")
            )
            "Actualizado: ${formato.format(java.util.Date(millis))}"
        }

    private fun marcarUltimaSincronizacionMonitoreos() {
        val ahora = System.currentTimeMillis()

        ultimaSincronizacionMonitoreosMillis = ahora

        obtenerPrefsSincronizacion()
            .edit()
            .putLong("ultima_sync_monitoreos_global", ahora)
            .apply()
    }

    private fun guardarSesionBasica(idUser: Long) {
        obtenerPrefsSesion()
            .edit()
            .putBoolean("sesion_activa", true)
            .putLong("id_user", idUser)
            .apply()
    }

    private fun guardarParentCiaSesion(idParentCia: Long) {
        obtenerPrefsSesion()
            .edit()
            .putLong("id_parent_cia", idParentCia)
            .remove("id_local_cia")
            .remove("id_productor")
            .remove("id_rancho")
            .remove("id_parcela")
            .apply()
    }
    private fun guardarCiaSesion(
        idParentCia: Long?,
        idLocalCia: Long
    ) {
        obtenerPrefsSesion()
            .edit()
            .putLong("id_parent_cia", idParentCia ?: 0L)
            .putLong("id_local_cia", idLocalCia)
            .remove("id_productor")
            .remove("id_rancho")
            .remove("id_parcela")
            .apply()
    }

    private fun guardarProductorSesion(idProductor: Long) {
        obtenerPrefsSesion()
            .edit()
            .putLong("id_productor", idProductor)
            .remove("id_rancho")
            .remove("id_parcela")
            .apply()
    }

    private fun obtenerProductorSesion(): Long {
        return obtenerPrefsSesion()
            .getLong("id_productor", 0L)
    }
    fun abrirPerfilDesdePantallaActual() {
        actualizarEstado {
            it.copy(
                pantallaAntesPerfil = it.pantallaActual,
                pantallaActual = PantallaActual.PERFIL_USUARIO
            )
        }
    }

    fun volverDesdePerfil() {
        val pantallaDestino = uiState.pantallaAntesPerfil
        val pantallaFallback = when {
            uiState.usuarioSesion?.esTecnico == true || uiState.usuarioSesion?.esInvitado == true -> {
                PantallaActual.MODULOS_TRABAJO
            }

            uiState.ciaSeleccionada != null -> {
                PantallaActual.MODULOS_TRABAJO
            }

            uiState.parentCiaSeleccionada != null -> {
                PantallaActual.SELECCION_CIA
            }

            else -> {
                PantallaActual.LOGIN
            }
        }

        actualizarEstado {
            it.copy(
                pantallaAntesPerfil = null,
                pantallaActual = if (
                    pantallaDestino != null &&
                    pantallaDestino != PantallaActual.PERFIL_USUARIO
                ) {
                    pantallaDestino
                } else {
                    pantallaFallback
                }
            )
        }
    }
    private fun guardarRanchoSesion(idRancho: Long?) {
        val editor = obtenerPrefsSesion().edit()

        if (idRancho == null || idRancho <= 0L) {
            editor.remove("id_rancho")
            editor.remove("id_parcela")
        } else {
            editor.putLong("id_rancho", idRancho)
            editor.remove("id_parcela")
        }

        editor.apply()
    }

    private fun guardarParcelaSesion(idParcela: Long?) {
        val editor = obtenerPrefsSesion().edit()

        if (idParcela == null || idParcela <= 0L) {
            editor.remove("id_parcela")
        } else {
            editor.putLong("id_parcela", idParcela)
        }

        editor.apply()
    }
    private fun limpiarCiaYFiltrosGuardados() {
        obtenerPrefsSesion()
            .edit()
            .remove("id_productor")
            .remove("id_rancho")
            .remove("id_parcela")
            .apply()
    }

    private fun borrarSesionGuardada() {
        obtenerPrefsSesion()
            .edit()
            .clear()
            .apply()
    }

    private fun cargarCredencialesRecordadasEnLogin() {
        val credenciales = tokenStorage.obtenerCredencialesRecordadas()

        usernameRecordadoLogin = credenciales?.username.orEmpty()
        passwordRecordadaLogin = credenciales?.password.orEmpty()
        recordarCredencialesLogin = credenciales != null
    }

    private fun guardarOClearCredencialesRecordadas(
        username: String,
        password: String,
        recordarCredenciales: Boolean
    ) {
        if (recordarCredenciales) {
            val seGuardaron = tokenStorage.guardarCredencialesRecordadas(
                username = username,
                password = password
            )

            if (!seGuardaron) {
                mostrarMensaje(
                    "No se pudieron guardar las credenciales en este dispositivo."
                )
            }
        } else {
            tokenStorage.limpiarCredencialesRecordadas()
        }

        cargarCredencialesRecordadasEnLogin()
    }

    private fun cargarSesionGuardadaAlIniciar() {
        viewModelScope.launch {
            try {
                actualizarEstado {
                    it.copy(
                        cargando = true,
                        pantallaActual = PantallaActual.CARGANDO_SESION
                    )
                }

                val resultado = withContext(Dispatchers.IO) {
                    val prefs = obtenerPrefsSesion()

                    val sesionActiva = prefs.getBoolean("sesion_activa", false)
                    val idUserGuardado = prefs.getLong("id_user", 0L)
                    val idParentCiaGuardada = prefs.getLong("id_parent_cia", 0L)
                    val idLocalCiaGuardada = prefs.getLong("id_local_cia", 0L)
                    val idCiaPreferenteGuardada = obtenerCiaPreferente(idUserGuardado)
                    val idCiaParaRestaurar = if (idLocalCiaGuardada > 0L) {
                        idLocalCiaGuardada
                    } else {
                        idCiaPreferenteGuardada
                    }

                    if (!sesionActiva || idUserGuardado <= 0L) {
                        null
                    } else {
                        val sesion = database.userDao()
                            .getSesionByIdUser(idUserGuardado)

                        if (sesion == null) {
                            null
                        } else {
                            val parentCias = database.userLocalCiaDao()
                                .getParentCiasByUser(sesion.idUser)

                            val ciasHijasUsuario = database.userLocalCiaDao()
                                .getCiasByUser(sesion.idUser)

                            val parentCiaGuardada = if (
                                idParentCiaGuardada > 0L &&
                                !sesion.esSupervisor &&
                                !sesion.esTecnico &&
                                !sesion.esInvitado
                            ) {
                                parentCias.firstOrNull { parentCia ->
                                    parentCia.idParentCia == idParentCiaGuardada
                                }
                            } else {
                                null
                            }

                            val ciasHijasGuardadas = when {
                                sesion.esSupervisor -> {
                                    ciasHijasUsuario
                                }

                                parentCiaGuardada != null -> {
                                    database.userLocalCiaDao()
                                        .getCiasByUserAndParent(
                                            idUser = sesion.idUser,
                                            idParentCia = parentCiaGuardada.idParentCia
                                        )
                                }

                                else -> {
                                    emptyList()
                                }
                            }

                            val ciaGuardada = if (idCiaParaRestaurar > 0L) {
                                ciasHijasGuardadas.firstOrNull { cia ->
                                    cia.idLocalCia == idCiaParaRestaurar
                                }
                            } else {
                                null
                            }

                            MainSesionRestauradaTemp(
                                sesion = sesion,
                                parentCias = parentCias,
                                ciasHijasUsuario = ciasHijasUsuario,
                                parentCiaGuardada = parentCiaGuardada,
                                ciasHijasGuardadas = ciasHijasGuardadas,
                                ciaGuardada = ciaGuardada
                            )
                        }
                    }
                }

                if (resultado == null) {
                    borrarSesionGuardada()
                    tokenStorage.limpiarTokens()

                    actualizarEstado {
                        it.copy(
                            cargando = false,
                            pantallaActual = PantallaActual.LOGIN
                        )
                    }

                    return@launch
                }

                val sesion = resultado.sesion

                actualizarEstado {
                    it.copy(
                        usuarioSesion = sesion,
                        idUsuarioActual = sesion.idUser,
                        nombreUsuarioActual = sesion.firstName,
                        rolUsuarioActual = sesion.roleName,
                        nivelRolUsuarioActual = sesion.level,

                        parentCiasUsuario = resultado.parentCias,
                        parentCiaSeleccionada = resultado.parentCiaGuardada,

                        ciasUsuario = when {
                            resultado.ciasHijasGuardadas.isNotEmpty() -> resultado.ciasHijasGuardadas
                            sesion.esSupervisor || sesion.esTecnico -> resultado.ciasHijasUsuario
                            else -> emptyList()
                        },

                        ciaSeleccionada = resultado.ciaGuardada,
                        seleccionarPreferente = resultado.ciaGuardada != null &&
                                resultado.ciaGuardada.idLocalCia == obtenerCiaPreferente(sesion.idUser),

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

                        cargando = false
                    )
                }

                when {
                    sesion.esTecnico || sesion.esInvitado -> {
                        // Los roles operativos no tienen acceso a aspersión, por lo que
                        // no deben detenerse en la pantalla de módulos.
                        abrirMonitoreosDesdeEncabezado()
                    }

                    resultado.ciaGuardada != null -> {
                        if (sesion.esAdmin || sesion.esGerente) {
                            irA(PantallaActual.MODULOS_TRABAJO)
                        } else {
                            // Supervisor y cualquier rol inferior entran directamente
                            // al módulo fitosanitario de la CIA restaurada.
                            abrirFiltrosMonitoreoConCia(resultado.ciaGuardada)
                        }
                    }

                    else -> {
                        actualizarEstado {
                            it.copy(
                                pantallaActual = PantallaActual.SELECCION_CIA
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()

                borrarSesionGuardada()
                tokenStorage.limpiarTokens()

                actualizarEstado {
                    it.copy(
                        cargando = false,
                        pantallaActual = PantallaActual.LOGIN,
                        mensaje = "No se pudo restaurar la sesión"
                    )
                }
            }
        }
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

    private fun fechaLimiteOffline(): Long {
        return System.currentTimeMillis() - MonitoreoSyncRepository.VENTANA_OFFLINE_MS
    }

    private suspend fun obtenerHeadersFuenteParaCias(
        idsCias: Set<Long>
    ): MainHeadersFuenteTemp {
        val programasCerrados = cerrarMonitoreosVencidosSiAplica()
        if (programasCerrados.isNotEmpty()) {
            idsCias.forEach { idCia ->
                headersOnlinePorCia.remove(idCia)
                ciasConHeadersOnline.remove(idCia)
            }
        }

        val puedeUsarApi = hayConexionInternet() &&
                idsCias.isNotEmpty() &&
                idsCias.all { it in ciasConHeadersOnline }

        if (puedeUsarApi) {
            return MainHeadersFuenteTemp(
                headers = idsCias
                    .flatMap { idCia -> headersOnlinePorCia[idCia].orEmpty() }
                    .distinctBy { it.extId },
                desdeApi = true
            )
        }

        val headerDao = database.localphytomonitoringheaderDao()
        val headersDentroDeVentana = headerDao
            .getHeadersDisponiblesOffline(fechaLimiteOffline())

        /*
         * Aunque el telefono pase meses sin conectarse, la pantalla nunca pierde
         * su referencia historica: conservamos y mostramos al menos los cinco
         * monitoreos mas recientes. Los pendientes/en proceso ya se protegen aparte.
         */
        val headersRespaldo = if (idsCias.isEmpty()) {
            headerDao.getAllHeaders()
                .sortedWith(
                    compareByDescending<LocalPhytomonitoringHeaderEntity> {
                        it.estStartDate
                            ?: it.startAt
                            ?: it.finishedAt
                            ?: it.estFinishDate
                            ?: Long.MIN_VALUE
                    }.thenByDescending { it.idHeader }
                )
                .take(MonitoreoSyncRepository.MINIMO_MONITOREOS_OFFLINE)
        } else {
            idsCias.flatMap { idCia ->
                headerDao.getHeadersMasRecientesPorCia(
                    idLocalCia = idCia,
                    limite = MonitoreoSyncRepository.MINIMO_MONITOREOS_OFFLINE
                )
            }
        }

        val headersOffline = (headersDentroDeVentana + headersRespaldo)
            .distinctBy { it.idHeader }
            .sortedWith(
                compareByDescending<LocalPhytomonitoringHeaderEntity> {
                    it.estStartDate
                        ?: it.startAt
                        ?: it.finishedAt
                        ?: it.estFinishDate
                        ?: Long.MIN_VALUE
                }.thenByDescending { it.idHeader }
            )

        if (idsCias.isEmpty()) {
            return MainHeadersFuenteTemp(
                headers = headersOffline,
                desdeApi = false
            )
        }

        val idsProgramasPermitidos = buildSet {
            idsCias.forEach { idCia ->
                database.localprogramDao()
                    .getProgramasByCia(idCia)
                    .forEach { programa -> add(programa.idProgram) }
            }
        }

        return MainHeadersFuenteTemp(
            headers = headersOffline.filter { it.idProgram in idsProgramasPermitidos },
            desdeApi = false
        )
    }

    private suspend fun cerrarMonitoreosVencidosSiAplica(): Set<Long> {
        val ahora = System.currentTimeMillis()
        val headerDao = database.localphytomonitoringheaderDao()
        val idsProgramas = headerDao
            .getIdsProgramasConMonitoreosVencidos(ahora)
            .toSet()

        if (idsProgramas.isEmpty()) return emptySet()

        database.withTransaction {
            headerDao.cerrarMonitoreosVencidos(ahora)
            idsProgramas.forEach { idProgram ->
                database.localprogramDao().recalcularEstadoDesdeHeaders(idProgram)
            }
        }

        return idsProgramas
    }

    private suspend fun obtenerHeadersFiltradosDeFuente(
        idLocalCia: Long,
        programIds: Set<Long>,
        idProgram: Long?,
        idPlot: Long?,
        startDate: Long?,
        endDate: Long?,
        statuses: Set<String>
    ): List<LocalPhytomonitoringHeaderEntity> {
        if (programIds.isEmpty()) return emptyList()

        val fuente = obtenerHeadersFuenteParaCias(setOf(idLocalCia))

        return fuente.headers
            .asSequence()
            .filter { it.idProgram in programIds }
            .filter { idProgram == null || it.idProgram == idProgram }
            .filter { idPlot == null || it.idLocalPlot == idPlot }
            .filter { header ->
                startDate == null ||
                        header.estStartDate?.let { it >= startDate } == true
            }
            .filter { header ->
                endDate == null ||
                        header.estStartDate?.let { it <= endDate } == true
            }
            .filter { it.status in statuses }
            .sortedByDescending { it.estStartDate ?: 0L }
            .toList()
    }

    fun limpiarMensaje() {
        actualizarEstado { it.copy(mensaje = null) }
    }

    fun irA(pantalla: PantallaActual) {
        actualizarEstado { it.copy(pantallaActual = pantalla) }
    }
    fun solicitarRecuperacionPassword(emailInput: String) {
        val email = emailInput.trim()

        if (
            email.isBlank() ||
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            mostrarMensaje("Ingresa un correo electrónico válido")
            return
        }

        val asunto = "Solicitud de recuperación de contraseña"

        val cuerpo = """
        Hola, solicito recuperar mi contraseña de Tierra Inteligente.

        Correo registrado: $email
        Usuario (si lo recuerda):
        Nombre completo:
        Teléfono de contacto:

        No incluyo mi contraseña anterior por seguridad.
    """.trimIndent()

        val context = getApplication<Application>().applicationContext

        val correoIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:tierrainteligente2@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            putExtra(Intent.EXTRA_TEXT, cuerpo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            val selector = Intent.createChooser(
                correoIntent,
                "Enviar solicitud de recuperación"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(selector)

            mostrarMensaje(
                "Selecciona Gmail o tu aplicación de correo para enviar la solicitud."
            )
        } catch (e: ActivityNotFoundException) {
            val clipboard = context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "Correo de soporte",
                    "tierrainteligente2@gmail.com"
                )
            )

            mostrarMensaje(
                "No hay una app de correo instalada. Se copió el correo de soporte."
            )
        }
    }
    fun volverAConsultaMonitoreos() {
        val sesion = uiState.usuarioSesion

        when {
            sesion == null -> {
                irA(PantallaActual.LOGIN)
            }

            sesion.esTecnico || sesion.esInvitado -> {
                actualizarEstado {
                    it.copy(
                        monitoreoSeleccionadoParaMapa = null,
                        monitoreoSeleccionadoParaReporte = null,
                        puntoSeleccionadoParaRegistro = null,
                        mapaMonitoreoPantallaCompleta = false,
                        pantallaActual = PantallaActual.LISTA_MONITOREOS
                    )
                }
                cargarMonitoreosDirectoPorUsuario(
                    sesion = sesion,
                    intentarSincronizacionInicial = true
                )
            }

            uiState.ciaSeleccionada != null -> {
                actualizarEstado {
                    it.copy(
                        monitoreoSeleccionadoParaMapa = null,
                        monitoreoSeleccionadoParaReporte = null,
                        puntoSeleccionadoParaRegistro = null,
                        mapaMonitoreoPantallaCompleta = false,
                        pantallaActual = PantallaActual.FILTROS_MONITOREO
                    )
                }

                cargarMonitoreosPorFiltrosProgresivos()
            }

            uiState.parentCiaSeleccionada != null -> {
                irA(PantallaActual.SELECCION_CIA)
            }

            else -> {
                irA(PantallaActual.SELECCION_CIA)
            }
        }
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

    private fun obtenerRolRegistroPermitido(roleInput: String): String {
        return when (normalizarRolParaDb(roleInput)) {
            "tecnico" -> "TECNICO"
            "invitado" -> "INVITADO"
            else -> "INVITADO"
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
            // Técnico e invitado no pasan por selección de CIA. Primero eligen
            // entre el módulo fitosanitario y el módulo de aspersión.
            "tecnico",
            "invitado" -> PantallaActual.MODULOS_TRABAJO

            "admin",
            "gerente",
            "supervisor" -> PantallaActual.SELECCION_CIA

            else -> PantallaActual.LOGIN
        }
    }
    private fun obtenerRolLocalDesdePerfilApi(
        perfilApi: UsuarioMeResponse?
    ): String {
        val nombreRolApi = perfilApi?.userRole?.name
            ?.trim()
            .orEmpty()

        if (nombreRolApi.isNotBlank()) {
            return nombreRolRealParaDb(nombreRolApi)
        }

        val nivelRolApi = perfilApi?.userRole?.level ?: 0

        return when {
            nivelRolApi >= 5 -> "SUPER ADMIN"
            nivelRolApi == 4 -> "GERENTE"
            nivelRolApi == 3 -> "ING.Y SUPERVISION"
            nivelRolApi == 2 -> "TECNICO"
            nivelRolApi == 1 -> "INVITADO"
            else -> "INVITADO" // Temporal para que el flujo local no se rompa
        }
    }
    private suspend fun obtenerOCrearUsuarioLocalDespuesDeLoginApi(
        username: String,
        password: String,
        perfilApi: UsuarioMeResponse?
    ): UserEntity {
        insertarRolesInicialesSiNoExisten()

        val extIdServidor = perfilApi?.id
            ?.takeIf { it.isNotBlank() }

        val usernameServidor = perfilApi?.username
            ?.takeIf { it.isNotBlank() }
            ?: username

        val emailServidor = perfilApi?.email
            ?.takeIf { it.isNotBlank() }
            ?: if (usernameServidor.contains("@")) {
                usernameServidor
            } else {
                "$usernameServidor@local.app"
            }

        val firstNameApi = perfilApi?.individual?.firstName
            ?.takeIf { it.isNotBlank() }

        val lastNameApi = perfilApi?.individual?.lastName
            ?.takeIf { it.isNotBlank() }

        val nombreBase = firstNameApi
            ?: usernameServidor
                .substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .replaceFirstChar { char -> char.uppercase() }
                .ifBlank { "Usuario" }

        val nombreRolLocal = obtenerRolLocalDesdePerfilApi(perfilApi)

        val rolLocal = database.localRoleDao().getRoleByName(nombreRolLocal)
            ?: database.localRoleDao().getRoleByName("SUPER ADMIN")
            ?: throw IllegalStateException("No existe el rol local $nombreRolLocal")

        val usuarioExistente = extIdServidor?.let { extId ->
            database.userDao().getUserByExtId(extId)
        }
            ?: database.userDao().getUserByUsername(usernameServidor)
            ?: database.userDao().getUserByUsername(username)
            ?: database.userDao().getUserByEmail(emailServidor)

        if (usuarioExistente != null) {
            database.userDao().updateUser(
                usuarioExistente.copy(
                    extId = extIdServidor ?: usuarioExistente.extId,
                    firstName = nombreBase,
                    lastName = lastNameApi,
                    username = usernameServidor,
                    email = emailServidor,
                    password = PasswordHasher.generarHash(password),
                    idRole = rolLocal.idRole
                )
            )

            return database.userDao().getUserById(usuarioExistente.idUser)
                ?: usuarioExistente
        }

        val idNuevoUsuario = database.userDao().insertUser(
            UserEntity(
                extId = extIdServidor,
                firstName = nombreBase,
                lastName = lastNameApi,
                username = usernameServidor,
                email = emailServidor,
                password = PasswordHasher.generarHash(password),
                idRole = rolLocal.idRole
            )
        )

        return database.userDao().getUserById(idNuevoUsuario)
            ?: throw IllegalStateException("No se pudo crear usuario local")
    }


    private suspend fun intentarLoginLocalSinInternet(
        username: String,
        password: String
    ): MainLoginServidorTemp? {
        if (hayConexionInternet()) {
            return null
        }

        val usuarioLocal = database.userDao().getUserByUsername(username)
            ?: database.userDao().getUserByEmail(username)
            ?: return MainLoginServidorTemp.Error(
                "Sin internet y no existe una sesión local guardada para este usuario. Conéctate una vez para preparar la cache."
            )

        val passwordCorrecto = PasswordHasher.verificarPassword(
            passwordIngresado = password,
            passwordGuardado = usuarioLocal.password
        )

        if (!passwordCorrecto) {
            return MainLoginServidorTemp.Error(
                "Sin internet y la contraseña local no coincide."
            )
        }

        val datosLogin = prepararLoginDesdeUsuarioLocal(usuarioLocal)
            ?: return MainLoginServidorTemp.Error(
                "Sin internet. El usuario existe localmente, pero no se pudo preparar la sesión."
            )

        return MainLoginServidorTemp.Exito(
            datos = datosLogin,
            access = null,
            refresh = "",
            mensajeSync = "Sin internet. Entraste usando la cache local guardada."
        )
    }

    private suspend fun sincronizarDatosOfflineInicialDeUsuario(
        idUserLocal: Long
    ): String? {
        if (!hayConexionInternet()) {
            return "Sin internet. Se usarán los datos locales guardados en este equipo."
        }

        val ciasUsuario = database.userLocalCiaDao()
            .getCiasByUser(idUserLocal)

        val errores = mutableListOf<String>()

        if (ciasUsuario.isEmpty()) {
            // Nunca hacemos una descarga global con idLocalCia = null.
            // Sin una CIA permitida no hay una forma segura de decidir qué datos guardar.
            return "Login correcto. No tienes CIAS asignadas; no se descargaron datos de campo."
        } else {
            ciasUsuario.forEach { cia ->
                val resultadoAgro = kotlinx.coroutines.withTimeoutOrNull(120000L) {
                    agroSyncRepository.sincronizarProductoresRanchosParcelas(
                        idLocalCia = cia.idLocalCia
                    )
                }

                when (resultadoAgro) {
                    null -> errores.add("La actualización de productores/ranchos/parcelas tardó demasiado para ${cia.nombre}")
                    is ResultadoAgroSync.Error -> errores.add(resultadoAgro.mensaje)
                    is ResultadoAgroSync.Exito -> Unit
                }

                when (
                    val resultadoMonitoreo = kotlinx.coroutines.withTimeoutOrNull(120000L) {
                        monitoreoSyncRepository.sincronizarMonitoreosFitosanitarios(
                            idLocalCia = cia.idLocalCia,
                            programasApiPrecargados =
                            (resultadoAgro as? ResultadoAgroSync.Exito)
                                ?.programasApi
                                ?.takeIf { it.isNotEmpty() }
                        )
                    }
                ) {
                    null -> errores.add("La actualización de monitoreos tardó demasiado para ${cia.nombre}")
                    is ResultadoMonitoreoSync.Error -> errores.add(resultadoMonitoreo.mensaje)
                    is ResultadoMonitoreoSync.Exito -> {
                        headersOnlinePorCia[cia.idLocalCia] =
                            resultadoMonitoreo.headersOnline
                        ciasConHeadersOnline += cia.idLocalCia
                    }
                }
            }
        }

        return errores
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " | ") { it }
            ?.let { "Login correcto, pero la cache quedó parcial: $it" }
    }

    private suspend fun sincronizarCiasDesdeApi(
        idUserLocal: Long,
        perfilApi: UsuarioMeResponse
    ): String? {
        return when (val resultado = organizationRepository.obtenerCiasPadreEHijas()) {
            is ResultadoCiasApi.Error -> {
                // No bloqueamos el login si falla la carga de CIAS.
                resultado.mensaje
            }

            is ResultadoCiasApi.Exito -> {
                val nombreRol = perfilApi.userRole?.name.orEmpty()
                val nivelRol = perfilApi.userRole?.level ?: 0

                val esSuperAdmin = nivelRol >= 5 ||
                        normalizarRolParaDb(nombreRol) == "admin"

                val datacentralIdsPermitidos = perfilApi.datacentrals
                    ?.mapNotNull { datacentral ->
                        datacentral.id?.takeIf { it.isNotBlank() }
                    }
                    ?.toSet()
                    .orEmpty()

                // Primero limpiamos lo anterior para que no se queden CIAS viejas.
                database.userLocalParentCiaDao()
                    .quitarTodasLasParentCiasDelUsuario(idUserLocal)

                database.userLocalCiaDao()
                    .quitarTodasLasCiasDelUsuario(idUserLocal)

                val ciasHijasPermitidas = if (esSuperAdmin) {
                    resultado.ciasHijas
                } else {
                    resultado.ciasHijas.filter { ciaHija ->
                        ciaHija.extId in datacentralIdsPermitidos
                    }
                }

                // Aquí está la regla nueva:
                // si no tiene CIAS, NO es error. Se loguea y la lista queda vacía.
                if (ciasHijasPermitidas.isEmpty()) {
                    return "Login correcto. No tienes CIAS asignadas."
                }

                val refsParent = mutableListOf<UserLocalParentCiaCrossRef>()
                val refsCias = mutableListOf<UserLocalCiaCrossRef>()

                ciasHijasPermitidas.forEach { ciaHijaApi ->
                    val idParentLocal = guardarCiaPadreDesdeApi(ciaHijaApi.parent)

                    val idCiaLocal = guardarCiaHijaDesdeApi(
                        ciaHijaApi = ciaHijaApi,
                        idParentCiaLocal = idParentLocal
                    )

                    refsParent.add(
                        UserLocalParentCiaCrossRef(
                            idUser = idUserLocal,
                            idParentCia = idParentLocal
                        )
                    )

                    refsCias.add(
                        UserLocalCiaCrossRef(
                            idUser = idUserLocal,
                            idLocalCia = idCiaLocal
                        )
                    )
                }

                database.userLocalParentCiaDao()
                    .asignarParentCiasAUsuario(refsParent.distinct())

                database.userLocalCiaDao()
                    .asignarCiasAUsuario(refsCias.distinct())

                null
            }
        }
    }

    private suspend fun guardarCiaPadreDesdeApi(
        parentApi: CiaPadreApiItem
    ): Long {
        val existentePorExtId = database.localParentCiaDao()
            .getParentCiaByExtId(parentApi.extId)

        val existentePorSlug = database.localParentCiaDao()
            .getParentCiaBySlug(parentApi.slug)

        val existente = existentePorExtId ?: existentePorSlug

        if (existente != null) {
            database.localParentCiaDao().updateParentCiaApiById(
                idParentCia = existente.idParentCia,
                extId = parentApi.extId,
                name = parentApi.name,
                slug = parentApi.slug,
                description = parentApi.description,
                level = 1
            )

            return existente.idParentCia
        }

        val idInsertado = database.localParentCiaDao().insertParentCiaApi(
            LocalParentCiaEntity(
                extId = parentApi.extId,
                name = parentApi.name,
                slug = parentApi.slug,
                description = parentApi.description,
                level = 1
            )
        )

        if (idInsertado > 0L) {
            return idInsertado
        }

        return database.localParentCiaDao()
            .getParentCiaByExtId(parentApi.extId)
            ?.idParentCia
            ?: database.localParentCiaDao()
                .getParentCiaBySlug(parentApi.slug)
                ?.idParentCia
            ?: throw IllegalStateException("No se pudo guardar CIA padre ${parentApi.name}")
    }

    private suspend fun guardarCiaHijaDesdeApi(
        ciaHijaApi: CiaHijaApiItem,
        idParentCiaLocal: Long
    ): Long {
        val existentePorExtId = database.localCiaDao()
            .getCiaByExtId(ciaHijaApi.extId)

        val existentePorSlug = database.localCiaDao()
            .getCiaBySlug(ciaHijaApi.slug)

        val existente = existentePorExtId ?: existentePorSlug

        if (existente != null) {
            database.localCiaDao().updateCiaApiById(
                idLocalCia = existente.idLocalCia,
                extId = ciaHijaApi.extId,
                name = ciaHijaApi.name,
                slug = ciaHijaApi.slug,
                description = ciaHijaApi.description,
                idParentCia = idParentCiaLocal
            )

            return existente.idLocalCia
        }

        val idInsertado = database.localCiaDao().insertCiaApi(
            LocalCiaEntity(
                extId = ciaHijaApi.extId,
                nombre = ciaHijaApi.name,
                slug = ciaHijaApi.slug,
                description = ciaHijaApi.description,
                idParentCia = idParentCiaLocal
            )
        )

        if (idInsertado > 0L) {
            return idInsertado
        }

        return database.localCiaDao()
            .getCiaByExtId(ciaHijaApi.extId)
            ?.idLocalCia
            ?: database.localCiaDao()
                .getCiaBySlug(ciaHijaApi.slug)
                ?.idLocalCia
            ?: throw IllegalStateException("No se pudo guardar CIA hija ${ciaHijaApi.name}")
    }
    private suspend fun prepararLoginDesdeUsuarioLocal(
        usuario: UserEntity
    ): MainLoginTemp? {
        val sesion = database.userDao()
            .getSesionByIdUser(usuario.idUser)
            ?: return null

        val parentCias = database.userLocalCiaDao()
            .getParentCiasByUser(sesion.idUser)

        val ciasHijasUsuario = database.userLocalCiaDao()
            .getCiasByUser(sesion.idUser)

        val idCiaPreferente = obtenerCiaPreferente(sesion.idUser)
        var parentCiaPreferente: LocalParentCiaEntity? = null
        var ciasHijasPreferente: List<LocalCiaEntity> = emptyList()

        val ciaPreferente = if (
            idCiaPreferente > 0L &&
            !sesion.esTecnico &&
            !sesion.esInvitado
        ) {
            if (sesion.esSupervisor) {
                ciasHijasUsuario.firstOrNull { cia ->
                    cia.idLocalCia == idCiaPreferente
                }
            } else {
                var ciaEncontrada: LocalCiaEntity? = null

                for (parentCia in parentCias) {
                    val hijas = database.userLocalCiaDao()
                        .getCiasByUserAndParent(
                            idUser = sesion.idUser,
                            idParentCia = parentCia.idParentCia
                        )

                    val candidata = hijas.firstOrNull { cia ->
                        cia.idLocalCia == idCiaPreferente
                    }

                    if (candidata != null) {
                        parentCiaPreferente = parentCia
                        ciasHijasPreferente = hijas
                        ciaEncontrada = candidata
                        break
                    }
                }

                ciaEncontrada
            }
        } else {
            null
        }

        return MainLoginTemp(
            sesion = sesion,
            parentCias = parentCias,
            ciasHijasUsuario = ciasHijasUsuario,
            parentCiaPreferente = parentCiaPreferente,
            ciasHijasPreferente = ciasHijasPreferente,
            ciaPreferente = ciaPreferente
        )
    }

    fun onLoginClick(
        usernameInput: String,
        passwordInput: String,
        recordarCredenciales: Boolean
    ) {
        val username = usernameInput.trim()
        val password = passwordInput

        if (username.isBlank() || password.isBlank()) {
            mostrarMensaje("Ingresa usuario y contraseña")
            return
        }

        if (uiState.cargando) return

        actualizarEstado { it.copy(cargando = true) }

        viewModelScope.launch {
            try {
                val resultadoServidor = withContext(Dispatchers.IO) {
                    intentarLoginLocalSinInternet(
                        username = username,
                        password = password
                    )?.let { return@withContext it }

                    when (val loginApi = authRepository.login(username, password)) {
                        is ResultadoLoginApi.Error -> {
                            intentarLoginLocalSinInternet(
                                username = username,
                                password = password
                            ) ?: MainLoginServidorTemp.Error(loginApi.mensaje)
                        }

                        is ResultadoLoginApi.Exito -> {
                            val refresh = loginApi.refresh

                            if (refresh.isNullOrBlank()) {
                                return@withContext MainLoginServidorTemp.Error(
                                    "El servidor validó el login, pero no regresó refresh token"
                                )
                            }

                            /*
                             * El backend normalmente regresa access + refresh en login.
                             * Si por alguna razón access viene vacío, usamos refresh para
                             * pedir un access nuevo antes de consultar /users/me/.
                             */
                            var access = loginApi.access

                            if (access.isNullOrBlank()) {
                                when (val refreshApi = authRepository.obtenerAccessConRefresh(refresh)) {
                                    is ResultadoRefreshApi.Exito -> {
                                        access = refreshApi.access
                                    }

                                    is ResultadoRefreshApi.Error -> {
                                        return@withContext MainLoginServidorTemp.Error(refreshApi.mensaje)
                                    }
                                }
                            }

                            val accessSeguro = access?.takeIf { it.isNotBlank() }
                                ?: return@withContext MainLoginServidorTemp.Error(
                                    "El servidor validó el login, pero no se pudo obtener access token"
                                )

                            /*
                             * A partir de aquí todas las consultas protegidas usan:
                             * Authorization: Bearer <accessSeguro>
                             */
                            tokenStorage.guardarTokens(
                                access = accessSeguro,
                                refresh = refresh
                            )

                            val perfilServidor = when (val perfilApi = userRepository.obtenerPerfilActual()) {
                                is ResultadoUsuarioMeApi.Exito -> {
                                    perfilApi.usuario
                                }

                                is ResultadoUsuarioMeApi.Error -> {
                                    return@withContext MainLoginServidorTemp.Error(
                                        "Login correcto, pero no se pudo obtener perfil: ${perfilApi.mensaje}"
                                    )
                                }
                            }

                            val usuarioLocal = obtenerOCrearUsuarioLocalDespuesDeLoginApi(
                                username = username,
                                password = password,
                                perfilApi = perfilServidor
                            )

                            val mensajeCias = sincronizarCiasDesdeApi(
                                idUserLocal = usuarioLocal.idUser,
                                perfilApi = perfilServidor
                            )

                            /*
                             * El login no descarga productores, ranchos, parcelas ni monitoreos.
                             * Esa sincronización se hace al tocar ⟳ Sincronizar o cuando la
                             * cache local está vacía, ya con la pantalla visible.
                             */
                            val mensajeSyncInicial: String? = null

                            val mensajeSync = listOfNotNull(
                                mensajeCias,
                                mensajeSyncInicial
                            )
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(separator = "\n")
                                .takeIf { it.isNotBlank() }

                            val datosLogin = prepararLoginDesdeUsuarioLocal(usuarioLocal)
                                ?: return@withContext MainLoginServidorTemp.Error(
                                    "Login API correcto, pero no se pudo preparar la sesión local"
                                )

                            MainLoginServidorTemp.Exito(
                                datos = datosLogin,
                                access = accessSeguro,
                                refresh = refresh,
                                mensajeSync = mensajeSync
                            )
                        }
                    }
                }

                when (resultadoServidor) {
                    is MainLoginServidorTemp.Error -> {
                        mostrarMensaje(resultadoServidor.mensaje)
                    }

                    is MainLoginServidorTemp.Exito -> {
                        val resultado = resultadoServidor.datos
                        val sesion = resultado.sesion
                        val parentCias = resultado.parentCias
                        val ciasHijasUsuario = resultado.ciasHijasUsuario

                        /*
                         * La casilla ahora solo controla si se recuerdan usuario y contraseña.
                         * La sesión activa se conserva mientras no se use “Cerrar sesión”.
                         */
                        guardarSesionBasica(sesion.idUser)

                        guardarOClearCredencialesRecordadas(
                            username = username,
                            password = password,
                            recordarCredenciales = recordarCredenciales
                        )

                        actualizarEstado {
                            it.copy(
                                usuarioSesion = sesion,
                                idUsuarioActual = sesion.idUser,
                                nombreUsuarioActual = sesion.firstName,
                                rolUsuarioActual = sesion.roleName,
                                nivelRolUsuarioActual = sesion.level,

                                parentCiasUsuario = parentCias,
                                parentCiaSeleccionada = resultado.parentCiaPreferente,

                                ciasUsuario = when {
                                    resultado.ciasHijasPreferente.isNotEmpty() -> resultado.ciasHijasPreferente
                                    sesion.esSupervisor || sesion.esTecnico -> ciasHijasUsuario
                                    else -> emptyList()
                                },

                                ciaSeleccionada = resultado.ciaPreferente,
                                seleccionarPreferente = resultado.ciaPreferente != null,

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

                        resultadoServidor.mensajeSync
                            ?.takeIf { it.isNotBlank() }
                            ?.let { mostrarMensaje(it) }

                        if (!(sesion.esTecnico || sesion.esInvitado)) {
                            when {
                                sesion.esSupervisor && ciasHijasUsuario.isEmpty() -> {
                                    mostrarMensaje("Login API correcto. No tienes CIAS hijas asignadas localmente")
                                }

                                resultado.ciaPreferente != null -> {
                                }

                                sesion.esSupervisor -> {
                                }

                                parentCias.isEmpty() -> {
                                    mostrarMensaje("Login API correcto. No tienes CIAS padre cargadas localmente")
                                }

                                else -> {
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al iniciar sesión con API: ${e.message}")
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
        val roleNameDb = obtenerRolRegistroPermitido(roleInput)

        when {
            firstName.isBlank() ||
                    lastname.isBlank() ||
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
                        val mensajeError = withContext(Dispatchers.IO) {
                            val registroApi = authRepository.signup(
                                username = username,
                                email = email,
                                password = password,
                                firstName = firstName,
                                lastName = lastname
                            )

                            if (registroApi is ResultadoSignupApi.Error) {
                                return@withContext registroApi.mensaje
                            }

                            insertarRolesInicialesSiNoExisten()

                            val rol = database.localRoleDao().getRoleByName(roleNameDb)
                                ?: return@withContext "No existe el rol local $roleNameDb"

                            val usuarioExistente = database.userDao().getUserByUsername(username)
                                ?: database.userDao().getUserByEmail(email)

                            if (usuarioExistente != null) {
                                database.userDao().updateUser(
                                    usuarioExistente.copy(
                                        firstName = firstName,
                                        lastName = lastname,
                                        username = username,
                                        email = email,
                                        password = PasswordHasher.generarHash(password),
                                        idRole = rol.idRole
                                    )
                                )
                            } else {
                                database.userDao().insertUser(
                                    UserEntity(
                                        firstName = firstName,
                                        lastName = lastname,
                                        username = username,
                                        email = email,
                                        password = PasswordHasher.generarHash(password),
                                        idRole = rol.idRole
                                    )
                                )
                            }

                            null
                        }

                        if (mensajeError != null) {
                            mostrarMensaje(mensajeError)
                        } else {
                            mostrarMensaje("Usuario registrado correctamente. Ahora inicia sesión")
                            irA(PantallaActual.LOGIN)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mostrarMensaje("No se pudo registrar: ${e.message}")
                    } finally {
                        actualizarEstado { it.copy(cargando = false) }
                    }
                }
            }
        }
    }

    fun onPreferenteChange(seleccionado: Boolean) {
        val idUser = uiState.idUsuarioActual
        val ciaActual = uiState.ciaSeleccionada

        if (seleccionado && ciaActual == null) {
            mostrarMensaje("Primero selecciona una CIA hija")
            return
        }

        if (idUser > 0L) {
            if (seleccionado && ciaActual != null) {
                guardarCiaPreferente(
                    idUser = idUser,
                    idLocalCia = ciaActual.idLocalCia
                )
            } else {
                borrarCiaPreferente(idUser)
            }
        }

        actualizarEstado {
            it.copy(seleccionarPreferente = seleccionado)
        }
    }

    fun onParentCiaChange(parentCia: LocalParentCiaEntity) {
        guardarParentCiaSesion(parentCia.idParentCia)

        actualizarEstado {
            it.copy(
                parentCiaSeleccionada = parentCia,

                ciasUsuario = emptyList(),
                ciaSeleccionada = null,
                seleccionarPreferente = false,

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
                    database.userLocalCiaDao()
                        .getCiasByUserAndParent(
                            idUser = sesion.idUser,
                            idParentCia = parentCia.idParentCia
                        )
                }

                val idCiaPreferente = obtenerCiaPreferente(sesion.idUser)
                val ciaPreferente = ciasHijas.firstOrNull { cia ->
                    cia.idLocalCia == idCiaPreferente
                }

                actualizarEstado { estadoActual ->
                    if (estadoActual.parentCiaSeleccionada?.idParentCia != parentCia.idParentCia) {
                        estadoActual
                    } else {
                        estadoActual.copy(
                            ciasUsuario = ciasHijas,
                            ciaSeleccionada = ciaPreferente,
                            seleccionarPreferente = ciaPreferente != null,

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

                when {
                    ciasHijas.isEmpty() -> {
                        mostrarMensaje("Esta CIA padre no tiene CIAS hijas asignadas")
                    }

                    ciaPreferente != null -> {
                        mostrarMensaje("CIA preferente cargada: ${ciaPreferente.nombre}")
                    }
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
    private fun ciaPermitidaParaUsuario(
        estado: MainUiState,
        cia: LocalCiaEntity
    ): Boolean {
        val perteneceAListaUsuario = estado.ciasUsuario.any { permitida ->
            permitida.idLocalCia == cia.idLocalCia
        }

        val perteneceAlParentSeleccionado =
            estado.parentCiaSeleccionada?.let { parent ->
                cia.idParentCia == parent.idParentCia
            } ?: true

        return perteneceAListaUsuario && perteneceAlParentSeleccionado
    }

    fun onCiaChange(cia: LocalCiaEntity) {
        val estado = uiState

        if (!ciaPermitidaParaUsuario(estado, cia)) {
            actualizarEstado {
                it.copy(
                    ciaSeleccionada = null,
                    seleccionarPreferente = false
                )
            }

            mostrarMensaje("Esta CIA no está asignada al usuario")
            return
        }

        val idUser = estado.idUsuarioActual

        if (idUser > 0L && estado.seleccionarPreferente) {
            guardarCiaPreferente(
                idUser = idUser,
                idLocalCia = cia.idLocalCia
            )
        }

        val idCiaPreferente = if (idUser > 0L) {
            obtenerCiaPreferente(idUser)
        } else {
            0L
        }

        actualizarEstado {
            it.copy(
                ciaSeleccionada = cia,
                seleccionarPreferente = idCiaPreferente == cia.idLocalCia
            )
        }
    }

    private fun abrirFiltrosMonitoreoConCia(
        cia: LocalCiaEntity,
        idProductorRestaurar: Long? = null
    ) {
        /*
         * Primero mostramos Room. No hay solicitudes de red al entrar a filtros:
         * así seleccionar CIA, volver de mapa y abrir la app es inmediato.
         */
        limpiarFiltros()

        actualizarEstado {
            it.copy(
                ciaSeleccionada = cia,
                pantallaActual = PantallaActual.FILTROS_MONITOREO,
                cargando = false
            )
        }

        cargarProductores(
            idLocalCia = cia.idLocalCia,
            idProductorRestaurar = idProductorRestaurar
        )

        /*
         * Primera vez: si no hay programas locales para esta CIA, arrancamos una
         * sola actualización en segundo plano. La pantalla no espera.
         */
        solicitarSincronizacionInicialSiCacheVacia(cia)
    }

    private fun solicitarSincronizacionInicialSiCacheVacia(cia: LocalCiaEntity) {
        if (!hayConexionInternet() || sincronizandoMonitoreos) return

        viewModelScope.launch {
            val cacheVacia = withContext(Dispatchers.IO) {
                database.localprogramDao()
                    .getProgramasByCia(cia.idLocalCia)
                    .isEmpty()
            }

            val faltaListaOnline = cia.idLocalCia !in ciasConHeadersOnline

            if (cacheVacia || faltaListaOnline) {
                sincronizarInformacionActual(mostrarMensajeFinal = false)
            }
        }
    }

    fun seleccionarCiaActual() {
        try {
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

            guardarCiaSesion(
                idParentCia = estado.parentCiaSeleccionada?.idParentCia,
                idLocalCia = cia.idLocalCia
            )

            limpiarFiltros()

            val sesion = estado.usuarioSesion
            if (sesion == null) {
                mostrarMensaje("No hay sesión activa")
                irA(PantallaActual.LOGIN)
                return
            }

            if (sesion.esAdmin || sesion.esGerente) {
                actualizarEstado {
                    it.copy(
                        ciaSeleccionada = cia,
                        pantallaActual = PantallaActual.MODULOS_TRABAJO,
                        cargando = false
                    )
                }
            } else {
                /*
                 * Supervisor y roles inferiores no pueden consultar aspersión.
                 * La pantalla de módulos no aporta ninguna opción adicional, así que
                 * se abre directamente la interfaz de filtros fitosanitarios.
                 */
                abrirFiltrosMonitoreoConCia(cia)
            }
        } catch (t: Throwable) {
            mostrarMensaje(
                "No se pudo seleccionar la CIA: ${t.message ?: "detalle no disponible"}"
            )
        }
    }

    private suspend fun obtenerRanchosDisponiblesParaCiaYProductor(
        idLocalCia: Long,
        idProductor: Long
    ): List<LocalRanchEntity> {
        /*
         * El productor sí debe pertenecer a la CIA seleccionada, pero sus ranchos
         * no necesitan tener un programa para mostrarse en el catálogo.
         */
        val productorPerteneceACia = database.localCiaAgroUnitDao()
            .getProductoresByCia(idLocalCia)
            .any { productor ->
                productor.idLocalAgroUnit == idProductor
            }

        if (!productorPerteneceACia) {
            return emptyList()
        }

        return database.localRanchDao()
            .getRanchosByProductor(idProductor)
            .distinctBy { rancho ->
                rancho.idLocalRanch
            }
            .sortedBy { rancho ->
                rancho.name.trim().lowercase(Locale.getDefault())
            }
    }

    private suspend fun obtenerParcelasDisponiblesParaCiaYRancho(
        idLocalCia: Long,
        idRancho: Long
    ): List<LocalPlotEntity> {
        /*
         * Verificamos que el rancho pertenezca a un productor de la CIA.
         * Después mostramos todas sus parcelas, aunque aún no tengan programa.
         */
        val idsProductoresCia = database.localCiaAgroUnitDao()
            .getProductoresByCia(idLocalCia)
            .map { productor ->
                productor.idLocalAgroUnit
            }
            .toSet()

        val ranchoPerteneceACia = database.localRanchDao()
            .getAllRanches()
            .any { rancho ->
                rancho.idLocalRanch == idRancho &&
                        rancho.idLocalAgroUnit in idsProductoresCia
            }

        if (!ranchoPerteneceACia) {
            return emptyList()
        }

        return database.localPlotDao()
            .getParcelasByRancho(idRancho)
            .distinctBy { parcela ->
                parcela.idLocalPlot
            }
            .sortedBy { parcela ->
                parcela.code?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: parcela.name.trim()
            }
    }
    fun cargarProductores(
        idLocalCia: Long,
        idProductorRestaurar: Long? = null
    ) {
        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val productoresCia = database.localCiaAgroUnitDao()
                        .getProductoresByCia(idLocalCia)
                        .sortedBy { it.commercial_name }

                    val productorRestaurado = idProductorRestaurar
                        ?.takeIf { id -> id > 0L }
                        ?.let { id ->
                            productoresCia.firstOrNull { productor ->
                                productor.idLocalAgroUnit == id
                            }
                        }

                    val ranchosFiltro = if (productorRestaurado != null) {
                        obtenerRanchosDisponiblesParaCiaYProductor(
                            idLocalCia = idLocalCia,
                            idProductor = productorRestaurado.idLocalAgroUnit
                        )
                    } else {
                        emptyList()
                    }

                    MainCatalogosFiltrosTemp(
                        productores = productoresCia,
                        productorRestaurado = productorRestaurado,
                        ranchos = ranchosFiltro,
                        parcelas = emptyList()
                    )
                }

                actualizarEstado {
                    it.copy(
                        productores = resultado.productores,
                        productorSeleccionado = resultado.productorRestaurado,

                        ranchos = resultado.ranchos,
                        ranchoSeleccionado = null,

                        parcelas = emptyList(),
                        parcelaSeleccionada = null,

                        ciclos = emptyList(),
                        cicloSeleccionado = null
                    )
                }

                if (resultado.productores.isEmpty()) {
                    mostrarMensaje("La CIA seleccionada no tiene productores asignados")
                }

                cargarMonitoreosPorFiltrosProgresivos()
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar productores: ${e.message}")
            }
        }
    }


    fun onProductorChange(productor: LocalAgroUnitEntity?) {
        if (productor == null) {
            obtenerPrefsSesion()
                .edit()
                .remove("id_productor")
                .remove("id_rancho")
                .remove("id_parcela")
                .apply()

            actualizarEstado {
                it.copy(
                    productorSeleccionado = null,

                    ranchos = emptyList(),
                    ranchoSeleccionado = null,

                    parcelas = emptyList(),
                    parcelaSeleccionada = null,

                    cicloSeleccionado = null,
                    ciclos = emptyList()
                )
            }

            cargarMonitoreosPorFiltrosProgresivos()
            return
        }

        guardarProductorSesion(productor.idLocalAgroUnit)

        actualizarEstado {
            it.copy(
                productorSeleccionado = productor,

                ranchos = emptyList(),
                ranchoSeleccionado = null,

                parcelas = emptyList(),
                parcelaSeleccionada = null,

                cicloSeleccionado = null,
                ciclos = emptyList()
            )
        }

        cargarRanchos(productor.idLocalAgroUnit)
        cargarMonitoreosPorFiltrosProgresivos()
    }

    private fun cargarRanchosYParcelasParaFiltros(productor: LocalAgroUnitEntity?) {
        val cia = uiState.ciaSeleccionada ?: return
        val idProductorEsperado = productor?.idLocalAgroUnit

        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val productoresCia = database.localCiaAgroUnitDao()
                        .getProductoresByCia(cia.idLocalCia)

                    val idsProductoresBase = if (idProductorEsperado != null) {
                        setOf(idProductorEsperado)
                    } else {
                        productoresCia.map { it.idLocalAgroUnit }.toSet()
                    }

                    val ranchosFiltro = database.localRanchDao()
                        .getAllRanches()
                        .filter { rancho ->
                            rancho.idLocalAgroUnit in idsProductoresBase
                        }
                        .sortedBy { it.name }

                    val idsRanchos = ranchosFiltro
                        .map { it.idLocalRanch }
                        .toSet()

                    val parcelasFiltro = database.localPlotDao()
                        .getAllPlots()
                        .filter { parcela ->
                            parcela.idLocalRanch in idsRanchos
                        }
                        .sortedBy { parcela ->
                            parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
                        }

                    ranchosFiltro to parcelasFiltro
                }

                actualizarEstado { estadoActual ->
                    val productorActual = estadoActual.productorSeleccionado?.idLocalAgroUnit

                    if (
                        estadoActual.ciaSeleccionada?.idLocalCia != cia.idLocalCia ||
                        productorActual != idProductorEsperado
                    ) {
                        estadoActual
                    } else {
                        estadoActual.copy(
                            ranchos = resultado.first,
                            parcelas = resultado.second,
                            ranchoSeleccionado = null,
                            parcelaSeleccionada = null,
                            ciclos = emptyList(),
                            cicloSeleccionado = null
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cargar ranchos y parcelas: ${e.message}")
            }
        }
    }

    fun cargarRanchos(idProductor: Long) {
        val cia = uiState.ciaSeleccionada

        if (cia == null) {
            mostrarMensaje("Selecciona una CIA antes de cargar ranchos")
            return
        }

        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    obtenerRanchosDisponiblesParaCiaYProductor(
                        idLocalCia = cia.idLocalCia,
                        idProductor = idProductor
                    )
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
                    mostrarMensaje(
                        "El productor seleccionado no tiene ranchos registrados en esta CIA"
                    )
                }
            } catch (e: Exception) {
                Log.e("MAIN_VM", "Error al cargar ranchos", e)
                mostrarMensaje("Error al cargar ranchos: ${e.message}")
            }
        }
    }

    fun onRanchoChange(rancho: LocalRanchEntity?) {
        val productorActual = uiState.productorSeleccionado

        if (productorActual == null) {
            mostrarMensaje("Primero selecciona un productor")
            actualizarEstado {
                it.copy(
                    ranchoSeleccionado = null,
                    parcelas = emptyList(),
                    parcelaSeleccionada = null,
                    ciclos = emptyList(),
                    cicloSeleccionado = null
                )
            }
            return
        }

        guardarRanchoSesion(rancho?.idLocalRanch)

        if (rancho == null) {
            actualizarEstado {
                it.copy(
                    ranchoSeleccionado = null,
                    parcelas = emptyList(),
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
                ranchoSeleccionado = rancho,
                parcelas = emptyList(),
                parcelaSeleccionada = null,
                cicloSeleccionado = null,
                ciclos = emptyList()
            )
        }

        cargarParcelas(rancho.idLocalRanch)
        cargarMonitoreosPorFiltrosProgresivos()
    }

    fun cargarParcelas(idRanch: Long) {
        val cia = uiState.ciaSeleccionada
        viewModelScope.launch {
            try {
                val sesion = uiState.usuarioSesion

                val lista = withContext(Dispatchers.IO) {
                    when {
                        sesion != null && (sesion.esTecnico || sesion.esInvitado) -> {
                            database.localPlotDao().getParcelasByRanchoAndUser(
                                idRanch = idRanch,
                                idUser = sesion.idUser
                            )
                        }
                        cia != null -> {
                            obtenerParcelasDisponiblesParaCiaYRancho(
                                idLocalCia = cia.idLocalCia,
                                idRancho = idRanch
                            )
                        }
                        else -> emptyList()
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
                Log.e("MAIN_VM", "Error al cargar parcelas", e)
                mostrarMensaje("Error al cargar parcelas: ${e.message}")
            }
        }
    }


    fun onParcelaChange(parcela: LocalPlotEntity?) {
        val productor = uiState.productorSeleccionado
        val rancho = uiState.ranchoSeleccionado

        if (productor == null) {
            mostrarMensaje("Primero selecciona un productor")
            actualizarEstado {
                it.copy(
                    parcelaSeleccionada = null,
                    ciclos = emptyList(),
                    cicloSeleccionado = null
                )
            }
            return
        }

        if (rancho == null) {
            mostrarMensaje("Primero selecciona un rancho")
            actualizarEstado {
                it.copy(
                    parcelaSeleccionada = null,
                    ciclos = emptyList(),
                    cicloSeleccionado = null
                )
            }
            return
        }

        guardarParcelaSesion(parcela?.idLocalPlot)

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

        val cia = uiState.ciaSeleccionada
        if (cia != null) {
            cargarCiclos(
                idLocalCia = cia.idLocalCia,
                idProductor = productor.idLocalAgroUnit,
                idPlot = parcela.idLocalPlot
            )
        }

        cargarMonitoreosPorFiltrosProgresivos()
    }

    fun cargarCiclos(
        idLocalCia: Long,
        idProductor: Long,
        idPlot: Long
    ) {
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    database.localprogramDao().getCiclosByProductorAndParcela(
                        idLocalCia = idLocalCia,
                        idProductor = idProductor,
                        idPlot = idPlot
                    )
                }

                actualizarEstado {
                    it.copy(ciclos = lista, cicloSeleccionado = null)
                }
            } catch (e: Exception) {
                Log.e("MAIN_VM", "Error al cargar ciclos", e)
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

                    // Buscar solo filtra Room. La sincronización se realiza una vez al abrir la CIA.
                    val programasCia = database.localprogramDao()
                        .getProgramasByCia(cia.idLocalCia)
                        .distinctBy { programa -> programa.idProgram }

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
                        obtenerHeadersFiltradosDeFuente(
                            idLocalCia = cia.idLocalCia,
                            programIds = idsProgramas.toSet(),
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
                            ).toSet()
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
                                val parcelaHeader = parcelasBaseMap[header.idLocalPlot]

                                val capturasUsuario = database.localphytomonitoringcheckpointDao()
                                    .countCheckpointsByHeaderAndUser(
                                        idHeader = header.idHeader,
                                        idUser = sesion.idUser
                                    )

                                val monitoreoAsignadoAlUsuario = header.assignedUserId == sesion.idUser
                                val parcelaAsignadaAlUsuario = parcelaHeader?.assignedUserId == sesion.idUser
                                val usuarioTieneCapturas = capturasUsuario > 0

                                monitoreoAsignadoAlUsuario ||
                                        parcelaAsignadaAlUsuario ||
                                        usuarioTieneCapturas
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

        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val estadoActual = uiState
                    val productorActual = estadoActual.productorSeleccionado
                    val ranchoActual = estadoActual.ranchoSeleccionado
                    val parcelaActual = estadoActual.parcelaSeleccionada

                    // Los cambios de filtros no hacen peticiones de red.
                    val programasCia = database.localprogramDao()
                        .getProgramasByCia(cia.idLocalCia)
                        .distinctBy { programa -> programa.idProgram }

                    var programasFiltrados = programasCia

                    productorActual?.let { productor ->
                        programasFiltrados = programasFiltrados.filter { programa ->
                            programa.idLocalAgroUnit == productor.idLocalAgroUnit
                        }
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
                        obtenerHeadersFiltradosDeFuente(
                            idLocalCia = cia.idLocalCia,
                            programIds = idsProgramas.toSet(),
                            idProgram = null,
                            idPlot = parcelaActual?.idLocalPlot,
                            startDate = null,
                            endDate = null,
                            statuses = setOf(
                                "Pendiente",
                                "pending",
                                "pendiente",
                                "En proceso",
                                "in_progress",
                                "en proceso",
                                "vigente",
                                "Completado",
                                "completed",
                                "completado",
                                "finalizado",
                                "Cancelado",
                                "cancelled",
                                "cancelado",
                                "canceled"
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

    private fun cargarMonitoreosDirectoPorUsuario(
        sesion: UsuarioSesion,
        intentarSincronizacionInicial: Boolean = true
    ) {
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

                val (resultado, idsCiasPermitidasUsuario) = withContext(Dispatchers.IO) {
                    val ciasPermitidasUsuario = database.userLocalCiaDao()
                        .getCiasByUser(sesion.idUser)

                    /*
                     * Con red se usa la lista temporal de la API. Sin red se usa
                     * Room, limitado al último mes, a los cinco más recientes y
                     * al trabajo aún pendiente.
                     */

                    val ciasPermitidasTecnico = if (sesion.esTecnico) {
                        ciasPermitidasUsuario
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

                    val fuenteHeaders = obtenerHeadersFuenteParaCias(
                        ciasPermitidasUsuario
                            .map { it.idLocalCia }
                            .toSet()
                    )
                    val headersTodos = fuenteHeaders.headers

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

                    val headersUsuario = if (fuenteHeaders.desdeApi) {
                        /*
                         * La API ya aplica los permisos del token. Esto también
                         * permite consultar históricos que no viven en Room.
                         */
                        headersTodos
                    } else {
                        headersTodos.filter { header ->
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
                                    monitoreoAsignadoAlUsuario ||
                                            parcelaAsignadaAlUsuario ||
                                            usuarioTieneCapturas
                                }
                                else -> false
                            }
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

                    val resultadoMonitoreos = MainResultadoMonitoreoTemp(
                        headers = headersUsuario,
                        productores = productoresRel,
                        ranchos = ranchosRel,
                        parcelas = parcelasRel,
                        programas = programasRel,
                        cultivos = cultivosRel
                    )

                    resultadoMonitoreos to ciasPermitidasUsuario
                        .map { cia -> cia.idLocalCia }
                        .toSet()
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

                val debeIniciarSincronizacionInicial =
                    intentarSincronizacionInicial &&
                            hayConexionInternet() &&
                            !sincronizandoMonitoreos &&
                            (
                                    resultado.headers.isEmpty() ||
                                            idsCiasPermitidasUsuario.any { idLocalCia ->
                                                idLocalCia !in ciasConHeadersOnline
                                            }
                                    )

                if (debeIniciarSincronizacionInicial) {
                    /*
                     * Usuario nuevo: la lista aparece de inmediato (vacía) y la
                     * primera descarga corre en segundo plano, sin retrasar login.
                     */
                    sincronizarInformacionActual(mostrarMensajeFinal = false)
                }

                if (resultado.headers.isEmpty() && !debeIniciarSincronizacionInicial) {
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

    /**
     * Acción única para el botón ⟳ Sincronizar.
     *
     * No se ejecuta al login, al cambiar filtros ni al regresar de otra pantalla.
     * Admin/gerente/supervisor actualizan solo su CIA actual; técnico/invitado
     * actualizan únicamente las CIAs que el servidor les asignó.
     */
    fun sincronizarInformacionActual(
        mostrarMensajeFinal: Boolean = true
    ) {
        if (sincronizandoMonitoreos) return

        val sesion = uiState.usuarioSesion
        if (sesion == null) {
            mostrarMensaje("No hay sesión activa")
            return
        }

        if (!hayConexionInternet()) {
            mostrarMensaje("Sin internet. Se muestran los datos guardados.")
            if (sesion.esTecnico || sesion.esInvitado) {
                cargarMonitoreosDirectoPorUsuario(
                    sesion = sesion,
                    intentarSincronizacionInicial = false
                )
            } else {
                cargarMonitoreosPorFiltrosProgresivos()
            }
            return
        }

        trabajoSincronizacionMonitoreos?.cancel()

        trabajoSincronizacionMonitoreos = viewModelScope.launch {
            sincronizandoMonitoreos = true

            try {
                val ciasObjetivo = withContext(Dispatchers.IO) {
                    when {
                        sesion.esAdmin || sesion.esGerente || sesion.esSupervisor -> {
                            uiState.ciaSeleccionada?.let { listOf(it) }.orEmpty()
                        }

                        else -> {
                            database.userLocalCiaDao()
                                .getCiasByUser(sesion.idUser)
                                .distinctBy { it.idLocalCia }
                        }
                    }
                }

                if (ciasObjetivo.isEmpty()) {
                    if (mostrarMensajeFinal) {
                        mostrarMensaje("No hay una CIA asignada para actualizar.")
                    }
                    return@launch
                }

                val errores = mutableListOf<String>()
                var actualizacionesCorrectas = 0

                ciasObjetivo.forEach { cia ->
                    /*
                     * Solo la CIA objetivo. Nunca se descarga información global de
                     * todas las CIAs del usuario al tocar el botón.
                     */
                    val resultadoAgro = withContext(Dispatchers.IO) {
                        agroSyncRepository.sincronizarProductoresRanchosParcelas(
                            idLocalCia = cia.idLocalCia
                        )
                    }

                    if (resultadoAgro is ResultadoAgroSync.Error) {
                        errores += "${cia.nombre}: ${resultadoAgro.mensaje}"
                        return@forEach
                    }

                    val resultadoMonitoreos = withContext(Dispatchers.IO) {
                        monitoreoSyncRepository.sincronizarMonitoreosFitosanitarios(
                            idLocalCia = cia.idLocalCia,
                            // El repositorio los revisa solo si faltan o vencio su cache diaria.
                            actualizarCatalogos = false,
                            programasApiPrecargados =
                            (resultadoAgro as? ResultadoAgroSync.Exito)
                                ?.programasApi
                                ?.takeIf { it.isNotEmpty() }
                        )
                    }

                    when (resultadoMonitoreos) {
                        is ResultadoMonitoreoSync.Exito -> {
                            actualizacionesCorrectas++
                            headersOnlinePorCia[cia.idLocalCia] =
                                resultadoMonitoreos.headersOnline
                            ciasConHeadersOnline += cia.idLocalCia

                            if (resultadoMonitoreos.advertencias.isNotEmpty()) {
                                errores += resultadoMonitoreos.advertencias.take(2)
                            }
                        }

                        is ResultadoMonitoreoSync.Error -> {
                            errores += "${cia.nombre}: ${resultadoMonitoreos.mensaje}"
                        }
                    }
                }

                if (actualizacionesCorrectas > 0) {
                    marcarUltimaSincronizacionMonitoreos()
                }

                /*
                 * Releer Room una sola vez al terminar. Los filtros no vuelven a
                 * tocar API y conservan la selección que ya tenía el usuario.
                 */
                if (sesion.esAdmin || sesion.esGerente || sesion.esSupervisor) {
                    val cia = uiState.ciaSeleccionada
                    if (cia != null) {
                        cargarProductores(
                            idLocalCia = cia.idLocalCia,
                            idProductorRestaurar = uiState.productorSeleccionado?.idLocalAgroUnit
                        )
                    }
                } else {
                    cargarMonitoreosDirectoPorUsuario(
                        sesion = sesion,
                        intentarSincronizacionInicial = false
                    )
                }

                if (mostrarMensajeFinal) {
                    when {
                        actualizacionesCorrectas <= 0 && errores.isNotEmpty() -> {
                            mostrarMensaje(
                                "No se pudo actualizar. ${errores.distinct().joinToString(" | ")}"
                            )
                        }

                        errores.isEmpty() -> {
                            mostrarMensaje("Información actualizada correctamente.")
                        }

                        else -> {
                            mostrarMensaje(
                                "Actualización parcial. ${errores.distinct().joinToString(" | ")}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MAIN_VM", "Error actualizando información", e)

                if (mostrarMensajeFinal) {
                    mostrarMensaje(
                        "No se pudo actualizar la información: ${e.message ?: "detalle no disponible"}"
                    )
                }
            } finally {
                sincronizandoMonitoreos = false
            }
        }
    }

    private fun headerPermitidoEnListaActual(header: LocalPhytomonitoringHeaderEntity): Boolean {
        val sesion = uiState.usuarioSesion ?: return false

        fun esMismoHeader(permitido: LocalPhytomonitoringHeaderEntity): Boolean {
            val extActual = header.extId?.trim().orEmpty()
            val extPermitido = permitido.extId?.trim().orEmpty()
            val coincidePorExtId = extActual.isNotBlank() &&
                    extPermitido.isNotBlank() &&
                    extActual == extPermitido

            return coincidePorExtId ||
                    (
                            permitido.idHeader == header.idHeader &&
                                    permitido.idProgram == header.idProgram &&
                                    permitido.idLocalPlot == header.idLocalPlot
                            )
        }

        return when {
            sesion.esAdmin || sesion.esGerente || sesion.esSupervisor -> {
                uiState.ciaSeleccionada != null &&
                        uiState.monitoreosEncontrados.any(::esMismoHeader)
            }

            sesion.esTecnico || sesion.esInvitado -> {
                uiState.monitoreosEncontrados.any(::esMismoHeader)
            }

            else -> false
        }
    }

    private fun bloquearHeaderSinPermiso(header: LocalPhytomonitoringHeaderEntity): Boolean {
        if (headerPermitidoEnListaActual(header)) {
            return false
        }

        mostrarMensaje("No tienes permiso para abrir este monitoreo con la CIA/filtros actuales")
        return true
    }

    fun abrirReporte(header: LocalPhytomonitoringHeaderEntity) {
        if (bloquearHeaderSinPermiso(header)) {
            return
        }

        if (esEstadoCanceladoVm(header.status)) {
            mostrarMensaje("Este monitoreo está cancelado. No se puede ver la información.")
            return
        }

        if (uiState.cargando) return

        viewModelScope.launch {
            try {
                actualizarEstado { it.copy(cargando = true) }

                val headerPreparado = obtenerHeaderFrescoSeguro(header)
                actualizarHeaderEnLista(headerPreparado)

                actualizarEstado {
                    it.copy(
                        monitoreoSeleccionadoParaReporte = headerPreparado,
                        monitoreoSeleccionadoParaMapa = null,
                        puntoSeleccionadoParaRegistro = null,
                        pantallaActual = PantallaActual.REPORTE_MONITOREO
                    )
                }
            } catch (e: Exception) {
                mostrarMensaje(
                    e.message ?: "No se pudo preparar el monitoreo para abrirlo."
                )
            } finally {
                actualizarEstado { it.copy(cargando = false) }
            }
        }
    }

    private suspend fun obtenerHeaderFrescoSeguro(
        header: LocalPhytomonitoringHeaderEntity
    ): LocalPhytomonitoringHeaderEntity {
        return withContext(Dispatchers.IO) {
            val dao = database.localphytomonitoringheaderDao()
            val local = header.extId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { dao.getHeaderByExtId(it) }
                ?: dao.getHeaderById(header.idHeader)

            if (local != null) {
                return@withContext local
            }

            if (!hayConexionInternet()) {
                throw IllegalStateException(
                    "Sin internet solo puedes abrir monitoreos del último mes, los 5 más recientes o trabajo pendiente."
                )
            }

            when (
                val preparado = monitoreoSyncRepository.prepararHeaderParaAbrir(header)
            ) {
                is ResultadoPrepararHeader.Exito -> preparado.header
                is ResultadoPrepararHeader.Error -> throw IllegalStateException(
                    preparado.mensaje
                )
            }
        }
    }

    private fun actualizarHeaderEnLista(
        headerActualizado: LocalPhytomonitoringHeaderEntity
    ) {
        headersOnlinePorCia.keys.toList().forEach { idCia ->
            headersOnlinePorCia[idCia] = headersOnlinePorCia[idCia]
                .orEmpty()
                .map { header ->
                    val mismoExtId = !header.extId.isNullOrBlank() &&
                            header.extId == headerActualizado.extId
                    if (header.idHeader == headerActualizado.idHeader || mismoExtId) {
                        headerActualizado
                    } else {
                        header
                    }
                }
        }

        actualizarEstado { estado ->
            estado.copy(
                monitoreosEncontrados = estado.monitoreosEncontrados.map { header ->
                    val mismoExtId = !header.extId.isNullOrBlank() &&
                            header.extId == headerActualizado.extId
                    if (header.idHeader == headerActualizado.idHeader || mismoExtId) {
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

                if (bloquearHeaderSinPermiso(headerActualizado)) {
                    return@launch
                }

                if (esEstadoCanceladoVm(headerActualizado.status)) {
                    mostrarMensaje("Este monitoreo está cancelado. No se puede abrir ni consultar información.")
                    return@launch
                }

                actualizarEstado {
                    it.copy(
                        monitoreoSeleccionadoParaMapa = headerActualizado,
                        monitoreoSeleccionadoParaReporte = null,
                        puntoSeleccionadoParaRegistro = null,
                        mapaMonitoreoPantallaCompleta = false,
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

    fun actualizarModoMapaMonitoreo(pantallaCompleta: Boolean) {
        actualizarEstado {
            it.copy(mapaMonitoreoPantallaCompleta = pantallaCompleta)
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

                if (esEstadoCanceladoVm(headerActualizado.status)) {
                    mostrarMensaje("Este monitoreo está cancelado. No se pueden registrar puntos.")
                    return@launch
                }

                if (esEstadoCerradoVm(headerActualizado.status)) {
                    mandarAReportePorMonitoreoCerrado(
                        header = headerActualizado,
                        mensaje = "Este monitoreo ya está cerrado. No se pueden registrar más puntos."
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
        val headerMapa = uiState.monitoreoSeleccionadoParaMapa
        val sesion = uiState.usuarioSesion

        viewModelScope.launch {
            try {
                val headerFresco = withContext(Dispatchers.IO) {
                    headerMapa?.let { header ->
                        database.localphytomonitoringheaderDao()
                            .getHeaderById(header.idHeader)
                            ?: header.copy(status = nuevoEstado)
                    }
                }

                if (headerFresco != null) {
                    actualizarHeaderEnLista(headerFresco)
                }

                if (esCompletado && headerFresco != null) {
                    actualizarEstado { estado ->
                        estado.copy(
                            monitoreoSeleccionadoParaReporte = headerFresco,
                            monitoreoSeleccionadoParaMapa = null,
                            puntoSeleccionadoParaRegistro = null,
                            finalizadosChecked = true,
                            pantallaActual = PantallaActual.REPORTE_MONITOREO
                        )
                    }

                    mostrarMensaje(
                        "Monitoreo finalizado. Si no había conexión, el estado se reenviará en la siguiente sincronización."
                    )

                    return@launch
                }

                actualizarEstado {
                    it.copy(
                        monitoreoSeleccionadoParaMapa = null,
                        puntoSeleccionadoParaRegistro = null,
                        finalizadosChecked = if (esCompletado) true else it.finalizadosChecked
                    )
                }

                mostrarMensaje(
                    if (
                        headerFresco?.additionalNotes
                            ?.trim()
                            ?.startsWith("PAUSADO", ignoreCase = true) == true
                    ) {
                        "Monitoreo pausado correctamente"
                    } else {
                        "Monitoreo guardado en proceso"
                    }
                )

                if (sesion != null && (sesion.esTecnico || sesion.esInvitado)) {
                    // Al pausar no descargamos de API inmediatamente,
                    // porque puede sobrescribir additionalNotes = "PAUSADO".
                    actualizarEstado {
                        it.copy(
                            pantallaActual = PantallaActual.LISTA_MONITOREOS
                        )
                    }
                } else {
                    actualizarEstado {
                        it.copy(
                            pantallaActual = PantallaActual.FILTROS_MONITOREO
                        )
                    }

                    cargarMonitoreosPorFiltrosProgresivos()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cerrar monitoreo: ${e.message}")
            }
        }
    }



    fun cancelarMonitoreoConfirmado(
        header: LocalPhytomonitoringHeaderEntity,
        motivo: String
    ) {
        val motivoLimpio = motivo.trim()

        if (motivoLimpio.isBlank()) {
            mostrarMensaje("El motivo de cancelación es obligatorio")
            return
        }

        if (uiState.cargando) return

        if (!hayConexionInternet()) {
            mostrarMensaje(
                "Conecta a internet para cancelar el monitoreo en el servidor."
            )
            return
        }

        viewModelScope.launch {
            try {
                actualizarEstado { it.copy(cargando = true) }

                val fechaCancelacion = System.currentTimeMillis()
                val notaCancelacion = "Cancelado: $motivoLimpio"

                val resultadoServidor = withContext(Dispatchers.IO) {
                    val headerExtId = header.extId
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    if (headerExtId == null) {
                        ResultadoActualizarHeaderApi.Error(
                            "El monitoreo no tiene identificador del servidor."
                        )
                    } else {
                        phytoMonitoringRepository.actualizarHeaderServidor(
                            idHeaderExt = headerExtId,
                            status = "cancelled",
                            additionalNotes = notaCancelacion
                        )
                    }
                }

                if (resultadoServidor is ResultadoActualizarHeaderApi.Error) {
                    mostrarMensaje(
                        "No se pudo cancelar en el servidor. Verifica internet e intenta nuevamente."
                    )
                    return@launch
                }

                val headerActualizado = withContext(Dispatchers.IO) {
                    database.localphytomonitoringheaderDao()
                        .cancelarMonitoreoConMotivo(
                            idHeader = header.idHeader,
                            fechaCancelacion = fechaCancelacion,
                            motivoCancelacion = notaCancelacion
                        )

                    database.localphytomonitoringheaderDao()
                        .marcarHeaderSincronizado(header.idHeader)

                    val actualizado = database.localphytomonitoringheaderDao()
                        .getHeaderById(header.idHeader)
                        ?: header.copy(
                            status = "Cancelado",
                            finishedAt = fechaCancelacion,
                            additionalNotes = notaCancelacion,
                            syncPending = false
                        )

                    database.localprogramDao()
                        .recalcularEstadoDesdeHeaders(actualizado.idProgram)

                    actualizado
                }

                actualizarHeaderEnLista(headerActualizado)

                mostrarMensaje(
                    "Monitoreo cancelado y enviado correctamente al servidor."
                )
            } catch (e: Exception) {
                e.printStackTrace()

                mostrarMensaje(
                    "No se pudo cancelar en el servidor. Verifica internet e intenta nuevamente."
                )
            } finally {
                actualizarEstado { it.copy(cargando = false) }
            }
        }
    }

    fun abrirModulosTrabajo() {
        val sesion = uiState.usuarioSesion

        when {
            sesion == null -> {
                mostrarMensaje("No hay sesión activa")
                irA(PantallaActual.LOGIN)
            }

            sesion.esAdmin || sesion.esGerente -> {
                if (uiState.ciaSeleccionada != null) {
                    irA(PantallaActual.MODULOS_TRABAJO)
                } else {
                    mostrarMensaje("Selecciona una CIA antes de abrir los módulos")
                    irA(PantallaActual.SELECCION_CIA)
                }
            }

            else -> {
                /*
                 * Supervisor, técnico e invitado no deben ver la pantalla de módulos,
                 * porque no tienen acceso a aspersión. Se abre monitoreo directamente.
                 * Supervisor entra a FILTROS_MONITOREO; técnico/invitado conservan su
                 * consulta restringida de monitoreos asignados.
                 */
                abrirMonitoreosDesdeEncabezado()
            }
        }
    }

    fun abrirAspersion() {
        val estado = uiState
        val sesion = estado.usuarioSesion

        if (sesion == null) {
            mostrarMensaje("No hay sesión activa")
            irA(PantallaActual.LOGIN)
            return
        }

        if (!sesion.puedeVerAspersion) {
            mostrarMensaje("Solo administrador y gerente pueden consultar aspersión")
            irA(PantallaActual.MODULOS_TRABAJO)
            return
        }

        val cia = estado.ciaSeleccionada

        if (cia == null) {
            mostrarMensaje("Selecciona una CIA antes de consultar aspersión")
            irA(PantallaActual.SELECCION_CIA)
            return
        }

        if (!ciaPermitidaParaUsuario(estado, cia)) {
            actualizarEstado {
                it.copy(ciaSeleccionada = null)
            }

            mostrarMensaje("No tienes acceso a la CIA seleccionada")
            irA(PantallaActual.SELECCION_CIA)
            return
        }

        irA(PantallaActual.ASPERSION_LISTA)
    }
    fun abrirPanelAdministrador() {
        if (puedeVerPanelTrabajoVm(uiState.rolUsuarioActual)) {
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

    fun cambiarCiaDesdeMenu() {
        val sesion = uiState.usuarioSesion

        if (sesion == null) {
            mostrarMensaje("No hay sesión activa")
            irA(PantallaActual.LOGIN)
            return
        }

        if (!(sesion.esAdmin || sesion.esGerente || sesion.esSupervisor)) {
            mostrarMensaje("Tu rol no tiene permitido cambiar de CIA")
            return
        }

        /*
         * Conservamos la CIA padre y la CIA hija actual.
         * Solo limpiamos filtros de monitoreo.
         */
        limpiarCiaYFiltrosGuardados()

        actualizarEstado {
            it.copy(
                // NO borramos ciaSeleccionada para que quede marcada
                // cuando regrese a la pantalla de selección de CIA.

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

                busquedaFueConSaltoFiltros = false,

                monitoreoSeleccionadoParaMapa = null,
                monitoreoSeleccionadoParaReporte = null,
                puntoSeleccionadoParaRegistro = null,

                monitoreosEncontrados = emptyList(),
                productoresResultado = emptyList(),
                ranchosResultado = emptyList(),
                parcelasResultado = emptyList(),
                programasResultado = emptyList(),
                cultivosResultado = emptyList(),

                pantallaActual = PantallaActual.SELECCION_CIA
            )
        }

        mostrarMensaje("Selecciona la CIA con la que quieres trabajar")
    }
    fun onMonitoreoCreadoDesdeAdmin() {
        uiState.ciaSeleccionada?.let { cia ->
            cargarProductores(cia.idLocalCia)
        }

        irA(PantallaActual.ADMIN_HOME)
    }

    fun onPerfilActualizado(usuario: UserEntity) {
        // Si se modificó la contraseña desde Perfil, relee la credencial cifrada
        // para que al cerrar sesión aparezca la contraseña nueva.
        cargarCredencialesRecordadasEnLogin()

        actualizarEstado {
            it.copy(
                idUsuarioActual = usuario.idUser,
                nombreUsuarioActual = usuario.firstName
            )
        }

        mostrarMensaje("Perfil actualizado")
    }

    fun pausarMonitoreoActualParaNavegar(onListo: () -> Unit) {
        val headerActual = uiState.monitoreoSeleccionadoParaMapa

        if (headerActual == null) {
            actualizarEstado {
                it.copy(
                    monitoreoSeleccionadoParaMapa = null,
                    monitoreoSeleccionadoParaReporte = null,
                    puntoSeleccionadoParaRegistro = null
                )
            }
            onListo()
            return
        }

        viewModelScope.launch {
            try {
                actualizarEstado { it.copy(cargando = true) }

                val headerActualizado = withContext(Dispatchers.IO) {
                    val dao = database.localphytomonitoringheaderDao()
                    val fresco = dao.getHeaderById(headerActual.idHeader) ?: headerActual

                    val yaPausado = fresco.additionalNotes
                        .trim()
                        .startsWith("PAUSADO", ignoreCase = true)

                    val estaCerradoOCancelado = esEstadoCerradoVm(fresco.status) ||
                            esEstadoCanceladoVm(fresco.status)

                    if (yaPausado || estaCerradoOCancelado) {
                        fresco
                    } else {
                        val nuevoHeader = fresco.copy(
                            status = "En proceso",
                            startAt = fresco.startAt ?: System.currentTimeMillis(),
                            finishedAt = null,
                            additionalNotes = "PAUSADO",
                            syncPending = true
                        )

                        dao.updateHeader(nuevoHeader)
                        nuevoHeader
                    }
                }

                actualizarHeaderEnLista(headerActualizado)

                actualizarEstado {
                    it.copy(
                        cargando = false,
                        monitoreoSeleccionadoParaMapa = null,
                        monitoreoSeleccionadoParaReporte = null,
                        puntoSeleccionadoParaRegistro = null
                    )
                }

                if (headerActualizado.additionalNotes.trim().startsWith("PAUSADO", ignoreCase = true)) {
                    mostrarMensaje("Monitoreo pausado. Puedes regresar después desde Monitoreos.")
                }

                onListo()
            } catch (e: Exception) {
                e.printStackTrace()
                actualizarEstado { it.copy(cargando = false) }
                mostrarMensaje("No se pudo salir del monitoreo: ${e.message}")
            }
        }
    }

    fun cerrarSesion() {
        if (uiState.cargando) return

        val refreshToken = tokenStorage.obtenerRefreshToken()

        viewModelScope.launch {
            try {
                actualizarEstado { it.copy(cargando = true) }

                val mensajeLogout = withContext(Dispatchers.IO) {
                    if (refreshToken.isNullOrBlank()) {
                        "Sesión cerrada"
                    } else {
                        when (authRepository.logout(refreshToken)) {
                            is ResultadoLogoutApi.Exito -> {
                                "Sesión cerrada correctamente"
                            }

                            is ResultadoLogoutApi.Error -> {

                                "Sesión cerrada localmente"
                            }
                        }
                    }
                }

                borrarSesionGuardada()
                tokenStorage.limpiarTokens()
                headersOnlinePorCia.clear()
                ciasConHeadersOnline.clear()

                uiState = MainUiState(
                    pantallaActual = PantallaActual.LOGIN,
                    mensaje = mensajeLogout
                )
            } catch (e: Exception) {
                e.printStackTrace()

                borrarSesionGuardada()
                tokenStorage.limpiarTokens()
                headersOnlinePorCia.clear()
                ciasConHeadersOnline.clear()

                uiState = MainUiState(
                    pantallaActual = PantallaActual.LOGIN,
                    mensaje = "Sesión cerrada localmente"
                )
            }
        }
    }

    fun manejarBack() {
        when (uiState.pantallaActual) {
            PantallaActual.REGISTRO,
            PantallaActual.RECUPERAR_PASSWORD,
            PantallaActual.INFORMACION,
            PantallaActual.CONTACTO -> {
                irA(PantallaActual.LOGIN)
            }

            PantallaActual.SELECCION_PARENT_CIA,
            PantallaActual.SELECCION_CIA -> {
                mostrarMensaje("Presiona atrás otra vez para salir de la app")
            }

            PantallaActual.MODULOS_TRABAJO -> {
                mostrarMensaje("Presiona atrás otra vez para salir de la app")
            }

            PantallaActual.FILTROS_MONITOREO -> {
                irA(PantallaActual.MODULOS_TRABAJO)
            }

            PantallaActual.LISTA_MONITOREOS -> {
                val sesion = uiState.usuarioSesion
                when {
                    sesion != null && (sesion.esTecnico || sesion.esInvitado) -> {
                        irA(PantallaActual.MODULOS_TRABAJO)
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

            PantallaActual.ASPERSION_LISTA -> {
                irA(PantallaActual.MODULOS_TRABAJO)
            }

            PantallaActual.ASPERSION_DETALLE -> {
                irA(PantallaActual.ASPERSION_LISTA)
            }

            PantallaActual.ASPERSION_MAPA -> {
                irA(PantallaActual.ASPERSION_DETALLE)
            }


            PantallaActual.REGISTRO_PUNTO_MONITOREO -> {
                irA(PantallaActual.MAPA_MONITOREO)
            }

            PantallaActual.MAPA_MONITOREO -> {
                volverAConsultaMonitoreos()
            }

            PantallaActual.REPORTE_MONITOREO -> {
                volverAConsultaMonitoreos()
            }

            PantallaActual.PERFIL_USUARIO -> {
                volverDesdePerfil()
            }

            PantallaActual.ADMIN_HOME -> {
                when {
                    uiState.ciaSeleccionada != null -> irA(PantallaActual.MODULOS_TRABAJO)
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

    private fun esEstadoCanceladoVm(status: String): Boolean {
        return when (status.trim().lowercase(Locale.getDefault())) {
            "cancelado",
            "cancelled",
            "canceled" -> true

            else -> false
        }
    }
    private fun prepararSeguridadLocal() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                insertarRolesInicialesSiNoExisten()
                val usuarios = database.userDao().getAllUsers()

                usuarios.forEach { usuario ->
                    if (PasswordHasher.necesitaRehash(usuario.password)) {
                        database.userDao().updateUser(
                            usuario.copy(
                                password = PasswordHasher.generarHash(usuario.password)
                            )
                        )
                    }
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
        if (idUser <= 0L) return 0L

        val prefs = getApplication<Application>()
            .getSharedPreferences("preferencias_app", android.content.Context.MODE_PRIVATE)

        return prefs.getLong("cia_preferente_usuario_$idUser", 0L)
    }

    private fun borrarCiaPreferente(idUser: Long) {
        if (idUser <= 0L) return

        val prefs = getApplication<Application>()
            .getSharedPreferences("preferencias_app", android.content.Context.MODE_PRIVATE)

        prefs.edit()
            .remove("cia_preferente_usuario_$idUser")
            .apply()
    }


}
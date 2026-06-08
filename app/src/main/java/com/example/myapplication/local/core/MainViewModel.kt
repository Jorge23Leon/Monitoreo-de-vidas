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
private sealed class MainLoginServidorTemp {
    data class Exito(
        val datos: MainLoginTemp,
        val access: String?,
        val refresh: String
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

    var uiState by mutableStateOf(MainUiState())
        private set

    init {
        insertarDatosInicialesSeguros()
        cargarSesionGuardadaAlIniciar()
    }

    private fun actualizarEstado(transform: (MainUiState) -> MainUiState) {
        uiState = transform(uiState)
    }


    private fun mostrarMensaje(mensaje: String) {
        actualizarEstado { it.copy(mensaje = mensaje) }
    }
    private fun obtenerPrefsSesion() =
        getApplication<Application>().getSharedPreferences(
            "sesion_app",
            android.content.Context.MODE_PRIVATE
        )

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
                PantallaActual.LISTA_MONITOREOS
            }

            uiState.ciaSeleccionada != null -> {
                PantallaActual.FILTROS_MONITOREO
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

                            /*
                             * Restauración de CIA:
                             * - Supervisor: usa sus CIAS hijas asignadas directamente.
                             * - Admin/Gerente: usa CIA padre guardada y busca sus CIAS hijas.
                             * - Técnico/Invitado: no necesitan CIA guardada, entran directo a monitoreos.
                             */
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
                                    database.localCiaDao()
                                        .getCiasByParentCia(parentCiaGuardada.idParentCia)
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
                        cargarMonitoreosDirectoPorUsuario(sesion)
                    }

                    resultado.ciaGuardada != null -> {
                        abrirFiltrosMonitoreoConCia(
                            cia = resultado.ciaGuardada,
                            idProductorRestaurar = obtenerProductorSesion()
                        )

                        mostrarMensaje("Sesión restaurada: ${sesion.firstName} - CIA: ${resultado.ciaGuardada.nombre}")
                    }

                    else -> {
                        actualizarEstado {
                            it.copy(
                                pantallaActual = PantallaActual.SELECCION_CIA
                            )
                        }

                        mostrarMensaje("Sesión restaurada: ${sesion.firstName}. Selecciona una CIA")
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()

                borrarSesionGuardada()

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

    fun limpiarMensaje() {
        actualizarEstado { it.copy(mensaje = null) }
    }

    fun irA(pantalla: PantallaActual) {
        actualizarEstado { it.copy(pantallaActual = pantalla) }
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
                        pantallaActual = PantallaActual.LISTA_MONITOREOS
                    )
                }
            }

            uiState.ciaSeleccionada != null -> {
                actualizarEstado {
                    it.copy(
                        monitoreoSeleccionadoParaMapa = null,
                        monitoreoSeleccionadoParaReporte = null,
                        puntoSeleccionadoParaRegistro = null,
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

        val usuarioExistente = database.userDao().getUserByUsername(usernameServidor)
            ?: database.userDao().getUserByUsername(username)
            ?: database.userDao().getUserByEmail(emailServidor)

        if (usuarioExistente != null) {
            database.userDao().updateUser(
                usuarioExistente.copy(
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
    private suspend fun sincronizarCiasDesdeApi(idUserLocal: Long): String? {
        return when (val resultado = organizationRepository.obtenerCiasPadreEHijas()) {
            is ResultadoCiasApi.Error -> {
                resultado.mensaje
            }

            is ResultadoCiasApi.Exito -> {
                val ciasHijas = resultado.ciasHijas

                if (ciasHijas.isEmpty()) {
                    return "El servidor no regresó CIAS para este usuario"
                }

                val padresInsertados = mutableMapOf<String, Long>()

                ciasHijas.forEach { ciaHijaApi ->
                    val idParentLocal = padresInsertados[ciaHijaApi.parent.extId]
                        ?: guardarCiaPadreDesdeApi(ciaHijaApi.parent)

                    padresInsertados[ciaHijaApi.parent.extId] = idParentLocal

                    guardarCiaHijaDesdeApi(
                        ciaHijaApi = ciaHijaApi,
                        idParentCiaLocal = idParentLocal
                    )
                }

                val refs = padresInsertados.values.distinct().map { idParentCia ->
                    UserLocalParentCiaCrossRef(
                        idUser = idUserLocal,
                        idParentCia = idParentCia
                    )
                }

                database.userLocalParentCiaDao()
                    .asignarParentCiasAUsuario(refs)

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
                    val hijas = database.localCiaDao()
                        .getCiasByParentCia(parentCia.idParentCia)

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
                val resultadoServidor = withContext(Dispatchers.IO) {
                    when (val loginApi = authRepository.login(username, password)) {
                        is ResultadoLoginApi.Error -> {
                            MainLoginServidorTemp.Error(loginApi.mensaje)
                        }

                        is ResultadoLoginApi.Exito -> {
                            val refresh = loginApi.refresh

                            if (refresh.isNullOrBlank()) {
                                return@withContext MainLoginServidorTemp.Error(
                                    "El servidor validó el login, pero no regresó refresh token"
                                )
                            }

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

                            tokenStorage.guardarTokens(
                                access = access,
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

                            val errorCias = sincronizarCiasDesdeApi(usuarioLocal.idUser)

                            if (errorCias != null) {
                                return@withContext MainLoginServidorTemp.Error(errorCias)
                            }

                            val datosLogin = prepararLoginDesdeUsuarioLocal(usuarioLocal)
                                ?: return@withContext MainLoginServidorTemp.Error(
                                    "Login API correcto, pero no se pudo preparar la sesión local"
                                )

                            MainLoginServidorTemp.Exito(
                                datos = datosLogin,
                                access = access,
                                refresh = refresh
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

                        guardarSesionBasica(sesion.idUser)

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

                        if (sesion.esTecnico || sesion.esInvitado) {
                            mostrarMensaje("Login API correcto. Cargando tus monitoreos")
                            cargarMonitoreosDirectoPorUsuario(sesion)
                        } else {
                            when {
                                sesion.esSupervisor && ciasHijasUsuario.isEmpty() -> {
                                    mostrarMensaje("Login API correcto. No tienes CIAS hijas asignadas localmente")
                                }

                                resultado.ciaPreferente != null -> {
                                    mostrarMensaje("Login API correcto. CIA preferente cargada: ${resultado.ciaPreferente.nombre}")
                                }

                                sesion.esSupervisor -> {
                                    mostrarMensaje("Login API correcto. Selecciona una CIA hija")
                                }

                                parentCias.isEmpty() -> {
                                    mostrarMensaje("Login API correcto. No tienes CIAS padre cargadas localmente")
                                }

                                else -> {
                                    mostrarMensaje("Login API correcto - Rol local: ${sesion.roleName}")
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
                    database.localCiaDao()
                        .getCiasByParentCia(parentCia.idParentCia)
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

    fun onCiaChange(cia: LocalCiaEntity) {
        val idUser = uiState.idUsuarioActual

        if (idUser > 0L && uiState.seleccionarPreferente) {
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
        limpiarFiltros()

        actualizarEstado {
            it.copy(
                ciaSeleccionada = cia,
                pantallaActual = PantallaActual.FILTROS_MONITOREO
            )
        }

        cargarProductores(
            idLocalCia = cia.idLocalCia,
            idProductorRestaurar = idProductorRestaurar
        )
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

        /*
         * Guardamos siempre la CIA seleccionada para restaurarla
         * después si el usuario no cierra sesión.
         */
        guardarCiaSesion(
            idParentCia = estado.parentCiaSeleccionada?.idParentCia,
            idLocalCia = cia.idLocalCia
        )

        abrirFiltrosMonitoreoConCia(cia)

        val extraPreferente = if (estado.seleccionarPreferente) {
            " como preferente"
        } else {
            ""
        }

        mostrarMensaje("CIA seleccionada$extraPreferente: ${cia.nombre}")
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

                    val productorRestaurado = idProductorRestaurar
                        ?.takeIf { id -> id > 0L }
                        ?.let { id ->
                            productoresCia.firstOrNull { productor ->
                                productor.idLocalAgroUnit == id
                            }
                        }

                    val programasCia = database.localprogramDao()
                        .getProgramasByCia(idLocalCia)

                    val programasBase = if (productorRestaurado != null) {
                        programasCia.filter { programa ->
                            programa.idLocalAgroUnit == productorRestaurado.idLocalAgroUnit
                        }
                    } else {
                        programasCia
                    }

                    val idsRanchos = programasBase
                        .map { programa -> programa.idLocalRanch }
                        .distinct()

                    val idsParcelas = programasBase
                        .map { programa -> programa.idLocalPlot }
                        .distinct()

                    val ranchosFiltro = if (idsRanchos.isEmpty()) {
                        emptyList()
                    } else {
                        database.localRanchDao()
                            .getRanchosByIds(idsRanchos)
                            .sortedBy { rancho -> rancho.name }
                    }

                    val parcelasFiltro = if (idsParcelas.isEmpty()) {
                        emptyList()
                    } else {
                        database.localPlotDao()
                            .getParcelasByIds(idsParcelas)
                            .sortedBy { parcela ->
                                parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
                            }
                    }

                    MainCatalogosFiltrosTemp(
                        productores = productoresCia,
                        productorRestaurado = productorRestaurado,
                        ranchos = ranchosFiltro,
                        parcelas = parcelasFiltro
                    )
                }

                actualizarEstado {
                    it.copy(
                        productores = resultado.productores,
                        productorSeleccionado = resultado.productorRestaurado,

                        ranchos = resultado.ranchos,
                        ranchoSeleccionado = null,

                        parcelas = resultado.parcelas,
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
                    ranchoSeleccionado = null,
                    parcelaSeleccionada = null,
                    cicloSeleccionado = null,
                    ciclos = emptyList()
                )
            }

            cargarRanchosYParcelasParaFiltros(productor = null)
            cargarMonitoreosPorFiltrosProgresivos()
            return
        }

        guardarProductorSesion(productor.idLocalAgroUnit)

        actualizarEstado {
            it.copy(
                productorSeleccionado = productor,
                ranchoSeleccionado = null,
                parcelaSeleccionada = null,
                cicloSeleccionado = null,
                ciclos = emptyList()
            )
        }

        cargarRanchosYParcelasParaFiltros(productor = productor)
        cargarMonitoreosPorFiltrosProgresivos()
    }

    private fun cargarRanchosYParcelasParaFiltros(productor: LocalAgroUnitEntity?) {
        val cia = uiState.ciaSeleccionada ?: return
        val idProductorEsperado = productor?.idLocalAgroUnit

        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val programasCia = database.localprogramDao()
                        .getProgramasByCia(cia.idLocalCia)

                    val programasBase = if (idProductorEsperado != null) {
                        programasCia.filter { programa ->
                            programa.idLocalAgroUnit == idProductorEsperado
                        }
                    } else {
                        programasCia
                    }

                    val idsRanchos = programasBase
                        .map { programa -> programa.idLocalRanch }
                        .distinct()

                    val idsParcelas = programasBase
                        .map { programa -> programa.idLocalPlot }
                        .distinct()

                    val ranchosFiltro = if (idsRanchos.isEmpty()) {
                        emptyList()
                    } else {
                        database.localRanchDao()
                            .getRanchosByIds(idsRanchos)
                            .sortedBy { rancho -> rancho.name }
                    }

                    val parcelasFiltro = if (idsParcelas.isEmpty()) {
                        emptyList()
                    } else {
                        database.localPlotDao()
                            .getParcelasByIds(idsParcelas)
                            .sortedBy { parcela ->
                                parcela.code?.takeIf { it.isNotBlank() } ?: parcela.name
                            }
                    }

                    ranchosFiltro to parcelasFiltro
                }

                actualizarEstado { estadoActual ->
                    val productorActual = estadoActual.productorSeleccionado?.idLocalAgroUnit

                    if (estadoActual.ciaSeleccionada?.idLocalCia != cia.idLocalCia ||
                        productorActual != idProductorEsperado
                    ) {
                        estadoActual
                    } else {
                        estadoActual.copy(
                            ranchos = resultado.first,
                            parcelas = resultado.second
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
        guardarRanchoSesion(rancho?.idLocalRanch)

        if (rancho == null) {
            val productorActual = uiState.productorSeleccionado

            actualizarEstado {
                it.copy(
                    ranchoSeleccionado = null,
                    parcelaSeleccionada = null,
                    cicloSeleccionado = null,
                    ciclos = emptyList()
                )
            }

            cargarRanchosYParcelasParaFiltros(productor = productorActual)
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
        guardarParcelaSesion(parcela?.idLocalPlot)

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

                    val programasCia = database.localprogramDao()
                        .getProgramasByCia(cia.idLocalCia)

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
                                    monitoreoAsignadoAlUsuario ||
                                            parcelaAsignadaAlUsuario ||
                                            usuarioTieneCapturas
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
        if (esEstadoCanceladoVm(header.status)) {
            mostrarMensaje("Este monitoreo está cancelado. No se puede ver la información.")
            return
        }

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
                if (esEstadoCanceladoVm(headerActualizado.status)) {
                    mostrarMensaje("Este monitoreo está cancelado. No se puede abrir ni consultar información.")
                    return@launch
                }

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
            actualizarEstado {
                it.copy(
                    pantallaActual = PantallaActual.FILTROS_MONITOREO
                )
            }

            cargarMonitoreosPorFiltrosProgresivos()
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

        viewModelScope.launch {
            try {
                actualizarEstado { it.copy(cargando = true) }

                val headerActualizado = withContext(Dispatchers.IO) {
                    val fechaCancelacion = System.currentTimeMillis()
                    val notaCancelacion = "Cancelado: $motivoLimpio"

                    database.localphytomonitoringheaderDao()
                        .cancelarMonitoreoConMotivo(
                            idHeader = header.idHeader,
                            fechaCancelacion = fechaCancelacion,
                            motivoCancelacion = notaCancelacion
                        )

                    database.localphytomonitoringheaderDao()
                        .getHeaderById(header.idHeader)
                        ?: header.copy(
                            status = "Cancelado",
                            finishedAt = fechaCancelacion,
                            additionalNotes = notaCancelacion
                        )
                }

                actualizarHeaderEnLista(headerActualizado)
                mostrarMensaje("Monitoreo cancelado correctamente")
            } catch (e: Exception) {
                e.printStackTrace()
                mostrarMensaje("Error al cancelar monitoreo: ${e.message}")
            } finally {
                actualizarEstado { it.copy(cargando = false) }
            }
        }
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
                            status = "Pendiente",
                            startAt = fresco.startAt ?: System.currentTimeMillis(),
                            finishedAt = null,
                            additionalNotes = "PAUSADO"
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

                uiState = MainUiState(
                    pantallaActual = PantallaActual.LOGIN,
                    mensaje = mensajeLogout
                )
            } catch (e: Exception) {
                e.printStackTrace()

                borrarSesionGuardada()
                tokenStorage.limpiarTokens()

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
            PantallaActual.INFORMACION,
            PantallaActual.CONTACTO -> {
                irA(PantallaActual.LOGIN)
            }

            PantallaActual.SELECCION_PARENT_CIA,
            PantallaActual.SELECCION_CIA -> {
                mostrarMensaje("Presiona atrás otra vez para salir de la app")
            }

            PantallaActual.FILTROS_MONITOREO -> {
                mostrarMensaje("Para cambiar de CIA usa el menú: Cambiar de CIA")
            }

            PantallaActual.LISTA_MONITOREOS -> {
                val sesion = uiState.usuarioSesion
                when {
                    sesion != null && (sesion.esTecnico || sesion.esInvitado) -> {
                        mostrarMensaje("Presiona atrás otra vez para salir de la app")
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

    private fun esEstadoCanceladoVm(status: String): Boolean {
        return when (status.trim().lowercase(Locale.getDefault())) {
            "cancelado",
            "cancelled",
            "canceled" -> true

            else -> false
        }
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
                            password = PasswordHasher.generarHash("1234"),
                            idRole = rolAdmin.idRole
                        )
                    )
                }
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

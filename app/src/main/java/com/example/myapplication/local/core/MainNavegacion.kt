package com.example.myapplication.local.core

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.local.admin.agricola.AdminGestionAgricolaScreen
import com.example.myapplication.local.admin.catalogos.AdminCatalogosScreen
import com.example.myapplication.local.admin.home.AdminHomeScreen
import com.example.myapplication.local.admin.monitoreos.AdminMonitoreoScreen
import com.example.myapplication.local.auth.LoginScreen
import com.example.myapplication.local.auth.RegistroUsuarioScreen
import com.example.myapplication.local.cia.SeleccionCiaScreen
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.info.ContactoScreen
import com.example.myapplication.local.info.InformacionScreen
import com.example.myapplication.local.info.PerfilUsuarioScreen
import com.example.myapplication.local.monitoreo.filtros.MonitoreoFiltrosScreen
import com.example.myapplication.local.monitoreo.lista.MonitoreoListaScreen
import com.example.myapplication.local.monitoreo.mapa.MonitoreoMapaScreen
import com.example.myapplication.local.monitoreo.registro.RegistroPuntoMonitoreoScreen
import com.example.myapplication.local.monitoreo.reporte.ReporteMonitoreoScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


private enum class AccionMenuTrabajo {
    PERFIL,
    MONITOREOS,
    PANEL_ADMIN,
    CAMBIAR_CIA,
    CERRAR_SESION
}

private fun textoAccionMenuTrabajo(accion: AccionMenuTrabajo): String {
    return when (accion) {
        AccionMenuTrabajo.PERFIL -> "Perfil"
        AccionMenuTrabajo.MONITOREOS -> "Monitoreos"
        AccionMenuTrabajo.PANEL_ADMIN -> "Panel administrador"
        AccionMenuTrabajo.CAMBIAR_CIA -> "Cambiar de CIA"
        AccionMenuTrabajo.CERRAR_SESION -> "Cerrar sesión"
    }
}


@Composable
fun MainNavegacion(
    database: AppDatabase,
    mainViewModel: MainViewModel,
    uiState: MainUiState
) {
    val context = LocalContext.current

    var accionMenuPendiente by remember {
        mutableStateOf<AccionMenuTrabajo?>(null)
    }

    fun estaEnTrabajoDeMonitoreo(): Boolean {
        return uiState.pantallaActual == PantallaActual.MAPA_MONITOREO ||
                uiState.pantallaActual == PantallaActual.REGISTRO_PUNTO_MONITOREO
    }

    fun ejecutarAccionMenu(accion: AccionMenuTrabajo) {
        when (accion) {
            AccionMenuTrabajo.PERFIL -> {
                mainViewModel.abrirPerfilDesdePantallaActual()
            }

            AccionMenuTrabajo.MONITOREOS -> {
                mainViewModel.abrirMonitoreosDesdeEncabezado()
            }

            AccionMenuTrabajo.PANEL_ADMIN -> {
                mainViewModel.abrirPanelAdministrador()
            }

            AccionMenuTrabajo.CAMBIAR_CIA -> {
                mainViewModel.cambiarCiaDesdeMenu()
            }

            AccionMenuTrabajo.CERRAR_SESION -> {
                mainViewModel.cerrarSesion()
            }
        }
    }

    fun solicitarAccionMenu(accion: AccionMenuTrabajo) {
        if (estaEnTrabajoDeMonitoreo() && uiState.monitoreoSeleccionadoParaMapa != null) {
            accionMenuPendiente = accion
        } else {
            ejecutarAccionMenu(accion)
        }
    }

    val cerrarSesionClick = {
        solicitarAccionMenu(AccionMenuTrabajo.CERRAR_SESION)
    }

    val cambiarCiaClick = {
        solicitarAccionMenu(AccionMenuTrabajo.CAMBIAR_CIA)
    }

    accionMenuPendiente?.let { accion ->
        AlertDialog(
            onDismissRequest = {
                accionMenuPendiente = null
            },
            title = {
                Text("Salir del monitoreo")
            },
            text = {
                Text(
                    text = "Vas a abrir ${textoAccionMenuTrabajo(accion)}. " +
                            "El monitoreo actual se pausará automáticamente para que puedas regresar después. " +
                            "Si tienes datos escritos en este punto y todavía no los guardas, se perderán."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val accionConfirmada = accion
                        accionMenuPendiente = null

                        mainViewModel.pausarMonitoreoActualParaNavegar {
                            ejecutarAccionMenu(accionConfirmada)
                        }
                    }
                ) {
                    Text("Pausar y salir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        accionMenuPendiente = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    when (uiState.pantallaActual) {
        PantallaActual.CARGANDO_SESION -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()

                Text(
                    text = "Cargando sesión..."
                )
            }
        }

        PantallaActual.LOGIN -> {
            LoginScreen(
                onLoginClick = { usernameInput, passwordInput ->
                    mainViewModel.onLoginClick(
                        usernameInput = usernameInput,
                        passwordInput = passwordInput
                    )
                },
                onRegisterClick = {
                    mainViewModel.irA(PantallaActual.REGISTRO)
                },
                onForgotPasswordClick = {
                    Toast.makeText(
                        context,
                        "Aquí irá la recuperación de contraseña",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onInformationClick = {
                    mainViewModel.irA(PantallaActual.INFORMACION)
                },
                onContactClick = {
                    mainViewModel.irA(PantallaActual.CONTACTO)
                }
            )
        }

        PantallaActual.REGISTRO -> {
            RegistroUsuarioScreen(
                onRegisterClick = { firstNameInput, lastnameInput, usernameInput, emailInput, passwordInput, confirmPasswordInput, roleInput ->
                    mainViewModel.registrarUsuario(
                        firstNameInput = firstNameInput,
                        lastnameInput = lastnameInput,
                        usernameInput = usernameInput,
                        emailInput = emailInput,
                        passwordInput = passwordInput,
                        confirmPasswordInput = confirmPasswordInput,
                        roleInput = roleInput
                    )
                },
                onBackToLoginClick = {
                    mainViewModel.irA(PantallaActual.LOGIN)
                }
            )
        }

        PantallaActual.INFORMACION -> {
            InformacionScreen(
                onCloseClick = {
                    mainViewModel.irA(PantallaActual.LOGIN)
                }
            )
        }

        PantallaActual.CONTACTO -> {
            ContactoScreen(
                onCloseClick = {
                    mainViewModel.irA(PantallaActual.LOGIN)
                }
            )
        }
        PantallaActual.SELECCION_PARENT_CIA,
        PantallaActual.SELECCION_CIA -> {
            SeleccionCiaScreen(
                nombreUsuario = uiState.nombreUsuarioActual,
                rolUsuario = uiState.rolUsuarioActual,

                parentCias = uiState.parentCiasUsuario,
                parentCiaSeleccionada = uiState.parentCiaSeleccionada,
                onParentCiaChange = { parentCia ->
                    mainViewModel.onParentCiaChange(parentCia)
                },

                cias = uiState.ciasUsuario,
                ciaSeleccionada = uiState.ciaSeleccionada,
                seleccionarPreferente = uiState.seleccionarPreferente,

                onPreferenteChange = { seleccionado ->
                    mainViewModel.onPreferenteChange(seleccionado)
                },

                onCiaChange = { cia ->
                    mainViewModel.onCiaChange(cia)
                },

                onSeleccionarClick = {
                    mainViewModel.seleccionarCiaActual()
                },

                onCerrarSesionClick = cerrarSesionClick,

                onPerfilClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                },

                onMonitoreosClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                },

                onAdminClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                }
            )
        }

        PantallaActual.FILTROS_MONITOREO -> {
            val textoCiaSeleccionada = uiState.ciaSeleccionada?.let { cia ->
                val parentCia = uiState.parentCiaSeleccionada
                    ?: uiState.parentCiasUsuario.firstOrNull { parent ->
                        parent.idParentCia == cia.idParentCia
                    }

                if (parentCia != null) {
                    "${parentCia.name} / ${cia.nombre}"
                } else {
                    cia.nombre
                }
            } ?: "Sin CIA"

            MonitoreoFiltrosScreen(
                nombreUsuario = uiState.nombreUsuarioActual,
                rolUsuario = uiState.rolUsuarioActual,
                nombreCia = textoCiaSeleccionada,

                productores = uiState.productores,
                ranchos = uiState.ranchos,
                parcelas = uiState.parcelas,

                productorSeleccionado = uiState.productorSeleccionado,
                ranchoSeleccionado = uiState.ranchoSeleccionado,
                parcelaSeleccionada = uiState.parcelaSeleccionada,

                monitoreos = uiState.monitoreosEncontrados,
                productoresResultado = uiState.productoresResultado,
                ranchosResultado = uiState.ranchosResultado,
                parcelasResultado = uiState.parcelasResultado,
                programasResultado = uiState.programasResultado,
                cultivosResultado = uiState.cultivosResultado,

                onProductorChange = { productor ->
                    mainViewModel.onProductorChange(productor)
                },

                onRanchoChange = { rancho ->
                    mainViewModel.onRanchoChange(rancho)
                },

                onParcelaChange = { parcela ->
                    mainViewModel.onParcelaChange(parcela)
                },

                onAbrirMapaClick = { header ->
                    mainViewModel.abrirMapa(header)
                },

                onAbrirReporteClick = { header ->
                    mainViewModel.abrirReporte(header)
                },
                onCancelarMonitoreoClick = { header, motivo ->
                    mainViewModel.cancelarMonitoreoConfirmado(
                        header = header,
                        motivo = motivo
                    )
                },

                onPerfilClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                },

                onMonitoreosClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                },

                onAdminClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                },
                onCambiarCiaClick = cambiarCiaClick,


                onCerrarSesionClick = cerrarSesionClick
            )
        }

        PantallaActual.LISTA_MONITOREOS -> {
            MonitoreoListaScreen(
                nombreUsuario = uiState.nombreUsuarioActual,
                rolUsuario = uiState.rolUsuarioActual,
                busquedaFueConSaltoFiltros = uiState.busquedaFueConSaltoFiltros,

                monitoreos = uiState.monitoreosEncontrados,
                productores = uiState.productoresResultado,
                ranchos = uiState.ranchosResultado,
                parcelas = uiState.parcelasResultado,
                programas = uiState.programasResultado,
                cultivos = uiState.cultivosResultado,

                onAbrirMapaClick = { header ->
                    val rolNormalizado = normalizarRolVm(uiState.rolUsuarioActual)

                    if (rolNormalizado == "gerente" || rolNormalizado == "supervisor") {
                        mainViewModel.abrirReporte(header)
                    } else {
                        mainViewModel.abrirMapa(header)
                    }
                },

                onAbrirReporteClick = { header ->
                    mainViewModel.abrirReporte(header)
                },

                onCancelarMonitoreoClick = { header, motivo ->
                    mainViewModel.cancelarMonitoreoConfirmado(
                        header = header,
                        motivo = motivo
                    )
                },

                onPerfilClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                },

                onMonitoreosClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                },

                onAdminClick = {
                    solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                },
                onCambiarCiaClick = cambiarCiaClick,


                onCerrarSesionClick = cerrarSesionClick
            )
        }

        PantallaActual.MAPA_MONITOREO -> {
            val header = uiState.monitoreoSeleccionadoParaMapa

            if (header == null) {
                LaunchedEffect(Unit) {
                    mainViewModel.volverAConsultaMonitoreos()
                }
            }else {
                val nombreMonitoreo = uiState.programasResultado
                    .firstOrNull { programa ->
                        programa.idProgram == header.idProgram
                    }
                    ?.cycle ?: header.cycle.ifBlank { "Monitoreo ${header.idHeader}" }
                MonitoreoMapaScreen(
                    database = database,
                    header = header,
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,
                    nombreMonitoreo = nombreMonitoreo,

                    onPuntoValidoClick = { idTargetPoint ->
                        mainViewModel.abrirPuntoParaRegistro(idTargetPoint)
                    },

                    onMonitoreoActualizado = { nuevoEstado ->
                        mainViewModel.onMonitoreoActualizado(nuevoEstado)
                    },

                    onBackClick = {
                        mainViewModel.volverAConsultaMonitoreos()
                    },

                    onPerfilClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },

                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }

        PantallaActual.REGISTRO_PUNTO_MONITOREO -> {
            val header = uiState.monitoreoSeleccionadoParaMapa
            val punto = uiState.puntoSeleccionadoParaRegistro

            if (header == null || punto == null) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(PantallaActual.MAPA_MONITOREO)
                }
            } else {
                RegistroPuntoMonitoreoScreen(
                    database = database,
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,
                    idUsuarioActual = uiState.idUsuarioActual,
                    header = header,
                    punto = punto,

                    onCancelar = {
                        mainViewModel.cancelarRegistroPunto()
                    },

                    onGuardado = {
                        mainViewModel.onPuntoGuardado()
                    },

                    onPerfilClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },
                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }

        PantallaActual.REPORTE_MONITOREO -> {
            val header = uiState.monitoreoSeleccionadoParaReporte

            if (header == null) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(PantallaActual.LISTA_MONITOREOS)
                }
            } else {
                val programa = uiState.programasResultado.firstOrNull { programa ->
                    programa.idProgram == header.idProgram
                }

                val parcela = uiState.parcelasResultado.firstOrNull { parcela ->
                    parcela.idLocalPlot == header.idLocalPlot
                }

                val rancho = parcela?.let { parcelaEncontrada ->
                    uiState.ranchosResultado.firstOrNull { rancho ->
                        rancho.idLocalRanch == parcelaEncontrada.idLocalRanch
                    }
                }

                val productor = programa?.let { programaEncontrado ->
                    uiState.productoresResultado.firstOrNull { productor ->
                        productor.idLocalAgroUnit == programaEncontrado.idLocalAgroUnit
                    }
                }

                ReporteMonitoreoScreen(
                    database = database,
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,
                    nombreCia = uiState.ciaSeleccionada?.nombre ?: "-",
                    header = header,
                    programa = programa,
                    productor = productor,
                    rancho = rancho,
                    parcela = parcela,

                    onBackClick = {
                        mainViewModel.volverAConsultaMonitoreos()
                    },

                    onPerfilClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },
                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }

        PantallaActual.ADMIN_HOME -> {
            if (!puedeVerPanelTrabajoVm(uiState.rolUsuarioActual)) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(
                        when {
                            uiState.ciaSeleccionada != null -> PantallaActual.FILTROS_MONITOREO
                            uiState.parentCiaSeleccionada != null -> PantallaActual.SELECCION_CIA
                            else -> PantallaActual.SELECCION_CIA
                        }
                    )
                }
            } else {
                AdminHomeScreen(
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,
                    nombreCia = uiState.ciaSeleccionada?.nombre ?: "Sin CIA",
                    puedeCrearMonitoreos = puedeCrearMonitoreosVm(uiState.rolUsuarioActual),
                    puedeGestionCatalogos = puedeGestionCatalogosVm(uiState.rolUsuarioActual),
                    puedeGestionAgricola = puedeGestionAgricolaVm(uiState.rolUsuarioActual),

                    onMonitoreosAdminClick = {
                        mainViewModel.irA(PantallaActual.ADMIN_MONITOREOS)
                    },

                    onCatalogosClick = {
                        mainViewModel.irA(PantallaActual.ADMIN_CATALOGOS)
                    },

                    onGestionAgricolaClick = {
                        mainViewModel.irA(PantallaActual.ADMIN_GESTION_AGRICOLA)
                    },

                    onPerfilClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },
                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }

        PantallaActual.ADMIN_MONITOREOS -> {
            if (!puedeCrearMonitoreosVm(uiState.rolUsuarioActual)) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(PantallaActual.FILTROS_MONITOREO)
                }
            } else {
                AdminMonitoreoScreen(
                    database = database,
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,
                    idLocalCia = uiState.ciaSeleccionada?.idLocalCia,
                    nombreCia = uiState.ciaSeleccionada?.nombre ?: "Sin CIA",
                    idUsuarioActual = uiState.idUsuarioActual,

                    onBackClick = {
                        mainViewModel.irA(PantallaActual.ADMIN_HOME)
                    },

                    onPerfilClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },

                    onMensaje = { mensaje ->
                        Toast.makeText(
                            context,
                            mensaje,
                            Toast.LENGTH_SHORT
                        ).show()
                    },

                    onMonitoreoCreado = {
                        mainViewModel.onMonitoreoCreadoDesdeAdmin()
                    },
                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }

        PantallaActual.ADMIN_CATALOGOS -> {
            if (!puedeGestionCatalogosVm(uiState.rolUsuarioActual)) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(PantallaActual.FILTROS_MONITOREO)
                }
            } else {
                AdminCatalogosScreen(
                    database = database,
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,
                    nombreCia = uiState.ciaSeleccionada?.nombre ?: "Sin CIA",

                    onBackClick = {
                        mainViewModel.irA(PantallaActual.ADMIN_HOME)
                    },

                    onPerfilClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },

                    onMensaje = { mensaje ->
                        Toast.makeText(
                            context,
                            mensaje,
                            Toast.LENGTH_SHORT
                        ).show()
                    },

                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }

        PantallaActual.ADMIN_GESTION_AGRICOLA -> {
            if (!puedeGestionAgricolaVm(uiState.rolUsuarioActual)) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(PantallaActual.FILTROS_MONITOREO)
                }
            } else {
                AdminGestionAgricolaScreen(
                    database = database,
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,
                    idLocalCia = uiState.ciaSeleccionada?.idLocalCia,
                    nombreCia = uiState.ciaSeleccionada?.nombre ?: "Sin CIA",

                    onBackClick = {
                        mainViewModel.irA(PantallaActual.ADMIN_HOME)
                    },

                    onPerfilClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PERFIL)
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },

                    onMensaje = { mensaje ->
                        Toast.makeText(
                            context,
                            mensaje,
                            Toast.LENGTH_SHORT
                        ).show()
                    },

                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }


        PantallaActual.PERFIL_USUARIO -> {
            if (uiState.idUsuarioActual == 0L) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(PantallaActual.LOGIN)
                }
            } else {
                PerfilUsuarioScreen(
                    database = database,
                    idUser = uiState.idUsuarioActual,
                    nombreUsuario = uiState.nombreUsuarioActual,
                    rolUsuario = uiState.rolUsuarioActual,

                    onBackClick = {
                        mainViewModel.volverDesdePerfil()
                    },

                    onPerfilActualizado = { usuario ->
                        mainViewModel.onPerfilActualizado(usuario)
                    },

                    onPerfilClick = {
                        // Ya está en perfil; no se modifica pantallaAntesPerfil.
                    },

                    onMonitoreosClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.MONITOREOS)
                    },

                    onAdminClick = {
                        solicitarAccionMenu(AccionMenuTrabajo.PANEL_ADMIN)
                    },

                    onCambiarCiaClick = cambiarCiaClick,

                    onCerrarSesionClick = cerrarSesionClick
                )
            }
        }
    }
}

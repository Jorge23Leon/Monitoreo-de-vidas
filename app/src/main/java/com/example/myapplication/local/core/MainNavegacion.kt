package com.example.myapplication.local.core

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun MainNavegacion(
    database: AppDatabase,
    mainViewModel: MainViewModel,
    uiState: MainUiState
) {
    val context = LocalContext.current

    when (uiState.pantallaActual) {

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

                onCerrarSesionClick = {
                    mainViewModel.cerrarSesion()
                },

                onPerfilClick = {
                    mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                },

                onMonitoreosClick = {
                    mainViewModel.abrirMonitoreosDesdeEncabezado()
                },

                onAdminClick = {
                    mainViewModel.abrirPanelAdministrador()
                }
            )
        }

        PantallaActual.FILTROS_MONITOREO -> {
            MonitoreoFiltrosScreen(
                nombreUsuario = uiState.nombreUsuarioActual,
                rolUsuario = uiState.rolUsuarioActual,
                nombreCia = uiState.ciaSeleccionada?.nombre ?: "Sin CIA",

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

                onPerfilClick = {
                    mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                },

                onMonitoreosClick = {
                    mainViewModel.abrirMonitoreosDesdeEncabezado()
                },

                onAdminClick = {
                    mainViewModel.abrirPanelAdministrador()
                }
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

                onPerfilClick = {
                    mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                },

                onMonitoreosClick = {
                    mainViewModel.abrirMonitoreosDesdeEncabezado()
                },

                onAdminClick = {
                    mainViewModel.abrirPanelAdministrador()
                }
            )
        }

        PantallaActual.MAPA_MONITOREO -> {
            val header = uiState.monitoreoSeleccionadoParaMapa

            if (header == null) {
                LaunchedEffect(Unit) {
                    mainViewModel.irA(PantallaActual.LISTA_MONITOREOS)
                }
            } else {
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

                    onPerfilClick = {
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
                    }
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
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
                    }
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
                        mainViewModel.irA(PantallaActual.LISTA_MONITOREOS)
                    },

                    onPerfilClick = {
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
                    }
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
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
                    }
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
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
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
                    }
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
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
                    },

                    onMensaje = { mensaje ->
                        Toast.makeText(
                            context,
                            mensaje,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
                    },

                    onMensaje = { mensaje ->
                        Toast.makeText(
                            context,
                            mensaje,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
                        mainViewModel.irA(
                            when {
                                uiState.ciaSeleccionada != null -> PantallaActual.FILTROS_MONITOREO
                                uiState.parentCiaSeleccionada != null -> PantallaActual.SELECCION_CIA
                                else -> PantallaActual.LOGIN
                            }
                        )
                    },

                    onPerfilActualizado = { usuario ->
                        mainViewModel.onPerfilActualizado(usuario)
                    },

                    onPerfilClick = {
                        mainViewModel.irA(PantallaActual.PERFIL_USUARIO)
                    },

                    onMonitoreosClick = {
                        mainViewModel.abrirMonitoreosDesdeEncabezado()
                    },

                    onAdminClick = {
                        mainViewModel.abrirPanelAdministrador()
                    }
                )
            }
        }
    }
}

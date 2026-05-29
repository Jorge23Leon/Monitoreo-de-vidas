package com.example.myapplication.local.core

import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import com.example.myapplication.local.models.UsuarioSesion

data class MainUiState(
    val pantallaActual: PantallaActual = PantallaActual.CARGANDO_SESION,
    val cargando: Boolean = false,

    val usuarioSesion: UsuarioSesion? = null,

    val idUsuarioActual: Long = 0L,
    val nombreUsuarioActual: String = "",
    val rolUsuarioActual: String = "",
    val nivelRolUsuarioActual: Int = 0,

    val busquedaFueConSaltoFiltros: Boolean = false,

    val parentCiasUsuario: List<LocalParentCiaEntity> = emptyList(),
    val parentCiaSeleccionada: LocalParentCiaEntity? = null,

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
    val finalizadosChecked: Boolean = true,
    val vigentesChecked: Boolean = true,
    val canceladosChecked: Boolean = false,

    val monitoreosEncontrados: List<LocalPhytomonitoringHeaderEntity> = emptyList(),
    val productoresResultado: List<LocalAgroUnitEntity> = emptyList(),
    val ranchosResultado: List<LocalRanchEntity> = emptyList(),
    val parcelasResultado: List<LocalPlotEntity> = emptyList(),
    val programasResultado: List<LocalProgramEntity> = emptyList(),
    val cultivosResultado: List<LocalCropCatalogEntity> = emptyList(),

    val monitoreoSeleccionadoParaMapa: LocalPhytomonitoringHeaderEntity? = null,
    val monitoreoSeleccionadoParaReporte: LocalPhytomonitoringHeaderEntity? = null,
    val puntoSeleccionadoParaRegistro: LocalPhytomonitoringTargetPointEntity? = null,

    val mensaje: String? = null
)

internal data class MainResultadoMonitoreoTemp(
    val headers: List<LocalPhytomonitoringHeaderEntity>,
    val productores: List<LocalAgroUnitEntity>,
    val ranchos: List<LocalRanchEntity>,
    val parcelas: List<LocalPlotEntity>,
    val programas: List<LocalProgramEntity>,
    val cultivos: List<LocalCropCatalogEntity>
)

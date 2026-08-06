package com.example.myapplication.local.aspersion.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.local.aspersion.ui.AspersionLayer
import com.example.myapplication.local.aspersion.ui.AspersionLegendItem
import com.example.myapplication.local.entities.LocalAspersionPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.monitoreo.mapa.MapaMonitoreoWebViewSeguro
import com.example.myapplication.local.monitoreo.mapa.hayInternet

@Composable
internal fun AspersionMapCanvas(
    points: List<LocalAspersionPointEntity>,
    vertices: List<LocalPlotVertexEntity>,
    layer: AspersionLayer,
    legendItems: List<AspersionLegendItem>,
    plotName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var internetAvailable by remember {
        mutableStateOf(hayInternet(context))
    }

    val html = remember(
        points,
        vertices,
        layer,
        legendItems,
        plotName,
        internetAvailable
    ) {
        createAspersionMapHtml(
            points = points,
            vertices = vertices,
            layer = layer,
            legendItems = legendItems,
            plotName = plotName,
            internetAvailable = internetAvailable
        )
    }

    MapaMonitoreoWebViewSeguro(
        modifier = modifier,
        htmlMapa = html,
        ubicacionUsuario = null,
        precisionGpsMetros = null,
        puntoLibreSeleccionado = null,
        internetDisponible = internetAvailable,
        onInternetDisponibleChange = { available ->
            internetAvailable = available
        },
        onPuntoLibreSeleccionado = { _, _ -> }
    )
}

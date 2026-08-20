package com.example.myapplication.local.ndvi.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.local.entities.LocalNdviPointEntity
import com.example.myapplication.local.monitoreo.mapa.MapaMonitoreoWebViewSeguro
import com.example.myapplication.local.monitoreo.mapa.hayInternet
import com.example.myapplication.local.ndvi.model.NdviIndex


/**
 * Mapa NDVI calculado localmente.
 *
 * Los puntos vienen de Room y el HTML construye una superficie
 * interpolada sin esperar el endpoint /contours/.
 */
@Composable
internal fun NdviMapCanvas(
    points: List<LocalNdviPointEntity>,
    index: NdviIndex,
    variableConfigJson: String?,
    modifier: Modifier = Modifier
) {

    val context =
        LocalContext.current

    var internetAvailable by
    remember {
        mutableStateOf(
            hayInternet(context)
        )
    }

    val html =
        remember(
            points,
            index,
            variableConfigJson,
            internetAvailable
        ) {

            createNdviMapHtml(
                points = points,
                index = index,
                internetAvailable = internetAvailable,
                variableConfigJson = variableConfigJson
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
        onPuntoLibreSeleccionado = { _, _ ->
            // NDVI es únicamente consulta.
        }
    )
}

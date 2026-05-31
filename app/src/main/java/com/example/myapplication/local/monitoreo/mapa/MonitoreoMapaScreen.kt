package com.example.myapplication.local.monitoreo.mapa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MonitoreoMapaScreen(
    database: AppDatabase,
    header: LocalPhytomonitoringHeaderEntity,
    nombreUsuario: String,
    rolUsuario: String = "",
    nombreMonitoreo: String,
    onPuntoValidoClick: (Long) -> Unit,
    onMonitoreoActualizado: (String) -> Unit,
    onPerfilClick: () -> Unit = {},
    onCerrarSesionClick: () -> Unit,
    onMonitoreosClick: () -> Unit = {},
    onCambiarCiaClick: (() -> Unit)? = null,
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val onPuntoValidoActual by rememberUpdatedState(onPuntoValidoClick)
    val onMonitoreoActualizadoActual by rememberUpdatedState(onMonitoreoActualizado)

    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }
    var puntos by remember { mutableStateOf<List<LocalPhytomonitoringTargetPointEntity>>(emptyList()) }
    var checkpoints by remember { mutableStateOf<List<LocalPhytomonitoringCheckpointEntity>>(emptyList()) }
    var ubicacionUsuario by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var internetDisponible by remember { mutableStateOf(hayInternet(context)) }
    var cargando by remember { mutableStateOf(true) }
    var finalizandoMonitoreo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val ubicacionActualizada by rememberUpdatedState(ubicacionUsuario)
    var tienePermisoUbicacion by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permisoUbicacionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        tienePermisoUbicacion = granted
        if (granted) ubicacionUsuario = obtenerUltimaUbicacion(context)
    }

    LaunchedEffect(header.idHeader) {
        cargando = true
        error = null
        internetDisponible = hayInternet(context)

        try {
            val resultado = withContext(Dispatchers.IO) {
                database.localphytomonitoringheaderDao().iniciarMonitoreoSiEstaPendiente(
                    idHeader = header.idHeader,
                    now = System.currentTimeMillis()
                )

                Triple(
                    database.LocalPlotVertexDao().getVerticesByPlot(header.idLocalPlot),
                    database.LocalPhytomonitoringTargetPointDao().getTargetPointsByHeader(header.idHeader),
                    database.localphytomonitoringcheckpointDao().getCheckpointsByHeader(header.idHeader)
                )
            }

            vertices = resultado.first
            puntos = resultado.second
            checkpoints = resultado.third
        } catch (e: Exception) {
            error = "Error al cargar mapa: ${e.message}"
        } finally {
            cargando = false
        }
    }

    LaunchedEffect(Unit) {
        if (!tienePermisoUbicacion) {
            permisoUbicacionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            ubicacionUsuario = obtenerUltimaUbicacion(context)
        }
    }

    DisposableEffect(tienePermisoUbicacion) {
        if (!tienePermisoUbicacion) {
            onDispose { }
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    ubicacionUsuario = Pair(location.latitude, location.longitude)
                }
            }

            try {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 2000L, 1f, listener, Looper.getMainLooper())
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onDispose {
                try {
                    locationManager.removeUpdates(listener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val capturasPuntosUnicos = remember(checkpoints, puntos, header.idHeader) {
        val idsPuntosActuales = puntos.map { it.idTargetPoint }.toSet()
        checkpoints
            .filter { it.idHeader == header.idHeader && it.idTargetPoint in idsPuntosActuales }
            .map { it.idTargetPoint }
            .distinct()
            .size
    }

    val htmlMapa = remember(vertices, puntos, checkpoints, nombreMonitoreo, internetDisponible) {
        crearHtmlMapaMonitoreo(
            nombreMonitoreo = nombreMonitoreo,
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            ubicacionInicial = ubicacionActualizada,
            internetDisponible = internetDisponible
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick,
                onCambiarCiaClick = onCambiarCiaClick,
                onCerrarSesionClick = onCerrarSesionClick
            )

            BarraMapaMonitoreo(nombreMonitoreo = nombreMonitoreo)

            when {
                cargando -> Text(text = "Cargando mapa...", modifier = Modifier.padding(16.dp), color = Color.DarkGray)
                error != null -> Text(text = error ?: "Error desconocido", modifier = Modifier.padding(16.dp), color = Color.Red)
                else -> {
                    ResumenMapaMonitoreo(
                        totalVertices = vertices.size,
                        totalPuntos = puntos.size,
                        totalCapturas = capturasPuntosUnicos
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BotonTerminarMonitoreo(
                        database = database,
                        header = header,
                        totalPuntos = puntos.size,
                        puntosCapturados = capturasPuntosUnicos,
                        finalizandoMonitoreo = finalizandoMonitoreo,
                        onFinalizandoChange = { finalizandoMonitoreo = it },
                        onMonitoreoActualizado = { estado -> onMonitoreoActualizadoActual(estado) }
                    )

                    MapaMonitoreoWebViewSeguro(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                        htmlMapa = htmlMapa,
                        ubicacionUsuario = ubicacionUsuario,
                        internetDisponible = internetDisponible,
                        onInternetDisponibleChange = { internetDisponible = it },
                        onPuntoValidoClick = { onPuntoValidoActual(it) }
                    )
                }
            }
        }
    }
}

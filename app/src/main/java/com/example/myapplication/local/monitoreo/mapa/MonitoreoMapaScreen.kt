package com.example.myapplication.local.monitoreo.mapa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onBackClick: () -> Unit = {},
    onPerfilClick: () -> Unit = {},
    onCerrarSesionClick: () -> Unit,
    onMonitoreosClick: () -> Unit = {},
    onCambiarCiaClick: (() -> Unit)? = null,
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val onPuntoValidoActual by rememberUpdatedState(onPuntoValidoClick)
    val onMonitoreoActualizadoActual by rememberUpdatedState(onMonitoreoActualizado)

    var headerActual by remember(header.idHeader) {
        mutableStateOf(header)
    }

    var vertices by remember {
        mutableStateOf<List<LocalPlotVertexEntity>>(emptyList())
    }

    var puntos by remember {
        mutableStateOf<List<LocalPhytomonitoringTargetPointEntity>>(emptyList())
    }

    var checkpoints by remember {
        mutableStateOf<List<LocalPhytomonitoringCheckpointEntity>>(emptyList())
    }

    var ubicacionUsuario by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }

    var internetDisponible by remember {
        mutableStateOf(hayInternet(context))
    }

    var cargando by remember {
        mutableStateOf(true)
    }

    var finalizandoMonitoreo by remember {
        mutableStateOf(false)
    }

    var accionDialogoMapa by remember {
        mutableStateOf<AccionDialogoMapa?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var ahoraMs by remember {
        mutableStateOf(System.currentTimeMillis())
    }

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

        if (granted) {
            ubicacionUsuario = obtenerUltimaUbicacion(context)
        }
    }

    suspend fun cargarDatosMapa() {
        cargando = true
        error = null
        internetDisponible = hayInternet(context)

        try {
            val resultado = withContext(Dispatchers.IO) {
                val headerFresco = database.localphytomonitoringheaderDao()
                    .getHeaderById(header.idHeader) ?: header

                val estaPausado = esMonitoreoPausadoMapa(
                    status = headerFresco.status,
                    additionalNotes = headerFresco.additionalNotes
                )

                val estaCerrado = esEstadoCerradoMapa(headerFresco.status)

                if (!estaPausado && !estaCerrado) {
                    database.localphytomonitoringheaderDao()
                        .iniciarMonitoreoSiEstaPendiente(
                            idHeader = headerFresco.idHeader,
                            now = System.currentTimeMillis()
                        )
                }

                val headerFinal = database.localphytomonitoringheaderDao()
                    .getHeaderById(header.idHeader) ?: headerFresco

                MapaCargaResultado(
                    header = headerFinal,
                    vertices = database.LocalPlotVertexDao()
                        .getVerticesByPlot(header.idLocalPlot),
                    puntos = database.LocalPhytomonitoringTargetPointDao()
                        .getTargetPointsByHeader(header.idHeader),
                    checkpoints = database.localphytomonitoringcheckpointDao()
                        .getCheckpointsByHeader(header.idHeader)
                )
            }

            headerActual = resultado.header
            vertices = resultado.vertices
            puntos = resultado.puntos
            checkpoints = resultado.checkpoints
        } catch (e: Exception) {
            error = "Error al cargar mapa: ${e.message}"
        } finally {
            cargando = false
        }
    }

    fun pausarMonitoreo() {
        if (finalizandoMonitoreo) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val actualizado = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    val inicioReal = fresco.startAt ?: System.currentTimeMillis()

                    val nuevoHeader = fresco.copy(
                        status = "Pendiente",
                        startAt = inicioReal,
                        finishedAt = null,
                        additionalNotes = "PAUSADO"
                    )

                    database.localphytomonitoringheaderDao()
                        .updateHeader(nuevoHeader)

                    nuevoHeader
                }

                headerActual = actualizado
                accionDialogoMapa = null

                Toast.makeText(
                    context,
                    "Monitoreo pausado. El tiempo sigue corriendo desde el inicio.",
                    Toast.LENGTH_SHORT
                ).show()

                onMonitoreoActualizadoActual("Pausado")
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al pausar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    fun continuarMonitoreo() {
        if (finalizandoMonitoreo) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val actualizado = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    val nuevoHeader = fresco.copy(
                        status = "En proceso",
                        startAt = fresco.startAt ?: System.currentTimeMillis(),
                        finishedAt = null,
                        additionalNotes = ""
                    )

                    database.localphytomonitoringheaderDao()
                        .updateHeader(nuevoHeader)

                    nuevoHeader
                }

                headerActual = actualizado

                Toast.makeText(
                    context,
                    "Monitoreo reanudado",
                    Toast.LENGTH_SHORT
                ).show()

                cargarDatosMapa()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al continuar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    fun terminarMonitoreo() {
        if (finalizandoMonitoreo) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val terminadoMs = System.currentTimeMillis()

                withContext(Dispatchers.IO) {
                    database.localphytomonitoringheaderDao()
                        .finalizarMonitoreo(
                            idHeader = headerActual.idHeader,
                            finishedAt = terminadoMs
                        )
                }

                headerActual = headerActual.copy(
                    status = "Completado",
                    finishedAt = terminadoMs
                )

                accionDialogoMapa = null

                Toast.makeText(
                    context,
                    "Monitoreo terminado correctamente",
                    Toast.LENGTH_SHORT
                ).show()

                onMonitoreoActualizadoActual("Completado")
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al terminar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    val capturasPuntosUnicos = remember(
        checkpoints,
        puntos,
        headerActual.idHeader
    ) {
        val idsPuntosActuales = puntos
            .map { it.idTargetPoint }
            .toSet()

        checkpoints
            .filter { checkpoint ->
                checkpoint.idHeader == headerActual.idHeader &&
                        checkpoint.idTargetPoint in idsPuntosActuales
            }
            .map { it.idTargetPoint }
            .distinct()
            .size
    }

    val estaPausado = esMonitoreoPausadoMapa(
        status = headerActual.status,
        additionalNotes = headerActual.additionalNotes
    )

    val estaCerrado = esEstadoCerradoMapa(headerActual.status)

    LaunchedEffect(headerActual.idHeader, estaCerrado) {
        while (!estaCerrado) {
            ahoraMs = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    val internetInicial = remember(header.idHeader) {
        hayInternet(context)
    }

    val htmlMapa = remember(
        vertices,
        puntos,
        checkpoints,
        nombreMonitoreo,
        internetInicial
    ) {
        crearHtmlMapaMonitoreo(
            nombreMonitoreo = nombreMonitoreo,
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            ubicacionInicial = null,
            internetDisponible = internetInicial
        )
    }

    fun solicitarRegreso() {
        when {
            cargando || finalizandoMonitoreo -> Unit
            estaCerrado -> onBackClick()
            estaPausado -> onBackClick()
            else -> accionDialogoMapa = AccionDialogoMapa.REGRESAR
        }
    }

    BackHandler(enabled = true) {
        solicitarRegreso()
    }

    LaunchedEffect(header.idHeader) {
        cargarDatosMapa()
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
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    ubicacionUsuario = Pair(
                        location.latitude,
                        location.longitude
                    )
                }
            }

            try {
                listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER
                ).forEach { provider ->
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(
                            provider,
                            1500L,
                            0.1f,
                            listener,
                            Looper.getMainLooper()
                        )
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

    accionDialogoMapa?.let { accion ->
        DialogoAccionMapa(
            accion = accion,
            totalPuntos = puntos.size,
            puntosCapturados = capturasPuntosUnicos,
            finalizandoMonitoreo = finalizandoMonitoreo,
            onDismiss = {
                accionDialogoMapa = null
            },
            onPausarConfirmado = {
                pausarMonitoreo()
            },
            onTerminarConfirmado = {
                terminarMonitoreo()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
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

            BarraMapaMonitoreo(
                nombreMonitoreo = nombreMonitoreo,
                status = headerActual.status,
                additionalNotes = headerActual.additionalNotes,
                startAt = headerActual.startAt,
                ahoraMs = ahoraMs,
                onRegresarClick = {
                    solicitarRegreso()
                }
            )

            when {
                cargando -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF0B6B20))

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Cargando mapa...",
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                error != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Text(
                            text = error ?: "Error desconocido",
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    ResumenMapaMonitoreo(
                        totalVertices = vertices.size,
                        totalPuntos = puntos.size,
                        totalCapturas = capturasPuntosUnicos
                    )

                    if (!estaCerrado) {
                        AccionesMapaMonitoreo(
                            totalPuntos = puntos.size,
                            puntosCapturados = capturasPuntosUnicos,
                            estaPausado = estaPausado,
                            finalizandoMonitoreo = finalizandoMonitoreo,
                            onPausarClick = {
                                accionDialogoMapa = AccionDialogoMapa.PAUSAR
                            },
                            onTerminarClick = {
                                accionDialogoMapa = AccionDialogoMapa.TERMINAR
                            },
                            onContinuarClick = {
                                continuarMonitoreo()
                            }
                        )
                    }

                    MapaMonitoreoWebViewSeguro(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 18.dp),
                        htmlMapa = htmlMapa,
                        ubicacionUsuario = ubicacionUsuario,
                        internetDisponible = internetDisponible,
                        onInternetDisponibleChange = {
                            // Se ignora para evitar recargar el WebView.
                        },
                        onPuntoValidoClick = { idTargetPoint ->
                            if (!estaPausado && !estaCerrado) {
                                onPuntoValidoActual(idTargetPoint)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Este monitoreo no permite capturar puntos en este estado",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

private data class MapaCargaResultado(
    val header: LocalPhytomonitoringHeaderEntity,
    val vertices: List<LocalPlotVertexEntity>,
    val puntos: List<LocalPhytomonitoringTargetPointEntity>,
    val checkpoints: List<LocalPhytomonitoringCheckpointEntity>
)
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
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
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

    var catalogo by remember {
        mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList())
    }

    var ubicacionUsuario by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }

    var puntoLibreSeleccionado by remember {
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

    var creandoPuntoLibre by remember {
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
                        .getCheckpointsByHeader(header.idHeader),
                    catalogo = database.localphytosanitarycatalogDao()
                        .getAllCatalogo()
                )
            }

            headerActual = resultado.header
            vertices = resultado.vertices
            puntos = resultado.puntos
            checkpoints = resultado.checkpoints
            catalogo = resultado.catalogo
        } catch (e: Exception) {
            error = "Error al cargar mapa: ${e.message}"
        } finally {
            cargando = false
        }
    }

    fun pausarMonitoreo() {
        if (finalizandoMonitoreo || creandoPuntoLibre) return

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
        if (finalizandoMonitoreo || creandoPuntoLibre) return

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
        if (finalizandoMonitoreo || creandoPuntoLibre) return

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

    fun cerrarMonitoreoAutomaticamentePorTiempo() {
        if (finalizandoMonitoreo || creandoPuntoLibre) return

        val cierreAutomaticoMs = obtenerCierreAutomaticoMs(headerActual.startAt) ?: return

        if (System.currentTimeMillis() < cierreAutomaticoMs) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val actualizado = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    if (esEstadoCerradoMapa(fresco.status)) {
                        null
                    } else {
                        val cierreMs = obtenerCierreAutomaticoMs(fresco.startAt)
                            ?: System.currentTimeMillis()

                        val notaAnterior = fresco.additionalNotes.trim()
                        val notaAutomatica =
                            "CERRADO_AUTOMATICO: Se cerró automáticamente porque se agotó el tiempo del monitoreo."

                        val notaFinal = when {
                            notaAnterior.isBlank() -> notaAutomatica
                            notaAnterior.equals("PAUSADO", ignoreCase = true) -> notaAutomatica
                            notaAnterior.contains("CERRADO_AUTOMATICO", ignoreCase = true) -> notaAnterior
                            else -> "$notaAnterior\n$notaAutomatica"
                        }

                        val nuevoHeader = fresco.copy(
                            status = "Completado",
                            finishedAt = cierreMs,
                            additionalNotes = notaFinal
                        )

                        database.localphytomonitoringheaderDao()
                            .updateHeader(nuevoHeader)

                        nuevoHeader
                    }
                }

                if (actualizado != null) {
                    headerActual = actualizado
                    accionDialogoMapa = null
                    puntoLibreSeleccionado = null

                    Toast.makeText(
                        context,
                        "Tiempo agotado. El monitoreo se cerró automáticamente.",
                        Toast.LENGTH_LONG
                    ).show()

                    onMonitoreoActualizadoActual("Completado")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "No se pudo cerrar por tiempo: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    val idsPuntosCapturados = remember(
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
            .toSet()
    }

    val puntosCapturados = idsPuntosCapturados.size
    val numeroSiguientePunto = puntosCapturados + 1

    val estaPausado = esMonitoreoPausadoMapa(
        status = headerActual.status,
        additionalNotes = headerActual.additionalNotes
    )

    val estaCerrado = esEstadoCerradoMapa(headerActual.status)

    val tiempoAgotado = tiempoMonitoreoAgotado(
        startAt = headerActual.startAt,
        ahoraMs = ahoraMs
    )

    fun crearPuntoLibreYRegistrar() {
        val puntoSeleccionado = puntoLibreSeleccionado ?: run {
            Toast.makeText(
                context,
                "Toca el mapa donde quieres hacer el monitoreo",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (creandoPuntoLibre || finalizandoMonitoreo) return

        if (tiempoAgotado) {
            cerrarMonitoreoAutomaticamentePorTiempo()
            Toast.makeText(
                context,
                "El tiempo del monitoreo ya se agotó",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (estaPausado || estaCerrado) {
            Toast.makeText(
                context,
                "Este monitoreo no permite crear puntos en este estado",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        creandoPuntoLibre = true

        coroutineScope.launch {
            try {
                val idNuevoPunto = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    if (esEstadoCerradoMapa(fresco.status)) {
                        throw IllegalStateException("El monitoreo ya está cerrado")
                    }

                    database.localphytomonitoringheaderDao()
                        .iniciarMonitoreoSiEstaPendiente(
                            idHeader = fresco.idHeader,
                            now = System.currentTimeMillis()
                        )

                    database.LocalPhytomonitoringTargetPointDao()
                        .insertTargetPoint(
                            LocalPhytomonitoringTargetPointEntity(
                                extId = null,
                                radiusM = 5,
                                lat = puntoSeleccionado.first,
                                lon = puntoSeleccionado.second,
                                status = "En proceso",
                                idHeader = fresco.idHeader,
                                idLocalPlot = fresco.idLocalPlot
                            )
                        )
                }

                puntoLibreSeleccionado = null
                onPuntoValidoActual(idNuevoPunto)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "No se pudo crear el punto: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                creandoPuntoLibre = false
            }
        }
    }

    LaunchedEffect(headerActual.idHeader, estaCerrado) {
        while (!estaCerrado) {
            ahoraMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(
        headerActual.idHeader,
        tiempoAgotado,
        estaCerrado,
        finalizandoMonitoreo
    ) {
        if (tiempoAgotado && !estaCerrado && !finalizandoMonitoreo) {
            cerrarMonitoreoAutomaticamentePorTiempo()
        }
    }

    val internetInicial = remember(header.idHeader) {
        hayInternet(context)
    }

    val htmlMapa = remember(
        vertices,
        puntos,
        checkpoints,
        catalogo,
        nombreMonitoreo,
        internetInicial
    ) {
        crearHtmlMapaMonitoreo(
            nombreMonitoreo = nombreMonitoreo,
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            catalogo = catalogo,
            ubicacionInicial = null,
            internetDisponible = internetInicial
        )
    }

    fun solicitarRegreso() {
        when {
            cargando || finalizandoMonitoreo || creandoPuntoLibre -> Unit
            estaCerrado -> onBackClick()
            estaPausado -> onBackClick()
            puntosCapturados <= 0 -> accionDialogoMapa = AccionDialogoMapa.REGRESAR
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
            puntosCapturados = puntosCapturados,
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
                    InstruccionMapaLibre(
                        puntosCapturados = puntosCapturados
                    )

                    ChipPuntosMonitoreados(
                        puntosCapturados = puntosCapturados
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        ) {
                            MapaMonitoreoWebViewSeguro(
                                modifier = Modifier
                                    .fillMaxSize(),
                                htmlMapa = htmlMapa,
                                ubicacionUsuario = ubicacionUsuario,
                                puntoLibreSeleccionado = puntoLibreSeleccionado,
                                internetDisponible = internetDisponible,
                                onInternetDisponibleChange = {
                                    // Se ignora para evitar recargar el WebView.
                                },
                                onPuntoLibreSeleccionado = { lat, lon ->
                                    if (!tiempoAgotado && !estaPausado && !estaCerrado) {
                                        puntoLibreSeleccionado = Pair(lat, lon)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Este monitoreo no permite capturar puntos en este estado",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )

                            puntoLibreSeleccionado?.let {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    ConfirmarPuntoLibreCard(
                                        numeroPunto = numeroSiguientePunto,
                                        creandoPunto = creandoPuntoLibre,
                                        onConfirmarClick = {
                                            crearPuntoLibreYRegistrar()
                                        },
                                        onCancelarClick = {
                                            puntoLibreSeleccionado = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (!estaCerrado && !tiempoAgotado) {
                        AccionesMapaMonitoreo(
                            totalPuntos = puntos.size,
                            puntosCapturados = puntosCapturados,
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
                }
            }
        }
    }
}

private data class MapaCargaResultado(
    val header: LocalPhytomonitoringHeaderEntity,
    val vertices: List<LocalPlotVertexEntity>,
    val puntos: List<LocalPhytomonitoringTargetPointEntity>,
    val checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    val catalogo: List<LocalPhytosanitaryCatalogEntity>
)

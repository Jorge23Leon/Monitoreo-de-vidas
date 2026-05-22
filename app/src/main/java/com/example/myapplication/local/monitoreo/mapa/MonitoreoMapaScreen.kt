package com.example.myapplication.local.monitoreo.mapa


import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val onPuntoValidoActual by rememberUpdatedState(onPuntoValidoClick)
    val onMonitoreoActualizadoActual by rememberUpdatedState(onMonitoreoActualizado)

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

    val ubicacionActualizada by rememberUpdatedState(ubicacionUsuario)

    var tienePermisoUbicacion by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cargando by remember {
        mutableStateOf(true)
    }

    var finalizandoMonitoreo by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val permisoUbicacionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        tienePermisoUbicacion = granted

        if (granted) {
            ubicacionUsuario = obtenerUltimaUbicacion(context)
        }
    }

    LaunchedEffect(header.idHeader) {
        cargando = true
        error = null
        internetDisponible = hayInternet(context)

        try {
            val resultado = withContext(Dispatchers.IO) {
                database.localphytomonitoringheaderDao()
                    .iniciarMonitoreoSiEstaPendiente(
                        idHeader = header.idHeader,
                        now = System.currentTimeMillis()
                    )

                val listaVertices = database.LocalPlotVertexDao()
                    .getVerticesByPlot(header.idLocalPlot)

                val listaPuntos = database.LocalPhytomonitoringTargetPointDao()
                    .getTargetPointsByHeader(header.idHeader)

                val listaCheckpoints = database.localphytomonitoringcheckpointDao()
                    .getCheckpointsByHeader(header.idHeader)

                Triple(listaVertices, listaPuntos, listaCheckpoints)
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
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER
                )

                providers.forEach { provider ->
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(
                            provider,
                            2000L,
                            1f,
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

    val capturasPuntosUnicos = remember(
        checkpoints,
        puntos,
        header.idHeader
    ) {
        val idsPuntosActuales = puntos
            .map { it.idTargetPoint }
            .toSet()

        checkpoints
            .filter { checkpoint ->
                checkpoint.idHeader == header.idHeader &&
                        checkpoint.idTargetPoint in idsPuntosActuales
            }
            .map { checkpoint ->
                checkpoint.idTargetPoint
            }
            .distinct()
            .size
    }

    val htmlMapa = remember(
        vertices,
        puntos,
        checkpoints,
        nombreMonitoreo,
        internetDisponible
    ) {
        crearHtmlMapaMonitoreo(
            nombreMonitoreo = nombreMonitoreo,
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            ubicacionInicial = ubicacionActualizada,
            internetDisponible = internetDisponible
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick
            )

            BarraMapaMonitoreo(
                nombreMonitoreo = nombreMonitoreo
            )

            if (cargando) {
                Text(
                    text = "Cargando mapa...",
                    modifier = Modifier.padding(16.dp),
                    color = Color.DarkGray
                )
            } else if (error != null) {
                Text(
                    text = error ?: "Error desconocido",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Red
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(72.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF6F6F6)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Resumen",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Text(
                            text = "Vértices: ${vertices.size}  |  Puntos: ${puntos.size}  |  Capturas: $capturasPuntosUnicos",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4A4A4A),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (finalizandoMonitoreo) return@Button

                        val totalPuntos = puntos.size
                        val puntosCapturados = capturasPuntosUnicos
                        val puntosFaltantes = totalPuntos - puntosCapturados

                        fun dejarEnProceso() {
                            finalizandoMonitoreo = true

                            coroutineScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        database.localphytomonitoringheaderDao()
                                            .dejarMonitoreoEnProceso(
                                                idHeader = header.idHeader
                                            )
                                    }

                                    Toast.makeText(
                                        context,
                                        "Monitoreo guardado en proceso",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    onMonitoreoActualizadoActual("En proceso")

                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error al dejar en proceso: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    finalizandoMonitoreo = false
                                }
                            }
                        }

                        fun terminarMonitoreo() {
                            finalizandoMonitoreo = true

                            coroutineScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        database.localphytomonitoringheaderDao()
                                            .finalizarMonitoreo(
                                                idHeader = header.idHeader,
                                                finishedAt = System.currentTimeMillis()
                                            )
                                    }

                                    Toast.makeText(
                                        context,
                                        "Monitoreo terminado correctamente",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    onMonitoreoActualizadoActual("Completado")

                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error al terminar monitoreo: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    finalizandoMonitoreo = false
                                }
                            }
                        }

                        if (totalPuntos <= 0) {
                            AlertDialog.Builder(context)
                                .setTitle("Sin puntos")
                                .setMessage("Este monitoreo no tiene puntos asignados.")
                                .setPositiveButton("Aceptar") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                            return@Button
                        }

                        if (puntosFaltantes > 0) {
                            AlertDialog.Builder(context)
                                .setTitle("Faltan puntos por monitorear")
                                .setMessage(
                                    "Llevas $puntosCapturados de $totalPuntos puntos capturados.\n\n" +
                                            "Aún faltan $puntosFaltantes puntos.\n\n" +
                                            "Puedes dejar el monitoreo en proceso para continuarlo después, " +
                                            "o terminarlo de todos modos."
                                )
                                .setNegativeButton("Dejar en proceso") { dialog, _ ->
                                    dialog.dismiss()
                                    dejarEnProceso()
                                }
                                .setPositiveButton("Terminar monitoreo") { dialog, _ ->
                                    dialog.dismiss()
                                    terminarMonitoreo()
                                }
                                .show()
                        } else {
                            AlertDialog.Builder(context)
                                .setTitle("Terminar monitoreo")
                                .setMessage(
                                    "Todos los puntos ya fueron capturados.\n\n" +
                                            "¿Deseas terminar el monitoreo?"
                                )
                                .setNegativeButton("Cancelar") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .setPositiveButton("Terminar") { dialog, _ ->
                                    dialog.dismiss()
                                    terminarMonitoreo()
                                }
                                .show()
                        }
                    },
                    enabled = !finalizandoMonitoreo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        disabledContainerColor = Color(0xFF9E9E9E)
                    )
                ) {
                    Text(
                        text = if (finalizandoMonitoreo) {
                            "Procesando..."
                        } else {
                            "✅ Terminar monitoreo"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                MapaMonitoreoWebViewSeguro(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    htmlMapa = htmlMapa,
                    ubicacionUsuario = ubicacionUsuario,
                    internetDisponible = internetDisponible,
                    onInternetDisponibleChange = { nuevoValor ->
                        internetDisponible = nuevoValor
                    },
                    onPuntoValidoClick = { idTargetPoint ->
                        onPuntoValidoActual(idTargetPoint)
                    }
                )
            }
        }
    }
}


@Composable
private fun MapaMonitoreoWebViewSeguro(
    modifier: Modifier = Modifier,
    htmlMapa: String,
    ubicacionUsuario: Pair<Double, Double>?,
    internetDisponible: Boolean,
    onInternetDisponibleChange: (Boolean) -> Unit,
    onPuntoValidoClick: (Long) -> Unit
) {
    AndroidView<View>(
        modifier = modifier,
        factory = { ctx ->
            try {
                WebView(ctx).apply {
                    webViewClient = WebViewClient()

                    addJavascriptInterface(
                        MapaBridge { idTargetPoint ->
                            onPuntoValidoClick(idTargetPoint)
                        },
                        "Android"
                    )

                    webChromeClient = object : WebChromeClient() {
                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            AlertDialog.Builder(ctx)
                                .setMessage(message ?: "")
                                .setPositiveButton("Aceptar") { dialog, _ ->
                                    dialog.dismiss()
                                    result?.confirm()
                                }
                                .setOnCancelListener {
                                    result?.cancel()
                                }
                                .show()

                            return true
                        }
                    }

                    setBackgroundColor(AndroidColor.WHITE)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    settings.cacheMode = if (hayInternet(ctx)) {
                        WebSettings.LOAD_DEFAULT
                    } else {
                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }

                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = settings.userAgentString + " AndroidWebViewMonitoreo"

                    tag = htmlMapa

                    loadDataWithBaseURL(
                        "file:///android_asset/",
                        htmlMapa,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            } catch (e: Throwable) {
                TextView(ctx).apply {
                    text = "No se pudo abrir el mapa.\n\nDetalle: ${e.javaClass.simpleName}: ${e.message}"
                    setTextColor(android.graphics.Color.RED)
                    setPadding(24, 24, 24, 24)
                }
            }
        },
        update = { view ->
            if (view is WebView) {
                val internetActual = hayInternet(view.context)

                view.settings.cacheMode = if (internetActual) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                }

                if (internetActual != internetDisponible) {
                    onInternetDisponibleChange(internetActual)
                }

                if (view.tag != htmlMapa) {
                    view.tag = htmlMapa
                    view.loadDataWithBaseURL(
                        "file:///android_asset/",
                        htmlMapa,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }

                ubicacionUsuario?.let { location ->
                    val js = """
                        if (window.updateUserLocation) {
                            window.updateUserLocation(${location.first}, ${location.second});
                        }
                    """.trimIndent()

                    view.postDelayed({
                        view.evaluateJavascript(js, null)
                    }, 300)
                }
            }
        }
    )
}

@Composable
private fun BarraMapaMonitoreo(
    nombreMonitoreo: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F6F6))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mapa del monitoreo",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = nombreMonitoreo,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun crearHtmlMapaMonitoreo(
    nombreMonitoreo: String,
    vertices: List<LocalPlotVertexEntity>,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    ubicacionInicial: Pair<Double, Double>?,
    internetDisponible: Boolean
): String {
    val verticesJson = crearVerticesJson(vertices)
    val puntosJson = crearPuntosJson(puntos, checkpoints)
    val nombreMonitoreoJson = JSONObject.quote(nombreMonitoreo)
    val internetJson = if (internetDisponible) "true" else "false"

    val usuarioJson = if (ubicacionInicial != null) {
        """
        {
            "lat": ${ubicacionInicial.first},
            "lon": ${ubicacionInicial.second}
        }
        """.trimIndent()
    } else {
        "null"
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta
                name="viewport"
                content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes"
            />

            <link rel="stylesheet" href="leaflet/leaflet.css" />

            <style>
                html, body {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    font-family: Arial, sans-serif;
                    background: #ffffff;
                }

                #map {
                    width: 100vw;
                    height: 100vh;
                    min-height: 420px;
                    border-radius: 16px;
                    overflow: hidden;
                    background:
                        linear-gradient(135deg, rgba(96,125,70,0.22) 25%, transparent 25%) -18px 0,
                        linear-gradient(225deg, rgba(96,125,70,0.22) 25%, transparent 25%) -18px 0,
                        linear-gradient(315deg, rgba(96,125,70,0.22) 25%, transparent 25%),
                        linear-gradient(45deg, rgba(96,125,70,0.22) 25%, transparent 25%);
                    background-size: 36px 36px;
                    background-color: #dfe8d1;
                }

                .legend {
                    background: rgba(255, 255, 255, 0.95);
                    padding: 9px 11px;
                    border-radius: 10px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.28);
                    font-size: 12px;
                    line-height: 19px;
                    color: #222;
                }

                .legend-title {
                    font-weight: bold;
                    margin-bottom: 4px;
                }

                .dot {
                    height: 10px;
                    width: 10px;
                    border-radius: 50%;
                    display: inline-block;
                    margin-right: 6px;
                }

                .offline-box {
                    background: rgba(255, 255, 255, 0.95);
                    color: #333;
                    padding: 7px 9px;
                    border-radius: 9px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.25);
                    font-size: 11px;
                    text-align: center;
                    line-height: 15px;
                }

                .map-title-box {
                    background: rgba(255, 255, 255, 0.95);
                    color: #1B5E20;
                    padding: 7px 10px;
                    border-radius: 9px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.22);
                    font-size: 12px;
                    font-weight: bold;
                    max-width: 230px;
                }

                .error-box {
                    padding: 12px;
                    color: #b00020;
                    font-size: 14px;
                }

                .leaflet-control-attribution {
                    font-size: 10px;
                }
            </style>
        </head>

        <body>
            <div id="map"></div>

            <script src="leaflet/leaflet.js"></script>

            <script>
                const nombreMonitoreo = $nombreMonitoreoJson;
                const vertices = $verticesJson;
                const puntos = $puntosJson;
                const usuarioInicial = $usuarioJson;
                const internetDisponible = $internetJson;

                let map = null;
                let userMarker = null;
                let currentUserLocation = null;

                function mostrarError(mensaje) {
                    document.body.innerHTML =
                        '<div class="error-box"><b>Error al cargar el mapa:</b><br>' +
                        mensaje +
                        '</div>';
                }

                function textoEstado(status) {
                    if (status === 'completed') return 'Capturado';
                    if (status === 'cancelled') return 'Cancelado';
                    if (status === 'in_progress') return 'En proceso';
                    return 'Pendiente';
                }

                function colorEstado(status) {
                    if (status === 'completed') return '#2E7D32';
                    if (status === 'cancelled') return '#D32F2F';
                    if (status === 'in_progress') return '#0288D1';
                    return '#F9A825';
                }

                function validarRangoDelPunto(p) {
                    if (currentUserLocation == null) {
                        alert('No se ha obtenido tu ubicación actual. Activa el GPS e intenta de nuevo.');
                        return null;
                    }

                    const distancia = map.distance(
                        [currentUserLocation.lat, currentUserLocation.lon],
                        [p.lat, p.lon]
                    );

                    if (distancia > p.radius) {
                        alert(
                            'Estás fuera del rango permitido para este punto.\n\n' +
                            'Distancia actual: ' + distancia.toFixed(1) + ' m\n' +
                            'Radio permitido: ' + p.radius + ' m'
                        );
                        return null;
                    }

                    return distancia;
                }

                window.updateUserLocation = function(lat, lon) {
                    currentUserLocation = {
                        lat: lat,
                        lon: lon
                    };

                    if (map == null) return;

                    if (userMarker == null) {
                        userMarker = L.circleMarker([lat, lon], {
                            radius: 10,
                            color: '#0D47A1',
                            weight: 3,
                            fillColor: '#1976D2',
                            fillOpacity: 0.95
                        })
                        .addTo(map)
                        .bindPopup('<b>Tu ubicación actual</b>');
                    } else {
                        userMarker.setLatLng([lat, lon]);
                    }
                };

                function agregarCapaBase() {
                    const capaSatelital = L.tileLayer(
                        'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                        {
                            maxZoom: 20,
                            attribution: 'Tiles © Esri'
                        }
                    );

                    capaSatelital.addTo(map);

                    if (!internetDisponible) {
                        const avisoOffline = L.control({ position: 'topright' });

                        avisoOffline.onAdd = function () {
                            const div = L.DomUtil.create('div', 'offline-box');
                            div.innerHTML = '<b>Sin internet</b><br>Usando caché si existe';
                            return div;
                        };

                        avisoOffline.addTo(map);
                    }
                }

                function agregarTituloMapa() {
                    const titleControl = L.control({ position: 'topleft' });

                    titleControl.onAdd = function () {
                        const div = L.DomUtil.create('div', 'map-title-box');
                        div.innerHTML = internetDisponible
                            ? 'Vista satelital de parcela'
                            : 'Satélite en caché / vista local';
                        return div;
                    };

                    titleControl.addTo(map);
                }

                function agregarLeyenda() {
                    const legend = L.control({ position: 'bottomleft' });

                    legend.onAdd = function () {
                        const div = L.DomUtil.create('div', 'legend');
                        div.innerHTML =
                            '<div class="legend-title">Estados</div>' +
                            '<span class="dot" style="background:#F9A825"></span>Pendiente<br>' +
                            '<span class="dot" style="background:#0288D1"></span>En proceso<br>' +
                            '<span class="dot" style="background:#2E7D32"></span>Capturado<br>' +
                            '<span class="dot" style="background:#1976D2"></span>Tu ubicación<br>' +
                            '<span class="dot" style="background:#D32F2F"></span>Cancelado';
                        return div;
                    };

                    legend.addTo(map);
                }

                try {
                    if (typeof L === 'undefined') {
                        mostrarError('No se pudo cargar Leaflet local. Revisa que exista app/src/main/assets/leaflet/leaflet.js y leaflet.css');
                    } else {
                        map = L.map('map', {
                            zoomControl: true,
                            preferCanvas: true
                        });

                        agregarCapaBase();

                        const polygonLatLng = vertices.map(v => [v.lat, v.lon]);

                        if (polygonLatLng.length > 0) {
                            const polygon = L.polygon(polygonLatLng, {
                                color: '#1B5E20',
                                weight: 4,
                                opacity: 1,
                                fillColor: internetDisponible ? '#7CB342' : '#8BC34A',
                                fillOpacity: internetDisponible ? 0.30 : 0.48
                            }).addTo(map);

                            polygon.bindPopup(
                                '<b>Parcela del monitoreo</b><br>' +
                                nombreMonitoreo +
                                '<br><br>Vértices: ' + polygonLatLng.length
                            );

                            map.fitBounds(polygon.getBounds(), {
                                padding: [30, 30],
                                maxZoom: 19
                            });
                        } else if (puntos.length > 0) {
                            map.setView([puntos[0].lat, puntos[0].lon], 18);
                        } else if (usuarioInicial != null) {
                            map.setView([usuarioInicial.lat, usuarioInicial.lon], 18);
                        } else {
                            map.setView([20.6767, -101.3563], 14);
                        }

                        puntos.forEach((p, index) => {
                            const color = colorEstado(p.status);

                            const marker = L.circleMarker([p.lat, p.lon], {
                                radius: 12,
                                color: '#1A1A1A',
                                weight: 2,
                                fillColor: color,
                                fillOpacity: 0.95
                            }).addTo(map);

                            const touchArea = L.circleMarker([p.lat, p.lon], {
                                radius: 30,
                                color: color,
                                weight: 0,
                                opacity: 0,
                                fillColor: color,
                                fillOpacity: 0,
                                interactive: true
                            }).addTo(map);

                            marker.bindTooltip(
                                'Punto ' + (index + 1) + ' - ' + textoEstado(p.status),
                                {
                                    permanent: false,
                                    direction: 'top'
                                }
                            );

                            function abrirPunto() {
                                if (p.status === 'completed') {
                                    alert('Este punto ya fue capturado.');
                                    return;
                                }

                                const distancia = validarRangoDelPunto(p);

                                if (distancia == null) {
                                    return;
                                }

                                if (window.Android && Android.onPuntoSeleccionado) {
                                    Android.onPuntoSeleccionado(String(p.idTargetPoint));
                                } else {
                                    alert('No se pudo abrir el registro del punto.');
                                }
                            }

                            marker.on('click', abrirPunto);
                            touchArea.on('click', abrirPunto);
                        });

                        if (usuarioInicial != null) {
                            window.updateUserLocation(usuarioInicial.lat, usuarioInicial.lon);
                        }

                        agregarTituloMapa();
                        agregarLeyenda();

                        setTimeout(function () {
                            map.invalidateSize();

                            if (polygonLatLng.length > 0) {
                                const bounds = L.latLngBounds(polygonLatLng);
                                map.fitBounds(bounds, {
                                    padding: [30, 30],
                                    maxZoom: 19
                                });
                            }
                        }, 500);
                    }
                } catch (e) {
                    mostrarError(e.message);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun crearVerticesJson(vertices: List<LocalPlotVertexEntity>): String {
    val array = JSONArray()

    vertices.sortedBy { it.level }.forEach { vertex ->
        val obj = JSONObject()
        obj.put("level", vertex.level)
        obj.put("lat", vertex.lat)
        obj.put("lon", vertex.lon)
        array.put(obj)
    }

    return array.toString()
}

private fun crearPuntosJson(
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>
): String {
    val idsPuntosActuales = puntos
        .map { it.idTargetPoint }
        .toSet()

    val idsCapturados = checkpoints
        .filter { checkpoint ->
            checkpoint.idTargetPoint in idsPuntosActuales
        }
        .map { checkpoint ->
            checkpoint.idTargetPoint
        }
        .toSet()

    val array = JSONArray()

    puntos.forEach { punto ->
        val statusBase = punto.status.lowercase().trim()

        val statusFinal = when {
            punto.idTargetPoint in idsCapturados -> "completed"

            statusBase == "completed" ||
                    statusBase == "completado" ||
                    statusBase == "capturado" -> "completed"

            statusBase == "cancelled" ||
                    statusBase == "cancelado" -> "cancelled"

            statusBase == "in_progress" ||
                    statusBase == "en proceso" -> "in_progress"

            else -> "pending"
        }

        val obj = JSONObject()
        obj.put("idTargetPoint", punto.idTargetPoint)
        obj.put("lat", punto.lat)
        obj.put("lon", punto.lon)
        obj.put("radius", punto.radiusM)
        obj.put("status", statusFinal)

        array.put(obj)
    }

    return array.toString()
}

@SuppressLint("MissingPermission")
private fun obtenerUltimaUbicacion(context: Context): Pair<Double, Double>? {
    return try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        val location = providers
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
            }
            .maxByOrNull { it.time }

        location?.let {
            Pair(it.latitude, it.longitude)
        }
    } catch (_: Exception) {
        null
    }
}

private fun hayInternet(context: Context): Boolean {
    return try {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false

        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    } catch (_: Exception) {
        false
    }
}

private class MapaBridge(
    private val onPuntoSeleccionado: (Long) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPuntoSeleccionado(idTargetPoint: String) {
        val id = idTargetPoint.toLongOrNull() ?: return

        mainHandler.post {
            onPuntoSeleccionado(id)
        }
    }
}

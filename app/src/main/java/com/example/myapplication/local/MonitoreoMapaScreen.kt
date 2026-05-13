package com.example.myapplication.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.app.AlertDialog
import android.webkit.JsResult
@Composable
fun MonitoreoMapaScreen(
    database: AppDatabase,
    header: LocalPhytomonitoringHeaderEntity,
    nombreMonitoreo: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

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

        try {
            val resultado = withContext(Dispatchers.IO) {
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

    val htmlMapa = remember(vertices, puntos, checkpoints, nombreMonitoreo) {
        crearHtmlMapaMonitoreo(
            nombreMonitoreo = nombreMonitoreo,
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            ubicacionInicial = ubicacionActualizada
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier.size(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52AFC4)),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("↩", color = Color.White, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column {
                    Text(
                        text = "Mapa del monitoreo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Text(
                        text = nombreMonitoreo,
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }

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
                        .height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F6F6)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Resumen",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Vértices: ${vertices.size} | Puntos: ${puntos.size} | Capturas: ${checkpoints.size}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Text(
                            text = "Verde = capturado, Amarillo = pendiente, Azul = tu ubicación",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )

                        Text(
                            text = "Solo puedes ver datos del punto si estás dentro del radio permitido.",
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
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

                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString =
                                settings.userAgentString + " AndroidWebViewMonitoreo"

                            tag = htmlMapa

                            loadDataWithBaseURL(
                                "https://unpkg.com/",
                                htmlMapa,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    update = { webView ->
                        if (webView.tag != htmlMapa) {
                            webView.tag = htmlMapa
                            webView.loadDataWithBaseURL(
                                "https://unpkg.com/",
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

                            webView.postDelayed({
                                webView.evaluateJavascript(js, null)
                            }, 300)
                        }
                    }
                )
            }
        }
    }
}

private fun crearHtmlMapaMonitoreo(
    nombreMonitoreo: String,
    vertices: List<LocalPlotVertexEntity>,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    ubicacionInicial: Pair<Double, Double>?
): String {
    val verticesJson = crearVerticesJson(vertices)
    val puntosJson = crearPuntosJson(puntos, checkpoints)
    val nombreMonitoreoJson = JSONObject.quote(nombreMonitoreo)

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

            <link
                rel="stylesheet"
                href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
            />

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
                    border-radius: 14px;
                    overflow: hidden;
                    background: #e8e8e8;
                }

                .legend {
                    background: white;
                    padding: 8px 10px;
                    border-radius: 8px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.25);
                    font-size: 12px;
                    line-height: 18px;
                }

                .dot {
                    height: 10px;
                    width: 10px;
                    border-radius: 50%;
                    display: inline-block;
                    margin-right: 6px;
                }

                .error-box {
                    padding: 12px;
                    color: #b00020;
                    font-size: 14px;
                }
            </style>
        </head>

        <body>
            <div id="map"></div>

            <script
                src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
            </script>

            <script>
                const nombreMonitoreo = $nombreMonitoreoJson;
                const vertices = $verticesJson;
                const puntos = $puntosJson;
                const usuarioInicial = $usuarioJson;

                let map = null;
                let userMarker = null;
                let userCircle = null;
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
                    return 'Pendiente';
                }

                function colorEstado(status) {
                    if (status === 'completed') return '#2E7D32';
                    if (status === 'cancelled') return '#D32F2F';
                    return '#F9A825';
                }

                function contenidoPunto(p, index, distancia) {
                    return (
                        '<b>Punto ' + (index + 1) + '</b><br>' +
                        'Estado: ' + textoEstado(p.status) + '<br>' +
                        'Radio permitido: ' + p.radius + ' m<br>' +
                        'Distancia actual: ' + distancia.toFixed(1) + ' m<br>' +
                        'Lat: ' + p.lat + '<br>' +
                        'Lon: ' + p.lon
                    );
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
                            radius: 9,
                            color: '#0D47A1',
                            weight: 2,
                            fillColor: '#1976D2',
                            fillOpacity: 0.95
                        })
                        .addTo(map)
                        .bindPopup('<b>Tu ubicación actual</b>');

                        userCircle = L.circle([lat, lon], {
                            radius: 8,
                            color: '#1976D2',
                            weight: 1,
                            fillColor: '#1976D2',
                            fillOpacity: 0.15
                        }).addTo(map);
                    } else {
                        userMarker.setLatLng([lat, lon]);
                        userCircle.setLatLng([lat, lon]);
                    }
                };

                try {
                    if (typeof L === 'undefined') {
                        mostrarError('No se pudo cargar Leaflet. Revisa internet del dispositivo o WebView.');
                    } else {
                        map = L.map('map', {
                            zoomControl: true
                        });

                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            maxZoom: 22,
                            attribution: '&copy; OpenStreetMap'
                        }).addTo(map);

                        const polygonLatLng = vertices.map(v => [v.lat, v.lon]);

                        if (polygonLatLng.length > 0) {
                            const polygon = L.polygon(polygonLatLng, {
                                color: '#2E7D32',
                                weight: 3,
                                fillColor: '#A5D6A7',
                                fillOpacity: 0.25
                            }).addTo(map);

                            polygon.bindPopup('Parcela del monitoreo<br><b>' + nombreMonitoreo + '</b>');
                            map.fitBounds(polygon.getBounds(), { padding: [25, 25] });
                        } else if (puntos.length > 0) {
                            map.setView([puntos[0].lat, puntos[0].lon], 18);
                        } else if (usuarioInicial != null) {
                            map.setView([usuarioInicial.lat, usuarioInicial.lon], 18);
                        } else {
                            map.setView([20.6767, -101.3563], 14);
                        }

                        puntos.forEach((p, index) => {
                            const color = colorEstado(p.status);

                            L.circle([p.lat, p.lon], {
                                radius: p.radius,
                                color: color,
                                weight: 1,
                                fillColor: color,
                                fillOpacity: 0.12
                            }).addTo(map);

                            const marker = L.circleMarker([p.lat, p.lon], {
                                radius: 8,
                                color: '#222222',
                                weight: 1,
                                fillColor: color,
                                fillOpacity: 0.95
                            }).addTo(map);

                            marker.on('click', function () {
                                const distancia = validarRangoDelPunto(p);

                                if (distancia == null) {
                                    return;
                                }

                                marker
                                    .bindPopup(contenidoPunto(p, index, distancia))
                                    .openPopup();
                            });
                        });

                        if (usuarioInicial != null) {
                            window.updateUserLocation(usuarioInicial.lat, usuarioInicial.lon);
                        }

                        const legend = L.control({ position: 'bottomleft' });

                        legend.onAdd = function () {
                            const div = L.DomUtil.create('div', 'legend');
                            div.innerHTML =
                                '<b>Estados</b><br>' +
                                '<span class="dot" style="background:#F9A825"></span>Pendiente<br>' +
                                '<span class="dot" style="background:#2E7D32"></span>Capturado<br>' +
                                '<span class="dot" style="background:#1976D2"></span>Tu ubicación<br>' +
                                '<span class="dot" style="background:#D32F2F"></span>Cancelado';
                            return div;
                        };

                        legend.addTo(map);

                        setTimeout(function () {
                            map.invalidateSize();

                            if (polygonLatLng.length > 0) {
                                const bounds = L.latLngBounds(polygonLatLng);
                                map.fitBounds(bounds, { padding: [25, 25] });
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
    val idsCapturados = checkpoints
        .map { it.idTargetPoint }
        .toSet()

    val array = JSONArray()

    puntos.forEach { punto ->
        val statusBase = punto.status.lowercase()

        val statusFinal = when {
            punto.idTargetPoint in idsCapturados -> "completed"
            statusBase == "completed" -> "completed"
            statusBase == "cancelled" -> "cancelled"
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
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

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
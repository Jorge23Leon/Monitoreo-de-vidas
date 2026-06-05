package com.example.myapplication.local.monitoreo.reporte

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.monitoreo.severidad.calcularSeveridadPorPunto
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun MapaReporteWebViewUi(
    htmlMapa: String,
    modifier: Modifier = Modifier
) {
    AndroidView<View>(
        modifier = modifier,
        factory = { ctx ->
            try {
                WebView(ctx).apply {
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.postDelayed({
                                view.evaluateJavascript(
                                    "if (window.reporteMap) { window.reporteMap.invalidateSize(); }",
                                    null
                                )
                            }, 350)
                        }
                    }
                    setBackgroundColor(android.graphics.Color.WHITE)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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
                    text = "No se pudo abrir el mapa del reporte. Revisa Android System WebView y los assets de Leaflet.\n\nDetalle: ${e.javaClass.simpleName}: ${e.message}"
                    setTextColor(android.graphics.Color.RED)
                    setPadding(24, 24, 24, 24)
                }
            }
        },
        update = { view ->
            if (view is WebView) {
                if (view.tag != htmlMapa) {
                    view.tag = htmlMapa
                    view.loadDataWithBaseURL(
                        "file:///android_asset/",
                        htmlMapa,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else {
                    view.postDelayed({
                        view.evaluateJavascript(
                            "if (window.reporteMap) { window.reporteMap.invalidateSize(); }",
                            null
                        )
                    }, 350)
                }
            }
        }
    )
}

internal fun crearHtmlMapaReporteUi(
    vertices: List<LocalPlotVertexEntity>,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    catalogo: List<LocalPhytosanitaryCatalogEntity>
): String {
    val catalogoMap = catalogo.associateBy { it.idPhytosanitary }
    val checkpointsPorPunto = checkpoints.groupBy { it.idTargetPoint }
    val verticesJson = JSONArray().apply {
        vertices.sortedBy { it.level }.forEach { vertex ->
            put(JSONObject().apply {
                put("level", vertex.level)
                put("lat", vertex.lat)
                put("lon", vertex.lon)
            })
        }
    }.toString()

    val puntosOrdenados = puntos.sortedBy { it.idTargetPoint }

    val puntosJson = JSONArray().apply {
        puntosOrdenados.forEachIndexed { index, punto ->
            val capturas = checkpointsPorPunto[punto.idTargetPoint].orEmpty()

            val severidadPunto = calcularSeveridadPorPunto(
                checkpointsPunto = capturas,
                catalogoPorId = catalogoMap
            )
            val nivelFinal = severidadPunto.nivelFinal
            val totalCantidadPunto = severidadPunto.totalCantidadPunto

            val capturasArray = JSONArray().apply {
                severidadPunto.fitos.forEach { fito ->
                    put(JSONObject().apply {
                        put("nombre", fito.nombre)
                        put("tipo", textoTipoCatalogo(fito.tipo))
                        put("fase", fito.etapasResumen)
                        put("cantidad", fito.cantidadTotal)
                        put("fecha", formatearFechaOpcionalReporteUi(fito.fechaUltimaCaptura))
                        put("severidad", fito.nivel.etiqueta)
                        put("color", fito.nivel.colorHex)
                    })
                }
            }

            val statusBase = punto.status.lowercase().trim()
            val statusFinal = when {
                capturas.isNotEmpty() -> "completed"
                statusBase == "cancelled" || statusBase == "cancelado" -> "cancelled"
                else -> "not_monitored"
            }

            put(JSONObject().apply {
                put("numero", index + 1)
                put("id", punto.idTargetPoint)
                put("lat", punto.lat)
                put("lon", punto.lon)
                put("radius", punto.radiusM)
                put("status", statusFinal)
                put("severity", nivelFinal.name.lowercase())
                put("severityLabel", nivelFinal.etiqueta)
                put("severityColor", nivelFinal.colorHex)
                put("totalCantidad", totalCantidadPunto)
                put("capturas", capturasArray)
            })
        }
    }.toString()

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes" />
            <link rel="stylesheet" href="leaflet/leaflet.css" />
            <style>
                html, body {
                    width: 100%;
                    height: 100%;
                    min-height: 320px;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    font-family: Arial, sans-serif;
                    background: #edf5e8;
                }

                #map {
                    width: 100vw;
                    height: 100vh;
                    min-height: 320px;
                    border-radius: 18px;
                    overflow: hidden;
                    background:
                        linear-gradient(135deg, rgba(123,179,66,0.20) 25%, transparent 25%) -16px 0,
                        linear-gradient(225deg, rgba(123,179,66,0.20) 25%, transparent 25%) -16px 0,
                        linear-gradient(315deg, rgba(123,179,66,0.20) 25%, transparent 25%),
                        linear-gradient(45deg, rgba(123,179,66,0.20) 25%, transparent 25%);
                    background-size: 32px 32px;
                    background-color: #dfe8d1;
                }

                .legend {
                    background: rgba(255,255,255,0.95);
                    padding: 9px 11px;
                    border-radius: 12px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.25);
                    font-size: 12px;
                    line-height: 19px;
                    color: #222;
                }

                .legend-title {
                    font-weight: bold;
                    margin-bottom: 4px;
                    color: #123D1F;
                }

                .dot {
                    height: 10px;
                    width: 10px;
                    border-radius: 50%;
                    display: inline-block;
                    margin-right: 6px;
                }

                .map-title-box {
                    background: rgba(255,255,255,0.96);
                    color: #123D1F;
                    padding: 8px 10px;
                    border-radius: 12px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.20);
                    font-size: 12px;
                    font-weight: bold;
                }

                .no-data-box {
                    background: rgba(255,255,255,0.96);
                    color: #5F6F64;
                    padding: 10px 12px;
                    border-radius: 12px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.20);
                    font-size: 12px;
                    line-height: 17px;
                    max-width: 240px;
                }

                .popup-title {
                    color: #123D1F;
                    font-weight: bold;
                    font-size: 14px;
                    margin-bottom: 4px;
                }

                .popup-row {
                    font-size: 12px;
                    margin-top: 2px;
                }

                .popup-captura {
                    margin-top: 6px;
                    padding-top: 5px;
                    border-top: 1px solid #e0e0e0;
                    font-size: 12px;
                }

                .error-box {
                    padding: 12px;
                    color: #b00020;
                    font-size: 14px;
                    background: #ffffff;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script src="leaflet/leaflet.js"></script>
            <script>
                const vertices = $verticesJson;
                const puntos = $puntosJson;

                function mostrarError(mensaje) {
                    document.body.innerHTML = '<div class="error-box"><b>Error al cargar mapa:</b><br>' + mensaje + '</div>';
                }

                function textoEstado(status) {
                    if (status === 'completed') return 'Monitoreado';
                    if (status === 'cancelled') return 'Cancelado';
                    return 'No monitoreado';
                }

                function colorEstado(status, p) {
                    if (status === 'completed') return (p && p.severityColor) ? p.severityColor : '#16A34A';
                    if (status === 'cancelled') return '#DC2626';
                    return '#D98A00';
                }

                function crearPopupPunto(p) {
                    let html = '<div class="popup-title">Punto ' + p.numero + '</div>';
                    html += '<div class="popup-row"><b>Estado:</b> ' + textoEstado(p.status) + '</div>';
                    if (p.status === 'completed') {
                        html += '<div class="popup-row"><b>Severidad:</b> ' + (p.severityLabel || 'Sin plaga') + '</div>';
                        html += '<div class="popup-row"><b>Total:</b> ' + (p.totalCantidad || 0) + '</div>';
                    }
                    html += '<div class="popup-row"><b>Lat:</b> ' + p.lat + '</div>';
                    html += '<div class="popup-row"><b>Lon:</b> ' + p.lon + '</div>';
                    html += '<div class="popup-row"><b>Radio:</b> ' + p.radius + ' m</div>';

                    if (p.capturas.length === 0) {
                        html += '<div class="popup-captura">No se realizó monitoreo en este punto.</div>';
                    } else {
                        p.capturas.forEach(function(c) {
                            html += '<div class="popup-captura">';
                            html += '<b>' + c.nombre + '</b><br>';
                            html += 'Tipo: ' + c.tipo + '<br>';
                            html += 'Fase: ' + c.fase + '<br>';
                            html += 'Cantidad: ' + c.cantidad + '<br>';
                            html += 'Severidad: <span style="color:' + (c.color || '#333') + ';font-weight:bold">' + (c.severidad || '-') + '</span><br>';
                            html += 'Fecha: ' + c.fecha;
                            html += '</div>';
                        });
                    }

                    return html;
                }

                function textoPlanoPunto(p) {
                    let txt = 'Punto ' + p.numero + '\nEstado: ' + textoEstado(p.status) + '\nLat: ' + p.lat + '\nLon: ' + p.lon;
                    if (p.capturas.length === 0) {
                        txt += '\nNo se realizó monitoreo en este punto.';
                    } else {
                        p.capturas.forEach(function(c) {
                            txt += '\n\n' + c.nombre + '\nTipo: ' + c.tipo + '\nFase: ' + c.fase + '\nCantidad: ' + c.cantidad + '\nSeveridad: ' + (c.severidad || '-') + '\nFecha: ' + c.fecha;
                        });
                    }
                    return txt;
                }

                function escaparParaAlert(texto) {
                    return texto
                        .replace(/\\/g, '\\\\')
                        .replace(/'/g, "\\'")
                        .replace(/\r/g, '')
                        .replace(/\n/g, '\\n');
                }

                function dibujarFallback(motivo) {
                    const todos = [];
                    vertices.forEach(function(v) { todos.push({lat: v.lat, lon: v.lon}); });
                    puntos.forEach(function(p) { todos.push({lat: p.lat, lon: p.lon}); });

                    if (todos.length === 0) {
                        document.getElementById('map').innerHTML =
                            '<div class="no-data-box" style="margin:16px"><b>Sin datos para dibujar</b><br>No se encontraron vértices ni puntos para este monitoreo.</div>';
                        return;
                    }

                    let minLat = todos[0].lat;
                    let maxLat = todos[0].lat;
                    let minLon = todos[0].lon;
                    let maxLon = todos[0].lon;

                    todos.forEach(function(c) {
                        minLat = Math.min(minLat, c.lat);
                        maxLat = Math.max(maxLat, c.lat);
                        minLon = Math.min(minLon, c.lon);
                        maxLon = Math.max(maxLon, c.lon);
                    });

                    if (Math.abs(maxLat - minLat) < 0.000001) {
                        maxLat += 0.0001;
                        minLat -= 0.0001;
                    }
                    if (Math.abs(maxLon - minLon) < 0.000001) {
                        maxLon += 0.0001;
                        minLon -= 0.0001;
                    }

                    const pad = 8;
                    function x(lon) {
                        return pad + ((lon - minLon) / (maxLon - minLon)) * (100 - pad * 2);
                    }
                    function y(lat) {
                        return pad + (1 - ((lat - minLat) / (maxLat - minLat))) * (100 - pad * 2);
                    }

                    let polygonPoints = '';
                    vertices.forEach(function(v) {
                        polygonPoints += x(v.lon).toFixed(2) + ',' + y(v.lat).toFixed(2) + ' ';
                    });

                    let svg = '';
                    svg += '<div class="map-title-box" style="position:absolute;left:12px;top:12px;z-index:2">Mapa local del reporte</div>';
                    svg += '<svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet" style="position:absolute;left:0;top:0;width:100%;height:100%;background:#dfe8d1">';
                    svg += '<defs><pattern id="grid" width="8" height="8" patternUnits="userSpaceOnUse"><path d="M 8 0 L 0 0 0 8" fill="none" stroke="#c8d8bd" stroke-width="0.25"/></pattern></defs>';
                    svg += '<rect x="0" y="0" width="100" height="100" fill="url(#grid)" />';

                    if (vertices.length > 0) {
                        svg += '<polygon points="' + polygonPoints + '" fill="#7CB342" fill-opacity="0.30" stroke="#1B5E20" stroke-width="0.9" />';
                    }

                    puntos.forEach(function(p) {
                        const px = x(p.lon).toFixed(2);
                        const py = y(p.lat).toFixed(2);
                        const color = colorEstado(p.status, p);
                        svg += '<circle cx="' + px + '" cy="' + py + '" r="3.4" fill="' + color + '" stroke="#1A1A1A" stroke-width="0.7" onclick="alert(\'' + escaparParaAlert(textoPlanoPunto(p)) + '\')" />';
                        svg += '<text x="' + px + '" y="' + (parseFloat(py) - 4.5).toFixed(2) + '" font-size="3.2" text-anchor="middle" fill="#123D1F" font-weight="bold">' + p.numero + '</text>';
                    });

                    svg += '</svg>';
                   svg += '<div class="legend" style="position:absolute;left:12px;bottom:12px;z-index:2"><div class="legend-title">Semáforo</div><span class="dot" style="background:#16A34A"></span>Verde: sin plaga<br><span class="dot" style="background:#FACC15"></span>Amarillo: severidad menor<br><span class="dot" style="background:#F97316"></span>Naranja: severidad mayor<br><span class="dot" style="background:#DC2626"></span>Rojo: supera severidad mayor</div>';
                    if (motivo) {
                        svg += '<div class="no-data-box" style="position:absolute;right:12px;top:12px;z-index:2"><b>Vista local</b><br>' + motivo + '</div>';
                    }

                    document.getElementById('map').style.position = 'relative';
                    document.getElementById('map').innerHTML = svg;
                }

                try {
                    if (typeof L === 'undefined') {
                        dibujarFallback('Leaflet local no cargó, pero se dibujó el polígono y los puntos con vista local.');
                    } else {
                        const map = L.map('map', {
                        zoomControl: true,
                        preferCanvas: true,
                        zoomSnap: 0.25,
                        zoomDelta: 0.5,
                        minZoom: 3,
                        maxZoom: 18,
                        bounceAtZoomLimits: false
});
                        window.reporteMap = map;

                        const capaSatelital = L.tileLayer(
                            'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                            {
                                minZoom: 3,
                                maxZoom: 18,
                                maxNativeZoom: 17,
                                attribution: 'Tiles © Esri',
                                errorTileUrl: ''
                            }
                        ).addTo(map);

                        const polygonLatLng = vertices.map(function(v) {
                            return [v.lat, v.lon];
                        });

                        if (polygonLatLng.length > 0) {
                            const polygon = L.polygon(polygonLatLng, {
                                color: '#1B5E20',
                                weight: 4,
                                opacity: 1,
                                fillColor: '#7CB342',
                                fillOpacity: 0.28
                            }).addTo(map);

                            polygon.bindPopup('<b>Parcela del monitoreo</b><br>Vértices: ' + polygonLatLng.length);

                            map.fitBounds(polygon.getBounds(), {
                                padding: [30, 30],
                                maxZoom: 17
                            });
                        } else if (puntos.length > 0) {
                            map.setView([puntos[0].lat, puntos[0].lon], 18);
                        } else {
                            map.setView([20.6767, -101.3563], 14);

                            const noData = L.control({ position: 'topright' });
                            noData.onAdd = function() {
                                const div = L.DomUtil.create('div', 'no-data-box');
                                div.innerHTML = '<b>Sin datos para dibujar</b><br>No se encontraron vértices ni puntos para este monitoreo.';
                                return div;
                            };
                            noData.addTo(map);
                        }

                        puntos.forEach(function(p) {
                            const color = colorEstado(p.status, p);

                            L.circle([p.lat, p.lon], {
                                radius: p.radius,
                                color: color,
                                weight: 1,
                                fillColor: color,
                                fillOpacity: 0.08
                            }).addTo(map);

                            L.circleMarker([p.lat, p.lon], {
                                radius: 11,
                                color: '#1A1A1A',
                                weight: 2,
                                fillColor: color,
                                fillOpacity: 0.95
                            })
                            .addTo(map)
                            .bindTooltip('Punto ' + p.numero + ' • ' + textoEstado(p.status), {
                                permanent: false,
                                direction: 'top'
                            })
                            .bindPopup(crearPopupPunto(p));
                        });

                        const titleControl = L.control({ position: 'topleft' });
                        titleControl.onAdd = function() {
                            const div = L.DomUtil.create('div', 'map-title-box');
                            div.innerHTML = 'Vista satelital del monitoreo';
                            return div;
                        };
                        titleControl.addTo(map);

                        const legend = L.control({ position: 'bottomleft' });
                        legend.onAdd = function() {
                            const div = L.DomUtil.create('div', 'legend');
                            div.innerHTML =
                        '<div class="legend-title">Semáforo</div>' +
                        '<span class="dot" style="background:#16A34A"></span>Verde: sin plaga<br>' +
                        '<span class="dot" style="background:#FACC15"></span>Amarillo: severidad menor<br>' +
                        '<span class="dot" style="background:#F97316"></span>Naranja: severidad mayor<br>' +
                        '<span class="dot" style="background:#DC2626"></span>Rojo: supera severidad mayor';
                            return div;
                        };
                        legend.addTo(map);

                        setTimeout(function() {
                            map.invalidateSize();
                            if (polygonLatLng.length > 0) {
                                map.fitBounds(L.latLngBounds(polygonLatLng), {
                                    padding: [30, 30],
                                    maxZoom: 17
                                });
                            }
                        }, 500);
                    }
                } catch (e) {
                    dibujarFallback(e.message);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

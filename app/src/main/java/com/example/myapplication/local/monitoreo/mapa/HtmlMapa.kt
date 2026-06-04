package com.example.myapplication.local.monitoreo.mapa

import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import org.json.JSONObject

internal fun crearHtmlMapaMonitoreo(
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

            <link rel="stylesheet" href="file:///android_asset/leaflet/leaflet.css" />

            <style>
                html, body {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    font-family: Arial, sans-serif;
                    background: #dfe8d1;
                    overflow: hidden;
                }

                #map {
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    width: 100%;
                    height: 100%;
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

                .loading-box,
                .error-box,
                .hint-box {
                    position: absolute;
                    left: 14px;
                    top: 14px;
                    right: 14px;
                    background: rgba(255, 255, 255, 0.94);
                    border-radius: 12px;
                    box-shadow: 0 3px 14px rgba(0,0,0,0.22);
                    padding: 12px;
                    z-index: 9999;
                    color: #1B5E20;
                    font-size: 13px;
                    line-height: 18px;
                }

                .error-box {
                    color: #b00020;
                }

                .legend {
                    background: rgba(255, 255, 255, 0.94);
                    padding: 8px 10px;
                    border-radius: 12px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.22);
                    font-size: 11px;
                    line-height: 18px;
                    color: #222;
                    margin-bottom: 28px;
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
                    vertical-align: middle;
                }

                .map-title-box {
                    background: rgba(255, 255, 255, 0.95);
                    color: #1B5E20;
                    padding: 7px 10px;
                    border-radius: 10px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.22);
                    font-size: 12px;
                    font-weight: bold;
                    max-width: 260px;
                }

                .leaflet-control-attribution {
                    font-size: 10px;
                }

                .leaflet-bottom {
                    bottom: 28px !important;
                }
            </style>
        </head>

        <body>
            <div id="map">
                <div class="loading-box">
                    <b>Cargando mapa...</b><br>
                    Preparando parcela y puntos monitoreados.
                </div>
            </div>

            <script src="file:///android_asset/leaflet/leaflet.js"></script>

            <script>
                const nombreMonitoreo = $nombreMonitoreoJson;
                const vertices = $verticesJson;
                const puntos = $puntosJson;
                const usuarioInicial = $usuarioJson;
                const internetDisponible = $internetJson;

                let map = null;
                let userMarker = null;
                let userAccuracy = null;
                let currentUserLocation = null;
                let selectedMarker = null;
                let selectedHalo = null;
                let polygonLatLng = [];

                function limpiarCargando() {
                    const loading = document.querySelector('.loading-box');
                    if (loading) loading.remove();
                }

                function normalizarPunto(p) {
                    return {
                        idTargetPoint: p.idTargetPoint,
                        lat: Number(p.lat),
                        lon: Number(p.lon),
                        status: (p.status || 'completed').toLowerCase()
                    };
                }

                function puntoEstaCompletado(p) {
                    const estado = (p.status || '').toLowerCase();
                    return estado === 'completed' ||
                        estado === 'completado' ||
                        estado === 'capturado';
                }

                function colorPunto(p) {
                    return puntoEstaCompletado(p) ? '#087A2D' : '#D98A00';
                }

                function textoPunto(p) {
                    return puntoEstaCompletado(p) ? 'Punto monitoreado' : 'Punto sin finalizar';
                }

                function mostrarError(mensaje) {
                    const mapDiv = document.getElementById('map');
                    mapDiv.innerHTML =
                        '<div class="error-box"><b>Error al cargar el mapa:</b><br>' +
                        mensaje +
                        '<br><br>Se mostrará una vista básica.</div>';
                    renderFallback();
                }

                function agregarCapaBase() {
                    if (internetDisponible) {
                        L.tileLayer(
                            'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                            {
                                maxZoom: 19,
                                maxNativeZoom: 19,
                                attribution: 'Tiles © Esri'
                            }
                        ).addTo(map);
                    } else {
                        L.tileLayer(
                            'file:///android_asset/leaflet/tiles/{z}/{x}/{y}.png',
                            {
                                minZoom: 0,
                                maxZoom: 19,
                                maxNativeZoom: 19,
                                attribution: 'Mapa local'
                            }
                        ).addTo(map);
                    }
                }

                function puntoDentroPoligono(lat, lon) {
                    if (polygonLatLng.length < 3) return true;

                    let dentro = false;
                    const x = lon;
                    const y = lat;

                    for (let i = 0, j = polygonLatLng.length - 1; i < polygonLatLng.length; j = i++) {
                        const yi = polygonLatLng[i][0];
                        const xi = polygonLatLng[i][1];
                        const yj = polygonLatLng[j][0];
                        const xj = polygonLatLng[j][1];

                        const intersecta = ((yi > y) !== (yj > y)) &&
                            (x < (xj - xi) * (y - yi) / ((yj - yi) || 0.000000001) + xi);

                        if (intersecta) dentro = !dentro;
                    }

                    return dentro;
                }

                function setSelectedFreePoint(lat, lon) {
                    if (map == null) return;

                    clearSelectedFreePoint();

                    selectedHalo = L.circle([lat, lon], {
                        radius: 4,
                        color: '#0B6B20',
                        weight: 3,
                        opacity: 0.95,
                        fillColor: '#55B947',
                        fillOpacity: 0.18,
                        interactive: false
                    }).addTo(map);

                    selectedMarker = L.circleMarker([lat, lon], {
                        radius: 15,
                        color: '#0B6B20',
                        weight: 4,
                        fillColor: '#FFFFFF',
                        fillOpacity: 0.95
                    }).addTo(map);

                    selectedMarker.bindTooltip('Punto nuevo seleccionado', {
                        permanent: false,
                        direction: 'top'
                    });
                }

                function clearSelectedFreePoint() {
                    if (selectedMarker != null) {
                        map.removeLayer(selectedMarker);
                        selectedMarker = null;
                    }

                    if (selectedHalo != null) {
                        map.removeLayer(selectedHalo);
                        selectedHalo = null;
                    }
                }

                window.setSelectedFreePoint = setSelectedFreePoint;
                window.clearSelectedFreePoint = clearSelectedFreePoint;

                function seleccionarDesdeMapa(lat, lon) {
                    if (!puntoDentroPoligono(lat, lon)) {
                        alert('Selecciona un punto dentro de la parcela.');
                        return;
                    }

                    setSelectedFreePoint(lat, lon);

                    if (window.Android && Android.onPuntoLibreSeleccionado) {
                        Android.onPuntoLibreSeleccionado(String(lat), String(lon));
                    }
                }

                window.updateUserLocation = function(lat, lon) {
                    if (map == null) return;

                    const latNum = Number(lat);
                    const lonNum = Number(lon);

                    if (isNaN(latNum) || isNaN(lonNum)) return;

                    currentUserLocation = {
                        lat: latNum,
                        lon: lonNum
                    };

                    if (userAccuracy != null) {
                        map.removeLayer(userAccuracy);
                    }

                    if (userMarker != null) {
                        map.removeLayer(userMarker);
                    }

                    userAccuracy = L.circle([latNum, lonNum], {
                        radius: 8,
                        color: '#1E88E5',
                        weight: 2,
                        opacity: 0.35,
                        fillColor: '#1E88E5',
                        fillOpacity: 0.18,
                        interactive: false
                    }).addTo(map);

                    userMarker = L.circleMarker([latNum, lonNum], {
                        radius: 10,
                        color: '#FFFFFF',
                        weight: 4,
                        fillColor: '#1E88E5',
                        fillOpacity: 1
                    }).addTo(map);

                    userMarker.bindTooltip('Tu ubicación actual', {
                        permanent: false,
                        direction: 'top'
                    });
                };

                function agregarTituloMapa() {
                    const control = L.control({ position: 'topright' });

                    control.onAdd = function() {
                        const div = L.DomUtil.create('div', 'map-title-box');
                        div.innerHTML = 'Toca el mapa donde estás parado';
                        return div;
                    };

                    control.addTo(map);
                }

                function aplicarLimitesDeParcela(bounds) {
                    if (!map || !bounds || !bounds.isValid()) return;
                    const boundsExpandidos = bounds.pad(0.45);
                    map.setMaxBounds(boundsExpandidos);
                    map.options.maxBoundsViscosity = 0.85;
                }

                function agregarLeyenda() {
                    const control = L.control({ position: 'bottomleft' });

                    control.onAdd = function() {
                        const div = L.DomUtil.create('div', 'legend');
                        div.innerHTML =
                            '<div class="legend-title">Estados</div>' +
                            '<div><span class="dot" style="background:#087A2D"></span>Monitoreado</div>' +
                            '<div><span class="dot" style="background:#1E88E5"></span>Tu ubicación</div>' +
                            '<div><span class="dot" style="background:#FFFFFF; border:2px solid #0B6B20; box-sizing:border-box"></span>Punto nuevo</div>';
                        return div;
                    };

                    control.addTo(map);
                }

                function renderFallback() {
                    const mapDiv = document.getElementById('map');
                    mapDiv.innerHTML = '<div class="error-box"><b>Sin mapa interactivo</b><br>No se pudo cargar Leaflet.</div>';
                }

                function inicializarMapa() {
                    limpiarCargando();

                    if (typeof L === 'undefined') {
                        renderFallback();
                        return;
                    }

                    map = L.map('map', {
                        zoomControl: true,
                        preferCanvas: true,
                        zoomSnap: 0.25,
                        zoomDelta: 0.5,
                        minZoom: 3,
                        maxZoom: 19,
                        bounceAtZoomLimits: false
                    });

                    map.setView([20.6767, -101.3563], 14);
                    agregarCapaBase();

                    polygonLatLng = vertices
                        .map(function(v) {
                            return [Number(v.lat), Number(v.lon)];
                        })
                        .filter(function(v) {
                            return !isNaN(v[0]) && !isNaN(v[1]);
                        });

                    if (polygonLatLng.length > 0) {
                        const polygon = L.polygon(polygonLatLng, {
                            color: '#0B6B20',
                            weight: 4,
                            opacity: 1,
                            fillColor: internetDisponible ? '#7CB342' : '#8BC34A',
                            fillOpacity: internetDisponible ? 0.26 : 0.45
                        }).addTo(map);

                        polygon.bindPopup(
                            '<b>Parcela del monitoreo</b><br>' +
                            nombreMonitoreo +
                            '<br><br>Toca dentro de la parcela para crear un punto.'
                        );

                        const boundsParcela = polygon.getBounds();
                        aplicarLimitesDeParcela(boundsParcela);
                        map.fitBounds(boundsParcela, {
                            padding: [18, 18],
                            maxZoom: 18
                        });
                    } else if (puntos.length > 0) {
                        const p0 = normalizarPunto(puntos[0]);
                        map.setView([p0.lat, p0.lon], 19);
                    } else if (usuarioInicial != null) {
                        map.setView([usuarioInicial.lat, usuarioInicial.lon], 18);
                    }

                    puntos.map(normalizarPunto).forEach(function(p, index) {
                        const marker = L.circleMarker([p.lat, p.lon], {
                            radius: 12,
                            color: '#FFFFFF',
                            weight: 3,
                            fillColor: colorPunto(p),
                            fillOpacity: 0.98
                        }).addTo(map);

                        marker.bindTooltip(
                            'Punto ' + (index + 1) + '<br>' + textoPunto(p),
                            {
                                permanent: false,
                                direction: 'top'
                            }
                        );
                    });

                    map.on('click', function(e) {
                        seleccionarDesdeMapa(e.latlng.lat, e.latlng.lng);
                    });

                    if (usuarioInicial != null) {
                        window.updateUserLocation(usuarioInicial.lat, usuarioInicial.lon);
                    }

                    agregarTituloMapa();
                    agregarLeyenda();

                    setTimeout(function () {
                        if (map != null) {
                            map.invalidateSize(true);
                            if (polygonLatLng.length > 0) {
                                const bounds = L.latLngBounds(polygonLatLng);
                                aplicarLimitesDeParcela(bounds);
                                map.fitBounds(bounds, {
                                    padding: [18, 18],
                                    maxZoom: 18
                                });
                            }
                        }
                    }, 450);
                }

                try {
                    if (document.readyState === 'complete' || document.readyState === 'interactive') {
                        setTimeout(inicializarMapa, 120);
                    } else {
                        document.addEventListener('DOMContentLoaded', function() {
                            setTimeout(inicializarMapa, 120);
                        });
                    }
                } catch (e) {
                    mostrarError(e.message || String(e));
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

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
                .error-box {
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
                    background: rgba(255, 255, 255, 0.92);
                    padding: 7px 9px;
                    border-radius: 10px;
                    box-shadow: 0 3px 12px rgba(0,0,0,0.22);
                    font-size: 11px;
                    line-height: 17px;
                    color: #222;
                    max-width: 150px;
                    margin-bottom: 42px;
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

                .offline-box,
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

                .offline-box {
                    color: #333;
                    font-size: 11px;
                    text-align: center;
                    line-height: 15px;
                }

                .leaflet-control-attribution {
                    font-size: 10px;
                }
                .leaflet-bottom {
                    bottom: 34px !important;
                }
            </style>
        </head>

        <body>
            <div id="map">
                <div class="loading-box">
                    <b>Cargando mapa...</b><br>
                    Preparando polígono y puntos del monitoreo.
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
                let currentUserLocation = null;

                function normalizarPunto(p) {
                    return {
                        idTargetPoint: p.idTargetPoint,
                        lat: Number(p.lat),
                        lon: Number(p.lon),
                        radius: Number(p.radius || 50),
                        status: p.status || 'pending'
                    };
                }

                function limpiarCargando() {
                    const loading = document.querySelector('.loading-box');
                    if (loading) loading.remove();
                }

                function mostrarError(mensaje) {
                    const mapDiv = document.getElementById('map');
                    mapDiv.innerHTML =
                        '<div class="error-box"><b>Error al cargar el mapa:</b><br>' +
                        mensaje +
                        '<br><br>Se mostrará una vista básica del polígono.</div>';
                    renderFallback(mensaje);
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
                        lat: Number(lat),
                        lon: Number(lon)
                    };

                    if (map == null || typeof L === 'undefined') return;

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
                            maxZoom: 22,
                            maxNativeZoom: 18,
                            updateWhenIdle: true,
                            updateWhenZooming: false,
                            keepBuffer: 2,
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
                            : 'Vista local / satélite en caché';
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

                function abrirPuntoDesdeJs(p) {
                    if (p.status === 'completed') {
                        alert('Este punto ya fue capturado.');
                        return;
                    }

                    if (map != null) {
                        const distancia = validarRangoDelPunto(p);
                        if (distancia == null) return;
                    }

                    if (window.Android && Android.onPuntoSeleccionado) {
                        Android.onPuntoSeleccionado(String(p.idTargetPoint));
                    } else {
                        alert('No se pudo abrir el registro del punto.');
                    }
                }

                function renderFallback(motivo) {
                    const mapDiv = document.getElementById('map');
                    const width = Math.max(mapDiv.clientWidth || 360, 320);
                    const height = Math.max(mapDiv.clientHeight || 420, 420);

                    const coords = [];
                    vertices.forEach(function(v) {
                        coords.push({ lat: Number(v.lat), lon: Number(v.lon) });
                    });
                    puntos.map(normalizarPunto).forEach(function(p) {
                        coords.push({ lat: p.lat, lon: p.lon });
                    });

                    if (coords.length === 0) {
                        mapDiv.innerHTML = '<div class="error-box"><b>Sin datos de mapa</b><br>No hay vértices ni puntos para mostrar.</div>';
                        return;
                    }

                    let minLat = coords[0].lat, maxLat = coords[0].lat;
                    let minLon = coords[0].lon, maxLon = coords[0].lon;

                    coords.forEach(function(c) {
                        minLat = Math.min(minLat, c.lat);
                        maxLat = Math.max(maxLat, c.lat);
                        minLon = Math.min(minLon, c.lon);
                        maxLon = Math.max(maxLon, c.lon);
                    });

                    const pad = 42;
                    const latSpan = Math.max(maxLat - minLat, 0.000001);
                    const lonSpan = Math.max(maxLon - minLon, 0.000001);

                    function x(lon) {
                        return pad + ((lon - minLon) / lonSpan) * (width - pad * 2);
                    }

                    function y(lat) {
                        return height - pad - ((lat - minLat) / latSpan) * (height - pad * 2);
                    }

                    const polygonPoints = vertices
                        .map(function(v) { return x(Number(v.lon)) + ',' + y(Number(v.lat)); })
                        .join(' ');

                    let svg = '';
                    svg += '<svg width="100%" height="100%" viewBox="0 0 ' + width + ' ' + height + '" xmlns="http://www.w3.org/2000/svg">';
                    svg += '<rect x="0" y="0" width="' + width + '" height="' + height + '" fill="#dfe8d1" />';
                    svg += '<text x="16" y="28" font-size="14" font-weight="bold" fill="#1B5E20">Vista básica del monitoreo</text>';
                    svg += '<text x="16" y="48" font-size="11" fill="#4B5A45">' + nombreMonitoreo + '</text>';

                    if (polygonPoints.length > 0) {
                        svg += '<polygon points="' + polygonPoints + '" fill="#7CB342" fill-opacity="0.35" stroke="#1B5E20" stroke-width="4" />';
                    }

                    puntos.map(normalizarPunto).forEach(function(p, index) {
                        const cx = x(p.lon);
                        const cy = y(p.lat);
                        const color = colorEstado(p.status);
                        svg += '<circle cx="' + cx + '" cy="' + cy + '" r="13" fill="' + color + '" stroke="#111" stroke-width="2" />';
                        svg += '<text x="' + (cx - 4) + '" y="' + (cy + 4) + '" font-size="10" font-weight="bold" fill="#111">' + (index + 1) + '</text>';
                    });

                    svg += '</svg>';
                    mapDiv.innerHTML = svg;
                }

                function inicializarMapa() {
                    limpiarCargando();

                    if (typeof L === 'undefined') {
                        renderFallback('Leaflet no cargó desde assets locales.');
                        return;
                    }

                    map = L.map('map', {
                        zoomControl: true,
                        preferCanvas: true,
                        zoomSnap: 0.25,
                        zoomDelta: 0.5,
                        minZoom: 3,
                        maxZoom: 22
                    });

                    map.setView([20.6767, -101.3563], 14);
                    agregarCapaBase();

                    const polygonLatLng = vertices
                        .map(function(v) { return [Number(v.lat), Number(v.lon)]; })
                        .filter(function(v) { return !isNaN(v[0]) && !isNaN(v[1]); });

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
                            padding: [10, 10],
                            maxZoom: 19
                        });
                    } else if (puntos.length > 0) {
                        const p0 = normalizarPunto(puntos[0]);
                        map.setView([puntos[0].lat, puntos[0].lon], 19);
                    } else if (usuarioInicial != null) {
                        map.setView([usuarioInicial.lat, usuarioInicial.lon], 17);
                    }

                    puntos.map(normalizarPunto).forEach(function(p, index) {
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

                        marker.on('click', function() { abrirPuntoDesdeJs(p); });
                        touchArea.on('click', function() { abrirPuntoDesdeJs(p); });
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
                                map.fitBounds(bounds, {
                                    padding: [10, 10],
                                    maxZoom: 19
                                });
                            }
                        }
                    }, 500);
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

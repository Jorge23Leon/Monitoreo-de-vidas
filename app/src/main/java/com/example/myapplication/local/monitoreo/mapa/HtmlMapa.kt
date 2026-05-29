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

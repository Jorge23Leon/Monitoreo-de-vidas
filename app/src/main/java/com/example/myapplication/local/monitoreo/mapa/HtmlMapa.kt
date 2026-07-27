package com.example.myapplication.local.monitoreo.mapa

import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import org.json.JSONObject

internal fun crearHtmlMapaMonitoreo(
    nombreMonitoreo: String,
    vertices: List<LocalPlotVertexEntity>,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    catalogo: List<LocalPhytosanitaryCatalogEntity>,
    ubicacionInicial: Pair<Double, Double>?,
    internetDisponible: Boolean,
    modoVistaPrevia: Boolean = false
): String {
    val verticesJson = crearVerticesJson(vertices)
    val puntosJson = crearPuntosJson(puntos, checkpoints, catalogo)
    val nombreMonitoreoJson = JSONObject.quote(nombreMonitoreo)
    val internetJson = if (internetDisponible) "true" else "false"
    val modoVistaPreviaJson = if (modoVistaPrevia) "true" else "false"

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
                    position: fixed;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    min-height: 100%;
                    margin: 0;
                    padding: 0;
                    font-family: Arial, sans-serif;
                    background: #dfe8d1;
                    overflow: hidden;
                }

                #map {
                    display: block;
                    position: absolute;
                    inset: 0;
                    width: 100%;
                    height: 100vh;
                    min-height: 100%;
                    border-radius: 0;
                    overflow: hidden;
                    background:
                        linear-gradient(135deg, rgba(96,125,70,0.22) 25%, transparent 25%) -18px 0,
                        linear-gradient(225deg, rgba(96,125,70,0.22) 25%, transparent 25%) -18px 0,
                        linear-gradient(315deg, rgba(96,125,70,0.22) 25%, transparent 25%),
                        linear-gradient(45deg, rgba(96,125,70,0.22) 25%, transparent 25%);
                    background-size: 36px 36px;
                    background-color: #dfe8d1;
                }

                .leaflet-container {
                    width: 100% !important;
                    height: 100% !important;
                    min-height: 100% !important;
                    background: #dfe8d1;
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
                .legend-fija {
                    margin: 0;
                    background: rgba(255, 255, 255, 0.95);
                    padding: 7px 8px;
                    border: 1px solid rgba(18,61,31,0.16);
                    border-radius: 11px;
                    box-shadow: 0 3px 10px rgba(0,0,0,0.22);
                    font-size: 10.5px;
                    line-height: 14px;
                    color: #222;
                    min-width: 126px;
                    max-width: 148px;
                    box-sizing: border-box;
                }

                .legend-fija .legend-title {
                    color: #123D1F;
                    font-size: 11px;
                    font-weight: bold;
                    margin-bottom: 4px;
                    white-space: nowrap;
                }

                .legend-row {
                    display: flex;
                    align-items: center;
                    gap: 5px;
                    white-space: nowrap;
                }

                .legend-fija .dot {
                    width: 9px;
                    height: 9px;
                    flex: 0 0 9px;
                    margin-right: 0;
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
                .marcador-pe-wrapper {
                    background: transparent !important;
                    border: none !important;
                }

                .marcador-pe {
                    position: relative;
                    width: 28px;
                    height: 32px;
                    user-select: none;
                    -webkit-user-select: none;
                }

                .marcador-pe-letras {
                    position: absolute;
                    top: 0;
                    left: 5px;
                    width: 18px;
                    display: flex;
                    justify-content: space-around;
                    align-items: center;
                    color: #FFFFFF;
                    font-size: 9px;
                    line-height: 11px;
                    font-weight: 900;
                    text-shadow:
                        -1px -1px 2px #1A1A1A,
                         1px -1px 2px #1A1A1A,
                        -1px  1px 2px #1A1A1A,
                         1px  1px 2px #1A1A1A;
                }

                .marcador-pe-circulo {
                    position: absolute;
                    top: 12px;
                    left: 5px;
                    display: flex;
                    width: 18px;
                    height: 18px;
                    overflow: hidden;
                    border: 2px solid #17211B;
                    border-radius: 50%;
                    box-sizing: border-box;
                    background: #16A34A;
                    box-shadow:
                        0 2px 7px rgba(0, 0, 0, 0.48),
                        0 0 0 2px rgba(255, 255, 255, 0.82);
                }

                .marcador-pe-mitad {
                    width: 50%;
                    height: 100%;
                    box-sizing: border-box;
                }

                .marcador-pe-plaga {
                    border-right: 1.5px solid #17211B;
                }

                .marcador-pe-enfermedad {
                    border-left: 1.5px solid #17211B;
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
                const modoVistaPrevia = $modoVistaPreviaJson;

                let map = null;
                let userMarker = null;
                let userAccuracy = null;
                let currentUserLocation = null;
                let selectedMarker = null;
                let selectedHalo = null;
                let polygonLatLng = [];

                window.ajustarMapaMonitoreo = function() {
                    if (map != null) {
                        map.invalidateSize(true);
                    }
                };

                function limpiarCargando() {
                    const loading = document.querySelector('.loading-box');
                    if (loading) loading.remove();
                }

                function escapeHtml(value) {
                    return String(value || '')
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#039;');
                }

                function normalizarPunto(p) {
                    return {
                        idTargetPoint: p.idTargetPoint,
                        lat: Number(p.lat),
                        lon: Number(p.lon),
                        radius: Number(p.radius || p.radiusM || 4),
                        status: String(p.status || 'pending').toLowerCase(),

                        // Campos anteriores conservados por compatibilidad.
                        totalCantidadPunto: Number(p.totalCantidadPunto || 0),
                        severityStatus:
                            p.severityStatus ||
                            p.severityLabel ||
                            'Pendiente',
                        severityColor:
                            p.severityColor ||
                            '#D98A00',
                        severityLevel:
                            Number(p.severityLevel || 0),

                        // Mitad izquierda: plagas.
                        plagaColor:
                            p.plagaColor ||
                            '#16A34A',
                        plagaTexto:
                            p.plagaTexto ||
                            'Sin evaluar',
                        plagaNivel:
                            Number(p.plagaNivel || 0),
                        totalCantidadPlaga:
                            Number(p.totalCantidadPlaga || 0),

                        // Mitad derecha: enfermedades.
                        enfermedadColor:
                            p.enfermedadColor ||
                            '#16A34A',
                        enfermedadTexto:
                            p.enfermedadTexto ||
                            'Sin evaluar',
                        enfermedadNivel:
                            Number(p.enfermedadNivel || 0)
                    };
                }

                function crearIconoPuntoDividido(p) {
                    const colorPlaga =
                        p.plagaColor ||
                        '#16A34A';

                    const colorEnfermedad =
                        p.enfermedadColor ||
                        '#16A34A';

                    const html =
                        '<div class="marcador-pe">' +
                            '<div class="marcador-pe-letras">' +
                                '<span>P</span>' +
                                '<span>E</span>' +
                            '</div>' +
                            '<div class="marcador-pe-circulo">' +
                                '<div ' +
                                    'class="marcador-pe-mitad marcador-pe-plaga" ' +
                                    'style="background:' + colorPlaga + '">' +
                                '</div>' +
                                '<div ' +
                                    'class="marcador-pe-mitad marcador-pe-enfermedad" ' +
                                    'style="background:' + colorEnfermedad + '">' +
                                '</div>' +
                            '</div>' +
                        '</div>';

                    return L.divIcon({
                        className: 'marcador-pe-wrapper',
                        html: html,
                        iconSize: [28, 32],
                        iconAnchor: [14, 21],
                        tooltipAnchor: [0, -16]
                    });
                }

                function puntoEstaCompletado(p) {
                    const estado = (p.status || '').toLowerCase();
                    return estado === 'completed' ||
                        estado === 'completado' ||
                        estado === 'capturado';
                }

                function puntoEstaCancelado(p) {
                    const estado = (p.status || '').toLowerCase();
                    return estado === 'cancelled' ||
                        estado === 'cancelado' ||
                        estado === 'canceled';
                }

                function colorPunto(p) {
                    if (puntoEstaCompletado(p)) {
                        return p.severityColor || '#16A34A';
                    }

                    if (puntoEstaCancelado(p)) {
                        return '#6B7280';
                    }

                    return '#D98A00';
                }

                function textoPunto(p) {
                    if (puntoEstaCancelado(p)) {
                        return 'Punto cancelado';
                    }

                    if (!puntoEstaCompletado(p)) {
                        return 'Punto pendiente';
                    }

                    return '' +
                        '<b>Punto monitoreado</b><br>' +
                        '<b>P · Plaga:</b> ' +
                        escapeHtml(p.plagaTexto || 'Sin evaluar') +
                        '<br>' +
                        '<b>E · Enfermedad:</b> ' +
                        escapeHtml(p.enfermedadTexto || 'Sin evaluar');
                }

                function mostrarError(mensaje) {
                    const mapDiv = document.getElementById('map');
                    mapDiv.innerHTML =
                        '<div class="error-box"><b>Error al cargar el mapa:</b><br>' +
                        escapeHtml(mensaje) +
                        '<br><br>Se mostrará una vista básica.</div>';
                    renderFallback();
                }

                function agregarCapaBase() {
                    /*
                     * Primero se dibuja un fondo local bonito.
                     * Después se agrega la capa satelital de Esri.
                     *
                     * En Android, WebMapa.kt intercepta estos tiles:
                     * - con internet: los descarga y los guarda en filesDir/map_tiles
                     * - sin internet: sirve los tiles guardados
                     *
                     * Si no existe el tile en cache, se queda visible este fondo local.
                     */
                    const MapaLocal = L.GridLayer.extend({
                        createTile: function(coords) {
                            const tile = document.createElement('canvas');
                            const size = this.getTileSize();

                            tile.width = size.x;
                            tile.height = size.y;

                            const ctx = tile.getContext('2d');

                            ctx.fillStyle = '#E7F0DC';
                            ctx.fillRect(0, 0, size.x, size.y);

                            const grad = ctx.createLinearGradient(0, 0, size.x, size.y);
                            grad.addColorStop(0, 'rgba(139, 195, 74, 0.20)');
                            grad.addColorStop(0.45, 'rgba(255, 255, 255, 0.18)');
                            grad.addColorStop(1, 'rgba(76, 175, 80, 0.18)');
                            ctx.fillStyle = grad;
                            ctx.fillRect(0, 0, size.x, size.y);

                            ctx.strokeStyle = 'rgba(27, 94, 32, 0.10)';
                            ctx.lineWidth = 1;

                            for (let x = -size.x; x < size.x * 2; x += 44) {
                                ctx.beginPath();
                                ctx.moveTo(x, 0);
                                ctx.lineTo(x + size.x, size.y);
                                ctx.stroke();
                            }

                            ctx.strokeStyle = 'rgba(255, 255, 255, 0.35)';
                            ctx.lineWidth = 1;

                            for (let x = 0; x <= size.x; x += 64) {
                                ctx.beginPath();
                                ctx.moveTo(x, 0);
                                ctx.lineTo(x, size.y);
                                ctx.stroke();
                            }

                            for (let y = 0; y <= size.y; y += 64) {
                                ctx.beginPath();
                                ctx.moveTo(0, y);
                                ctx.lineTo(size.x, y);
                                ctx.stroke();
                            }

                            ctx.strokeStyle = 'rgba(27, 94, 32, 0.08)';
                            ctx.lineWidth = 3;

                            ctx.beginPath();
                            ctx.moveTo(0, size.y * 0.35);
                            ctx.lineTo(size.x, size.y * 0.25);
                            ctx.stroke();

                            ctx.beginPath();
                            ctx.moveTo(0, size.y * 0.72);
                            ctx.lineTo(size.x, size.y * 0.62);
                            ctx.stroke();

                            return tile;
                        }
                    });

                    new MapaLocal({
                        tileSize: 256,
                        minZoom: 0,
                        maxZoom: 28,
                        attribution: 'Mapa local'
                    }).addTo(map);

                    L.tileLayer(
                        'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                        {
                            maxZoom: 28,
                            maxNativeZoom: 18,
                            attribution: internetDisponible ? 'Tiles © Esri' : 'Mapa en cache',
                            opacity: 1,
                            errorTileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lCjQ9wAAAABJRU5ErkJggg=='
                        }
                    ).addTo(map);
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

                /*
                 * El toque al mapa NO define las coordenadas del punto.
                 * Solamente confirma que la persona desea capturar un punto.
                 * La posición usada es siempre currentUserLocation, recibida del GPS de Android.
                 */
                function seleccionarDesdeMapa() {
                    if (modoVistaPrevia) {
                        if (window.Android && Android.onPuntoLibreSeleccionado) {
                            Android.onPuntoLibreSeleccionado('0', '0');
                        }
                        return;
                    }

                    if (currentUserLocation == null) {
                        alert('Aún no se obtiene tu ubicación GPS. Espera unos segundos e inténtalo de nuevo.');
                        return;
                    }

                    const latGps = Number(currentUserLocation.lat);
                    const lonGps = Number(currentUserLocation.lon);

                    if (isNaN(latGps) || isNaN(lonGps)) {
                        alert('La ubicación GPS no es válida todavía. Inténtalo nuevamente.');
                        return;
                    }

                    if (!puntoDentroPoligono(latGps, lonGps)) {
                        alert('Tu ubicación GPS actual está fuera de la parcela. Acércate al área verde para registrar el punto.');
                        return;
                    }

                    setSelectedFreePoint(latGps, lonGps);

                    if (window.Android && Android.onPuntoLibreSeleccionado) {
                        Android.onPuntoLibreSeleccionado(String(latGps), String(lonGps));
                    }
                }

                window.updateUserLocation = function(lat, lon, accuracy) {
                    if (map == null) return;

                    const latNum = Number(lat);
                    const lonNum = Number(lon);
                    const accuracyNum = Number(accuracy);

                    if (isNaN(latNum) || isNaN(lonNum)) return;

                    const radioPrecision = isNaN(accuracyNum)
                        ? 8
                        : Math.max(3, Math.min(60, accuracyNum));

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
                        radius: radioPrecision,
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

                    const textoPrecision = isNaN(accuracyNum)
                        ? 'Tu ubicación actual'
                        : 'Tu ubicación · precisión aprox. ' + Math.round(accuracyNum) + ' m';

                    userMarker.bindTooltip(textoPrecision, {
                        permanent: false,
                        direction: 'top'
                    });
                };

                function agregarTituloMapa() {
                    const control = L.control({ position: 'topright' });

                    control.onAdd = function() {
                        const div = L.DomUtil.create('div', 'map-title-box');
                        div.innerHTML = 'Toca el mapa para confirmar tu ubicación GPS';
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
                    const controlPlagas = L.control({
                        position: 'bottomleft'
                    });

                    controlPlagas.onAdd = function() {
                        const div = L.DomUtil.create('div', 'legend-fija');

                        div.innerHTML =
                            '<div class="legend-title">P · Plagas</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#16A34A"></span>Sin plaga</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#FACC15"></span>Severidad menor</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#F97316"></span>Severidad mayor</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#DC2626"></span>Severidad alta</div>';

                        L.DomEvent.disableClickPropagation(div);
                        L.DomEvent.disableScrollPropagation(div);
                        return div;
                    };

                    controlPlagas.addTo(map);

                    const controlEnfermedades = L.control({
                        position: 'bottomright'
                    });

                    controlEnfermedades.onAdd = function() {
                        const div = L.DomUtil.create('div', 'legend-fija');

                        div.innerHTML =
                            '<div class="legend-title">E · Enfermedades</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#16A34A"></span>Sin presencia</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#FACC15"></span>Baja</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#F97316"></span>Media</div>' +
                            '<div class="legend-row"><span class="dot" style="background:#DC2626"></span>Alta</div>';

                        L.DomEvent.disableClickPropagation(div);
                        L.DomEvent.disableScrollPropagation(div);
                        return div;
                    };

                    controlEnfermedades.addTo(map);
                }

                function renderFallback() {
                    const mapDiv = document.getElementById('map');
                    mapDiv.innerHTML = '<div class="error-box"><b>Sin mapa interactivo</b><br>No se pudo cargar Leaflet.</div>';
                }

                function agregarPuntoMonitoreado(p, index) {
                    /*
                     * El radio queda neutral porque el marcador ahora representa
                     * dos estados independientes: plaga y enfermedad.
                     */
                    if (puntoEstaCompletado(p)) {
                        L.circle([p.lat, p.lon], {
                            radius: Math.max(3, p.radius || 4),
                            color: '#334155',
                            weight: 1,
                            opacity: 0.35,
                            fillColor: '#FFFFFF',
                            fillOpacity: 0.06,
                            interactive: false
                        }).addTo(map);
                    }

                    let marker;

                    if (puntoEstaCompletado(p)) {
                        marker = L.marker(
                            [p.lat, p.lon],
                            {
                                icon: crearIconoPuntoDividido(p),
                                keyboard: false,
                                riseOnHover: true,
                                riseOffset: 500
                            }
                        ).addTo(map);
                    } else {
                        const colorEstado = puntoEstaCancelado(p)
                            ? '#6B7280'
                            : '#D98A00';

                        marker = L.circleMarker(
                            [p.lat, p.lon],
                            {
                                radius: 9,
                                color: '#FFFFFF',
                                weight: 3,
                                fillColor: colorEstado,
                                fillOpacity: 0.98
                            }
                        ).addTo(map);
                    }

                    marker.bindTooltip(
                        'Punto ' + (index + 1) +
                        '<br>' +
                        textoPunto(p),
                        {
                            permanent: false,
                            direction: 'top',
                            opacity: 0.97
                        }
                    );

                    marker.on('click', function(e) {
                        if (e && e.originalEvent) {
                            L.DomEvent.stopPropagation(
                                e.originalEvent
                            );
                        }

                        if (modoVistaPrevia) {
                            if (window.Android && Android.onPuntoLibreSeleccionado) {
                                Android.onPuntoLibreSeleccionado('0', '0');
                            }
                            return;
                        }

                        if (puntoEstaCompletado(p)) {
                            alert(
                                'Punto ' + (index + 1) +
                                ' capturado.\\n\\n' +
                                'Plaga (P): ' +
                                (p.plagaTexto || 'Sin evaluar') +
                                '\\n' +
                                'Enfermedad (E): ' +
                                (p.enfermedadTexto || 'Sin evaluar')
                            );
                            return;
                        }

                        if (puntoEstaCancelado(p)) {
                            alert('Este punto está cancelado.');
                            return;
                        }

                        alert('Este punto está pendiente.');
                    });
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
                        maxZoom: 28,
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

                        /*
                         * El polígono también debe aceptar toques. Antes tenía un
                         * bindPopup(), por lo que el toque abría este globo y no
                         * llegaba a la selección del punto. Aquí lo mandamos directo
                         * al mismo flujo de confirmación que usa el mapa.
                         */
                        polygon.on('click', function(e) {
                            if (e && e.originalEvent) {
                                L.DomEvent.stopPropagation(e.originalEvent);
                            }

                            seleccionarDesdeMapa();
                        });

                        const boundsParcela = polygon.getBounds();
                        aplicarLimitesDeParcela(boundsParcela);
                        map.fitBounds(boundsParcela, {
                            padding: [18, 18],
                            maxZoom: 18
                        });
                    } else if (puntos.length > 0) {
                        const p0 = normalizarPunto(puntos[0]);
                        map.setView([p0.lat, p0.lon], 18);
                    } else if (usuarioInicial != null) {
                        map.setView([usuarioInicial.lat, usuarioInicial.lon], 18);
                    }

                    puntos.map(normalizarPunto).forEach(function(p, index) {
                        agregarPuntoMonitoreado(p, index);
                    });

                    map.on('click', function(e) {
                        seleccionarDesdeMapa();
                    });

                    if (usuarioInicial != null) {
                        window.updateUserLocation(usuarioInicial.lat, usuarioInicial.lon);
                    }

                    agregarTituloMapa();

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

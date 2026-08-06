package com.example.myapplication.local.aspersion.map

import com.example.myapplication.local.aspersion.ui.AspersionLayer
import com.example.myapplication.local.aspersion.ui.AspersionLegendItem
import com.example.myapplication.local.aspersion.ui.bucketForPoint
import com.example.myapplication.local.aspersion.ui.colorForAspersionPoint
import com.example.myapplication.local.aspersion.ui.formatNumber
import com.example.myapplication.local.aspersion.ui.valueForLayer
import com.example.myapplication.local.entities.LocalAspersionPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import org.json.JSONArray
import org.json.JSONObject

internal fun createAspersionMapHtml(
    points: List<LocalAspersionPointEntity>,
    vertices: List<LocalPlotVertexEntity>,
    layer: AspersionLayer,
    legendItems: List<AspersionLegendItem>,
    plotName: String,
    internetAvailable: Boolean
): String {
    val pointsJson = JSONArray().apply {
        points.forEach { point ->
            put(
                JSONObject().apply {
                    put("id", point.pointId)
                    put("lat", point.latitude)
                    put("lon", point.longitude)
                    put("heading", point.courseDeg ?: point.vehicleHeading ?: 0.0)
                    put("width", point.boomWidthM ?: 0.0)
                    put("height", point.distanceM ?: 0.0)
                    put("color", colorForAspersionPoint(point, layer, legendItems))
                    put("bucket", bucketForPoint(point, layer, legendItems))
                    put("layerValue", valueForLayer(point, layer) ?: JSONObject.NULL)
                    put("applied", point.appliedRateL ?: JSONObject.NULL)
                    put("target", point.targetRateL ?: JSONObject.NULL)
                    put("quality", point.rateQuality ?: JSONObject.NULL)
                    put("area", point.areaHa ?: JSONObject.NULL)
                    put("speed", point.speedKmh ?: JSONObject.NULL)
                    put("flow", point.liquidFlowLs ?: JSONObject.NULL)
                    put("pressure", point.boomPressureBar ?: JSONObject.NULL)
                    put("productivity", point.productionHah ?: JSONObject.NULL)
                }
            )
        }
    }

    val verticesJson = JSONArray().apply {
        vertices.sortedBy(LocalPlotVertexEntity::level).forEach { vertex ->
            put(
                JSONArray().apply {
                    put(vertex.lat)
                    put(vertex.lon)
                }
            )
        }
    }

    val layerLabel = JSONObject.quote(layer.label)
    val layerUnit = JSONObject.quote(layer.unit)
    val layerKey = JSONObject.quote(layer.key)
    val plotNameJson = JSONObject.quote(plotName)
    val internetJson = if (internetAvailable) "true" else "false"

    return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="utf-8" />
            <meta
                name="viewport"
                content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes"
            />
            <link rel="stylesheet" href="file:///android_asset/leaflet/leaflet.css" />
            <style>
                html, body, #map {
                    position: fixed;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    background: #dfe8d1;
                    font-family: Arial, sans-serif;
                }
                .leaflet-container { background: #dfe8d1; }
                .leaflet-control-attribution { font-size: 9px; }
                .map-message {
                    position: absolute;
                    z-index: 9999;
                    left: 14px;
                    right: 14px;
                    top: 14px;
                    padding: 12px;
                    border-radius: 12px;
                    background: rgba(255,255,255,.94);
                    color: #1f3d2a;
                    box-shadow: 0 3px 14px rgba(0,0,0,.22);
                    font-size: 13px;
                    line-height: 18px;
                    text-align: center;
                }
                .popup {
                    min-width: 176px;
                    color: #263238;
                    font-size: 12px;
                    line-height: 17px;
                }
                .popup b { color: #123d1f; }
                .popup .coords { color: #64748b; margin-bottom: 4px; }
                .popup .category {
                    display: inline-block;
                    margin-top: 4px;
                    padding: 2px 7px;
                    border-radius: 999px;
                    color: white;
                    font-weight: 700;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script src="file:///android_asset/leaflet/leaflet.js"></script>
            <script>
                const points = $pointsJson;
                const vertices = $verticesJson;
                const layerLabel = $layerLabel;
                const layerUnit = $layerUnit;
                const layerKey = $layerKey;
                const plotName = $plotNameJson;
                const internetAvailable = $internetJson;
                const METERS_PER_DEGREE_LAT = 111320;

                let map = null;

                window.ajustarMapaMonitoreo = function() {
                    if (map) map.invalidateSize(true);
                };

                function safe(value) {
                    return String(value == null ? '' : value)
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#039;');
                }

                function number(value, decimals) {
                    const n = Number(value);
                    return Number.isFinite(n) ? n.toFixed(decimals) : '—';
                }

                function rectangleRing(point) {
                    const width = Number(point.width);
                    const height = Number(point.height);
                    if (!(width > 0) || !(height > 0)) return null;

                    const lat = Number(point.lat);
                    const lon = Number(point.lon);
                    const heading = Number(point.heading || 0) * Math.PI / 180;
                    const sin = Math.sin(heading);
                    const cos = Math.cos(heading);
                    const metersPerDegreeLon =
                        METERS_PER_DEGREE_LAT * Math.cos(lat * Math.PI / 180);
                    const halfWidth = width / 2;
                    const halfHeight = height / 2;
                    const corners = [
                        [-halfWidth, -halfHeight],
                        [ halfWidth, -halfHeight],
                        [ halfWidth,  halfHeight],
                        [-halfWidth,  halfHeight]
                    ];

                    return corners.map(function(corner) {
                        const x = corner[0];
                        const y = corner[1];
                        const east = x * cos + y * sin;
                        const north = -x * sin + y * cos;
                        return [
                            lat + north / METERS_PER_DEGREE_LAT,
                            lon + east / metersPerDegreeLon
                        ];
                    });
                }

                function layerValueText(point) {
                    if (layerKey === 'application') {
                        return number(point.layerValue, 1) + '%';
                    }
                    if (layerKey === 'rate_quality') {
                        return safe(point.quality || 'Sin dato');
                    }
                    return number(point.layerValue, 3) +
                        (layerUnit ? ' ' + safe(layerUnit) : '');
                }

                function popupHtml(point) {
                    const pressurePsi = Number(point.pressure) * 14.538;
                    let extra = '';
                    if (layerKey === 'application') {
                        extra =
                            '<div>Aplicado: <b>' + number(point.applied, 1) + ' L/ha</b></div>' +
                            '<div>Meta: <b>' + number(point.target, 1) + ' L/ha</b></div>';
                    } else if (layerKey === 'boom_pressure' && Number.isFinite(pressurePsi)) {
                        extra = '<div>Equivalencia: <b>' + pressurePsi.toFixed(2) + ' PSI</b></div>';
                    }

                    return '<div class="popup">' +
                        '<div class="coords">' + number(point.lat, 6) + ', ' +
                            number(point.lon, 6) + '</div>' +
                        '<div>' + safe(layerLabel) + ': <b>' +
                            layerValueText(point) + '</b></div>' +
                        extra +
                        '<div>Área del punto: <b>' + number(point.area, 6) + ' ha</b></div>' +
                        '<span class="category" style="background:' + safe(point.color) + '">' +
                            safe(point.bucket.replace(/_/g, ' ')) +
                        '</span>' +
                    '</div>';
                }

                try {
                    map = L.map('map', {
                        zoomControl: true,
                        preferCanvas: true,
                        maxZoom: 20
                    });

                    L.tileLayer(
                        'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                        {
                            maxZoom: 20,
                            attribution: 'Esri'
                        }
                    ).addTo(map);

                    const bounds = [];

                    if (vertices.length >= 3) {
                        L.polygon(vertices, {
                            color: '#16a34a',
                            weight: 2,
                            fillColor: '#22c55e',
                            fillOpacity: 0.08,
                            interactive: false
                        }).addTo(map);
                        vertices.forEach(function(vertex) { bounds.push(vertex); });
                    }

                    points.forEach(function(point) {
                        const ring = rectangleRing(point);
                        if (!ring) return;

                        const polygon = L.polygon(ring, {
                            color: point.color,
                            weight: 0.4,
                            fillColor: point.color,
                            fillOpacity: 0.82
                        }).addTo(map);
                        polygon.bindPopup(popupHtml(point), {
                            maxWidth: 260
                        });
                        ring.forEach(function(position) { bounds.push(position); });
                    });

                    if (bounds.length > 0) {
                        map.fitBounds(bounds, {
                            padding: [18, 18],
                            maxZoom: 19
                        });
                    } else {
                        map.setView([20.5, -101.0], 6);
                        const message = document.createElement('div');
                        message.className = 'map-message';
                        message.innerHTML = '<b>Sin puntos visibles</b><br>' +
                            'Activa una categoría o sincroniza esta aspersión.';
                        document.body.appendChild(message);
                    }

                    L.control.scale({
                        imperial: false,
                        position: 'bottomleft'
                    }).addTo(map);

                    setTimeout(function() {
                        map.invalidateSize(true);
                    }, 180);
                } catch (error) {
                    const message = document.createElement('div');
                    message.className = 'map-message';
                    message.innerHTML = '<b>No se pudo dibujar el mapa.</b><br>' +
                        safe(error && error.message ? error.message : error);
                    document.body.appendChild(message);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

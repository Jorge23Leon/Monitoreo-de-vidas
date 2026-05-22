package com.example.myapplication.local.map



import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MapaPoligonoParcela(
    modifier: Modifier = Modifier,
    vertices: List<LocalPlotVertexEntity>,
    titulo: String = "Vista satelital del polígono"
) {
    val html = crearHtmlMapaPoligonoParcela(
        vertices = vertices,
        titulo = titulo
    )

    AndroidView<View>(
        modifier = modifier,
        factory = { context ->
            try {
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    setBackgroundColor(AndroidColor.WHITE)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    tag = html

                    loadDataWithBaseURL(
                        "file:///android_asset/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            } catch (e: Throwable) {
                TextView(context).apply {
                    text = "No se pudo abrir el mapa.\n\n${e.message}"
                    setTextColor(android.graphics.Color.RED)
                    setPadding(24, 24, 24, 24)
                }
            }
        },
        update = { view ->
            if (view is WebView && view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

private fun crearHtmlMapaPoligonoParcela(
    vertices: List<LocalPlotVertexEntity>,
    titulo: String
): String {
    val verticesJson = crearVerticesPoligonoJson(vertices)

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
                    background: #ffffff;
                    font-family: Arial, sans-serif;
                }

                #map {
                    width: 100vw;
                    height: 100vh;
                    min-height: 260px;
                    border-radius: 16px;
                    overflow: hidden;
                    background: #eef5ec;
                }


                .vertex-label {
                    background: #1B5E20;
                    color: white;
                    border-radius: 50%;
                    width: 22px;
                    height: 22px;
                    line-height: 22px;
                    text-align: center;
                    font-size: 12px;
                    font-weight: bold;
                    border: 2px solid white;
                    box-shadow: 0 1px 5px rgba(0,0,0,0.35);
                }

                .error-box {
                    padding: 14px;
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
                const vertices = $verticesJson;

                function mostrarError(mensaje) {
                    document.body.innerHTML =
                        '<div class="error-box"><b>Error al cargar mapa:</b><br>' +
                        mensaje +
                        '</div>';
                }

                try {
                    if (typeof L === 'undefined') {
                        mostrarError('No se pudo cargar Leaflet. Revisa assets/leaflet/leaflet.js y leaflet.css');
                    } else {
                        const map = L.map('map', {
                            zoomControl: true,
                            preferCanvas: true
                        });

                        L.tileLayer(
                            'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                            {
                                maxZoom: 20,
                                attribution: 'Tiles © Esri'
                            }
                        ).addTo(map);

                        const polygonLatLng = vertices.map(v => [v.lat, v.lon]);

                        if (polygonLatLng.length > 0) {
                            const polygon = L.polygon(polygonLatLng, {
                                color: '#1B5E20',
                                weight: 4,
                                opacity: 1,
                                fillColor: '#7CB342',
                                fillOpacity: 0.32
                            }).addTo(map);

                            polygon.bindPopup(
                                '<b>Polígono de parcela</b><br>' +
                                'Vértices: ' + polygonLatLng.length
                            );

                            vertices.forEach((v, index) => {
                                L.circleMarker([v.lat, v.lon], {
                                    radius: 7,
                                    color: '#ffffff',
                                    weight: 3,
                                    fillColor: '#1B5E20',
                                    fillOpacity: 1
                                }).addTo(map);

                                L.marker([v.lat, v.lon], {
                                    icon: L.divIcon({
                                        className: '',
                                        html: '<div class="vertex-label">' + v.level + '</div>',
                                        iconSize: [22, 22],
                                        iconAnchor: [11, 11]
                                    })
                                }).addTo(map);
                            });

                            map.fitBounds(polygon.getBounds(), {
                                padding: [26, 26],
                                maxZoom: 19
                            });
                        } else {
                            map.setView([20.6767, -101.3563], 14);
                        }


                        setTimeout(function () {
                            map.invalidateSize();

                            if (polygonLatLng.length > 0) {
                                map.fitBounds(L.latLngBounds(polygonLatLng), {
                                    padding: [26, 26],
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

private fun crearVerticesPoligonoJson(
    vertices: List<LocalPlotVertexEntity>
): String {
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
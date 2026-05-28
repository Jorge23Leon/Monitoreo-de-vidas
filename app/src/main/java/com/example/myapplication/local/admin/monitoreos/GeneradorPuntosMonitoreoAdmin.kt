package com.example.myapplication.local.admin.monitoreos

import com.example.myapplication.local.entities.LocalPlotVertexEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AdminGeneratedPoint(
    val lat: Double,
    val lon: Double
)

private data class AdminMetricPoint(
    val x: Double,
    val y: Double,
    val lat: Double,
    val lon: Double
)

private data class AdminCandidatePoint(
    val punto: AdminGeneratedPoint,
    val x: Double,
    val y: Double,
    val fila: Int,
    val columna: Int
)

fun generarPuntosDentroDelPoligonoAdmin(
    vertices: List<LocalPlotVertexEntity>,
    cantidad: Int
): List<AdminGeneratedPoint> {
    if (vertices.size < 3 || cantidad <= 0) return emptyList()

    val poligono = vertices
        .sortedBy { it.level }
        .map { AdminGeneratedPoint(lat = it.lat, lon = it.lon) }

    val refLat = poligono.map { it.lat }.average()
    val refLon = poligono.map { it.lon }.average()
    val metrosPorGradoLat = 111_320.0
    val metrosPorGradoLon = max(
        111_320.0 * cos(Math.toRadians(refLat)),
        1.0
    )

    val poligonoMetros = poligono.map { punto ->
        AdminMetricPoint(
            x = (punto.lon - refLon) * metrosPorGradoLon,
            y = (punto.lat - refLat) * metrosPorGradoLat,
            lat = punto.lat,
            lon = punto.lon
        )
    }

    val direccion = calcularDireccionPrincipalAdmin(poligonoMetros)
    val ux = direccion.first
    val uy = direccion.second
    val vx = -uy
    val vy = ux

    val rotados = poligonoMetros.map { p ->
        val s = p.x * ux + p.y * uy
        val t = p.x * vx + p.y * vy
        s to t
    }

    val minS = rotados.minOf { it.first }
    val maxS = rotados.maxOf { it.first }
    val minT = rotados.minOf { it.second }
    val maxT = rotados.maxOf { it.second }

    val largo = max(maxS - minS, 1.0)
    val ancho = max(maxT - minT, 1.0)
    val area = max(calcularAreaPoligonoMetrosAdmin(poligonoMetros), largo * ancho * 0.35)

    val separacionBase = sqrt(area / cantidad.toDouble()).coerceAtLeast(1.0)

    var columnasBase = (largo / separacionBase).roundToInt().coerceAtLeast(1)
    var filasBase = ceil(cantidad.toDouble() / columnasBase.toDouble()).toInt().coerceAtLeast(1)

    val proporcion = (largo / ancho).coerceIn(0.20, 8.0)
    columnasBase = max(columnasBase, ceil(sqrt(cantidad * proporcion)).toInt()).coerceAtLeast(1)
    filasBase = ceil(cantidad.toDouble() / columnasBase.toDouble()).toInt().coerceAtLeast(1)

    val candidatos = mutableListOf<AdminCandidatePoint>()
    var expansion = 0

    while (candidatos.size < cantidad && expansion <= 30) {
        candidatos.clear()

        val filas = (filasBase + expansion / 2).coerceAtLeast(1)
        val columnas = (ceil(cantidad.toDouble() / filas.toDouble()).toInt() + expansion).coerceAtLeast(1)

        val pasoS = largo / (columnas + 1)
        val pasoT = ancho / (filas + 1)

        for (fila in 0 until filas) {
            val t = minT + pasoT * (fila + 1)
            val desfase = if (fila % 2 == 0) 0.0 else pasoS / 2.0

            for (columna in 0 until columnas) {
                val s = minS + pasoS * (columna + 1) + desfase
                if (s > maxS) continue

                val x = ux * s + vx * t
                val y = uy * s + vy * t

                val lat = refLat + (y / metrosPorGradoLat)
                val lon = refLon + (x / metrosPorGradoLon)
                val punto = AdminGeneratedPoint(lat = lat, lon = lon)

                if (puntoDentroDelPoligonoAdmin(punto, poligono)) {
                    candidatos.add(
                        AdminCandidatePoint(
                            punto = punto,
                            x = x,
                            y = y,
                            fila = fila,
                            columna = columna
                        )
                    )
                }
            }
        }

        expansion++
    }

    val seleccionados = seleccionarPuntosLinealesPorDistanciaAdmin(
        candidatos = candidatos,
        cantidad = cantidad
    )

    if (seleccionados.size >= cantidad) {
        return seleccionados
    }

    val extra = generarPuntosDensosDeRespaldoAdmin(
        poligono = poligono,
        poligonoMetros = poligonoMetros,
        refLat = refLat,
        refLon = refLon,
        metrosPorGradoLat = metrosPorGradoLat,
        metrosPorGradoLon = metrosPorGradoLon,
        cantidad = cantidad * 4
    )

    return (seleccionados + extra)
        .distinctBy { "${"%.7f".format(Locale.US, it.lat)}_${"%.7f".format(Locale.US, it.lon)}" }
        .let { puntos -> seleccionarPuntosPorDistanciaMaximaAdmin(puntos, cantidad) }
}

private fun generarPuntosDensosDeRespaldoAdmin(
    poligono: List<AdminGeneratedPoint>,
    poligonoMetros: List<AdminMetricPoint>,
    refLat: Double,
    refLon: Double,
    metrosPorGradoLat: Double,
    metrosPorGradoLon: Double,
    cantidad: Int
): List<AdminGeneratedPoint> {
    if (cantidad <= 0) return emptyList()

    val minX = poligonoMetros.minOf { it.x }
    val maxX = poligonoMetros.maxOf { it.x }
    val minY = poligonoMetros.minOf { it.y }
    val maxY = poligonoMetros.maxOf { it.y }

    val ancho = max(maxX - minX, 1.0)
    val alto = max(maxY - minY, 1.0)
    val proporcion = (ancho / alto).coerceIn(0.25, 4.0)
    val columnas = ceil(sqrt(cantidad * proporcion)).toInt().coerceAtLeast(2)
    val filas = ceil(cantidad.toDouble() / columnas.toDouble()).toInt().coerceAtLeast(2)

    val puntos = mutableListOf<AdminGeneratedPoint>()

    for (fila in 0 until filas) {
        val y = minY + ((alto / (filas + 1)) * (fila + 1))
        val desfase = if (fila % 2 == 0) 0.0 else (ancho / (columnas + 1)) / 2.0

        for (columna in 0 until columnas) {
            val x = minX + ((ancho / (columnas + 1)) * (columna + 1)) + desfase
            if (x > maxX) continue

            val lat = refLat + (y / metrosPorGradoLat)
            val lon = refLon + (x / metrosPorGradoLon)
            val punto = AdminGeneratedPoint(lat = lat, lon = lon)

            if (puntoDentroDelPoligonoAdmin(punto, poligono)) {
                puntos.add(punto)
            }
        }
    }

    return puntos
}

private fun seleccionarPuntosPorDistanciaMaximaAdmin(
    puntos: List<AdminGeneratedPoint>,
    cantidad: Int
): List<AdminGeneratedPoint> {
    if (puntos.isEmpty() || cantidad <= 0) return emptyList()
    if (puntos.size <= cantidad) return puntos

    val seleccionados = mutableListOf<AdminGeneratedPoint>()
    val centroLat = puntos.map { it.lat }.average()
    val centroLon = puntos.map { it.lon }.average()

    val primerPunto = puntos.minBy { hypot(it.lat - centroLat, it.lon - centroLon) }
    seleccionados.add(primerPunto)

    while (seleccionados.size < cantidad) {
        val siguiente = puntos
            .filter { it !in seleccionados }
            .maxByOrNull { candidato ->
                seleccionados.minOf { seleccionado ->
                    hypot(candidato.lat - seleccionado.lat, candidato.lon - seleccionado.lon)
                }
            } ?: break

        seleccionados.add(siguiente)
    }

    return seleccionados
}

private fun calcularDireccionPrincipalAdmin(
    poligono: List<AdminMetricPoint>
): Pair<Double, Double> {
    if (poligono.size < 2) return 1.0 to 0.0

    var mejorDx = 1.0
    var mejorDy = 0.0
    var mayorDistancia = 0.0

    for (i in poligono.indices) {
        val actual = poligono[i]
        val siguiente = poligono[(i + 1) % poligono.size]
        val dx = siguiente.x - actual.x
        val dy = siguiente.y - actual.y
        val distancia = hypot(dx, dy)

        if (distancia > mayorDistancia) {
            mayorDistancia = distancia
            mejorDx = dx
            mejorDy = dy
        }
    }

    if (mayorDistancia <= 0.0) return 1.0 to 0.0

    return (mejorDx / mayorDistancia) to (mejorDy / mayorDistancia)
}

private fun calcularAreaPoligonoMetrosAdmin(
    poligono: List<AdminMetricPoint>
): Double {
    if (poligono.size < 3) return 0.0

    var suma = 0.0

    for (i in poligono.indices) {
        val actual = poligono[i]
        val siguiente = poligono[(i + 1) % poligono.size]
        suma += actual.x * siguiente.y - siguiente.x * actual.y
    }

    return abs(suma) / 2.0
}

private fun seleccionarPuntosLinealesPorDistanciaAdmin(
    candidatos: List<AdminCandidatePoint>,
    cantidad: Int
): List<AdminGeneratedPoint> {
    if (candidatos.isEmpty() || cantidad <= 0) return emptyList()

    val ordenados = candidatos
        .distinctBy {
            "${"%.7f".format(Locale.US, it.punto.lat)}_${"%.7f".format(Locale.US, it.punto.lon)}"
        }
        .sortedWith(compareBy<AdminCandidatePoint> { it.fila }.thenBy { it.columna })

    if (ordenados.size <= cantidad) {
        return ordenados.map { it.punto }
    }

    if (cantidad == 1) {
        val centroX = ordenados.map { it.x }.average()
        val centroY = ordenados.map { it.y }.average()

        return listOf(
            ordenados.minBy { candidato ->
                hypot(candidato.x - centroX, candidato.y - centroY)
            }.punto
        )
    }

    val resultado = mutableListOf<AdminGeneratedPoint>()
    val salto = (ordenados.size - 1).toDouble() / (cantidad - 1).toDouble()

    for (i in 0 until cantidad) {
        val indice = (i * salto).roundToInt().coerceIn(0, ordenados.lastIndex)
        val punto = ordenados[indice].punto

        if (punto !in resultado) {
            resultado.add(punto)
        }
    }

    var cursor = 0
    while (resultado.size < cantidad && cursor < ordenados.size) {
        val punto = ordenados[cursor].punto
        if (punto !in resultado) resultado.add(punto)
        cursor++
    }

    return resultado.take(cantidad)
}

private fun puntoDentroDelPoligonoAdmin(
    punto: AdminGeneratedPoint,
    poligono: List<AdminGeneratedPoint>
): Boolean {
    var dentro = false
    var j = poligono.size - 1

    for (i in poligono.indices) {
        val pi = poligono[i]
        val pj = poligono[j]

        val cruza = ((pi.lon > punto.lon) != (pj.lon > punto.lon)) &&
                (punto.lat < (pj.lat - pi.lat) * (punto.lon - pi.lon) / (pj.lon - pi.lon) + pi.lat)

        if (cruza) dentro = !dentro
        j = i
    }

    return dentro
}

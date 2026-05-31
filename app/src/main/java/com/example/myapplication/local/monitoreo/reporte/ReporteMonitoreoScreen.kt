package com.example.myapplication.local.monitoreo.reporte

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UNUSED_PARAMETER")
@Composable
fun ReporteMonitoreoScreen(
    database: AppDatabase,
    nombreUsuario: String,
    rolUsuario: String = "",
    nombreCia: String,
    header: LocalPhytomonitoringHeaderEntity,
    programa: LocalProgramEntity?,
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?,
    onBackClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)? = null,
    onCerrarSesionClick: () -> Unit,
    onPerfilClick: () -> Unit = {},
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current

    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var puntos by remember { mutableStateOf<List<LocalPhytomonitoringTargetPointEntity>>(emptyList()) }
    var checkpoints by remember { mutableStateOf<List<LocalPhytomonitoringCheckpointEntity>>(emptyList()) }
    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }
    var catalogo by remember { mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList()) }
    var nombreCultivo by remember { mutableStateOf("Cultivo no identificado") }
    var fotoCultivo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(header.idHeader) {
        cargando = true
        error = null

        try {
            val data = withContext(Dispatchers.IO) {
                val puntosDb = database.LocalPhytomonitoringTargetPointDao()
                    .getTargetPointsByHeader(header.idHeader)

                val checkpointsDb = database.localphytomonitoringcheckpointDao()
                    .getCheckpointsByHeader(header.idHeader)

                val verticesDb = database.LocalPlotVertexDao()
                    .getVerticesByPlot(header.idLocalPlot)

                val catalogoDb = database.localphytosanitarycatalogDao()
                    .getAllCatalogo()

                val cultivoDb = database.localCropCatalogDao()
                    .getCropById(header.idCrop)

                ReporteDataUi(
                    puntos = puntosDb,
                    checkpoints = checkpointsDb,
                    vertices = verticesDb,
                    catalogo = catalogoDb,
                    cultivo = cultivoDb?.name ?: "Cultivo no identificado",
                    fotoCultivo = cultivoDb?.photo
                )
            }

            puntos = data.puntos
            checkpoints = data.checkpoints
            vertices = data.vertices
            catalogo = data.catalogo
            nombreCultivo = data.cultivo
            fotoCultivo = data.fotoCultivo
        } catch (e: Exception) {
            e.printStackTrace()
            error = "Error al cargar reporte: ${e.javaClass.simpleName} - ${e.message}"
        } finally {
            cargando = false
        }
    }

    val catalogoMap = remember(catalogo) {
        catalogo.associateBy { it.idPhytosanitary }
    }

    val puntosOrdenados = remember(puntos) {
        puntos.sortedBy { it.idTargetPoint }
    }

    val numeroPuntoMap = remember(puntosOrdenados) {
        puntosOrdenados.mapIndexed { index, punto ->
            punto.idTargetPoint to (index + 1)
        }.toMap()
    }

    val puntosMap = remember(puntos) {
        puntos.associateBy { it.idTargetPoint }
    }

    val puntosConCaptura = remember(checkpoints, puntos) {
        val idsValidos = puntos.map { it.idTargetPoint }.toSet()
        checkpoints
            .filter { it.idHeader == header.idHeader && it.idTargetPoint in idsValidos }
            .map { it.idTargetPoint }
            .toSet()
    }

    val totalPuntos = puntos.size
    val puntosCapturados = puntosConCaptura.size
    val puntosPendientes = (totalPuntos - puntosCapturados).coerceAtLeast(0)
    val porcentajeAvance = if (totalPuntos > 0) {
        ((puntosCapturados.toDouble() / totalPuntos.toDouble()) * 100.0).toInt()
    } else {
        0
    }

    var detalleSuperiorExpandido by remember { mutableStateOf(false) }

    val filasTabla = remember(checkpoints, puntosMap, catalogoMap, numeroPuntoMap) {
        checkpoints
            .sortedWith(
                compareBy<LocalPhytomonitoringCheckpointEntity> {
                    numeroPuntoMap[it.idTargetPoint] ?: 9999
                }.thenBy {
                    it.capturedAt ?: 0L
                }
            )
            .map { checkpoint ->
                val punto = puntosMap[checkpoint.idTargetPoint]
                val item = catalogoMap[checkpoint.idPhytosanitary]

                FilaReporteCapturaUi(
                    numeroPunto = numeroPuntoMap[checkpoint.idTargetPoint] ?: 0,
                    lat = punto?.lat,
                    lon = punto?.lon,
                    coordenadas = if (punto != null) {
                        "${punto.lat}, ${punto.lon}"
                    } else {
                        "-"
                    },
                    plagaEnfermedad = item?.name ?: "Sin identificar",
                    tipo = textoTipoCatalogo(item?.type),
                    fase = checkpoint.stage ?: "-",
                    cantidad = checkpoint.qty ?: 0,
                    fechaCaptura = formatearFechaOpcionalReporteUi(checkpoint.capturedAt),
                    notas = checkpoint.notes ?: ""
                )
            }
    }

    val htmlMapa = remember(vertices, puntos, checkpoints, catalogo) {
        crearHtmlMapaReporteUi(
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            catalogo = catalogo
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F8F1))
    ) {
        EncabezadoApp(
            nombreUsuario = nombreUsuario,
            rolUsuario = rolUsuario,
            onPerfilClick = onPerfilClick,
            onMonitoreosClick = onMonitoreosClick,
            onAdminClick = onAdminClick,
            onCambiarCiaClick = onCambiarCiaClick,
            onCerrarSesionClick = onCerrarSesionClick
        )

        when {
            cargando -> {
                PantallaEstadoReporte(
                    titulo = "Cargando reporte...",
                    mensaje = "Estamos preparando la información del monitoreo.",
                    color = Color(0xFF1B5E20)
                )
            }

            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No se pudo cargar el reporte",
                        color = Color(0xFFB00020),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = error ?: "Error desconocido",
                        color = Color.DarkGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HeroReporteCard(
                        idHeader = header.idHeader,
                        estado = textoEstadoReporteUi(header.status),
                        ciclo = programa?.cycle ?: header.cycle,
                        nombreCia = nombreCia,
                        productor = productor?.commercial_name ?: "-",
                        rancho = rancho?.name ?: "-",
                        parcela = parcela?.code ?: "-",
                        cultivo = nombreCultivo,
                        fotoCultivo = fotoCultivo,
                        fechaProgramada = formatearFechaReporteUi(header.estStartDate),
                        inicioReal = formatearFechaOpcionalReporteUi(header.startAt),
                        finalizado = formatearFechaOpcionalReporteUi(header.finishedAt),
                        porcentajeAvance = porcentajeAvance,
                        detalleExpandido = detalleSuperiorExpandido,
                        onToggleDetalle = { detalleSuperiorExpandido = !detalleSuperiorExpandido }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TarjetaResumenReporte(
                            titulo = "Puntos",
                            valor = totalPuntos.toString(),
                            detalle = "Asignados",
                            modifier = Modifier.weight(1f)
                        )

                        TarjetaResumenReporte(
                            titulo = "Capturados",
                            valor = puntosCapturados.toString(),
                            detalle = "$porcentajeAvance% avance",
                            modifier = Modifier.weight(1f)
                        )

                        TarjetaResumenReporte(
                            titulo = "Pendientes",
                            valor = puntosPendientes.toString(),
                            detalle = "Por revisar",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mapa satelital del reporte",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF123D1F)
                                    )

                                    Text(
                                        text = "Vista satelital, polígono, puntos monitoreados y puntos sin captura",
                                        fontSize = 12.sp,
                                        color = Color(0xFF5F6F64)
                                    )
                                }

                                ChipEstadoReporte(
                                    texto = "${vertices.size} vértices",
                                    colorFondo = Color(0xFFE8F5E9),
                                    colorTexto = Color(0xFF1B5E20)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            MapaReporteWebViewUi(
                                htmlMapa = htmlMapa,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFDDE8D6),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val ubicacionArchivo = descargarCsvReporteUi(
                                context = context,
                                header = header,
                                nombreCia = nombreCia,
                                productor = productor?.commercial_name ?: "-",
                                rancho = rancho?.name ?: "-",
                                parcela = parcela?.code ?: "-",
                                cultivo = nombreCultivo,
                                filas = filasTabla
                            )

                            Toast.makeText(
                                context,
                                "CSV descargado en: $ubicacionArchivo",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = "⬇ Descargar CSV del reporte",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Tabla de capturas",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF123D1F)
                            )

                            Text(
                                text = "Detalle de plagas, enfermedades, fases y cantidades capturadas.",
                                fontSize = 12.sp,
                                color = Color(0xFF5F6F64)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            TablaReporteCapturasUi(filas = filasTabla)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

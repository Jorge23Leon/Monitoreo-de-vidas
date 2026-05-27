package com.example.myapplication.local.monitoreo.reporte

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.os.Environment
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.common.ImageUriBox
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            onAdminClick = onAdminClick
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

@Composable
private fun PantallaEstadoReporte(
    titulo: String,
    mensaje: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = titulo,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = mensaje,
            color = Color.DarkGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HeroReporteCard(
    idHeader: Long,
    estado: String,
    ciclo: String,
    nombreCia: String,
    productor: String,
    rancho: String,
    parcela: String,
    cultivo: String,
    fotoCultivo: String?,
    fechaProgramada: String,
    inicioReal: String,
    finalizado: String,
    porcentajeAvance: Int,
    detalleExpandido: Boolean,
    onToggleDetalle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF123D1F),
                            Color(0xFF2E7D32),
                            Color(0xFF7CB342)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reporte fitosanitario",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            text = "Monitoreo #$idHeader",
                            color = Color(0xFFE8F5E9),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    ChipEstadoReporte(
                        texto = estado,
                        colorFondo = Color.White.copy(alpha = 0.92f),
                        colorTexto = Color(0xFF1B5E20)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImageUriBox(
                        photo = fotoCultivo,
                        fallbackIcon = "🌱",
                        sizeDp = 76
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Productor",
                            color = Color(0xFFE8F5E9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = productor,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Cultivo: $cultivo",
                            color = Color(0xFFE8F5E9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DatoPrincipalHeroReporte(
                        titulo = "Rancho",
                        valor = rancho,
                        modifier = Modifier.weight(1f)
                    )

                    DatoPrincipalHeroReporte(
                        titulo = "Parcela",
                        valor = parcela,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .clickable { onToggleDetalle() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Datos adicionales",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )

                            Text(
                                text = if (detalleExpandido) {
                                    "Toca para ocultar CIA, ciclo y fechas"
                                } else {
                                    "Toca para ver CIA, ciclo, cultivo y fechas"
                                },
                                color = Color(0xFFE8F5E9),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        ChipEstadoReporte(
                            texto = if (detalleExpandido) "Ocultar ▲" else "Ver más ▼",
                            colorFondo = Color.White.copy(alpha = 0.92f),
                            colorTexto = Color(0xFF1B5E20)
                        )
                    }

                    if (detalleExpandido) {
                        Spacer(modifier = Modifier.height(10.dp))
                        RowInfoHeroReporte("CIA", nombreCia)
                        RowInfoHeroReporte("Ciclo", ciclo)
                        RowInfoHeroReporte("Cultivo", cultivo)
                        RowInfoHeroReporte("Estado", estado)
                        RowInfoHeroReporte("Fecha programada", fechaProgramada)
                        RowInfoHeroReporte("Inicio real", inicioReal)
                        RowInfoHeroReporte("Finalizado", finalizado)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Avance del monitoreo",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "$porcentajeAvance%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(porcentajeAvance.coerceIn(0, 100) / 100f)
                                .height(10.dp)
                                .background(Color.White, RoundedCornerShape(20.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatoPrincipalHeroReporte(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = titulo,
            color = Color(0xFFE8F5E9),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Text(
            text = valor,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun RowInfoHeroReporte(titulo: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            color = Color(0xFFE8F5E9),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = valor,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.weight(1.35f)
        )
    }
}

@Composable
private fun TarjetaResumenReporte(
    titulo: String,
    valor: String,
    detalle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titulo,
                color = Color(0xFF5F6F64),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Text(
                text = valor,
                color = Color(0xFF123D1F),
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = detalle,
                color = Color(0xFF6D6D6D),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChipEstadoReporte(
    texto: String,
    colorFondo: Color,
    colorTexto: Color
) {
    Box(
        modifier = Modifier
            .background(colorFondo, RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = colorTexto,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun MapaReporteWebViewUi(
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

@Composable
private fun TablaReporteCapturasUi(filas: List<FilaReporteCapturaUi>) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
    ) {
        Row(
            modifier = Modifier
                .background(Color(0xFF1B5E20), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(vertical = 8.dp)
        ) {
            CeldaHeaderReporteUi("Punto", 70)
            CeldaHeaderReporteUi("Coordenadas", 190)
            CeldaHeaderReporteUi("Plaga / Enfermedad", 180)
            CeldaHeaderReporteUi("Tipo", 110)
            CeldaHeaderReporteUi("Fase", 120)
            CeldaHeaderReporteUi("Cantidad", 90)
            CeldaHeaderReporteUi("Fecha", 150)
        }

        if (filas.isEmpty()) {
            Text(
                text = "No hay capturas registradas para este monitoreo.",
                modifier = Modifier.padding(14.dp),
                color = Color.Gray,
                fontSize = 13.sp
            )
        } else {
            filas.forEachIndexed { index, fila ->
                val fondo = if (index % 2 == 0) Color(0xFFFAFCF9) else Color.White

                Row(
                    modifier = Modifier
                        .background(fondo)
                        .border(0.5.dp, Color(0xFFE0E7DE))
                        .padding(vertical = 7.dp)
                ) {
                    CeldaTextoReporteUi(fila.numeroPunto.toString(), 70)
                    CeldaTextoReporteUi(fila.coordenadas, 190)
                    CeldaTextoReporteUi(fila.plagaEnfermedad, 180)
                    CeldaTextoReporteUi(fila.tipo, 110)
                    CeldaTextoReporteUi(fila.fase, 120)
                    CeldaTextoReporteUi(fila.cantidad.toString(), 90)
                    CeldaTextoReporteUi(fila.fechaCaptura, 150)
                }
            }
        }
    }
}

@Composable
private fun CeldaHeaderReporteUi(texto: String, ancho: Int) {
    Text(
        text = texto,
        modifier = Modifier.width(ancho.dp),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CeldaTextoReporteUi(texto: String, ancho: Int) {
    Text(
        text = texto,
        modifier = Modifier
            .width(ancho.dp)
            .padding(horizontal = 5.dp),
        color = Color(0xFF2E2E2E),
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        maxLines = 3
    )
}

private fun crearHtmlMapaReporteUi(
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

            val capturasArray = JSONArray().apply {
                capturas.forEach { checkpoint ->
                    val item = catalogoMap[checkpoint.idPhytosanitary]
                    put(JSONObject().apply {
                        put("nombre", item?.name ?: "Sin identificar")
                        put("tipo", textoTipoCatalogo(item?.type))
                        put("fase", checkpoint.stage ?: "-")
                        put("cantidad", checkpoint.qty ?: 0)
                        put("fecha", formatearFechaOpcionalReporteUi(checkpoint.capturedAt))
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

                function colorEstado(status) {
                    if (status === 'completed') return '#2E7D32';
                    if (status === 'cancelled') return '#D32F2F';
                    return '#D32F2F';
                }

                function crearPopupPunto(p) {
                    let html = '<div class="popup-title">Punto ' + p.numero + '</div>';
                    html += '<div class="popup-row"><b>Estado:</b> ' + textoEstado(p.status) + '</div>';
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
                            txt += '\n\n' + c.nombre + '\nTipo: ' + c.tipo + '\nFase: ' + c.fase + '\nCantidad: ' + c.cantidad + '\nFecha: ' + c.fecha;
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
                        const color = colorEstado(p.status);
                        svg += '<circle cx="' + px + '" cy="' + py + '" r="3.4" fill="' + color + '" stroke="#1A1A1A" stroke-width="0.7" onclick="alert(\'' + escaparParaAlert(textoPlanoPunto(p)) + '\')" />';
                        svg += '<text x="' + px + '" y="' + (parseFloat(py) - 4.5).toFixed(2) + '" font-size="3.2" text-anchor="middle" fill="#123D1F" font-weight="bold">' + p.numero + '</text>';
                    });

                    svg += '</svg>';
                    svg += '<div class="legend" style="position:absolute;left:12px;bottom:12px;z-index:2"><div class="legend-title">Estados</div><span class="dot" style="background:#2E7D32"></span>Monitoreado<br><span class="dot" style="background:#D32F2F"></span>No monitoreado</div>';

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
                            preferCanvas: true
                        });
                        window.reporteMap = map;

                        const capaSatelital = L.tileLayer(
                            'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                            {
                                maxZoom: 20,
                                attribution: 'Tiles © Esri'
                            }
                        );
                        capaSatelital.addTo(map);

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
                                maxZoom: 19
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
                            const color = colorEstado(p.status);

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
                                '<div class="legend-title">Estados</div>' +
                                '<span class="dot" style="background:#2E7D32"></span>Monitoreado<br>' +
                                '<span class="dot" style="background:#D32F2F"></span>No monitoreado / Cancelado';
                            return div;
                        };
                        legend.addTo(map);

                        setTimeout(function() {
                            map.invalidateSize();
                            if (polygonLatLng.length > 0) {
                                map.fitBounds(L.latLngBounds(polygonLatLng), {
                                    padding: [30, 30],
                                    maxZoom: 19
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

private fun descargarCsvReporteUi(
    context: Context,
    header: LocalPhytomonitoringHeaderEntity,
    nombreCia: String,
    productor: String,
    rancho: String,
    parcela: String,
    cultivo: String,
    filas: List<FilaReporteCapturaUi>
): String {
    val nombreArchivo = "reporte_monitoreo_${header.idHeader}_${System.currentTimeMillis()}.csv"
    val contenido = crearContenidoCsvReporteUi(
        header = header,
        nombreCia = nombreCia,
        productor = productor,
        rancho = rancho,
        parcela = parcela,
        cultivo = cultivo,
        filas = filas
    )

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        guardarCsvEnDescargasMediaStore(
            context = context,
            nombreArchivo = nombreArchivo,
            contenido = contenido
        )
    } else {
        guardarCsvEnDescargasLegacy(
            context = context,
            nombreArchivo = nombreArchivo,
            contenido = contenido
        )
    }
}

private fun crearContenidoCsvReporteUi(
    header: LocalPhytomonitoringHeaderEntity,
    nombreCia: String,
    productor: String,
    rancho: String,
    parcela: String,
    cultivo: String,
    filas: List<FilaReporteCapturaUi>
): String {
    return buildString {
        appendLine("REPORTE DE MONITOREO FITOSANITARIO")
        appendLine("Campo,Valor")
        appendLine(listOf("ID Monitoreo", header.idHeader.toString()).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("CIA", nombreCia).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Productor", productor).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Rancho", rancho).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Parcela", parcela).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Cultivo", cultivo).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Estado", textoEstadoReporteUi(header.status)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Fecha programada", formatearFechaReporteUi(header.estStartDate)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Inicio real", formatearFechaOpcionalReporteUi(header.startAt)).joinToString(",") { escaparCsvUi(it) })
        appendLine(listOf("Finalizado", formatearFechaOpcionalReporteUi(header.finishedAt)).joinToString(",") { escaparCsvUi(it) })

        appendLine()
        appendLine("Punto,Latitud,Longitud,Coordenadas,Plaga o Enfermedad,Tipo,Fase,Cantidad,Fecha de captura,Notas")

        filas.forEach { fila ->
            appendLine(
                listOf(
                    fila.numeroPunto.toString(),
                    fila.lat?.toString() ?: "",
                    fila.lon?.toString() ?: "",
                    fila.coordenadas,
                    fila.plagaEnfermedad,
                    fila.tipo,
                    fila.fase,
                    fila.cantidad.toString(),
                    fila.fechaCaptura,
                    fila.notas
                ).joinToString(",") { escaparCsvUi(it) }
            )
        }
    }
}

private fun guardarCsvEnDescargasMediaStore(
    context: Context,
    nombreArchivo: String,
    contenido: String
): String {
    val resolver = context.contentResolver

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Monitoreos")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values)
        ?: throw IllegalStateException("No se pudo crear el archivo CSV en Descargas")

    try {
        resolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write(contenido)
            }
        } ?: throw IllegalStateException("No se pudo abrir el archivo CSV")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }

    return "Descargas/Monitoreos/$nombreArchivo"
}

private fun guardarCsvEnDescargasLegacy(
    context: Context,
    nombreArchivo: String,
    contenido: String
): String {
    return try {
        val carpeta = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!carpeta.exists()) carpeta.mkdirs()

        val archivo = File(carpeta, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)
        archivo.absolutePath
    } catch (_: Exception) {
        val carpetaApp = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!carpetaApp.exists()) carpetaApp.mkdirs()

        val archivo = File(carpetaApp, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)
        archivo.absolutePath
    }
}

private fun escaparCsvUi(valor: String): String {
    val limpio = valor.replace("\"", "\"\"")
    return "\"$limpio\""
}

private fun textoTipoCatalogo(type: String?): String {
    return when (type?.lowercase()?.trim()) {
        "plaga" -> "Plaga"
        "enfermedad" -> "Enfermedad"
        else -> type ?: "-"
    }
}

private fun textoEstadoReporteUi(status: String): String {
    return when (status.lowercase().trim()) {
        "pending", "pendiente" -> "Pendiente"
        "in_progress", "en proceso", "vigente" -> "En proceso"
        "completed", "completado", "finalizado" -> "Completado"
        "cancelled", "cancelado" -> "Cancelado"
        else -> status
    }
}

private fun formatearFechaReporteUi(fecha: Long?): String {
    if (fecha == null) return "No programada"
    return try {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(fecha))
    } catch (_: Exception) {
        "-"
    }
}

private fun formatearFechaOpcionalReporteUi(fecha: Long?): String {
    if (fecha == null) return "No registrado"

    return try {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(fecha))
    } catch (_: Exception) {
        "-"
    }
}

private data class ReporteDataUi(
    val puntos: List<LocalPhytomonitoringTargetPointEntity>,
    val checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    val vertices: List<LocalPlotVertexEntity>,
    val catalogo: List<LocalPhytosanitaryCatalogEntity>,
    val cultivo: String,
    val fotoCultivo: String?
)

private data class FilaReporteCapturaUi(
    val numeroPunto: Int,
    val lat: Double?,
    val lon: Double?,
    val coordenadas: String,
    val plagaEnfermedad: String,
    val tipo: String,
    val fase: String,
    val cantidad: Int,
    val fechaCaptura: String,
    val notas: String
)

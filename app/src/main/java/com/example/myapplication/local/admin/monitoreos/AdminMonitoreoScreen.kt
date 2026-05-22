package com.example.myapplication.local.admin.monitoreos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.app.DatePickerDialog
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun AdminMonitoreoScreen(
    database: AppDatabase,
    nombreUsuario: String,
    rolUsuario: String,
    idLocalCia: Long?,
    nombreCia: String,
    onBackClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit,
    onMensaje: (String) -> Unit,
    onMonitoreoCreado: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var cargandoInicial by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }

    var productores by remember { mutableStateOf<List<LocalAgroUnitEntity>>(emptyList()) }
    var ranchos by remember { mutableStateOf<List<LocalRanchEntity>>(emptyList()) }
    var parcelas by remember { mutableStateOf<List<LocalPlotEntity>>(emptyList()) }
    var cultivos by remember { mutableStateOf<List<LocalCropCatalogEntity>>(emptyList()) }
    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }

    var productorSeleccionado by remember { mutableStateOf<LocalAgroUnitEntity?>(null) }
    var ranchoSeleccionado by remember { mutableStateOf<LocalRanchEntity?>(null) }
    var parcelaSeleccionada by remember { mutableStateOf<LocalPlotEntity?>(null) }
    var cultivoSeleccionado by remember { mutableStateOf<LocalCropCatalogEntity?>(null) }

    var ciclo by remember { mutableStateOf("") }
    var fechaInicioMillis by remember { mutableStateOf<Long?>(null) }
    var fechaFinMillis by remember { mutableStateOf<Long?>(null) }
    var radioTexto by remember { mutableStateOf("20") }
    var cantidadPuntosTexto by remember { mutableStateOf("10") }

    LaunchedEffect(idLocalCia) {
        cargandoInicial = true
        try {
            val idCia = idLocalCia
            if (idCia == null || idCia <= 0L) {
                productores = emptyList()
                cultivos = emptyList()
                onMensaje("Selecciona una CIA antes de crear monitoreos")
            } else {
                val datos = withContext(Dispatchers.IO) {
                    val productoresCia = database.localCiaAgroUnitDao().getProductoresByCia(idCia)
                    val cultivosDb = database.localCropCatalogDao().getAllCrops()
                    productoresCia to cultivosDb
                }
                productores = datos.first
                cultivos = datos.second
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onMensaje("Error al cargar datos: ${e.message}")
        } finally {
            cargandoInicial = false
        }
    }

    fun cargarRanchos(productor: LocalAgroUnitEntity) {
        coroutineScope.launch {
            try {
                ranchos = withContext(Dispatchers.IO) {
                    database.localRanchDao().getRanchosByProductor(productor.idLocalAgroUnit)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar ranchos: ${e.message}")
            }
        }
    }

    fun cargarParcelas(rancho: LocalRanchEntity) {
        coroutineScope.launch {
            try {
                parcelas = withContext(Dispatchers.IO) {
                    database.localPlotDao().getParcelasByRancho(rancho.idLocalRanch)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar parcelas: ${e.message}")
            }
        }
    }

    fun cargarVertices(parcela: LocalPlotEntity) {
        coroutineScope.launch {
            try {
                vertices = withContext(Dispatchers.IO) {
                    database.LocalPlotVertexDao().getVerticesByPlot(parcela.idLocalPlot)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar vértices: ${e.message}")
            }
        }
    }

    fun limpiarDependenciasDesdeProductor() {
        ranchoSeleccionado = null
        parcelaSeleccionada = null
        ranchos = emptyList()
        parcelas = emptyList()
        vertices = emptyList()
    }

    fun limpiarDependenciasDesdeRancho() {
        parcelaSeleccionada = null
        parcelas = emptyList()
        vertices = emptyList()
    }

    fun validarYGuardar() {
        val idCia = idLocalCia
        val productor = productorSeleccionado
        val rancho = ranchoSeleccionado
        val parcela = parcelaSeleccionada
        val cultivo = cultivoSeleccionado
        val inicioMillis = fechaInicioMillis?.let { normalizarFechaAdmin(it, finDelDia = false) }
        val finMillis = fechaFinMillis?.let { normalizarFechaAdmin(it, finDelDia = true) }
        val radio = radioTexto.trim().toIntOrNull()
        val cantidadPuntos = cantidadPuntosTexto.trim().toIntOrNull()
        val cicloLimpio = normalizarCicloMonitoreoAdmin(ciclo)

        when {
            idCia == null || idCia <= 0L -> onMensaje("Selecciona una CIA primero")
            productor == null -> onMensaje("Selecciona un productor")
            rancho == null -> onMensaje("Selecciona un rancho")
            parcela == null -> onMensaje("Selecciona una parcela")
            cultivo == null -> onMensaje("Selecciona un cultivo")
            cicloLimpio.isBlank() -> onMensaje("Escribe el ciclo del monitoreo")
            inicioMillis == null -> onMensaje("Selecciona la fecha de inicio")
            finMillis == null -> onMensaje("Selecciona la fecha fin")
            inicioMillis > finMillis -> onMensaje("La fecha de inicio no puede ser mayor que la fecha fin")
            radio == null || radio <= 0 -> onMensaje("El radio debe ser mayor a 0")
            cantidadPuntos == null || cantidadPuntos <= 0 -> onMensaje("La cantidad de puntos debe ser mayor a 0")
            vertices.size < 3 -> onMensaje("La parcela necesita al menos 3 vértices para generar puntos")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        val resultado = withContext(Dispatchers.IO) {
                            val puntosGenerados = generarPuntosDentroDelPoligonoAdmin(
                                vertices = vertices,
                                cantidad = cantidadPuntos
                            )

                            if (puntosGenerados.isEmpty()) {
                                throw IllegalStateException(
                                    "No se pudieron generar puntos dentro del polígono. Revisa los vértices de la parcela"
                                )
                            }

                            val idProgram = database.localprogramDao().insertProgram(
                                LocalProgramEntity(
                                    cycle = cicloLimpio,
                                    estStartDate = inicioMillis,
                                    estFinishDate = finMillis,
                                    actStartDate = null,
                                    actFinishDate = null,
                                    status = "Pendiente",
                                    idLocalAgroUnit = productor.idLocalAgroUnit,
                                    idLocalRanch = rancho.idLocalRanch,
                                    idCrop = cultivo.idCrop,
                                    idLocalPlot = parcela.idLocalPlot
                                )
                            )

                            val idHeader = database.localphytomonitoringheaderDao().insertHeader(
                                LocalPhytomonitoringHeaderEntity(
                                    cycle = cicloLimpio,
                                    estStartDate = inicioMillis,
                                    estFinishDate = finMillis,
                                    startAt = null,
                                    finishedAt = null,
                                    status = "Pendiente",
                                    idProgram = idProgram,
                                    idLocalPlot = parcela.idLocalPlot,
                                    idCrop = cultivo.idCrop
                                )
                            )

                            puntosGenerados.forEach { punto ->
                                database.LocalPhytomonitoringTargetPointDao().insertTargetPoint(
                                    LocalPhytomonitoringTargetPointEntity(
                                        radiusM = radio,
                                        lat = punto.lat,
                                        lon = punto.lon,
                                        status = "Pendiente",
                                        idHeader = idHeader,
                                        idLocalPlot = parcela.idLocalPlot
                                    )
                                )
                            }

                            idHeader to puntosGenerados.size
                        }

                        onMensaje("Monitoreo realizado")
                        ciclo = ""
                        fechaInicioMillis = null
                        fechaFinMillis = null
                        cantidadPuntosTexto = "10"
                        radioTexto = "20"
                        productorSeleccionado = null
                        ranchoSeleccionado = null
                        parcelaSeleccionada = null
                        cultivoSeleccionado = null
                        ranchos = emptyList()
                        parcelas = emptyList()
                        vertices = emptyList()
                        onAdminClick()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMensaje("No se pudo crear el monitoreo: ${e.message}")
                    } finally {
                        guardando = false
                    }
                }
            }
        }
    }

    val avanceFormulario = listOf(
        productorSeleccionado != null,
        ranchoSeleccionado != null,
        parcelaSeleccionada != null,
        cultivoSeleccionado != null,
        ciclo.isNotBlank(),
        fechaInicioMillis != null && fechaFinMillis != null,
        radioTexto.isNotBlank() && cantidadPuntosTexto.isNotBlank(),
        vertices.size >= 3
    ).count { it } / 8f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AdminMonitorHeroCard(
                nombreCia = nombreCia,
                avance = avanceFormulario,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (cargandoInicial) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Cargando productores y cultivos...",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E4D22)
                        )
                    }
                }
            } else {
                AdminMonitorSectionCard(
                    numero = "1",
                    titulo = "Ubicación del monitoreo",
                    subtitulo = "Selecciona productor, rancho y parcela de la CIA actual."
                ) {
                    AdminSelectorField(
                        etiqueta = "Productor",
                        valor = productorSeleccionado?.commercial_name ?: "Seleccionar productor",
                        opciones = productores,
                        textoOpcion = { it.commercial_name },
                        habilitado = !guardando && productores.isNotEmpty(),
                        onSeleccionar = { productor ->
                            productorSeleccionado = productor
                            limpiarDependenciasDesdeProductor()
                            cargarRanchos(productor)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AdminSelectorField(
                        etiqueta = "Rancho",
                        valor = ranchoSeleccionado?.name ?: "Seleccionar rancho",
                        opciones = ranchos,
                        textoOpcion = { "${it.name} (${it.code})" },
                        habilitado = !guardando && ranchos.isNotEmpty(),
                        onSeleccionar = { rancho ->
                            ranchoSeleccionado = rancho
                            limpiarDependenciasDesdeRancho()
                            cargarParcelas(rancho)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AdminSelectorField(
                        etiqueta = "Parcela",
                        valor = parcelaSeleccionada?.code ?: "Seleccionar parcela",
                        opciones = parcelas,
                        textoOpcion = { parcela ->
                            if (parcela.description.isNullOrBlank()) parcela.code else "${parcela.code} - ${parcela.description}"
                        },
                        habilitado = !guardando && parcelas.isNotEmpty(),
                        onSeleccionar = { parcela ->
                            parcelaSeleccionada = parcela
                            vertices = emptyList()
                            cargarVertices(parcela)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PolygonStatusCard(vertices = vertices)
                }

                Spacer(modifier = Modifier.height(14.dp))

                AdminMonitorSectionCard(
                    numero = "2",
                    titulo = "Datos del programa",
                    subtitulo = "Define cultivo, ciclo y fechas estimadas."
                ) {
                    AdminSelectorField(
                        etiqueta = "Cultivo",
                        valor = cultivoSeleccionado?.let { cultivo ->
                            if (cultivo.variedad.isNullOrBlank()) cultivo.name else "${cultivo.name} - ${cultivo.variedad}"
                        } ?: "Seleccionar cultivo",
                        opciones = cultivos,
                        textoOpcion = { cultivo ->
                            if (cultivo.variedad.isNullOrBlank()) cultivo.name else "${cultivo.name} - ${cultivo.variedad}"
                        },
                        habilitado = !guardando && cultivos.isNotEmpty(),
                        onSeleccionar = { cultivo -> cultivoSeleccionado = cultivo }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = ciclo,
                        onValueChange = { ciclo = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ciclo") },
                        placeholder = { Text("Ejemplo: Primavera-Verano 2026") },
                        enabled = !guardando,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminDatePickerField(
                            etiqueta = "Inicio",
                            valor = formatearFechaAdmin(fechaInicioMillis),
                            habilitado = !guardando,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                mostrarDatePickerNativoAdmin(
                                    context = context,
                                    fechaActualMillis = fechaInicioMillis,
                                    onSeleccionar = { fechaInicioMillis = it }
                                )
                            }
                        )

                        AdminDatePickerField(
                            etiqueta = "Fin",
                            valor = formatearFechaAdmin(fechaFinMillis),
                            habilitado = !guardando,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                mostrarDatePickerNativoAdmin(
                                    context = context,
                                    fechaActualMillis = fechaFinMillis,
                                    onSeleccionar = { fechaFinMillis = it }
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AdminMonitorSectionCard(
                    numero = "3",
                    titulo = "Puntos objetivo",
                    subtitulo = "Configura radio y cantidad de puntos ordenados dentro del polígono."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = radioTexto,
                            onValueChange = { radioTexto = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text("Radio m") },
                            enabled = !guardando,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        OutlinedTextField(
                            value = cantidadPuntosTexto,
                            onValueChange = { cantidadPuntosTexto = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text("Puntos") },
                            enabled = !guardando,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AdminResumenMonitoreo(
                        productor = productorSeleccionado,
                        rancho = ranchoSeleccionado,
                        parcela = parcelaSeleccionada,
                        cultivo = cultivoSeleccionado,
                        ciclo = ciclo,
                        fechaInicio = formatearFechaAdmin(fechaInicioMillis),
                        fechaFin = formatearFechaAdmin(fechaFinMillis),
                        radioTexto = radioTexto,
                        cantidadPuntosTexto = cantidadPuntosTexto,
                        totalVertices = vertices.size
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { validarYGuardar() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !guardando,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        if (guardando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Guardando...")
                        } else {
                            Text(
                                text = "Crear monitoreo y generar puntos",
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun mostrarDatePickerNativoAdmin(
    context: android.content.Context,
    fechaActualMillis: Long?,
    onSeleccionar: (Long) -> Unit
) {
    val calendar = Calendar.getInstance()

    if (fechaActualMillis != null) {
        calendar.timeInMillis = fechaActualMillis
    }

    val anio = calendar.get(Calendar.YEAR)
    val mes = calendar.get(Calendar.MONTH)
    val dia = calendar.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val seleccion = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            onSeleccionar(seleccion.timeInMillis)
        },
        anio,
        mes,
        dia
    ).show()
}

@Composable
private fun AdminDatePickerField(
    etiqueta: String,
    valor: String,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = etiqueta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E4D22),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (habilitado) Color(0xFFB6C8AA) else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(if (habilitado) Color.White else Color(0xFFF5F5F5))
                .clickable(enabled = habilitado) { onClick() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = valor.ifBlank { "Seleccionar fecha" },
                color = if (habilitado) Color(0xFF263B1E) else Color.Gray,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "📅",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun AdminMonitorHeroCard(
    nombreCia: String,
    avance: Float,
    onBackClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1B5E20),
                            Color(0xFF43A047)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🗺️ Administrar monitoreos",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 21.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Crea programas, encabezados y puntos objetivo con la base actual de MyApplication.",
                        color = Color(0xFFE8F5E9),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Volver")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CIA: $nombreCia",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { avance.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
private fun AdminMonitorSectionCard(
    numero: String,
    titulo: String,
    subtitulo: String,
    contenido: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = numero,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        text = subtitulo,
                        fontSize = 12.sp,
                        color = Color(0xFF5E6D58),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            contenido()
        }
    }
}

@Composable
private fun <T> AdminSelectorField(
    etiqueta: String,
    valor: String,
    opciones: List<T>,
    textoOpcion: (T) -> String,
    habilitado: Boolean,
    onSeleccionar: (T) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = etiqueta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E4D22),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (habilitado) Color(0xFFB6C8AA) else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(if (habilitado) Color.White else Color(0xFFF5F5F5))
                    .clickable(enabled = habilitado) { abierto = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valor,
                    color = if (habilitado) Color(0xFF263B1E) else Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "⌄", color = Color(0xFF2E7D32), fontWeight = FontWeight.Black)
            }

            DropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false },
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                if (opciones.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin opciones disponibles") },
                        onClick = { abierto = false }
                    )
                } else {
                    opciones.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(textoOpcion(opcion)) },
                            onClick = {
                                abierto = false
                                onSeleccionar(opcion)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolygonStatusCard(vertices: List<LocalPlotVertexEntity>) {
    val listo = vertices.size >= 3

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (listo) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = if (listo) "✅" else "⚠️", fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (listo) "Polígono listo" else "Faltan vértices",
                    fontWeight = FontWeight.Black,
                    color = if (listo) Color(0xFF1B5E20) else Color(0xFFE65100)
                )
                Text(
                    text = if (listo) {
                        "La parcela tiene ${vertices.size} vértices. Se pueden generar puntos dentro del lote."
                    } else {
                        "La parcela necesita mínimo 3 vértices en LocalPlotVertexEntity."
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF4E5D45),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun AdminResumenMonitoreo(
    productor: LocalAgroUnitEntity?,
    rancho: LocalRanchEntity?,
    parcela: LocalPlotEntity?,
    cultivo: LocalCropCatalogEntity?,
    ciclo: String,
    fechaInicio: String,
    fechaFin: String,
    radioTexto: String,
    cantidadPuntosTexto: String,
    totalVertices: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Resumen antes de guardar",
                fontWeight = FontWeight.Black,
                color = Color(0xFF1B5E20),
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFD9E7D1))
            Spacer(modifier = Modifier.height(8.dp))

            ResumenRow("Productor", productor?.commercial_name ?: "Pendiente")
            ResumenRow("Rancho", rancho?.name ?: "Pendiente")
            ResumenRow("Parcela", parcela?.code ?: "Pendiente")
            ResumenRow("Cultivo", cultivo?.name ?: "Pendiente")
            ResumenRow("Ciclo", ciclo.ifBlank { "Pendiente" })
            ResumenRow("Fechas", "${fechaInicio.ifBlank { "Inicio" }} → ${fechaFin.ifBlank { "Fin" }}")
            ResumenRow("Puntos", "${cantidadPuntosTexto.ifBlank { "0" }} puntos / radio ${radioTexto.ifBlank { "0" }} m")
            ResumenRow("Vértices", "$totalVertices registrados")

            AnimatedVisibility(visible = totalVertices < 3) {
                Text(
                    text = "No se podrá guardar hasta que la parcela tenga polígono completo.",
                    color = Color(0xFFE65100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ResumenRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF4E5D45),
            modifier = Modifier.width(82.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF263B1E),
            modifier = Modifier.weight(1f)
        )
    }
}

private data class AdminGeneratedPoint(
    val lat: Double,
    val lon: Double
)


private fun formatearFechaAdmin(fechaMillis: Long?): String {
    if (fechaMillis == null) return ""

    return SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        .format(Date(normalizarFechaAdmin(fechaMillis, finDelDia = false)))
}

private fun normalizarFechaAdmin(fechaMillis: Long, finDelDia: Boolean): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = fechaMillis

    if (finDelDia) {
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
    } else {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    return calendar.timeInMillis
}

private fun normalizarCicloMonitoreoAdmin(texto: String): String {
    return texto
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9 ._\\-/]"), "")
        .take(80)
}

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

private fun generarPuntosDentroDelPoligonoAdmin(
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

    /*
     * La separación base nace del área real del polígono:
     * si pides pocos puntos, aumenta la distancia; si pides muchos, la reduce.
     */
    val separacionBase = sqrt(area / cantidad.toDouble()).coerceAtLeast(1.0)

    var columnasBase = (largo / separacionBase).roundToInt().coerceAtLeast(1)
    var filasBase = ceil(cantidad.toDouble() / columnasBase.toDouble()).toInt().coerceAtLeast(1)

    /*
     * Ajuste por forma: si la parcela es larga, favorece líneas longitudinales.
     * Si es más ancha, aumenta filas para no amontonar puntos.
     */
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

package com.example.myapplication.local.monitoreo.registro

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import com.example.myapplication.local.monitoreo.severidad.NivelSeveridad
import com.example.myapplication.local.monitoreo.severidad.RangosSeveridad
import com.example.myapplication.local.monitoreo.severidad.SEVERIDAD_MAYOR_DEFAULT
import com.example.myapplication.local.monitoreo.severidad.agregarMetadataRangosSeveridad
import com.example.myapplication.local.monitoreo.severidad.calcularNivelSeveridad
import com.example.myapplication.local.monitoreo.severidad.rangosSeveridadDesdeTexto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("UNUSED_PARAMETER")
@Composable
fun RegistroPuntoMonitoreoScreen(
    database: AppDatabase,
    nombreUsuario: String,
    rolUsuario: String = "",
    idUsuarioActual: Long,
    header: LocalPhytomonitoringHeaderEntity,
    punto: LocalPhytomonitoringTargetPointEntity,
    onCancelar: () -> Unit,
    onCerrarSesionClick: () -> Unit,
    onGuardado: () -> Unit,
    onPerfilClick: () -> Unit = {},
    onCambiarCiaClick: (() -> Unit)? = null,
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val preferenciasSeveridad = remember(header.idHeader) {
        context.getSharedPreferences("severidad_monitoreo", Context.MODE_PRIVATE)
    }

    val claveSeveridadMayor = remember(header.idHeader) {
        "severidad_mayor_header_${header.idHeader}"
    }

    var catalogo by remember { mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList()) }
    var etapas by remember { mutableStateOf<List<LocalPhytostageEntity>>(emptyList()) }
    var fitoSeleccionado by remember { mutableStateOf<LocalPhytosanitaryCatalogEntity?>(null) }

    val etapasPorFito = remember { mutableStateMapOf<Long, List<LocalPhytostageEntity>>() }
    val cantidadesPorEtapa = remember { mutableStateMapOf<ClaveEtapaUi, Int>() }
    val fitosSinEtapasSeleccionados = remember { mutableStateMapOf<Long, Boolean>() }
    var severidadMayorPunto by rememberSaveable(header.idHeader) {
        mutableStateOf(
            preferenciasSeveridad.getString(
                claveSeveridadMayor,
                SEVERIDAD_MAYOR_DEFAULT.toString()
            ) ?: SEVERIDAD_MAYOR_DEFAULT.toString()
        )
    }

    var observaciones by rememberSaveable { mutableStateOf("") }
    var nombreCultivo by remember { mutableStateOf("Cultivo no identificado") }
    var fotoCultivo by remember { mutableStateOf<String?>(null) }
    var numeroPuntoVisible by remember { mutableStateOf(1) }
    var registrosAgregados by rememberSaveable { mutableStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var finalizando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var mostrarAvisoRegresar by remember { mutableStateOf(false) }
    var tipoConfirmacionGuardado by remember { mutableStateOf<TipoConfirmacionGuardado?>(null) }

    BackHandler(enabled = true) {
        mostrarAvisoRegresar = true
    }

    if (mostrarAvisoRegresar) {
        AlertDialog(
            onDismissRequest = {
                mostrarAvisoRegresar = false
            },
            title = {
                Text(
                    text = "Guardar punto",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Debes guardar el punto antes de regresar. Puedes guardarlo como Sin plaga o capturar una plaga/enfermedad y presionar Guardar registro."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarAvisoRegresar = false
                    }
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        cargando = true
        error = null

        try {
            val resultado = withContext(Dispatchers.IO) {
                val catalogoDb = database.localphytosanitarycatalogDao()
                    .getAllCatalogo()
                    .filterNot { item -> item.name.equals("Sin plaga", ignoreCase = true) }

                val cultivoDb = database.localCropCatalogDao().getCropById(header.idCrop)

                val puntosDelMonitoreo = database.LocalPhytomonitoringTargetPointDao()
                    .getTargetPointsByHeader(header.idHeader)
                    .sortedBy { it.idTargetPoint }

                val numeroPuntoCalculado = puntosDelMonitoreo
                    .indexOfFirst { it.idTargetPoint == punto.idTargetPoint }
                    .let { index -> if (index >= 0) index + 1 else 1 }

                val capturasExistentes = database.localphytomonitoringcheckpointDao()
                    .getCheckpointsByTargetPoint(punto.idTargetPoint)
                    .filter { checkpoint -> checkpoint.presenceStatus == 1 }

                val totalPlagasAgregadas = capturasExistentes
                    .map { checkpoint -> checkpoint.idPhytosanitary }
                    .distinct()
                    .size

                RegistroPuntoDataUi(
                    catalogo = catalogoDb,
                    nombreCultivo = cultivoDb?.name ?: "Cultivo no identificado",
                    fotoCultivo = cultivoDb?.photo,
                    numeroPuntoVisible = numeroPuntoCalculado,
                    totalPlagasAgregadas = totalPlagasAgregadas
                )
            }

            catalogo = resultado.catalogo
            nombreCultivo = resultado.nombreCultivo
            fotoCultivo = resultado.fotoCultivo
            numeroPuntoVisible = resultado.numeroPuntoVisible
            registrosAgregados = resultado.totalPlagasAgregadas
        } catch (e: Exception) {
            error = "Error al cargar datos: ${e.message}"
        } finally {
            cargando = false
        }
    }

    LaunchedEffect(fitoSeleccionado?.idPhytosanitary) {
        val fito = fitoSeleccionado
        etapas = emptyList()

        if (fito != null) {
            try {
                val etapasDb = etapasPorFito[fito.idPhytosanitary]
                    ?: withContext(Dispatchers.IO) {
                        database.localphytostageDao()
                            .getStagesByPhytosanitary(fito.idPhytosanitary)
                    }.also { etapasCargadas ->
                        etapasPorFito[fito.idPhytosanitary] = etapasCargadas
                    }

                etapas = etapasDb

                if (etapasDb.isEmpty()) {
                    fitosSinEtapasSeleccionados[fito.idPhytosanitary] = true
                } else {
                    etapasDb.forEach { etapa ->
                        val clave = ClaveEtapaUi(
                            idPhytosanitary = fito.idPhytosanitary,
                            stage = etapa.stage
                        )

                        if (!cantidadesPorEtapa.containsKey(clave)) {
                            cantidadesPorEtapa[clave] = 0
                        }
                    }
                }
            } catch (e: Exception) {
                error = "Error al cargar etapas: ${e.message}"
            }
        }
    }

    val registrosPendientesPorEtapa = cantidadesPorEtapa.values.count { cantidad -> cantidad > 0 }
    val registrosPendientesSinEtapas = fitosSinEtapasSeleccionados.values.count { seleccionado -> seleccionado }
    val registrosPendientes = registrosPendientesPorEtapa + registrosPendientesSinEtapas

    fun totalCantidadPuntoActual(): Int {
        val totalEtapas = cantidadesPorEtapa.values.sum()
        val presenciaGeneral = fitosSinEtapasSeleccionados.values.count { seleccionado -> seleccionado }

        return totalEtapas + presenciaGeneral
    }

    fun rangosTextoValidosParaPunto(): RangosSeveridad? {
        return rangosSeveridadDesdeTexto(severidadMayorPunto)
    }

    fun idsFitosPendientes(): Set<Long> {
        val idsConCantidad = cantidadesPorEtapa
            .filter { entrada -> entrada.value > 0 }
            .keys
            .map { clave -> clave.idPhytosanitary }

        val idsSinEtapas = fitosSinEtapasSeleccionados
            .filter { entrada -> entrada.value }
            .keys

        return (idsConCantidad + idsSinEtapas).toSet()
    }

    fun nivelColorRegistro(nivel: NivelSeveridad): Color {
        return when (nivel) {
            NivelSeveridad.VERDE -> Color(0xFF16A34A)
            NivelSeveridad.AMARILLO -> Color(0xFFD6A100)
            NivelSeveridad.NARANJA -> Color(0xFFF97316)
            NivelSeveridad.ROJO -> Color(0xFFDC2626)
        }
    }

    fun registrarSinPlaga() {
        if (finalizando) return
        finalizando = true

        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var sinPlaga = database.localphytosanitarycatalogDao()
                        .getAllCatalogo()
                        .firstOrNull { item -> item.name.equals("Sin plaga", ignoreCase = true) }

                    if (sinPlaga == null) {
                        val idNuevo = database.localphytosanitarycatalogDao()
                            .insertPhytosanitary(
                                LocalPhytosanitaryCatalogEntity(
                                    name = "Sin plaga",
                                    type = "SIN_PLAGA",
                                    minRefValue = 0,
                                    maxRefValue = 0,
                                    description = "Punto revisado sin presencia de plagas o enfermedades",
                                    photo = null,
                                    idDefaultCrop = header.idCrop
                                )
                            )

                        sinPlaga = database.localphytosanitarycatalogDao()
                            .getPhytosanitaryById(idNuevo)
                    }

                    if (sinPlaga != null) {
                        val checkpoint = LocalPhytomonitoringCheckpointEntity(
                            qty = 0,
                            presenceStatus = 0,
                            stage = null,
                            notes = "Punto revisado sin presencia de plagas o enfermedades",
                            capturedAt = System.currentTimeMillis(),
                            capturedByUserId = idUsuarioActual,
                            idTargetPoint = punto.idTargetPoint,
                            idHeader = header.idHeader,
                            idPhytosanitary = sinPlaga.idPhytosanitary,
                            idLocalPlot = punto.idLocalPlot
                        )

                        database.localphytomonitoringcheckpointDao().insertCheckpoint(checkpoint)
                    }

                    database.LocalPhytomonitoringTargetPointDao()
                        .actualizarStatusPunto(
                            idTargetPoint = punto.idTargetPoint,
                            status = "Completado"
                        )
                }

                Toast.makeText(context, "Punto registrado sin plagas", Toast.LENGTH_SHORT).show()
                onGuardado()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al registrar sin plaga: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizando = false
            }
        }
    }

    fun guardarRegistro() {
        if (finalizando) return

        val registrosConCantidad = cantidadesPorEtapa.filter { it.value > 0 }
        val fitosSinEtapasPendientes = fitosSinEtapasSeleccionados
            .filter { it.value }
            .keys

        if (
            registrosConCantidad.isEmpty() &&
            fitosSinEtapasPendientes.isEmpty() &&
            registrosAgregados <= 0
        ) {
            Toast.makeText(
                context,
                "Selecciona una o varias plagas/enfermedades y captura cantidades",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val rangosPunto = rangosTextoValidosParaPunto()
        if (rangosPunto == null) {
            Toast.makeText(
                context,
                "Revisa la severidad del punto: la severidad mayor debe ser un número mayor a 0",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        finalizando = true

        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ahora = System.currentTimeMillis()
                    val notasConSeveridad = agregarMetadataRangosSeveridad(
                        notas = observaciones.ifBlank { null },
                        rangos = rangosPunto
                    )

                    registrosConCantidad.forEach { (clave, cantidad) ->
                        val checkpoint = LocalPhytomonitoringCheckpointEntity(
                            qty = cantidad,
                            presenceStatus = 1,
                            stage = clave.stage,
                            notes = notasConSeveridad,
                            capturedAt = ahora,
                            capturedByUserId = idUsuarioActual,
                            idTargetPoint = punto.idTargetPoint,
                            idHeader = header.idHeader,
                            idPhytosanitary = clave.idPhytosanitary,
                            idLocalPlot = punto.idLocalPlot
                        )

                        database.localphytomonitoringcheckpointDao().insertCheckpoint(checkpoint)
                    }

                    fitosSinEtapasPendientes.forEach { idPhytosanitary ->
                        val checkpoint = LocalPhytomonitoringCheckpointEntity(
                            qty = 1,
                            presenceStatus = 1,
                            stage = null,
                            notes = notasConSeveridad,
                            capturedAt = ahora,
                            capturedByUserId = idUsuarioActual,
                            idTargetPoint = punto.idTargetPoint,
                            idHeader = header.idHeader,
                            idPhytosanitary = idPhytosanitary,
                            idLocalPlot = punto.idLocalPlot
                        )

                        database.localphytomonitoringcheckpointDao().insertCheckpoint(checkpoint)
                    }

                    database.LocalPhytomonitoringTargetPointDao()
                        .actualizarStatusPunto(
                            idTargetPoint = punto.idTargetPoint,
                            status = "Completado"
                        )
                }

                Toast.makeText(context, "Punto finalizado correctamente", Toast.LENGTH_SHORT).show()
                onGuardado()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al finalizar punto: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizando = false
            }
        }
    }

    tipoConfirmacionGuardado?.let { tipo ->
        AlertDialog(
            onDismissRequest = {
                tipoConfirmacionGuardado = null
            },
            title = {
                Text(
                    text = "Confirmar guardado",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (tipo == TipoConfirmacionGuardado.SIN_PLAGA) {
                        "¿Estás seguro que quieres guardar este punto como SIN PLAGA?"
                    } else {
                        "¿Estás seguro que quieres guardar el registro de este punto?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !finalizando,
                    onClick = {
                        val accion = tipoConfirmacionGuardado
                        tipoConfirmacionGuardado = null

                        if (accion == TipoConfirmacionGuardado.SIN_PLAGA) {
                            registrarSinPlaga()
                        } else {
                            guardarRegistro()
                        }
                    }
                ) {
                    Text(
                        text = "Confirmar",
                        color = Color(0xFF0B6B20),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !finalizando,
                    onClick = {
                        tipoConfirmacionGuardado = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
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

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .padding(bottom = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RegistroPuntoSuperiorCard(
                    punto = punto,
                    numeroPunto = numeroPuntoVisible,
                    nombreCultivo = nombreCultivo,
                    fotoCultivo = fotoCultivo
                )

                Spacer(modifier = Modifier.height(14.dp))

                ElementoSeleccionadoCard(fito = fitoSeleccionado)

                Spacer(modifier = Modifier.height(14.dp))

                val mayorTexto = severidadMayorPunto
                val rangos = rangosSeveridadDesdeTexto(mayorTexto) ?: RangosSeveridad()
                val totalPuntoActual = totalCantidadPuntoActual()
                val nivel = calcularNivelSeveridad(
                    cantidadTotal = totalPuntoActual,
                    presenceStatus = if (totalPuntoActual > 0) 1 else 0,
                    rangos = rangos
                )

                SemaforoSeveridadCard(
                    mayorTexto = mayorTexto,
                    totalSeleccionado = totalPuntoActual,
                    nivelTexto = nivel.etiqueta,
                    colorNivel = nivelColorRegistro(nivel),
                    onMayorChange = { nuevo ->
                        severidadMayorPunto = nuevo

                        val mayor = nuevo.toIntOrNull()
                        if (mayor != null && mayor > 0) {
                            preferenciasSeveridad.edit()
                                .putString(claveSeveridadMayor, nuevo)
                                .apply()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    cargando -> {
                        InfoBox(text = "Cargando plagas y enfermedades...")
                    }

                    error != null -> {
                        InfoBox(text = error ?: "Error desconocido", isError = true)
                    }

                    catalogo.isEmpty() -> {
                        InfoBox(text = "No hay plagas o enfermedades registradas.", isError = true)
                    }

                    else -> {
                        CatalogoPlagasHorizontal(
                            catalogo = catalogo,
                            fitoSeleccionado = fitoSeleccionado,
                            onSelected = { item -> fitoSeleccionado = item }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                fitoSeleccionado?.let { fito ->
                    if (etapas.isEmpty()) {
                        InfoBox(
                            text = "${textoTipoFitoRegistro(fito.type)} seleccionada sin etapas. Se guardará como presencia general."
                        )
                    } else {
                        val etapasUi = etapas.map { etapa ->
                            val clave = ClaveEtapaUi(
                                idPhytosanitary = fito.idPhytosanitary,
                                stage = etapa.stage
                            )

                            EtapaCantidadUi(
                                etapa = etapa,
                                cantidad = cantidadesPorEtapa[clave] ?: 0,
                                onMenos = {
                                    val actual = cantidadesPorEtapa[clave] ?: 0
                                    cantidadesPorEtapa[clave] = maxOf(0, actual - 1)
                                },
                                onMas = {
                                    val actual = cantidadesPorEtapa[clave] ?: 0
                                    cantidadesPorEtapa[clave] = actual + 1
                                }
                            )
                        }

                        EtapasCantidadCard(etapas = etapasUi)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle(title = "Observaciones")

                Spacer(modifier = Modifier.height(8.dp))

                ObservacionesBox(
                    value = observaciones,
                    onValueChange = { observaciones = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoBox(
                    text = "Registros guardados: $registrosAgregados  •  Capturas por guardar: $registrosPendientes"
                )
            }

            BarraAccionesRegistro(
                finalizando = finalizando,
                onSinPlagaClick = {
                    tipoConfirmacionGuardado = TipoConfirmacionGuardado.SIN_PLAGA
                },
                onGuardarClick = {
                    tipoConfirmacionGuardado = TipoConfirmacionGuardado.CON_REGISTRO
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 42.dp)
            )
        }
    }
}

private enum class TipoConfirmacionGuardado {
    SIN_PLAGA,
    CON_REGISTRO
}
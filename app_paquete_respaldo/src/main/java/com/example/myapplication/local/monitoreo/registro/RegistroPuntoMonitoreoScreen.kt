package com.example.myapplication.local.monitoreo.registro

// CAMBIO PUNTOS/CSV: la pantalla de registro muestra el número real del label
// y usa el orden local únicamente como respaldo para puntos antiguos.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.example.myapplication.local.monitoreo.severidad.agregarMetadataRangosSeveridad
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import com.example.myapplication.local.monitoreo.media.PhytoMediaStorage
import com.example.myapplication.local.monitoreo.severidad.NivelSeveridad
import com.example.myapplication.local.monitoreo.severidad.RangosSeveridad
import com.example.myapplication.local.monitoreo.severidad.SEVERIDAD_MAYOR_DEFAULT
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
    var tipoCatalogoSeleccionado by rememberSaveable {
        mutableStateOf(TipoCatalogoRegistroUi.PLAGAS)
    }

    val etapasPorFito = remember { mutableStateMapOf<Long, List<LocalPhytostageEntity>>() }
    val fotosRepresentativasPorFito = remember { mutableStateMapOf<Long, String?>() }
    val cantidadesPorEtapa = remember { mutableStateMapOf<ClaveEtapaUi, Int>() }
    val fitosSinEtapasSeleccionados = remember { mutableStateMapOf<Long, Boolean>() }

    /*
     * Las enfermedades no se capturan por cantidad.
     *
     * NO_PRESENTE -> presenceStatus = 0, qty = 0, stage = null
     * PRESENTE    -> presenceStatus = 1, qty = 1 técnico, fase obligatoria
     *                (Inicio, Desarrollo o Avanzado).
     */
    val estadosEnfermedadPorFito = remember {
        mutableStateMapOf<Long, EstadoEnfermedadUi>()
    }

    var severidadMayorPunto by rememberSaveable(header.idHeader) {
        mutableStateOf(
            preferenciasSeveridad.getString(
                claveSeveridadMayor,
                SEVERIDAD_MAYOR_DEFAULT.toString()
            ) ?: SEVERIDAD_MAYOR_DEFAULT.toString()
        )
    }

    var observaciones by rememberSaveable { mutableStateOf("") }
    var fotoUriSeleccionada by rememberSaveable(header.idHeader, punto.idTargetPoint) {
        mutableStateOf<String?>(null)
    }
    var mostrarCamaraTrasera by rememberSaveable(header.idHeader, punto.idTargetPoint) {
        mutableStateOf(false)
    }
    var nombreCultivo by remember { mutableStateOf("Cultivo no identificado") }
    var fotoCultivo by remember { mutableStateOf<String?>(null) }
    var numeroPuntoVisible by remember { mutableStateOf(1) }
    var registrosAgregados by rememberSaveable { mutableStateOf(0) }
    var idsFitosGuardados by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var cargando by remember { mutableStateOf(true) }
    var finalizando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var mostrarAvisoRegresar by remember { mutableStateOf(false) }
    var tipoConfirmacionGuardado by remember { mutableStateOf<TipoConfirmacionGuardado?>(null) }

    // La evidencia se toma con CameraX dentro de la app para forzar la cámara trasera.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permitido ->
        if (permitido) {
            mostrarCamaraTrasera = true
        } else {
            Toast.makeText(
                context,
                "Se necesita permiso de cámara para tomar la evidencia.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            fotoUriSeleccionada = uri.toString()
        }
    }

    if (mostrarCamaraTrasera) {
        CameraEvidenciaTraseraDialog(
            onCancelar = {
                mostrarCamaraTrasera = false
            },
            onFotoTomada = { uriFoto ->
                fotoUriSeleccionada = uriFoto
                mostrarCamaraTrasera = false
            }
        )
    }

    BackHandler(enabled = !mostrarCamaraTrasera) {
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

    LaunchedEffect(
        header.idCrop,
        header.idHeader,
        punto.idTargetPoint
    ) {
        cargando = true
        error = null

        try {
            val resultado = withContext(Dispatchers.IO) {
                /*
                 * Solo se muestran plagas y enfermedades relacionadas con el cultivo
                 * real del monitoreo. Los registros generales o de otros cultivos no
                 * deben mezclarse en la captura.
                 */
                val catalogoDb = database.localphytosanitarycatalogDao()
                    .getCatalogoByCrop(header.idCrop)
                    .filterNot { item ->
                        item.name.equals("Sin plaga", ignoreCase = true)
                    }

                val cultivoDb = database.localCropCatalogDao().getCropById(header.idCrop)

                val puntosDelMonitoreo = database.LocalPhytomonitoringTargetPointDao()
                    .getTargetPointsByHeader(header.idHeader)
                    .sortedBy { it.idTargetPoint }

                val indiceRespaldo = puntosDelMonitoreo
                    .indexOfFirst { it.idTargetPoint == punto.idTargetPoint }
                    .let { index -> if (index >= 0) index + 1 else 1 }

                val numeroPuntoCalculado = Regex("\\d+")
                    .find(punto.label)
                    ?.value
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?: indiceRespaldo

                val todasLasCapturasExistentes = database.localphytomonitoringcheckpointDao()
                    .getCheckpointsByTargetPoint(punto.idTargetPoint)

                val capturasExistentes = todasLasCapturasExistentes
                    .filter { checkpoint ->
                        (checkpoint.qty ?: 0) > 0 && checkpoint.presenceStatus != 0
                    }

                val totalPlagasAgregadas = capturasExistentes
                    .map { checkpoint -> checkpoint.idPhytosanitary }
                    .distinct()
                    .size

                RegistroPuntoDataUi(
                    catalogo = catalogoDb,
                    nombreCultivo = cultivoDb?.name ?: "Cultivo no identificado",
                    fotoCultivo = cultivoDb?.photo,
                    numeroPuntoVisible = numeroPuntoCalculado,
                    totalPlagasAgregadas = totalPlagasAgregadas,
                    idsFitosRegistrados = todasLasCapturasExistentes
                        .mapNotNull { checkpoint ->
                            checkpoint.idPhytosanitary
                        }
                        .toSet()
                )
            }

            catalogo = resultado.catalogo

            val hayPlagas = resultado.catalogo.any { fito ->
                esPlagaRegistro(fito.type)
            }
            val hayEnfermedades = resultado.catalogo.any { fito ->
                esEnfermedadRegistro(fito.type)
            }

            if (!hayPlagas && hayEnfermedades) {
                tipoCatalogoSeleccionado = TipoCatalogoRegistroUi.ENFERMEDADES
            }

            nombreCultivo = resultado.nombreCultivo
            fotoCultivo = resultado.fotoCultivo
            numeroPuntoVisible = resultado.numeroPuntoVisible
            registrosAgregados = resultado.totalPlagasAgregadas
            idsFitosGuardados = resultado.idsFitosRegistrados
            resultado.catalogo.forEach { fito ->
                val etapasFito = withContext(Dispatchers.IO) {
                    database.localphytostageDao()
                        .getStagesByPhytosanitary(fito.idPhytosanitary)
                }

                etapasPorFito[fito.idPhytosanitary] = etapasFito
                fotosRepresentativasPorFito[fito.idPhytosanitary] =
                    fotoRepresentativaFitoRegistro(fito, etapasFito)
            }
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

                fotosRepresentativasPorFito[fito.idPhytosanitary] =
                    fotoRepresentativaFitoRegistro(fito, etapasDb)

                if (esEnfermedadRegistro(fito.type)) {
                    // Solo Inicio, Desarrollo y Avanzado. Nunca contadores ni Terminal.
                    etapas = fasesEnfermedadPermitidas(etapasDb)
                    return@LaunchedEffect
                }

                val etapasOrdenadas = ordenarEtapasParaRegistro(
                    etapas = etapasDb,
                    tipoFito = fito.type
                )

                etapas = etapasOrdenadas

                if (etapasOrdenadas.isEmpty()) {
                    fitosSinEtapasSeleccionados[fito.idPhytosanitary] = true
                } else {
                    etapasOrdenadas.forEach { etapa ->
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
    val registrosPendientesEnfermedad = estadosEnfermedadPorFito.size
    val registrosPendientes =
        registrosPendientesPorEtapa + registrosPendientesSinEtapas + registrosPendientesEnfermedad

    val idsFitosRegistradosVisuales = remember(
        idsFitosGuardados,
        cantidadesPorEtapa.toMap(),
        fitosSinEtapasSeleccionados.toMap(),
        estadosEnfermedadPorFito.toMap()
    ) {
        buildSet {
            addAll(idsFitosGuardados)
            cantidadesPorEtapa
                .filterValues { cantidad -> cantidad > 0 }
                .keys
                .forEach { clave -> add(clave.idPhytosanitary) }
            fitosSinEtapasSeleccionados
                .filterValues { seleccionado -> seleccionado }
                .keys
                .forEach(::add)
            addAll(estadosEnfermedadPorFito.keys)
        }
    }

    /* Solo las plagas aportan una cantidad real al punto. */
    fun totalCantidadPuntoActual(): Int {
        val totalEtapas = cantidadesPorEtapa.values.sum()
        val presenciaGeneral = fitosSinEtapasSeleccionados.values.count { seleccionado -> seleccionado }
        return totalEtapas + presenciaGeneral
    }

    fun prioridadSeveridad(nivel: NivelSeveridad): Int {
        return when (nivel) {
            NivelSeveridad.VERDE -> 0
            NivelSeveridad.AMARILLO -> 1
            NivelSeveridad.NARANJA -> 2
            NivelSeveridad.ROJO -> 3
        }
    }

    /*
     * La enfermedad define su severidad por fase, no por qty:
     * Inicio = amarillo, Desarrollo = naranja, Avanzado = rojo.
     */
    fun nivelSeveridadEnfermedadesActual(): NivelSeveridad {
        return estadosEnfermedadPorFito.values
            .filter { it.presencia == PresenciaEnfermedadUi.PRESENTE }
            .map { estado ->
                when (estado.stage?.trim()?.lowercase()) {
                    "inicio" -> NivelSeveridad.AMARILLO
                    "desarrollo" -> NivelSeveridad.NARANJA
                    "avanzado", "avanzada" -> NivelSeveridad.ROJO
                    else -> NivelSeveridad.VERDE
                }
            }
            .maxByOrNull { prioridadSeveridad(it) }
            ?: NivelSeveridad.VERDE
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

        val idsEnfermedad = estadosEnfermedadPorFito.keys

        return (idsConCantidad + idsSinEtapas + idsEnfermedad).toSet()
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
            var fotoCreada: java.io.File? = null
            try {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
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

                    val capturedAt = System.currentTimeMillis()
                    val notasSinPlaga = observaciones
                        .trim()
                        .takeIf { it.isNotBlank() }



                    fotoCreada = fotoUriSeleccionada
                        ?.takeIf { it.isNotBlank() }
                        ?.let { photoUri ->
                            PhytoMediaStorage.guardarFotoPendiente(
                                context = context.applicationContext,
                                sourceUri = Uri.parse(photoUri),
                                idHeader = header.idHeader,
                                idTargetPoint = punto.idTargetPoint,
                                capturedAt = capturedAt
                            )
                        }

                    if (sinPlaga != null) {
                        val checkpoint = LocalPhytomonitoringCheckpointEntity(
                            qty = 0,
                            presenceStatus = null,
                            stage = null,
                            notes = notasSinPlaga,
                            photoRef = fotoCreada?.name,
                            photoLocalPath = fotoCreada?.absolutePath,
                            photoUrl = null,
                            capturedAt = capturedAt,
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
                }

                Toast.makeText(context, "Punto registrado sin plagas", Toast.LENGTH_SHORT).show()
                onGuardado()
            } catch (e: Exception) {
                fotoCreada?.delete()
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
        val enfermedadesPendientes = estadosEnfermedadPorFito.toMap()

        if (
            registrosConCantidad.isEmpty() &&
            fitosSinEtapasPendientes.isEmpty() &&
            enfermedadesPendientes.isEmpty() &&
            registrosAgregados <= 0
        ) {
            Toast.makeText(
                context,
                "Selecciona una plaga o evalúa una enfermedad antes de guardar.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val enfermedadesPresentesSinFase = enfermedadesPendientes.filterValues { estado ->
            estado.presencia == PresenciaEnfermedadUi.PRESENTE && estado.stage.isNullOrBlank()
        }

        if (enfermedadesPresentesSinFase.isNotEmpty()) {
            Toast.makeText(
                context,
                "Selecciona Inicio, Desarrollo o Avanzado para cada enfermedad presente.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        /* Los rangos M aplican únicamente cuando hay plagas con cantidad. */
        val hayPlagasConCantidad =
            registrosConCantidad.isNotEmpty() || fitosSinEtapasPendientes.isNotEmpty()
        val rangosPunto = rangosTextoValidosParaPunto()
        if (hayPlagasConCantidad && rangosPunto == null) {
            Toast.makeText(
                context,
                "Revisa la severidad del punto: la severidad mayor debe ser un número mayor a 0",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        finalizando = true

        coroutineScope.launch {
            var fotoCreada: java.io.File? = null
            try {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                    val ahora = System.currentTimeMillis()
                    val notasLimpias = observaciones
                        .trim()
                        .takeIf { it.isNotBlank() }
                    val notasParaGuardar: String? =
                        if (hayPlagasConCantidad && rangosPunto != null) {
                            agregarMetadataRangosSeveridad(
                                notas = notasLimpias,
                                rangos = rangosPunto
                            )
                        } else {
                            notasLimpias
                        }

                    // Una sola imagen por punto + timestamp. Todas las etapas guardadas
                    // en esta captura comparten photoRef y photoLocalPath.

                    fotoCreada = fotoUriSeleccionada
                        ?.takeIf { it.isNotBlank() }
                        ?.let { photoUri ->
                            PhytoMediaStorage.guardarFotoPendiente(
                                context = context.applicationContext,
                                sourceUri = Uri.parse(photoUri),
                                idHeader = header.idHeader,
                                idTargetPoint = punto.idTargetPoint,
                                capturedAt = ahora
                            )
                        }

                    registrosConCantidad.forEach { (clave, cantidad) ->
                        val checkpoint = LocalPhytomonitoringCheckpointEntity(
                            qty = cantidad,
                            presenceStatus = null,
                            stage = clave.stage,
                            notes = notasParaGuardar,
                            photoRef = fotoCreada?.name,
                            photoLocalPath = fotoCreada?.absolutePath,
                            photoUrl = null,
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
                            presenceStatus = null,
                            stage = null,
                            notes = notasParaGuardar,
                            photoRef = fotoCreada?.name,
                            photoLocalPath = fotoCreada?.absolutePath,
                            photoUrl = null,
                            capturedAt = ahora,
                            capturedByUserId = idUsuarioActual,
                            idTargetPoint = punto.idTargetPoint,
                            idHeader = header.idHeader,
                            idPhytosanitary = idPhytosanitary,
                            idLocalPlot = punto.idLocalPlot
                        )

                        database.localphytomonitoringcheckpointDao().insertCheckpoint(checkpoint)
                    }

                    enfermedadesPendientes.forEach { (idPhytosanitary, estado) ->
                        val presente = estado.presencia == PresenciaEnfermedadUi.PRESENTE

                        val checkpoint = LocalPhytomonitoringCheckpointEntity(
                            // qty=1 es técnico para el backend; no representa cantidad de enfermedad.
                            qty = if (presente) 1 else 0,
                            presenceStatus = if (presente) 1 else 0,
                            stage = if (presente) {
                                estado.stage?.trim()?.takeIf { it.isNotBlank() }
                            } else {
                                null
                            },
                            notes = notasParaGuardar,
                            photoRef = fotoCreada?.name,
                            photoLocalPath = fotoCreada?.absolutePath,
                            photoUrl = null,
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
                }

                Toast.makeText(context, "Punto finalizado correctamente", Toast.LENGTH_SHORT).show()
                onGuardado()
            } catch (e: Exception) {
                fotoCreada?.delete()
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

    val catalogoPlagas = catalogoPorTipoOrdenadoRegistro(
        catalogo = catalogo,
        tipoSeleccionado = TipoCatalogoRegistroUi.PLAGAS
    )
    val catalogoEnfermedades = catalogoPorTipoOrdenadoRegistro(
        catalogo = catalogo,
        tipoSeleccionado = TipoCatalogoRegistroUi.ENFERMEDADES
    )
    val catalogoVisible = when (tipoCatalogoSeleccionado) {
        TipoCatalogoRegistroUi.PLAGAS -> catalogoPlagas
        TipoCatalogoRegistroUi.ENFERMEDADES -> catalogoEnfermedades
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

                ElementoSeleccionadoCard(
                    fito = fitoSeleccionado,
                    fotoRepresentativa = fitoSeleccionado?.let {
                        fotosRepresentativasPorFito[it.idPhytosanitary]
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                val mostrandoPlagas =
                    tipoCatalogoSeleccionado == TipoCatalogoRegistroUi.PLAGAS

                if (mostrandoPlagas) {
                    val mayorTexto = severidadMayorPunto
                    val rangos = rangosSeveridadDesdeTexto(mayorTexto)
                        ?: RangosSeveridad()

                    val totalPuntoActual = totalCantidadPuntoActual()

                    /*
                     * Esta tarjeta representa solamente las plagas.
                     * Las enfermedades ya no deben mezclarse con este cálculo.
                     */
                    val nivelPlaga = calcularNivelSeveridad(
                        cantidadTotal = totalPuntoActual,
                        presenceStatus = if (totalPuntoActual > 0) 1 else 0,
                        rangos = rangos
                    )

                    SemaforoSeveridadCard(
                        mayorTexto = mayorTexto,
                        totalSeleccionado = totalPuntoActual,
                        nivelTexto = nivelPlaga.etiqueta,
                        colorNivel = nivelColorRegistro(nivelPlaga),
                        onMayorChange = { nuevo ->
                            severidadMayorPunto = nuevo

                            val mayor = nuevo.toIntOrNull()

                            if (mayor != null && mayor > 0) {
                                preferenciasSeveridad
                                    .edit()
                                    .putString(claveSeveridadMayor, nuevo)
                                    .apply()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

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
                        SelectorTipoCatalogoRegistro(
                            tipoSeleccionado = tipoCatalogoSeleccionado,
                            totalPlagas = catalogoPlagas.size,
                            totalEnfermedades = catalogoEnfermedades.size,
                            onTipoSeleccionado = { nuevoTipo ->
                                if (nuevoTipo != tipoCatalogoSeleccionado) {
                                    tipoCatalogoSeleccionado = nuevoTipo
                                    fitoSeleccionado = null
                                    etapas = emptyList()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (catalogoVisible.isEmpty()) {
                            val textoTipo = when (tipoCatalogoSeleccionado) {
                                TipoCatalogoRegistroUi.PLAGAS -> "plagas"
                                TipoCatalogoRegistroUi.ENFERMEDADES -> "enfermedades"
                            }

                            InfoBox(
                                text = "No hay $textoTipo cargadas para este monitoreo.",
                                isError = true
                            )
                        } else {
                            CatalogoPlagasHorizontal(
                                catalogo = catalogoVisible,
                                fitoSeleccionado = fitoSeleccionado,
                                idsFitosRegistrados = idsFitosRegistradosVisuales,
                                fotosRepresentativas = fotosRepresentativasPorFito,
                                onSelected = { item -> fitoSeleccionado = item }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                fitoSeleccionado?.let { fito ->
                    when {
                        esEnfermedadRegistro(fito.type) -> {
                            val estadoActual = estadosEnfermedadPorFito[fito.idPhytosanitary]

                            PresenciaFaseEnfermedadCard(
                                presencia = estadoActual?.presencia,
                                fases = etapas,
                                faseSeleccionada = estadoActual?.stage,
                                onNoPresente = {
                                    estadosEnfermedadPorFito[fito.idPhytosanitary] =
                                        EstadoEnfermedadUi(
                                            presencia = PresenciaEnfermedadUi.NO_PRESENTE,
                                            stage = null
                                        )
                                },
                                onPresente = {
                                    val faseAnterior = estadoActual
                                        ?.takeIf { it.presencia == PresenciaEnfermedadUi.PRESENTE }
                                        ?.stage

                                    estadosEnfermedadPorFito[fito.idPhytosanitary] =
                                        EstadoEnfermedadUi(
                                            presencia = PresenciaEnfermedadUi.PRESENTE,
                                            stage = faseAnterior
                                        )
                                },
                                onFaseSeleccionada = { fase ->
                                    estadosEnfermedadPorFito[fito.idPhytosanitary] =
                                        EstadoEnfermedadUi(
                                            presencia = PresenciaEnfermedadUi.PRESENTE,
                                            stage = fase.stage
                                        )
                                }
                            )
                        }

                        etapas.isEmpty() -> {
                            InfoBox(
                                text = "${textoTipoFitoRegistro(fito.type)} seleccionada sin etapas. Se guardará como presencia general."
                            )
                        }

                        else -> {
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle(title = "Observaciones")

                Spacer(modifier = Modifier.height(8.dp))

                ObservacionesBox(
                    value = observaciones,
                    onValueChange = { observaciones = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                EvidenciaFotograficaCard(
                    photoUri = fotoUriSeleccionada,
                    enabled = !finalizando,
                    onTomarFoto = {
                        val permisoConcedido = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (permisoConcedido) {
                            mostrarCamaraTrasera = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onElegirImagen = {
                        imagePickerLauncher.launch("image/*")
                    },
                    onEliminarFoto = {
                        fotoUriSeleccionada = null
                    }
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

@Composable
private fun SelectorTipoCatalogoRegistro(
    tipoSeleccionado: TipoCatalogoRegistroUi,
    totalPlagas: Int,
    totalEnfermedades: Int,
    onTipoSeleccionado: (TipoCatalogoRegistroUi) -> Unit
) {
    fun esSeleccionado(tipo: TipoCatalogoRegistroUi): Boolean {
        return tipoSeleccionado == tipo
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = {
                onTipoSeleccionado(TipoCatalogoRegistroUi.PLAGAS)
            },
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .border(
                    width = 1.dp,
                    color = if (esSeleccionado(TipoCatalogoRegistroUi.PLAGAS)) {
                        Color(0xFF0B6B20)
                    } else {
                        Color(0xFFD0D7DE)
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (esSeleccionado(TipoCatalogoRegistroUi.PLAGAS)) {
                    Color(0xFF0B6B20)
                } else {
                    Color.White
                }
            )
        ) {
            Text(
                text = " Plagas ($totalPlagas)",
                color = if (esSeleccionado(TipoCatalogoRegistroUi.PLAGAS)) {
                    Color.White
                } else {
                    Color(0xFF1D2430)
                },
                fontWeight = FontWeight.Black
            )
        }

        Button(
            onClick = {
                onTipoSeleccionado(TipoCatalogoRegistroUi.ENFERMEDADES)
            },
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .border(
                    width = 1.dp,
                    color = if (esSeleccionado(TipoCatalogoRegistroUi.ENFERMEDADES)) {
                        Color(0xFF2E7D32)
                    } else {
                        Color(0xFFD0D7DE)
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (esSeleccionado(TipoCatalogoRegistroUi.ENFERMEDADES)) {
                    Color(0xFF2E7D32)
                } else {
                    Color.White
                }
            )
        ) {
            Text(
                text = " Enfermedades ($totalEnfermedades)",
                color = if (esSeleccionado(TipoCatalogoRegistroUi.ENFERMEDADES)) {
                    Color.White
                } else {
                    Color(0xFF1D2430)
                },
                fontWeight = FontWeight.Black
            )
        }
    }
}

private enum class TipoConfirmacionGuardado {
    SIN_PLAGA,
    CON_REGISTRO
}

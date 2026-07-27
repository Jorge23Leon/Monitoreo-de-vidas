package com.example.myapplication.local.monitoreo.mapa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.api.phytomonitoring.PhytoMonitoringRepository
import com.example.myapplication.local.api.phytomonitoring.ResultadoActualizarHeaderApi
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringHeaderEntity
import com.example.myapplication.local.entities.LocalPhytomonitoringTargetPointEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.monitoreo.fechaCierreProgramadaMonitoreoMs
import com.example.myapplication.local.monitoreo.tiempoMonitoreoAgotado
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun MonitoreoMapaScreen(
    database: AppDatabase,
    header: LocalPhytomonitoringHeaderEntity,
    nombreUsuario: String,
    rolUsuario: String = "",
    nombreMonitoreo: String,
    mostrarMapaCompletoInicial: Boolean = false,
    onModoMapaCompletoChange: (Boolean) -> Unit = {},
    onPuntoValidoClick: (Long) -> Unit,
    onMonitoreoActualizado: (String) -> Unit,
    onBackClick: () -> Unit = {},
    onPerfilClick: () -> Unit = {},
    onCerrarSesionClick: () -> Unit,
    onMonitoreosClick: () -> Unit = {},
    onCambiarCiaClick: (() -> Unit)? = null,
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val onPuntoValidoActual by rememberUpdatedState(onPuntoValidoClick)
    val onMonitoreoActualizadoActual by rememberUpdatedState(onMonitoreoActualizado)

    val phytoMonitoringRepository = remember(context.applicationContext) {
        PhytoMonitoringRepository(context.applicationContext)
    }

    var headerActual by remember(header.idHeader) {
        mutableStateOf(header)
    }

    var vertices by remember {
        mutableStateOf<List<LocalPlotVertexEntity>>(emptyList())
    }

    var puntos by remember {
        mutableStateOf<List<LocalPhytomonitoringTargetPointEntity>>(emptyList())
    }

    var checkpoints by remember {
        mutableStateOf<List<LocalPhytomonitoringCheckpointEntity>>(emptyList())
    }

    var catalogo by remember {
        mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList())
    }

    var programaActual by remember(header.idProgram) {
        mutableStateOf<LocalProgramEntity?>(null)
    }

    var ubicacionGps by remember {
        mutableStateOf<UbicacionGpsMapa?>(null)
    }

    val ubicacionUsuario = ubicacionGps?.coordenadasVisuales

    var mostrarMapaPantallaCompleta by remember(header.idHeader) {
        mutableStateOf(mostrarMapaCompletoInicial)
    }

    fun actualizarModoMapa(completo: Boolean) {
        if (mostrarMapaPantallaCompleta == completo) return
        mostrarMapaPantallaCompleta = completo
        onModoMapaCompletoChange(completo)
    }

    var puntoLibreSeleccionado by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }

    var internetDisponible by remember {
        mutableStateOf(hayInternet(context))
    }

    var cargando by remember {
        mutableStateOf(true)
    }

    var finalizandoMonitoreo by remember {
        mutableStateOf(false)
    }

    var creandoPuntoLibre by remember {
        mutableStateOf(false)
    }

    var accionDialogoMapa by remember {
        mutableStateOf<AccionDialogoMapa?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var ahoraMs by remember {
        mutableStateOf(System.currentTimeMillis())
    }

    val fechaCierreProgramadaMs = fechaCierreProgramadaMonitoreoMs(
        programa = programaActual
    )

    var tienePermisoUbicacion by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permisoUbicacionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        tienePermisoUbicacion = granted

        if (granted) {
            ubicacionGps = obtenerUltimaUbicacion(context)
        }
    }

    fun esEstadoPendienteLocal(status: String): Boolean {
        return status.trim().equals("Pendiente", ignoreCase = true) ||
                status.trim().equals("pending", ignoreCase = true)
    }

    fun fechaApiUtc(millis: Long?): String? {
        if (millis == null) return null

        return SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }

    fun notasAlCerrar(notas: String): String {
        return if (notas.trim().equals("PAUSADO", ignoreCase = true)) {
            ""
        } else {
            notas
        }
    }

    /**
     * El toque al mapa solo funciona como una confirmación de intención.
     * La coordenada que se guarda siempre es la ubicación GPS obtenida por Android.
     */
    fun ubicacionGpsEstaDentroDeParcela(
        ubicacionGps: Pair<Double, Double>
    ): Boolean {
        if (vertices.size < 3) return true

        val latitud = ubicacionGps.first
        val longitud = ubicacionGps.second
        var dentro = false
        var indiceAnterior = vertices.lastIndex

        vertices.indices.forEach { indiceActual ->
            val verticeActual = vertices[indiceActual]
            val verticeAnterior = vertices[indiceAnterior]

            val intersecta =
                ((verticeActual.lat > latitud) != (verticeAnterior.lat > latitud)) &&
                        (longitud <
                                (verticeAnterior.lon - verticeActual.lon) *
                                (latitud - verticeActual.lat) /
                                ((verticeAnterior.lat - verticeActual.lat)
                                    .takeIf { it != 0.0 } ?: 0.000000001) +
                                verticeActual.lon)

            if (intersecta) dentro = !dentro
            indiceAnterior = indiceActual
        }

        return dentro
    }

    /**
     * Room se actualiza primero para que el trabajo de campo no se pierda.
     * Cuando hay red, el mismo cambio se manda al PATCH de Django.
     * Si falla, MonitoreoSyncRepository lo reintentará antes de volver a descargar headers.
     */
    suspend fun sincronizarEstadoHeaderServidor(
        headerLocal: LocalPhytomonitoringHeaderEntity,
        statusApi: String
    ): String? {
        val idHeaderExt = headerLocal.extId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return "El monitoreo no tiene ext_id para enviarlo al servidor."

        if (!hayInternet(context)) {
            return "Sin conexión: el estado quedó guardado localmente y se reenviará al sincronizar."
        }

        return withContext(Dispatchers.IO) {
            when (
                val resultado = phytoMonitoringRepository.actualizarHeaderServidor(
                    idHeaderExt = idHeaderExt,
                    status = statusApi,
                    startedAt = fechaApiUtc(headerLocal.startAt),
                    finishedAt = fechaApiUtc(headerLocal.finishedAt),
                    additionalNotes = headerLocal.additionalNotes
                )
            ) {
                is ResultadoActualizarHeaderApi.Exito -> {
                    database.localphytomonitoringheaderDao()
                        .marcarHeaderSincronizado(headerLocal.idHeader)
                    null
                }
                is ResultadoActualizarHeaderApi.Error -> resultado.mensaje
            }
        }
    }

    suspend fun cargarDatosMapa() {
        cargando = true
        error = null
        internetDisponible = hayInternet(context)

        try {
            /*
             * Abrir el mapa NO inicia el monitoreo.
             * El header permanece pendiente hasta confirmar el primer punto.
             */
            val resultado = withContext(Dispatchers.IO) {
                val headerFresco = database.localphytomonitoringheaderDao()
                    .getHeaderById(header.idHeader) ?: header

                MapaCargaResultado(
                    header = headerFresco,
                    programa = database.localprogramDao()
                        .getProgramById(headerFresco.idProgram),
                    vertices = database.LocalPlotVertexDao()
                        .getVerticesByPlot(header.idLocalPlot),
                    puntos = database.LocalPhytomonitoringTargetPointDao()
                        .getTargetPointsByHeader(header.idHeader),
                    checkpoints = database.localphytomonitoringcheckpointDao()
                        .getCheckpointsByHeader(header.idHeader),
                    catalogo = database.localphytosanitarycatalogDao()
                        .getAllCatalogo()
                )
            }

            headerActual = resultado.header
            programaActual = resultado.programa
            vertices = resultado.vertices
            puntos = resultado.puntos
            checkpoints = resultado.checkpoints
            catalogo = resultado.catalogo
        } catch (e: Exception) {
            error = "Error al cargar mapa: ${e.message}"
        } finally {
            cargando = false
        }
    }

    fun pausarMonitoreo() {
        if (finalizandoMonitoreo || creandoPuntoLibre) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val actualizado = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    val inicioReal = fresco.startAt ?: System.currentTimeMillis()
                    val nuevoHeader = fresco.copy(
                        // Pausado no es Pendiente: el ciclo sigue siendo in_progress.
                        status = "En proceso",
                        startAt = inicioReal,
                        finishedAt = null,
                        additionalNotes = "PAUSADO",
                        syncPending = true
                    )

                    database.localphytomonitoringheaderDao().updateHeader(nuevoHeader)
                    nuevoHeader
                }

                val errorServidor = sincronizarEstadoHeaderServidor(
                    headerLocal = actualizado,
                    statusApi = "in_progress"
                )

                headerActual = actualizado
                accionDialogoMapa = null

                Toast.makeText(
                    context,
                    if (errorServidor == null) {
                        "Monitoreo pausado y enviado al servidor"
                    } else {
                        "Monitoreo pausado localmente. Se reenviará al sincronizar."
                    },
                    Toast.LENGTH_SHORT
                ).show()

                onMonitoreoActualizadoActual("Pausado")
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al pausar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    fun continuarMonitoreo() {
        if (finalizandoMonitoreo || creandoPuntoLibre) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val actualizado = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    val nuevoHeader = fresco.copy(
                        status = "En proceso",
                        startAt = fresco.startAt ?: System.currentTimeMillis(),
                        finishedAt = null,
                        additionalNotes = "",
                        syncPending = true
                    )

                    database.localphytomonitoringheaderDao().updateHeader(nuevoHeader)
                    nuevoHeader
                }

                val errorServidor = sincronizarEstadoHeaderServidor(
                    headerLocal = actualizado,
                    statusApi = "in_progress"
                )

                headerActual = actualizado

                Toast.makeText(
                    context,
                    if (errorServidor == null) {
                        "Monitoreo reanudado y enviado al servidor"
                    } else {
                        "Monitoreo reanudado localmente. Se reenviará al sincronizar."
                    },
                    Toast.LENGTH_SHORT
                ).show()

                cargarDatosMapa()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al continuar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    fun terminarMonitoreo() {
        if (finalizandoMonitoreo || creandoPuntoLibre) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val terminadoMs = System.currentTimeMillis()

                val actualizado = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    val nuevoHeader = fresco.copy(
                        status = "Completado",
                        finishedAt = terminadoMs,
                        additionalNotes = notasAlCerrar(fresco.additionalNotes),
                        syncPending = true
                    )

                    database.localphytomonitoringheaderDao().updateHeader(nuevoHeader)
                    database.localprogramDao().recalcularEstadoDesdeHeaders(nuevoHeader.idProgram)
                    nuevoHeader
                }

                /*
                 * No cerramos el header remoto todavía. El backend bloquea
                 * checkpoints/fotos cuando status=completed. ReporteMonitoreoScreen
                 * hará la sincronización completa y el PATCH final al terminar.
                 */
                headerActual = actualizado
                accionDialogoMapa = null

                Toast.makeText(
                    context,
                    "Monitoreo terminado localmente. Falta sincronizar capturas y evidencias desde el reporte.",
                    Toast.LENGTH_LONG
                ).show()

                onMonitoreoActualizadoActual("Completado")
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error al terminar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    fun cerrarMonitoreoAutomaticamentePorTiempo() {
        if (finalizandoMonitoreo || creandoPuntoLibre) return

        val cierreAutomaticoMs = fechaCierreProgramadaMs ?: return

        if (System.currentTimeMillis() < cierreAutomaticoMs) return

        finalizandoMonitoreo = true

        coroutineScope.launch {
            try {
                val actualizado = withContext(Dispatchers.IO) {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    if (esEstadoCerradoMapa(fresco.status)) {
                        null
                    } else {
                        val programaFresco = database.localprogramDao()
                            .getProgramById(fresco.idProgram)
                        val cierreMs = fechaCierreProgramadaMonitoreoMs(
                            programa = programaFresco
                        ) ?: return@withContext null

                        val notaAnterior = fresco.additionalNotes.trim()
                        val notaAutomatica =
                            "CERRADO_AUTOMATICO: Se cerró automáticamente porque se agotó el tiempo del monitoreo."

                        val notaFinal = when {
                            notaAnterior.isBlank() -> notaAutomatica
                            notaAnterior.equals("PAUSADO", ignoreCase = true) -> notaAutomatica
                            notaAnterior.contains("CERRADO_AUTOMATICO", ignoreCase = true) -> notaAnterior
                            else -> "$notaAnterior\n$notaAutomatica"
                        }

                        val nuevoHeader = fresco.copy(
                            status = "Completado",
                            finishedAt = cierreMs,
                            additionalNotes = notaFinal,
                            syncPending = true
                        )

                        database.localphytomonitoringheaderDao().updateHeader(nuevoHeader)
                        database.localprogramDao().recalcularEstadoDesdeHeaders(nuevoHeader.idProgram)
                        nuevoHeader
                    }
                }

                if (actualizado != null) {
                    /*
                     * Igual que el cierre manual: se conserva completed solo en Room
                     * hasta que el reporte suba checkpoints, targets y fotos.
                     */
                    headerActual = actualizado
                    accionDialogoMapa = null
                    puntoLibreSeleccionado = null

                    Toast.makeText(
                        context,
                        "Tiempo agotado. El cierre quedó local; sincroniza las capturas desde el reporte.",
                        Toast.LENGTH_LONG
                    ).show()

                    onMonitoreoActualizadoActual("Completado")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "No se pudo cerrar por tiempo: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finalizandoMonitoreo = false
            }
        }
    }

    val idsPuntosCapturados = remember(
        checkpoints,
        puntos,
        headerActual.idHeader
    ) {
        val idsPuntosActuales = puntos
            .map { it.idTargetPoint }
            .toSet()

        checkpoints
            .filter { checkpoint ->
                checkpoint.idHeader == headerActual.idHeader &&
                        checkpoint.idTargetPoint in idsPuntosActuales
            }
            .map { it.idTargetPoint }
            .distinct()
            .toSet()
    }

    val puntosCapturados = idsPuntosCapturados.size
    val numeroSiguientePunto = puntosCapturados + 1

    val estaPausado = esMonitoreoPausadoMapa(
        status = headerActual.status,
        additionalNotes = headerActual.additionalNotes
    )

    val estaCerrado = esEstadoCerradoMapa(headerActual.status)

    val tiempoAgotado = tiempoMonitoreoAgotado(
        fechaCierreProgramadaMs = fechaCierreProgramadaMs,
        ahoraMs = ahoraMs
    )

    fun crearPuntoLibreYRegistrar() {
        /*
         * puntoLibreSeleccionado contiene una captura de la ubicación GPS real
         * tomada cuando la persona tocó el mapa. Nunca contiene la coordenada
         * visual del toque sobre el mapa.
         */
        val ubicacionGpsSeleccionada = puntoLibreSeleccionado ?: run {
            Toast.makeText(
                context,
                "Aún no se obtiene tu ubicación GPS. Espera unos segundos e inténtalo de nuevo.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!ubicacionGpsEstaDentroDeParcela(ubicacionGpsSeleccionada)) {
            Toast.makeText(
                context,
                "Tu ubicación GPS actual está fuera de la parcela. Acércate al área verde para registrar el punto.",
                Toast.LENGTH_LONG
            ).show()
            puntoLibreSeleccionado = null
            return
        }

        if (creandoPuntoLibre || finalizandoMonitoreo) return

        if (tiempoAgotado) {
            cerrarMonitoreoAutomaticamentePorTiempo()
            Toast.makeText(
                context,
                "El tiempo del monitoreo ya se agotó",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (estaPausado || estaCerrado) {
            Toast.makeText(
                context,
                "Este monitoreo no permite crear puntos en este estado",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        creandoPuntoLibre = true

        coroutineScope.launch {
            try {
                val resultadoCreacion = withContext(Dispatchers.IO) {
                    database.withTransaction {
                    val fresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(headerActual.idHeader) ?: headerActual

                    if (esEstadoCerradoMapa(fresco.status)) {
                        throw IllegalStateException("El monitoreo ya está cerrado")
                    }

                    if (esMonitoreoPausadoMapa(fresco.status, fresco.additionalNotes)) {
                        throw IllegalStateException("El monitoreo está pausado")
                    }

                    /*
                     * Este es el único punto donde un header pendiente se inicia.
                     * Ocurre después de que la persona tocó la parcela y confirmó.
                     */
                    val estabaPendiente = esEstadoPendienteLocal(fresco.status)

                    if (estabaPendiente) {
                        database.localphytomonitoringheaderDao()
                            .iniciarMonitoreoSiEstaPendiente(
                                idHeader = fresco.idHeader,
                                now = System.currentTimeMillis()
                            )
                    }

                    val headerDespuesDeInicio = database.localphytomonitoringheaderDao()
                        .getHeaderById(fresco.idHeader) ?: fresco

                    val idNuevoPunto = database.LocalPhytomonitoringTargetPointDao()
                        .insertTargetPoint(
                            LocalPhytomonitoringTargetPointEntity(
                                extId = null,
                                radiusM = 5,
                                // Se guarda la ubicación GPS real; no la coordenada del toque visual.
                                lat = ubicacionGpsSeleccionada.first,
                                lon = ubicacionGpsSeleccionada.second,
                                status = "En proceso",
                                idHeader = headerDespuesDeInicio.idHeader,
                                idLocalPlot = headerDespuesDeInicio.idLocalPlot
                            )
                        )

                    PuntoLibreCreadoResultado(
                        idTargetPoint = idNuevoPunto,
                        headerActualizado = headerDespuesDeInicio,
                        inicioConfirmado = estabaPendiente &&
                                !esEstadoPendienteLocal(headerDespuesDeInicio.status)
                    )
                    }
                }

                headerActual = resultadoCreacion.headerActualizado

                if (resultadoCreacion.inicioConfirmado) {
                    val errorServidor = sincronizarEstadoHeaderServidor(
                        headerLocal = resultadoCreacion.headerActualizado,
                        statusApi = "in_progress"
                    )

                    if (errorServidor != null) {
                        Log.w(
                            "PHYTO_STATUS",
                            "Inicio guardado localmente; se reenviará al sincronizar: $errorServidor"
                        )
                    }
                }

                puntoLibreSeleccionado = null
                onPuntoValidoActual(resultadoCreacion.idTargetPoint)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "No se pudo crear el punto: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                creandoPuntoLibre = false
            }
        }
    }

    LaunchedEffect(headerActual.idHeader, estaCerrado) {
        while (!estaCerrado) {
            ahoraMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(
        headerActual.idHeader,
        tiempoAgotado,
        estaCerrado,
        finalizandoMonitoreo
    ) {
        if (tiempoAgotado && !estaCerrado && !finalizandoMonitoreo) {
            cerrarMonitoreoAutomaticamentePorTiempo()
        }
    }

    val internetInicial = remember(header.idHeader) {
        hayInternet(context)
    }

    val htmlMapa = remember(
        vertices,
        puntos,
        checkpoints,
        catalogo,
        nombreMonitoreo,
        internetInicial
    ) {
        crearHtmlMapaMonitoreo(
            nombreMonitoreo = nombreMonitoreo,
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            catalogo = catalogo,
            ubicacionInicial = null,
            internetDisponible = internetInicial,
            modoVistaPrevia = false
        )
    }

    val htmlMapaVistaPrevia = remember(
        vertices,
        puntos,
        checkpoints,
        catalogo,
        nombreMonitoreo,
        internetInicial
    ) {
        crearHtmlMapaMonitoreo(
            nombreMonitoreo = nombreMonitoreo,
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            catalogo = catalogo,
            ubicacionInicial = null,
            internetDisponible = internetInicial,
            modoVistaPrevia = true
        )
    }

    fun solicitarPuntoDesdeMapa() {
        if (tiempoAgotado || estaPausado || estaCerrado) {
            Toast.makeText(
                context,
                "Este monitoreo no permite capturar puntos en este estado",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val ubicacionGpsActual = ubicacionGps
        if (!ubicacionGpsListaParaCaptura(ubicacionGpsActual)) {
            val mensajeGps = when {
                ubicacionGpsActual == null -> {
                    "Buscando una ubicación GPS precisa. Espera unos segundos e inténtalo nuevamente."
                }

                ubicacionGpsActual.precisionMetros > PRECISION_GPS_MAXIMA_CAPTURA_M -> {
                    "La precisión GPS actual es de aproximadamente " +
                            "${ubicacionGpsActual.precisionMetros.toInt()} m. " +
                            "Espera a que mejore antes de registrar el punto."
                }

                else -> {
                    "La ubicación GPS no es reciente. Espera una nueva lectura."
                }
            }

            Toast.makeText(context, mensajeGps, Toast.LENGTH_LONG).show()
            return
        }

        val ubicacionGpsConfirmada = ubicacionGpsActual ?: return
        val coordenadasGps = ubicacionGpsConfirmada.coordenadasCaptura
        if (!ubicacionGpsEstaDentroDeParcela(coordenadasGps)) {
            Toast.makeText(
                context,
                "Tu ubicación GPS actual está fuera de la parcela. No se puede registrar un punto fuera del área verde.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        puntoLibreSeleccionado = coordenadasGps
    }

    fun solicitarRegreso() {
        when {
            cargando || finalizandoMonitoreo || creandoPuntoLibre -> Unit
            estaCerrado -> onBackClick()
            estaPausado -> onBackClick()
            puntosCapturados <= 0 -> accionDialogoMapa = AccionDialogoMapa.REGRESAR
            else -> accionDialogoMapa = AccionDialogoMapa.REGRESAR
        }
    }

    BackHandler(enabled = true) {
        solicitarRegreso()
    }

    LaunchedEffect(header.idHeader) {
        cargarDatosMapa()
    }

    LaunchedEffect(Unit) {
        if (!tienePermisoUbicacion) {
            permisoUbicacionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            ubicacionGps = obtenerUltimaUbicacion(context)
        }
    }

    DisposableEffect(tienePermisoUbicacion) {
        if (!tienePermisoUbicacion) {
            onDispose { }
        } else {
            val fusedLocationClient = LocationServices
                .getFusedLocationProviderClient(context)

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1_500L
            )
                .setMinUpdateIntervalMillis(900L)
                .setMinUpdateDistanceMeters(0.5f)
                .setMaxUpdateAgeMillis(0L)
                .setWaitForAccurateLocation(true)
                .setGranularity(Granularity.GRANULARITY_FINE)
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.locations.forEach { location ->
                        filtrarUbicacionGpsMapa(
                            anterior = ubicacionGps,
                            nueva = location
                        )?.let { ubicacionFiltrada ->
                            ubicacionGps = ubicacionFiltrada
                        }
                    }
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                ).addOnFailureListener { errorGps ->
                    Log.e(
                        "MAPA_GPS",
                        "No se pudieron iniciar las actualizaciones GPS",
                        errorGps
                    )
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onDispose {
                try {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (mostrarMapaPantallaCompleta) {
        Dialog(
            onDismissRequest = {
                if (!creandoPuntoLibre) {
                    puntoLibreSeleccionado = null
                    actualizarModoMapa(false)
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                CabeceraMapaCompleto(
                    nombreMonitoreo = nombreMonitoreo,
                    onCerrar = {
                        if (!creandoPuntoLibre) {
                            puntoLibreSeleccionado = null
                            actualizarModoMapa(false)
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFFEAF5E8))
                ) {
                    MapaMonitoreoWebViewSeguro(
                        modifier = Modifier.fillMaxSize(),
                        htmlMapa = htmlMapa,
                        ubicacionUsuario = ubicacionUsuario,
                        precisionGpsMetros = ubicacionGps?.precisionMetros,
                        puntoLibreSeleccionado = puntoLibreSeleccionado,
                        internetDisponible = internetDisponible,
                        onInternetDisponibleChange = {
                            // La capa cambia sin reconstruir el WebView.
                        },
                        onPuntoLibreSeleccionado = { _, _ ->
                            solicitarPuntoDesdeMapa()
                        }
                    )

                    puntoLibreSeleccionado?.let {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            ConfirmarPuntoLibreCard(
                                numeroPunto = numeroSiguientePunto,
                                creandoPunto = creandoPuntoLibre,
                                onConfirmarClick = {
                                    crearPuntoLibreYRegistrar()
                                },
                                onCancelarClick = {
                                    puntoLibreSeleccionado = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /*
     * El mapa pequeño también puede iniciar el mismo flujo de captura.
     * La confirmación se muestra como diálogo para que no quede limitada por
     * la altura de la vista previa ni choque con la navegación del teléfono.
     */
    if (puntoLibreSeleccionado != null && !mostrarMapaPantallaCompleta) {
        Dialog(
            onDismissRequest = {
                if (!creandoPuntoLibre) {
                    puntoLibreSeleccionado = null
                }
            }
        ) {
            ConfirmarPuntoLibreCard(
                numeroPunto = numeroSiguientePunto,
                creandoPunto = creandoPuntoLibre,
                onConfirmarClick = {
                    crearPuntoLibreYRegistrar()
                },
                onCancelarClick = {
                    puntoLibreSeleccionado = null
                }
            )
        }
    }

    accionDialogoMapa?.let { accion ->
        DialogoAccionMapa(
            accion = accion,
            totalPuntos = puntos.size,
            puntosCapturados = puntosCapturados,
            finalizandoMonitoreo = finalizandoMonitoreo,
            onDismiss = {
                accionDialogoMapa = null
            },
            onPausarConfirmado = {
                pausarMonitoreo()
            },
            onTerminarConfirmado = {
                terminarMonitoreo()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick,
                onCambiarCiaClick = onCambiarCiaClick,
                onCerrarSesionClick = onCerrarSesionClick
            )

            BarraMapaMonitoreo(
                nombreMonitoreo = nombreMonitoreo,
                status = headerActual.status,
                additionalNotes = headerActual.additionalNotes,
                fechaCierreProgramadaMs = fechaCierreProgramadaMs,
                ahoraMs = ahoraMs,
                onRegresarClick = {
                    solicitarRegreso()
                }
            )

            when {
                cargando -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF0B6B20))

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Cargando mapa...",
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                error != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Text(
                            text = error ?: "Error desconocido",
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    InstruccionMapaLibre(
                        puntosCapturados = puntosCapturados
                    )

                    ChipPuntosMonitoreados(
                        puntosCapturados = puntosCapturados
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color(0xFFEAF5E8))
                            ) {
                                if (!mostrarMapaPantallaCompleta) {
                                    MapaMonitoreoWebViewSeguro(
                                        modifier = Modifier.fillMaxSize(),
                                        htmlMapa = htmlMapaVistaPrevia,
                                        ubicacionUsuario = ubicacionUsuario,
                                        precisionGpsMetros = ubicacionGps?.precisionMetros,
                                        puntoLibreSeleccionado = puntoLibreSeleccionado,
                                        internetDisponible = internetDisponible,
                                        onInternetDisponibleChange = {
                                            // La capa cambia sin reconstruir el WebView.
                                        },
                                        onPuntoLibreSeleccionado = { _, _ ->
                                            solicitarPuntoDesdeMapa()
                                        }
                                    )
                                }
                            }

                            AbrirMapaCompletoCard(
                                onAbrirMapa = {
                                    puntoLibreSeleccionado = null
                                    actualizarModoMapa(true)
                                }
                            )
                        }
                    }

                    if (!estaCerrado && !tiempoAgotado) {
                        AccionesMapaMonitoreo(
                            totalPuntos = puntos.size,
                            puntosCapturados = puntosCapturados,
                            estaPausado = estaPausado,
                            finalizandoMonitoreo = finalizandoMonitoreo,
                            onPausarClick = {
                                accionDialogoMapa = AccionDialogoMapa.PAUSAR
                            },
                            onTerminarClick = {
                                accionDialogoMapa = AccionDialogoMapa.TERMINAR
                            },
                            onContinuarClick = {
                                continuarMonitoreo()
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class MapaCargaResultado(
    val header: LocalPhytomonitoringHeaderEntity,
    val programa: LocalProgramEntity?,
    val vertices: List<LocalPlotVertexEntity>,
    val puntos: List<LocalPhytomonitoringTargetPointEntity>,
    val checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    val catalogo: List<LocalPhytosanitaryCatalogEntity>
)

private data class PuntoLibreCreadoResultado(
    val idTargetPoint: Long,
    val headerActualizado: LocalPhytomonitoringHeaderEntity,
    val inicioConfirmado: Boolean
)

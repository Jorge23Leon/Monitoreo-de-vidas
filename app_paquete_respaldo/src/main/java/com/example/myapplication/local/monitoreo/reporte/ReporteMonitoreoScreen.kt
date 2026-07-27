package com.example.myapplication.local.monitoreo.reporte

import android.widget.Toast
import java.io.File
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.api.phytomonitoring.PhytoCheckpointSyncRepository
import com.example.myapplication.local.api.phytomonitoring.ResultadoCheckpointSync
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
import com.example.myapplication.local.monitoreo.media.PhytoMediaStorage
import com.example.myapplication.local.monitoreo.severidad.calcularSeveridadPorPunto
import com.example.myapplication.local.monitoreo.severidad.limpiarMetadataRangosSeveridad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import java.security.MessageDigest


private object EstadoSincronizacionReporte {
    const val PENDIENTE = "PENDIENTE"
    const val SINCRONIZANDO = "SINCRONIZANDO"
    const val SINCRONIZADO = "SINCRONIZADO"
    const val ERROR = "ERROR"
}
private const val PREFS_SYNC_REPORTES =
    "estado_sincronizacion_reportes"

private fun claveFirmaReporte(idHeader: Long): String {
    return "firma_reporte_$idHeader"
}

private fun claveMensajeReporte(idHeader: Long): String {
    return "mensaje_reporte_$idHeader"
}

private fun crearFirmaContenidoReporte(
    header: LocalPhytomonitoringHeaderEntity,
    puntos: List<LocalPhytomonitoringTargetPointEntity>,
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    fotosPendientes: Int
): String {
    val puntosPorId = puntos.associateBy { it.idTargetPoint }

    val contenido = buildString {
        appendLine(
            listOf(
                "H",
                header.extId.orEmpty(),
                header.status.trim().lowercase(),
                header.startAt?.toString().orEmpty(),
                header.finishedAt?.toString().orEmpty(),
                header.syncPending.toString(),
                fotosPendientes.toString()
            ).joinToString("|")
        )

        puntos
            .sortedWith(
                compareBy<LocalPhytomonitoringTargetPointEntity> {
                    it.lat
                }.thenBy {
                    it.lon
                }.thenBy {
                    it.idTargetPoint
                }
            )
            .forEach { punto ->
                append("P|")
                append(punto.lat)
                append('|')
                append(punto.lon)
                append('|')
                append(punto.radiusM)
                append('|')
                append(punto.status.trim().lowercase())
                append('|')
                append(punto.extId.orEmpty())
                appendLine()
            }

        checkpoints
            .map { checkpoint ->
                val punto = puntosPorId[checkpoint.idTargetPoint]

                val tieneFoto =
                    !checkpoint.photoRef.isNullOrBlank() ||
                            !checkpoint.photoUrl.isNullOrBlank() ||
                            !checkpoint.photoLocalPath.isNullOrBlank()

                listOf(
                    "C",
                    punto?.lat?.toString().orEmpty(),
                    punto?.lon?.toString().orEmpty(),
                    checkpoint.idPhytosanitary?.toString().orEmpty(),
                    checkpoint.stage?.trim().orEmpty(),
                    checkpoint.presenceStatus?.toString().orEmpty(),
                    checkpoint.qty?.toString().orEmpty(),
                    checkpoint.notes?.trim().orEmpty(),
                    checkpoint.capturedAt?.toString().orEmpty(),
                    tieneFoto.toString(),
                    checkpoint.extId.orEmpty(),
                    checkpoint.photoRef.orEmpty(),
                    checkpoint.photoUrl.orEmpty()
                ).joinToString("|")
            }
            .sorted()
            .forEach { fila ->
                appendLine(fila)
            }
    }

    val bytes = MessageDigest
        .getInstance("SHA-256")
        .digest(contenido.toByteArray(Charsets.UTF_8))

    return bytes.joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
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
    val coroutineScope = rememberCoroutineScope()

    val preferenciasSync = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences(
            PREFS_SYNC_REPORTES,
            Context.MODE_PRIVATE
        )
    }
    // CAMBIO CSV: solicita permiso una vez para poder mostrar notificaciones.
    val permisoNotificacionesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permitido ->
        if (!permitido) {
            Toast.makeText(
                context,
                "Activa las notificaciones para ver el aviso de descarga del CSV.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var cargando by remember { mutableStateOf(true) }

    var estadoSincronizacion by rememberSaveable(header.idHeader) {
        mutableStateOf(EstadoSincronizacionReporte.PENDIENTE)
    }

    var ultimoMensajeSync by rememberSaveable(header.idHeader) {mutableStateOf(
        "Este reporte todavía no se ha enviado. Toca Sincronizar cuando tengas conexión a Internet."
    )
    }

    var descargandoCsv by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var puntos by remember { mutableStateOf<List<LocalPhytomonitoringTargetPointEntity>>(emptyList()) }
    var checkpoints by remember { mutableStateOf<List<LocalPhytomonitoringCheckpointEntity>>(emptyList()) }
    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }
    var catalogo by remember { mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList()) }
    var nombreCultivo by remember { mutableStateOf("Cultivo no identificado") }
    var fotoCultivo by remember { mutableStateOf<String?>(null) }
    var rutaFotoDetalle by remember { mutableStateOf<String?>(null) }
    var mostrarFotoDetalle by remember { mutableStateOf(false) }
    var descargandoFotoRemota by remember { mutableStateOf(false) }
    var errorFotoDetalle by remember { mutableStateOf<String?>(null) }
    var comentarioDetalle by remember { mutableStateOf<String?>(null) }
    var mostrarMapaPantallaCompleta by remember { mutableStateOf(false) }

    suspend fun cargarReporteDesdeRoom(): ReporteDataUi {
        return withContext(Dispatchers.IO) {
            val headerFresco = database.localphytomonitoringheaderDao()
                .getHeaderById(header.idHeader) ?: header


            val puntosDb = database.LocalPhytomonitoringTargetPointDao()
                .getTargetPointsByHeader(headerFresco.idHeader)

            val checkpointsDbRaw = database.localphytomonitoringcheckpointDao()
                .getCheckpointsByHeader(headerFresco.idHeader)

            val checkpointsDb = deduplicarCheckpointsReporte(checkpointsDbRaw)

            val verticesDb = database.LocalPlotVertexDao()
                .getVerticesByPlot(headerFresco.idLocalPlot)

            val catalogoDb = database.localphytosanitarycatalogDao()
                .getAllCatalogo()

            val cultivoDb = database.localCropCatalogDao()
                .getCropById(headerFresco.idCrop)

            ReporteDataUi(
                header = headerFresco,
                puntos = puntosDb,
                checkpoints = checkpointsDb,
                vertices = verticesDb,
                catalogo = catalogoDb,
                cultivo = cultivoDb?.name ?: "Cultivo no identificado",
                fotoCultivo = cultivoDb?.photo,
                fotosPendientes = PhytoMediaStorage.contarFotosPendientes(
                    context = context.applicationContext,
                    idHeader = headerFresco.idHeader
                ),
                mensajeSync = null
            )
        }
    }

    fun aplicarDataReporte(data: ReporteDataUi) {
        puntos = data.puntos
        checkpoints = data.checkpoints
        vertices = data.vertices
        catalogo = data.catalogo
        nombreCultivo = data.cultivo
        fotoCultivo = data.fotoCultivo
        data.mensajeSync
            ?.takeIf { it.isNotBlank() }
            ?.let { mensaje ->
                ultimoMensajeSync = mensaje
            }
    }
    fun mensajeSincronizacionUsuario(
        mensajeTecnico: String?
    ): String {
        val mensaje = mensajeTecnico.orEmpty()

        return when {
            mensaje.contains("Unable to resolve host", ignoreCase = true) ||
                    mensaje.contains(
                        "No address associated with hostname",
                        ignoreCase = true
                    ) ||
                    mensaje.contains("UnknownHostException", ignoreCase = true) ||
                    mensaje.contains("Network is unreachable", ignoreCase = true) ||
                    mensaje.contains("Failed to connect", ignoreCase = true) -> {
                "No se pudo conectar con el servidor. Verifica que tengas conexión a Internet y vuelve a intentarlo."
            }

            mensaje.contains("timeout", ignoreCase = true) ||
                    mensaje.contains("timed out", ignoreCase = true) ||
                    mensaje.contains("SocketTimeoutException", ignoreCase = true) -> {
                "La conexión está tardando demasiado. Revisa tu Internet y vuelve a intentarlo."
            }

            mensaje.contains("401", ignoreCase = true) ||
                    mensaje.contains("403", ignoreCase = true) ||
                    mensaje.contains("sesión", ignoreCase = true) &&
                    mensaje.contains("venc", ignoreCase = true) -> {
                "Tu sesión venció. Cierra sesión, vuelve a ingresar e intenta sincronizar nuevamente."
            }

            else -> {
                "No se pudo sincronizar el reporte. Revisa tu conexión a Internet y vuelve a intentarlo."
            }
        }
    }
    fun mensajeExitoParaUsuario(
        mensajeTecnico: String?,
        fotosPendientes: Boolean
    ): String {
        if (fotosPendientes) {
            return "La información se guardó, pero algunas fotos todavía están pendientes. Revisa tu conexión a Internet y toca Reintentar."
        }

        val mensaje = mensajeTecnico.orEmpty()

        return when {
            mensaje.contains(
                "Capturas y targets sincronizados correctamente",
                ignoreCase = true
            ) -> {
                "¡Listo! La información del monitoreo se guardó correctamente."
            }

            mensaje.contains(
                "No había capturas nuevas",
                ignoreCase = true
            ) -> {
                "Todo está al día. No había información nueva por enviar."
            }

            mensaje.contains(
                "no habia capturas nuevas",
                ignoreCase = true
            ) -> {
                "Todo está al día. No había información nueva por enviar."
            }

            else -> {
                "¡Listo! El reporte se sincronizó correctamente."
            }
        }
    }

    fun sincronizarReporteManual() {
        val puedeIniciarSincronizacion =
            estadoSincronizacion == EstadoSincronizacionReporte.PENDIENTE ||
                    estadoSincronizacion == EstadoSincronizacionReporte.ERROR

        if (!puedeIniciarSincronizacion || cargando) return

        estadoSincronizacion = EstadoSincronizacionReporte.SINCRONIZANDO
        ultimoMensajeSync =
            "Estamos guardando la información del monitoreo. Espera un momento..."
        error = null

        coroutineScope.launch {
            try {
                val resultadoSync = withContext(Dispatchers.IO) {
                    val headerFresco = database.localphytomonitoringheaderDao()
                        .getHeaderById(header.idHeader) ?: header

                    kotlinx.coroutines.withTimeoutOrNull(120_000L) {
                        PhytoCheckpointSyncRepository(
                            context = context.applicationContext,
                            database = database
                        ).sincronizarHeaderCsv(headerFresco)
                    }
                }

                when (resultadoSync) {
                    null -> {
                        estadoSincronizacion = EstadoSincronizacionReporte.ERROR
                        ultimoMensajeSync =
                            "La conexión está tardando más de lo normal. Revisa tu Internet y vuelve a intentarlo."
                    }

                    is ResultadoCheckpointSync.Exito -> {
                        val dataActualizada = cargarReporteDesdeRoom()
                        aplicarDataReporte(dataActualizada)

                        val hayPendientesDeFoto = resultadoSync.fotosPendientes

                        val mensajeUsuario = mensajeExitoParaUsuario(
                            mensajeTecnico = resultadoSync.mensaje,
                            fotosPendientes = hayPendientesDeFoto
                        )

                        estadoSincronizacion = if (hayPendientesDeFoto) {
                            EstadoSincronizacionReporte.ERROR
                        } else {
                            EstadoSincronizacionReporte.SINCRONIZADO
                        }

                        ultimoMensajeSync = mensajeUsuario


                        if (!hayPendientesDeFoto) {
                            val firmaSincronizada = crearFirmaContenidoReporte(
                                header = dataActualizada.header,
                                puntos = dataActualizada.puntos,
                                checkpoints = dataActualizada.checkpoints,
                                fotosPendientes = dataActualizada.fotosPendientes
                            )

                            preferenciasSync
                                .edit()
                                .putString(
                                    claveFirmaReporte(header.idHeader),
                                    firmaSincronizada
                                )
                                .putString(
                                    claveMensajeReporte(header.idHeader),
                                    mensajeUsuario
                                )
                                .apply()
                        } else {
                            preferenciasSync
                                .edit()
                                .remove(claveFirmaReporte(header.idHeader))
                                .remove(claveMensajeReporte(header.idHeader))
                                .apply()
                        }
                        Toast.makeText(
                            context,
                            mensajeUsuario,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is ResultadoCheckpointSync.Error -> {
                        estadoSincronizacion = EstadoSincronizacionReporte.ERROR

                        ultimoMensajeSync = mensajeSincronizacionUsuario(
                            resultadoSync.mensaje
                        )
                    }
                }
            } catch (e: Exception) {
                estadoSincronizacion = EstadoSincronizacionReporte.ERROR

                ultimoMensajeSync = mensajeSincronizacionUsuario(
                    e.message
                )
            }
        }
    }

    fun rutaArchivoLocalValida(ruta: String?): String? {
        return ruta
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { rutaLimpia ->
                runCatching {
                    !rutaLimpia.startsWith("http://", ignoreCase = true) &&
                            !rutaLimpia.startsWith("https://", ignoreCase = true) &&
                            File(rutaLimpia).exists() &&
                            File(rutaLimpia).length() > 0L
                }.getOrDefault(false)
            }
    }

    fun abrirFotoDetalle(fila: FilaReporteCapturaUi) {
        val rutaLocalDirecta = rutaArchivoLocalValida(fila.rutaFotoLocal)

        val rutaLocalPorReferencia = fila.photoRef
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { photoRef ->
                PhytoMediaStorage.buscarFotoLocalPorNombre(
                    context = context.applicationContext,
                    idHeader = fila.idHeader,
                    fileName = photoRef
                )?.absolutePath
            }
            ?.let { rutaArchivoLocalValida(it) }

        val rutaLocalPorFecha = PhytoMediaStorage.buscarFotoLocal(
            context = context.applicationContext,
            idHeader = fila.idHeader,
            idTargetPoint = fila.idTargetPoint,
            capturedAt = fila.capturedAtMillis
        )?.absolutePath
            ?.let { rutaArchivoLocalValida(it) }

        val rutaLocal = rutaLocalDirecta
            ?: rutaLocalPorReferencia
            ?: rutaLocalPorFecha

        val urlRemota = fila.photoUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("/") }

        if (rutaLocal == null && urlRemota == null) {
            Toast.makeText(
                context,
                "Esta captura no tiene evidencia fotográfica disponible en el teléfono ni en el servidor.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        errorFotoDetalle = null
        rutaFotoDetalle = rutaLocal
        mostrarFotoDetalle = true

        // Si la evidencia existe en el teléfono, se abre directo y nunca se intenta bajar.
        if (rutaLocal != null) {
            if (!fila.photoRef.isNullOrBlank() && rutaLocal != fila.rutaFotoLocal) {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        database.localphytomonitoringcheckpointDao()
                            .actualizarRutaLocalFotoPorReferencia(
                                idHeader = fila.idHeader,
                                photoRef = fila.photoRef.trim(),
                                photoLocalPath = rutaLocal
                            )
                    }
                    aplicarDataReporte(cargarReporteDesdeRoom())
                }
            }
            return
        }

        if (descargandoFotoRemota) return

        descargandoFotoRemota = true
        coroutineScope.launch {
            try {
                val archivoLocal = withContext(Dispatchers.IO) {
                    PhytoMediaStorage.descargarFotoRemotaComoUploaded(
                        context = context.applicationContext,
                        photoUrl = urlRemota!!,
                        idHeader = fila.idHeader,
                        idTargetPoint = fila.idTargetPoint,
                        capturedAt = fila.capturedAtMillis
                            ?: error("La captura no tiene fecha para guardar la evidencia."),
                        photoRef = fila.photoRef
                    )
                }

                if (!fila.photoRef.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        database.localphytomonitoringcheckpointDao()
                            .actualizarRutaLocalFotoPorReferencia(
                                idHeader = fila.idHeader,
                                photoRef = fila.photoRef.trim(),
                                photoLocalPath = archivoLocal.absolutePath
                            )
                    }
                }

                rutaFotoDetalle = archivoLocal.absolutePath
                aplicarDataReporte(cargarReporteDesdeRoom())
            } catch (e: Exception) {
                rutaFotoDetalle = null
                val mensaje = e.message.orEmpty()
                errorFotoDetalle = when {
                    mensaje.contains("HTTP 404", ignoreCase = true) ->
                        "La evidencia ya no está disponible en el servidor (HTTP 404). Si la foto fue tomada en este teléfono, vuelve a sincronizar o revisa que no se haya borrado la carpeta local de la app. Si no está en el teléfono, hay que revisar el volumen/media del backend."
                    mensaje.contains("HTTP 401", ignoreCase = true) || mensaje.contains("HTTP 403", ignoreCase = true) ->
                        "El servidor no permitió abrir la evidencia. Cierra sesión, vuelve a entrar y reintenta."
                    else ->
                        "No se pudo abrir la evidencia: ${e.message ?: "error desconocido"}"
                }
            } finally {
                descargandoFotoRemota = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisoNotificacionesLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    LaunchedEffect(header.idHeader) {
        cargando = true
        error = null

        try {
            val data = cargarReporteDesdeRoom()
            aplicarDataReporte(data)

            val firmaActual = crearFirmaContenidoReporte(
                header = data.header,
                puntos = data.puntos,
                checkpoints = data.checkpoints,
                fotosPendientes = data.fotosPendientes
            )

            val firmaGuardada = preferenciasSync.getString(
                claveFirmaReporte(header.idHeader),
                null
            )

            val reporteSigueSinCambios =
                firmaGuardada != null &&
                        firmaGuardada == firmaActual &&
                        !data.header.syncPending &&
                        data.fotosPendientes == 0 &&
                        data.puntos.all { !it.extId.isNullOrBlank() } &&
                        data.checkpoints.all { !it.extId.isNullOrBlank() }

            if (reporteSigueSinCambios) {
                estadoSincronizacion =
                    EstadoSincronizacionReporte.SINCRONIZADO

                ultimoMensajeSync = preferenciasSync.getString(
                    claveMensajeReporte(header.idHeader),
                    null
                ) ?: "Todo está al día. Este reporte ya fue enviado correctamente."
            } else {
                estadoSincronizacion =
                    EstadoSincronizacionReporte.PENDIENTE

                ultimoMensajeSync =
                    "Este reporte tiene información pendiente de enviar."
            }
        } catch (e: Exception) {
            e.printStackTrace()

            error =
                "Error al cargar reporte: ${e.javaClass.simpleName} - ${e.message}"
        } finally {
            cargando = false
        }
    }

    val catalogoMap = remember(catalogo) {
        catalogo.associateBy { it.idPhytosanitary }
    }

    val numeroPuntoMap = remember(puntos) {
        crearNumeroPuntoMapPorCoordenada(puntos)
    }
    val puntosMap = remember(puntos) {
        puntos.associateBy { it.idTargetPoint }
    }

    val puntosConCaptura = remember(checkpoints, puntos, numeroPuntoMap) {
        val idsValidos = puntos.map { it.idTargetPoint }.toSet()

        checkpoints
            .filter { checkpoint ->
                checkpoint.idHeader == header.idHeader &&
                        checkpoint.idTargetPoint in idsValidos
            }
            .mapNotNull { checkpoint ->
                numeroPuntoMap[checkpoint.idTargetPoint]
            }
            .toSet()
    }

    val totalPuntos = numeroPuntoMap.values.toSet().size
    val puntosCapturados = puntosConCaptura.size
    val puntosPendientes = (totalPuntos - puntosCapturados).coerceAtLeast(0)
    val porcentajeAvance = if (totalPuntos > 0) {
        ((puntosCapturados.toDouble() / totalPuntos.toDouble()) * 100.0).toInt()
    } else {
        0
    }

    var contenidoSuperiorExpandido by rememberSaveable(header.idHeader) {
        mutableStateOf(false)
    }
    var detalleSuperiorExpandido by rememberSaveable(header.idHeader) {
        mutableStateOf(false)
    }

    /*
     * La severidad por umbral técnico se calcula únicamente con PLAGAS.
     * Las enfermedades usan su fase: Inicio, Desarrollo o Avanzado.
     */
    val severidadPorPuntoMap = remember(
        checkpoints,
        catalogoMap,
        numeroPuntoMap
    ) {
        checkpoints
            .mapNotNull { checkpoint ->
                numeroPuntoMap[checkpoint.idTargetPoint]?.let { numero ->
                    numero to checkpoint
                }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            .mapValues { (_, capturasMismaCoordenada) ->
                val capturasPlaga = capturasMismaCoordenada.filter { checkpoint ->
                    val fito = checkpoint.idPhytosanitary?.let(catalogoMap::get)

                    fito != null &&
                            !esEnfermedadReporte(fito.type) &&
                            !esSinPlagaReporte(checkpoint, fito) &&
                            checkpoint.presenceStatus != 0 &&
                            (checkpoint.qty ?: 0) > 0
                }

                calcularSeveridadPorPunto(
                    checkpointsPunto = capturasPlaga,
                    catalogoPorId = catalogoMap
                )
            }
    }

    val filasTabla = remember(checkpoints, puntosMap, catalogoMap, numeroPuntoMap, severidadPorPuntoMap) {
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
                val item = checkpoint.idPhytosanitary?.let { id ->
                    catalogoMap[id]
                }
                val esSinPlaga = esSinPlagaReporte(
                    checkpoint = checkpoint,
                    fito = item
                )
                val numeroPunto = numeroPuntoMap[checkpoint.idTargetPoint] ?: 0
                val severidadPunto = severidadPorPuntoMap[numeroPunto]
                val nivelPunto = severidadPunto?.nivelFinal
                val esEnfermedad = esEnfermedadReporte(item?.type)
                val estadoEnfermedad = if (esEnfermedad) {
                    calcularEstadoSeveridadEnfermedadReporte(checkpoint)
                } else {
                    null
                }

                val cantidadVisible = when {
                    esSinPlaga -> "0"
                    esEnfermedad -> "—"
                    else -> (checkpoint.qty ?: 0).toString()
                }

                val severidadVisible = when {
                    esSinPlaga -> "Sin plaga"
                    esEnfermedad -> estadoEnfermedad?.etiqueta ?: "No presente"
                    else -> nivelPunto?.etiqueta ?: "Sin plaga"
                }

                val colorSeveridadVisible = when {
                    esSinPlaga -> "#16A34A"
                    esEnfermedad -> estadoEnfermedad?.colorHex ?: "#16A34A"
                    else -> nivelPunto?.colorHex ?: "#16A34A"
                }

                val rutaGuardada = checkpoint.photoLocalPath
                    ?.takeIf { ruta ->
                        runCatching {
                            val archivo = File(ruta)
                            archivo.exists() && archivo.length() > 0L
                        }.getOrDefault(false)
                    }

                val rutaLocalPorReferencia = checkpoint.photoRef
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { photoRef ->
                        PhytoMediaStorage.buscarFotoLocalPorNombre(
                            context = context.applicationContext,
                            idHeader = checkpoint.idHeader,
                            fileName = photoRef
                        )?.absolutePath
                    }
                    ?.takeIf { ruta ->
                        runCatching {
                            val archivo = File(ruta)
                            archivo.exists() && archivo.length() > 0L
                        }.getOrDefault(false)
                    }

                val rutaLocalDetectada = PhytoMediaStorage.buscarFotoLocal(
                    context = context.applicationContext,
                    idHeader = checkpoint.idHeader,
                    idTargetPoint = checkpoint.idTargetPoint,
                    capturedAt = checkpoint.capturedAt
                )?.absolutePath

                FilaReporteCapturaUi(
                    numeroPunto = numeroPunto,
                    lat = punto?.lat,
                    lon = punto?.lon,
                    plagaEnfermedad = if (esSinPlaga) "Sin plaga" else item?.name ?: "Sin identificar",
                    tipo = if (esSinPlaga) "-" else textoTipoCatalogo(item?.type),
                    fase = textoPresenciaFaseReporte(
                        checkpoint = checkpoint,
                        fito = item
                    ),
                    cantidad = cantidadVisible,
                    severidad = severidadVisible,
                    colorSeveridadHex = colorSeveridadVisible,
                    fechaCaptura = formatearFechaOpcionalReporteUi(checkpoint.capturedAt),
                    notas = limpiarMetadataRangosSeveridad(checkpoint.notes),
                    rutaFotoLocal = rutaGuardada ?: rutaLocalPorReferencia ?: rutaLocalDetectada ?: checkpoint.photoUrl,
                    photoRef = checkpoint.photoRef,
                    photoUrl = checkpoint.photoUrl,
                    idHeader = checkpoint.idHeader,
                    idTargetPoint = checkpoint.idTargetPoint,
                    capturedAtMillis = checkpoint.capturedAt
                )
            }
    }

    val htmlMapa = remember(vertices, puntos, checkpoints, catalogo) {
        crearHtmlMapaReporteUi(
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            catalogo = catalogo,
            pantallaCompleta = false
        )
    }

    val htmlMapaPantallaCompleta = remember(vertices, puntos, checkpoints, catalogo) {
        crearHtmlMapaReporteUi(
            vertices = vertices,
            puntos = puntos,
            checkpoints = checkpoints,
            catalogo = catalogo,
            pantallaCompleta = true
        )
    }


    if (mostrarMapaPantallaCompleta) {
        Dialog(
            onDismissRequest = { mostrarMapaPantallaCompleta = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { mostrarMapaPantallaCompleta = false }
                    ) {
                        Text(
                            text = "←",
                            color = Color(0xFF123D1F),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mapa del monitoreo",
                            color = Color(0xFF123D1F),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Toca un punto para ver el detalle",
                            color = Color(0xFF5F6F64),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ChipEstadoReporte(
                        texto = "${vertices.size} vértices",
                        colorFondo = Color(0xFFE8F5E9),
                        colorTexto = Color(0xFF1B5E20)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFFEAF5E8))
                ) {
                    key(htmlMapaPantallaCompleta) {
                        MapaReporteWebViewUi(
                            htmlMapa = htmlMapaPantallaCompleta,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (mostrarFotoDetalle) {
        AlertDialog(
            onDismissRequest = {
                mostrarFotoDetalle = false
                rutaFotoDetalle = null
                errorFotoDetalle = null
            },
            title = {
                Text(
                    text = "Evidencia fotográfica",
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        descargandoFotoRemota -> {
                            CircularProgressIndicator(color = Color(0xFF1B5E20))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Descargando evidencia...",
                                color = Color(0xFF1B5E20),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        !rutaFotoDetalle.isNullOrBlank() -> {
                            ImageUriBox(
                                photo = rutaFotoDetalle,
                                fallbackIcon = "📷",
                                sizeDp = 280,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        else -> {
                            Text(
                                text = errorFotoDetalle
                                    ?: "No se encontró una imagen válida para esta captura.",
                                color = Color(0xFFB3261E),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarFotoDetalle = false
                        rutaFotoDetalle = null
                        errorFotoDetalle = null
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    comentarioDetalle?.let { comentario ->
        AlertDialog(
            onDismissRequest = { comentarioDetalle = null },
            title = {
                Text(
                    text = "Observaciones",
                    fontWeight = FontWeight.Black
                )
            },
            text = { Text(comentario) },
            confirmButton = {
                TextButton(onClick = { comentarioDetalle = null }) {
                    Text("Cerrar")
                }
            }
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
                        inicioReal = formatearFechaHoraCompactaReporteUi(
                            fecha = header.startAt,
                            textoVacio = "Sin iniciar"
                        ),
                        finReal = formatearFechaHoraCompactaReporteUi(
                            fecha = header.finishedAt,
                            textoVacio = "Sin finalizar"
                        ),
                        porcentajeAvance = porcentajeAvance,
                        contenidoExpandido = contenidoSuperiorExpandido,
                        onToggleContenido = {
                            contenidoSuperiorExpandido = !contenidoSuperiorExpandido
                        },
                        detalleExpandido = detalleSuperiorExpandido,
                        onToggleDetalle = { detalleSuperiorExpandido = !detalleSuperiorExpandido }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val colorSincronizacion = when (estadoSincronizacion) {
                        EstadoSincronizacionReporte.SINCRONIZADO -> Color(0xFF2E7D32)
                        EstadoSincronizacionReporte.ERROR -> Color(0xFFC62828)
                        else -> Color(0xFFF9A825)
                    }

                    val textoEstadoSincronizacion = when (estadoSincronizacion) {
                        EstadoSincronizacionReporte.PENDIENTE ->
                            "Pendiente de enviar"

                        EstadoSincronizacionReporte.SINCRONIZANDO ->
                            "Guardando información"

                        EstadoSincronizacionReporte.SINCRONIZADO ->
                            "Todo listo"

                        EstadoSincronizacionReporte.ERROR ->
                            "No se pudo enviar"

                        else ->
                            "Estado del reporte"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TarjetaResumenReporte(
                            titulo = "Puntos",
                            valor = totalPuntos.toString(),
                            detalle = "Asignados",
                            icono = "◎",
                            colorAcento = Color(0xFF1B7A35),
                            modifier = Modifier.weight(1f)
                        )

                        TarjetaResumenReporte(
                            titulo = "Capturados",
                            valor = puntosCapturados.toString(),
                            detalle = "$porcentajeAvance% avance",
                            icono = "✓",
                            colorAcento = Color(0xFF1B7A35),
                            modifier = Modifier.weight(1f)
                        )

                        TarjetaResumenReporte(
                            titulo = "Pendientes",
                            valor = puntosPendientes.toString(),
                            detalle = "Por revisar",
                            icono = "◉",
                            colorAcento = Color(0xFFF57C00),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mapa del monitoreo",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF123D1F)
                                    )

                                    Text(
                                        text = "Polígono y puntos del monitoreo",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5F6F64)
                                    )
                                }

                                ChipEstadoReporte(
                                    texto = "${vertices.size} vértices",
                                    colorFondo = Color(0xFFE8F5E9),
                                    colorTexto = Color(0xFF1B5E20)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFDDE8D6),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                // Fuerza a crear un WebView nuevo cuando cambia el HTML del mapa.
                                // Esto evita que quede visible una versión anterior de las leyendas.
                                key(htmlMapa.hashCode()) {
                                    MapaReporteWebViewUi(
                                        htmlMapa = htmlMapa,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = { mostrarMapaPantallaCompleta = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⛶ Abrir mapa completo y tocar puntos",
                                    color = Color(0xFF1B5E20),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF6)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Button(
                                onClick = {
                                    descargandoCsv = true

                                    coroutineScope.launch {
                                        try {
                                            val archivoCsv = withContext(Dispatchers.IO) {
                                                descargarCsvReporteUi(
                                                    context = context.applicationContext,
                                                    header = header,
                                                    productor = productor?.commercial_name ?: "-",
                                                    rancho = rancho?.name ?: "-",
                                                    parcela = parcela?.code ?: "-",
                                                    checkpoints = checkpoints,
                                                    puntos = puntos,
                                                    catalogo = catalogo
                                                )
                                            }

                                            NotificacionCsvReporte.mostrar(
                                                context = context.applicationContext,
                                                archivo = archivoCsv
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "No se pudo descargar el CSV: ${e.message ?: "error desconocido"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } finally {
                                            descargandoCsv = false
                                        }
                                    }
                                },
                                enabled = !descargandoCsv,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF166534),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFB8C9BC),
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "↓",
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 27.sp
                                    )
                                    Text(
                                        text = if (descargandoCsv) "Guardando..." else "Descargar CSV",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Button(
                                onClick = { sincronizarReporteManual() },
                                enabled = (
                                        estadoSincronizacion == EstadoSincronizacionReporte.PENDIENTE ||
                                                estadoSincronizacion == EstadoSincronizacionReporte.ERROR
                                        ) && !cargando,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color.White,
                                    disabledContainerColor = when (estadoSincronizacion) {
                                        EstadoSincronizacionReporte.SINCRONIZADO -> Color(0xFF2E7D32)
                                        EstadoSincronizacionReporte.SINCRONIZANDO -> Color(0xFFD99A22)
                                        else -> Color(0xFFBDBDBD)
                                    },
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (
                                            estadoSincronizacion == EstadoSincronizacionReporte.SINCRONIZADO
                                        ) "✓" else "↻",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 26.sp
                                    )
                                    Text(
                                        text = when (estadoSincronizacion) {
                                            EstadoSincronizacionReporte.SINCRONIZANDO -> "Enviando..."
                                            EstadoSincronizacionReporte.SINCRONIZADO -> "Sincronizado"
                                            EstadoSincronizacionReporte.ERROR -> "Reintentar"
                                            else -> "Sincronizar"
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (estadoSincronizacion) {
                                EstadoSincronizacionReporte.SINCRONIZADO -> Color(0xFFE8F5E9)
                                EstadoSincronizacionReporte.ERROR -> Color(0xFFFFEBEE)
                                else -> Color(0xFFFFF8E1)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = textoEstadoSincronizacion,
                                color = colorSincronizacion,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = ultimoMensajeSync,
                                color = Color(0xFF3E4A40),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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

                            TablaReporteCapturasUi(
                                filas = filasTabla,
                                onFotoClick = { fila ->
                                    abrirFotoDetalle(fila)
                                },
                                onComentarioClick = { fila ->
                                    comentarioDetalle = fila.notas
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

private fun deduplicarCheckpointsReporte(
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>
): List<LocalPhytomonitoringCheckpointEntity> {
    val vistosExtId = mutableSetOf<String>()
    val vistosCaptura = mutableSetOf<String>()

    return checkpoints
        .sortedWith(
            compareBy<LocalPhytomonitoringCheckpointEntity> { checkpoint ->
                if (checkpoint.extId.isNullOrBlank()) 1 else 0
            }.thenByDescending { checkpoint ->
                checkpoint.capturedAt ?: 0L
            }
        )
        .filter { checkpoint ->
            val extId = checkpoint.extId?.trim()?.takeIf { it.isNotBlank() }
            val minutoCaptura = (checkpoint.capturedAt ?: 0L) / 60000L
            val llaveCaptura = listOf(
                checkpoint.idHeader,
                checkpoint.idTargetPoint,
                checkpoint.idPhytosanitary,
                checkpoint.stage.orEmpty(),
                checkpoint.qty ?: -9999,
                minutoCaptura
            ).joinToString("|")

            val extValido = extId == null || vistosExtId.add(extId)
            val capturaValida = vistosCaptura.add(llaveCaptura)

            extValido && capturaValida
        }
        .sortedWith(
            compareBy<LocalPhytomonitoringCheckpointEntity> { checkpoint ->
                checkpoint.capturedAt ?: 0L
            }.thenBy { checkpoint ->
                checkpoint.idCheckpoint
            }
        )
}

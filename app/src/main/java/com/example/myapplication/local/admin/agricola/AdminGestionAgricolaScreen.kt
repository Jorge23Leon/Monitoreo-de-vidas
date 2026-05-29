package com.example.myapplication.local.admin.agricola

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCiaAgroUnitCrossRef
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun AdminGestionAgricolaScreen(
    database: AppDatabase,
    nombreUsuario: String,
    rolUsuario: String,
    idLocalCia: Long?,
    nombreCia: String,
    onBackClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onCerrarSesionClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit,
    onMensaje: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var tabActual by remember { mutableStateOf(AdminGestionTab.PRODUCTORES) }

    var productores by remember { mutableStateOf<List<LocalAgroUnitEntity>>(emptyList()) }
    var ranchos by remember { mutableStateOf<List<LocalRanchEntity>>(emptyList()) }
    var parcelas by remember { mutableStateOf<List<LocalPlotEntity>>(emptyList()) }
    var vertices by remember { mutableStateOf<List<LocalPlotVertexEntity>>(emptyList()) }

    var mostrarDialogProductor by remember { mutableStateOf(false) }
    var mostrarDialogRancho by remember { mutableStateOf(false) }
    var mostrarDialogParcela by remember { mutableStateOf(false) }
    var mostrarDialogImportarRanchosCsv by remember { mutableStateOf(false) }
    var mostrarDialogImportarParcelasCsv by remember { mutableStateOf(false) }
    var mostrarConfirmacionEliminar by remember { mutableStateOf<ConfirmacionEliminarGestion?>(null) }

    var productorEnEdicion by remember { mutableStateOf<LocalAgroUnitEntity?>(null) }
    var ranchoEnEdicion by remember { mutableStateOf<LocalRanchEntity?>(null) }
    var parcelaEnEdicion by remember { mutableStateOf<LocalPlotEntity?>(null) }

    fun recargarDatos() {
        coroutineScope.launch {
            cargando = true
            try {
                val datos = withContext(Dispatchers.IO) {
                    val productoresCia = if (idLocalCia == null) {
                        emptyList()
                    } else {
                        database.localCiaAgroUnitDao().getProductoresByCia(idLocalCia)
                    }

                    val idsProductores = productoresCia.map { it.idLocalAgroUnit }.toSet()
                    val ranchosCia = database.localRanchDao()
                        .getAllRanches()
                        .filter { it.idLocalAgroUnit in idsProductores }

                    val idsRanchos = ranchosCia.map { it.idLocalRanch }.toSet()
                    val parcelasCia = database.localPlotDao()
                        .getAllPlots()
                        .filter { it.idLocalRanch in idsRanchos }

                    val idsParcelas = parcelasCia.map { it.idLocalPlot }.toSet()
                    val verticesCia = database.LocalPlotVertexDao()
                        .getAllPlotVertices()
                        .filter { it.idLocalPlot in idsParcelas }

                    DatosGestionAgricola(
                        productores = productoresCia,
                        ranchos = ranchosCia,
                        parcelas = parcelasCia,
                        vertices = verticesCia
                    )
                }

                productores = datos.productores
                ranchos = datos.ranchos
                parcelas = datos.parcelas
                vertices = datos.vertices
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar gestión agrícola: ${e.message}")
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(idLocalCia) {
        recargarDatos()
    }

    fun cerrarDialogos() {
        if (!guardando) {
            mostrarDialogProductor = false
            mostrarDialogRancho = false
            mostrarDialogParcela = false
            mostrarDialogImportarRanchosCsv = false
            mostrarDialogImportarParcelasCsv = false
            productorEnEdicion = null
            ranchoEnEdicion = null
            parcelaEnEdicion = null
        }
    }

    fun abrirAgregarProductor() {
        productorEnEdicion = null
        mostrarDialogProductor = true
    }

    fun abrirEditarProductor(productor: LocalAgroUnitEntity) {
        productorEnEdicion = productor
        mostrarDialogProductor = true
    }

    fun abrirAgregarRancho() {
        ranchoEnEdicion = null
        mostrarDialogRancho = true
    }

    fun abrirEditarRancho(rancho: LocalRanchEntity) {
        ranchoEnEdicion = rancho
        mostrarDialogRancho = true
    }

    fun abrirAgregarParcela() {
        parcelaEnEdicion = null
        mostrarDialogParcela = true
    }

    fun abrirEditarParcela(parcela: LocalPlotEntity) {
        parcelaEnEdicion = parcela
        mostrarDialogParcela = true
    }


    fun abrirImportarRanchosCsv() {
        mostrarDialogImportarRanchosCsv = true
    }

    fun abrirImportarParcelasCsv() {
        mostrarDialogImportarParcelasCsv = true
    }

    fun guardarProductor(
        productorBase: LocalAgroUnitEntity?,
        nombre: String,
        slugInput: String
    ) {
        val idCia = idLocalCia
        val nombreLimpio = nombre.trim()
        val slugLimpio = slugInput.trim().ifBlank { generarSlugGestion(nombreLimpio) }

        when {
            idCia == null -> onMensaje("Selecciona una CIA antes de agregar productores")
            nombreLimpio.isBlank() -> onMensaje("Escribe el nombre del productor")
            slugLimpio.isBlank() -> onMensaje("Escribe un código o slug válido")
            productores.any {
                it.idLocalAgroUnit != (productorBase?.idLocalAgroUnit ?: -1L) &&
                        it.commercial_name.equals(nombreLimpio, ignoreCase = true)
            } -> onMensaje("Ese productor ya existe en esta CIA")
            productores.any {
                it.idLocalAgroUnit != (productorBase?.idLocalAgroUnit ?: -1L) &&
                        it.slug.equals(slugLimpio, ignoreCase = true)
            } -> onMensaje("Ese código/slug ya existe en esta CIA")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        withContext(Dispatchers.IO) {
                            if (productorBase == null) {
                                val nuevoId = database.localAgroUnitDao().insertAgroUnit(
                                    LocalAgroUnitEntity(
                                        ext_Id = null,
                                        commercial_name = nombreLimpio,
                                        slug = slugLimpio
                                    )
                                )
                                database.localCiaAgroUnitDao().asignarProductorACia(
                                    LocalCiaAgroUnitCrossRef(
                                        idLocalCia = idCia,
                                        idLocalAgroUnit = nuevoId,
                                        extId = null
                                    )
                                )
                            } else {
                                database.localAgroUnitDao().updateAgroUnit(
                                    productorBase.copy(
                                        commercial_name = nombreLimpio,
                                        slug = slugLimpio
                                    )
                                )
                            }
                        }
                        mostrarDialogProductor = false
                        productorEnEdicion = null
                        onMensaje(if (productorBase == null) "Productor agregado" else "Productor actualizado")
                        recargarDatos()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMensaje("No se pudo guardar el productor: ${e.message}")
                    } finally {
                        guardando = false
                    }
                }
            }
        }
    }

    fun guardarRancho(
        ranchoBase: LocalRanchEntity?,
        productor: LocalAgroUnitEntity?,
        nombre: String,
        codigo: String,
        latTexto: String,
        lonTexto: String
    ) {
        val productorSeleccionado = productor
        val nombreLimpio = nombre.trim()
        val codigoLimpio = codigo.trim().uppercase(Locale.getDefault())
        val lat = latTexto.trim().ifBlank { null }?.toDoubleOrNull()
        val lon = lonTexto.trim().ifBlank { null }?.toDoubleOrNull()

        when {
            productorSeleccionado == null -> onMensaje("Selecciona un productor")
            nombreLimpio.isBlank() -> onMensaje("Escribe el nombre del rancho")
            codigoLimpio.isBlank() -> onMensaje("Escribe el código del rancho")
            latTexto.isNotBlank() && lat == null -> onMensaje("La latitud del rancho no es válida")
            lonTexto.isNotBlank() && lon == null -> onMensaje("La longitud del rancho no es válida")
            lat != null && lat !in -90.0..90.0 -> onMensaje("La latitud debe estar entre -90 y 90")
            lon != null && lon !in -180.0..180.0 -> onMensaje("La longitud debe estar entre -180 y 180")
            ranchos.any {
                it.idLocalRanch != (ranchoBase?.idLocalRanch ?: -1L) &&
                        it.idLocalAgroUnit == productorSeleccionado.idLocalAgroUnit &&
                        it.code.equals(codigoLimpio, ignoreCase = true)
            } -> onMensaje("Ya existe un rancho con ese código para el productor seleccionado")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        withContext(Dispatchers.IO) {
                            if (ranchoBase == null) {
                                database.localRanchDao().insertRanch(
                                    LocalRanchEntity(
                                        extId = null,
                                        name = nombreLimpio,
                                        code = codigoLimpio,
                                        lat = lat,
                                        lon = lon,
                                        idLocalAgroUnit = productorSeleccionado.idLocalAgroUnit
                                    )
                                )
                            } else {
                                database.localRanchDao().updateRanch(
                                    ranchoBase.copy(
                                        name = nombreLimpio,
                                        code = codigoLimpio,
                                        lat = lat,
                                        lon = lon,
                                        idLocalAgroUnit = productorSeleccionado.idLocalAgroUnit
                                    )
                                )
                            }
                        }
                        mostrarDialogRancho = false
                        ranchoEnEdicion = null
                        onMensaje(if (ranchoBase == null) "Rancho agregado" else "Rancho actualizado")
                        recargarDatos()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMensaje("No se pudo guardar el rancho: ${e.message}")
                    } finally {
                        guardando = false
                    }
                }
            }
        }
    }

    fun guardarRanchosDesdeCsv(
        productorDefault: LocalAgroUnitEntity?,
        ranchosCsv: List<ImportedRanchCsv>
    ) {
        if (productores.isEmpty()) {
            onMensaje("Primero agrega un productor para importar ranchos")
            return
        }

        coroutineScope.launch {
            guardando = true
            try {
                val resultado = withContext(Dispatchers.IO) {
                    var insertados = 0
                    var omitidos = 0
                    val productoresBySlug = productores.associateBy { it.slug.normalizarClaveGestion() }
                    val productoresByName = productores.associateBy { it.commercial_name.normalizarClaveGestion() }
                    val ranchosExistentes = database.localRanchDao().getAllRanches().toMutableList()

                    ranchosCsv.forEach { item ->
                        val productorCsv = item.productorRef?.normalizarClaveGestion()?.let { clave ->
                            productoresBySlug[clave] ?: productoresByName[clave]
                        }
                        val productorDestino = productorCsv ?: productorDefault
                        val codigo = item.code.trim().uppercase(Locale.getDefault())
                        val nombre = item.name.trim()

                        val datosInvalidos = productorDestino == null || nombre.isBlank() || codigo.isBlank() ||
                                (item.lat != null && item.lat !in -90.0..90.0) ||
                                (item.lon != null && item.lon !in -180.0..180.0)

                        val duplicado = productorDestino != null && ranchosExistentes.any {
                            it.idLocalAgroUnit == productorDestino.idLocalAgroUnit && it.code.equals(codigo, ignoreCase = true)
                        }

                        if (datosInvalidos || duplicado || productorDestino == null) {
                            omitidos++
                        } else {
                            val nuevo = LocalRanchEntity(
                                extId = null,
                                name = nombre,
                                code = codigo,
                                lat = item.lat,
                                lon = item.lon,
                                idLocalAgroUnit = productorDestino.idLocalAgroUnit
                            )
                            val nuevoId = database.localRanchDao().insertRanch(nuevo)
                            ranchosExistentes.add(nuevo.copy(idLocalRanch = nuevoId))
                            insertados++
                        }
                    }
                    ResultadoImportacionGestion(insertados = insertados, omitidos = omitidos)
                }

                mostrarDialogImportarRanchosCsv = false
                onMensaje("Ranchos importados: ${resultado.insertados}. Omitidos: ${resultado.omitidos}.")
                recargarDatos()
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("No se pudieron importar los ranchos: ${e.message}")
            } finally {
                guardando = false
            }
        }
    }

    fun guardarParcelasDesdeCsv(
        ranchoDefault: LocalRanchEntity?,
        parcelasCsv: List<ImportedPlotCsv>
    ) {
        if (ranchos.isEmpty()) {
            onMensaje("Primero agrega un rancho para importar parcelas")
            return
        }

        coroutineScope.launch {
            guardando = true
            try {
                val resultado = withContext(Dispatchers.IO) {
                    var insertados = 0
                    var omitidos = 0
                    var coordenadasDefault = 0
                    val ranchosByCode = ranchos.associateBy { it.code.normalizarClaveGestion() }
                    val ranchosByName = ranchos.associateBy { it.name.normalizarClaveGestion() }
                    val parcelasExistentes = database.localPlotDao().getAllPlots().toMutableList()

                    parcelasCsv.forEach { item ->
                        val ranchoCsv = item.ranchoRef?.normalizarClaveGestion()?.let { clave ->
                            ranchosByCode[clave] ?: ranchosByName[clave]
                        }
                        val ranchoDestino = ranchoCsv ?: ranchoDefault
                        val codigo = item.code.trim().uppercase(Locale.getDefault())
                        val lat = item.lat ?: ranchoDestino?.lat ?: 0.0
                        val lon = item.lon ?: ranchoDestino?.lon ?: 0.0

                        val datosInvalidos = ranchoDestino == null || codigo.isBlank() ||
                                lat !in -90.0..90.0 || lon !in -180.0..180.0

                        val duplicado = ranchoDestino != null && parcelasExistentes.any {
                            it.idLocalRanch == ranchoDestino.idLocalRanch && (it.code?.equals(codigo, ignoreCase = true) == true)
                        }

                        if (datosInvalidos || duplicado || ranchoDestino == null) {
                            omitidos++
                        } else {
                            if (item.lat == null || item.lon == null) coordenadasDefault++
                            val nueva = LocalPlotEntity(
                                extId = null,
                                name = item.description?.trim()?.takeIf { it.isNotBlank() } ?: codigo,
                                code = codigo,
                                lat = lat,
                                lon = lon,
                                idLocalRanch = ranchoDestino.idLocalRanch
                            )
                            val nuevoId = database.localPlotDao().insertPlot(nueva)
                            parcelasExistentes.add(nueva.copy(idLocalPlot = nuevoId))
                            insertados++
                        }
                    }
                    ResultadoImportacionGestion(
                        insertados = insertados,
                        omitidos = omitidos,
                        coordenadasDefault = coordenadasDefault
                    )
                }

                mostrarDialogImportarParcelasCsv = false
                val avisoCoordenadas = if (resultado.coordenadasDefault > 0) {
                    " ${resultado.coordenadasDefault} quedaron con coordenada default; carga sus coordenadas para actualizar el centro."
                } else {
                    ""
                }
                onMensaje("Parcelas importadas: ${resultado.insertados}. Omitidas: ${resultado.omitidos}.$avisoCoordenadas")
                recargarDatos()
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("No se pudieron importar las parcelas: ${e.message}")
            } finally {
                guardando = false
            }
        }
    }

    fun guardarParcela(
        parcelaBase: LocalPlotEntity?,
        rancho: LocalRanchEntity?,
        codigo: String,
        descripcion: String,
        latTexto: String,
        lonTexto: String,
        puntosPoligono: List<ImportedPolygonPoint>
    ) {
        val ranchoSeleccionado = rancho
        val codigoLimpio = codigo.trim().uppercase(Locale.getDefault())
        val nombreParcela = descripcion.trim().ifBlank { codigoLimpio }
        val latTextoLimpio = latTexto.trim()
        val lonTextoLimpio = lonTexto.trim()
        val latManual = latTextoLimpio.ifBlank { "0" }.toDoubleOrNull()
        val lonManual = lonTextoLimpio.ifBlank { "0" }.toDoubleOrNull()
        val tienePoligonoAutomatico = puntosPoligono.size >= 3
        val centroLatPoligono = if (tienePoligonoAutomatico) puntosPoligono.map { it.lat }.average() else null
        val centroLonPoligono = if (tienePoligonoAutomatico) puntosPoligono.map { it.lon }.average() else null
        val lat = centroLatPoligono ?: latManual
        val lon = centroLonPoligono ?: lonManual

        when {
            ranchoSeleccionado == null -> onMensaje("Selecciona un rancho")
            codigoLimpio.isBlank() -> onMensaje("Escribe el código de la parcela")
            latTextoLimpio.isNotBlank() && latManual == null -> onMensaje("Escribe una latitud válida para la parcela")
            lonTextoLimpio.isNotBlank() && lonManual == null -> onMensaje("Escribe una longitud válida para la parcela")
            lat == null -> onMensaje("La latitud no es válida")
            lon == null -> onMensaje("La longitud no es válida")
            lat !in -90.0..90.0 -> onMensaje("La latitud debe estar entre -90 y 90")
            lon !in -180.0..180.0 -> onMensaje("La longitud debe estar entre -180 y 180")
            parcelas.any {
                it.idLocalPlot != (parcelaBase?.idLocalPlot ?: -1L) &&
                        it.idLocalRanch == ranchoSeleccionado.idLocalRanch &&
                        (it.code?.equals(codigoLimpio, ignoreCase = true) == true)
            } -> onMensaje("Ya existe una parcela con ese código en el rancho seleccionado")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    var parcelaGuardada: LocalPlotEntity? = null
                    try {
                        withContext(Dispatchers.IO) {
                            val parcelaPersistida = if (parcelaBase == null) {
                                val nuevaParcela = LocalPlotEntity(
                                    extId = null,
                                    name = nombreParcela,
                                    code = codigoLimpio,
                                    lat = lat,
                                    lon = lon,
                                    idLocalRanch = ranchoSeleccionado.idLocalRanch
                                )
                                val nuevoId = database.localPlotDao().insertPlot(nuevaParcela)
                                nuevaParcela.copy(idLocalPlot = nuevoId)
                            } else {
                                val parcelaActualizada = parcelaBase.copy(
                                    name = nombreParcela,
                                    code = codigoLimpio,
                                    lat = lat,
                                    lon = lon,
                                    idLocalRanch = ranchoSeleccionado.idLocalRanch
                                )
                                database.localPlotDao().updatePlot(parcelaActualizada)
                                parcelaActualizada
                            }

                            parcelaGuardada = parcelaPersistida

                            if (tienePoligonoAutomatico) {
                                val verticesNuevos = puntosPoligono
                                    .sortedBy { it.level }
                                    .mapIndexed { index, punto ->
                                        LocalPlotVertexEntity(
                                            extId = null,
                                            level = index + 1,
                                            lat = punto.lat,
                                            lon = punto.lon,
                                            idLocalPlot = parcelaPersistida.idLocalPlot
                                        )
                                    }

                                database.LocalPlotVertexDao().deleteVerticesByPlot(parcelaPersistida.idLocalPlot)
                                database.LocalPlotVertexDao().insertPlotVertices(verticesNuevos)

                                val parcelaConCentro = parcelaPersistida.copy(
                                    lat = verticesNuevos.map { it.lat }.average(),
                                    lon = verticesNuevos.map { it.lon }.average()
                                )
                                database.localPlotDao().updatePlot(parcelaConCentro)
                                parcelaGuardada = parcelaConCentro
                            }
                        }
                        mostrarDialogParcela = false
                        parcelaEnEdicion = null

                        if (tienePoligonoAutomatico) {
                            onMensaje(
                                if (parcelaBase == null) {
                                    "Parcela agregada con ${puntosPoligono.size} coordenadas"
                                } else {
                                    "Parcela actualizada con ${puntosPoligono.size} coordenadas"
                                }
                            )
                        } else if (parcelaBase == null) {
                            onMensaje("Parcela agregada")
                        } else {
                            onMensaje("Parcela actualizada")
                        }
                        recargarDatos()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMensaje("No se pudo guardar la parcela: ${e.message}")
                    } finally {
                        guardando = false
                    }
                }
            }
        }
    }

    fun eliminarProductor(productor: LocalAgroUnitEntity) {
        coroutineScope.launch {
            guardando = true
            try {
                val mensajeBloqueo = withContext(Dispatchers.IO) {
                    val ranchosRelacionados = database.localRanchDao()
                        .getRanchesByAgroUnit(productor.idLocalAgroUnit)
                    val programasRelacionados = database.localprogramDao()
                        .getProgramsByAgroUnit(productor.idLocalAgroUnit)

                    when {
                        ranchosRelacionados.isNotEmpty() -> "No se puede eliminar: el productor tiene ranchos relacionados"
                        programasRelacionados.isNotEmpty() -> "No se puede eliminar: el productor tiene programas/monitoreos relacionados"
                        else -> {
                            database.localAgroUnitDao().deleteAgroUnit(productor)
                            null
                        }
                    }
                }
                onMensaje(mensajeBloqueo ?: "Productor eliminado")
                recargarDatos()
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("No se pudo eliminar el productor: ${e.message}")
            } finally {
                guardando = false
                mostrarConfirmacionEliminar = null
            }
        }
    }

    fun eliminarRancho(rancho: LocalRanchEntity) {
        coroutineScope.launch {
            guardando = true
            try {
                val mensajeBloqueo = withContext(Dispatchers.IO) {
                    val parcelasRelacionadas = database.localPlotDao()
                        .getPlotsByRanch(rancho.idLocalRanch)
                    val programasRelacionados = database.localprogramDao()
                        .getAllPrograms()
                        .filter { it.idLocalRanch == rancho.idLocalRanch }

                    when {
                        parcelasRelacionadas.isNotEmpty() -> "No se puede eliminar: el rancho tiene parcelas relacionadas"
                        programasRelacionados.isNotEmpty() -> "No se puede eliminar: el rancho tiene programas/monitoreos relacionados"
                        else -> {
                            database.localRanchDao().deleteRanch(rancho)
                            null
                        }
                    }
                }
                onMensaje(mensajeBloqueo ?: "Rancho eliminado")
                recargarDatos()
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("No se pudo eliminar el rancho: ${e.message}")
            } finally {
                guardando = false
                mostrarConfirmacionEliminar = null
            }
        }
    }

    fun eliminarParcela(parcela: LocalPlotEntity) {
        coroutineScope.launch {
            guardando = true
            try {
                val mensajeBloqueo = withContext(Dispatchers.IO) {
                    val programasRelacionados = database.localprogramDao()
                        .getProgramsByPlot(parcela.idLocalPlot)
                    val headersRelacionados = database.localphytomonitoringheaderDao()
                        .getHeadersByPlot(parcela.idLocalPlot)

                    when {
                        programasRelacionados.isNotEmpty() -> "No se puede eliminar: la parcela tiene programas relacionados"
                        headersRelacionados.isNotEmpty() -> "No se puede eliminar: la parcela tiene monitoreos relacionados"
                        else -> {
                            database.localPlotDao().deletePlot(parcela)
                            null
                        }
                    }
                }
                onMensaje(mensajeBloqueo ?: "Parcela eliminada")
                recargarDatos()
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("No se pudo eliminar la parcela: ${e.message}")
            } finally {
                guardando = false
                mostrarConfirmacionEliminar = null
            }
        }
    }

    if (mostrarDialogProductor) {
        DialogProductorGestion(
            guardando = guardando,
            productor = productorEnEdicion,
            onDismiss = { cerrarDialogos() },
            onGuardar = { nombre, slug ->
                guardarProductor(productorEnEdicion, nombre, slug)
            }
        )
    }

    if (mostrarDialogRancho) {
        DialogRanchoGestion(
            guardando = guardando,
            rancho = ranchoEnEdicion,
            productores = productores,
            onDismiss = { cerrarDialogos() },
            onGuardar = { productor, nombre, codigo, lat, lon ->
                guardarRancho(ranchoEnEdicion, productor, nombre, codigo, lat, lon)
            }
        )
    }

    if (mostrarDialogParcela) {
        DialogParcelaGestion(
            guardando = guardando,
            parcela = parcelaEnEdicion,
            ranchos = ranchos,
            productores = productores,
            onDismiss = { cerrarDialogos() },
            onMensaje = onMensaje,
            onGuardar = { rancho, codigo, descripcion, lat, lon, puntosPoligono ->
                guardarParcela(
                    parcelaBase = parcelaEnEdicion,
                    rancho = rancho,
                    codigo = codigo,
                    descripcion = descripcion,
                    latTexto = lat,
                    lonTexto = lon,
                    puntosPoligono = puntosPoligono
                )
            }
        )
    }

    if (mostrarDialogImportarRanchosCsv) {
        DialogImportarRanchosCsvGestion(
            guardando = guardando,
            productores = productores,
            onDismiss = { cerrarDialogos() },
            onAceptar = { productorDefault, ranchosCsv ->
                guardarRanchosDesdeCsv(productorDefault, ranchosCsv)
            },
            onMensaje = onMensaje
        )
    }

    if (mostrarDialogImportarParcelasCsv) {
        DialogImportarParcelasCsvGestion(
            guardando = guardando,
            ranchos = ranchos,
            onDismiss = { cerrarDialogos() },
            onAceptar = { ranchoDefault, parcelasCsv ->
                guardarParcelasDesdeCsv(ranchoDefault, parcelasCsv)
            },
            onMensaje = onMensaje
        )
    }

    mostrarConfirmacionEliminar?.let { confirmacion ->
        AlertDialog(
            onDismissRequest = {
                if (!guardando) mostrarConfirmacionEliminar = null
            },
            title = {
                Text(
                    text = "Confirmar eliminación",
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFB71C1C)
                )
            },
            text = {
                Text(
                    text = confirmacion.mensaje,
                    color = Color(0xFF3B3B3B),
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (confirmacion) {
                            is ConfirmacionEliminarGestion.Productor -> eliminarProductor(confirmacion.productor)
                            is ConfirmacionEliminarGestion.Rancho -> eliminarRancho(confirmacion.rancho)
                            is ConfirmacionEliminarGestion.Parcela -> eliminarParcela(confirmacion.parcela)
                        }
                    },
                    enabled = !guardando,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Eliminar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionEliminar = null }, enabled = !guardando) {
                    Text("Cancelar", color = Color(0xFF2E7D32))
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

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
                onAdminClick = onAdminClick,
                onCerrarSesionClick = onCerrarSesionClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AdminGestionHeroCard(
                nombreCia = nombreCia,
                totalProductores = productores.size,
                totalRanchos = ranchos.size,
                totalParcelas = parcelas.size,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AdminGestionTabs(
                tabActual = tabActual,
                onTabChange = { tabActual = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (idLocalCia == null) {
                EmptyGestionMessage("No hay una CIA seleccionada. Primero selecciona una CIA para administrar sus productores, ranchos y parcelas.")
            } else if (cargando) {
                AdminGestionLoadingCard()
            } else {
                when (tabActual) {
                    AdminGestionTab.PRODUCTORES -> {
                        GestionSectionCard(
                            icono = "👨‍🌾",
                            titulo = "Productores",
                            descripcion = "Agrega productores y asígnalos a la CIA actual.",
                            botonTexto = "Agregar productor",
                            onAgregarClick = { abrirAgregarProductor() }
                        ) {
                            if (productores.isEmpty()) {
                                EmptyGestionMessage("Todavía no hay productores registrados en esta CIA.")
                            } else {
                                productores.forEach { productor ->
                                    val totalRanchos = ranchos.count { it.idLocalAgroUnit == productor.idLocalAgroUnit }
                                    GestionInfoItem(
                                        icono = "👨‍🌾",
                                        titulo = productor.commercial_name,
                                        subtitulo = "Código/slug: ${productor.slug}  •  Ranchos: $totalRanchos",
                                        onEditar = { abrirEditarProductor(productor) },
                                        onEliminar = {
                                            mostrarConfirmacionEliminar = ConfirmacionEliminarGestion.Productor(productor)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    AdminGestionTab.RANCHOS -> {
                        GestionSectionCard(
                            icono = "🏡",
                            titulo = "Ranchos",
                            descripcion = "Cada rancho debe pertenecer a un productor.",
                            botonTexto = "Agregar rancho",
                            onAgregarClick = { abrirAgregarRancho() }
                        ) {
                            if (productores.isEmpty()) {
                                EmptyGestionMessage("Primero agrega un productor para poder crear ranchos.")
                            } else {
                                GestionSecondaryActionButton(
                                    text = "Importar ranchos desde archivo",
                                    icon = "📄",
                                    onClick = { abrirImportarRanchosCsv() }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            if (productores.isNotEmpty() && ranchos.isEmpty()) {
                                EmptyGestionMessage("Todavía no hay ranchos registrados.")
                            } else if (ranchos.isNotEmpty()) {
                                ranchos.forEach { rancho ->
                                    val productor = productores.firstOrNull { it.idLocalAgroUnit == rancho.idLocalAgroUnit }
                                    val totalParcelas = parcelas.count { it.idLocalRanch == rancho.idLocalRanch }
                                    GestionInfoItem(
                                        icono = "🏡",
                                        titulo = rancho.name,
                                        subtitulo = "Código: ${rancho.code}  •  Productor: ${productor?.commercial_name ?: "No encontrado"}  •  Parcelas: $totalParcelas",
                                        onEditar = { abrirEditarRancho(rancho) },
                                        onEliminar = {
                                            mostrarConfirmacionEliminar = ConfirmacionEliminarGestion.Rancho(rancho)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    AdminGestionTab.PARCELAS -> {
                        GestionSectionCard(
                            icono = "🌾",
                            titulo = "Parcelas",
                            descripcion = "Cada parcela debe pertenecer a un rancho. Al agregar o editar puedes cargar sus coordenadas en el mismo formulario.",
                            botonTexto = "Agregar parcela",
                            onAgregarClick = { abrirAgregarParcela() }
                        ) {
                            if (ranchos.isEmpty()) {
                                EmptyGestionMessage("Primero agrega un rancho para poder crear parcelas.")
                            } else if (parcelas.isEmpty()) {
                                EmptyGestionMessage("Todavía no hay parcelas registradas.")
                            } else {
                                parcelas.forEach { parcela ->
                                    val rancho = ranchos.firstOrNull { it.idLocalRanch == parcela.idLocalRanch }
                                    val verticesParcela = vertices
                                        .filter { it.idLocalPlot == parcela.idLocalPlot }
                                        .sortedBy { it.level }

                                    val puntosPoligono = verticesParcela.map { vertice ->
                                        ImportedPolygonPoint(
                                            level = vertice.level,
                                            lat = vertice.lat,
                                            lon = vertice.lon
                                        )
                                    }

                                    val totalCoordenadas = verticesParcela.size
                                    val estadoCoordenadas = if (totalCoordenadas >= 3) {
                                        "Coordenadas cargadas: $totalCoordenadas"
                                    } else {
                                        "Sin coordenadas"
                                    }

                                    GestionInfoItem(
                                        icono = "🌾",
                                        titulo = parcela.nombreMostrarGestion(),
                                        subtitulo = "Rancho: ${rancho?.name ?: "No encontrado"}  •  $estadoCoordenadas  •  Centro: ${parcela.lat ?: "-"}, ${parcela.lon ?: "-"}  •  Nombre: ${parcela.name}",
                                        onEditar = { abrirEditarParcela(parcela) },
                                        onEliminar = {
                                            mostrarConfirmacionEliminar = ConfirmacionEliminarGestion.Parcela(parcela)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (puntosPoligono.size >= 3) {
                                        PolygonPreviewCardGestion(
                                            puntos = puntosPoligono,
                                            altura = 170
                                        )
                                    } else {
                                        EmptyGestionMessage(
                                            "Esta parcela todavía no tiene coordenadas. Presiona Editar y carga el CSV/Excel/Notas del polígono."
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

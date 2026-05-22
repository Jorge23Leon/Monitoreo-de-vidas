package com.example.myapplication.local.admin.catalogos

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AdminCatalogoTab {
    CULTIVOS,
    FITOSANITARIO
}

@Composable
fun AdminCatalogosScreen(
    database: AppDatabase,
    nombreUsuario: String,
    rolUsuario: String,
    nombreCia: String,
    onBackClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit,
    onMensaje: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var tabActual by remember { mutableStateOf(AdminCatalogoTab.CULTIVOS) }

    var cultivos by remember { mutableStateOf<List<LocalCropCatalogEntity>>(emptyList()) }
    var catalogoFito by remember { mutableStateOf<List<LocalPhytosanitaryCatalogEntity>>(emptyList()) }
    var etapas by remember { mutableStateOf<List<LocalPhytostageEntity>>(emptyList()) }

    var mostrarDialogCultivo by remember { mutableStateOf(false) }
    var mostrarDialogFito by remember { mutableStateOf(false) }

    var cultivoEnEdicion by remember { mutableStateOf<LocalCropCatalogEntity?>(null) }
    var fitoEnEdicion by remember { mutableStateOf<LocalPhytosanitaryCatalogEntity?>(null) }

    fun recargarCatalogos() {
        coroutineScope.launch {
            cargando = true
            try {
                val datos = withContext(Dispatchers.IO) {
                    Triple(
                        database.localCropCatalogDao().getAllCrops(),
                        database.localphytosanitarycatalogDao().getAllCatalogo(),
                        database.localphytostageDao().getAllPhytostages()
                    )
                }
                cultivos = datos.first
                catalogoFito = datos.second
                etapas = datos.third
            } catch (e: Exception) {
                e.printStackTrace()
                onMensaje("Error al cargar catálogos: ${e.message}")
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(Unit) {
        recargarCatalogos()
    }

    fun abrirAgregarCultivo() {
        cultivoEnEdicion = null
        mostrarDialogCultivo = true
    }

    fun abrirEditarCultivo(cultivo: LocalCropCatalogEntity) {
        cultivoEnEdicion = cultivo
        mostrarDialogCultivo = true
    }

    fun abrirAgregarFito() {
        fitoEnEdicion = null
        mostrarDialogFito = true
    }

    fun abrirEditarFito(item: LocalPhytosanitaryCatalogEntity) {
        if (esSinPlagaSistema(item)) {
            onMensaje("El registro 'Sin plaga' es del sistema y no se debe editar")
            return
        }
        fitoEnEdicion = item
        mostrarDialogFito = true
    }

    fun cerrarDialogos() {
        if (!guardando) {
            mostrarDialogCultivo = false
            mostrarDialogFito = false
            cultivoEnEdicion = null
            fitoEnEdicion = null
        }
    }

    fun guardarCultivo(
        cultivoBase: LocalCropCatalogEntity?,
        nombre: String,
        variedad: String,
        codigo: String,
        descripcion: String,
        photo: String?
    ) {
        val nombreLimpio = nombre.trim()
        val variedadLimpia = variedad.trim().ifBlank { null }
        val codigoLimpio = codigo.trim().ifBlank { null }
        val descripcionLimpia = descripcion.trim().ifBlank { null }
        val photoLimpia = photo?.trim()?.ifBlank { null }

        when {
            nombreLimpio.isBlank() -> onMensaje("Escribe el nombre del cultivo")
            cultivos.any {
                it.idCrop != (cultivoBase?.idCrop ?: -1L) &&
                        it.name.equals(nombreLimpio, ignoreCase = true) &&
                        (it.variedad ?: "").equals(variedadLimpia ?: "", ignoreCase = true)
            } -> onMensaje("Ese cultivo ya existe")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        withContext(Dispatchers.IO) {
                            if (cultivoBase == null) {
                                database.localCropCatalogDao().insertCrop(
                                    LocalCropCatalogEntity(
                                        extId = null,
                                        name = nombreLimpio,
                                        variedad = variedadLimpia,
                                        code = codigoLimpio,
                                        description = descripcionLimpia,
                                        photo = photoLimpia
                                    )
                                )
                            } else {
                                database.localCropCatalogDao().updateCrop(
                                    cultivoBase.copy(
                                        name = nombreLimpio,
                                        variedad = variedadLimpia,
                                        code = codigoLimpio,
                                        description = descripcionLimpia,
                                        photo = photoLimpia
                                    )
                                )
                            }
                        }
                        mostrarDialogCultivo = false
                        cultivoEnEdicion = null
                        onMensaje(if (cultivoBase == null) "Cultivo agregado" else "Cultivo actualizado")
                        recargarCatalogos()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMensaje("No se pudo guardar el cultivo: ${e.message}")
                    } finally {
                        guardando = false
                    }
                }
            }
        }
    }

    fun guardarFitosanitario(
        itemBase: LocalPhytosanitaryCatalogEntity?,
        nombre: String,
        tipo: String,
        cultivo: LocalCropCatalogEntity?,
        minRef: String,
        maxRef: String,
        descripcion: String,
        photo: String?,
        etapasTexto: String
    ) {
        val nombreLimpio = nombre.trim()
        val tipoLimpio = tipo.trim().uppercase()
        val minValue = minRef.trim().toIntOrNull()
        val maxValue = maxRef.trim().toIntOrNull()
        val descripcionLimpia = descripcion.trim().ifBlank { null }
        val photoLimpia = photo?.trim()?.ifBlank { null }
        val idCultivo = cultivo?.idCrop

        val etapasSeparadas = etapasTexto
            .split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val etapasPermitidas = etapasFijasPorTipoFitoAdmin(tipoLimpio)
        val etapasLimpias = etapasSeparadas
            .mapNotNull { normalizarEtapaFijaFitoAdmin(it) }
            .filter { etapa ->
                etapasPermitidas.any { permitida ->
                    permitida.equals(etapa, ignoreCase = true)
                }
            }
            .distinctBy { it.lowercase() }


        when {
            itemBase != null && esSinPlagaSistema(itemBase) -> onMensaje("El registro 'Sin plaga' es del sistema y no se debe editar")
            nombreLimpio.isBlank() -> onMensaje("Escribe el nombre de la plaga o enfermedad")
            tipoLimpio !in listOf("PLAGA", "ENFERMEDAD") -> onMensaje("Selecciona si es plaga o enfermedad")
            etapasLimpias.isEmpty() -> onMensaje("Selecciona al menos una etapa fija para esta plaga/enfermedad")
            minRef.isNotBlank() && minValue == null -> onMensaje("El valor mínimo debe ser numérico")
            maxRef.isNotBlank() && maxValue == null -> onMensaje("El valor máximo debe ser numérico")
            minValue != null && maxValue != null && minValue > maxValue -> onMensaje("El mínimo no puede ser mayor que el máximo")
            catalogoFito.any {
                it.idPhytosanitary != (itemBase?.idPhytosanitary ?: -1L) &&
                        it.name.equals(nombreLimpio, ignoreCase = true) &&
                        it.type.equals(tipoLimpio, ignoreCase = true) &&
                        it.idDefaultCrop == idCultivo
            } -> onMensaje("Ese registro ya existe para el cultivo seleccionado")
            else -> {
                coroutineScope.launch {
                    guardando = true
                    try {
                        withContext(Dispatchers.IO) {
                            val idFitosanitario = if (itemBase == null) {
                                database.localphytosanitarycatalogDao().insertPhytosanitary(
                                    LocalPhytosanitaryCatalogEntity(
                                        extId = null,
                                        name = nombreLimpio,
                                        type = tipoLimpio,
                                        minRefValue = minValue,
                                        maxRefValue = maxValue,
                                        description = descripcionLimpia,
                                        photo = photoLimpia,
                                        idDefaultCrop = idCultivo
                                    )
                                )
                            } else {
                                database.localphytosanitarycatalogDao().updatePhytosanitary(
                                    itemBase.copy(
                                        name = nombreLimpio,
                                        type = tipoLimpio,
                                        minRefValue = minValue,
                                        maxRefValue = maxValue,
                                        description = descripcionLimpia,
                                        photo = photoLimpia,
                                        idDefaultCrop = idCultivo
                                    )
                                )
                                itemBase.idPhytosanitary
                            }

                            val etapasActuales = database.localphytostageDao()
                                .getStagesByPhytosanitary(idFitosanitario)

                            val etapasAEliminar = etapasActuales
                                .filter { etapaActual ->
                                    etapasLimpias.none { etapaNueva ->
                                        etapaNueva.equals(etapaActual.stage, ignoreCase = true)
                                    }
                                }

                            etapasAEliminar.forEach { etapa ->
                                database.localphytostageDao().deletePhytostage(etapa)
                            }

                            val etapasNuevas = etapasLimpias
                                .filter { etapaNueva ->
                                    etapasActuales.none { etapaActual ->
                                        etapaActual.stage.equals(etapaNueva, ignoreCase = true)
                                    }
                                }
                                .map { etapaNueva ->
                                    LocalPhytostageEntity(
                                        ext_id = null,
                                        stage = etapaNueva,
                                        photo = null,
                                        idPhytosanitary = idFitosanitario
                                    )
                                }

                            if (etapasNuevas.isNotEmpty()) {
                                database.localphytostageDao().insertPhytostages(etapasNuevas)
                            }
                        }
                        mostrarDialogFito = false
                        fitoEnEdicion = null
                        onMensaje(
                            if (itemBase == null) {
                                "Catálogo fitosanitario agregado con etapas fijas"
                            } else {
                                "Catálogo fitosanitario actualizado con etapas fijas"
                            }
                        )
                        recargarCatalogos()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMensaje("No se pudo guardar el catálogo: ${e.message}")
                    } finally {
                        guardando = false
                    }
                }
            }
        }
    }

    if (mostrarDialogCultivo) {
        DialogCultivoAdmin(
            guardando = guardando,
            cultivo = cultivoEnEdicion,
            onDismiss = { cerrarDialogos() },
            onGuardar = { nombre, variedad, codigo, descripcion, photo ->
                guardarCultivo(cultivoEnEdicion, nombre, variedad, codigo, descripcion, photo)
            }
        )
    }

    if (mostrarDialogFito) {
        DialogFitosanitarioAdmin(
            guardando = guardando,
            item = fitoEnEdicion,
            cultivos = cultivos,
            etapas = etapas,
            onDismiss = { cerrarDialogos() },
            onGuardar = { nombre, tipo, cultivo, minRef, maxRef, descripcion, photo, etapasTexto ->
                guardarFitosanitario(
                    itemBase = fitoEnEdicion,
                    nombre = nombre,
                    tipo = tipo,
                    cultivo = cultivo,
                    minRef = minRef,
                    maxRef = maxRef,
                    descripcion = descripcion,
                    photo = photo,
                    etapasTexto = etapasTexto
                )
            }
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
                onAdminClick = onAdminClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AdminCatalogosHeroCard(
                nombreCia = nombreCia,
                totalCultivos = cultivos.size,
                totalFito = catalogoFito.size,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AdminCatalogTabs(
                tabActual = tabActual,
                onTabChange = { tabActual = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (cargando) {
                AdminCatalogLoadingCard()
            } else {
                when (tabActual) {
                    AdminCatalogoTab.CULTIVOS -> {
                        CatalogSectionCard(
                            icono = "🌱",
                            titulo = "Cultivos",
                            descripcion = "Registra cultivos con foto, variedad, código y descripción.",
                            botonTexto = "Agregar cultivo",
                            onAgregarClick = { abrirAgregarCultivo() }
                        ) {
                            if (cultivos.isEmpty()) {
                                EmptyCatalogMessage("Todavía no hay cultivos registrados.")
                            } else {
                                cultivos.forEach { cultivo ->
                                    CultivoCatalogItem(
                                        cultivo = cultivo,
                                        onEditar = { abrirEditarCultivo(cultivo) }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    AdminCatalogoTab.FITOSANITARIO -> {
                        CatalogSectionCard(
                            icono = "🐛",
                            titulo = "Plagas y enfermedades",
                            descripcion = "Registra plagas o enfermedades. Las etapas se seleccionan de una lista fija según el tipo.",
                            botonTexto = "Agregar plaga/enfermedad",
                            onAgregarClick = { abrirAgregarFito() }
                        ) {
                            if (catalogoFito.isEmpty()) {
                                EmptyCatalogMessage("Todavía no hay plagas o enfermedades registradas.")
                            } else {
                                catalogoFito.forEach { item ->
                                    val cultivo = cultivos.firstOrNull { it.idCrop == item.idDefaultCrop }
                                    val etapasItem = etapas
                                        .filter { it.idPhytosanitary == item.idPhytosanitary }
                                        .sortedBy { it.idLocalPhytostage }
                                        .map { it.stage }

                                    FitoCatalogItem(
                                        item = item,
                                        cultivo = cultivo,
                                        etapasAsociadas = etapasItem,
                                        onEditar = { abrirEditarFito(item) }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun AdminCatalogosHeroCard(
    nombreCia: String,
    totalCultivos: Int,
    totalFito: Int,
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
                        text = "📚 Administrar catálogos",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 21.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prepara cultivos, plagas y enfermedades. Las etapas ya están fijas y se seleccionan dentro de cada registro.",
                        color = Color(0xFFE8F5E9),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Volver")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "CIA actual: $nombreCia",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCatalogChip("Cultivos", totalCultivos, Modifier.weight(1f))
                StatCatalogChip("Fito", totalFito, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCatalogChip(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = label, color = Color(0xFFE8F5E9), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AdminCatalogTabs(
    tabActual: AdminCatalogoTab,
    onTabChange: (AdminCatalogoTab) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CatalogTabButton("Cultivos", "🌱", tabActual == AdminCatalogoTab.CULTIVOS, Modifier.weight(1f)) {
                onTabChange(AdminCatalogoTab.CULTIVOS)
            }
            CatalogTabButton("Fito", "🐛", tabActual == AdminCatalogoTab.FITOSANITARIO, Modifier.weight(1f)) {
                onTabChange(AdminCatalogoTab.FITOSANITARIO)
            }
        }
    }
}

@Composable
private fun CatalogTabButton(
    text: String,
    icon: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF2E7D32) else Color(0xFFF1F5EE))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFF2E4D22),
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AdminCatalogLoadingCard() {
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
            Text(text = "Cargando catálogos...", fontWeight = FontWeight.Bold, color = Color(0xFF2E4D22))
        }
    }
}

@Composable
private fun CatalogSectionCard(
    icono: String,
    titulo: String,
    descripcion: String,
    botonTexto: String,
    onAgregarClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) { Text(text = icono, fontSize = 22.sp) }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = titulo, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                    Text(text = descripcion, fontSize = 12.sp, color = Color(0xFF5E6D58), lineHeight = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onAgregarClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
            ) {
                Text(text = "+ $botonTexto", fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFD9E7D1))
            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun EmptyCatalogMessage(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            textAlign = TextAlign.Center,
            color = Color(0xFFE65100),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CultivoCatalogItem(
    cultivo: LocalCropCatalogEntity,
    onEditar: () -> Unit
) {
    CatalogInfoItem(
        icono = "🌱",
        photo = cultivo.photo,
        titulo = cultivo.name,
        subtitulo = listOfNotNull(
            cultivo.variedad?.let { "Variedad: $it" },
            cultivo.code?.let { "Código: $it" },
            cultivo.description
        ).joinToString("  •  ").ifBlank { "Sin información adicional" },
        onEditar = onEditar
    )
}

@Composable
private fun FitoCatalogItem(
    item: LocalPhytosanitaryCatalogEntity,
    cultivo: LocalCropCatalogEntity?,
    etapasAsociadas: List<String>,
    onEditar: () -> Unit
) {
    val tipoTexto = textoTipoAdmin(item.type)
    val esRegistroSistema = esSinPlagaSistema(item)
    val etapasTexto = etapasAsociadas
        .map { nombreVisibleEtapaFitoAdmin(it) }
        .joinToString(", ")
        .ifBlank { "Sin etapas asignadas" }

    CatalogInfoItem(
        icono = if (esRegistroSistema) "✅" else if (item.type.equals("ENFERMEDAD", ignoreCase = true)) "🦠" else "🐛",
        photo = item.photo,
        titulo = item.name,
        subtitulo = buildString {
            append(tipoTexto)
            append("  •  ")
            append(if (esRegistroSistema) "Registro del sistema" else cultivo?.name ?: "General")
            if (!esRegistroSistema) {
                append("  •  Etapas: ")
                append(etapasTexto)
            }
            if (!esRegistroSistema && (item.minRefValue != null || item.maxRefValue != null)) {
                append("  •  Ref: ${item.minRefValue ?: "-"} a ${item.maxRefValue ?: "-"}")
            }
            if (!item.description.isNullOrBlank()) {
                append("  •  ${item.description}")
            }
        },
        onEditar = if (esRegistroSistema) null else onEditar,
        textoBloqueado = if (esRegistroSistema) "Sistema" else null
    )
}

@Composable
private fun CatalogInfoItem(
    icono: String,
    photo: String?,
    titulo: String,
    subtitulo: String,
    onEditar: (() -> Unit)?,
    textoBloqueado: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CatalogPhotoBox(photo = photo, fallbackIcon = icono, sizeDp = 46)

            Column(modifier = Modifier.weight(1f)) {
                Text(text = titulo, fontWeight = FontWeight.Black, color = Color(0xFF263B1E), fontSize = 14.sp)
                Text(text = subtitulo, color = Color(0xFF5E6D58), fontSize = 12.sp, lineHeight = 15.sp)
            }

            if (onEditar != null) {
                OutlinedButton(
                    onClick = onEditar,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
                ) {
                    Text("Editar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else if (!textoBloqueado.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textoBloqueado,
                        color = Color(0xFF1B5E20),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogCultivoAdmin(
    guardando: Boolean,
    cultivo: LocalCropCatalogEntity?,
    onDismiss: () -> Unit,
    onGuardar: (String, String, String, String, String?) -> Unit
) {
    var nombre by remember(cultivo?.idCrop) { mutableStateOf(cultivo?.name ?: "") }
    var variedad by remember(cultivo?.idCrop) { mutableStateOf(cultivo?.variedad ?: "") }
    var codigo by remember(cultivo?.idCrop) { mutableStateOf(cultivo?.code ?: "") }
    var descripcion by remember(cultivo?.idCrop) { mutableStateOf(cultivo?.description ?: "") }
    var photo by remember(cultivo?.idCrop) { mutableStateOf(cultivo?.photo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AdminDialogConfirmButton(
                text = if (cultivo == null) "Guardar" else "Actualizar",
                guardando = guardando,
                enabled = nombre.trim().isNotBlank(),
                onClick = { onGuardar(nombre, variedad, codigo, descripcion, photo) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = { AdminDialogTitle(if (cultivo == null) "Agregar cultivo" else "Editar cultivo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogInfoText("El cultivo quedará disponible para programar monitoreos.")
                Spacer(modifier = Modifier.height(12.dp))
                AdminImagePickerField(photo = photo, guardando = guardando, onPhotoChange = { photo = it })
                Spacer(modifier = Modifier.height(12.dp))
                AdminDialogTextField(nombre, { nombre = it }, "Nombre del cultivo", "Ejemplo: Maíz", guardando)
                Spacer(modifier = Modifier.height(8.dp))
                AdminDialogTextField(variedad, { variedad = it }, "Variedad", "Opcional", guardando)
                Spacer(modifier = Modifier.height(8.dp))
                AdminDialogTextField(codigo, { codigo = it }, "Código", "Opcional", guardando)
                Spacer(modifier = Modifier.height(8.dp))
                AdminDialogTextField(descripcion, { descripcion = it }, "Descripción", "Opcional", guardando)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun DialogFitosanitarioAdmin(
    guardando: Boolean,
    item: LocalPhytosanitaryCatalogEntity?,
    cultivos: List<LocalCropCatalogEntity>,
    etapas: List<LocalPhytostageEntity>,
    onDismiss: () -> Unit,
    onGuardar: (String, String, LocalCropCatalogEntity?, String, String, String, String?, String) -> Unit
) {
    val etapasActuales = remember(item?.idPhytosanitary, etapas) {
        if (item == null) {
            emptyList()
        } else {
            etapas
                .filter { it.idPhytosanitary == item.idPhytosanitary }
                .sortedBy { it.idLocalPhytostage }
                .mapNotNull { normalizarEtapaFijaFitoAdmin(it.stage) }
        }
    }

    var nombre by remember(item?.idPhytosanitary) { mutableStateOf(item?.name ?: "") }
    var tipo by remember(item?.idPhytosanitary) { mutableStateOf(item?.type?.uppercase() ?: "PLAGA") }
    var cultivoSeleccionado by remember(item?.idPhytosanitary, cultivos) {
        mutableStateOf(cultivos.firstOrNull { it.idCrop == item?.idDefaultCrop })
    }
    var minRef by remember(item?.idPhytosanitary) { mutableStateOf(item?.minRefValue?.toString() ?: "") }
    var maxRef by remember(item?.idPhytosanitary) { mutableStateOf(item?.maxRefValue?.toString() ?: "") }
    var descripcion by remember(item?.idPhytosanitary) { mutableStateOf(item?.description ?: "") }
    var photo by remember(item?.idPhytosanitary) { mutableStateOf(item?.photo) }
    var etapasSeleccionadas by remember(item?.idPhytosanitary, etapasActuales) {
        mutableStateOf(etapasActuales.distinctBy { it.lowercase() })
    }

    val etapasPermitidas = remember(tipo) {
        etapasFijasPorTipoFitoAdmin(tipo)
    }

    LaunchedEffect(tipo, item?.idPhytosanitary) {
        val seleccionValidada = etapasSeleccionadas
            .mapNotNull { normalizarEtapaFijaFitoAdmin(it) }
            .filter { etapa ->
                etapasPermitidas.any { permitida ->
                    permitida.equals(etapa, ignoreCase = true)
                }
            }
            .distinctBy { it.lowercase() }

        etapasSeleccionadas = if (seleccionValidada.isEmpty()) {
            etapasPermitidas
        } else {
            seleccionValidada
        }
    }

    val etapasTexto = etapasSeleccionadas.joinToString("\n")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AdminDialogConfirmButton(
                text = if (item == null) "Guardar" else "Actualizar",
                guardando = guardando,
                enabled = nombre.trim().isNotBlank() && etapasSeleccionadas.isNotEmpty(),
                onClick = { onGuardar(nombre, tipo, cultivoSeleccionado, minRef, maxRef, descripcion, photo, etapasTexto) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = { AdminDialogTitle(if (item == null) "Agregar plaga/enfermedad" else "Editar plaga/enfermedad") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogInfoText(
                    "Puedes dejarlo como general o asociarlo a un cultivo. " +
                            "Las etapas ya están controladas por el sistema y solo puedes seleccionar las que correspondan al tipo."
                )
                Spacer(modifier = Modifier.height(12.dp))
                AdminImagePickerField(photo = photo, guardando = guardando, onPhotoChange = { photo = it })
                Spacer(modifier = Modifier.height(12.dp))
                AdminDialogTextField(nombre, { nombre = it }, "Nombre", "Ejemplo: Gusano cogollero", guardando)
                Spacer(modifier = Modifier.height(10.dp))
                TypeSelectorAdmin(
                    tipo = tipo,
                    onTipoChange = { nuevoTipo ->
                        tipo = nuevoTipo
                        etapasSeleccionadas = etapasFijasPorTipoFitoAdmin(nuevoTipo)
                    },
                    habilitado = !guardando
                )
                Spacer(modifier = Modifier.height(10.dp))
                AdminSelectorFieldCatalog(
                    etiqueta = "Cultivo relacionado",
                    valor = cultivoSeleccionado?.let {
                        if (it.variedad.isNullOrBlank()) it.name else "${it.name} - ${it.variedad}"
                    } ?: "General / sin cultivo específico",
                    opciones = cultivos,
                    textoOpcion = { if (it.variedad.isNullOrBlank()) it.name else "${it.name} - ${it.variedad}" },
                    habilitado = !guardando,
                    permitirGeneral = true,
                    onGeneral = { cultivoSeleccionado = null },
                    onSeleccionar = { cultivoSeleccionado = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminDialogNumberField(minRef, { minRef = it }, "Mín", guardando, Modifier.weight(1f))
                    AdminDialogNumberField(maxRef, { maxRef = it }, "Máx", guardando, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))

                AdminEtapasSelectorField(
                    tipo = tipo,
                    etapasSeleccionadas = etapasSeleccionadas,
                    guardando = guardando,
                    onToggleEtapa = { etapa ->
                        etapasSeleccionadas = if (etapasSeleccionadas.any { it.equals(etapa, ignoreCase = true) }) {
                            etapasSeleccionadas.filterNot { it.equals(etapa, ignoreCase = true) }
                        } else {
                            (etapasSeleccionadas + etapa).distinctBy { it.lowercase() }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                AdminDialogTextField(descripcion, { descripcion = it }, "Descripción", "Opcional", guardando)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun AdminEtapasSelectorField(
    tipo: String,
    etapasSeleccionadas: List<String>,
    guardando: Boolean,
    onToggleEtapa: (String) -> Unit
) {
    val etapasPermitidas = etapasFijasPorTipoFitoAdmin(tipo)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Etapas fijas para ${textoTipoAdmin(tipo)}",
                fontWeight = FontWeight.Black,
                color = Color(0xFF1B5E20),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            DialogInfoText(
                if (tipo.equals("ENFERMEDAD", ignoreCase = true)) {
                    "Selecciona únicamente las etapas permitidas para enfermedades."
                } else {
                    "Selecciona únicamente las etapas permitidas para plagas."
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            etapasPermitidas.forEachIndexed { index, etapa ->
                AdminEtapaFijaSeleccionableItem(
                    numero = index + 1,
                    etapa = etapa,
                    seleccionada = etapasSeleccionadas.any { it.equals(etapa, ignoreCase = true) },
                    guardando = guardando,
                    onClick = { onToggleEtapa(etapa) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (etapasSeleccionadas.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selecciona al menos una etapa para guardar.",
                    color = Color(0xFFD84315),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AdminEtapaFijaSeleccionableItem(
    numero: Int,
    etapa: String,
    seleccionada: Boolean,
    guardando: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (seleccionada) Color(0xFF2E7D32) else Color(0xFFD9E7D1),
                shape = RoundedCornerShape(14.dp)
            )
            .background(if (seleccionada) Color(0xFFE8F5E9) else Color.White)
            .clickable(enabled = !guardando) { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (seleccionada) Color(0xFF2E7D32) else Color(0xFFF1F5EE)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (seleccionada) "✓" else numero.toString(),
                color = if (seleccionada) Color.White else Color(0xFF1B5E20),
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
        }

        Text(
            text = nombreVisibleEtapaFitoAdmin(etapa),
            modifier = Modifier.weight(1f),
            color = Color(0xFF263B1E),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        Text(
            text = if (seleccionada) "Seleccionada" else "Agregar",
            color = if (seleccionada) Color(0xFF2E7D32) else Color(0xFF5E6D58),
            fontWeight = FontWeight.Black,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AdminImagePickerField(
    photo: String?,
    guardando: Boolean,
    onPhotoChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Algunos proveedores no permiten permiso persistente; aun así guardamos el URI.
            }
            onPhotoChange(uri.toString())
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF6))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Imagen",
                fontWeight = FontWeight.Black,
                color = Color(0xFF1B5E20),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CatalogPhotoBox(photo = photo, fallbackIcon = "🖼️", sizeDp = 70)
                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { launcher.launch(arrayOf("image/*")) },
                        enabled = !guardando,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        Text(if (photo.isNullOrBlank()) "Seleccionar imagen" else "Cambiar imagen")
                    }
                    AnimatedVisibility(visible = !photo.isNullOrBlank()) {
                        TextButton(
                            onClick = { onPhotoChange(null) },
                            enabled = !guardando
                        ) {
                            Text("Quitar imagen", color = Color(0xFFD84315))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogPhotoBox(
    photo: String?,
    fallbackIcon: String,
    sizeDp: Int
) {
    val context = LocalContext.current
    var bitmap by remember(photo) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photo) {
        bitmap = if (photo.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(photo)).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFD9E7D1),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val img = bitmap
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = "Imagen del catálogo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(text = fallbackIcon, fontSize = (sizeDp / 2).sp)
        }
    }
}

@Composable
private fun AdminDialogConfirmButton(
    text: String,
    guardando: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !guardando && enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (guardando) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Guardando")
        } else {
            Text(text)
        }
    }
}

@Composable
private fun AdminDialogTitle(text: String) {
    Text(text = text, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
}

@Composable
private fun DialogInfoText(text: String) {
    Text(text = text, fontSize = 12.sp, color = Color(0xFF5E6D58), lineHeight = 15.sp)
}

@Composable
private fun AdminDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    guardando: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        enabled = !guardando,
        singleLine = true
    )
}

@Composable
private fun AdminDialogMultiLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    guardando: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        enabled = !guardando,
        singleLine = false,
        minLines = 3,
        maxLines = 6
    )
}

@Composable
private fun AdminDialogNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    guardando: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() }) },
        modifier = modifier,
        label = { Text(label) },
        enabled = !guardando,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun TypeSelectorAdmin(
    tipo: String,
    onTipoChange: (String) -> Unit,
    habilitado: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TypeButtonAdmin(
            text = "Plaga",
            icon = "🐛",
            selected = tipo == "PLAGA",
            enabled = habilitado,
            modifier = Modifier.weight(1f),
            onClick = { onTipoChange("PLAGA") }
        )
        TypeButtonAdmin(
            text = "Enfermedad",
            icon = "🦠",
            selected = tipo == "ENFERMEDAD",
            enabled = habilitado,
            modifier = Modifier.weight(1f),
            onClick = { onTipoChange("ENFERMEDAD") }
        )
    }
}

@Composable
private fun TypeButtonAdmin(
    text: String,
    icon: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF2E7D32) else Color(0xFFD9E7D1),
                shape = RoundedCornerShape(16.dp)
            )
            .background(if (selected) Color(0xFFE8F5E9) else Color.White)
            .clickable(enabled = enabled) { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Black,
            color = if (selected) Color(0xFF1B5E20) else Color(0xFF5E6D58),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun <T> AdminSelectorFieldCatalog(
    etiqueta: String,
    valor: String,
    opciones: List<T>,
    textoOpcion: (T) -> String,
    habilitado: Boolean,
    permitirGeneral: Boolean,
    onGeneral: () -> Unit,
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
                if (permitirGeneral) {
                    DropdownMenuItem(
                        text = { Text("General / sin cultivo específico") },
                        onClick = {
                            abierto = false
                            onGeneral()
                        }
                    )
                }

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


private fun etapasFijasPorTipoFitoAdmin(tipo: String): List<String> {
    return when (tipo.trim().uppercase()) {
        "ENFERMEDAD", "ENFERMEDADES", "DISEASE" -> listOf(
            "inicio",
            "desarrollo",
            "avanzado",
            "terminal"
        )

        else -> listOf(
            "huevecillo",
            "larva",
            "pupa",
            "adulto",
            "adulto_alas"
        )
    }
}

private fun normalizarEtapaFijaFitoAdmin(etapa: String): String? {
    val limpia = etapa.trim()
        .lowercase()
        .replace("-", "_")
        .replace(" ", "_")

    return when (limpia) {
        "huevo", "huevecillo" -> "huevecillo"
        "larva" -> "larva"
        "pupa" -> "pupa"
        "adulto" -> "adulto"
        "adulto_ala", "adulto_alas", "adultoalas" -> "adulto_alas"
        "inicio" -> "inicio"
        "desarrollo" -> "desarrollo"
        "avanzado" -> "avanzado"
        "terminal" -> "terminal"
        else -> null
    }
}

private fun nombreVisibleEtapaFitoAdmin(etapa: String): String {
    return when (normalizarEtapaFijaFitoAdmin(etapa) ?: etapa.trim().lowercase()) {
        "huevecillo" -> "Huevecillo"
        "larva" -> "Larva"
        "pupa" -> "Pupa"
        "adulto" -> "Adulto"
        "adulto_alas" -> "Adulto alas"
        "inicio" -> "Inicio"
        "desarrollo" -> "Desarrollo"
        "avanzado" -> "Avanzado"
        "terminal" -> "Terminal"
        else -> etapa
    }
}

private fun esSinPlagaSistema(item: LocalPhytosanitaryCatalogEntity): Boolean {
    val tipoNormalizado = item.type.trim()
        .lowercase()
        .replace("-", "_")
        .replace(" ", "_")

    val nombreNormalizado = item.name.trim()
        .lowercase()
        .replace("_", " ")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")

    return tipoNormalizado in listOf("sin_plaga", "no_plaga") ||
            nombreNormalizado in listOf(
        "sin plaga",
        "sin plagas",
        "no hay plaga",
        "no hay plagas",
        "sin enfermedad",
        "sin enfermedades"
    )
}

private fun textoTipoAdmin(type: String): String {
    return when (type.trim().lowercase()) {
        "plaga" -> "Plaga"
        "enfermedad" -> "Enfermedad"
        "sin_plaga", "sin plaga" -> "Sin plaga"
        "no_plaga", "no plaga" -> "Sin plaga"
        else -> type
    }
}

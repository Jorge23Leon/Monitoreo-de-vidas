package com.example.myapplication.local.admin.catalogos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SeccionFitosanitarioAdmin(
    catalogoFito: List<LocalPhytosanitaryCatalogEntity>,
    cultivos: List<LocalCropCatalogEntity>,
    etapas: List<LocalPhytostageEntity>,
    onAgregarFito: () -> Unit,
    onEditarFito: (LocalPhytosanitaryCatalogEntity) -> Unit
) {
    CatalogSectionCard(
        icono = "🐛",
        titulo = "Plagas y enfermedades",
        descripcion = "Registra plagas o enfermedades. Las etapas se seleccionan de una lista fija según el tipo.",
        botonTexto = "Agregar plaga/enfermedad",
        onAgregarClick = onAgregarFito
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

                ItemFitosanitarioAdmin(
                    item = item,
                    cultivo = cultivo,
                    etapasAsociadas = etapasItem,
                    onEditar = { onEditarFito(item) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun ItemFitosanitarioAdmin(
    item: LocalPhytosanitaryCatalogEntity,
    cultivo: LocalCropCatalogEntity?,
    etapasAsociadas: List<String>,
    onEditar: () -> Unit
) {
    val tipoTexto = textoTipoAdmin(item.type)
    val esRegistroSistema = esSinPlagaSistemaAdmin(item)
    val etapasTexto = etapasAsociadas
        .map { nombreVisibleEtapaFitoAdmin(it) }
        .joinToString(", ")
        .ifBlank { "Sin etapas asignadas" }

    CatalogInfoItemAdmin(
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
internal fun DialogoFitosanitarioAdmin(
    guardando: Boolean,
    item: LocalPhytosanitaryCatalogEntity?,
    cultivos: List<LocalCropCatalogEntity>,
    etapas: List<LocalPhytostageEntity>,
    onDismiss: () -> Unit,
    onGuardar: (String, String, LocalCropCatalogEntity?, String, String, String, String?, List<EtapaFitoAdminUi>) -> Unit
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

    val fotosEtapasActuales = remember(item?.idPhytosanitary, etapas) {
        if (item == null) {
            emptyMap<String, String?>()
        } else {
            etapas
                .filter { it.idPhytosanitary == item.idPhytosanitary }
                .associate { etapa ->
                    val etapaNormalizada = normalizarEtapaFijaFitoAdmin(etapa.stage) ?: etapa.stage
                    etapaNormalizada to etapa.photo
                }
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
    var fotosEtapas by remember(item?.idPhytosanitary, fotosEtapasActuales) {
        mutableStateOf(fotosEtapasActuales)
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

        fotosEtapas = fotosEtapas.filterKeys { etapa ->
            etapasPermitidas.any { permitida ->
                permitida.equals(etapa, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AdminDialogConfirmButton(
                text = if (item == null) "Guardar" else "Actualizar",
                guardando = guardando,
                enabled = nombre.trim().isNotBlank() && etapasSeleccionadas.isNotEmpty(),
                onClick = {
                    val etapasParaGuardar = etapasSeleccionadas.map { etapa ->
                        val etapaNormalizada = normalizarEtapaFijaFitoAdmin(etapa) ?: etapa
                        EtapaFitoAdminUi(
                            nombre = etapaNormalizada,
                            photo = fotosEtapas[etapaNormalizada]
                        )
                    }

                    onGuardar(
                        nombre,
                        tipo,
                        cultivoSeleccionado,
                        minRef,
                        maxRef,
                        descripcion,
                        photo,
                        etapasParaGuardar
                    )
                }
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
                SelectorTipoFitosanitarioAdmin(
                    tipo = tipo,
                    onTipoChange = { nuevoTipo ->
                        tipo = nuevoTipo
                        etapasSeleccionadas = etapasFijasPorTipoFitoAdmin(nuevoTipo)
                        fotosEtapas = emptyMap()
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

                SelectorEtapasFijasAdmin(
                    tipo = tipo,
                    etapasSeleccionadas = etapasSeleccionadas,
                    fotosEtapas = fotosEtapas,
                    guardando = guardando,
                    onToggleEtapa = { etapa ->
                        val etapaNormalizada = normalizarEtapaFijaFitoAdmin(etapa) ?: etapa
                        val yaSeleccionada = etapasSeleccionadas.any {
                            it.equals(etapaNormalizada, ignoreCase = true)
                        }

                        etapasSeleccionadas = if (yaSeleccionada) {
                            etapasSeleccionadas.filterNot {
                                it.equals(etapaNormalizada, ignoreCase = true)
                            }
                        } else {
                            (etapasSeleccionadas + etapaNormalizada).distinctBy { it.lowercase() }
                        }

                        if (yaSeleccionada) {
                            fotosEtapas = fotosEtapas - etapaNormalizada
                        }
                    },
                    onPhotoEtapaChange = { etapa, nuevaFoto ->
                        val etapaNormalizada = normalizarEtapaFijaFitoAdmin(etapa) ?: etapa
                        fotosEtapas = fotosEtapas + (etapaNormalizada to nuevaFoto)
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
private fun SelectorEtapasFijasAdmin(
    tipo: String,
    etapasSeleccionadas: List<String>,
    fotosEtapas: Map<String, String?>,
    guardando: Boolean,
    onToggleEtapa: (String) -> Unit,
    onPhotoEtapaChange: (String, String?) -> Unit
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
                    "Selecciona las etapas permitidas para enfermedades y agrega imagen a cada una."
                } else {
                    "Selecciona las etapas permitidas para plagas y agrega imagen a cada una."
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            etapasPermitidas.forEachIndexed { index, etapa ->
                val etapaNormalizada = normalizarEtapaFijaFitoAdmin(etapa) ?: etapa
                val seleccionada = etapasSeleccionadas.any {
                    it.equals(etapaNormalizada, ignoreCase = true)
                }

                ItemEtapaFijaAdmin(
                    numero = index + 1,
                    etapa = etapaNormalizada,
                    seleccionada = seleccionada,
                    photo = fotosEtapas[etapaNormalizada],
                    guardando = guardando,
                    onClick = { onToggleEtapa(etapaNormalizada) },
                    onPhotoChange = { nuevaFoto ->
                        onPhotoEtapaChange(etapaNormalizada, nuevaFoto)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
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
private fun ItemEtapaFijaAdmin(
    numero: Int,
    etapa: String,
    seleccionada: Boolean,
    photo: String?,
    guardando: Boolean,
    onClick: () -> Unit,
    onPhotoChange: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (seleccionada) Color(0xFF2E7D32) else Color(0xFFD9E7D1),
                shape = RoundedCornerShape(14.dp)
            )
            .background(if (seleccionada) Color(0xFFE8F5E9) else Color.White)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !guardando) { onClick() },
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

        if (seleccionada) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Imagen de ${nombreVisibleEtapaFitoAdmin(etapa)}",
                color = Color(0xFF1B5E20),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AdminImagePickerField(
                photo = photo,
                guardando = guardando,
                onPhotoChange = onPhotoChange
            )
        }
    }
}

@Composable
private fun SelectorTipoFitosanitarioAdmin(
    tipo: String,
    onTipoChange: (String) -> Unit,
    habilitado: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BotonTipoFitosanitarioAdmin(
            text = "Plaga",
            icon = "🐛",
            selected = tipo == "PLAGA",
            enabled = habilitado,
            modifier = Modifier.weight(1f),
            onClick = { onTipoChange("PLAGA") }
        )
        BotonTipoFitosanitarioAdmin(
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
private fun BotonTipoFitosanitarioAdmin(
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

internal suspend fun guardarFitosanitarioAdmin(
    database: AppDatabase,
    catalogoFitoActual: List<LocalPhytosanitaryCatalogEntity>,
    itemBase: LocalPhytosanitaryCatalogEntity?,
    nombre: String,
    tipo: String,
    cultivo: LocalCropCatalogEntity?,
    minRef: String,
    maxRef: String,
    descripcion: String,
    photo: String?,
    etapasUi: List<EtapaFitoAdminUi>
): ResultadoOperacionCatalogoAdmin {
    val nombreLimpio = nombre.trim()
    val tipoLimpio = tipo.trim().uppercase()
    val minValue = minRef.trim().toIntOrNull()
    val maxValue = maxRef.trim().toIntOrNull()
    val descripcionLimpia = descripcion.trim().ifBlank { null }
    val photoLimpia = photo?.trim()?.ifBlank { null }
    val idCultivo = cultivo?.idCrop

    val etapasPermitidas = etapasFijasPorTipoFitoAdmin(tipoLimpio)
    val etapasLimpias = etapasUi
        .mapNotNull { etapaUi ->
            val nombreNormalizado = normalizarEtapaFijaFitoAdmin(etapaUi.nombre)
                ?: return@mapNotNull null

            val esPermitida = etapasPermitidas.any { permitida ->
                permitida.equals(nombreNormalizado, ignoreCase = true)
            }

            if (!esPermitida) {
                null
            } else {
                EtapaFitoAdminUi(
                    nombre = nombreNormalizado,
                    photo = etapaUi.photo?.trim()?.ifBlank { null }
                )
            }
        }
        .distinctBy { it.nombre.lowercase() }

    when {
        itemBase != null && esSinPlagaSistemaAdmin(itemBase) -> {
            return ResultadoOperacionCatalogoAdmin(false, "El registro 'Sin plaga' es del sistema y no se debe editar")
        }

        nombreLimpio.isBlank() -> {
            return ResultadoOperacionCatalogoAdmin(false, "Escribe el nombre de la plaga o enfermedad")
        }

        tipoLimpio !in listOf("PLAGA", "ENFERMEDAD") -> {
            return ResultadoOperacionCatalogoAdmin(false, "Selecciona si es plaga o enfermedad")
        }

        etapasLimpias.isEmpty() -> {
            return ResultadoOperacionCatalogoAdmin(false, "Selecciona al menos una etapa fija para esta plaga/enfermedad")
        }

        minRef.isNotBlank() && minValue == null -> {
            return ResultadoOperacionCatalogoAdmin(false, "El valor mínimo debe ser numérico")
        }

        maxRef.isNotBlank() && maxValue == null -> {
            return ResultadoOperacionCatalogoAdmin(false, "El valor máximo debe ser numérico")
        }

        minValue != null && maxValue != null && minValue > maxValue -> {
            return ResultadoOperacionCatalogoAdmin(false, "El mínimo no puede ser mayor que el máximo")
        }

        catalogoFitoActual.any {
            it.idPhytosanitary != (itemBase?.idPhytosanitary ?: -1L) &&
                    it.name.equals(nombreLimpio, ignoreCase = true) &&
                    it.type.equals(tipoLimpio, ignoreCase = true) &&
                    it.idDefaultCrop == idCultivo
        } -> {
            return ResultadoOperacionCatalogoAdmin(false, "Ese registro ya existe para el cultivo seleccionado")
        }
    }

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

        etapasActuales.forEach { etapa ->
            database.localphytostageDao().deletePhytostage(etapa)
        }

        val etapasNuevas = etapasLimpias.map { etapaNueva ->
            LocalPhytostageEntity(
                ext_id = null,
                stage = etapaNueva.nombre,
                photo = etapaNueva.photo,
                idPhytosanitary = idFitosanitario
            )
        }

        if (etapasNuevas.isNotEmpty()) {
            database.localphytostageDao().insertPhytostages(etapasNuevas)
        }
    }

    return ResultadoOperacionCatalogoAdmin(
        exito = true,
        mensaje = if (itemBase == null) {
            "Catálogo fitosanitario agregado con imágenes por etapa"
        } else {
            "Catálogo fitosanitario actualizado con imágenes por etapa"
        }
    )
}

internal fun etapasFijasPorTipoFitoAdmin(tipo: String): List<String> {
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

internal fun normalizarEtapaFijaFitoAdmin(etapa: String): String? {
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

internal fun nombreVisibleEtapaFitoAdmin(etapa: String): String {
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

internal fun esSinPlagaSistemaAdmin(item: LocalPhytosanitaryCatalogEntity): Boolean {
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

internal fun textoTipoAdmin(type: String): String {
    return when (type.trim().lowercase()) {
        "plaga" -> "Plaga"
        "enfermedad" -> "Enfermedad"
        "sin_plaga", "sin plaga" -> "Sin plaga"
        "no_plaga", "no plaga" -> "Sin plaga"
        else -> type
    }
}

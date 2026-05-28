package com.example.myapplication.local.admin.catalogos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.local.entities.AppDatabase
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SeccionCultivosAdmin(
    cultivos: List<LocalCropCatalogEntity>,
    onAgregarCultivo: () -> Unit,
    onEditarCultivo: (LocalCropCatalogEntity) -> Unit
) {
    CatalogSectionCard(
        icono = "🌱",
        titulo = "Cultivos",
        descripcion = "Registra cultivos con foto, variedad, código y descripción.",
        botonTexto = "Agregar cultivo",
        onAgregarClick = onAgregarCultivo
    ) {
        if (cultivos.isEmpty()) {
            EmptyCatalogMessage("Todavía no hay cultivos registrados.")
        } else {
            cultivos.forEach { cultivo ->
                ItemCultivoAdmin(
                    cultivo = cultivo,
                    onEditar = { onEditarCultivo(cultivo) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun ItemCultivoAdmin(
    cultivo: LocalCropCatalogEntity,
    onEditar: () -> Unit
) {
    CatalogInfoItemAdmin(
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
internal fun DialogoCultivoAdmin(
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

internal suspend fun guardarCultivoAdmin(
    database: AppDatabase,
    cultivosActuales: List<LocalCropCatalogEntity>,
    cultivoBase: LocalCropCatalogEntity?,
    nombre: String,
    variedad: String,
    codigo: String,
    descripcion: String,
    photo: String?
): ResultadoOperacionCatalogoAdmin {
    val nombreLimpio = nombre.trim()
    val variedadLimpia = variedad.trim().ifBlank { null }
    val codigoLimpio = codigo.trim().ifBlank { null }
    val descripcionLimpia = descripcion.trim().ifBlank { null }
    val photoLimpia = photo?.trim()?.ifBlank { null }

    when {
        nombreLimpio.isBlank() -> {
            return ResultadoOperacionCatalogoAdmin(false, "Escribe el nombre del cultivo")
        }

        cultivosActuales.any {
            it.idCrop != (cultivoBase?.idCrop ?: -1L) &&
                    it.name.equals(nombreLimpio, ignoreCase = true) &&
                    (it.variedad ?: "").equals(variedadLimpia ?: "", ignoreCase = true)
        } -> {
            return ResultadoOperacionCatalogoAdmin(false, "Ese cultivo ya existe")
        }
    }

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

    return ResultadoOperacionCatalogoAdmin(
        exito = true,
        mensaje = if (cultivoBase == null) "Cultivo agregado" else "Cultivo actualizado"
    )
}

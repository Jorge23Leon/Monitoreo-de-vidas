package com.example.myapplication.local.admin.agricola

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalRanchEntity

@Composable
internal fun DialogRanchoGestion(
    guardando: Boolean,
    rancho: LocalRanchEntity?,
    productores: List<LocalAgroUnitEntity>,
    onDismiss: () -> Unit,
    onGuardar: (LocalAgroUnitEntity?, String, String, String, String) -> Unit
) {
    var productorSeleccionado by remember(rancho?.idLocalRanch, productores) {
        mutableStateOf(productores.firstOrNull { it.idLocalAgroUnit == rancho?.idLocalAgroUnit } ?: productores.firstOrNull())
    }
    var nombre by remember(rancho?.idLocalRanch) { mutableStateOf(rancho?.name ?: "") }
    var codigo by remember(rancho?.idLocalRanch) { mutableStateOf(rancho?.code ?: "") }
    var lat by remember(rancho?.idLocalRanch) { mutableStateOf(rancho?.lat?.toString() ?: "") }
    var lon by remember(rancho?.idLocalRanch) { mutableStateOf(rancho?.lon?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            GestionDialogConfirmButton(
                text = if (rancho == null) "Guardar" else "Actualizar",
                guardando = guardando,
                enabled = productorSeleccionado != null && nombre.trim().isNotBlank() && codigo.trim().isNotBlank(),
                onClick = { onGuardar(productorSeleccionado, nombre, codigo, lat, lon) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = { GestionDialogTitle(if (rancho == null) "Agregar rancho" else "Editar rancho") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogInfoGestion("Selecciona el productor al que pertenece este rancho.")
                Spacer(modifier = Modifier.height(12.dp))
                SelectorGestionField(
                    etiqueta = "Productor",
                    valor = productorSeleccionado?.commercial_name ?: "Seleccionar productor",
                    opciones = productores,
                    textoOpcion = { it.commercial_name },
                    habilitado = !guardando && productores.isNotEmpty(),
                    onSeleccionar = { productorSeleccionado = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                GestionTextField(nombre, { nombre = it }, "Nombre del rancho", "Ejemplo: Rancho CIEM", guardando)
                Spacer(modifier = Modifier.height(8.dp))
                GestionTextField(codigo, { codigo = it }, "Código del rancho", "Ejemplo: RANCHO-CIEM", guardando)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GestionDecimalField(lat, { lat = it }, "Latitud", guardando, Modifier.weight(1f), obligatorio = false)
                    GestionDecimalField(lon, { lon = it }, "Longitud", guardando, Modifier.weight(1f), obligatorio = false)
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

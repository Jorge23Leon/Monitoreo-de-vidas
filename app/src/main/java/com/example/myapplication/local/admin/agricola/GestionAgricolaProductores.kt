package com.example.myapplication.local.admin.agricola

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
import com.example.myapplication.local.entities.LocalAgroUnitEntity

@Composable
internal fun DialogProductorGestion(
    guardando: Boolean,
    productor: LocalAgroUnitEntity?,
    onDismiss: () -> Unit,
    onGuardar: (String, String) -> Unit
) {
    var nombre by remember(productor?.idLocalAgroUnit) { mutableStateOf(productor?.commercial_name ?: "") }
    var slug by remember(productor?.idLocalAgroUnit) { mutableStateOf(productor?.slug ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            GestionDialogConfirmButton(
                text = if (productor == null) "Guardar" else "Actualizar",
                guardando = guardando,
                enabled = nombre.trim().isNotBlank(),
                onClick = { onGuardar(nombre, slug) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando) {
                Text("Cancelar", color = Color(0xFF2E7D32))
            }
        },
        title = { GestionDialogTitle(if (productor == null) "Agregar productor" else "Editar productor") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogInfoGestion("El productor quedará asignado a la CIA actual.")
                Spacer(modifier = Modifier.height(12.dp))
                GestionTextField(nombre, { nombre = it }, "Nombre comercial", "Ejemplo: Productor CIEM", guardando)
                Spacer(modifier = Modifier.height(8.dp))
                GestionTextField(slug, { slug = it }, "Código / slug", "Opcional. Si lo dejas vacío se genera solo", guardando)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

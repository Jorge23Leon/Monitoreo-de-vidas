package com.example.myapplication.local.admin.monitoreos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun FormularioMonitoreoAdmin(
    guardando: Boolean,
    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    cultivos: List<LocalCropCatalogEntity>,
    vertices: List<LocalPlotVertexEntity>,
    productorSeleccionado: LocalAgroUnitEntity?,
    ranchoSeleccionado: LocalRanchEntity?,
    parcelaSeleccionada: LocalPlotEntity?,
    cultivoSeleccionado: LocalCropCatalogEntity?,
    ciclo: String,
    fechaInicioMillis: Long?,
    fechaFinMillis: Long?,
    onProductorSeleccionado: (LocalAgroUnitEntity) -> Unit,
    onRanchoSeleccionado: (LocalRanchEntity) -> Unit,
    onParcelaSeleccionada: (LocalPlotEntity) -> Unit,
    onCultivoSeleccionado: (LocalCropCatalogEntity) -> Unit,
    onCicloChange: (String) -> Unit,
    onFechaInicioChange: (Long) -> Unit,
    onFechaFinChange: (Long) -> Unit,
    onGuardarClick: () -> Unit
) {
    val context = LocalContext.current

    AdminMonitorSectionCard(
        numero = "1",
        titulo = "Ubicación del monitoreo",
        subtitulo = "Selecciona productor, rancho y parcela. Los puntos se crearán en campo."
    ) {
        AdminSelectorField(
            etiqueta = "Productor",
            valor = productorSeleccionado?.commercial_name ?: "Seleccionar productor",
            opciones = productores,
            textoOpcion = { it.commercial_name },
            habilitado = !guardando && productores.isNotEmpty(),
            onSeleccionar = onProductorSeleccionado
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminSelectorField(
            etiqueta = "Rancho",
            valor = ranchoSeleccionado?.name ?: "Seleccionar rancho",
            opciones = ranchos,
            textoOpcion = { "${it.name} (${it.code})" },
            habilitado = !guardando && ranchos.isNotEmpty(),
            onSeleccionar = onRanchoSeleccionado
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminSelectorField(
            etiqueta = "Parcela",
            valor = parcelaSeleccionada?.nombreMostrarAdmin() ?: "Seleccionar parcela",
            opciones = parcelas,
            textoOpcion = { parcela -> parcela.nombreMostrarAdmin() },
            habilitado = !guardando && parcelas.isNotEmpty(),
            onSeleccionar = onParcelaSeleccionada
        )

        Spacer(modifier = Modifier.height(12.dp))

        PolygonStatusCard(vertices = vertices)
    }

    Spacer(modifier = Modifier.height(14.dp))

    AdminMonitorSectionCard(
        numero = "2",
        titulo = "Datos del programa",
        subtitulo = "Define cultivo, ciclo y fechas estimadas del monitoreo."
    ) {
        AdminSelectorField(
            etiqueta = "Cultivo",
            valor = cultivoSeleccionado?.let { cultivo ->
                if (cultivo.variedad.isNullOrBlank()) cultivo.name else "${cultivo.name} - ${cultivo.variedad}"
            } ?: "Seleccionar cultivo",
            opciones = cultivos,
            textoOpcion = { cultivo ->
                if (cultivo.variedad.isNullOrBlank()) cultivo.name else "${cultivo.name} - ${cultivo.variedad}"
            },
            habilitado = !guardando && cultivos.isNotEmpty(),
            onSeleccionar = onCultivoSeleccionado
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = ciclo,
            onValueChange = onCicloChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ciclo") },
            placeholder = { Text("Ejemplo: Primavera-Verano 2026") },
            enabled = !guardando,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdminDatePickerField(
                etiqueta = "Inicio",
                valor = formatearFechaAdmin(fechaInicioMillis),
                habilitado = !guardando,
                modifier = Modifier.weight(1f),
                onClick = {
                    mostrarDatePickerNativoAdmin(
                        context = context,
                        fechaActualMillis = fechaInicioMillis,
                        onSeleccionar = onFechaInicioChange
                    )
                }
            )

            AdminDatePickerField(
                etiqueta = "Fin",
                valor = formatearFechaAdmin(fechaFinMillis),
                habilitado = !guardando,
                modifier = Modifier.weight(1f),
                onClick = {
                    mostrarDatePickerNativoAdmin(
                        context = context,
                        fechaActualMillis = fechaFinMillis,
                        onSeleccionar = onFechaFinChange
                    )
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    AdminMonitorSectionCard(
        numero = "3",
        titulo = "Monitoreo libre por punto",
        subtitulo = "El técnico caminará, tocará el mapa y creará cada punto al momento de capturar."
    ) {
        AdminResumenMonitoreo(
            productor = productorSeleccionado,
            rancho = ranchoSeleccionado,
            parcela = parcelaSeleccionada,
            cultivo = cultivoSeleccionado,
            ciclo = ciclo,
            fechaInicio = formatearFechaAdmin(fechaInicioMillis),
            fechaFin = formatearFechaAdmin(fechaFinMillis),
            totalVertices = vertices.size
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onGuardarClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !guardando,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            )
        ) {
            if (guardando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Guardando...")
            } else {
                Text(
                    text = "Crear monitoreo libre",
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

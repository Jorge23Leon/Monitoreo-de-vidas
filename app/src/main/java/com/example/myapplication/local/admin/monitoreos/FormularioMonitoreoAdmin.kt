package com.example.myapplication.local.admin.monitoreos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.local.api.fieldops.FieldTaskApiItem
import com.example.myapplication.local.api.fieldops.MasterProgramApiItem
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity

data class SubprogramaAdminItem(
    val programa: FieldTaskApiItem,
    val rancho: LocalRanchEntity,
    val parcela: LocalPlotEntity,
    val cultivo: LocalCropCatalogEntity,
    val fechaInicioMillis: Long,
    val fechaFinMillis: Long
)

@Composable
fun FormularioMonitoreoAdmin(
    guardando: Boolean,
    cargandoProgramas: Boolean,
    cargandoSubprogramas: Boolean,
    productores: List<LocalAgroUnitEntity>,
    programas: List<MasterProgramApiItem>,
    subprogramas: List<SubprogramaAdminItem>,
    vertices: List<LocalPlotVertexEntity>,
    productorSeleccionado: LocalAgroUnitEntity?,
    programaSeleccionado: MasterProgramApiItem?,
    subprogramaSeleccionado: SubprogramaAdminItem?,
    onProductorSeleccionado: (LocalAgroUnitEntity) -> Unit,
    onProgramaSeleccionado: (MasterProgramApiItem) -> Unit,
    onSubprogramaSeleccionado: (SubprogramaAdminItem) -> Unit,
    onGuardarClick: () -> Unit
) {
    AdminMonitorSectionCard(
        numero = "1",
        titulo = "Programa y subprograma",
        subtitulo = "Selecciona únicamente registros que ya existen en Django."
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
            etiqueta = "Programa",
            valor = when {
                cargandoProgramas -> "Cargando programas..."
                programaSeleccionado != null -> textoProgramaMaestroAdmin(programaSeleccionado)
                productorSeleccionado == null -> "Primero selecciona un productor"
                programas.isEmpty() -> "No hay programas activos"
                else -> "Seleccionar programa"
            },
            opciones = programas,
            textoOpcion = { textoProgramaMaestroAdmin(it) },
            habilitado = !guardando &&
                    !cargandoProgramas &&
                    productorSeleccionado != null &&
                    programas.isNotEmpty(),
            onSeleccionar = onProgramaSeleccionado
        )

        if (
            productorSeleccionado != null &&
            !cargandoProgramas &&
            programas.isEmpty()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Este productor no tiene programas activos disponibles.",
                color = Color(0xFFE65100),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AdminSelectorField(
            etiqueta = "Subprograma",
            valor = when {
                cargandoSubprogramas -> "Cargando subprogramas..."
                subprogramaSeleccionado != null -> textoSubprogramaAdmin(
                    programa = subprogramaSeleccionado.programa,
                    parcela = subprogramaSeleccionado.parcela
                )
                programaSeleccionado == null -> "Primero selecciona un programa"
                subprogramas.isEmpty() -> "No hay subprogramas disponibles"
                else -> "Seleccionar subprograma"
            },
            opciones = subprogramas,
            textoOpcion = {
                textoSubprogramaAdmin(
                    programa = it.programa,
                    parcela = it.parcela
                )
            },
            habilitado = !guardando &&
                    !cargandoSubprogramas &&
                    programaSeleccionado != null &&
                    subprogramas.isNotEmpty(),
            onSeleccionar = onSubprogramaSeleccionado
        )

        if (
            programaSeleccionado != null &&
            !cargandoSubprogramas &&
            subprogramas.isEmpty()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Solo aparecen subprogramas activos, completos y con una parcela " +
                        "disponible para este usuario.",
                color = Color(0xFFE65100),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    AdminMonitorSectionCard(
        numero = "2",
        titulo = "Datos heredados",
        subtitulo = "Rancho, parcela, cultivo, ciclo y fechas vienen del subprograma; no se vuelven a capturar."
    ) {
        val detalle = subprogramaSeleccionado

        AdminReadOnlyField(
            etiqueta = "Rancho del subprograma",
            valor = detalle?.rancho?.let { "${it.name} (${it.code})" }
                ?: "Pendiente de seleccionar subprograma"
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminReadOnlyField(
            etiqueta = "Parcela del subprograma",
            valor = detalle?.parcela?.nombreMostrarAdmin()
                ?: "Pendiente de seleccionar subprograma"
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminReadOnlyField(
            etiqueta = "Cultivo",
            valor = detalle?.cultivo?.let(::textoCultivoAdmin)
                ?: "Pendiente de seleccionar subprograma"
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminReadOnlyField(
            etiqueta = "Ciclo",
            valor = detalle?.programa?.cycle
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Pendiente de seleccionar subprograma"
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminReadOnlyField(
            etiqueta = "Vigencia",
            valor = detalle?.let {
                "${formatearFechaAdmin(it.fechaInicioMillis)} → " +
                        formatearFechaAdmin(it.fechaFinMillis)
            } ?: "Pendiente de seleccionar subprograma"
        )

        Spacer(modifier = Modifier.height(12.dp))

        PolygonStatusCard(vertices = vertices)
    }

    Spacer(modifier = Modifier.height(14.dp))

    AdminMonitorSectionCard(
        numero = "3",
        titulo = "Crear sesión de monitoreo",
        subtitulo = "No se crearán programas ni subprogramas nuevos. Solo se ligará la sesión fitosanitaria."
    ) {
        AdminResumenMonitoreo(
            productor = productorSeleccionado,
            programaMaestro = programaSeleccionado,
            subprograma = subprogramaSeleccionado?.programa,
            rancho = subprogramaSeleccionado?.rancho,
            parcela = subprogramaSeleccionado?.parcela,
            cultivo = subprogramaSeleccionado?.cultivo,
            ciclo = subprogramaSeleccionado?.programa?.cycle.orEmpty(),
            fechaInicio = subprogramaSeleccionado
                ?.let { formatearFechaAdmin(it.fechaInicioMillis) }
                .orEmpty(),
            fechaFin = subprogramaSeleccionado
                ?.let { formatearFechaAdmin(it.fechaFinMillis) }
                .orEmpty(),
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
                Text("Creando sesión...")
            } else {
                Text(
                    text = "Crear sesión de monitoreo",
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

fun textoProgramaMaestroAdmin(programa: MasterProgramApiItem): String {
    val titulo = programa.title
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: programa.code
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        ?: "Programa"

    val codigo = programa.code
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { it != titulo }

    val fechas = listOfNotNull(
        programa.estStartDate?.take(10),
        programa.estFinishDate?.take(10)
    ).joinToString(" → ")

    return listOfNotNull(
        codigo?.let { "$titulo ($it)" } ?: titulo,
        fechas.takeIf { it.isNotBlank() }
    ).joinToString(" • ")
}

fun textoSubprogramaAdmin(
    programa: FieldTaskApiItem,
    parcela: LocalPlotEntity? = null
): String {
    val titulo = programa.title
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "-" }
        ?: programa.voucherCode
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "-" }
        ?: "Subprograma"

    val codigo = programa.voucherCode
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "-" && it != titulo }

    val ubicacion = parcela?.nombreMostrarAdmin()
    val ciclo = programa.cycle?.trim()?.takeIf { it.isNotBlank() }

    return listOfNotNull(
        codigo?.let { "$titulo ($it)" } ?: titulo,
        ubicacion,
        ciclo
    ).joinToString(" • ")
}

private fun textoCultivoAdmin(cultivo: LocalCropCatalogEntity): String {
    return if (cultivo.variedad.isNullOrBlank()) {
        cultivo.name
    } else {
        "${cultivo.name} - ${cultivo.variedad}"
    }
}
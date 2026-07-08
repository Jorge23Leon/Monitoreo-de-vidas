package com.example.myapplication.local.admin.monitoreos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.local.api.fieldops.MasterProgramApiItem
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalCropCatalogEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity

@Composable
fun FormularioMonitoreoAdmin(
    guardando: Boolean,
    cargandoProgramasMaestros: Boolean,
    cargandoCiclos: Boolean,
    productores: List<LocalAgroUnitEntity>,
    programasMaestros: List<MasterProgramApiItem>,
    ciclosDisponibles: List<String>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    cultivos: List<LocalCropCatalogEntity>,
    vertices: List<LocalPlotVertexEntity>,
    productorSeleccionado: LocalAgroUnitEntity?,
    programaMaestroSeleccionado: MasterProgramApiItem?,
    ranchoSeleccionado: LocalRanchEntity?,
    parcelaSeleccionada: LocalPlotEntity?,
    cultivoSeleccionado: LocalCropCatalogEntity?,
    ciclo: String,
    fechaInicioMillis: Long?,
    fechaFinMillis: Long?,
    onProductorSeleccionado: (LocalAgroUnitEntity) -> Unit,
    onProgramaMaestroSeleccionado: (MasterProgramApiItem) -> Unit,
    onRanchoSeleccionado: (LocalRanchEntity) -> Unit,
    onParcelaSeleccionada: (LocalPlotEntity) -> Unit,
    onCultivoSeleccionado: (LocalCropCatalogEntity) -> Unit,
    onCicloSeleccionado: (String) -> Unit,
    onFechaInicioChange: (Long) -> Unit,
    onFechaFinChange: (Long) -> Unit,
    onGuardarClick: () -> Unit
) {
    val context = LocalContext.current

    AdminMonitorSectionCard(
        numero = "1",
        titulo = "Ubicación y programa maestro",
        subtitulo = "El programa remoto debe quedar ligado al productor, al programa maestro y a la parcela."
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
            etiqueta = "Programa maestro",
            valor = when {
                cargandoProgramasMaestros -> "Cargando programas maestros..."
                programaMaestroSeleccionado != null -> textoProgramaMaestroAdmin(programaMaestroSeleccionado)
                productorSeleccionado == null -> "Primero selecciona un productor"
                programasMaestros.isEmpty() -> "No hay programas maestros activos"
                else -> "Seleccionar programa maestro"
            },
            opciones = programasMaestros,
            textoOpcion = { textoProgramaMaestroAdmin(it) },
            habilitado = !guardando &&
                    !cargandoProgramasMaestros &&
                    productorSeleccionado != null &&
                    programasMaestros.isNotEmpty(),
            onSeleccionar = onProgramaMaestroSeleccionado
        )

        if (
            productorSeleccionado != null &&
            !cargandoProgramasMaestros &&
            programasMaestros.isEmpty()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Este productor no tiene programas maestros activos disponibles desde la API.",
                color = Color(0xFFE65100),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AdminSelectorField(
            etiqueta = "Rancho",
            valor = ranchoSeleccionado?.name ?: "Seleccionar rancho",
            opciones = ranchos,
            textoOpcion = { "${it.name} (${it.code})" },
            habilitado = !guardando && productorSeleccionado != null && ranchos.isNotEmpty(),
            onSeleccionar = onRanchoSeleccionado
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminSelectorField(
            etiqueta = "Parcela",
            valor = parcelaSeleccionada?.nombreMostrarAdmin() ?: "Seleccionar parcela",
            opciones = parcelas,
            textoOpcion = { parcela -> parcela.nombreMostrarAdmin() },
            habilitado = !guardando && ranchoSeleccionado != null && parcelas.isNotEmpty(),
            onSeleccionar = onParcelaSeleccionada
        )

        Spacer(modifier = Modifier.height(12.dp))

        PolygonStatusCard(vertices = vertices)
    }

    Spacer(modifier = Modifier.height(14.dp))

    AdminMonitorSectionCard(
        numero = "2",
        titulo = "Datos del programa",
        subtitulo = "Selecciona cultivo, ciclo existente y fechas. Django validará el rango del programa maestro."
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

        /*
         * El ciclo se selecciona desde Django. Así se manda exactamente un
         * formato que el backend ya aceptó, por ejemplo Primavera-2026.
         */
        AdminSelectorField(
            etiqueta = "Ciclo del servidor",
            valor = when {
                cargandoCiclos -> "Cargando ciclos disponibles..."
                ciclo.isNotBlank() -> ciclo
                programaMaestroSeleccionado == null -> "Primero selecciona un programa maestro"
                ciclosDisponibles.isEmpty() -> "No hay ciclos válidos disponibles"
                else -> "Seleccionar ciclo"
            },
            opciones = ciclosDisponibles,
            textoOpcion = { it },
            habilitado = !guardando &&
                    !cargandoCiclos &&
                    programaMaestroSeleccionado != null &&
                    ciclosDisponibles.isNotEmpty(),
            onSeleccionar = onCicloSeleccionado
        )

        if (
            programaMaestroSeleccionado != null &&
            !cargandoCiclos &&
            ciclosDisponibles.isEmpty()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "El ciclo no se escribe manualmente. Selecciona otro programa maestro " +
                        "o crea primero un programa con ciclo válido en Django.",
                color = Color(0xFFE65100),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

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
        titulo = "Crear en servidor",
        subtitulo = "Se creará primero el Programa y después la sesión fitosanitaria. Los checkpoints se capturan en campo."
    ) {
        AdminResumenMonitoreo(
            productor = productorSeleccionado,
            programaMaestro = programaMaestroSeleccionado,
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
                Text("Creando en servidor...")
            } else {
                Text(
                    text = "Crear monitoreo en servidor",
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
        ?: "Programa maestro"

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

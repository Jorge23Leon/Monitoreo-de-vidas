package com.example.myapplication.local

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalProgramEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MonitoreoFiltrosScreen(
    nombreUsuario: String,
    nombreCia: String,

    productores: List<LocalAgroUnitEntity>,
    ranchos: List<LocalRanchEntity>,
    parcelas: List<LocalPlotEntity>,
    ciclos: List<LocalProgramEntity>,

    productorSeleccionado: LocalAgroUnitEntity?,
    ranchoSeleccionado: LocalRanchEntity?,
    parcelaSeleccionada: LocalPlotEntity?,
    cicloSeleccionado: LocalProgramEntity?,

    fechaInicioTexto: String,
    fechaFinTexto: String,

    finalizadosChecked: Boolean,
    vigentesChecked: Boolean,
    canceladosChecked: Boolean,

    onProductorChange: (LocalAgroUnitEntity) -> Unit,
    onRanchoChange: (LocalRanchEntity) -> Unit,
    onParcelaChange: (LocalPlotEntity) -> Unit,
    onCicloChange: (LocalProgramEntity) -> Unit,

    onFechaInicioChange: (String) -> Unit,
    onFechaFinChange: (String) -> Unit,

    onFinalizadosChange: (Boolean) -> Unit,
    onVigentesChange: (Boolean) -> Unit,
    onCanceladosChange: (Boolean) -> Unit,

    onBuscarClick: () -> Unit,
    onSaltarFiltrosClick: () -> Unit,
    onBackClick: () -> Unit,
    onCerrarSesionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            HeaderMonitoreo(
                nombreUsuario = nombreUsuario,
                onBackClick = onBackClick,
                onSaltarFiltrosClick = onSaltarFiltrosClick
            )

            Text(
                text = "CIA seleccionada: $nombreCia",
                fontSize = 12.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = "Filtros de monitoreo",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SelectorFiltro(
                            label = "Productor",
                            selected = productorSeleccionado,
                            items = productores,
                            itemText = { it.commercial_name },
                            enabled = productores.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            onSelected = onProductorChange
                        )

                        SelectorFiltro(
                            label = "Rancho",
                            selected = ranchoSeleccionado,
                            items = ranchos,
                            itemText = { it.name },
                            enabled = ranchos.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            onSelected = onRanchoChange
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SelectorFiltro(
                            label = "Parcela",
                            selected = parcelaSeleccionada,
                            items = parcelas,
                            itemText = { it.code },
                            enabled = parcelas.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            onSelected = onParcelaChange
                        )

                        SelectorFiltro(
                            label = "Ciclo",
                            selected = cicloSeleccionado,
                            items = ciclos,
                            itemText = { it.cycle },
                            enabled = ciclos.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            onSelected = onCicloChange
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CampoFecha(
                            label = "Fecha de inicio",
                            value = fechaInicioTexto,
                            onValueChange = onFechaInicioChange,
                            modifier = Modifier.weight(1f)
                        )

                        CampoFecha(
                            label = "Fecha de fin",
                            value = fechaFinTexto,
                            onValueChange = onFechaFinChange,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Estado del monitoreo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EstadoCuadro(
                            titulo = "Pendiente",
                            subtitulo = "En proceso",
                            checked = vigentesChecked,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onFinalizadosChange(false)
                                onVigentesChange(true)
                                onCanceladosChange(false)
                            }
                        )


                        EstadoCuadro(
                            titulo = "Completado",
                            subtitulo = "Finalizado",
                            checked = finalizadosChecked,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onFinalizadosChange(true)
                                onVigentesChange(false)
                                onCanceladosChange(false)
                            }
                        )

                        EstadoCuadro(
                            titulo = "Cancelado",
                            subtitulo = "No activo",
                            checked = canceladosChecked,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onFinalizadosChange(false)
                                onVigentesChange(false)
                                onCanceladosChange(true)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBuscarClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7ED957)),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(
                    text = "Buscar monitoreos",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onCerrarSesionClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A3A3)),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier
                    .width(170.dp)
                    .height(44.dp)
            ) {
                Text(
                    text = "Cerrar Sesión",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeaderMonitoreo(
    nombreUsuario: String,
    onBackClick: () -> Unit,
    onSaltarFiltrosClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52AFC4)),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("↩", color = Color.White, fontSize = 22.sp)
        }

        OutlinedButton(
            onClick = onSaltarFiltrosClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(38.dp),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Saltar filtros",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(22.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🟩", fontSize = 34.sp)

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "TIERRA",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )

                    Text(
                        text = "INTELIGENTE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Bienvenido $nombreUsuario",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF666666)
            )
        }
    }

    Spacer(modifier = Modifier.height(125.dp))
}

@Composable
private fun <T> SelectorFiltro(
    label: String,
    selected: T?,
    items: List<T>,
    itemText: (T) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Black,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Box {
            OutlinedButton(
                onClick = {
                    if (enabled) expanded = true
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = selected?.let(itemText) ?: "",
                    modifier = Modifier.weight(1f),
                    color = Color.Black,
                    maxLines = 1
                )

                Text(
                    text = "➜",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (items.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin datos") },
                        onClick = { expanded = false }
                    )
                } else {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(itemText(item)) },
                            onClick = {
                                onSelected(item)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoFecha(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val formatoFecha = remember {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply {
            isLenient = false
        }
    }

    fun abrirCalendario() {
        val calendario = Calendar.getInstance()

        if (value.isNotBlank()) {
            try {
                val fechaGuardada = formatoFecha.parse(value)
                if (fechaGuardada != null) {
                    calendario.time = fechaGuardada
                }
            } catch (_: Exception) {
                // Si la fecha guardada no se puede leer, abre con la fecha actual.
            }
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val fechaSeleccionada = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                onValueChange(formatoFecha.format(fechaSeleccionada.time))
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Black,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        OutlinedButton(
            onClick = {
                abrirCalendario()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = value.ifBlank { "Seleccionar fecha" },
                color = if (value.isBlank()) Color.Gray else Color.Black,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )

            Text(
                text = "📅",
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun EstadoCuadro(
    titulo: String,
    subtitulo: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (checked) {
                Color(0xFFE9E0FA)
            } else {
                Color(0xFFF6EEEE)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = titulo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitulo,
                fontSize = 9.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = if (checked) "✓" else "",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6A4FB3)
            )
        }
    }
}
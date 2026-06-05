package com.example.myapplication.local.monitoreo.filtros

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalProgramEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun <T> SelectorFiltro(
    label: String,
    selected: T?,
    items: List<T>,
    itemText: (T) -> String,
    emptyText: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
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
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = selected?.let(itemText) ?: emptyText,
                    modifier = Modifier.weight(1f),
                    color = if (selected == null) Color.Gray else Color.Black,
                    maxLines = 1,
                    fontSize = 12.sp,
                    fontWeight = if (selected == null) FontWeight.Normal else FontWeight.Bold
                )

                Text(
                    text = "⌄",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                if (items.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text("Sin datos")
                        },
                        onClick = {
                            expanded = false
                        }
                    )
                } else {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(itemText(item))
                            },
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
internal fun <T> SelectorFiltroConTodos(
    label: String,
    selected: T?,
    allText: String,
    items: List<T>,
    itemText: (T) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
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
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = selected?.let(itemText) ?: allText,
                    modifier = Modifier.weight(1f),
                    color = if (enabled) Color.Black else Color.Gray,
                    maxLines = 1,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "⌄",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(allText)
                    },
                    onClick = {
                        onSelected(null)
                        expanded = false
                    }
                )

                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(itemText(item))
                        },
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

@Composable
internal fun SelectorFiltroOpcionalPrograma(
    icono: String,
    label: String,
    selected: LocalProgramEntity?,
    items: List<LocalProgramEntity>,
    modifier: Modifier = Modifier,
    onSelected: (LocalProgramEntity?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = {
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(
                text = icono,
                fontSize = 17.sp
            )

            Spacer(modifier = Modifier.width(7.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    maxLines = 1
                )

                Text(
                    text = selected?.let { textoCicloFiltro(it.cycle) } ?: "Todos",
                    fontSize = 12.sp,
                    color = Color.Black,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "⌄",
                fontSize = 13.sp,
                color = Color.Black
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            DropdownMenuItem(
                text = {
                    Text("Todos")
                },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )

            items.forEach { programa ->
                DropdownMenuItem(
                    text = {
                        Text(textoCicloFiltro(programa.cycle))
                    },
                    onClick = {
                        onSelected(programa)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun SelectorFechaFiltro(
    icono: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current

    val formato = remember {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply {
            isLenient = false
        }
    }

    fun abrirCalendario() {
        val calendar = Calendar.getInstance()

        parseFechaInicioFiltro(value)?.let { fecha ->
            calendar.timeInMillis = fecha
        }

        DatePickerDialog(
            context,
            { _, year, month, day ->
                val fecha = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                onValueChange(formato.format(fecha.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    OutlinedButton(
        onClick = {
            abrirCalendario()
        },
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(
            text = icono,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.width(7.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.DarkGray,
                maxLines = 1
            )

            Text(
                text = value.ifBlank { "Fecha" },
                color = if (value.isBlank()) Color.Gray else Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Text(
            text = "⌄",
            fontSize = 13.sp,
            color = Color.Black
        )
    }
}

@Composable
internal fun SelectorEstadoFiltro(
    icono: String,
    label: String,
    selected: String,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val estados = listOf(
        "Todos",
        "Pendiente",
        "En proceso",
        "Completado",
        "Cancelado"
    )

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = {
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(
                text = icono,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.width(7.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    maxLines = 1
                )

                Text(
                    text = selected,
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Text(
                text = "⌄",
                color = Color.Black,
                fontSize = 13.sp
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            estados.forEach { estado ->
                DropdownMenuItem(
                    text = {
                        Text(estado)
                    },
                    onClick = {
                        onSelected(estado)
                        expanded = false
                    }
                )
            }
        }
    }
}
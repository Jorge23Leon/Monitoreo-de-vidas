package com.example.myapplication.local.ndvi.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val NdviFilterGreen = Color(0xFF0B6B20)
private val NdviFilterGreenDark = Color(0xFF173B1A)
private val NdviFilterGreenSoft = Color(0xFFE8F5E9)
private val NdviFilterSecondary = Color(0xFF667064)

@Composable
internal fun NdviSessionFiltersPanel(
    state: NdviUiState,
    onProducerChange: (String?) -> Unit,
    onRanchChange: (String?) -> Unit,
    onPlotChange: (String?) -> Unit,
    onStartDateChange: (Long?) -> Unit,
    onEndDateChange: (Long?) -> Unit,
    onClear: () -> Unit,
    onToggle: () -> Unit
) {

    val filtrosActivos = listOf(
        state.filters.producerId != null,
        state.filters.ranchId != null,
        state.filters.plotId != null,
        state.filters.startDateMillis != null,
        state.filters.endDateMillis != null
    ).count { it }

    val productor = state.filterOptions.producers
        .firstOrNull {
            it.id == state.filters.producerId
        }
        ?.label

    val rancho = state.filterOptions.ranches
        .firstOrNull {
            it.id == state.filters.ranchId
        }
        ?.label

    val parcela = state.filterOptions.plots
        .firstOrNull {
            it.id == state.filters.plotId
        }
        ?.label

    val resumen = buildList {

        productor
            ?.takeIf(String::isNotBlank)
            ?.let {
                add("Productor: $it")
            }

        rancho
            ?.takeIf(String::isNotBlank)
            ?.let {
                add("Rancho: $it")
            }

        parcela
            ?.takeIf(String::isNotBlank)
            ?.let {
                add("Parcela: $it")
            }

        state.filters.startDateMillis?.let {
            add(
                "Desde: ${formatNdviFilterDate(it)}"
            )
        }

        state.filters.endDateMillis?.let {
            add(
                "Hasta: ${formatNdviFilterDate(it)}"
            )
        }

    }.joinToString(" · ")
        .ifBlank {
            "Todas las sesiones NDVI"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 11.dp
                )
        ) {

            /*
             * CABECERA DEL PANEL
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = onToggle
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            NdviFilterGreenSoft
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "≡",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = NdviFilterGreen
                    )
                }

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Filtros",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NdviFilterGreenDark
                        )

                        if (filtrosActivos > 0) {

                            Spacer(
                                modifier = Modifier.width(7.dp)
                            )

                            Text(
                                text = "$filtrosActivos aplicados",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NdviFilterGreen,
                                modifier = Modifier
                                    .background(
                                        color = NdviFilterGreenSoft,
                                        shape = RoundedCornerShape(
                                            20.dp
                                        )
                                    )
                                    .padding(
                                        horizontal = 8.dp,
                                        vertical = 3.dp
                                    )
                            )
                        }
                    }

                    Text(
                        text = resumen,
                        fontSize = 11.sp,
                        color = NdviFilterSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = onToggle
                ) {

                    Text(
                        text =
                        if (
                            state.sessionFiltersExpanded
                        ) {
                            "Cerrar"
                        } else {
                            "Editar"
                        },
                        color = NdviFilterGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text =
                    if (
                        state.sessionFiltersExpanded
                    ) {
                        "⌃"
                    } else {
                        "⌄"
                    },
                    color = NdviFilterGreenDark,
                    fontSize = 17.sp
                )
            }

            /*
             * FILTROS EXPANDIDOS
             */
            if (state.sessionFiltersExpanded) {

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                /*
                 * PRODUCTOR
                 */
                NdviDropdownFilter(
                    label = "Productor",
                    selectedId =
                    state.filters.producerId,
                    options =
                    state.filterOptions.producers,
                    allText =
                    "Todos los productores",
                    enabled =
                    state.filterOptions.producers
                        .isNotEmpty(),
                    modifier =
                    Modifier.fillMaxWidth(),
                    onSelected =
                    onProducerChange
                )

                Spacer(
                    modifier = Modifier.height(9.dp)
                )

                /*
                 * RANCHO / PARCELA
                 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                    Arrangement.spacedBy(9.dp)
                ) {

                    NdviDropdownFilter(
                        label = "Rancho",
                        selectedId =
                        state.filters.ranchId,
                        options =
                        state.filterOptions.ranches,
                        allText =
                        if (
                            state.filters.producerId == null
                        ) {
                            "Selecciona productor"
                        } else {
                            "Todos"
                        },
                        enabled =
                        state.filters.producerId != null &&
                                state.filterOptions.ranches
                                    .isNotEmpty(),
                        modifier =
                        Modifier.weight(1f),
                        onSelected =
                        onRanchChange
                    )

                    NdviDropdownFilter(
                        label = "Parcela",
                        selectedId =
                        state.filters.plotId,
                        options =
                        state.filterOptions.plots,
                        allText =
                        if (
                            state.filters.ranchId == null
                        ) {
                            "Selecciona rancho"
                        } else {
                            "Todas"
                        },
                        enabled =
                        state.filters.ranchId != null &&
                                state.filterOptions.plots
                                    .isNotEmpty(),
                        modifier =
                        Modifier.weight(1f),
                        onSelected =
                        onPlotChange
                    )
                }

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                /*
                 * FECHAS
                 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                    Arrangement.spacedBy(9.dp)
                ) {

                    NdviDateFilter(
                        label = "Inicio",
                        value =
                        state.filters.startDateMillis,
                        modifier =
                        Modifier.weight(1f),
                        onSelected =
                        onStartDateChange
                    )

                    NdviDateFilter(
                        label = "Fin",
                        value =
                        state.filters.endDateMillis,
                        modifier =
                        Modifier.weight(1f),
                        onSelected =
                        onEndDateChange
                    )
                }

                /*
                 * LIMPIAR FILTROS
                 */
                if (filtrosActivos > 0) {

                    TextButton(
                        onClick = onClear,
                        modifier =
                        Modifier.align(
                            Alignment.End
                        )
                    ) {

                        Text(
                            text = "↻ Limpiar filtros",
                            color = NdviFilterGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NdviDropdownFilter(
    label: String,
    selectedId: String?,
    options: List<NdviFilterOption>,
    allText: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (String?) -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    val selectedLabel =
        options.firstOrNull {
            it.id == selectedId
        }?.label

    Column(
        modifier = modifier
    ) {

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            modifier = Modifier.padding(
                start = 4.dp,
                bottom = 4.dp
            )
        )

        Box {

            OutlinedButton(
                onClick = {

                    if (enabled) {
                        expanded = true
                    }
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(
                    8.dp
                ),
                contentPadding = PaddingValues(
                    horizontal = 8.dp
                )
            ) {

                Text(
                    text =
                    selectedLabel ?: allText,
                    modifier =
                    Modifier.weight(1f),
                    color =
                    if (enabled) {
                        Color.Black
                    } else {
                        Color.Gray
                    },
                    maxLines = 1,
                    overflow =
                    TextOverflow.Ellipsis,
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

                options.forEach { option ->

                    DropdownMenuItem(
                        text = {
                            Text(option.label)
                        },
                        onClick = {

                            onSelected(option.id)

                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NdviDateFilter(
    label: String,
    value: Long?,
    modifier: Modifier = Modifier,
    onSelected: (Long?) -> Unit
) {

    val context = LocalContext.current

    val text =
        value
            ?.let(
                ::formatNdviFilterDate
            )
            ?: "Fecha"

    OutlinedButton(
        onClick = {

            val calendar =
                Calendar.getInstance().apply {

                    if (value != null) {
                        timeInMillis = value
                    }
                }

            DatePickerDialog(
                context,
                { _, year, month, day ->

                    val selected =
                        Calendar.getInstance()
                            .apply {

                                set(
                                    year,
                                    month,
                                    day,
                                    0,
                                    0,
                                    0
                                )

                                set(
                                    Calendar.MILLISECOND,
                                    0
                                )

                            }.timeInMillis

                    onSelected(selected)
                },
                calendar.get(
                    Calendar.YEAR
                ),
                calendar.get(
                    Calendar.MONTH
                ),
                calendar.get(
                    Calendar.DAY_OF_MONTH
                )
            ).show()
        },
        modifier =
        modifier.height(54.dp),
        shape =
        RoundedCornerShape(12.dp),
        contentPadding =
        PaddingValues(
            horizontal = 10.dp
        )
    ) {

        Text(
            text = "▣",
            fontSize = 16.sp
        )

        Spacer(
            modifier = Modifier.width(7.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.DarkGray,
                maxLines = 1
            )

            Text(
                text = text,
                color =
                if (value == null) {
                    Color.Gray
                } else {
                    Color.Black
                },
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

private fun formatNdviFilterDate(
    value: Long
): String {

    return SimpleDateFormat(
        "dd-MM-yyyy",
        Locale.getDefault()
    ).format(
        Date(value)
    )
}
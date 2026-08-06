package com.example.myapplication.local.aspersion.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
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

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun AspersionSessionFiltersPanel(
    state: AspersionUiState,
    onSearchChange: (String) -> Unit,
    onProducerChange: (String?) -> Unit,
    onRanchChange: (String?) -> Unit,
    onPlotChange: (String?) -> Unit,
    onProgramChange: (String?) -> Unit,
    onStartDateChange: (Long?) -> Unit,
    onEndDateChange: (Long?) -> Unit,
    onStatusChange: (String?) -> Unit,
    onClear: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Filtros de aspersión",
                        color = AspersionGreenDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Productor → rancho → parcela",
                        color = Color(0xFF68736B),
                        fontSize = 10.sp
                    )
                }
                OutlinedButton(
                    onClick = onToggle,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (state.sessionFiltersExpanded) "Contraer" else "Mostrar",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (state.sessionFiltersExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                AspersionDropdownFilter(
                    label = "Productor",
                    selectedId = state.filters.producerId,
                    options = state.filterOptions.producers,
                    enabled = state.filterOptions.producers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    onSelected = onProducerChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspersionDropdownFilter(
                        label = "Rancho",
                        selectedId = state.filters.ranchId,
                        options = state.filterOptions.ranches,
                        enabled = state.filters.producerId != null &&
                                state.filterOptions.ranches.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onSelected = onRanchChange
                    )
                    AspersionDropdownFilter(
                        label = "Parcela",
                        selectedId = state.filters.plotId,
                        options = state.filterOptions.plots,
                        enabled = state.filters.ranchId != null &&
                                state.filterOptions.plots.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onSelected = onPlotChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspersionDateFilter(
                        label = "Fecha inicio",
                        value = state.filters.startDateMillis,
                        modifier = Modifier.weight(1f),
                        onSelected = onStartDateChange
                    )
                    AspersionDateFilter(
                        label = "Fecha fin",
                        value = state.filters.endDateMillis,
                        modifier = Modifier.weight(1f),
                        onSelected = onEndDateChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onClear,
                    enabled = state.filters.isActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6ECE7),
                        contentColor = AspersionText
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Limpiar filtros", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AspersionDropdownFilter(
    label: String,
    selectedId: String?,
    options: List<AspersionFilterOption>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { option ->
        option.id == selectedId
    }?.label

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 9.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = Color(0xFF6A746D)
                )
                Text(
                    text = selectedLabel ?: "Todos",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("⌄", fontWeight = FontWeight.Bold)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AspersionDateFilter(
    label: String,
    value: Long?,
    modifier: Modifier = Modifier,
    onSelected: (Long?) -> Unit
) {
    val context = LocalContext.current
    val text = remember(value) {
        value?.let { millis ->
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(millis))
        } ?: "Todas"
    }

    OutlinedButton(
        onClick = {
            val calendar = Calendar.getInstance().apply {
                if (value != null) timeInMillis = value
            }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val selected = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onSelected(selected)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 9.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 9.sp, color = Color(0xFF6A746D))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        if (value != null) {
            Text(
                text = "×",
                modifier = Modifier.padding(start = 6.dp),
                fontWeight = FontWeight.Black
            )
        } else {
            Text("⌄", fontWeight = FontWeight.Bold)
        }
    }
}
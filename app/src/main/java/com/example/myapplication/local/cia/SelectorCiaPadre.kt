package com.example.myapplication.local.cia

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.R
import com.example.myapplication.local.entities.LocalParentCiaEntity

@Composable
fun SelectorCiaPadre(
    parentCias: List<LocalParentCiaEntity>,
    parentCiaSeleccionada: LocalParentCiaEntity?,
    onParentCiaChange: (LocalParentCiaEntity) -> Unit
) {
    var expandedParentCia by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFDDE8D6),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFCF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "1. Selecciona tu organización (CIA padre)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF253A25)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (parentCias.isNotEmpty()) {
                            expandedParentCia = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cia_padre),
                        contentDescription = "CIA padre",
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = parentCiaSeleccionada?.name ?: "Selecciona una CIA padre",
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )

                    Text(
                        text = "⌄",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                DropdownMenu(
                    expanded = expandedParentCia,
                    onDismissRequest = { expandedParentCia = false }
                ) {
                    if (parentCias.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No hay CIAS padre asignadas") },
                            onClick = { expandedParentCia = false }
                        )
                    } else {
                        parentCias.forEach { parentCia ->
                            DropdownMenuItem(
                                text = { Text(parentCia.name) },
                                onClick = {
                                    onParentCiaChange(parentCia)
                                    expandedParentCia = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
package com.example.myapplication.local.cia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity

@Composable
fun SeleccionCiaScreen(
    nombreUsuario: String,
    rolUsuario: String = "",

    parentCias: List<LocalParentCiaEntity>,
    parentCiaSeleccionada: LocalParentCiaEntity?,
    onParentCiaChange: (LocalParentCiaEntity) -> Unit,

    cias: List<LocalCiaEntity>,
    ciaSeleccionada: LocalCiaEntity?,

    seleccionarPreferente: Boolean,
    onPreferenteChange: (Boolean) -> Unit,

    onCiaChange: (LocalCiaEntity) -> Unit,
    onSeleccionarClick: () -> Unit,
    onCerrarSesionClick: () -> Unit,
    onPerfilClick: () -> Unit = {},
    onMonitoreosClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    var expandedParentCia by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Seleccionar organización",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F331F),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Primero elige la CIA padre y después selecciona una CIA hija.",
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Color(0xFFDDE8D6),
                            shape = RoundedCornerShape(18.dp)
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFAFCF8)
                    ),
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

                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (parentCias.isNotEmpty()) {
                                        expandedParentCia = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = "🏢",
                                    fontSize = 20.sp
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = parentCiaSeleccionada?.name
                                        ?: "Selecciona una CIA padre",
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

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Color(0xFFDDE8D6),
                            shape = RoundedCornerShape(18.dp)
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFAFCF8)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "2. Selecciona una CIA hija",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF253A25)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (parentCiaSeleccionada == null) {
                            CajaMensajeSeleccion(
                                texto = "Selecciona una CIA padre para mostrar sus CIAS hijas."
                            )
                        } else if (cias.isEmpty()) {
                            CajaMensajeSeleccion(
                                texto = "Esta CIA padre no tiene CIAS hijas asignadas."
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFEAF5E4))
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🏢",
                                            fontSize = 22.sp
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Text(
                                            text = parentCiaSeleccionada.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E4A27),
                                            modifier = Modifier.weight(1f)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .border(
                                                    width = 2.dp,
                                                    color = Color(0xFF4A8F2A),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "−",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4A8F2A)
                                            )
                                        }
                                    }

                                    cias.forEachIndexed { index, cia ->
                                        CiaHijaTreeItem(
                                            cia = cia,
                                            seleccionada = ciaSeleccionada?.idLocalCia == cia.idLocalCia,
                                            esUltima = index == cias.lastIndex,
                                            onClick = {
                                                onCiaChange(cia)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEFF8E8)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ⓘ",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4A8F2A)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = if (ciaSeleccionada == null) {
                                "Selecciona una CIA hija del árbol para continuar."
                            } else {
                                "CIA hija seleccionada: ${ciaSeleccionada.nombre}"
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF3F5F35),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFAFAFA)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⭐",
                            fontSize = 22.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Checkbox(
                            checked = seleccionarPreferente,
                            onCheckedChange = onPreferenteChange,
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "Seleccionar CIA hija preferente",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            Text(
                                text = "La app recordará esta CIA hija para este usuario.",
                                fontSize = 11.sp,
                                color = Color(0xFF777777)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onCerrarSesionClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5A3A3)
                        ),
                        shape = RoundedCornerShape(30.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Salir",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Button(
                        onClick = onSeleccionarClick,
                        enabled = parentCiaSeleccionada != null && ciaSeleccionada != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2F7D20),
                            disabledContainerColor = Color(0xFFE0E0E0)
                        ),
                        shape = RoundedCornerShape(30.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Continuar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun CiaHijaTreeItem(
    cia: LocalCiaEntity,
    seleccionada: Boolean,
    esUltima: Boolean,
    onClick: () -> Unit
) {
    val fondo = if (seleccionada) {
        Color(0xFFEAF5E4)
    } else {
        Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (esUltima) 21.dp else 42.dp)
                    .background(Color(0xFFD2DCCB))
                    .align(Alignment.TopCenter)
            )

            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(1.dp)
                    .background(Color(0xFFD2DCCB))
                    .align(Alignment.CenterEnd)
            )
        }

        Text(
            text = "🌱",
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = cia.nombre,
                fontSize = 15.sp,
                fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.SemiBold,
                color = Color(0xFF263D24)
            )

            Text(
                text = cia.description ?: "CIA hija disponible",
                fontSize = 11.sp,
                color = Color(0xFF777777),
                maxLines = 1
            )
        }

        RadioVisualSeleccionado(seleccionada = seleccionada)
    }
}

@Composable
private fun RadioVisualSeleccionado(
    seleccionada: Boolean
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(
                width = 2.dp,
                color = if (seleccionada) Color(0xFF2F7D20) else Color(0xFF6EA560),
                shape = CircleShape
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (seleccionada) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(0xFF2F7D20), CircleShape)
            )
        }
    }
}

@Composable
private fun CajaMensajeSeleccion(
    texto: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF4F8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = texto,
            modifier = Modifier.padding(14.dp),
            fontSize = 13.sp,
            color = Color(0xFF4E5D45),
            textAlign = TextAlign.Center
        )
    }
}
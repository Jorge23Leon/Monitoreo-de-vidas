package com.example.myapplication.local

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalCiaEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues

@Composable
fun SeleccionCiaScreen(
    nombreUsuario: String,
    cias: List<LocalCiaEntity>,
    ciaSeleccionada: LocalCiaEntity?,
    seleccionarPreferente: Boolean,
    onPreferenteChange: (Boolean) -> Unit,
    onCiaChange: (LocalCiaEntity) -> Unit,
    onSeleccionarClick: () -> Unit,
    onCerrarSesionClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val fechaActual = remember {
        SimpleDateFormat(
            "dd 'de' MMMM 'de' yyyy",
            Locale("es", "MX")
        ).format(Date())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            EncabezadoCompacto(
                nombreUsuario = nombreUsuario,
                fechaActual = fechaActual
            )
            Spacer(modifier = Modifier.height(80.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = Color(0xFF7CB342),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Selecciona la CIA con la que deseas trabajar.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4E5D45),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF2F8EF)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 3.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "🚜",
                                        fontSize = 34.sp
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = "Monitoreo agrícola",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E4A27)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Gestión de empresas, productores, ranchos, parcelas y monitoreos fitosanitarios.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF5F6F5A),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFE3F2FD), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🏢",
                                    fontSize = 24.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "CIA:",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = ciaSeleccionada?.nombre ?: "Seleccionar",
                                        fontSize = 13.sp,
                                        color = Color.Black,
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
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    if (cias.isEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                Text("No hay CIAS asignadas")
                                            },
                                            onClick = {
                                                expanded = false
                                            }
                                        )
                                    } else {
                                        cias.forEach { cia ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(cia.nombre)
                                                },
                                                onClick = {
                                                    onCiaChange(cia)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
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
                                        text = "Seleccionar preferente",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )

                                    Text(
                                        text = "La app recordará esta CIA para este usuario.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF777777)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))

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
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = "Salir",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Button(
                                onClick = onSeleccionarClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF81E873)
                                ),
                                shape = RoundedCornerShape(30.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = "Seleccionar",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}
@Composable
fun EncabezadoCompacto(
    nombreUsuario: String,
    fechaActual: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color.White)
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp)
    ) {
        // Logo + Tierra Inteligente un poquito más abajo
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 26.dp)
                    .background(
                        color = Color(0xFF7CB342),
                        shape = RoundedCornerShape(3.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🌱",
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "TIERRA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    lineHeight = 17.sp
                )

                Text(
                    text = "INTELIGENTE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    lineHeight = 9.sp
                )
            }
        }

        // Bienvenido + fecha centrado y un poco más arriba
        Text(
            text = "Bienvenido $nombreUsuario - $fechaActual",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6D6D6D),
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        // Menú hamburguesa un poco abajo
        Text(
            text = "☰",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 13.dp),
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}
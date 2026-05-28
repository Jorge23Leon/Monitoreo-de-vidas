package com.example.myapplication.local.common

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EncabezadoApp(
    nombreUsuario: String,
    rolUsuario: String = "",
    onPerfilClick: (() -> Unit)? = null,
    onMonitoreosClick: (() -> Unit)? = null,
    onAdminClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    var menuAbierto by remember {
        mutableStateOf(false)
    }

    val rolNormalizado = remember(rolUsuario) {
        normalizarRolHeader(rolUsuario)
    }

    val puedeVerPanelTrabajo = remember(rolUsuario) {
        rolNormalizado in listOf(
            "admin",
            "gerente",
            "supervisor"
        )
    }

    val puedeVerMonitoreos = remember(rolUsuario) {
        rolNormalizado in listOf(
            "admin",
            "gerente",
            "supervisor",
            "tecnico",
            "invitado"
        )
    }

    val fechaActual = remember {
        SimpleDateFormat(
            "dd 'de' MMMM 'de' yyyy",
            Locale("es", "MX")
        ).format(Date())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color.White)
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 4.dp),
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

        Text(
            text = "Bienvenido $nombreUsuario - $fechaActual",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 70.dp, end = 45.dp, bottom = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6D6D6D),
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp)
        ) {
            Text(
                text = "☰",
                modifier = Modifier
                    .clickable {
                        menuAbierto = true
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            DropdownMenu(
                expanded = menuAbierto,
                onDismissRequest = {
                    menuAbierto = false
                },
                modifier = Modifier.background(Color.White)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "👤 Perfil del usuario",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    },
                    onClick = {
                        menuAbierto = false

                        if (onPerfilClick != null) {
                            onPerfilClick()
                        } else {
                            Toast.makeText(
                                context,
                                "Perfil del usuario",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                if (puedeVerMonitoreos) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "📋 Monitoreos",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                        },
                        onClick = {
                            menuAbierto = false

                            if (onMonitoreosClick != null) {
                                onMonitoreosClick()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Apartado de monitoreos",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }

                if (puedeVerPanelTrabajo) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🛠 Panel de trabajo",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                        },
                        onClick = {
                            menuAbierto = false

                            if (onAdminClick != null) {
                                onAdminClick()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Panel de trabajo",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun normalizarRolHeader(rol: String): String {
    val limpio = rol
        .trim()
        .lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(".", "")
        .replace("_", " ")
        .replace(Regex("\\s+"), " ")

    return when (limpio) {
        "super admin", "admin", "administrador" -> "admin"

        "gerente" -> "gerente"

        "ingy supervision",
        "ing y supervision",
        "supervisor" -> "supervisor"

        "tecnico",
        "tecnicos",
        "técnico",
        "técnicos" -> "tecnico"

        "invitado" -> "invitado"

        else -> limpio
    }
}

private fun esSuperAdminHeader(rol: String): Boolean {
    return normalizarRolHeader(rol) == "admin"
}
package com.example.myapplication.local.common

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    onCambiarCiaClick: (() -> Unit)? = null,
    onMonitoreosClick: (() -> Unit)? = null,
    onAdminClick: (() -> Unit)? = null,
    onCerrarSesionClick: () -> Unit
)  {
    val context = LocalContext.current
    val logoAgroindustryId = remember(context) {
        context.resources.getIdentifier(
            "logo_agroindustry",
            "drawable",
            context.packageName
        )
    }


    var menuAbierto by remember {
        mutableStateOf(false)
    }
    var mostrarDialogCerrarSesion by remember {
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
    val puedeCambiarCia = onCambiarCiaClick != null && rolNormalizado in listOf(
        "admin",
        "gerente",
        "supervisor"
    )



    val fechaActual = remember {
        SimpleDateFormat(
            "dd 'de' MMMM 'de' yyyy",
            Locale("es", "MX")
        ).format(Date())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(Color.White)
            .padding(
                start = 0.dp,
                end = 10.dp,
                top = 2.dp,
                bottom = 2.dp
            )
    ) {

        if (logoAgroindustryId != 0) {
            Image(
                painter = painterResource(id = logoAgroindustryId),
                contentDescription = "GPA Agroindustry",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(145.dp)
                    .height(55.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "GPA Agroindustry",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 2.dp, top = 10.dp),
                color = Color(0xFF6D6D6D),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }


        Text(
            text = "Bienvenido $nombreUsuario - $fechaActual",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 48.dp, bottom = 2.dp),
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



                if (puedeCambiarCia) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🏢 Cambiar de CIA",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                        },
                        onClick = {
                            menuAbierto = false
                            onCambiarCiaClick?.invoke()
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

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "🚪 Cerrar sesión",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB3261E)
                        )
                    },
                    onClick = {
                        menuAbierto = false
                        mostrarDialogCerrarSesion = true
                    }
                )
            }
        }
    }

    if (mostrarDialogCerrarSesion) {
        AlertDialog(
            onDismissRequest = {
                mostrarDialogCerrarSesion = false
            },
            title = {
                Text(
                    text = "Cerrar sesión",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que quieres cerrar sesión?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarDialogCerrarSesion = false
                        onCerrarSesionClick()
                    }
                ) {
                    Text(
                        text = "Sí, cerrar sesión",
                        color = Color(0xFFB3261E),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mostrarDialogCerrarSesion = false
                    }
                ) {
                    Text(
                        text = "Cancelar",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
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
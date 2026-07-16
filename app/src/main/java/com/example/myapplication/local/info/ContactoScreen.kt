package com.example.myapplication.local.info

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.R

private val FondoPantalla = Color(0xFFF4F8F1)
private val VerdeOscuro = Color(0xFF184F22)
private val VerdeMedio = Color(0xFF2E7D32)
private val VerdeClaro = Color(0xFF77C36D)
private val TextoPrincipal = Color(0xFF1B2C1E)
private val TextoSecundario = Color(0xFF5E6D61)

/**
 * Contacto y soporte.
 *
 * CAMBIO:
 * Las descripciones y textos secundarios vuelven a peso normal.
 * Solo los encabezados se mantienen en negritas para que la pantalla
 * conserve jerarquía visual.
 *
 * Requiere:
 * - res/drawable/gpa.webp
 * - res/drawable/ic_social_facebook.png
 * - res/drawable/ic_social_instagram.png
 * - res/drawable/ic_social_youtube.png
 */
@Composable
fun ContactoScreen(
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoPantalla)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactHeroCard(
                title = "Contacto y soporte",
                subtitle = "Comunícate con el equipo de soporte si necesitas ayuda con el uso de la aplicación."
            )

            Spacer(modifier = Modifier.height(18.dp))

            ContactInfoCard(
                icon = Icons.Filled.HeadsetMic,
                title = "Soporte técnico",
                description = "Aquí podemos brindarte soporte técnico relacionado con el uso de la aplicación, errores de acceso, fallas en el registro de monitoreos o dudas sobre la captura de información."
            )

            ContactInfoCard(
                icon = Icons.Filled.Email,
                title = "Correo de soporte",
                description = "tierrainteligente2@gmail.com",
                clickable = true,
                actionText = "Toca para enviar correo",
                onClick = {
                    abrirCorreoSoporte(
                        context = context,
                        correo = "tierrainteligente2@gmail.com"
                    )
                }
            )

            ContactInfoCard(
                icon = Icons.Filled.Call,
                title = "Teléfono Grupo GPA",
                description = "445 131 9493",
                clickable = true,
                actionText = "Toca para abrir teléfono",
                onClick = {
                    abrirTelefonoSoporte(
                        context = context,
                        telefono = "4451319493"
                    )
                }
            )

            ContactInfoCard(
                icon = Icons.Filled.LocationOn,
                title = "Ubicación actual",
                description = "Grupo GPA Puerto Interior\nPuerto Interior, Guanajuato, C.P. 36275",
                clickable = true,
                actionText = "Toca para abrir ubicación en Google Maps",
                onClick = {
                    abrirUbicacionGpa(context)
                }
            )

            ContactInfoCard(
                icon = Icons.Filled.ChatBubbleOutline,
                title = "Recomendación al solicitar ayuda",
                description = "Cuando reportes un problema, incluye tu usuario, el tipo de error, la pantalla donde ocurrió y una breve descripción de lo que estabas realizando. Esto ayuda a dar seguimiento más rápido."
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Redes sociales",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            SocialSection(context)

            Spacer(modifier = Modifier.height(28.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar",
                    tint = TextoPrincipal
                )
            }
        }
    }
}

@Composable
private fun ContactHeroCard(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            VerdeOscuro,
                            VerdeMedio,
                            VerdeClaro
                        )
                    )
                )
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.97f)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.16f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.gpa),
                        contentDescription = "Grupo GPA",
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactInfoCard(
    icon: ImageVector,
    title: String,
    description: String,
    clickable: Boolean = false,
    actionText: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .then(
                if (clickable && onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAF5E8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = VerdeMedio,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextoPrincipal
                )

                if (clickable) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = actionText ?: "Toca para abrir",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = VerdeMedio
                    )
                }
            }

            if (clickable) {
                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Abrir",
                    tint = TextoSecundario
                )
            }
        }
    }
}

@Composable
private fun SocialSection(
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SocialImageButton(
            iconRes = R.drawable.ic_social_facebook,
            label = "Facebook",
            onClick = {
                abrirRedSocial(
                    context = context,
                    url = "https://www.facebook.com/gpamex",
                    nombreRed = "Facebook"
                )
            }
        )

        SocialImageButton(
            iconRes = R.drawable.ic_social_instagram,
            label = "Instagram",
            onClick = {
                abrirRedSocial(
                    context = context,
                    url = "https://www.instagram.com/gpamex?igsh=Y2tkdnUyNXk5Zjkw",
                    nombreRed = "Instagram"
                )
            }
        )

        SocialImageButton(
            iconRes = R.drawable.ic_social_youtube,
            label = "YouTube",
            onClick = {
                abrirRedSocial(
                    context = context,
                    url = "https://youtube.com/@gpamex?si=IGtYRT6i3Yaj78kP",
                    nombreRed = "YouTube"
                )
            }
        )
    }
}

@Composable
private fun SocialImageButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = TextoSecundario,
            textAlign = TextAlign.Center
        )
    }
}

private fun abrirCorreoSoporte(
    context: Context,
    correo: String
) {
    val asunto = "Soporte - Tierra Inteligente"
    val mensaje = """
        Hola, necesito ayuda con la aplicación Tierra Inteligente.

        Usuario:
        Detalle del problema:
    """.trimIndent()

    try {
        val intentGmail = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$correo")
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            putExtra(Intent.EXTRA_TEXT, mensaje)
            setPackage("com.google.android.gm")
        }
        context.startActivity(intentGmail)
    } catch (_: Exception) {
        try {
            val intentCorreo = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$correo")
                putExtra(Intent.EXTRA_SUBJECT, asunto)
                putExtra(Intent.EXTRA_TEXT, mensaje)
            }
            context.startActivity(intentCorreo)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "No se encontró una aplicación para enviar correo",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun abrirTelefonoSoporte(
    context: Context,
    telefono: String
) {
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:$telefono")
            )
        )
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "No se pudo abrir la aplicación de teléfono",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun abrirUbicacionGpa(
    context: Context
) {
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://maps.app.goo.gl/Lvnj8WqVarPMrNkb7?g_st=aw")
            )
        )
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "No se pudo abrir Google Maps",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun abrirRedSocial(
    context: Context,
    url: String,
    nombreRed: String
) {
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "No se pudo abrir $nombreRed",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactoScreenPreview() {
    MaterialTheme {
        ContactoScreen(onCloseClick = {})
    }
}

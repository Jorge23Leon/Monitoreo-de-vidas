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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
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

private val InfoFondo = Color(0xFFF4F8F0)
private val InfoVerdeOscuro = Color(0xFF1B5224)
private val InfoVerdeMedio = Color(0xFF2F7D32)
private val InfoVerdeClaro = Color(0xFF97CB74)
private val InfoTextoPrincipal = Color(0xFF1B2C1E)
private val InfoTextoSecundario = Color(0xFF5E6D61)

/**
 * Información.
 *
 * CAMBIO:
 * Las descripciones, subtítulos y nombres de redes vuelven a peso normal.
 * Solo los títulos se mantienen en negritas.
 *
 * Requiere:
 * - res/drawable/gpa.webp
 * - res/drawable/ic_social_facebook.png
 * - res/drawable/ic_social_instagram.png
 * - res/drawable/ic_social_youtube.png
 */
@Composable
fun InformacionScreen(
    onCloseClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InfoFondo)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InfoHeroCard(
                title = "Información",
                subtitle = "Conoce el objetivo de la aplicación y el enfoque tecnológico de Grupo GPA."
            )

            Spacer(modifier = Modifier.height(18.dp))

            InfoCard(
                icon = Icons.Filled.Info,
                title = "Descripción de la app",
                text = "Esta aplicación está diseñada para apoyar el monitoreo agrícola de plagas y enfermedades en cultivos. Permite registrar información en campo, consultar puntos de monitoreo, capturar datos técnicos y organizar evidencia para facilitar el seguimiento del cultivo."
            )

            InfoCard(
                icon = Icons.Filled.Flag,
                title = "Objetivo de la aplicación",
                text = "El objetivo principal es apoyar a técnicos y productores en la identificación, registro y seguimiento de plagas y enfermedades, ayudando a mejorar la toma de decisiones mediante información ordenada, precisa y disponible para consulta."
            )

            InfoCard(
                icon = Icons.Filled.Business,
                title = "Información de Grupo GPA",
                text = "Grupo GPA es una empresa mexicana con sede en León, Guanajuato. Se enfoca en investigación, desarrollo, innovación, producción y comercialización, con especialidad en la industria metalmecánica y en la fabricación de maquinaria de automatización."
            )

            InfoCard(
                icon = Icons.Filled.Settings,
                title = "Experiencia e innovación",
                text = "GPA ha evolucionado desde soluciones de automatización hacia la fabricación de máquinas y componentes complejos. Su trabajo se relaciona con sectores como la industria automotriz, ferroviaria, aeroespacial, agroindustrial y metalmecánica."
            )

            InfoCard(
                icon = Icons.Filled.Eco,
                title = "Relación con el sector agroindustrial",
                text = "Dentro del enfoque agroindustrial, la digitalización permite fortalecer el registro de datos en campo, el seguimiento de cultivos y el análisis técnico para mejorar procesos agrícolas. Esta aplicación forma parte de esa visión: usar tecnología para organizar información y apoyar decisiones en campo."
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Redes sociales",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = InfoTextoPrincipal,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            InfoSocialSection()

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
                    tint = InfoTextoPrincipal
                )
            }
        }
    }
}

@Composable
private fun InfoHeroCard(
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
                            InfoVerdeOscuro,
                            InfoVerdeMedio,
                            InfoVerdeClaro
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
private fun InfoCard(
    icon: ImageVector,
    title: String,
    text: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
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
                    tint = InfoVerdeMedio,
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
                    color = InfoTextoPrincipal
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = InfoTextoSecundario
                )
            }
        }
    }
}

@Composable
private fun InfoSocialSection() {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        InfoSocialImageButton(
            iconRes = R.drawable.ic_social_facebook,
            label = "Facebook",
            onClick = {
                abrirRedSocialInfo(
                    context = context,
                    url = "https://www.facebook.com/gpamex",
                    nombreRed = "Facebook"
                )
            }
        )

        InfoSocialImageButton(
            iconRes = R.drawable.ic_social_instagram,
            label = "Instagram",
            onClick = {
                abrirRedSocialInfo(
                    context = context,
                    url = "https://www.instagram.com/gpamex?igsh=Y2tkdnUyNXk5Zjkw",
                    nombreRed = "Instagram"
                )
            }
        )

        InfoSocialImageButton(
            iconRes = R.drawable.ic_social_youtube,
            label = "YouTube",
            onClick = {
                abrirRedSocialInfo(
                    context = context,
                    url = "https://youtube.com/@gpamex?si=IGtYRT6i3Yaj78kP",
                    nombreRed = "YouTube"
                )
            }
        )
    }
}

@Composable
private fun InfoSocialImageButton(
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
            color = InfoTextoSecundario,
            textAlign = TextAlign.Center
        )
    }
}

private fun abrirRedSocialInfo(
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
private fun InformacionScreenPreview() {
    MaterialTheme {
        InformacionScreen(onCloseClick = {})
    }
}

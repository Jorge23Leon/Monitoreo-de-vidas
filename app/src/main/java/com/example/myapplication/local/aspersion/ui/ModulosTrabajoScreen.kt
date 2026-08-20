package com.example.myapplication.local.aspersion.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp


private val NdviModuleGreen = Color(0xFF43A047)
private val NdviModuleBackground = Color(0xFFF1F8E9)


@Composable
fun ModulosTrabajoScreen(
    nombreUsuario: String,
    rolUsuario: String,
    nombreCia: String,
    onMonitoreoClick: () -> Unit,
    onAspersionClick: () -> Unit,
    onNdviClick: () -> Unit,
    mostrarAspersion: Boolean,
    onPerfilClick: () -> Unit,
    onAdminClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)?,
    onCerrarSesionClick: () -> Unit
) {

    val ciaLabel =
        nombreCia
            .takeUnless {
                it.isBlank() ||
                        it == "Sin CIA"
            }
            ?: "Sesiones asignadas"


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7FAF5),
                        AspersionBackground
                    )
                )
            )
            .padding(
                horizontal = 15.dp,
                vertical = 10.dp
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                ),
            horizontalAlignment = Alignment.Start
        ) {

            // =================================================
            // ENCABEZADO
            // =================================================

            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreoClick,
                onAdminClick = onAdminClick,
                onCambiarCiaClick = onCambiarCiaClick,
                onCerrarSesionClick = onCerrarSesionClick
            )


            Spacer(
                modifier = Modifier.height(22.dp)
            )


            Text(
                text = "Módulos de trabajo",
                color = AspersionGreenDark,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black
            )


            Spacer(
                modifier = Modifier.height(4.dp)
            )


            Text(
                text = "Elige una opción para continuar",
                color = Color(0xFF647068),
                fontSize = 14.sp
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            // =================================================
            // CIA
            // =================================================

            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color(0xFFE5F1E8),
                contentColor = AspersionGreenDark,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = AspersionGreen.copy(
                        alpha = 0.14f
                    ),
                    shape = RoundedCornerShape(50.dp)
                )
            ) {

                Row(
                    modifier = Modifier.padding(
                        horizontal = 13.dp,
                        vertical = 8.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {

                    Icon(
                        imageVector = Icons.Rounded.Business,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )


                    Text(
                        text = ciaLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            Spacer(
                modifier = Modifier.height(20.dp)
            )


            // =================================================
            // MONITOREO
            // =================================================

            ModuleCard(
                imageName = "plagas",
                title = "Monitoreo",
                description =
                "Plagas, enfermedades, capturas de campo y reportes.",
                actionLabel = "Abrir monitoreo",
                color = AspersionGreen,
                backgroundColor = Color(0xFFF3F9F4),
                onClick = onMonitoreoClick
            )


            // =================================================
            // ASPERSIÓN
            // =================================================

            if (mostrarAspersion) {

                Spacer(
                    modifier = Modifier.height(14.dp)
                )


                ModuleCard(
                    imageName = "aspersion",
                    title = "Aspersión",
                    description =
                    "Área aplicada, dosis, velocidad, presión, caudal y productividad.",
                    actionLabel = "Ver sesiones",
                    color = AspersionBlue,
                    backgroundColor = Color(0xFFF2F6FD),
                    onClick = onAspersionClick
                )
            }


            // =================================================
            // NDVI
            // =================================================

            Spacer(
                modifier = Modifier.height(14.dp)
            )


            ModuleCard(
                /**
                 * IMPORTANTE:
                 *
                 * En tu drawable el archivo se llama:
                 *
                 * ndbi.webp
                 *
                 * Por eso aquí usamos "ndbi".
                 */
                imageName = "ndbi",
                title = "NDVI",
                description =
                "Índices vegetativos, vigor del cultivo y análisis geoespacial.",
                actionLabel = "Ver sesiones NDVI",
                color = NdviModuleGreen,
                backgroundColor = NdviModuleBackground,
                onClick = onNdviClick
            )


            Spacer(
                modifier = Modifier.height(24.dp)
            )
        }
    }
}


// =============================================================
// TARJETA DE MÓDULO
// =============================================================

@Composable
private fun ModuleCard(
    imageName: String,
    title: String,
    description: String,
    actionLabel: String,
    color: Color,
    backgroundColor: Color,
    onClick: () -> Unit
) {

    val context = LocalContext.current


    /**
     * Busca automáticamente:
     *
     * drawable/plagas.webp
     * drawable/aspersion.webp
     * drawable/ndbi.webp
     *
     * Así no dependemos del package generado para R.
     */
    val imageResourceId =
        remember(
            context,
            imageName
        ) {

            context.resources.getIdentifier(
                imageName,
                "drawable",
                context.packageName
            )
        }


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = color.copy(
                    alpha = 0.12f
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                onClick = onClick
            ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White,
                            backgroundColor
                        )
                    )
                )
                .padding(
                    horizontal = 17.dp,
                    vertical = 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {


            // =================================================
            // IMAGEN REAL DEL MÓDULO
            // =================================================

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        color.copy(
                            alpha = 0.10f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {

                if (imageResourceId != 0) {

                    Image(
                        painter = painterResource(
                            id = imageResourceId
                        ),
                        contentDescription = title,
                        modifier = Modifier
                            .size(66.dp)
                            .clip(
                                RoundedCornerShape(12.dp)
                            ),
                        contentScale = ContentScale.Fit
                    )

                } else {

                    /**
                     * Esto solo aparecería si escribimos
                     * mal el nombre de algún drawable.
                     */
                    Text(
                        text = "?",
                        color = color,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }


            // =================================================
            // TEXTO
            // =================================================

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    color = color,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black
                )


                Spacer(
                    modifier = Modifier.height(3.dp)
                )


                Text(
                    text = description,
                    color = Color(0xFF5F6962),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )


                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                Text(
                    text = actionLabel,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }


            // =================================================
            // FLECHA
            // =================================================

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(
                        RoundedCornerShape(13.dp)
                    )
                    .background(color),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector =
                    Icons.Rounded.ArrowForward,
                    contentDescription =
                    "Abrir $title",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
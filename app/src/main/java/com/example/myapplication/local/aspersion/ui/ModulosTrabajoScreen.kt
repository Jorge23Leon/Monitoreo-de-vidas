package com.example.myapplication.local.aspersion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HomeGreenDark = Color(0xFF075C38)
private val HomeGreen = Color(0xFF16A34A)
private val HomeLightGreen = Color(0xFFEAF7EE)
private val HomeBlue = Color(0xFF1976D2)
private val HomeTextDark = Color(0xFF173B2B)
private val HomeTextGray = Color(0xFF5F6862)
private val HomeBackground = Color(0xFFF5F8F4)

@Composable
fun ModulosTrabajoScreen(
    nombreUsuario: String,
    rolUsuario: String,
    nombreCia: String,
    onMonitoreoClick: () -> Unit,
    onAspersionClick: () -> Unit,
    mostrarAspersion: Boolean,
    onPerfilClick: () -> Unit,
    onAdminClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)?,
    onCerrarSesionClick: () -> Unit
) {
    val ciaLabel = nombreCia
        .takeUnless { it.isBlank() || it.equals("Sin CIA", ignoreCase = true) }
        ?: "Sesiones asignadas"

    val fechaActual = remember {
        SimpleDateFormat(
            "dd 'de' MMMM 'de' yyyy",
            Locale("es", "MX")
        ).format(Date())
    }

    var mostrarMenu by remember {
        mutableStateOf(false)
    }

    var mostrarContenido by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        delay(100)
        mostrarContenido = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 22.dp
                )
        ) {
            HomeHeroHeader(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                fechaActual = fechaActual,
                nombreCia = ciaLabel,
                mostrarMenu = mostrarMenu,
                onMenuChange = { mostrarMenu = it },
                onPerfilClick = onPerfilClick,
                onAdminClick = onAdminClick,
                onCambiarCiaClick = onCambiarCiaClick,
                onCerrarSesionClick = onCerrarSesionClick
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                /*
                 * El margen negativo visual se consigue colocando las tarjetas
                 * muy cerca del final del encabezado.
                 */
                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = mostrarContenido,
                    enter = fadeIn(
                        animationSpec = tween(450)
                    ) + slideInVertically(
                        animationSpec = tween(450),
                        initialOffsetY = { it / 3 }
                    )
                ) {
                    ModuleImageCard(
                        imageRes = R.drawable.plagas,
                        title = "Monitoreo",
                        description = "Plagas, enfermedades, capturas de campo y reportes.",
                        accentColor = HomeGreen,
                        imageBackground = HomeLightGreen,
                        onClick = onMonitoreoClick
                    )
                }

                if (mostrarAspersion) {
                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedVisibility(
                        visible = mostrarContenido,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 100
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 100
                            ),
                            initialOffsetY = { it / 2 }
                        )
                    ) {
                        ModuleImageCard(
                            imageRes = R.drawable.aspersion,
                            title = "Aspersión",
                            description = "Área aplicada, dosis, velocidad, presión, caudal y productividad.",
                            accentColor = HomeBlue,
                            imageBackground = Color(0xFFE7F0FF),
                            onClick = onAspersionClick
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HomeHeroHeader(
    nombreUsuario: String,
    rolUsuario: String,
    fechaActual: String,
    nombreCia: String,
    mostrarMenu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onPerfilClick: () -> Unit,
    onAdminClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)?,
    onCerrarSesionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .clip(
                RoundedCornerShape(
                    bottomStart = 34.dp,
                    bottomEnd = 34.dp
                )
            )
    ) {
        Image(
            painter = painterResource(R.drawable.campo),
            contentDescription = "Campo agrícola",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        /*
         * Oscurece ligeramente la parte superior para que el logo,
         * el menú y los textos siempre sean legibles.
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF087E62).copy(alpha = 0.88f),
                            Color(0xFF12A77A).copy(alpha = 0.42f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 760f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding() + 8.dp,
                    start = 18.dp,
                    end = 18.dp,
                    bottom = 22.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = {
                            onMenuChange(true)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Abrir menú",
                            tint = Color.White,
                            modifier = Modifier.size(27.dp)
                        )
                    }

                    HomeDropdownMenu(
                        expanded = mostrarMenu,
                        onDismiss = {
                            onMenuChange(false)
                        },
                        onPerfilClick = {
                            onMenuChange(false)
                            onPerfilClick()
                        },
                        onAdminClick = {
                            onMenuChange(false)
                            onAdminClick()
                        },
                        onCambiarCiaClick = onCambiarCiaClick?.let { cambiarCia ->
                            {
                                onMenuChange(false)
                                cambiarCia()
                            }
                        },
                        onCerrarSesionClick = {
                            onMenuChange(false)
                            onCerrarSesionClick()
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Image(
                    painter = painterResource(R.drawable.logo_agroindustry),
                    contentDescription = "CPA Agroindustry",
                    modifier = Modifier
                        .height(53.dp)
                        .width(165.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    IconButton(
                        onClick = {
                            // Preparado para conectar notificaciones posteriormente.
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsNone,
                            contentDescription = "Notificaciones",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    /*
                     * Indicador decorativo. Se puede conectar después
                     * con el número real de notificaciones.
                     */
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .background(
                                color = Color(0xFFFF5252),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "¡Bienvenido de vuelta!",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "${rolUsuario.ifBlank { "Usuario" }} · $fechaActual",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 12.sp
            )

            if (nombreUsuario.isNotBlank()) {
                Text(
                    text = nombreUsuario,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "¿Qué deseas\nconsultar hoy?",
                color = Color.White,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color.White.copy(alpha = 0.84f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 17.dp,
                        vertical = 10.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = HomeGreenDark,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = nombreCia,
                        color = HomeGreenDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = HomeGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPerfilClick: () -> Unit,
    onAdminClick: () -> Unit,
    onCambiarCiaClick: (() -> Unit)?,
    onCerrarSesionClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(245.dp)
            .background(Color.White)
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "Mi perfil",
                    fontWeight = FontWeight.SemiBold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = HomeGreenDark
                )
            },
            onClick = onPerfilClick
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Panel administrador",
                    fontWeight = FontWeight.SemiBold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = HomeGreenDark
                )
            },
            onClick = onAdminClick
        )

        if (onCambiarCiaClick != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Cambiar de CIA",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = HomeGreenDark
                    )
                },
                onClick = onCambiarCiaClick
            )
        }

        Divider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = Color(0xFFE5E7E6)
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Cerrar sesión",
                    color = Color(0xFFB3261E),
                    fontWeight = FontWeight.Bold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = Color(0xFFB3261E)
                )
            },
            onClick = onCerrarSesionClick
        )
    }
}

@Composable
private fun ModuleImageCard(
    imageRes: Int,
    title: String,
    description: String,
    accentColor: Color,
    imageBackground: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }

    val estaPresionada by interactionSource.collectIsPressedAsState()

    val escala by animateFloatAsState(
        targetValue = if (estaPresionada) 0.975f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "module_card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(escala)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 9.dp,
            pressedElevation = 3.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 17.dp,
                    vertical = 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(82.dp),
                shape = CircleShape,
                color = imageBackground,
                shadowElevation = 5.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Image(
                        painter = painterResource(imageRes),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = description,
                    color = HomeTextGray,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.ArrowForwardIos,
                contentDescription = "Abrir $title",
                tint = accentColor,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}
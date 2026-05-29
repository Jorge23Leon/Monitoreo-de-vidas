package com.example.myapplication.local.admin.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.EncabezadoApp

@Composable
fun AdminHomeScreen(
    nombreUsuario: String,
    rolUsuario: String,
    nombreCia: String,
    puedeCrearMonitoreos: Boolean,
    puedeGestionCatalogos: Boolean,
    puedeGestionAgricola: Boolean,
    onMonitoreosAdminClick: () -> Unit,
    onCatalogosClick: () -> Unit,
    onGestionAgricolaClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onCerrarSesionClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF7EF))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EncabezadoApp(
                nombreUsuario = nombreUsuario,
                rolUsuario = rolUsuario,
                onPerfilClick = onPerfilClick,
                onMonitoreosClick = onMonitoreosClick,
                onAdminClick = onAdminClick,
                onCerrarSesionClick = onCerrarSesionClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            PanelTrabajoHeroSinVerde(nombreCia = nombreCia)

            Spacer(modifier = Modifier.height(20.dp))

            var mostroOpcion = false

            if (puedeCrearMonitoreos) {
                mostroOpcion = true
                AdminOptionCardSinVerde(
                    icono = "📋",
                    titulo = "Crear y administrar\nmonitoreos",
                    colorFondoIcono = Color(0xFFF1E2C2),
                    onClick = onMonitoreosAdminClick
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (puedeGestionCatalogos) {
                mostroOpcion = true
                AdminOptionCardSinVerde(
                    icono = "📚",
                    titulo = "Administrar\ncatálogos",
                    colorFondoIcono = Color(0xFFEDE3D2),
                    onClick = onCatalogosClick
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (puedeGestionAgricola) {
                mostroOpcion = true
                AdminOptionCardSinVerde(
                    icono = "🚜",
                    titulo = "Gestión agrícola",
                    colorFondoIcono = Color(0xFFF4E8C9),
                    onClick = onGestionAgricolaClick
                )
            }

            if (!mostroOpcion) {
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3D6)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Tu rol no tiene opciones administrativas.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        color = Color(0xFF8A5A00),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PanelTrabajoHeroSinVerde(
    nombreCia: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFBF2),
                            Color(0xFFF6EBD8),
                            Color(0xFFE7D2AA)
                        )
                    )
                )
        ) {
            Text(
                text = "✦",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 22.dp),
                fontSize = 56.sp,
                color = Color(0x33A87825)
            )

            Text(
                text = "◡",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 8.dp),
                fontSize = 96.sp,
                color = Color(0x22A87825)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 22.dp, top = 30.dp, end = 18.dp)
            ) {
                Text(
                    text = "Panel de trabajo",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1F1F1F),
                    lineHeight = 38.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "CIA actual: $nombreCia",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7A5A24)
                )
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🌾",
                    fontSize = 34.sp
                )
            }
        }
    }
}

@Composable
private fun AdminOptionCardSinVerde(
    icono: String,
    titulo: String,
    colorFondoIcono: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFFFFCF6),
                            Color(0xFFF7EFE2)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 90.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorFondoIcono),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icono,
                    fontSize = 46.sp
                )
            }

            Text(
                text = titulo,
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1F1F1F),
                lineHeight = 26.sp
            )

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1E2C2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "›",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF7A5A24)
                )
            }
        }
    }
}
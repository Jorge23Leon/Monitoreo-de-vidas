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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onMonitoreosAdminClick: () -> Unit,
    onCatalogosClick: () -> Unit,
    onGestionAgricolaClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onMonitoreosClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(18.dp)
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
                onAdminClick = onAdminClick
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Panel administrador",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "CIA actual: $nombreCia",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4E5D45),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            AdminOptionCard(
                icono = "🗺️",
                titulo = "Crear y administrar monitoreos",
                descripcion = "Preparar programas, encabezados y puntos objetivo usando LocalProgramEntity, LocalPhytomonitoringHeaderEntity y LocalPhytomonitoringTargetPointEntity.",
                onClick = onMonitoreosAdminClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdminOptionCard(
                icono = "📚",
                titulo = "Administrar catálogos",
                descripcion = "Gestionar cultivos, plagas, enfermedades y etapas usando LocalCropCatalogEntity, LocalPhytosanitaryCatalogEntity y LocalPhytostageEntity.",
                onClick = onCatalogosClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdminOptionCard(
                icono = "🌾",
                titulo = "Gestión agrícola",
                descripcion = "Agregar productores, ranchos, parcelas y vértices usando LocalAgroUnitEntity, LocalRanchEntity, LocalPlotEntity y LocalPlotVertexEntity.",
                onClick = onGestionAgricolaClick
            )
        }
    }
}

@Composable
private fun AdminOptionCard(
    icono: String,
    titulo: String,
    descripcion: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFF6FFF1),
                            Color.White
                        )
                    )
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = icono,
                fontSize = 34.sp
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = titulo,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1B5E20)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = descripcion,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4E5D45),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

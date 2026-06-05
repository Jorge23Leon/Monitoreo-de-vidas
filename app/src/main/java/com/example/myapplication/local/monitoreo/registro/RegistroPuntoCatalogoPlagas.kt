package com.example.myapplication.local.monitoreo.registro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.common.ImageUriBox
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity

@Composable
internal fun CatalogoPlagasHorizontal(
    catalogo: List<LocalPhytosanitaryCatalogEntity>,
    fitoSeleccionado: LocalPhytosanitaryCatalogEntity?,
    onSelected: (LocalPhytosanitaryCatalogEntity) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionTitle(
                title = "Catálogo fitosanitario",
                actionText = "Ver todo  ›"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                catalogo.forEach { item ->
                    FitoMiniCard(
                        item = item,
                        seleccionado = fitoSeleccionado?.idPhytosanitary == item.idPhytosanitary,
                        onClick = { onSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FitoMiniCard(
    item: LocalPhytosanitaryCatalogEntity,
    seleccionado: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(132.dp)
            .height(150.dp)
            .border(
                width = if (seleccionado) 2.dp else 1.dp,
                color = if (seleccionado) Color(0xFF0D47C5) else Color(0xFFE1E5EA),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado) Color(0xFFF4F8FF) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (seleccionado) 5.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ImageUriBox(
                        photo = item.photo,
                        fallbackIcon = iconoTipoFitoRegistro(item.type),
                        sizeDp = 58,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                if (esEnfermedadRegistro(item.type)) {
                                    Color(0xFFEAF4E7)
                                } else {
                                    Color(0xFFFFEFEA)
                                }
                            )
                    )

                    if (seleccionado) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D47C5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D2430),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = textoTipoFitoRegistro(item.type),
                    modifier = Modifier
                        .background(colorChipFondoTipoRegistro(item.type), RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = colorChipTextoTipoRegistro(item.type),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
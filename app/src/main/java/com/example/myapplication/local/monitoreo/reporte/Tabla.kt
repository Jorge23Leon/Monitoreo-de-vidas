package com.example.myapplication.local.monitoreo.reporte

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TablaReporteCapturasUi(filas: List<FilaReporteCapturaUi>) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
    ) {
        Row(
            modifier = Modifier
                .background(Color(0xFF1B5E20), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(vertical = 8.dp)
        ) {
            CeldaHeaderReporteUi("Punto", 70)
            CeldaHeaderReporteUi("Coordenadas", 190)
            CeldaHeaderReporteUi("Plaga / Enfermedad", 180)
            CeldaHeaderReporteUi("Tipo", 110)
            CeldaHeaderReporteUi("Fase", 120)
            CeldaHeaderReporteUi("Cantidad", 90)
            CeldaHeaderReporteUi("point_severity", 140)
            CeldaHeaderReporteUi("Fecha", 150)
        }

        if (filas.isEmpty()) {
            Text(
                text = "No hay capturas registradas para este monitoreo.",
                modifier = Modifier.padding(14.dp),
                color = Color.Gray,
                fontSize = 13.sp
            )
        } else {
            filas.forEachIndexed { index, fila ->
                val fondo = if (index % 2 == 0) Color(0xFFFAFCF9) else Color.White

                Row(
                    modifier = Modifier
                        .background(fondo)
                        .border(0.5.dp, Color(0xFFE0E7DE))
                        .padding(vertical = 7.dp)
                ) {
                    CeldaTextoReporteUi(fila.numeroPunto.toString(), 70)
                    CeldaTextoReporteUi(fila.coordenadas, 190)
                    CeldaTextoReporteUi(fila.plagaEnfermedad, 180)
                    CeldaTextoReporteUi(fila.tipo, 110)
                    CeldaTextoReporteUi(fila.fase, 120)
                    CeldaTextoReporteUi(fila.cantidad.toString(), 90)
                    CeldaChipSeveridadReporteUi(
                        texto = fila.severidad,
                        colorHex = fila.colorSeveridadHex,
                        ancho = 140
                    )
                    CeldaTextoReporteUi(fila.fechaCaptura, 150)
                }
            }
        }
    }
}

@Composable
private fun CeldaHeaderReporteUi(texto: String, ancho: Int) {
    Text(
        text = texto,
        modifier = Modifier.width(ancho.dp),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CeldaTextoReporteUi(texto: String, ancho: Int) {
    Text(
        text = texto,
        modifier = Modifier
            .width(ancho.dp)
            .padding(horizontal = 5.dp),
        color = Color(0xFF2E2E2E),
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        maxLines = 3
    )
}


@Composable
private fun CeldaChipSeveridadReporteUi(
    texto: String,
    colorHex: String,
    ancho: Int
) {
    val color = colorDesdeHexReporteUi(colorHex)

    Text(
        text = texto,
        modifier = Modifier
            .width(ancho.dp)
            .padding(horizontal = 7.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

private fun colorDesdeHexReporteUi(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF2E7D32)
    }
}

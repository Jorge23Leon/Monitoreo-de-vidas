package com.example.myapplication.local

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EncabezadoApp(
    nombreUsuario: String
) {
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

        Text(
            text = "☰",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 13.dp),
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}
package com.example.myapplication.local.aspersion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val AspersionGreen = Color(0xFF176B3A)
internal val AspersionGreenDark = Color(0xFF0F4D2C)
internal val AspersionBackground = Color(0xFFF5F7F2)
internal val AspersionBlue = Color(0xFF2166D1)
internal val AspersionText = Color(0xFF26332B)

@Composable
internal fun AspersionLayerSelector(
    selectedLayer: AspersionLayer,
    onLayerSelected: (AspersionLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        AspersionLayer.entries.forEach { layer ->
            val selected = layer == selectedLayer
            Surface(
                modifier = Modifier.clickable {
                    onLayerSelected(layer)
                },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) AspersionGreenDark else Color.White,
                shadowElevation = if (selected) 2.dp else 0.dp,
                border = if (selected) {
                    null
                } else {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFFD7DED8)
                    )
                }
            ) {
                Text(
                    text = layer.label,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    color = if (selected) Color.White else AspersionText,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun AspersionLegend(
    items: List<AspersionLegendItem>,
    visibleKeys: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (items.isEmpty()) {
        Text(
            text = "Esta variable no tiene datos para construir rangos.",
            modifier = modifier.padding(10.dp),
            color = Color(0xFF6A746C),
            fontSize = 12.sp
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val visible = item.key in visibleKeys
            val itemColor = colorFromHex(item.colorHex)

            Surface(
                modifier = Modifier.clickable {
                    onToggle(item.key)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (visible) Color.White else Color(0xFFF0F1F0),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (visible) itemColor.copy(alpha = 0.65f) else Color(0xFFD5D8D5)
                )
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 8.dp else 10.dp,
                        vertical = if (compact) 7.dp else 9.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (visible) itemColor else Color.Transparent)
                            .then(
                                Modifier.background(
                                    if (visible) itemColor else Color(0xFFE1E3E1)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (visible) {
                            Text(
                                text = "✓",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(7.dp))

                    Column {
                        Text(
                            text = item.label,
                            color = if (visible) AspersionText else Color(0xFF8B918D),
                            fontSize = if (compact) 11.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )

                        displayLegendRange(item)?.let { range ->
                            Text(
                                text = range,
                                color = Color(0xFF707871),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        if (!compact) {
                            Text(
                                text = "${item.pointCount} puntos · " +
                                    "${formatNumber(item.areaHa, 4)} ha",
                                color = Color(0xFF707871),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AspersionMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF68736B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                color = accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color(0xFF7A837C),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun AspersionLoadingCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = AspersionGreen,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = AspersionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

internal fun sessionTitle(
    session: LocalAspersionSessionEntity,
    plotNamesById: Map<String, String>
): String {
    return session.plotName
        ?.takeIf(String::isNotBlank)
        ?: session.plotId
        ?.let(plotNamesById::get)
        ?.takeIf(String::isNotBlank)
        ?: "Aspersión ${session.sessionId.take(8)}"
}

internal fun programTitle(
    session: LocalAspersionSessionEntity,
    programNamesById: Map<String, String>
): String {
    return session.programName
        ?.takeIf(String::isNotBlank)
        ?: session.programCycle
            ?.takeIf(String::isNotBlank)
        ?: session.programId
        ?.let(programNamesById::get)
        ?.takeIf(String::isNotBlank)
        ?: "Programa sin nombre local"
}

internal fun formatAspersionDate(value: String?): String {
    val clean = value?.trim().orEmpty()
    if (clean.isEmpty()) return "Fecha no disponible"

    val inputFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd"
    )

    inputFormats.forEach { pattern ->
        runCatching {
            val date = SimpleDateFormat(pattern, Locale.US).parse(clean)
                ?: return@runCatching null
            return SimpleDateFormat(
                "dd MMM yyyy",
                Locale("es", "MX")
            ).format(date)
        }
    }

    return clean.take(10)
}

internal fun formatSyncTime(value: Long?): String {
    if (value == null || value <= 0L) return "Sin descarga local"
    return "Guardado " + SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale("es", "MX")
    ).format(Date(value))
}

internal fun colorFromHex(hex: String): Color {
    return runCatching {
        val clean = hex.removePrefix("#")
        val value = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or value)
            8 -> Color(value)
            else -> Color(0xFF94A3B8)
        }
    }.getOrDefault(Color(0xFF94A3B8))
}

package com.example.myapplication.local.monitoreo.registro

import androidx.compose.ui.graphics.Color
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity
import com.example.myapplication.local.entities.LocalPhytostageEntity

internal data class RegistroPuntoDataUi(
    val catalogo: List<LocalPhytosanitaryCatalogEntity>,
    val nombreCultivo: String,
    val fotoCultivo: String?,
    val numeroPuntoVisible: Int,
    val totalPlagasAgregadas: Int
)

internal data class ClaveEtapaUi(
    val idPhytosanitary: Long,
    val stage: String
)

internal data class EtapaCantidadUi(
    val etapa: LocalPhytostageEntity,
    val cantidad: Int,
    val onMenos: () -> Unit,
    val onMas: () -> Unit
)

internal fun esEnfermedadRegistro(type: String?): Boolean {
    return type
        ?.trim()
        ?.lowercase()
        ?.contains("enfermedad") == true
}

internal fun textoTipoFitoRegistro(type: String?): String {
    val limpio = type?.trim()?.lowercase().orEmpty()

    return when {
        limpio.contains("enfermedad") -> "Enfermedad"
        limpio.contains("plaga") -> "Plaga"
        limpio.contains("sin") -> "Sin plaga"
        else -> type?.ifBlank { "Tipo" } ?: "Tipo"
    }
}

internal fun iconoTipoFitoRegistro(type: String?): String {
    return if (esEnfermedadRegistro(type)) "🦠" else "🐛"
}

internal fun colorChipFondoTipoRegistro(type: String?): Color {
    return if (esEnfermedadRegistro(type)) Color(0xFFEAF4E7) else Color(0xFFFFECE6)
}

internal fun colorChipTextoTipoRegistro(type: String?): Color {
    return if (esEnfermedadRegistro(type)) Color(0xFF2E7D32) else Color(0xFF9B3B18)
}

internal fun iconoEtapaRegistro(stage: String): String {
    val etapa = stage.trim().lowercase()

    return when {
        etapa.contains("huev") -> "◌"
        etapa.contains("larva") -> "🐛"
        etapa.contains("pupa") -> "⬯"
        etapa.contains("adulto") -> "🦟"
        etapa.contains("inicio") -> "🌱"
        etapa.contains("desarrollo") -> "🍃"
        etapa.contains("avanz") -> "🌿"
        etapa.contains("terminal") -> "🥀"
        else -> "•"
    }
}

internal fun colorIconoEtapaRegistro(stage: String): Color {
    val etapa = stage.trim().lowercase()

    return when {
        etapa.contains("huevecillo") -> Color(0xFFFFF3CD)
        etapa.contains("larva") -> Color(0xFFE6F4DA)
        etapa.contains("pupa") -> Color(0xFFF0E6FA)
        etapa.contains("adulto") -> Color(0xFFE3F2FD)
        etapa.contains("inicio") -> Color(0xFFE8F5E9)
        etapa.contains("desarrollo") -> Color(0xFFEAF4E7)
        etapa.contains("avanzada") -> Color(0xFFFFF3E0)
        etapa.contains("terminal") -> Color(0xFFFFEBEE)
        else -> Color(0xFFEDEDED)
    }
}

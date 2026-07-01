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

/**
 * Estado de una enfermedad evaluada en el punto.
 *
 * - NO_PRESENTE: se registra que la enfermedad se revisó y no fue detectada.
 * - PRESENTE: además requiere una fase entre inicio, desarrollo o avanzado.
 */
internal enum class PresenciaEnfermedadUi {
    NO_PRESENTE,
    PRESENTE
}

internal data class EstadoEnfermedadUi(
    val presencia: PresenciaEnfermedadUi,
    val stage: String? = null
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
/**
 * Para enfermedades se permiten únicamente tres fases.
 * "Terminal" deja de estar disponible en el registro nuevo.
 */
internal fun fasesEnfermedadPermitidas(
    etapas: List<LocalPhytostageEntity>
): List<LocalPhytostageEntity> {
    return etapas
        .filter { etapa ->
            val nombre = etapa.stage.trim().lowercase()

            nombre.contains("inicio") ||
                    nombre.contains("desarrollo") ||
                    nombre.contains("avanz")
        }
        .sortedBy { etapa ->
            val nombre = etapa.stage.trim().lowercase()

            when {
                nombre.contains("inicio") -> 0
                nombre.contains("desarrollo") -> 1
                nombre.contains("avanz") -> 2
                else -> 99
            }
        }
}

internal fun ordenarEtapasParaRegistro(
    etapas: List<LocalPhytostageEntity>,
    tipoFito: String?
): List<LocalPhytostageEntity> {
    return if (esEnfermedadRegistro(tipoFito)) {
        fasesEnfermedadPermitidas(etapas)
    } else {
        etapas.sortedBy { etapa ->
            val nombre = etapa.stage.trim().lowercase()

            when {
                nombre == "adulto" -> 0
                nombre.contains("adulto") && !nombre.contains("alas") -> 0
                nombre.contains("adulto") && nombre.contains("alas") -> 1
                nombre.contains("huev") -> 2
                nombre.contains("larva") || nombre.contains("joven") -> 3
                nombre.contains("pupa") -> 4
                else -> 99
            }
        }
    }
}

internal fun fotoRepresentativaFitoRegistro(
    fito: LocalPhytosanitaryCatalogEntity,
    etapas: List<LocalPhytostageEntity>
): String? {
    if (etapas.isEmpty()) return fito.photo

    val esEnfermedad = esEnfermedadRegistro(fito.type)

    val fotoPreferida = if (esEnfermedad) {
        etapas.firstOrNull { etapa ->
            val nombre = etapa.stage.trim().lowercase()
            nombre.contains("avanz")
        }?.photo
    } else {
        etapas.firstOrNull { etapa ->
            val nombre = etapa.stage.trim().lowercase()
            nombre.contains("adulto") && !nombre.contains("alas")
        }?.photo
            ?: etapas.firstOrNull { etapa ->
                val nombre = etapa.stage.trim().lowercase()
                nombre.contains("adulto")
            }?.photo
    }

    return fotoPreferida
        ?.takeIf { it.isNotBlank() }
        ?: etapas.firstOrNull { !it.photo.isNullOrBlank() }?.photo
        ?: fito.photo
}
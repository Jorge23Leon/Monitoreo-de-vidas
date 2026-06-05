package com.example.myapplication.local.monitoreo.severidad

import com.example.myapplication.local.entities.LocalPhytomonitoringCheckpointEntity
import com.example.myapplication.local.entities.LocalPhytosanitaryCatalogEntity

/*
 * Solo se captura la SEVERIDAD MAYOR.
 * La severidad menor se calcula automáticamente como la mitad de la mayor.
 *
 * Ejemplo si mayor = 10:
 * Verde: 0
 * Severidad menor: 1-5
 * Severidad mayor: 6-10
 * Rojo: 11+
 */
internal const val SEVERIDAD_MAYOR_DEFAULT = 20

internal enum class NivelSeveridad(
    val orden: Int,
    val etiqueta: String,
    val colorHex: String
) {
    VERDE(
        orden = 0,
        etiqueta = "Sin plaga",
        colorHex = "#16A34A"
    ),
    AMARILLO(
        orden = 1,
        etiqueta = "Severidad menor",
        colorHex = "#FACC15"
    ),
    NARANJA(
        orden = 2,
        etiqueta = "Severidad mayor",
        colorHex = "#F97316"
    ),
    ROJO(
        orden = 3,
        etiqueta = "Supera severidad mayor",
        colorHex = "#DC2626"
    )
}

internal data class RangosSeveridad(
    val mayor: Int = SEVERIDAD_MAYOR_DEFAULT
) {
    val menor: Int
        get() = maxOf(1, mayor / 2)
}

private val REGEX_METADATA_SEVERIDAD_NUEVA = Regex("\\[SEV_PUNTO:M=(\\d+)\\]")
private val REGEX_METADATA_SEVERIDAD_ANTIGUA = Regex("\\[SEV_PUNTO:m=(\\d+);M=(\\d+)\\]")

internal fun agregarMetadataRangosSeveridad(
    notas: String?,
    rangos: RangosSeveridad
): String {
    val notasLimpias = limpiarMetadataRangosSeveridad(notas).trim()
    val metadata = "[SEV_PUNTO:M=${rangos.mayor}]"

    return if (notasLimpias.isBlank()) {
        metadata
    } else {
        "$metadata $notasLimpias"
    }
}

internal fun limpiarMetadataRangosSeveridad(notas: String?): String {
    return notas
        ?.replace(REGEX_METADATA_SEVERIDAD_NUEVA, "")
        ?.replace(REGEX_METADATA_SEVERIDAD_ANTIGUA, "")
        ?.trim()
        .orEmpty()
}

internal fun rangosSeveridadDesdeCheckpoints(
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>
): RangosSeveridad? {
    checkpoints.forEach { checkpoint ->
        val notas = checkpoint.notes.orEmpty()

        val matchNuevo = REGEX_METADATA_SEVERIDAD_NUEVA.find(notas)
        if (matchNuevo != null) {
            val mayor = matchNuevo.groupValues.getOrNull(1)?.toIntOrNull()
            if (mayor != null && mayor > 0) {
                return RangosSeveridad(mayor = mayor)
            }
        }

        val matchAntiguo = REGEX_METADATA_SEVERIDAD_ANTIGUA.find(notas)
        if (matchAntiguo != null) {
            val mayor = matchAntiguo.groupValues.getOrNull(2)?.toIntOrNull()
            if (mayor != null && mayor > 0) {
                return RangosSeveridad(mayor = mayor)
            }
        }
    }

    return null
}

internal data class SeveridadFitoPuntoUi(
    val idPhytosanitary: Long,
    val nombre: String,
    val tipo: String,
    val cantidadTotal: Int,
    val etapasResumen: String,
    val fechaUltimaCaptura: Long?,
    val nivel: NivelSeveridad
)

internal data class SeveridadPuntoUi(
    val nivelFinal: NivelSeveridad,
    val totalCantidadPunto: Int,
    val fitos: List<SeveridadFitoPuntoUi>,
    val tieneCapturas: Boolean
)

internal fun rangosSeveridadDesdeCatalogo(
    item: LocalPhytosanitaryCatalogEntity?
): RangosSeveridad {
    val mayor = item?.maxRefValue?.takeIf { it > 0 } ?: SEVERIDAD_MAYOR_DEFAULT
    return RangosSeveridad(mayor = mayor)
}

internal fun rangosSeveridadDesdeTexto(
    mayorTexto: String
): RangosSeveridad? {
    val mayor = mayorTexto.trim().toIntOrNull() ?: return null
    if (mayor <= 0) return null

    return RangosSeveridad(mayor = mayor)
}

internal fun calcularNivelSeveridad(
    cantidadTotal: Int,
    presenceStatus: Int?,
    rangos: RangosSeveridad = RangosSeveridad()
): NivelSeveridad {
    if (presenceStatus == 0 || cantidadTotal <= 0) {
        return NivelSeveridad.VERDE
    }

    return when {
        cantidadTotal <= rangos.menor -> NivelSeveridad.AMARILLO
        cantidadTotal <= rangos.mayor -> NivelSeveridad.NARANJA
        else -> NivelSeveridad.ROJO
    }
}

internal fun combinarNivelSeveridad(
    niveles: List<NivelSeveridad>
): NivelSeveridad {
    return niveles.maxByOrNull { it.orden } ?: NivelSeveridad.VERDE
}

internal fun totalCantidadPorFitosanitario(
    checkpoints: List<LocalPhytomonitoringCheckpointEntity>,
    idPhytosanitary: Long
): Int {
    return checkpoints
        .filter { checkpoint ->
            checkpoint.idPhytosanitary == idPhytosanitary && checkpoint.presenceStatus == 1
        }
        .sumOf { checkpoint -> checkpoint.qty ?: 0 }
}

internal fun calcularSeveridadPorPunto(
    checkpointsPunto: List<LocalPhytomonitoringCheckpointEntity>,
    catalogoPorId: Map<Long, LocalPhytosanitaryCatalogEntity>,
    rangos: RangosSeveridad = RangosSeveridad()
): SeveridadPuntoUi {
    val capturasConPresencia = checkpointsPunto.filter { checkpoint ->
        checkpoint.presenceStatus == 1
    }

    val totalPunto = capturasConPresencia.sumOf { checkpoint ->
        checkpoint.qty ?: 0
    }

    val tieneSinPlaga = checkpointsPunto.any { checkpoint ->
        checkpoint.presenceStatus == 0
    }

    val rangosPunto = rangosSeveridadDesdeCheckpoints(checkpointsPunto) ?: rangos

    val nivelGlobal = calcularNivelSeveridad(
        cantidadTotal = totalPunto,
        presenceStatus = when {
            totalPunto > 0 -> 1
            tieneSinPlaga -> 0
            else -> null
        },
        rangos = rangosPunto
    )

    val fitos = capturasConPresencia
        .groupBy { checkpoint -> checkpoint.idPhytosanitary }
        .map { (idPhytosanitary, capturasFito) ->
            val item = catalogoPorId[idPhytosanitary]
            val totalFito = capturasFito.sumOf { checkpoint -> checkpoint.qty ?: 0 }

            SeveridadFitoPuntoUi(
                idPhytosanitary = idPhytosanitary,
                nombre = item?.name ?: "Sin identificar",
                tipo = item?.type ?: "-",
                cantidadTotal = totalFito,
                etapasResumen = resumenEtapasCapturadas(capturasFito),
                fechaUltimaCaptura = capturasFito.maxOfOrNull { checkpoint -> checkpoint.capturedAt ?: 0L }
                    ?.takeIf { fecha -> fecha > 0L },
                nivel = nivelGlobal
            )
        }
        .sortedWith(
            compareByDescending<SeveridadFitoPuntoUi> { fito -> fito.cantidadTotal }
                .thenBy { fito -> fito.nombre }
        )

    return SeveridadPuntoUi(
        nivelFinal = nivelGlobal,
        totalCantidadPunto = totalPunto,
        fitos = fitos,
        tieneCapturas = checkpointsPunto.isNotEmpty()
    )
}

private fun resumenEtapasCapturadas(
    capturas: List<LocalPhytomonitoringCheckpointEntity>
): String {
    return capturas
        .filter { checkpoint -> checkpoint.presenceStatus == 1 }
        .groupBy { checkpoint -> checkpoint.stage?.trim()?.takeIf { it.isNotBlank() } ?: "General" }
        .map { (stage, capturasStage) ->
            val totalStage = capturasStage.sumOf { checkpoint -> checkpoint.qty ?: 0 }
            "$stage: $totalStage"
        }
        .joinToString(", ")
        .ifBlank { "-" }
}

internal fun textoRangoSemaforo(rangos: RangosSeveridad): String {
    val inicioMayor = rangos.menor + 1
    val inicioRojo = rangos.mayor + 1

    return "Verde: 0  •  Severidad menor: 1-${rangos.menor}  •  Severidad mayor: $inicioMayor-${rangos.mayor}  •  Rojo: $inicioRojo+"
}

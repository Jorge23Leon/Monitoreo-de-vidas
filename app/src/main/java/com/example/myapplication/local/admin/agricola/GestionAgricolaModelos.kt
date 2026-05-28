package com.example.myapplication.local.admin.agricola

import com.example.myapplication.local.entities.LocalAgroUnitEntity
import com.example.myapplication.local.entities.LocalPlotEntity
import com.example.myapplication.local.entities.LocalPlotVertexEntity
import com.example.myapplication.local.entities.LocalRanchEntity
import java.text.Normalizer
import java.util.Locale

internal enum class AdminGestionTab {
    PRODUCTORES,
    RANCHOS,
    PARCELAS
}


internal data class DatosGestionAgricola(
    val productores: List<LocalAgroUnitEntity>,
    val ranchos: List<LocalRanchEntity>,
    val parcelas: List<LocalPlotEntity>,
    val vertices: List<LocalPlotVertexEntity>
)

internal data class ResultadoImportacionGestion(
    val insertados: Int,
    val omitidos: Int,
    val coordenadasDefault: Int = 0
)

internal fun LocalPlotEntity.nombreMostrarGestion(): String {
    return code?.takeIf { it.isNotBlank() } ?: name
}

internal fun String.normalizarClaveGestion(): String {
    val normalized = Normalizer.normalize(trim().lowercase(Locale.getDefault()), Normalizer.Form.NFD)
    return normalized
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]"), "")
}

internal sealed class ConfirmacionEliminarGestion(
    val mensaje: String
) {
    data class Productor(val productor: LocalAgroUnitEntity) : ConfirmacionEliminarGestion(
        "¿Seguro que deseas eliminar el productor \"${productor.commercial_name}\"? Solo se eliminará si no tiene ranchos, programas o monitoreos relacionados."
    )

    data class Rancho(val rancho: LocalRanchEntity) : ConfirmacionEliminarGestion(
        "¿Seguro que deseas eliminar el rancho \"${rancho.name}\"? Solo se eliminará si no tiene parcelas, programas o monitoreos relacionados."
    )

    data class Parcela(val parcela: LocalPlotEntity) : ConfirmacionEliminarGestion(
        "¿Seguro que deseas eliminar la parcela \"${parcela.nombreMostrarGestion()}\"? Solo se eliminará si no tiene programas o monitoreos relacionados."
    )
}

internal data class ImportedRanchCsv(
    val productorRef: String?,
    val name: String,
    val code: String,
    val lat: Double?,
    val lon: Double?
)

internal data class ImportedPlotCsv(
    val ranchoRef: String?,
    val code: String,
    val description: String?,
    val lat: Double?,
    val lon: Double?
)


internal data class ImportedPolygonPoint(
    val level: Int,
    val lat: Double,
    val lon: Double
)


internal fun generarSlugGestion(texto: String): String {
    val normalizado = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase(Locale.getDefault())
        .replace("[^a-z0-9]+".toRegex(), "-")
        .trim('-')

    return normalizado.ifBlank { "productor-${System.currentTimeMillis()}" }
}

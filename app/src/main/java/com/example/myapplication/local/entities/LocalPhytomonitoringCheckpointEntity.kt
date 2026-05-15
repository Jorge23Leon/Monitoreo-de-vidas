package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytomonitoring_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = LocalPhytomonitoringTargetPointEntity::class,
            parentColumns = ["idTargetPoint"],
            childColumns = ["idTargetPoint"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalPhytomonitoringHeaderEntity::class,
            parentColumns = ["idHeader"],
            childColumns = ["idHeader"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalPhytosanitaryCatalogEntity::class,
            parentColumns = ["idPhytosanitary"],
            childColumns = ["idPhytosanitary"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LocalPlotEntity::class,
            parentColumns = ["idLocalPlot"],
            childColumns = ["idLocalPlot"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idTargetPoint"]),
        Index(value = ["idHeader"]),
        Index(value = ["idPhytosanitary"]),
        Index(value = ["idLocalPlot"]),
        Index(value = ["captured_at"])
    ]
)
data class LocalPhytomonitoringCheckpointEntity(
    @PrimaryKey(autoGenerate = true)
    val idCheckpoint: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    // Cantidad encontrada:
    // insectos, daños, plantas afectadas, etc.
    val qty: Int? = null,

    // 0 = No presente
    // 1 = Presente
    @ColumnInfo(name = "presence_status")
    val presenceStatus: Int? = null,

    // Etapa o fase encontrada:
    // "Huevo", "Larva", "Adulto", "Inicial", "Avanzada", etc.
    val stage: String? = null,

    // Observaciones o notas del monitoreo
    val notes: String? = null,

    // Foto local o URL de la imagen
   //todo: val photo: String? = null,

    // Fecha y hora de captura en milisegundos
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long? = null,

    // Punto objetivo
    val idTargetPoint: Long,

    // Header / sesión de monitoreo
    val idHeader: Long,

    // Plaga o enfermedad registrada
    val idPhytosanitary: Long,

    // Parcela donde se registró
    val idLocalPlot: Long
)
package com.example.myapplication.local.entities

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

    val extId: String? = null,


    // Cantidad encontrada
    // Por ejemplo: cantidad de insectos, daños, plantas afectadas, etc.
    val qty: Int? = null,

    // Estado de presencia
    // Puede servir para indicar si hubo presencia o no
    // Ejemplo:
    // 0 = No presente
    // 1 = Presente
    val presence_status: Int? = null,

    // Etapa o fase encontrada
    // Ejemplo:
    // "Huevo", "Larva", "Adulto", "Inicial", "Avanzada"
    val stage: String? = null,

    // Fecha y hora en que se capturó el checkpoint
    val captured_at: Long? = null,


    // Indica a qué punto objetivo pertenece este registro
    val idTargetPoint: Long,

    // Indica a qué sesión o tarea de monitoreo pertenece
    val idHeader: Long,

    // Indica qué plaga o enfermedad se registró
    val idPhytosanitary: Long,

    // Indica en qué parcela se hizo el registro
    val idLocalPlot: Long
)
package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "local_cia_agro_units",
    primaryKeys = ["idLocalCia", "idLocalAgroUnit"],
    foreignKeys = [
        ForeignKey(
            entity = LocalCiaEntity::class,
            parentColumns = ["idLocalCia"],
            childColumns = ["idLocalCia"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalAgroUnitEntity::class,
            parentColumns = ["idLocalAgroUnit"],
            childColumns = ["idLocalAgroUnit"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idLocalCia"]),
        Index(value = ["idLocalAgroUnit"]),
        Index(value = ["ext_id"], unique = true)
    ]
)
data class LocalCiaAgroUnitCrossRef(
    val idLocalCia: Long,

    val idLocalAgroUnit: Long,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null
)
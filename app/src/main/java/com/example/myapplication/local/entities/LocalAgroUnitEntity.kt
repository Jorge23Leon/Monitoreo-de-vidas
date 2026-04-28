package com.example.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_agro_units",
    foreignKeys = [
        ForeignKey(
            entity = LocalCiaEntity::class,
            parentColumns = ["idLocalCia"],
            childColumns = ["idLocalCia"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idLocalCia"]),
        Index(value = ["code"])
    ]
)
data class LocalAgroUnitEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalAgroUnit: Long = 0,

    val extId: String? = null,
    val name: String,
    val code: String,
    val description: String? = null,

    val idLocalCia: Long
)
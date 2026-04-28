package com.example.myapplication.local.entities

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
        Index(value = ["slug"])
    ]
)
data class LocalAgroUnitEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalAgroUnit: Long = 0,
    val ext_Id: String? = null,
    val commercial_name: String,
    val slug: String,
    val idLocalCia: Long,
    val ext_IdLocalCia: String? = null,
)
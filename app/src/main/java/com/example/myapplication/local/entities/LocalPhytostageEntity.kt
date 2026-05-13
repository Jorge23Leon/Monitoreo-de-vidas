package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytostages",
    foreignKeys = [
        ForeignKey(
            entity = LocalPhytosanitaryCatalogEntity::class,
            parentColumns = ["idPhytosanitary"],
            childColumns = ["idPhytosanitary"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idPhytosanitary"]),
        Index(value = ["idPhytosanitary", "stage"], unique = true)
    ]
)
data class LocalPhytostageEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalPhytostage: Long = 0,

    val ext_id: String? = null,

    val stage: String,

    val photo: String? = null,

    val idPhytosanitary: Long
)
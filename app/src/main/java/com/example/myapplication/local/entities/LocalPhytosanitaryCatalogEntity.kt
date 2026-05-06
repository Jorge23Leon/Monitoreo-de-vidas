package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytosanitary_catalog",
    foreignKeys = [
        ForeignKey(
            entity = LocalCropCatalogEntity::class,
            parentColumns = ["idCrop"],
            childColumns = ["idDefaultCrop"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["name"]),
        Index(value = ["type"]),
        Index(value = ["idDefaultCrop"]),
        Index(value = ["idDefaultCrop", "name", "type"], unique = true)
    ]
)
data class LocalPhytosanitaryCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val idPhytosanitary: Long = 0,

    val ext_Id: String? = null,

    val name: String,
    val description: String? = null,

    // PLAGA o ENFERMEDAD
    val type: String,

    val min_ref_value: Int? = null,
    val max_ref_value: Int? = null,

    // FK opcional hacia local_crop_catalog
    val idDefaultCrop: Long? = null
)
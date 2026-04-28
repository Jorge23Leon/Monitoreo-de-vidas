package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_crop_catalog",
    indices = [
        Index(value = ["code"], unique = true)
    ]
)
data class LocalCropCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val idCrop: Long = 0,
    val ext_Id: String? = null,
    val name: String,
    val code: String,
    val description: String? = null,
    val photo: String? = null
)
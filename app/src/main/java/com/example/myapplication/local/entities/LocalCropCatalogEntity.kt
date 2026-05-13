package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_crop_catalog",
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["name"]),
        Index(value = ["variedad"])
    ]
)
data class LocalCropCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val idCrop: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val name: String,

    val variedad: String? = null,

    val description: String? = null,

    val photo: String? = null
)
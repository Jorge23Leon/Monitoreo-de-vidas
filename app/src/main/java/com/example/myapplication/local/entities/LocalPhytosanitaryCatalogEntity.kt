package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
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
        Index(value = ["ext_id"], unique = true),
        Index(value = ["name"]),
        Index(value = ["type"]),
        Index(value = ["idDefaultCrop"]),
        Index(value = ["idDefaultCrop", "name", "type"], unique = true)
    ]
)
data class LocalPhytosanitaryCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val idPhytosanitary: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val name: String,

    val type: String,

    @ColumnInfo(name = "min_ref_value")
    val minRefValue: Int? = null,

    @ColumnInfo(name = "max_ref_value")
    val maxRefValue: Int? = null,

    val description: String? = null,

    val photo: String? = null,

    val idDefaultCrop: Long? = null
)
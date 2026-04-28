package com.example.myapplication.local.entities


import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytosanitary_catalog",
    indices = [
        Index(value = ["name"]),
        Index(value = ["type"])
    ]
)
data class LocalPhytosanitaryCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val idPhytosanitary: Long = 0,

    val ext_Id: String? = null,
    val name: String,
    val description: String? = null,
    val type: String, // PLAGA o ENFERMEDAD
    val min_ref_value: Int? = null,
    val max_ref_value: Int? = null,

)
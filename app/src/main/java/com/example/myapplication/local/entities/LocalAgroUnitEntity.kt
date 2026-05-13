package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_agro_units",
    indices = [
        Index(value = ["ext_Id"], unique = true),
        Index(value = ["commercial_name"], unique = true),
        Index(value = ["slug"], unique = true)
    ]
)
data class LocalAgroUnitEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalAgroUnit: Long = 0,

    val ext_Id: String? = null,
    val commercial_name: String,
    val slug: String
)
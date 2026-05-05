package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_cias",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["ext_Id"], unique = true)
    ]
)
data class LocalCiaEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalCia: Long = 0,

    val ext_Id: String? = null,
    val nombre: String,
    val slug: String,
    val description: String? = null
)
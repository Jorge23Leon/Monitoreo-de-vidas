package com.example.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_cias",
    indices = [
        Index(value = ["slug"], unique = true)
    ]
)

data class LocalCiaEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalCia: Long = 0,
    val extId: String? = null,
    val nombre: String,
    val slug: String,
    val description: String? = null
)
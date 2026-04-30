package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_phytostages",
    indices = [
        Index(value = ["name"])
    ]
)
data class LocalPhytostageEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalPhytostage: Long = 0,
    val ext_id: String? = null,
    val name: String,
    val photo: String? = null
)
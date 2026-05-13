package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_cias",
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["nombre"], unique = true),
        Index(value = ["slug"], unique = true)
    ]
)
data class LocalCiaEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalCia: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val nombre: String,

    val slug: String,

    val description: String? = null
)
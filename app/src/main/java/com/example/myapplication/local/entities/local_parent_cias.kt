package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_parent_cias",
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["name"], unique = true),
        Index(value = ["slug"], unique = true)
    ]
)
data class LocalParentCiaEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "idParentCia")
    val idParentCia: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "slug")
    val slug: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "level")
    val level: Int = 1
)

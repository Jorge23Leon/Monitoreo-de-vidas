package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.local.entities.LocalCiaEntity

@Entity(
    tableName = "local_cias",
    foreignKeys = [
        ForeignKey(
            entity = LocalParentCiaEntity::class,
            parentColumns = ["idParentCia"],
            childColumns = ["idParentCia"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["name"], unique = true),
        Index(value = ["slug"], unique = true),
        Index(value = ["idParentCia"])
    ]
)
data class LocalCiaEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalCia: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    @ColumnInfo(name = "name")
    val nombre: String,

    val slug: String,

    val description: String? = null,

    val idParentCia: Long
)

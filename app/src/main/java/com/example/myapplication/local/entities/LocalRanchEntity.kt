package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_ranches",
    foreignKeys = [
        ForeignKey(
            entity = LocalAgroUnitEntity::class,
            parentColumns = ["idLocalAgroUnit"],
            childColumns = ["idLocalAgroUnit"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["idLocalAgroUnit"]),
        Index(value = ["idLocalAgroUnit", "code"], unique = true)
    ]
)
data class LocalRanchEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalRanch: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    val name: String,

    val code: String,

    val lat: Double? = null,

    val lon: Double? = null,

    val idLocalAgroUnit: Long
)
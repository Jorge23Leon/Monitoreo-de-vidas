package com.example.myapplication.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.local.entities.LocalAgroUnitEntity

@Entity(
    tableName = "local_ranches",
    foreignKeys = [
        ForeignKey(
            entity = LocalAgroUnitEntity::class,
            parentColumns = ["idLocalAgroUnit"],
            childColumns = ["idLocalAgroUnit"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idLocalAgroUnit"]),
        Index(value = ["code"])
    ]
)
data class LocalRanchEntity(
    @PrimaryKey(autoGenerate = true)
    val idLocalRanch: Long = 0,
    val ext_Id: String? = null,
    val name: String,
    val code: String,
    val lat: Double? ,
    val lon: Double?,
    val idLocalAgroUnit: Long,
    val ext_IdLocalAgroUnit: String? = null,

    )
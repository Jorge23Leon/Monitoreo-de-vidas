package com.example.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "user_local_cias",
    primaryKeys = ["idUser", "idLocalCia"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["idUser"],
            childColumns = ["idUser"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalCiaEntity::class,
            parentColumns = ["idLocalCia"],
            childColumns = ["idLocalCia"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idUser"]),
        Index(value = ["idLocalCia"])
    ]
)
data class UserLocalCiaCrossRef(
    val idUser: Long,
    val idLocalCia: Long
)
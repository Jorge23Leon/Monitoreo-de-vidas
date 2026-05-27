package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "user_local_parent_cias",
    primaryKeys = ["idUser", "idParentCia"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["idUser"],
            childColumns = ["idUser"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalParentCiaEntity::class,
            parentColumns = ["idParentCia"],
            childColumns = ["idParentCia"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idUser"]),
        Index(value = ["idParentCia"])
    ]
)
data class UserLocalParentCiaCrossRef(
    @ColumnInfo(name = "idUser")
    val idUser: Long,

    @ColumnInfo(name = "idParentCia")
    val idParentCia: Long
)

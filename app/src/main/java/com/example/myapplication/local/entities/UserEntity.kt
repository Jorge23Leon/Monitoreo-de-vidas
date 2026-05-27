package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = LocalRoleEntity::class,
            parentColumns = ["idRole"],
            childColumns = ["idRole"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["username"], unique = true),
        Index(value = ["email"], unique = true),
        Index(value = ["idRole"])
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val idUser: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    @ColumnInfo(name = "first_name")
    val firstName: String,

    @ColumnInfo(name = "last_name")
    val lastName: String? = null,

    val username: String,
    val email: String,
    val password: String,

    val idRole: Long
)
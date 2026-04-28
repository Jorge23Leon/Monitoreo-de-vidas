package com.example.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["email"], unique = true)
    ]
)

data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val idUser: Long = 0,
    val extId: String? = null,
    val first_name: String,
    val lastname: String? = null,
    val username: String,
    val email: String,
    val password: String,
    val role: String
)
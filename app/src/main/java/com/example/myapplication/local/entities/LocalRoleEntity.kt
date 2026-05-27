package com.example.myapplication.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_roles",
    indices = [
        Index(value = ["ext_id"], unique = true),
        Index(value = ["role_name"], unique = true),
        Index(value = ["level"])
    ]
)
data class LocalRoleEntity(
    @PrimaryKey(autoGenerate = true)
    val idRole: Long = 0,

    @ColumnInfo(name = "ext_id")
    val extId: String? = null,

    @ColumnInfo(name = "role_name")
    val roleName: String,

    val level: Int
)
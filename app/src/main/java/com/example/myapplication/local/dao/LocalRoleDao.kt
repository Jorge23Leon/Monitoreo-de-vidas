package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalRoleEntity

@Dao
interface LocalRoleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRole(role: LocalRoleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoles(roles: List<LocalRoleEntity>)

    @Query("SELECT * FROM local_roles ORDER BY level DESC")
    suspend fun getAllRoles(): List<LocalRoleEntity>

    @Query("SELECT * FROM local_roles WHERE idRole = :idRole LIMIT 1")
    suspend fun getRoleById(idRole: Long): LocalRoleEntity?

    @Query("""
        SELECT * FROM local_roles
        WHERE LOWER(TRIM(role_name)) = LOWER(TRIM(:roleName))
        LIMIT 1
    """)
    suspend fun getRoleByName(roleName: String): LocalRoleEntity?

    @Query("SELECT COUNT(*) FROM local_roles")
    suspend fun countRoles(): Int
}
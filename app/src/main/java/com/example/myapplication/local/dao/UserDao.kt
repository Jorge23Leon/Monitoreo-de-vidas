package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.myapplication.local.entities.UserEntity

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("SELECT * FROM users ORDER BY idUser ASC")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE idUser = :idUser LIMIT 1")
    suspend fun getUserById(idUser: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("""
        SELECT * FROM users
        WHERE TRIM(username) = TRIM(:username)
        AND TRIM(password) = TRIM(:password)
        AND LOWER(TRIM(role)) = LOWER(TRIM(:role))
        LIMIT 1
    """)
    suspend fun login(
        username: String,
        password: String,
        role: String
    ): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int
}
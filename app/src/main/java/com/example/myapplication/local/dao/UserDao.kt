package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.UserEntity
import com.example.myapplication.local.models.UsuarioSesion

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

    @Query("""
        SELECT * FROM users
        WHERE ext_id = :extId
        LIMIT 1
    """)
    suspend fun getUserByExtId(extId: String): UserEntity?

    @Query("""
        SELECT * FROM users
        WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username))
        LIMIT 1
    """)
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("""
        SELECT * FROM users
        WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email))
        LIMIT 1
    """)
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("""
    SELECT 
        u.idUser AS idUser,
        u.ext_id AS extId,
        u.first_name AS firstName,
        u.last_name AS lastName,
        u.username AS username,
        u.email AS email,

        r.idRole AS idRole,
        r.role_name AS roleName,
        r.level AS level
    FROM users AS u
    INNER JOIN local_roles AS r
        ON r.idRole = u.idRole
    WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))
      AND TRIM(u.password) = TRIM(:password)
    LIMIT 1
""")
    suspend fun loginConRol(
        username: String,
        password: String
    ): UsuarioSesion?


    @Query(
        """
    SELECT 
        u.idUser AS idUser,
        u.ext_id AS extId,
        u.first_name AS firstName,
        u.last_name AS lastName,
        u.username AS username,
        u.email AS email,

        r.idRole AS idRole,
        r.role_name AS roleName,
        r.level AS level
    FROM users u
    INNER JOIN local_roles r
        ON r.idRole = u.idRole
    WHERE u.idUser = :idUser
    LIMIT 1
    """
    )
    suspend fun getSesionByIdUser(
        idUser: Long
    ): UsuarioSesion?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("""
        SELECT COUNT(*) FROM users
        WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username))
    """)
    suspend fun countByUsername(username: String): Int

    @Query("""
        SELECT COUNT(*) FROM users
        WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email))
    """)
    suspend fun countByEmail(email: String): Int

    @Query("""
        SELECT COUNT(*)
        FROM users AS u
        INNER JOIN local_roles AS r
            ON r.idRole = u.idRole
        WHERE LOWER(TRIM(r.role_name)) = LOWER(TRIM(:roleName))
    """)
    suspend fun countByRole(roleName: String): Int

    @Query("""
        SELECT COUNT(*)
        FROM users
        WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username))
          AND idUser != :idUser
    """)
    suspend fun existeUsernameEnOtroUsuario(
        username: String,
        idUser: Long
    ): Int

    @Query("""
        SELECT COUNT(*)
        FROM users
        WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email))
          AND idUser != :idUser
    """)
    suspend fun existeEmailEnOtroUsuario(
        email: String,
        idUser: Long
    ): Int

    @Query("""
        UPDATE users
        SET first_name = :firstName,
            last_name = :lastName,
            username = :username,
            email = :email,
            password = :password
        WHERE idUser = :idUser
    """)
    suspend fun actualizarPerfilUsuarioSinRol(
        idUser: Long,
        firstName: String,
        lastName: String,
        username: String,
        email: String,
        password: String
    ): Int
}
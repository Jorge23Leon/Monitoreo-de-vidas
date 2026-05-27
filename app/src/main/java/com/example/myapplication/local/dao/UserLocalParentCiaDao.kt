package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity
import com.example.myapplication.local.entities.UserLocalParentCiaCrossRef

@Dao
interface UserLocalParentCiaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun asignarParentCiaAUsuario(ref: UserLocalParentCiaCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun asignarParentCiasAUsuario(refs: List<UserLocalParentCiaCrossRef>)

    @Query("""
        SELECT p.*
        FROM local_parent_cias AS p
        INNER JOIN user_local_parent_cias AS up
            ON up.idParentCia = p.idParentCia
        WHERE up.idUser = :idUser
        ORDER BY p.name ASC
    """)
    suspend fun getParentCiasByUser(idUser: Long): List<LocalParentCiaEntity>

    @Query("""
        SELECT c.*
        FROM local_cias AS c
        INNER JOIN user_local_parent_cias AS up
            ON up.idParentCia = c.idParentCia
        WHERE up.idUser = :idUser
        ORDER BY c.name ASC
    """)
    suspend fun getCiasHijasByUser(idUser: Long): List<LocalCiaEntity>

    @Query("DELETE FROM user_local_parent_cias WHERE idUser = :idUser AND idParentCia = :idParentCia")
    suspend fun quitarParentCiaAUsuario(idUser: Long, idParentCia: Long)

    @Query("DELETE FROM user_local_parent_cias WHERE idUser = :idUser")
    suspend fun quitarTodasLasParentCiasDelUsuario(idUser: Long)
}

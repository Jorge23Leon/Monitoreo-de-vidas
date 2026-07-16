package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalCiaEntity
import com.example.myapplication.local.entities.LocalParentCiaEntity
import com.example.myapplication.local.entities.UserLocalCiaCrossRef

@Dao
interface UserLocalCiaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun asignarCiaAUsuario(ref: UserLocalCiaCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun asignarCiasAUsuario(refs: List<UserLocalCiaCrossRef>)

    @Query("""
        SELECT DISTINCT p.*
        FROM local_parent_cias AS p
        INNER JOIN local_cias AS c
            ON c.idParentCia = p.idParentCia
        INNER JOIN user_local_cias AS uc
            ON uc.idLocalCia = c.idLocalCia
        WHERE uc.idUser = :idUser
        ORDER BY p.name ASC
    """)
    suspend fun getParentCiasByUser(idUser: Long): List<LocalParentCiaEntity>

    @Query("""
        SELECT c.*
        FROM local_cias AS c
        INNER JOIN user_local_cias AS uc
            ON uc.idLocalCia = c.idLocalCia
        WHERE uc.idUser = :idUser
        ORDER BY c.name ASC
    """)
    suspend fun getCiasByUser(idUser: Long): List<LocalCiaEntity>

    @Query("""
        SELECT c.*
        FROM local_cias AS c
        INNER JOIN user_local_cias AS uc
            ON uc.idLocalCia = c.idLocalCia
        WHERE uc.idUser = :idUser
          AND c.idParentCia = :idParentCia
        ORDER BY c.name ASC
    """)
    suspend fun getCiasByUserAndParent(
        idUser: Long,
        idParentCia: Long
    ): List<LocalCiaEntity>

    @Query("DELETE FROM user_local_cias WHERE idUser = :idUser")
    suspend fun quitarTodasLasCiasDelUsuario(idUser: Long)
}
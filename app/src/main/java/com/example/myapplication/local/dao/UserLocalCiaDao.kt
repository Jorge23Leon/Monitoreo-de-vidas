package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.myapplication.local.entities.UserLocalCiaCrossRef

@Dao
interface UserLocalCiaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUserLocalCia(relation: UserLocalCiaCrossRef)

    @Query("""
        SELECT * FROM user_local_cias
        WHERE idUser = :idUser
    """)
    suspend fun getCiasByUser(idUser: Long): List<UserLocalCiaCrossRef>

    @Query("""
        SELECT * FROM user_local_cias
        WHERE idLocalCia = :idLocalCia
    """)
    suspend fun getUsersByCia(idLocalCia: Long): List<UserLocalCiaCrossRef>

    @Query("""
        DELETE FROM user_local_cias
        WHERE idUser = :idUser AND idLocalCia = :idLocalCia
    """)
    suspend fun deleteUserLocalCia(idUser: Long, idLocalCia: Long)

    @Delete
    suspend fun deleteRelation(relation: UserLocalCiaCrossRef)
}
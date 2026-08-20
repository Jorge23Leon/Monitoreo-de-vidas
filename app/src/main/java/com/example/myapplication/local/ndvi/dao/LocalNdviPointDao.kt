package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.local.entities.LocalNdviPointEntity
import kotlinx.coroutines.flow.Flow


/**
 * Acceso local a los puntos georreferenciados NDVI.
 */
@Dao
interface LocalNdviPointDao {


    // =====================================================
    // OBSERVAR
    // =====================================================

    /**
     * Observa los puntos de una sesión.
     *
     * Cuando Room cambie esos puntos,
     * la pantalla que observe este Flow podrá actualizarse.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_points
        WHERE session_id = :sessionId
        ORDER BY point_id
        """
    )
    fun observePointsBySession(
        sessionId: String
    ): Flow<List<LocalNdviPointEntity>>


    // =====================================================
    // CONSULTAR
    // =====================================================

    /**
     * Obtiene todos los puntos de una sesión.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_points
        WHERE session_id = :sessionId
        ORDER BY point_id
        """
    )
    suspend fun getPointsBySession(
        sessionId: String
    ): List<LocalNdviPointEntity>


    /**
     * Obtiene un punto específico.
     */
    @Query(
        """
        SELECT *
        FROM local_ndvi_points
        WHERE point_id = :pointId
        LIMIT 1
        """
    )
    suspend fun getPointById(
        pointId: String
    ): LocalNdviPointEntity?


    /**
     * Cuenta cuántos puntos tenemos realmente
     * guardados para una sesión.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM local_ndvi_points
        WHERE session_id = :sessionId
        """
    )
    suspend fun countPointsBySession(
        sessionId: String
    ): Int


    // =====================================================
    // GUARDAR
    // =====================================================

    /**
     * Los puntos vienen del backend y su UUID
     * identifica de forma única cada registro.
     *
     * IGNORE evita duplicarlos.
     */
    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertPoint(
        point: LocalNdviPointEntity
    ): Long


    /**
     * Inserta muchos puntos de una sola vez.
     *
     * Esto es MUCHO más eficiente que hacer:
     *
     * for (...) {
     *     insertPoint(...)
     * }
     */
    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertPoints(
        points: List<LocalNdviPointEntity>
    ): List<Long>


    // =====================================================
    // ELIMINAR
    // =====================================================

    /**
     * Borra exclusivamente los puntos
     * pertenecientes a una sesión.
     *
     * Se utilizará cuando queramos refrescar
     * completamente una sesión NDVI.
     */
    @Query(
        """
        DELETE FROM local_ndvi_points
        WHERE session_id = :sessionId
        """
    )
    suspend fun deletePointsBySession(
        sessionId: String
    ): Int


    /**
     * Borrado total de la caché de puntos NDVI.
     *
     * No lo usaremos durante una sincronización normal.
     */
    @Query(
        """
        DELETE FROM local_ndvi_points
        """
    )
    suspend fun deleteAllPoints(): Int
}
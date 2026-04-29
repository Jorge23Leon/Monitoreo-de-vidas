package com.example.myapplication.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.local.entities.LocalPlotVertexEntity

@Dao
interface LocalPlotVertexDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlotVertex(vertex: LocalPlotVertexEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlotVertices(vertices: List<LocalPlotVertexEntity>)

    @Update
    suspend fun updatePlotVertex(vertex: LocalPlotVertexEntity)

    @Delete
    suspend fun deletePlotVertex(vertex: LocalPlotVertexEntity)

    @Query("SELECT * FROM local_plot_vertexes ORDER BY idLocalPlotVertex ASC")
    suspend fun getAllPlotVertices(): List<LocalPlotVertexEntity>

    @Query("SELECT * FROM local_plot_vertexes WHERE idLocalPlotVertex = :idLocalPlotVertex LIMIT 1")
    suspend fun getPlotVertexById(idLocalPlotVertex: Long): LocalPlotVertexEntity?

    @Query("""
        SELECT * FROM local_plot_vertexes
        WHERE idLocalPlot = :idLocalPlot
        ORDER BY level ASC
    """)
    suspend fun getVerticesByPlot(idLocalPlot: Long): List<LocalPlotVertexEntity>

    @Query("""
        SELECT COUNT(*) FROM local_plot_vertexes
        WHERE idLocalPlot = :idLocalPlot
    """)
    suspend fun countVerticesByPlot(idLocalPlot: Long): Int

    @Query("""
        DELETE FROM local_plot_vertexes
        WHERE idLocalPlot = :idLocalPlot
    """)
    suspend fun deleteVerticesByPlot(idLocalPlot: Long)

    @Query("DELETE FROM local_plot_vertexes")
    suspend fun deleteAllPlotVertices()
}
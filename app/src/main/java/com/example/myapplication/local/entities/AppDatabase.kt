package com.example.myapplication.local.entities

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.local.dao.UserDao

@Database(
    entities = [
        UserEntity::class,
        LocalCiaEntity::class,
        UserLocalCiaCrossRef::class,
        LocalAgroUnitEntity::class,
        LocalRanchEntity::class,
        LocalCropCatalogEntity::class,
        LocalPlotEntity::class,
        LocalPlotVertexEntity::class,
        LocalProgramEntity::class,
        LocalPhytomonitoringHeaderEntity::class,
        LocalPhytomonitoringTargetPointEntity::class,
        LocalPhytosanitaryCatalogEntity::class,
        LocalPhytostageEntity::class,
        LocalPhytomonitoringCheckpointEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "MonitoreosPlagas.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
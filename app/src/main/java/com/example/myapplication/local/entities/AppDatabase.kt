package com.example.myapplication.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.local.entities.*

@Database(
    entities = [
        UserEntity::class,
        LocalCiaEntity::class,
        UserLocalCiaCrossRef::class,
        LocalAgroUnitEntity::class,
        LocalRanchEntity::class,
        LocalCropCatalogEntity::class,
        LocalPlotEntity::class,
        LocalProgramEntity::class,
        LocalPhytomonitoringHeaderEntity::class,
        LocalPhytomonitoringTargetPointEntity::class,
        LocalPhytosanitaryCatalogEntity::class,
        LocalPhytostageEntity::class,
        LocalPhytomonitoringCheckpointEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

   // abstract fun generalDao(): GeneralDao

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
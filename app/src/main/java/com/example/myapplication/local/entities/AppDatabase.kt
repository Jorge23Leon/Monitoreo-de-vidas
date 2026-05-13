package com.example.myapplication.local.entities

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.local.dao.UserDao
import com.example.myapplication.local.dao.LocalCiaDao
import com.example.myapplication.local.dao.LocalAgroUnitDao
import com.example.myapplication.local.dao.LocalCiaAgroUnitDao
import com.example.myapplication.local.dao.LocalRanchDao
import com.example.myapplication.local.dao.LocalPlotDao
import com.example.myapplication.local.dao.LocalCropCatalogDao
import com.example.myapplication.local.dao.LocalPlotVertexDao
import com.example.myapplication.local.dao.LocalPhytomonitoringHeaderDao
import com.example.myapplication.local.dao.LocalProgramDao
import com.example.myapplication.local.dao.LocalPhytosanitaryCatalogDao
import com.example.myapplication.local.dao.LocalPhytostageDao
import com.example.myapplication.local.dao.LocalPhytomonitoringCheckpointDao
import com.example.myapplication.local.dao.UserLocalCiaDao
import com.example.myapplication.local.dao.LocalPhytomonitoringTargetPointDao




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
        LocalPhytomonitoringCheckpointEntity::class,
        LocalCiaAgroUnitCrossRef::class

    ],
    version = 15,
    exportSchema = false
)
abstract class
AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun localCiaDao(): LocalCiaDao
    abstract fun localAgroUnitDao(): LocalAgroUnitDao
    abstract fun localRanchDao(): LocalRanchDao
    abstract fun localPlotDao(): LocalPlotDao
    abstract fun localCropCatalogDao(): LocalCropCatalogDao
    abstract fun LocalPlotVertexDao(): LocalPlotVertexDao
    abstract fun localphytomonitoringheaderDao(): LocalPhytomonitoringHeaderDao
    abstract fun localprogramDao(): LocalProgramDao
    abstract fun localphytosanitarycatalogDao(): LocalPhytosanitaryCatalogDao
    abstract fun localphytostageDao(): LocalPhytostageDao
    abstract fun localphytomonitoringcheckpointDao(): LocalPhytomonitoringCheckpointDao
    abstract fun UserLocalCiaDao(): UserLocalCiaDao
    abstract fun LocalPhytomonitoringTargetPointDao(): LocalPhytomonitoringTargetPointDao
    abstract fun localCiaAgroUnitDao(): LocalCiaAgroUnitDao



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
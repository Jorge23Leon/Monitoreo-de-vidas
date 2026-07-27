package com.example.myapplication.local.entities

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.local.dao.LocalAgroUnitDao
import com.example.myapplication.local.dao.LocalCiaAgroUnitDao
import com.example.myapplication.local.dao.LocalCiaDao
import com.example.myapplication.local.dao.LocalCropCatalogDao
import com.example.myapplication.local.dao.LocalParentCiaDao
import com.example.myapplication.local.dao.LocalPhytomonitoringCheckpointDao
import com.example.myapplication.local.dao.LocalPhytomonitoringHeaderDao
import com.example.myapplication.local.dao.LocalPhytomonitoringTargetPointDao
import com.example.myapplication.local.dao.LocalPhytosanitaryCatalogDao
import com.example.myapplication.local.dao.LocalPhytostageDao
import com.example.myapplication.local.dao.LocalPlotDao
import com.example.myapplication.local.dao.LocalPlotVertexDao
import com.example.myapplication.local.dao.LocalProgramDao
import com.example.myapplication.local.dao.LocalRanchDao
import com.example.myapplication.local.dao.LocalRoleDao
import com.example.myapplication.local.dao.UserDao
import com.example.myapplication.local.dao.UserLocalCiaDao
import com.example.myapplication.local.dao.UserLocalParentCiaDao

@Database(
    entities = [

        LocalRoleEntity::class,
        UserEntity::class,
        LocalParentCiaEntity::class,
        LocalCiaEntity::class,
        UserLocalParentCiaCrossRef::class,
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
        UserLocalCiaCrossRef::class,
        LocalCiaAgroUnitCrossRef::class
    ],
    version = 36,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun localRoleDao(): LocalRoleDao
    abstract fun localParentCiaDao(): LocalParentCiaDao
    abstract fun userLocalParentCiaDao(): UserLocalParentCiaDao
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
    abstract fun LocalPhytomonitoringTargetPointDao(): LocalPhytomonitoringTargetPointDao
    abstract fun localCiaAgroUnitDao(): LocalCiaAgroUnitDao
    abstract fun userLocalCiaDao(): UserLocalCiaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE local_phytomonitoring_headers " +
                            "ADD COLUMN sync_pending INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE local_phytomonitoring_headers
                    SET sync_pending = 1
                    WHERE UPPER(TRIM(additional_notes)) LIKE 'PAUSADO%'
                       OR EXISTS (
                            SELECT 1
                            FROM local_phytomonitoring_target_points tp
                            WHERE tp.idHeader = local_phytomonitoring_headers.idHeader
                              AND (tp.ext_id IS NULL OR TRIM(tp.ext_id) = '')
                       )
                       OR EXISTS (
                            SELECT 1
                            FROM local_phytomonitoring_checkpoints cp
                            WHERE cp.idHeader = local_phytomonitoring_headers.idHeader
                              AND (
                                   cp.ext_id IS NULL OR TRIM(cp.ext_id) = ''
                                   OR LOWER(COALESCE(cp.photo_local_path, '')) LIKE '%pending%'
                              )
                       )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "index_local_phytomonitoring_headers_sync_pending " +
                            "ON local_phytomonitoring_headers(sync_pending)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "index_local_phytomonitoring_headers_est_start_date " +
                            "ON local_phytomonitoring_headers(est_start_date)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "MonitoreosPlagas.db"
                )
                    .addMigrations(MIGRATION_35_36)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

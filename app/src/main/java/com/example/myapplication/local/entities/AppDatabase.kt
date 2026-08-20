package com.example.myapplication.local.entities

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.local.dao.LocalAgroUnitDao
import com.example.myapplication.local.dao.LocalAspersionPointDao
import com.example.myapplication.local.dao.LocalAspersionSessionDao
import com.example.myapplication.local.dao.LocalAspersionStatsDao
import com.example.myapplication.local.dao.LocalAspersionVariableStatDao
import com.example.myapplication.local.dao.LocalCiaAgroUnitDao
import com.example.myapplication.local.dao.LocalCiaDao
import com.example.myapplication.local.dao.LocalCropCatalogDao
import com.example.myapplication.local.dao.LocalNdviPointDao
import com.example.myapplication.local.dao.LocalNdviSessionDao
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
        LocalCiaAgroUnitCrossRef::class,

        // =====================================================
        // ASPERSIÓN
        // =====================================================

        LocalAspersionSessionEntity::class,
        LocalAspersionPointEntity::class,
        LocalAspersionStatsEntity::class,
        LocalAspersionVariableStatEntity::class,

        // =====================================================
        // NDVI
        // =====================================================

        LocalNdviSessionEntity::class,
        LocalNdviPointEntity::class
    ],

    version = 39,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {


    // =========================================================
    // DAO EXISTENTES
    // =========================================================

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


    // =========================================================
    // DAO ASPERSIÓN
    // =========================================================

    abstract fun localAspersionSessionDao(): LocalAspersionSessionDao
    abstract fun localAspersionPointDao(): LocalAspersionPointDao
    abstract fun localAspersionStatsDao(): LocalAspersionStatsDao
    abstract fun localAspersionVariableStatDao(): LocalAspersionVariableStatDao


    // =========================================================
    // DAO NDVI
    // =========================================================

    abstract fun localNdviSessionDao(): LocalNdviSessionDao
    abstract fun localNdviPointDao(): LocalNdviPointDao


    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null


        // =====================================================
        // MIGRACIÓN 35 -> 36
        // =====================================================

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


        // =====================================================
        // MIGRACIÓN 36 -> 37
        // ASPERSIÓN
        // =====================================================

        /**
         * Agrega el caché local del módulo de aspersión sin eliminar ni
         * transformar ninguna tabla fitosanitaria existente.
         */
        private val MIGRATION_36_37 = object : Migration(36, 37) {

            override fun migrate(db: SupportSQLiteDatabase) {

                // =================================================
                // SESIONES ASPERSIÓN
                // =================================================

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_aspersion_sessions` (
                        `session_id` TEXT NOT NULL,
                        `program_id` TEXT,
                        `plot_id` TEXT,
                        `evaluation_id` TEXT,
                        `aspersion_date` TEXT,
                        `status` TEXT,
                        `assigned_to_id` TEXT,
                        `assigned_to_username` TEXT,
                        `est_start_date` TEXT,
                        `est_finish_date` TEXT,
                        `act_start_date` TEXT,
                        `act_finish_date` TEXT,
                        `import_status` TEXT,
                        `import_errors_json` TEXT,
                        `imported_at` TEXT,
                        `last_template_id` TEXT,
                        `points_count` INTEGER,
                        `created_at` TEXT,
                        `updated_at` TEXT,
                        `downloaded_points_count` INTEGER NOT NULL DEFAULT 0,
                        `points_download_complete` INTEGER NOT NULL DEFAULT 0,
                        `last_synced_at` INTEGER,
                        PRIMARY KEY(`session_id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_sessions_program_id` " +
                            "ON `local_aspersion_sessions` (`program_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_sessions_plot_id` " +
                            "ON `local_aspersion_sessions` (`plot_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_sessions_assigned_to_id` " +
                            "ON `local_aspersion_sessions` (`assigned_to_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_sessions_aspersion_date` " +
                            "ON `local_aspersion_sessions` (`aspersion_date`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_sessions_status` " +
                            "ON `local_aspersion_sessions` (`status`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_sessions_import_status` " +
                            "ON `local_aspersion_sessions` (`import_status`)"
                )


                // =================================================
                // PUNTOS ASPERSIÓN
                // =================================================

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_aspersion_points` (
                        `point_id` TEXT NOT NULL,
                        `session_id` TEXT NOT NULL,
                        `plot_id` TEXT,
                        `geom_type` TEXT,
                        `longitude` REAL NOT NULL,
                        `latitude` REAL NOT NULL,
                        `timestamp` TEXT,
                        `pass_number` INTEGER,
                        `elevation_m` REAL,
                        `course_deg` REAL,
                        `vehicle_heading` REAL,
                        `distance_m` REAL,
                        `duration_s` INTEGER,
                        `speed_kmh` REAL,
                        `satellites` INTEGER,
                        `gnss_hdop` REAL,
                        `gnss_vdop` REAL,
                        `gnss_pdop` REAL,
                        `is_diff_active` INTEGER,
                        `diff_mode` TEXT,
                        `xte_implement` REAL,
                        `xte_vehicle` REAL,
                        `is_steering_active` INTEGER,
                        `is_area_counting` INTEGER,
                        `active_rows` INTEGER,
                        `app_status` TEXT,
                        `boom_width_m` REAL,
                        `liquid_flow_ls` REAL,
                        `boom_pressure_bar` REAL,
                        `press_ag_kpa` REAL,
                        `press_aux_kpa` REAL,
                        `production_hah` REAL,
                        `droplet_size` TEXT,
                        `nozzle_color` TEXT,
                        `nozzle_capacity` REAL,
                        `nozzle_ref_pressure` REAL,
                        `target_rate_l` REAL,
                        `applied_rate_l` REAL,
                        `product_quantity` REAL,
                        `area_ha` REAL,
                        `rate_quality` TEXT,
                        `evaluation_id` TEXT,
                        `extra_raw_json` TEXT,
                        `created_at` TEXT,
                        PRIMARY KEY(`point_id`),
                        FOREIGN KEY(`session_id`)
                            REFERENCES `local_aspersion_sessions`(`session_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_points_session_id` " +
                            "ON `local_aspersion_points` (`session_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_points_plot_id` " +
                            "ON `local_aspersion_points` (`plot_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_points_timestamp` " +
                            "ON `local_aspersion_points` (`timestamp`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_points_rate_quality` " +
                            "ON `local_aspersion_points` (`rate_quality`)"
                )


                // =================================================
                // ESTADÍSTICAS ASPERSIÓN
                // =================================================

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_aspersion_stats` (
                        `session_id` TEXT NOT NULL,
                        `plot_id` TEXT,
                        `points_count` INTEGER,
                        `area_total_ha` REAL,
                        `mean_target_l` REAL,
                        `mean_applied_l` REAL,
                        `ratio_applied` REAL,
                        `pct_below` REAL,
                        `pct_in_range` REAL,
                        `pct_above` REAL,
                        `last_refresh` TEXT,
                        `cached_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`session_id`),
                        FOREIGN KEY(`session_id`)
                            REFERENCES `local_aspersion_sessions`(`session_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_stats_plot_id` " +
                            "ON `local_aspersion_stats` (`plot_id`)"
                )


                // =================================================
                // ESTADÍSTICAS POR VARIABLE
                // =================================================

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_aspersion_variable_stats` (
                        `session_id` TEXT NOT NULL,
                        `variable_key` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `count` INTEGER NOT NULL,
                        `mean_value` REAL,
                        `min_value` REAL,
                        `max_value` REAL,
                        `stddev` REAL,
                        `cached_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`session_id`, `variable_key`),
                        FOREIGN KEY(`session_id`)
                            REFERENCES `local_aspersion_sessions`(`session_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_aspersion_variable_stats_variable_key` " +
                            "ON `local_aspersion_variable_stats` (`variable_key`)"
                )
            }
        }


        // =====================================================
        // MIGRACIÓN 37 -> 38
        // CONTEXTO ASPERSIÓN
        // =====================================================

        /**
         * Guarda el contexto productor -> rancho -> parcela -> programa de la
         * sesión. Así Aspersión conserva sus filtros aun sin abrir Monitoreos.
         */
        private val MIGRATION_37_38 = object : Migration(37, 38) {

            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN program_name TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN program_cycle TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN plot_name TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN plot_code TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN ranch_id TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN ranch_name TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN producer_id TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN producer_name TEXT"
                )

                db.execSQL(
                    "ALTER TABLE local_aspersion_sessions " +
                            "ADD COLUMN data_central_ids TEXT"
                )
            }
        }


        // =====================================================
        // MIGRACIÓN 38 -> 39
        // NDVI
        // =====================================================

        /**
         * Agrega el almacenamiento local del módulo NDVI.
         *
         * Crea:
         *
         * - local_ndvi_sessions
         * - local_ndvi_points
         *
         * No elimina ni modifica datos existentes de
         * Monitoreo o Aspersión.
         */
        private val MIGRATION_38_39 = object : Migration(38, 39) {

            override fun migrate(db: SupportSQLiteDatabase) {


                // =================================================
                // SESIONES NDVI
                // =================================================

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_ndvi_sessions` (
                        `session_id` TEXT NOT NULL,
                        `program_id` TEXT,
                        `plot_id` TEXT,
                        `session_date` TEXT,
                        `status` TEXT,
                        `assigned_to_id` TEXT,
                        `assigned_to_username` TEXT,
                        `est_start_date` TEXT,
                        `est_finish_date` TEXT,
                        `act_start_date` TEXT,
                        `act_finish_date` TEXT,
                        `import_status` TEXT,
                        `import_errors_json` TEXT,
                        `imported_at` TEXT,
                        `points_count` INTEGER,
                        `contour_status` TEXT,
                        `created_at` TEXT,
                        `updated_at` TEXT,
                        `downloaded_points_count` INTEGER NOT NULL DEFAULT 0,
                        `points_download_complete` INTEGER NOT NULL DEFAULT 0,
                        `last_synced_at` INTEGER,
                        PRIMARY KEY(`session_id`)
                    )
                    """.trimIndent()
                )


                // -------------------------------------------------
                // ÍNDICES SESIONES NDVI
                // -------------------------------------------------

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_sessions_program_id` " +
                            "ON `local_ndvi_sessions` (`program_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_sessions_plot_id` " +
                            "ON `local_ndvi_sessions` (`plot_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_sessions_assigned_to_id` " +
                            "ON `local_ndvi_sessions` (`assigned_to_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_sessions_session_date` " +
                            "ON `local_ndvi_sessions` (`session_date`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_sessions_status` " +
                            "ON `local_ndvi_sessions` (`status`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_sessions_import_status` " +
                            "ON `local_ndvi_sessions` (`import_status`)"
                )


                // =================================================
                // PUNTOS NDVI
                // =================================================

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_ndvi_points` (
                        `point_id` TEXT NOT NULL,
                        `session_id` TEXT NOT NULL,
                        `geom_type` TEXT,
                        `longitude` REAL NOT NULL,
                        `latitude` REAL NOT NULL,
                        `obj_id` INTEGER,
                        `lot_label` TEXT,
                        `dataset_date` TEXT,
                        `product` TEXT,
                        `ndvi` REAL,
                        `nir_vigor` REAL,
                        `osavi` REAL,
                        `vari` REAL,
                        `bare_soil_index` REAL,
                        `image_red` REAL,
                        `image_green` REAL,
                        `image_blue` REAL,
                        `red_edge` REAL,
                        `swir` REAL,
                        `ndre` REAL,
                        `msavi2` REAL,
                        `gndvi` REAL,
                        `ndmi` REAL,
                        `psri` REAL,
                        `created_at` TEXT,
                        PRIMARY KEY(`point_id`),
                        FOREIGN KEY(`session_id`)
                            REFERENCES `local_ndvi_sessions`(`session_id`)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )


                // -------------------------------------------------
                // ÍNDICES PUNTOS NDVI
                // -------------------------------------------------

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_points_session_id` " +
                            "ON `local_ndvi_points` (`session_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_points_obj_id` " +
                            "ON `local_ndvi_points` (`obj_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_local_ndvi_points_dataset_date` " +
                            "ON `local_ndvi_points` (`dataset_date`)"
                )
            }
        }


        // =====================================================
        // OBTENER BASE DE DATOS
        // =====================================================

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "MonitoreosPlagas.db"
                )
                    .addMigrations(
                        MIGRATION_35_36,
                        MIGRATION_36_37,
                        MIGRATION_37_38,
                        MIGRATION_38_39
                    )
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
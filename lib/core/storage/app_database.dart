import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

class AppDatabase {
  AppDatabase._();
  static final instance = AppDatabase._();
  Database? _db;

  static const version = 39;

  Future<Database> get database async {
    if (_db != null) return _db!;
    final root = await getDatabasesPath();
    final path = p.join(root, 'monitoreo_ciagro_flutter.db');
    _db = await openDatabase(
      path,
      version: version,
      onConfigure: (db) async => db.execute('PRAGMA foreign_keys = ON'),
      onCreate: (db, _) async {
        for (final sql in _schemaSql) {
          await db.execute(sql);
        }
        await db.execute('CREATE TABLE IF NOT EXISTS app_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT)');
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        // Esta migración Flutter nace directamente con el esquema Room v39.
        // Si se instala sobre una versión Flutter anterior, aquí se agregan
        // migraciones incrementales sin borrar datos del usuario.
        await db.execute('CREATE TABLE IF NOT EXISTS app_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT)');
      },
    );
    return _db!;
  }

  Future<void> setMeta(String key, String? value) async {
    final db = await database;
    if (value == null) {
      await db.delete('app_meta', where: 'meta_key = ?', whereArgs: [key]);
      return;
    }
    await db.insert('app_meta', {'meta_key': key, 'meta_value': value}, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<String?> getMeta(String key) async {
    final db = await database;
    final rows = await db.query('app_meta', columns: ['meta_value'], where: 'meta_key = ?', whereArgs: [key], limit: 1);
    return rows.isEmpty ? null : rows.first['meta_value'] as String?;
  }

  static const List<String> _schemaSql = [
    r'''CREATE TABLE IF NOT EXISTS "local_roles" ("idRole" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "role_name" TEXT NOT NULL, "level" INTEGER NOT NULL)''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_roles_ext_id" ON "local_roles" ("ext_id")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_roles_role_name" ON "local_roles" ("role_name")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_roles_level" ON "local_roles" ("level")''',
    r'''CREATE TABLE IF NOT EXISTS "users" ("idUser" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "first_name" TEXT NOT NULL, "last_name" TEXT, "username" TEXT NOT NULL, "email" TEXT NOT NULL, "password" TEXT NOT NULL, "idRole" INTEGER NOT NULL, FOREIGN KEY("idRole") REFERENCES "local_roles"("idRole") ON UPDATE NO ACTION ON DELETE RESTRICT )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_users_ext_id" ON "users" ("ext_id")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_users_username" ON "users" ("username")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_users_email" ON "users" ("email")''',
    r'''CREATE INDEX IF NOT EXISTS "index_users_idRole" ON "users" ("idRole")''',
    r'''CREATE TABLE IF NOT EXISTS "local_parent_cias" ("idParentCia" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "name" TEXT NOT NULL, "slug" TEXT NOT NULL, "description" TEXT, "level" INTEGER NOT NULL)''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_parent_cias_ext_id" ON "local_parent_cias" ("ext_id")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_parent_cias_name" ON "local_parent_cias" ("name")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_parent_cias_slug" ON "local_parent_cias" ("slug")''',
    r'''CREATE TABLE IF NOT EXISTS "local_cias" ("idLocalCia" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "name" TEXT NOT NULL, "slug" TEXT NOT NULL, "description" TEXT, "idParentCia" INTEGER NOT NULL, FOREIGN KEY("idParentCia") REFERENCES "local_parent_cias"("idParentCia") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_cias_ext_id" ON "local_cias" ("ext_id")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_cias_name" ON "local_cias" ("name")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_cias_slug" ON "local_cias" ("slug")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_cias_idParentCia" ON "local_cias" ("idParentCia")''',
    r'''CREATE TABLE IF NOT EXISTS "user_local_parent_cias" ("idUser" INTEGER NOT NULL, "idParentCia" INTEGER NOT NULL, PRIMARY KEY("idUser", "idParentCia"), FOREIGN KEY("idUser") REFERENCES "users"("idUser") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idParentCia") REFERENCES "local_parent_cias"("idParentCia") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE INDEX IF NOT EXISTS "index_user_local_parent_cias_idUser" ON "user_local_parent_cias" ("idUser")''',
    r'''CREATE INDEX IF NOT EXISTS "index_user_local_parent_cias_idParentCia" ON "user_local_parent_cias" ("idParentCia")''',
    r'''CREATE TABLE IF NOT EXISTS "local_agro_units" ("idLocalAgroUnit" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_Id" TEXT, "commercial_name" TEXT NOT NULL, "slug" TEXT NOT NULL)''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_agro_units_ext_Id" ON "local_agro_units" ("ext_Id")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_agro_units_commercial_name" ON "local_agro_units" ("commercial_name")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_agro_units_slug" ON "local_agro_units" ("slug")''',
    r'''CREATE TABLE IF NOT EXISTS "local_ranches" ("idLocalRanch" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "name" TEXT NOT NULL, "code" TEXT NOT NULL, "lat" REAL, "lon" REAL, "idLocalAgroUnit" INTEGER NOT NULL, FOREIGN KEY("idLocalAgroUnit") REFERENCES "local_agro_units"("idLocalAgroUnit") ON UPDATE NO ACTION ON DELETE RESTRICT )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_ranches_ext_id" ON "local_ranches" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ranches_idLocalAgroUnit" ON "local_ranches" ("idLocalAgroUnit")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_ranches_idLocalAgroUnit_code" ON "local_ranches" ("idLocalAgroUnit", "code")''',
    r'''CREATE TABLE IF NOT EXISTS "local_crop_catalog" ("idCrop" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "name" TEXT NOT NULL, "variedad" TEXT, "code" TEXT, "description" TEXT, "photo" TEXT)''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_crop_catalog_ext_id" ON "local_crop_catalog" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_crop_catalog_name" ON "local_crop_catalog" ("name")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_crop_catalog_variedad" ON "local_crop_catalog" ("variedad")''',
    r'''CREATE TABLE IF NOT EXISTS "local_plots" ("idLocalPlot" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "name" TEXT NOT NULL, "code" TEXT, "lat" REAL, "lon" REAL, "idLocalRanch" INTEGER NOT NULL, "assigned_user_id" INTEGER, FOREIGN KEY("idLocalRanch") REFERENCES "local_ranches"("idLocalRanch") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("assigned_user_id") REFERENCES "users"("idUser") ON UPDATE NO ACTION ON DELETE SET NULL )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_plots_ext_id" ON "local_plots" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_plots_idLocalRanch" ON "local_plots" ("idLocalRanch")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_plots_assigned_user_id" ON "local_plots" ("assigned_user_id")''',
    r'''CREATE TABLE IF NOT EXISTS "local_plot_vertexes" ("idLocalPlotVertex" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "level" INTEGER NOT NULL, "lat" REAL NOT NULL, "lon" REAL NOT NULL, "idLocalPlot" INTEGER NOT NULL, FOREIGN KEY("idLocalPlot") REFERENCES "local_plots"("idLocalPlot") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_plot_vertexes_ext_id" ON "local_plot_vertexes" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_plot_vertexes_idLocalPlot" ON "local_plot_vertexes" ("idLocalPlot")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_plot_vertexes_idLocalPlot_level" ON "local_plot_vertexes" ("idLocalPlot", "level")''',
    r'''CREATE TABLE IF NOT EXISTS "local_programs" ("idProgram" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "cycle" TEXT NOT NULL, "est_start_date" INTEGER NOT NULL, "est_finish_date" INTEGER NOT NULL, "act_start_date" INTEGER, "act_finish_date" INTEGER, "status" TEXT NOT NULL, "idLocalCia" INTEGER NOT NULL, "idLocalAgroUnit" INTEGER NOT NULL, "idLocalRanch" INTEGER NOT NULL, "idCrop" INTEGER NOT NULL, "idLocalPlot" INTEGER NOT NULL, FOREIGN KEY("idLocalCia") REFERENCES "local_cias"("idLocalCia") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idLocalAgroUnit") REFERENCES "local_agro_units"("idLocalAgroUnit") ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY("idLocalRanch") REFERENCES "local_ranches"("idLocalRanch") ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY("idCrop") REFERENCES "local_crop_catalog"("idCrop") ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY("idLocalPlot") REFERENCES "local_plots"("idLocalPlot") ON UPDATE NO ACTION ON DELETE RESTRICT )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_programs_ext_id" ON "local_programs" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_idLocalCia" ON "local_programs" ("idLocalCia")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_idLocalAgroUnit" ON "local_programs" ("idLocalAgroUnit")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_idLocalRanch" ON "local_programs" ("idLocalRanch")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_idCrop" ON "local_programs" ("idCrop")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_idLocalPlot" ON "local_programs" ("idLocalPlot")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_cycle" ON "local_programs" ("cycle")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_status" ON "local_programs" ("status")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_idLocalCia_idLocalAgroUnit_idLocalRanch_idLocalPlot" ON "local_programs" ("idLocalCia", "idLocalAgroUnit", "idLocalRanch", "idLocalPlot")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_programs_idLocalCia_idLocalAgroUnit_idLocalRanch_idLocalPlot_idCrop_cycle" ON "local_programs" ("idLocalCia", "idLocalAgroUnit", "idLocalRanch", "idLocalPlot", "idCrop", "cycle")''',
    r'''CREATE TABLE IF NOT EXISTS "local_phytomonitoring_headers" ("idHeader" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "cycle" TEXT NOT NULL, "est_start_date" INTEGER, "est_finish_date" INTEGER, "start_at" INTEGER, "finished_at" INTEGER, "additional_notes" TEXT NOT NULL DEFAULT '', "radius_tolerance" REAL NOT NULL DEFAULT 50.0, "status" TEXT NOT NULL, "idProgram" INTEGER NOT NULL, "idCrop" INTEGER NOT NULL, "idLocalPlot" INTEGER NOT NULL, "assigned_user_id" INTEGER, "sync_pending" INTEGER NOT NULL DEFAULT 0, FOREIGN KEY("idProgram") REFERENCES "local_programs"("idProgram") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idCrop") REFERENCES "local_crop_catalog"("idCrop") ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY("idLocalPlot") REFERENCES "local_plots"("idLocalPlot") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("assigned_user_id") REFERENCES "users"("idUser") ON UPDATE NO ACTION ON DELETE SET NULL )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_ext_id" ON "local_phytomonitoring_headers" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_idProgram" ON "local_phytomonitoring_headers" ("idProgram")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_idCrop" ON "local_phytomonitoring_headers" ("idCrop")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_idLocalPlot" ON "local_phytomonitoring_headers" ("idLocalPlot")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_status" ON "local_phytomonitoring_headers" ("status")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_cycle" ON "local_phytomonitoring_headers" ("cycle")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_assigned_user_id" ON "local_phytomonitoring_headers" ("assigned_user_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_est_start_date" ON "local_phytomonitoring_headers" ("est_start_date")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_headers_sync_pending" ON "local_phytomonitoring_headers" ("sync_pending")''',
    r'''CREATE TABLE IF NOT EXISTS "local_phytomonitoring_target_points" ("idTargetPoint" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "label" TEXT NOT NULL DEFAULT '', "radius_m" INTEGER NOT NULL, "lat" REAL NOT NULL, "lon" REAL NOT NULL, "status" TEXT NOT NULL, "idHeader" INTEGER NOT NULL, "idLocalPlot" INTEGER NOT NULL, FOREIGN KEY("idHeader") REFERENCES "local_phytomonitoring_headers"("idHeader") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idLocalPlot") REFERENCES "local_plots"("idLocalPlot") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_phytomonitoring_target_points_ext_id" ON "local_phytomonitoring_target_points" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_target_points_idHeader" ON "local_phytomonitoring_target_points" ("idHeader")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_target_points_idLocalPlot" ON "local_phytomonitoring_target_points" ("idLocalPlot")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_target_points_status" ON "local_phytomonitoring_target_points" ("status")''',
    r'''CREATE TABLE IF NOT EXISTS "local_phytosanitary_catalog" ("idPhytosanitary" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "name" TEXT NOT NULL, "type" TEXT NOT NULL, "min_ref_value" INTEGER, "max_ref_value" INTEGER, "description" TEXT, "photo" TEXT, "idDefaultCrop" INTEGER, FOREIGN KEY("idDefaultCrop") REFERENCES "local_crop_catalog"("idCrop") ON UPDATE NO ACTION ON DELETE SET NULL )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_phytosanitary_catalog_ext_id" ON "local_phytosanitary_catalog" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytosanitary_catalog_name" ON "local_phytosanitary_catalog" ("name")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytosanitary_catalog_type" ON "local_phytosanitary_catalog" ("type")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytosanitary_catalog_idDefaultCrop" ON "local_phytosanitary_catalog" ("idDefaultCrop")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_phytosanitary_catalog_idDefaultCrop_name_type" ON "local_phytosanitary_catalog" ("idDefaultCrop", "name", "type")''',
    r'''CREATE TABLE IF NOT EXISTS "local_phytostages" ("idLocalPhytostage" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "stage" TEXT NOT NULL, "photo" TEXT, "idPhytosanitary" INTEGER NOT NULL, FOREIGN KEY("idPhytosanitary") REFERENCES "local_phytosanitary_catalog"("idPhytosanitary") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_phytostages_ext_id" ON "local_phytostages" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytostages_idPhytosanitary" ON "local_phytostages" ("idPhytosanitary")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_phytostages_idPhytosanitary_stage" ON "local_phytostages" ("idPhytosanitary", "stage")''',
    r'''CREATE TABLE IF NOT EXISTS "local_phytomonitoring_checkpoints" ("idCheckpoint" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "ext_id" TEXT, "qty" INTEGER, "presence_status" INTEGER, "stage" TEXT, "notes" TEXT, "photo_ref" TEXT, "photo_local_path" TEXT, "photo_url" TEXT, "captured_at" INTEGER, "captured_by_user_id" INTEGER, "idTargetPoint" INTEGER NOT NULL, "idHeader" INTEGER NOT NULL, "idPhytosanitary" INTEGER, "idLocalPlot" INTEGER NOT NULL, FOREIGN KEY("idTargetPoint") REFERENCES "local_phytomonitoring_target_points"("idTargetPoint") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idHeader") REFERENCES "local_phytomonitoring_headers"("idHeader") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idPhytosanitary") REFERENCES "local_phytosanitary_catalog"("idPhytosanitary") ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY("idLocalPlot") REFERENCES "local_plots"("idLocalPlot") ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY("captured_by_user_id") REFERENCES "users"("idUser") ON UPDATE NO ACTION ON DELETE SET NULL )''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_ext_id" ON "local_phytomonitoring_checkpoints" ("ext_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_idTargetPoint" ON "local_phytomonitoring_checkpoints" ("idTargetPoint")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_idHeader" ON "local_phytomonitoring_checkpoints" ("idHeader")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_idPhytosanitary" ON "local_phytomonitoring_checkpoints" ("idPhytosanitary")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_idLocalPlot" ON "local_phytomonitoring_checkpoints" ("idLocalPlot")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_captured_at" ON "local_phytomonitoring_checkpoints" ("captured_at")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_photo_ref" ON "local_phytomonitoring_checkpoints" ("photo_ref")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_phytomonitoring_checkpoints_captured_by_user_id" ON "local_phytomonitoring_checkpoints" ("captured_by_user_id")''',
    r'''CREATE TABLE IF NOT EXISTS "user_local_cias" ("idUser" INTEGER NOT NULL, "idLocalCia" INTEGER NOT NULL, PRIMARY KEY("idUser", "idLocalCia"), FOREIGN KEY("idUser") REFERENCES "users"("idUser") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idLocalCia") REFERENCES "local_cias"("idLocalCia") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE INDEX IF NOT EXISTS "index_user_local_cias_idUser" ON "user_local_cias" ("idUser")''',
    r'''CREATE INDEX IF NOT EXISTS "index_user_local_cias_idLocalCia" ON "user_local_cias" ("idLocalCia")''',
    r'''CREATE TABLE IF NOT EXISTS "local_cia_agro_units" ("idLocalCia" INTEGER NOT NULL, "idLocalAgroUnit" INTEGER NOT NULL, "ext_id" TEXT, PRIMARY KEY("idLocalCia", "idLocalAgroUnit"), FOREIGN KEY("idLocalCia") REFERENCES "local_cias"("idLocalCia") ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY("idLocalAgroUnit") REFERENCES "local_agro_units"("idLocalAgroUnit") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_cia_agro_units_idLocalCia" ON "local_cia_agro_units" ("idLocalCia")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_cia_agro_units_idLocalAgroUnit" ON "local_cia_agro_units" ("idLocalAgroUnit")''',
    r'''CREATE UNIQUE INDEX IF NOT EXISTS "index_local_cia_agro_units_ext_id" ON "local_cia_agro_units" ("ext_id")''',
    r'''CREATE TABLE IF NOT EXISTS "local_aspersion_sessions" ("session_id" TEXT NOT NULL, "program_id" TEXT, "program_name" TEXT, "program_cycle" TEXT, "plot_id" TEXT, "plot_name" TEXT, "plot_code" TEXT, "ranch_id" TEXT, "ranch_name" TEXT, "producer_id" TEXT, "producer_name" TEXT, "data_central_ids" TEXT, "evaluation_id" TEXT, "aspersion_date" TEXT, "status" TEXT, "assigned_to_id" TEXT, "assigned_to_username" TEXT, "est_start_date" TEXT, "est_finish_date" TEXT, "act_start_date" TEXT, "act_finish_date" TEXT, "import_status" TEXT, "import_errors_json" TEXT, "imported_at" TEXT, "last_template_id" TEXT, "points_count" INTEGER, "created_at" TEXT, "updated_at" TEXT, "downloaded_points_count" INTEGER NOT NULL DEFAULT 0, "points_download_complete" INTEGER NOT NULL DEFAULT 0, "last_synced_at" INTEGER, PRIMARY KEY("session_id"))''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_sessions_program_id" ON "local_aspersion_sessions" ("program_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_sessions_plot_id" ON "local_aspersion_sessions" ("plot_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_sessions_assigned_to_id" ON "local_aspersion_sessions" ("assigned_to_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_sessions_aspersion_date" ON "local_aspersion_sessions" ("aspersion_date")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_sessions_status" ON "local_aspersion_sessions" ("status")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_sessions_import_status" ON "local_aspersion_sessions" ("import_status")''',
    r'''CREATE TABLE IF NOT EXISTS "local_aspersion_points" ("point_id" TEXT NOT NULL, "session_id" TEXT NOT NULL, "plot_id" TEXT, "geom_type" TEXT, "longitude" REAL NOT NULL, "latitude" REAL NOT NULL, "timestamp" TEXT, "pass_number" INTEGER, "elevation_m" REAL, "course_deg" REAL, "vehicle_heading" REAL, "distance_m" REAL, "duration_s" INTEGER, "speed_kmh" REAL, "satellites" INTEGER, "gnss_hdop" REAL, "gnss_vdop" REAL, "gnss_pdop" REAL, "is_diff_active" INTEGER, "diff_mode" TEXT, "xte_implement" REAL, "xte_vehicle" REAL, "is_steering_active" INTEGER, "is_area_counting" INTEGER, "active_rows" INTEGER, "app_status" TEXT, "boom_width_m" REAL, "liquid_flow_ls" REAL, "boom_pressure_bar" REAL, "press_ag_kpa" REAL, "press_aux_kpa" REAL, "production_hah" REAL, "droplet_size" TEXT, "nozzle_color" TEXT, "nozzle_capacity" REAL, "nozzle_ref_pressure" REAL, "target_rate_l" REAL, "applied_rate_l" REAL, "product_quantity" REAL, "area_ha" REAL, "rate_quality" TEXT, "evaluation_id" TEXT, "extra_raw_json" TEXT, "created_at" TEXT, PRIMARY KEY("point_id"), FOREIGN KEY("session_id") REFERENCES "local_aspersion_sessions"("session_id") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_points_session_id" ON "local_aspersion_points" ("session_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_points_plot_id" ON "local_aspersion_points" ("plot_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_points_timestamp" ON "local_aspersion_points" ("timestamp")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_points_rate_quality" ON "local_aspersion_points" ("rate_quality")''',
    r'''CREATE TABLE IF NOT EXISTS "local_aspersion_stats" ("session_id" TEXT NOT NULL, "plot_id" TEXT, "points_count" INTEGER, "area_total_ha" REAL, "mean_target_l" REAL, "mean_applied_l" REAL, "ratio_applied" REAL, "pct_below" REAL, "pct_in_range" REAL, "pct_above" REAL, "last_refresh" TEXT, "cached_at" INTEGER NOT NULL DEFAULT 0, PRIMARY KEY("session_id"), FOREIGN KEY("session_id") REFERENCES "local_aspersion_sessions"("session_id") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_stats_plot_id" ON "local_aspersion_stats" ("plot_id")''',
    r'''CREATE TABLE IF NOT EXISTS "local_aspersion_variable_stats" ("session_id" TEXT NOT NULL, "variable_key" TEXT NOT NULL, "label" TEXT NOT NULL, "count" INTEGER NOT NULL, "mean_value" REAL, "min_value" REAL, "max_value" REAL, "stddev" REAL, "cached_at" INTEGER NOT NULL DEFAULT 0, PRIMARY KEY("session_id", "variable_key"), FOREIGN KEY("session_id") REFERENCES "local_aspersion_sessions"("session_id") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_aspersion_variable_stats_variable_key" ON "local_aspersion_variable_stats" ("variable_key")''',
    r'''CREATE TABLE IF NOT EXISTS "local_ndvi_sessions" ("session_id" TEXT NOT NULL, "program_id" TEXT, "plot_id" TEXT, "session_date" TEXT, "status" TEXT, "assigned_to_id" TEXT, "assigned_to_username" TEXT, "est_start_date" TEXT, "est_finish_date" TEXT, "act_start_date" TEXT, "act_finish_date" TEXT, "import_status" TEXT, "import_errors_json" TEXT, "imported_at" TEXT, "points_count" INTEGER, "contour_status" TEXT, "created_at" TEXT, "updated_at" TEXT, "downloaded_points_count" INTEGER NOT NULL DEFAULT 0, "points_download_complete" INTEGER NOT NULL DEFAULT 0, "last_synced_at" INTEGER, PRIMARY KEY("session_id"))''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_sessions_program_id" ON "local_ndvi_sessions" ("program_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_sessions_plot_id" ON "local_ndvi_sessions" ("plot_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_sessions_assigned_to_id" ON "local_ndvi_sessions" ("assigned_to_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_sessions_session_date" ON "local_ndvi_sessions" ("session_date")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_sessions_status" ON "local_ndvi_sessions" ("status")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_sessions_import_status" ON "local_ndvi_sessions" ("import_status")''',
    r'''CREATE TABLE IF NOT EXISTS "local_ndvi_points" ("point_id" TEXT NOT NULL, "session_id" TEXT NOT NULL, "geom_type" TEXT, "longitude" REAL NOT NULL, "latitude" REAL NOT NULL, "obj_id" INTEGER, "lot_label" TEXT, "dataset_date" TEXT, "product" TEXT, "ndvi" REAL, "nir_vigor" REAL, "osavi" REAL, "vari" REAL, "bare_soil_index" REAL, "image_red" REAL, "image_green" REAL, "image_blue" REAL, "red_edge" REAL, "swir" REAL, "ndre" REAL, "msavi2" REAL, "gndvi" REAL, "ndmi" REAL, "psri" REAL, "created_at" TEXT, PRIMARY KEY("point_id"), FOREIGN KEY("session_id") REFERENCES "local_ndvi_sessions"("session_id") ON UPDATE NO ACTION ON DELETE CASCADE )''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_points_session_id" ON "local_ndvi_points" ("session_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_points_obj_id" ON "local_ndvi_points" ("obj_id")''',
    r'''CREATE INDEX IF NOT EXISTS "index_local_ndvi_points_dataset_date" ON "local_ndvi_points" ("dataset_date")'''
  ];
}

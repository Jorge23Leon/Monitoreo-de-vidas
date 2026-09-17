import 'dart:convert';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'monitoring_models.dart';

class MonitoringLocalStore {
  MonitoringLocalStore._();
  static final instance = MonitoringLocalStore._();
  Database? _db;

  Future<Database> get database async {
    if (_db != null) return _db!;
    final root = await getDatabasesPath();
    _db = await openDatabase(
      p.join(root, 'ciagro_monitoring_v3.db'),
      version: 3,
      onConfigure: (db) async => db.execute('PRAGMA foreign_keys = ON'),
      onCreate: (db, _) async {
        await db.execute('''
          CREATE TABLE monitoring_headers (
            remote_id TEXT PRIMARY KEY,
            status TEXT NOT NULL,
            crop_id TEXT,
            plot_id TEXT,
            assigned_to TEXT,
            cia_id TEXT,
            start_date TEXT,
            end_date TEXT,
            json TEXT NOT NULL,
            updated_at INTEGER NOT NULL
          )
        ''');
        await db.execute('CREATE INDEX idx_headers_assigned ON monitoring_headers(assigned_to)');
        await db.execute('CREATE INDEX idx_headers_crop ON monitoring_headers(crop_id)');
        await db.execute('CREATE INDEX idx_headers_cia ON monitoring_headers(cia_id)');

        await db.execute('''
          CREATE TABLE target_points (
            remote_id TEXT PRIMARY KEY,
            server_id TEXT,
            header_id TEXT NOT NULL,
            label TEXT NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            radius_m REAL NOT NULL,
            status TEXT NOT NULL,
            sync_state TEXT NOT NULL DEFAULT 'synced',
            last_error TEXT,
            json TEXT NOT NULL,
            updated_at INTEGER NOT NULL
          )
        ''');
        await db.execute('CREATE INDEX idx_target_header ON target_points(header_id)');
        await db.execute('CREATE UNIQUE INDEX idx_target_server ON target_points(server_id) WHERE server_id IS NOT NULL');
        await db.execute('CREATE INDEX idx_target_sync ON target_points(sync_state)');

        await db.execute('''
          CREATE TABLE catalog_items (
            remote_id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            type TEXT NOT NULL,
            crop_id TEXT,
            photo TEXT,
            description TEXT,
            json TEXT NOT NULL,
            updated_at INTEGER NOT NULL
          )
        ''');
        await db.execute('CREATE INDEX idx_catalog_crop ON catalog_items(crop_id)');
        await db.execute('CREATE INDEX idx_catalog_type ON catalog_items(type)');

        await db.execute('''
          CREATE TABLE checkpoint_queue (
            local_id TEXT PRIMARY KEY,
            remote_id TEXT,
            header_id TEXT NOT NULL,
            target_id TEXT NOT NULL,
            phyto_issue_id TEXT,
            phyto_name TEXT NOT NULL,
            phyto_type TEXT NOT NULL,
            stage TEXT,
            presence_status TEXT NOT NULL,
            qty INTEGER NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            notes TEXT,
            photo_path TEXT,
            captured_at TEXT NOT NULL,
            sync_state TEXT NOT NULL DEFAULT 'pending',
            last_error TEXT
          )
        ''');
        await db.execute('CREATE INDEX idx_checkpoint_header ON checkpoint_queue(header_id)');
        await db.execute('CREATE INDEX idx_checkpoint_target ON checkpoint_queue(target_id)');
        await db.execute('CREATE INDEX idx_checkpoint_sync ON checkpoint_queue(sync_state)');

        await db.execute('''
          CREATE TABLE v3_meta (
            meta_key TEXT PRIMARY KEY,
            meta_value TEXT
          )
        ''');
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.execute('ALTER TABLE monitoring_headers ADD COLUMN cia_id TEXT');
          await db.execute(
            'CREATE INDEX IF NOT EXISTS idx_headers_cia ON monitoring_headers(cia_id)',
          );
        }
        if (oldVersion < 3) {
          await db.execute('ALTER TABLE target_points ADD COLUMN server_id TEXT');
          await db.execute("ALTER TABLE target_points ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'synced'");
          await db.execute('ALTER TABLE target_points ADD COLUMN last_error TEXT');
          await db.execute('UPDATE target_points SET server_id = remote_id WHERE server_id IS NULL');
          await db.execute('CREATE UNIQUE INDEX IF NOT EXISTS idx_target_server ON target_points(server_id) WHERE server_id IS NOT NULL');
          await db.execute('CREATE INDEX IF NOT EXISTS idx_target_sync ON target_points(sync_state)');
        }
      },
    );
    return _db!;
  }

  Future<void> cacheHeaders(Iterable<MonitoringHeader> headers) async {
    final db = await database;
    for (final item in headers) {
      if (item.id.isEmpty) continue;
      var map = item.toMap();
      final queued = await meta('phyto_header_patch:${item.id}');
      if (queued != null && queued.trim().isNotEmpty) {
        final existing = await db.query(
          'monitoring_headers',
          columns: ['status', 'json'],
          where: 'remote_id = ?',
          whereArgs: [item.id],
          limit: 1,
        );
        if (existing.isNotEmpty) {
          final oldRaw = Map<String, dynamic>.from(
            jsonDecode('${existing.first['json']}') as Map,
          );
          final downloadedRaw = Map<String, dynamic>.from(item.raw);
          downloadedRaw['status'] = existing.first['status'];
          downloadedRaw['additional_notes'] = oldRaw['additional_notes'];
          map = MonitoringHeader.fromJson(downloadedRaw).toMap();
        }
      }
      await db.insert(
        'monitoring_headers',
        map,
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
  }

  Future<List<MonitoringHeader>> headers({
    String? assignedTo,
    String? dataCentralId,
  }) async {
    final db = await database;
    final clauses = <String>[];
    final args = <Object?>[];
    if (assignedTo != null && assignedTo.isNotEmpty) {
      clauses.add('assigned_to = ?');
      args.add(assignedTo);
    }
    if (dataCentralId != null && dataCentralId.isNotEmpty) {
      clauses.add('cia_id = ?');
      args.add(dataCentralId);
    }
    final rows = await db.query(
      'monitoring_headers',
      where: clauses.isEmpty ? null : clauses.join(' AND '),
      whereArgs: clauses.isEmpty ? null : args,
      orderBy: 'COALESCE(start_date, "") DESC, updated_at DESC',
    );
    return rows.map(MonitoringHeader.fromDb).toList(growable: false);
  }

  Future<void> cacheTargetPoints(Iterable<TargetPoint> points) async {
    final db = await database;
    for (final point in points) {
      if (point.id.isEmpty || point.headerId.isEmpty) continue;
      final serverId = point.serverId ?? point.id;
      final existing = await db.query(
        'target_points',
        columns: ['remote_id', 'status'],
        where: 'server_id = ?',
        whereArgs: [serverId],
        limit: 1,
      );
      final map = point.toMap()
        ..['server_id'] = serverId
        ..['sync_state'] = 'synced'
        ..['last_error'] = null;
      if (existing.isNotEmpty) {
        final localId = '${existing.first['remote_id']}';
        final currentStatus = '${existing.first['status'] ?? ''}';
        if (currentStatus.toLowerCase().contains('complet')) {
          map['status'] = currentStatus;
        }
        map.remove('remote_id');
        await db.update(
          'target_points',
          map,
          where: 'remote_id = ?',
          whereArgs: [localId],
        );
      } else {
        await db.insert(
          'target_points',
          map,
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
    }
  }

  Future<void> insertLocalTarget(TargetPoint point) async {
    final db = await database;
    await db.insert(
      'target_points',
      point.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<TargetPoint>> targetPoints(String headerId) async {
    final db = await database;
    final rows = await db.query(
      'target_points',
      where: 'header_id = ?',
      whereArgs: [headerId],
      orderBy: 'updated_at ASC, label COLLATE NOCASE',
    );
    final points = rows.map(TargetPoint.fromDb).toList();
    points.sort((a, b) {
      if (a.visibleNumber > 0 && b.visibleNumber > 0) {
        return a.visibleNumber.compareTo(b.visibleNumber);
      }
      return a.label.compareTo(b.label);
    });
    return points;
  }

  Future<TargetPoint?> targetPoint(String localId) async {
    final db = await database;
    final rows = await db.query(
      'target_points',
      where: 'remote_id = ?',
      whereArgs: [localId],
      limit: 1,
    );
    return rows.isEmpty ? null : TargetPoint.fromDb(rows.first);
  }

  Future<List<TargetPoint>> pendingTargets({String? headerId}) async {
    final db = await database;
    final rows = await db.query(
      'target_points',
      where: headerId == null
          ? "server_id IS NULL OR sync_state != 'synced'"
          : "header_id = ? AND (server_id IS NULL OR sync_state != 'synced')",
      whereArgs: headerId == null ? null : [headerId],
      orderBy: 'updated_at ASC',
    );
    return rows.map(TargetPoint.fromDb).toList(growable: false);
  }

  Future<String?> targetServerId(String localId) async {
    final point = await targetPoint(localId);
    return point?.serverId;
  }

  Future<void> markTargetSynced(String localId, String serverId) async {
    final db = await database;
    await db.update(
      'target_points',
      {'server_id': serverId, 'sync_state': 'synced', 'last_error': null},
      where: 'remote_id = ?',
      whereArgs: [localId],
    );
  }

  Future<void> markTargetFailed(String localId, Object error) async {
    final db = await database;
    await db.update(
      'target_points',
      {'sync_state': 'failed', 'last_error': error.toString()},
      where: 'remote_id = ?',
      whereArgs: [localId],
    );
  }

  Future<void> setTargetCompleted(String targetId) async {
    final db = await database;
    await db.update(
      'target_points',
      {'status': 'Completado'},
      where: 'remote_id = ?',
      whereArgs: [targetId],
    );
  }

  Future<void> updateHeaderState(
    String headerId, {
    required String status,
    String? additionalNotes,
  }) async {
    final db = await database;
    final rows = await db.query(
      'monitoring_headers',
      columns: ['json'],
      where: 'remote_id = ?',
      whereArgs: [headerId],
      limit: 1,
    );
    if (rows.isEmpty) return;
    final raw = Map<String, dynamic>.from(
      jsonDecode('${rows.first['json']}') as Map,
    );
    raw['status'] = status;
    raw['additional_notes'] = additionalNotes;
    await db.update(
      'monitoring_headers',
      {
        'status': status,
        'json': jsonEncode(raw),
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      },
      where: 'remote_id = ?',
      whereArgs: [headerId],
    );
  }

  Future<void> cacheCatalog(Iterable<PhytoCatalogItem> items) async {
    final db = await database;
    final batch = db.batch();
    for (final item in items) {
      if (item.id.isNotEmpty) {
        batch.insert('catalog_items', item.toDbMap(), conflictAlgorithm: ConflictAlgorithm.replace);
      }
    }
    await batch.commit(noResult: true);
  }

  Future<List<PhytoCatalogItem>> catalogForCrop(String? cropId) async {
    final db = await database;
    final normalizedCropId = cropId?.trim();
    if (normalizedCropId == null || normalizedCropId.isEmpty) {
      return const <PhytoCatalogItem>[];
    }

    // Offline conserva exactamente la misma regla que producción: solo
    // elementos asociados al cultivo del programa. No usar catálogo global.
    final rows = await db.query(
      'catalog_items',
      where: 'crop_id = ?',
      whereArgs: [normalizedCropId],
      orderBy: 'name COLLATE NOCASE',
    );
    return rows
        .map(PhytoCatalogItem.fromDb)
        .where((e) => !e.isNoPest)
        .toList(growable: false);
  }

  /// Guarda la captura completa de un punto en una sola transacción.
  /// Si la app se cierra a mitad del guardado, nunca queda el target marcado
  /// como completado sin sus checkpoints (ni al revés).
  Future<void> saveCapturedPoint(
    String targetId,
    Iterable<PendingCheckpoint> checkpoints,
  ) async {
    final db = await database;
    await db.transaction((txn) async {
      final batch = txn.batch();
      for (final checkpoint in checkpoints) {
        batch.insert(
          'checkpoint_queue',
          checkpoint.toDbMap(),
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
      batch.update(
        'target_points',
        {'status': 'Completado'},
        where: 'remote_id = ?',
        whereArgs: [targetId],
      );
      await batch.commit(noResult: true);
    });
  }

  Future<void> insertPending(Iterable<PendingCheckpoint> checkpoints) async {
    final db = await database;
    await db.transaction((txn) async {
      final batch = txn.batch();
      for (final checkpoint in checkpoints) {
        batch.insert(
          'checkpoint_queue',
          checkpoint.toDbMap(),
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
      await batch.commit(noResult: true);
    });
  }

  Future<List<PendingCheckpoint>> checkpointsForHeader(String headerId) async {
    final db = await database;
    final rows = await db.query(
      'checkpoint_queue',
      where: 'header_id = ?',
      whereArgs: [headerId],
      orderBy: 'captured_at DESC, local_id DESC',
    );
    return rows.map(PendingCheckpoint.fromDb).toList(growable: false);
  }

  Future<List<PendingCheckpoint>> checkpointsForTarget(String targetId) async {
    final db = await database;
    final rows = await db.query(
      'checkpoint_queue',
      where: 'target_id = ?',
      whereArgs: [targetId],
      orderBy: 'captured_at DESC',
    );
    return rows.map(PendingCheckpoint.fromDb).toList(growable: false);
  }

  Future<List<PendingCheckpoint>> pendingCheckpoints({String? headerId}) async {
    final db = await database;
    final where = headerId == null
        ? 'sync_state != ?'
        : 'sync_state != ? AND header_id = ?';
    final args = headerId == null
        ? <Object?>['synced']
        : <Object?>['synced', headerId];
    final rows = await db.query(
      'checkpoint_queue',
      where: where,
      whereArgs: args,
      orderBy: 'captured_at ASC',
    );
    return rows.map(PendingCheckpoint.fromDb).toList(growable: false);
  }

  Future<int> pendingCount({String? headerId}) async =>
      (await pendingCheckpoints(headerId: headerId)).length;

  Future<void> markSyncing(String localId) async {
    final db = await database;
    await db.update(
      'checkpoint_queue',
      {'sync_state': 'syncing', 'last_error': null},
      where: 'local_id = ?',
      whereArgs: [localId],
    );
  }

  Future<void> setCheckpointRemoteId(String localId, String remoteId) async {
    final db = await database;
    await db.update(
      'checkpoint_queue',
      {'remote_id': remoteId, 'last_error': null},
      where: 'local_id = ?',
      whereArgs: [localId],
    );
  }

  Future<void> markSynced(String localId, String remoteId) async {
    final db = await database;
    await db.update(
      'checkpoint_queue',
      {'sync_state': 'synced', 'remote_id': remoteId, 'last_error': null},
      where: 'local_id = ?',
      whereArgs: [localId],
    );
  }

  Future<void> markFailed(String localId, Object error) async {
    final db = await database;
    await db.update(
      'checkpoint_queue',
      {'sync_state': 'failed', 'last_error': error.toString()},
      where: 'local_id = ?',
      whereArgs: [localId],
    );
  }

  Future<void> setMeta(String key, String value) async {
    final db = await database;
    await db.insert(
      'v3_meta',
      {'meta_key': key, 'meta_value': value},
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<String?> meta(String key) async {
    final db = await database;
    final rows = await db.query(
      'v3_meta',
      columns: ['meta_value'],
      where: 'meta_key = ?',
      whereArgs: [key],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first['meta_value']?.toString();
  }

  Future<void> deleteMeta(String key) async {
    final db = await database;
    await db.delete('v3_meta', where: 'meta_key = ?', whereArgs: [key]);
  }
}

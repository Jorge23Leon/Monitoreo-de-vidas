import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:dio/dio.dart';
import 'package:path/path.dart' as p;

import '../../core/api/api_client.dart';
import 'monitoring_local_store.dart';
import 'monitoring_models.dart';

class MonitoringRepository {
  MonitoringRepository({
    ApiClient? apiClient,
    MonitoringLocalStore? localStore,
  })  : _apiClient = apiClient ?? ApiClient.instance,
        _local = localStore ?? MonitoringLocalStore.instance;

  final ApiClient _apiClient;
  final MonitoringLocalStore _local;

  Dio get _api => _apiClient.dio;

  /// Descarga la misma relación lógica que usa Kotlin:
  /// CIA -> programas -> productor -> rancho -> parcela -> header.
  /// Si no hay red, vuelve a Room/SQLite sin bloquear al técnico.
  Future<List<MonitoringHeader>> headers({
    String? assignedTo,
    String? dataCentralId,
    String? viewerUserId,
  }) async {
    try {
      // Estas consultas son independientes. Antes se hacían una detrás de
      // otra y la pantalla esperaba la suma de todos los tiempos de red.
      final results = await Future.wait<dynamic>([
        _paged(
          'api/v1/field_ops/tasks/',
          query: {if (dataCentralId != null) 'datacentral': dataCentralId},
        ),
        _safePaged(
          'api/v1/organizations/',
          // Igual que `useProducers(datacentral)` del frontend: nunca mezclar
          // otras agro-unidades con el selector de Productor. El backend aplica
          // además el scope real del usuario/token.
          query: {
            'unit_type': 'Productor',
            if (dataCentralId != null) 'datacentral': dataCentralId,
          },
        ),
        _safePaged('api/v1/geo_assets/ranches/'),
        _geoPlotFeatures(),
        // El catálogo de cultivos es la fuente oficial del nombre/variedad y
        // de la fotografía cargada en producción. Se descarga una sola vez
        // junto con el resto de datos del listado.
        _safePaged('api/v1/agro-catalogs/crops/'),
        _paged(
          'api/v1/monitoring/phyto/headers/',
          query: {'assigned_to': assignedTo},
        ),
      ]);

      final tasks = results[0] as List<Map<String, dynamic>>;
      final producers = results[1] as List<Map<String, dynamic>>;
      final ranches = results[2] as List<Map<String, dynamic>>;
      final plotFeatures = results[3] as List<Map<String, dynamic>>;
      final crops = results[4] as List<Map<String, dynamic>>;
      final maps = results[5] as List<Map<String, dynamic>>;

      final taskById = <String, Map<String, dynamic>>{};
      for (final task in tasks) {
        final id = flexibleId(task['id']);
        if (id != null) taskById[id] = task;
      }
      final producerById = _byId(producers);
      final cropById = _byId(crops);

      // Paridad con el frontend web:
      // CIA -> Productores visibles -> Ranchos visibles -> Parcelas visibles.
      // Aunque los endpoints ya respetan permisos del token, al existir una CIA
      // seleccionada hacemos también la poda jerárquica local para impedir que un
      // nombre/rama de otra CIA se cuele en filtros por datos cacheados.
      final ranchById = <String, Map<String, dynamic>>{};
      for (final ranch in ranches) {
        final id = flexibleId(ranch['id']);
        if (id == null) continue;
        final props = ranch['properties'] is Map
            ? Map<String, dynamic>.from(ranch['properties'] as Map)
            : ranch;
        final producer = flexibleId(props['producer']);
        if (dataCentralId != null &&
            producer != null &&
            !producerById.containsKey(producer)) {
          continue;
        }
        ranchById[id] = ranch;
      }

      final plotById = <String, Map<String, dynamic>>{};
      for (final feature in plotFeatures) {
        final id = flexibleId(feature['id']);
        if (id == null) continue;
        final props = feature['properties'] is Map
            ? Map<String, dynamic>.from(feature['properties'] as Map)
            : const <String, dynamic>{};
        final ranch = flexibleId(props['ranch']);
        final producer = flexibleId(props['producer']) ??
            flexibleId(props['producer_id']);
        if (dataCentralId != null) {
          final allowedByRanch = ranch != null && ranchById.containsKey(ranch);
          final allowedByProducer =
              producer != null && producerById.containsKey(producer);
          if (!allowedByRanch && !allowedByProducer) continue;
        }
        plotById[id] = feature;
      }

      final items = <MonitoringHeader>[];
      for (final source in maps) {
        final taskId = flexibleId(source['field_task']);
        final task = taskId == null ? null : taskById[taskId];

        // Cuando hay CIA seleccionada, no se deben mezclar programas de otra CIA.
        if (dataCentralId != null && task == null) continue;

        final raw = <String, dynamic>{...source};
        if (dataCentralId != null && dataCentralId.isNotEmpty) {
          raw['_v3_data_central'] = dataCentralId;
        }

        if (task != null) {
          final plotId = flexibleId(task['plot']) ?? flexibleId(source['plot']);
          final producerId = flexibleId(task['agro_unit']);
          final plot = plotId == null ? null : plotById[plotId];
          final props = plot?['properties'] is Map
              ? Map<String, dynamic>.from(plot!['properties'] as Map)
              : const <String, dynamic>{};
          final ranchId = flexibleId(props['ranch']) ?? flexibleId(task['ranch']);
          final ranch = ranchId == null ? null : ranchById[ranchId];
          final producerIdResolved = producerId ?? flexibleId(props['producer_id']);
          final producer = producerIdResolved == null ? null : producerById[producerIdResolved];
          // El cultivo de la SESION fitosanitaria es la fuente autoritativa.
          // `field_task.crop` solo se usa como respaldo cuando el header no trae
          // cultivo. Esto evita mostrar Maiz (o filtrar su catalogo) si el
          // MonitoringHeader fue creado para Moringa pero la task vinculada
          // conserva otro cultivo.
          final headerCropRaw = source['crop'] ?? source['crop_id'];
          final taskCropRaw = task['crop'] ?? task['crop_id'];
          final headerCropId = flexibleId(headerCropRaw);
          final taskCropId = flexibleId(taskCropRaw);
          final cropId = headerCropId ?? taskCropId;
          final cropCatalog = cropId == null ? null : cropById[cropId];

          raw['_v3_program_name'] = firstText(task, const [
                'title', 'name', 'cycle'
              ]) ?? 'Monitoreo';
          raw['_v3_program_cycle'] = firstText(task, const ['cycle']);
          raw['_v3_start_date'] = firstText(task, const ['est_start_date']) ??
              firstText(source, const ['estimated_start_date']);
          raw['_v3_end_date'] = firstText(task, const ['est_finish_date']) ??
              firstText(source, const ['estimated_end_date']);
          raw['_v3_plot_id'] = plotId;
          raw['_v3_plot_code'] = firstText(props, const ['code', 'plot_code']);
          raw['_v3_plot_name'] = _plotLabel(plot, props);
          raw['_v3_ranch_id'] = ranchId;
          raw['_v3_ranch_name'] = firstText(props, const ['ranch_name']) ??
              _entityLabel(
                ranch,
                fallback: 'Rancho sin nombre',
              );
          raw['_v3_producer_id'] = producerIdResolved;
          raw['_v3_producer_name'] = firstText(props, const ['producer_name']) ??
              _entityLabel(
                producer,
                preferred: const ['commercial_name', 'name', 'nombre', 'display_name'],
                fallback: 'Productor sin nombre',
              );
          raw['_v3_crop_id'] = cropId;
          raw['_v3_crop_source'] = headerCropId != null ? 'header' : 'field_task';
          if (taskCropId != null) raw['_v3_task_crop_id'] = taskCropId;

          final headerEmbeddedCrop = headerCropRaw is Map
              ? Map<String, dynamic>.from(headerCropRaw)
              : null;
          final taskEmbeddedCrop = taskCropRaw is Map
              ? Map<String, dynamic>.from(taskCropRaw)
              : null;

          // Solo se permite usar nombre/foto de la task si su ID coincide con
          // el cultivo autoritativo. Si no coincide, ignorarlo evita mezclar
          // datos de otro cultivo.
          final matchingTaskCrop =
              taskCropId != null && taskCropId == cropId ? taskEmbeddedCrop : null;
          final matchingTaskText =
              taskCropId != null && taskCropId == cropId
                  ? firstText(task, const ['crop_name', 'crop_variety_name'])
                  : null;

          raw['_v3_crop_name'] =
              (headerEmbeddedCrop == null
                  ? null
                  : firstText(headerEmbeddedCrop, const ['name', 'nombre', 'variety'])) ??
              firstText(source, const ['crop_name']) ??
              (cropCatalog == null
                  ? null
                  : firstText(cropCatalog, const ['name', 'nombre', 'variety'])) ??
              (matchingTaskCrop == null
                  ? null
                  : firstText(matchingTaskCrop, const ['name', 'nombre', 'variety'])) ??
              matchingTaskText;

          final cropPhoto = photoUrlFrom(source['crop_photo']) ??
              photoUrlFrom(headerEmbeddedCrop) ??
              photoUrlFrom(cropCatalog) ??
              photoUrlFrom(matchingTaskCrop);
          if (cropPhoto != null) {
            raw['_v3_crop_photo'] = cropPhoto;
          } else {
            raw.remove('_v3_crop_photo');
          }
          if (plot != null) raw['_v3_plot_feature'] = plot;
        }

        if (viewerUserId != null && viewerUserId.isNotEmpty) {
          // La API ya aplicó permisos del token. Guardamos quién pudo ver este
          // header para que el modo offline no mezcle datos de otra sesión.
          raw['_v3_visible_user'] = viewerUserId;
        }

        final item = MonitoringHeader.fromJson(raw);
        if (item.id.isNotEmpty) items.add(item);
      }

      await _local.cacheHeaders(items);
      return _sortHeaders(items);
    } catch (_) {
      var cached = await _local.headers(
        // `assignedTo` no se usa como filtro duro para roles operativos porque
        // Kotlin también permite headers visibles por parcela/capturas previas.
        assignedTo: viewerUserId == null ? assignedTo : null,
        dataCentralId: dataCentralId,
      );
      if (viewerUserId != null && viewerUserId.isNotEmpty) {
        cached = cached.where((header) {
          final visibleUser = flexibleId(header.raw['_v3_visible_user']);
          return visibleUser == viewerUserId || header.assignedTo == viewerUserId;
        }).toList(growable: false);
      }
      return _sortHeaders(_offlineWindow(cached));
    }
  }

  Future<List<TargetPoint>> targetPoints(String headerId) async {
    // Igual que Android/Kotlin: el mapa abre primero con SQLite. Si ya hay
    // puntos cacheados no bloqueamos la interfaz esperando Internet.
    final cached = _sortTargets(await _local.targetPoints(headerId));
    if (cached.isNotEmpty) {
      unawaited(refreshTargetPoints(headerId));
      return cached;
    }
    return refreshTargetPoints(headerId);
  }

  Future<List<TargetPoint>> refreshTargetPoints(String headerId) async {
    try {
      // El backend soporta ?header=<uuid>. Antes Flutter descargaba TODOS los
      // target-points y filtraba después, lo que hacía lenta la pantalla y podía
      // dejar datos incompletos si una página fallaba.
      final maps = await _paged(
        'api/v1/monitoring/phyto/target-points/',
        query: {'header': headerId},
      );
      final remote = maps
          .map(TargetPoint.fromJson)
          .where((e) => e.id.isNotEmpty && e.headerId == headerId)
          .toList(growable: false);
      await _local.cacheTargetPoints(remote);
    } catch (_) {
      // Offline es un estado normal de trabajo en campo.
    }
    return _sortTargets(await _local.targetPoints(headerId));
  }

  Future<TargetPoint> createLocalTarget({
    required String headerId,
    required double latitude,
    required double longitude,
  }) async {
    final current = await _local.targetPoints(headerId);
    final captured = await _local.checkpointsForHeader(headerId);
    final capturedTargetIds = captured.map((e) => e.targetId).toSet();
    final usedNumbers = current.map((e) => e.visibleNumber).where((e) => e > 0).toSet();
    var number = capturedTargetIds.length + 1;
    while (usedNumbers.contains(number)) {
      number++;
    }
    final now = DateTime.now().microsecondsSinceEpoch;
    final point = TargetPoint.local(
      id: 'local_${headerId}_$now',
      headerId: headerId,
      label: 'Punto $number',
      latitude: latitude,
      longitude: longitude,
    );
    await _local.insertLocalTarget(point);
    return point;
  }

  Future<List<({double lat, double lon})>> plotPolygon(MonitoringHeader header) async {
    final cached = header.raw['_v3_plot_feature'];
    if (cached is Map) {
      final parsed = _polygonFromFeature(Map<String, dynamic>.from(cached));
      if (parsed.isNotEmpty) return parsed;
    }
    if (header.plotId == null || header.plotId!.isEmpty) return const [];

    // Evita descargar TODAS las parcelas solo para dibujar una. El detalle de
    // parcela es suficiente y reduce mucho el tiempo al abrir mapa/reporte.
    try {
      final response = await _api.get('api/v1/geo_assets/plots/${header.plotId}/');
      final data = response.data;
      if (data is Map) {
        final parsed = _polygonFromFeature(Map<String, dynamic>.from(data));
        if (parsed.isNotEmpty) return parsed;
      }
    } catch (_) {
      // Compatibilidad con instalaciones antiguas: usa el listado paginado.
    }

    try {
      final features = await _geoPlotFeatures();
      for (final feature in features) {
        if (flexibleId(feature['id']) == header.plotId) {
          return _polygonFromFeature(feature);
        }
      }
    } catch (_) {}
    return const [];
  }

  Future<List<PhytoCatalogItem>> catalogForCropCachedFirst(String? cropId) async {
    final cached = await _local.catalogForCrop(cropId);
    if (cached.isNotEmpty) {
      // El reporte no debe esperar otra descarga completa del catálogo si ya
      // existe una copia válida. Se refresca en segundo plano.
      unawaited(catalogForCrop(cropId));
      return cached;
    }
    return catalogForCrop(cropId);
  }

  Future<List<PhytoCatalogItem>> catalogForCrop(String? cropId) async {
    // Un monitoreo fitosanitario SIEMPRE debe trabajar con el catálogo del
    // cultivo de su programa. Si por algún dato antiguo no existe cropId, no
    // mostramos un catálogo global porque podría permitir registrar una plaga
    // o enfermedad que no corresponde a ese cultivo.
    final normalizedCropId = cropId?.trim();
    if (normalizedCropId == null || normalizedCropId.isEmpty) {
      return const <PhytoCatalogItem>[];
    }

    try {
      // Es el mismo filtro oficial que usa el frontend web:
      // GET /agro-catalogs/phytosanitary/?default_crop=<id>
      final remote = await _paged(
        'api/v1/agro-catalogs/phytosanitary/',
        query: {
          'default_crop': int.tryParse(normalizedCropId) ?? normalizedCropId,
        },
      );
      var items = remote
          .map(PhytoCatalogItem.fromJson)
          .where((e) => e.id.isNotEmpty && !e.isNoPest)
          .toList(growable: false);

      // El listado puede traer stage_photos resumido. Consultamos detalle solo
      // para los elementos devueltos por ESE cultivo y así recuperamos las
      // fotografías de plaga/enfermedad y de cada fase cargadas en producción.
      items = await _hydrateCatalog(items, normalizedCropId);
      items = _filterCatalog(items, normalizedCropId);
      await _local.cacheCatalog(items);
      return items;
    } catch (_) {
      // Offline: únicamente el catálogo cacheado del mismo cultivo. Nunca se
      // cae al catálogo global.
      return _local.catalogForCrop(normalizedCropId);
    }
  }

  Future<List<Map<String, dynamic>>> remoteCheckpoints(String headerId) async {
    try {
      return await _paged(
        'api/v1/monitoring/phyto/checkpoints/',
        query: {'header': headerId},
      );
    } catch (_) {
      return const [];
    }
  }

  Future<List<PendingCheckpoint>> localCheckpoints(String headerId) =>
      _local.checkpointsForHeader(headerId);

  Future<List<PendingCheckpoint>> targetLocalCheckpoints(String targetId) =>
      _local.checkpointsForTarget(targetId);

  Future<int> pendingCount(String headerId) async {
    final checkpoints = await _local.pendingCheckpoints(headerId: headerId);
    final targets = await _local.pendingTargets(headerId: headerId);
    return checkpoints.length + targets.length +
        ((await _local.meta(_headerPatchKey(headerId))) == null ? 0 : 1);
  }

  Future<void> saveLocalPoint({
    required String headerId,
    required TargetPoint target,
    required List<PendingCheckpoint> checkpoints,
  }) async {
    if (target.headerId != headerId) {
      throw Exception('El punto no pertenece al monitoreo seleccionado.');
    }
    if (checkpoints.isEmpty) {
      throw Exception('No hay registros para guardar.');
    }
    if (checkpoints.any(
      (checkpoint) =>
          checkpoint.headerId != headerId || checkpoint.targetId != target.id,
    )) {
      throw Exception('Hay capturas que no corresponden al punto seleccionado.');
    }
    await _local.saveCapturedPoint(target.id, checkpoints);
  }

  /// Igual que Kotlin: sincroniza primero targets, luego checkpoints/fotos y al
  /// final el PATCH del header. Un fallo no borra ninguna captura local.
  Future<SyncResult> syncPending({String? headerId}) async {
    var uploaded = 0;
    var failed = 0;
    var connectionUnavailable = false;
    final errors = <String>[];

    void rememberError(Object error) {
      final message = _friendlySyncError(error);
      if (!errors.contains(message)) errors.add(message);
      if (_isConnectivityError(error)) connectionUnavailable = true;
    }

    final pendingTargets = await _local.pendingTargets(headerId: headerId);
    for (final target in pendingTargets) {
      if (connectionUnavailable) break;
      try {
        await _syncTarget(target);
        uploaded++;
      } catch (error) {
        failed++;
        rememberError(error);
        await _local.markTargetFailed(target.id, error);
      }
    }

    final pending = await _local.pendingCheckpoints(headerId: headerId);
    for (final checkpoint in pending) {
      if (connectionUnavailable) break;
      try {
        await _local.markSyncing(checkpoint.localId);
        final targetServerId = await _local.targetServerId(checkpoint.targetId);
        if (targetServerId == null || targetServerId.isEmpty) {
          throw Exception('El punto todavía no está vinculado con el servidor.');
        }

        // Si el JSON ya fue creado en un intento anterior, NO se vuelve a
        // hacer POST. Esto evita duplicados cuando lo único que falló fue la foto.
        var remoteId = checkpoint.remoteId;
        if (remoteId == null || remoteId.isEmpty) {
          final body = _checkpointBody(checkpoint, targetServerId: targetServerId);
          final response = await _api.post(
            'api/v1/monitoring/phyto/checkpoints/create/',
            data: body,
          );
          if (response.data is! Map) {
            throw Exception('El servidor no confirmó la captura.');
          }
          final map = Map<String, dynamic>.from(response.data as Map);
          remoteId = flexibleId(map['id']);
          if (remoteId == null) {
            throw Exception('El servidor no devolvió el ID de la captura.');
          }
          // Persistimos inmediatamente el vínculo remoto antes de tocar la foto.
          await _local.setCheckpointRemoteId(checkpoint.localId, remoteId);
        }

        final confirmedRemoteId = remoteId;
        if (confirmedRemoteId == null || confirmedRemoteId.isEmpty) {
          throw Exception('La captura todavía no tiene ID remoto confirmado.');
        }

        final photoPath = checkpoint.photoPath?.trim();
        if (photoPath != null && photoPath.isNotEmpty) {
          final photo = File(photoPath);
          if (!await photo.exists()) {
            throw Exception(
              'La evidencia fotográfica ya no está disponible en el teléfono.',
            );
          }
          // La foto se sube DESPUÉS del checkpoint por PATCH multipart, igual
          // que la app Android. Si falla, el registro queda pendiente para
          // reintentar y nunca se marca como sincronizado.
          await _uploadPhotoEvidenceZip(
            checkpoint.headerId,
            photoPath,
          );
        }

        await _local.markSynced(checkpoint.localId, confirmedRemoteId);
        uploaded++;
      } catch (error) {
        failed++;
        rememberError(error);
        await _local.markFailed(checkpoint.localId, error);
      }
    }

    // Repara registros de versiones anteriores que quedaron marcados como
    // sincronizados aunque el backend nunca guardó el multipart `photo`.
    // Solo se revisa la sesión actual para no hacer descargas masivas.
    if (!connectionUnavailable && headerId != null) {
      try {
        uploaded += await _repairMissingRemotePhotos(headerId);
      } catch (error) {
        failed++;
        rememberError(error);
      }
    }

    if (!connectionUnavailable) {
      final headerIds = headerId == null
          ? <String>{
              ...pending.map((e) => e.headerId),
              ...pendingTargets.map((e) => e.headerId),
            }
          : <String>{headerId};
      for (final id in headerIds) {
        if (connectionUnavailable) break;
        try {
          if (await _syncQueuedHeaderPatch(id)) uploaded++;
        } catch (error) {
          failed++;
          rememberError(error);
        }
      }
    }

    if (connectionUnavailable) {
      final remainingTargets =
          await _local.pendingTargets(headerId: headerId);
      final remainingCheckpoints =
          await _local.pendingCheckpoints(headerId: headerId);
      final remaining = remainingTargets.length + remainingCheckpoints.length;
      return SyncResult(
        uploaded: uploaded,
        failed: remaining == 0 ? failed : remaining,
        message: uploaded > 0
            ? '$uploaded cambio(s) se alcanzaron a sincronizar. El resto sigue guardado en el teléfono. No hay conexión con CIAGRO; revisa Internet o que el túnel/backend esté activo y vuelve a tocar Sincronizar.'
            : 'Sin conexión con CIAGRO. Tus registros y evidencias siguen guardados en el teléfono. Revisa Internet o que el túnel/backend esté activo y vuelve a tocar Sincronizar.',
      );
    }

    if (uploaded == 0 && failed == 0) {
      return const SyncResult(
        uploaded: 0,
        failed: 0,
        message: 'No hay cambios pendientes de sincronización.',
      );
    }

    return SyncResult(
      uploaded: uploaded,
      failed: failed,
      message: failed == 0
          ? '$uploaded cambio(s) sincronizado(s) correctamente, incluidas las evidencias disponibles.'
          : "$uploaded sincronizado(s), $failed pendiente(s). ${errors.isEmpty ? 'Tus datos siguen guardados en el teléfono para reintentar.' : errors.first}",
    );
  }

  Future<void> startHeader(String headerId) async {
    await _queueHeaderPatch(
      headerId,
      status: 'in_progress',
      additionalNotes: null,
      extra: {'started_at': DateTime.now().toUtc().toIso8601String()},
    );
    await _tryQueuedHeaderPatch(headerId);
  }

  Future<void> pauseHeader(String headerId) async {
    await _queueHeaderPatch(
      headerId,
      status: 'in_progress',
      additionalNotes: 'PAUSADO',
    );
    await _tryQueuedHeaderPatch(headerId);
  }

  Future<void> resumeHeader(String headerId) async {
    await _queueHeaderPatch(
      headerId,
      status: 'in_progress',
      additionalNotes: null,
    );
    await _tryQueuedHeaderPatch(headerId);
  }

  Future<void> completeHeader(String headerId, {String? notes}) async {
    final records = await _local.checkpointsForHeader(headerId);
    if (records.isEmpty) {
      throw Exception('Debes guardar al menos un punto antes de finalizar el monitoreo.');
    }
    await _queueHeaderPatch(
      headerId,
      status: 'completed',
      additionalNotes: notes,
      extra: {'finished_at': DateTime.now().toUtc().toIso8601String()},
    );
    // Igual que Kotlin: el cierre es local. El PATCH remoto final se hace desde
    // la sincronización del reporte, DESPUÉS de targets, capturas y evidencias.
  }

  Future<bool> autoCloseIfExpired(MonitoringHeader header) async {
    if (header.isCompleted) return true;
    if (header.endDate == null) return false;
    final end = DateTime.tryParse(header.endDate!);
    if (end == null) return false;
    final endOfDay = DateTime(end.year, end.month, end.day, 23, 59, 59);
    if (!DateTime.now().isAfter(endOfDay)) return false;

    await _queueHeaderPatch(
      header.id,
      status: 'completed',
      additionalNotes: 'CERRADO_AUTOMATICO: terminó la fecha del programa.',
      extra: {'finished_at': DateTime.now().toUtc().toIso8601String()},
    );
    await _tryQueuedHeaderPatch(header.id);
    return true;
  }

  Future<void> _syncTarget(TargetPoint target) async {
    if (target.serverId != null && target.serverId!.isNotEmpty) {
      await _local.markTargetSynced(target.id, target.serverId!);
      return;
    }
    final response = await _api.post(
      'api/v1/monitoring/phyto/target-points/create/',
      data: {
        'header': target.headerId,
        'geom': {
          'type': 'Point',
          'coordinates': [target.longitude, target.latitude],
        },
        'radius_m': target.radiusM < 1 ? 5.0 : target.radiusM,
        'label': target.label,
        'status': 'pending',
      },
    );
    if (response.data is! Map) {
      throw Exception('el servidor no confirmó el punto');
    }
    final serverId = flexibleId((response.data as Map)['id']);
    if (serverId == null) throw Exception('el servidor no devolvió ID del punto');
    await _local.markTargetSynced(target.id, serverId);
  }

  Future<void> _queueHeaderPatch(
    String headerId, {
    required String status,
    String? additionalNotes,
    Map<String, dynamic>? extra,
  }) async {
    await _local.updateHeaderState(
      headerId,
      status: status,
      additionalNotes: additionalNotes,
    );
    final body = <String, dynamic>{
      'status': status,
      'additional_notes': additionalNotes,
      ...?extra,
    };
    await _local.setMeta(_headerPatchKey(headerId), jsonEncode(body));
  }

  Future<void> _tryQueuedHeaderPatch(String headerId) async {
    try {
      await _syncQueuedHeaderPatch(headerId);
    } catch (_) {
      // Offline: queda en meta y se reintenta con Sincronizar.
    }
  }

  Future<bool> _syncQueuedHeaderPatch(String headerId) async {
    final encoded = await _local.meta(_headerPatchKey(headerId));
    if (encoded == null || encoded.trim().isEmpty) return false;
    final body = Map<String, dynamic>.from(jsonDecode(encoded) as Map);

    // El backend puede bloquear nuevos checkpoints/fotos después de completed.
    // Por eso el cierre remoto espera hasta que NO quede nada pendiente.
    if ('${body['status'] ?? ''}'.toLowerCase() == 'completed') {
      final targets = await _local.pendingTargets(headerId: headerId);
      final checkpoints = await _local.pendingCheckpoints(headerId: headerId);
      if (targets.isNotEmpty || checkpoints.isNotEmpty) return false;
    }

    await _api.patch(
      'api/v1/monitoring/phyto/headers/$headerId/update/',
      data: body,
    );
    await _local.deleteMeta(_headerPatchKey(headerId));
    return true;
  }

  String _headerPatchKey(String headerId) => 'phyto_header_patch:$headerId';

  Map<String, dynamic> _checkpointBody(
    PendingCheckpoint checkpoint, {
    required String targetServerId,
  }) {
    final phytoId = checkpoint.phytoIssueId == null
        ? null
        : int.tryParse(checkpoint.phytoIssueId!);
    if (!checkpoint.isNoPest && phytoId == null) {
      throw Exception(
        '${checkpoint.phytoName}: el fitosanitario no tiene un ID numérico válido.',
      );
    }

    final diseaseAbsent = checkpoint.isDisease &&
        checkpoint.presenceStatus == 'low' && checkpoint.qty == 0;
    final stage = checkpoint.isNoPest || diseaseAbsent
        ? null
        : _normalizeStage(checkpoint.stage);

    // Las plagas sin etapas explícitas sí se permiten como presencia general.
    if (checkpoint.isDisease && !diseaseAbsent && stage == null) {
      throw Exception('${checkpoint.phytoName}: falta seleccionar una fase.');
    }

    return {
      'header': checkpoint.headerId,
      'target': targetServerId,
      'phyto_issue': checkpoint.isNoPest ? null : phytoId,
      'stage': stage,
      'presence_status': checkpoint.presenceStatus,
      'qty': checkpoint.isNoPest || diseaseAbsent ? 0 : checkpoint.qty,
      'geom': {
        'type': 'Point',
        'coordinates': [checkpoint.longitude, checkpoint.latitude],
      },
      'notes': _cleanUserNotes(checkpoint.notes),
      'photo_ref': checkpoint.photoPath == null
          ? null
          : p.basename(checkpoint.photoPath!),
      'captured_at': checkpoint.capturedAt.toUtc().toIso8601String(),
    };
  }

  Future<int> _repairMissingRemotePhotos(String headerId) async {
    final local = await _local.checkpointsForHeader(headerId);
    final candidates = local.where((checkpoint) {
      final remoteId = checkpoint.remoteId?.trim();
      final photoPath = checkpoint.photoPath?.trim();
      return checkpoint.syncState == 'synced' &&
          remoteId != null &&
          remoteId.isNotEmpty &&
          photoPath != null &&
          photoPath.isNotEmpty;
    }).toList(growable: false);
    if (candidates.isEmpty) return 0;

    // Aquí NO usamos remoteCheckpoints(), porque ese método oculta los errores
    // de red para permitir trabajar offline. En una sincronización necesitamos
    // distinguir claramente entre "sin foto" y "sin conexión".
    final remote = await _paged(
      'api/v1/monitoring/phyto/checkpoints/',
      query: {'header': headerId},
    );
    final remoteById = <String, Map<String, dynamic>>{};
    for (final item in remote) {
      final id = flexibleId(item['id']);
      if (id != null) remoteById[id] = item;
    }

    var repaired = 0;
    for (final checkpoint in candidates) {
      final remoteId = checkpoint.remoteId!.trim();
      final serverItem = remoteById[remoteId];
      if (serverItem == null) continue;

      // El backend de CIAGRO puede devolver la evidencia como `photo` o
      // `photo_url`. `photo_ref` es solo el nombre local de referencia.
      final remotePhoto =
          photoUrlFrom(serverItem['photo_url']) ?? photoUrlFrom(serverItem['photo']);
      if (remotePhoto != null) continue;

      final file = File(checkpoint.photoPath!.trim());
      if (!await file.exists()) continue;

      try {
        // Capturas creadas con versiones viejas pueden existir en el servidor
        // sin photo_ref. El endpoint oficial de evidencias enlaza por ese nombre,
        // así que primero lo reconciliamos y luego subimos el ZIP.
        final remotePhotoRef = _cleanText(serverItem['photo_ref']);
        final expectedPhotoRef = p.basename(file.path);
        if (remotePhotoRef == null || remotePhotoRef != expectedPhotoRef) {
          await _api.patch(
            'api/v1/monitoring/phyto/checkpoints/$remoteId/update/',
            data: {'photo_ref': expectedPhotoRef},
          );
        }

        await _uploadPhotoEvidenceZip(headerId, file.path);
        await _local.markSynced(checkpoint.localId, remoteId);
        repaired++;
      } catch (error) {
        // Convertimos el registro histórico en pendiente para que el usuario
        // pueda reintentarlo posteriormente sin perder la evidencia local.
        await _local.markFailed(checkpoint.localId, error);
        rethrow;
      }
    }
    return repaired;
  }

  /// Sube una evidencia usando el contrato que el backend utiliza para
  /// relacionar `photo_ref` -> archivo real.
  ///
  /// El checkpoint JSON ya contiene `photo_ref` (basename de [filePath]).
  /// El servidor recibe después un ZIP multipart en `photos_zip` y vincula
  /// cada entrada por nombre. Esto hace que el frontend web reciba finalmente
  /// `photo` con una URL de MEDIA, en lugar de quedarse solo con la referencia.
  Future<void> _uploadPhotoEvidenceZip(
    String headerId,
    String filePath,
  ) async {
    final photo = File(filePath);
    if (!await photo.exists()) {
      throw Exception(
        'La evidencia fotográfica ya no está disponible en el teléfono.',
      );
    }

    final fileName = p.basename(filePath);
    final bytes = await photo.readAsBytes();
    if (bytes.isEmpty) {
      throw Exception('La evidencia fotográfica está vacía.');
    }

    final archive = Archive()
      ..addFile(ArchiveFile(fileName, bytes.length, bytes));
    final zipBytes = ZipEncoder().encode(archive);
    if (zipBytes == null || zipBytes.isEmpty) {
      throw Exception('No se pudo preparar la evidencia para sincronizar.');
    }

    final form = FormData.fromMap({
      'header': headerId,
      'photos_zip': MultipartFile.fromBytes(
        zipBytes,
        filename: 'evidencia_${DateTime.now().millisecondsSinceEpoch}.zip',
      ),
    });

    final response = await _api.post(
      'api/v1/monitoring/phyto/checkpoints/upload-photos/',
      data: form,
    );

    if (response.data is! Map) {
      throw Exception('El servidor no confirmó la evidencia fotográfica.');
    }

    final data = Map<String, dynamic>.from(response.data as Map);
    final matched = _flexibleInt(data['matched']) ?? 0;
    final unmatched = _stringList(data['unmatched_files']);

    // Un 200 con matched=0 es válido para el endpoint, pero NO para este
    // archivo: significaría que photo_ref y el nombre enviado no coinciden.
    if (matched <= 0 || unmatched.contains(fileName)) {
      throw Exception(
        'La foto llegó al servidor, pero no pudo vincularse con la captura. '
        'Vuelve a sincronizar; tus datos siguen guardados en el teléfono.',
      );
    }
  }

  int? _flexibleInt(dynamic value) {
    if (value is int) return value;
    return int.tryParse(value?.toString() ?? '');
  }

  List<String> _stringList(dynamic value) {
    if (value is! List) return const <String>[];
    return value
        .map((item) => item?.toString().trim() ?? '')
        .where((item) => item.isNotEmpty)
        .toList(growable: false);
  }

  String? _cleanText(dynamic value) {
    final text = value?.toString().trim();
    return text == null || text.isEmpty ? null : text;
  }

  String? _cleanUserNotes(String? notes) {
    final clean = (notes ?? '')
        .replaceAll(RegExp(r'\[SEV_PUNTO:M=\d+\]'), '')
        .replaceAll(RegExp(r'\[SEV_PUNTO:m=\d+;M=\d+\]'), '')
        .trim();
    return clean.isEmpty ? null : clean;
  }

  bool _isConnectivityError(Object error) {
    if (error is SocketException) return true;
    if (error is DioException) {
      if (error.type == DioExceptionType.connectionError ||
          error.type == DioExceptionType.connectionTimeout ||
          error.type == DioExceptionType.sendTimeout ||
          error.type == DioExceptionType.receiveTimeout) {
        return true;
      }
      final inner = error.error;
      if (inner is SocketException) return true;
      final text = error.toString().toLowerCase();
      return text.contains('failed host lookup') ||
          text.contains('network is unreachable') ||
          text.contains('connection refused') ||
          text.contains('connection reset');
    }
    final text = error.toString().toLowerCase();
    return text.contains('failed host lookup') ||
        text.contains('socketexception') ||
        text.contains('network is unreachable') ||
        text.contains('connection refused');
  }

  String _friendlySyncError(Object error) {
    if (_isConnectivityError(error)) {
      return 'No se pudo conectar con CIAGRO. Tus datos siguen guardados para reintentar.';
    }
    if (error is DioException) {
      final status = error.response?.statusCode;
      if (status == 401) {
        return 'La sesión venció. Vuelve a iniciar sesión y después sincroniza.';
      }
      if (status == 403) {
        return 'El servidor rechazó esta operación por permisos.';
      }
      if (status == 404) {
        return 'El servidor no encontró uno de los registros que se intentó sincronizar.';
      }
      if (status != null && status >= 500) {
        return 'CIAGRO respondió con un error temporal. Tus datos siguen guardados para reintentar.';
      }
    }
    final text = error.toString().replaceFirst('Exception: ', '').trim();
    return text.isEmpty
        ? 'No se pudo sincronizar este registro. Se conservará para reintentar.'
        : text;
  }

  String? _normalizeStage(String? stage) {
    if (stage == null || stage.trim().isEmpty) return null;
    final value = stage.trim().toLowerCase().replaceAll('_', ' ');
    if (value.contains('adulto') && value.contains('alas')) return 'adulto_alas';
    if (value.contains('huev')) return 'huevesillo';
    if (value.contains('larva') || value.contains('joven')) return 'larva';
    if (value.contains('ninfa')) return 'ninfa';
    if (value.contains('pupa')) return 'pupa';
    if (value == 'adulto') return 'adulto';
    if (value.contains('inicio')) return 'inicio';
    if (value.contains('desarrollo')) return 'desarrollo';
    if (value.contains('avanz')) return 'avanzado';
    if (value.contains('terminal') || value.contains('podrido')) return 'terminal';
    return value.replaceAll(' ', '_');
  }

  Future<List<PhytoCatalogItem>> _hydrateCatalog(
    List<PhytoCatalogItem> items,
    String? cropId,
  ) async {
    final output = List<PhytoCatalogItem>.from(items);
    final indexes = <int>[];
    for (var i = 0; i < output.length; i++) {
      final item = output[i];
      final possibleCrop = cropId == null ||
          item.cropId == null ||
          item.cropId!.isEmpty ||
          item.cropId == cropId;
      if (possibleCrop &&
          (item.cropId == null ||
              !item.hasExplicitStageData ||
              !item.hasStageMediaData ||
              item.photo == null)) {
        indexes.add(i);
      }
    }

    for (var offset = 0; offset < indexes.length; offset += 6) {
      final chunk = indexes.skip(offset).take(6).toList();
      final results = await Future.wait(
        chunk.map((index) async {
          final base = output[index];
          try {
            final response = await _api.get(
              'api/v1/agro-catalogs/phytosanitary/${base.id}/',
            );
            if (response.data is Map) {
              return MapEntry(
                index,
                base.mergeDetail(
                  PhytoCatalogItem.fromJson(
                    Map<String, dynamic>.from(response.data as Map),
                  ),
                ),
              );
            }
          } catch (_) {}
          return MapEntry(index, base);
        }),
      );
      for (final result in results) {
        output[result.key] = result.value;
      }
    }
    return output;
  }

  List<PhytoCatalogItem> _filterCatalog(
    List<PhytoCatalogItem> items,
    String? cropId,
  ) {
    final normalizedCropId = cropId?.trim();
    if (normalizedCropId == null || normalizedCropId.isEmpty) {
      return const <PhytoCatalogItem>[];
    }

    // No mezclar elementos genéricos o de otros cultivos. El endpoint ya fue
    // filtrado por default_crop; este segundo filtro evita que datos antiguos
    // o respuestas inconsistentes entren al registro.
    return items
        .where((e) =>
            !e.isNoPest &&
            e.cropId != null &&
            e.cropId!.trim() == normalizedCropId)
        .toList(growable: false);
  }

  List<TargetPoint> _sortTargets(List<TargetPoint> points) {
    final copy = List<TargetPoint>.from(points);
    copy.sort((a, b) {
      if (a.visibleNumber > 0 && b.visibleNumber > 0) {
        return a.visibleNumber.compareTo(b.visibleNumber);
      }
      return a.label.compareTo(b.label);
    });
    return copy;
  }

  List<MonitoringHeader> _offlineWindow(List<MonitoringHeader> items) {
    if (items.isEmpty) return items;
    final cutoff = DateTime.now().subtract(const Duration(days: 30));
    final sorted = _sortHeaders(List<MonitoringHeader>.from(items));
    final recent = <String, MonitoringHeader>{};

    for (final header in sorted) {
      final state = header.status.toLowerCase();
      final protected = state.contains('pending') ||
          state.contains('pendiente') ||
          state.contains('progress') ||
          state.contains('progreso') ||
          header.isPaused;
      final date = DateTime.tryParse(header.startDate ?? '') ??
          DateTime.tryParse(header.endDate ?? '');
      if (protected || date == null || !date.toLocal().isBefore(cutoff)) {
        recent[header.id] = header;
      }
    }

    // Igual que Kotlin: aunque el teléfono pase mucho tiempo sin conectarse,
    // se conservan al menos los cinco monitoreos más recientes como respaldo.
    for (final header in sorted.take(5)) {
      recent[header.id] = header;
    }
    return _sortHeaders(recent.values.toList(growable: false));
  }

  List<MonitoringHeader> _sortHeaders(List<MonitoringHeader> items) {
    final copy = List<MonitoringHeader>.from(items);
    copy.sort((a, b) {
      final ad = DateTime.tryParse(a.startDate ?? '');
      final bd = DateTime.tryParse(b.startDate ?? '');
      if (ad != null && bd != null) return bd.compareTo(ad);
      return a.programName.compareTo(b.programName);
    });
    return copy;
  }

  Map<String, Map<String, dynamic>> _byId(List<Map<String, dynamic>> rows) {
    final result = <String, Map<String, dynamic>>{};
    for (final row in rows) {
      final id = flexibleId(row['id'] ?? row['ext_id']);
      if (id != null) result[id] = row;
    }
    return result;
  }

  String _entityLabel(
    Map<String, dynamic>? item, {
    List<String> preferred = const ['commercial_name', 'name', 'nombre', 'display_name'],
    required String fallback,
  }) {
    if (item == null) return fallback;
    return firstText(item, preferred) ?? fallback;
  }

  String _plotLabel(
    Map<String, dynamic>? feature,
    Map<String, dynamic> props,
  ) {
    if (feature == null) return 'Parcela sin nombre';
    return firstText(props, const ['name', 'nombre', 'description', 'display_name']) ??
        'Parcela sin nombre';
  }

  List<({double lat, double lon})> _polygonFromFeature(
    Map<String, dynamic> feature,
  ) {
    final geometry = feature['geometry'];
    if (geometry is! Map) return const [];
    final coordinates = geometry['coordinates'];
    if (coordinates is! List || coordinates.isEmpty) return const [];

    // GeoJSON Polygon: [ring[ [lon,lat], ... ]]. MultiPolygon: [[ring...]].
    dynamic ring = coordinates;
    while (ring is List && ring.isNotEmpty &&
        ring.first is List && (ring.first as List).isNotEmpty &&
        (ring.first as List).first is List) {
      ring = ring.first;
    }
    if (ring is! List) return const [];
    final output = <({double lat, double lon})>[];
    for (final pair in ring) {
      if (pair is List && pair.length >= 2) {
        final lon = double.tryParse('${pair[0]}');
        final lat = double.tryParse('${pair[1]}');
        if (lat != null && lon != null) output.add((lat: lat, lon: lon));
      }
    }
    return output;
  }

  Future<List<Map<String, dynamic>>> _geoPlotFeatures() async {
    final all = <Map<String, dynamic>>[];
    for (var page = 1; page <= 500; page++) {
      final response = await _api.get(
        'api/v1/geo_assets/plots/',
        queryParameters: {'page': page},
      );
      final data = response.data;
      if (data is! Map) break;
      final results = data['results'];
      if (results is Map && results['features'] is List) {
        all.addAll(
          (results['features'] as List)
              .whereType<Map>()
              .map((e) => Map<String, dynamic>.from(e)),
        );
      } else if (results is List) {
        all.addAll(
          results.whereType<Map>().map((e) => Map<String, dynamic>.from(e)),
        );
      }
      if (data['next'] == null || '${data['next']}'.trim().isEmpty) break;
    }
    return all;
  }

  Future<List<Map<String, dynamic>>> _safePaged(
    String path, {
    Map<String, dynamic>? query,
  }) async {
    try {
      return await _paged(path, query: query);
    } catch (_) {
      return const [];
    }
  }

  Future<List<Map<String, dynamic>>> _paged(
    String path, {
    Map<String, dynamic>? query,
  }) async {
    final all = <Map<String, dynamic>>[];
    final baseQuery = <String, dynamic>{...?query}
      ..removeWhere((_, value) => value == null || '$value'.trim().isEmpty);

    for (var page = 1; page <= 500; page++) {
      final response = await _api.get(
        path,
        queryParameters: {...baseQuery, 'page': page},
      );
      final batch = ApiClient.unpackResults(response.data);
      all.addAll(batch);

      if (response.data is! Map) break;
      final root = response.data as Map;
      if (root['next'] == null || '${root['next']}'.trim().isEmpty) break;
    }
    return all;
  }
}

class SyncResult {
  const SyncResult({
    required this.uploaded,
    required this.failed,
    required this.message,
  });

  final int uploaded;
  final int failed;
  final String message;

  bool get ok => failed == 0;
}

import 'dart:convert';

String _text(dynamic value) => value == null ? '' : value.toString().trim();

String? flexibleId(dynamic value) {
  if (value == null) return null;
  if (value is num || value is String) {
    final text = _text(value);
    return text.isEmpty ? null : text;
  }
  if (value is Map) {
    for (final key in const ['id', 'uuid', 'ext_id', 'extId', 'pk']) {
      final candidate = flexibleId(value[key]);
      if (candidate != null) return candidate;
    }
  }
  return null;
}

String? firstText(Map<String, dynamic> json, List<String> keys) {
  for (final key in keys) {
    final value = json[key];
    if (value is String && value.trim().isNotEmpty) return value.trim();
    if (value is num) return value.toString();
  }
  return null;
}

bool _looksLikeImagePath(String value) {
  final text = value.trim().toLowerCase();
  if (text.isEmpty) return false;
  return text.startsWith('http://') ||
      text.startsWith('https://') ||
      text.startsWith('/media/') ||
      text.startsWith('media/') ||
      text.startsWith('/uploads/') ||
      text.startsWith('uploads/') ||
      text.startsWith('/files/') ||
      text.startsWith('files/') ||
      text.startsWith('/api/v1/core/attachments/') ||
      text.startsWith('api/v1/core/attachments/') ||
      text.endsWith('.jpg') ||
      text.endsWith('.jpeg') ||
      text.endsWith('.png') ||
      text.endsWith('.webp') ||
      text.endsWith('.gif') ||
      text.endsWith('.avif');
}

String? photoUrlFrom(dynamic value) {
  if (value is String) {
    final clean = value.trim();
    return _looksLikeImagePath(clean) ? clean : null;
  }
  if (value is List) {
    for (final entry in value) {
      final result = photoUrlFrom(entry);
      if (result != null) return result;
    }
  }
  if (value is Map) {
    const preferredKeys = [
      'download_url', 'file_url', 'image_url', 'photo_url', 'absolute_url',
      'content_url', 'original_url', 'source_url', 'thumbnail_url',
      'attachment_url', 'download', 'photo', 'image', 'file', 'thumbnail',
      'path', 'url', 'href', 'resource_url', 'detail_url', 'api_url',
    ];
    for (final key in preferredKeys) {
      final result = photoUrlFrom(value[key]);
      if (result != null) return result;
    }
    for (final entry in value.values) {
      final result = photoUrlFrom(entry);
      if (result != null) return result;
    }
  }
  return null;
}

String normalizePhytoType(String raw, String name) {
  final value = '$raw $name'
      .toLowerCase()
      .replaceAll('á', 'a')
      .replaceAll('é', 'e')
      .replaceAll('í', 'i')
      .replaceAll('ó', 'o')
      .replaceAll('ú', 'u');
  const diseaseWords = [
    'disease', 'enfermedad', 'roya', 'antracnosis', 'tizon', 'mosaico',
    'mancha', 'moho', 'virus', 'carbon', 'pudricion', 'fusarium',
    'achaparramiento', 'bacter', 'mildiu', 'cenicilla',
  ];
  if (diseaseWords.any(value.contains)) return 'Enfermedad';
  if (value.contains('sin plaga') || value.contains('sin_plaga')) return 'SIN_PLAGA';
  return 'Plaga';
}

class MonitoringHeader {
  const MonitoringHeader({
    required this.id,
    required this.status,
    required this.cropId,
    required this.plotId,
    required this.assignedTo,
    required this.dataCentralId,
    required this.startDate,
    required this.endDate,
    required this.raw,
  });

  final String id;
  final String status;
  final String? cropId;
  final String? plotId;
  final String? assignedTo;
  final String? dataCentralId;
  final String? startDate;
  final String? endDate;
  final Map<String, dynamic> raw;

  factory MonitoringHeader.fromJson(Map<String, dynamic> json) => MonitoringHeader(
        id: flexibleId(json['id']) ?? '',
        status: _text(json['status']).isEmpty ? 'pending' : _text(json['status']),
        // El crop del MonitoringHeader viene del backend y manda sobre
        // cualquier enriquecimiento local/cacheado de versiones anteriores.
        cropId: flexibleId(json['crop']) ??
            flexibleId(json['crop_id']) ??
            flexibleId(json['_v3_crop_id']),
        plotId: flexibleId(json['_v3_plot_id'] ?? json['plot']),
        assignedTo: flexibleId(json['assigned_to']),
        dataCentralId: flexibleId(
          json['_v3_data_central'] ??
              json['datacentral'] ??
              json['data_central'] ??
              json['data_central_id'],
        ),
        startDate: firstText(json, const [
          '_v3_start_date',
          'estimated_start_date',
          'start_date',
        ]),
        endDate: firstText(json, const [
          '_v3_end_date',
          'estimated_end_date',
          'estimated_finish_date',
          'est_finish_date',
          'finish_date',
        ]),
        raw: json,
      );

  String get fieldTaskId => flexibleId(raw['field_task']) ?? '';
  String get programName => firstText(raw, const [
        '_v3_program_name', 'program_name', 'title', 'name'
      ]) ?? 'Monitoreo';
  String get producerName => firstText(raw, const [
        '_v3_producer_name', 'producer_name', 'agro_unit_name'
      ]) ?? 'Productor sin nombre';
  String get ranchName => firstText(raw, const [
        '_v3_ranch_name', 'ranch_name'
      ]) ?? 'Rancho sin nombre';

  /// Igual que la app Kotlin: para identificar una parcela se muestra primero
  /// su código. Si el backend no lo envía, se conserva el nombre como respaldo.
  String get plotName => firstText(raw, const [
        '_v3_plot_code', 'plot_code', '_v3_plot_name', 'plot_name'
      ]) ?? 'Parcela sin nombre';

  String? get plotCode => firstText(raw, const [
        '_v3_plot_code', 'plot_code'
      ]);
  String get cropName {
    final embedded = raw['crop'];
    if (embedded is Map) {
      final nested = firstText(
        Map<String, dynamic>.from(embedded),
        const ['name', 'nombre', 'variety'],
      );
      if (nested != null) return nested;
    }
    // Si el backend expone crop_name, se prefiere sobre el valor enriquecido
    // localmente. `_v3_crop_name` queda como respaldo para el API actual.
    return firstText(raw, const ['crop_name', '_v3_crop_name']) ??
        'Cultivo sin nombre';
  }

  /// Fotografía de referencia cargada en el catálogo de cultivos de CIAGRO.
  /// Puede ser absoluta, /media/... o attachment; AuthenticatedRemoteImage se
  /// encarga de resolverla con el JWT de la sesión.
  String? get cropPhoto =>
      photoUrlFrom(raw['crop_photo']) ??
      photoUrlFrom(raw['crop']) ??
      photoUrlFrom(raw['_v3_crop_photo']);
  String? get producerId => flexibleId(raw['_v3_producer_id']);
  String? get ranchId => flexibleId(raw['_v3_ranch_id']);

  String get title => programName;

  bool get isCompleted => status.toLowerCase().contains('complet');
  bool get isCancelled => status.toLowerCase().contains('cancel');
  bool get isPaused => '${raw['additional_notes'] ?? ''}'.toUpperCase().contains('PAUSADO');

  /// Fecha real de actividad en campo.
  /// - completado/cancelado: finished_at
  /// - en progreso/pausado: started_at
  /// - pendiente: no inventa la fecha estimada como si ya se hubiera monitoreado.
  String? get startedAt => firstText(raw, const ['started_at']);
  String? get finishedAt => firstText(raw, const ['finished_at']);
  String? get activityDate {
    if (isCompleted || isCancelled) return finishedAt;
    final normalized = status.toLowerCase().replaceAll(' ', '_');
    if (normalized.contains('progress') || isPaused) return startedAt;
    return null;
  }

  String? get cancellationReason {
    if (!isCancelled) return null;
    final value = firstText(raw, const ['additional_notes']);
    if (value == null || value.toUpperCase() == 'PAUSADO') return null;
    return value;
  }

  Map<String, dynamic> toMap() => {
        'remote_id': id,
        'status': status,
        'crop_id': cropId,
        'plot_id': plotId,
        'assigned_to': assignedTo,
        'cia_id': dataCentralId,
        'start_date': startDate,
        'end_date': endDate,
        'json': jsonEncode(raw),
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      };

  factory MonitoringHeader.fromDb(Map<String, Object?> row) {
    final raw = Map<String, dynamic>.from(jsonDecode('${row['json']}') as Map);
    if (row['cia_id'] != null) raw['_v3_data_central'] = row['cia_id'];
    raw['status'] = row['status']?.toString() ?? raw['status'];
    return MonitoringHeader.fromJson(raw);
  }
}

class TargetPoint {
  const TargetPoint({
    required this.id,
    required this.headerId,
    required this.label,
    required this.latitude,
    required this.longitude,
    required this.radiusM,
    required this.status,
    required this.raw,
    this.serverId,
    this.syncState = 'synced',
    this.lastError,
  });

  /// Identificador local estable. Para puntos creados en campo es `local_*`.
  final String id;
  final String? serverId;
  final String headerId;
  final String label;
  final double latitude;
  final double longitude;
  final double radiusM;
  final String status;
  final String syncState;
  final String? lastError;
  final Map<String, dynamic> raw;

  factory TargetPoint.fromJson(Map<String, dynamic> json) {
    final geom = json['geom'];
    double lat = 0;
    double lon = 0;
    if (geom is Map && geom['coordinates'] is List) {
      final c = geom['coordinates'] as List;
      if (c.length >= 2) {
        lon = double.tryParse('${c[0]}') ?? 0;
        lat = double.tryParse('${c[1]}') ?? 0;
      }
    }
    final id = flexibleId(json['_local_id'] ?? json['id']) ?? '';
    final serverId = json.containsKey('_server_id')
        ? flexibleId(json['_server_id'])
        : flexibleId(json['server_id'] ?? json['id']);
    return TargetPoint(
      id: id,
      serverId: serverId,
      headerId: flexibleId(json['header']) ?? '',
      label: _text(json['label']).isEmpty ? 'Punto' : _text(json['label']),
      latitude: lat,
      longitude: lon,
      radiusM: double.tryParse('${json['radius_m'] ?? 5}') ?? 5,
      status: _text(json['status']).isEmpty ? 'En proceso' : _text(json['status']),
      syncState: _text(json['_sync_state']).isEmpty ? 'synced' : _text(json['_sync_state']),
      lastError: json['_last_error']?.toString(),
      raw: json,
    );
  }

  factory TargetPoint.local({
    required String id,
    required String headerId,
    required String label,
    required double latitude,
    required double longitude,
  }) {
    final raw = <String, dynamic>{
      'id': id,
      '_local_id': id,
      '_server_id': null,
      '_sync_state': 'pending',
      'header': headerId,
      'label': label,
      'radius_m': 5.0,
      'status': 'En proceso',
      'origin': 'mobile',
      'geom': {
        'type': 'Point',
        'coordinates': [longitude, latitude],
      },
    };
    return TargetPoint(
      id: id,
      serverId: null,
      headerId: headerId,
      label: label,
      latitude: latitude,
      longitude: longitude,
      radiusM: 5,
      status: 'En proceso',
      syncState: 'pending',
      raw: raw,
    );
  }

  int get visibleNumber =>
      int.tryParse(RegExp(r'\d+').firstMatch(label)?.group(0) ?? '') ?? 0;
  bool get completed {
    final value = status.trim().toLowerCase().replaceAll('_', ' ');
    // Django usa `visited` para un target ya atendido. La base Android local
    // usa `Completado`. Ambos representan el mismo estado operativo.
    return value.contains('complet') ||
        value == 'visited' ||
        value.contains('visitad') ||
        value == 'captured' ||
        value.contains('capturad');
  }
  bool get needsSync => serverId == null || syncState != 'synced';

  Map<String, dynamic> get navigationRaw => {
        ...raw,
        'id': id,
        '_local_id': id,
        '_server_id': serverId,
        '_sync_state': syncState,
        '_last_error': lastError,
        'header': headerId,
        'label': label,
        'radius_m': radiusM,
        'status': status,
        'geom': {
          'type': 'Point',
          'coordinates': [longitude, latitude],
        },
      };

  Map<String, dynamic> toMap() => {
        'remote_id': id,
        'server_id': serverId,
        'header_id': headerId,
        'label': label,
        'lat': latitude,
        'lon': longitude,
        'radius_m': radiusM,
        'status': status,
        'sync_state': syncState,
        'last_error': lastError,
        'json': jsonEncode(navigationRaw),
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      };

  factory TargetPoint.fromDb(Map<String, Object?> row) {
    final decoded = Map<String, dynamic>.from(jsonDecode('${row['json']}') as Map);
    final localId = '${row['remote_id']}';
    final serverId = row['server_id']?.toString();
    final lat = double.tryParse('${row['lat']}') ?? 0;
    final lon = double.tryParse('${row['lon']}') ?? 0;
    decoded
      ..['id'] = localId
      ..['_local_id'] = localId
      ..['_server_id'] = serverId
      ..['_sync_state'] = row['sync_state']?.toString() ?? 'synced'
      ..['_last_error'] = row['last_error']?.toString()
      ..['header'] = row['header_id']?.toString() ?? decoded['header']
      ..['label'] = row['label']?.toString() ?? decoded['label']
      ..['status'] = row['status']?.toString() ?? decoded['status']
      ..['radius_m'] = double.tryParse('${row['radius_m']}') ?? 5.0
      ..['geom'] = {
        'type': 'Point',
        'coordinates': [lon, lat],
      };
    return TargetPoint.fromJson(decoded);
  }
}

class PhytoStage {
  const PhytoStage({required this.name, this.photo});
  final String name;
  final String? photo;
}

class PhytoCatalogItem {
  const PhytoCatalogItem({
    required this.id,
    required this.name,
    required this.type,
    required this.cropId,
    required this.photo,
    required this.description,
    required this.stages,
    required this.raw,
  });

  final String id;
  final String name;
  final String type;
  final String? cropId;
  final String? photo;
  final String? description;
  final List<PhytoStage> stages;
  final Map<String, dynamic> raw;

  bool get isDisease => type.toLowerCase().contains('enfermedad') || type.toLowerCase().contains('disease');
  bool get isNoPest => type.toLowerCase().contains('sin_plaga') || name.toLowerCase() == 'sin plaga';
  bool get isPest => !isDisease && !isNoPest;
  int? get numericId => int.tryParse(id);
  bool get hasExplicitStageData {
    for (final key in const [
      'stages', 'etapas', 'development_stages', 'phases', 'fases',
      'stage_photos', 'photos', 'images', 'stage_images',
      'development_photos', 'phase_photos',
    ]) {
      final value = raw[key];
      if (value is List && value.isNotEmpty) return true;
    }
    return false;
  }

  /// Indica si el listado ya trae fotografías específicas de etapas/fases.
  /// Si solo trae nombres, el repositorio consulta el detalle para recuperar
  /// las imágenes cargadas en producción.
  bool get hasStageMediaData {
    for (final key in const [
      'stage_photos', 'photos', 'images', 'stage_images',
      'development_photos', 'phase_photos',
    ]) {
      final value = raw[key];
      if (value is List && value.isNotEmpty) return true;
    }

    for (final key in const [
      'stages', 'etapas', 'development_stages', 'phases', 'fases',
    ]) {
      final value = raw[key];
      if (value is! List) continue;
      for (final entry in value) {
        if (entry is Map && photoUrlFrom(entry) != null) return true;
      }
    }
    return false;
  }

  factory PhytoCatalogItem.fromJson(Map<String, dynamic> json) {
    final name = firstText(
          json,
          const ['name', 'nombre', 'common_name', 'label'],
        ) ??
        'Sin nombre';
    final typeRaw = firstText(
          json,
          const ['type', 'tipo', 'category', 'kind'],
        ) ??
        '';
    final type = normalizePhytoType(typeRaw, name);
    final mainPhoto = _extractMainPhoto(json);
    final stages = _extractStages(
      json,
      isDisease: type.toLowerCase().contains('enfermedad'),
    );
    return PhytoCatalogItem(
      id: flexibleId(json['id'] ?? json['uuid'] ?? json['ext_id']) ?? '',
      name: name,
      type: type,
      cropId: _extractCropId(json),
      photo: mainPhoto ?? _firstStagePhoto(stages),
      description: firstText(json, const ['description', 'descripcion', 'comments', 'notes']),
      stages: stages,
      raw: json,
    );
  }
  static String? _firstStagePhoto(List<PhytoStage> stages) {
    for (final stage in stages) {
      if (stage.photo != null && stage.photo!.trim().isNotEmpty) return stage.photo;
    }
    return null;
  }


  static String? _extractCropId(Map<String, dynamic> json) {
    for (final key in const [
      'default_crop', 'defaultCrop', 'default_crop_id', 'defaultCropId',
      'crop', 'crop_id', 'cropId',
    ]) {
      final result = flexibleId(json[key]);
      if (result != null) return result;
    }
    return null;
  }

  static String? _extractMainPhoto(Map<String, dynamic> json) {
    const directKeys = [
      'download_url', 'file_url', 'image_url', 'photo_url', 'absolute_url',
      'content_url', 'original_url', 'source_url', 'thumbnail_url',
      'attachment_url', 'download', 'photo', 'image', 'file', 'thumbnail',
      'path', 'url', 'href', 'resource_url', 'detail_url', 'api_url',
    ];
    for (final key in directKeys) {
      final result = photoUrlFrom(json[key]);
      if (result != null) return result;
    }
    for (final key in const [
      'attachments_url', 'attachment', 'attachments', 'media', 'photos',
      'images', 'additional_params',
    ]) {
      final result = photoUrlFrom(json[key]);
      if (result != null) return result;
    }
    return null;
  }

  static String _stageKey(String value) => value
      .trim()
      .toLowerCase()
      .replaceAll('_', ' ')
      .replaceAll(RegExp(r'\s+'), ' ');

  static List<PhytoStage> _extractStages(
    Map<String, dynamic> json, {
    required bool isDisease,
  }) {
    dynamic firstList(List<String> keys) {
      for (final key in keys) {
        final value = json[key];
        if (value is List && value.isNotEmpty) return value;
      }
      return null;
    }

    final stagesRaw = firstList(const [
      'stages', 'etapas', 'development_stages', 'phases', 'fases',
    ]);
    final photosRaw = firstList(const [
      'stage_photos', 'photos', 'images', 'stage_images',
      'development_photos', 'phase_photos',
    ]);

    final photosByName = <String, String>{};
    final photosByOrder = <String>[];
    final namesFromPhotos = <String>[];

    if (photosRaw is List) {
      for (final entry in photosRaw) {
        if (entry is String) {
          if (_looksLikeImagePath(entry)) photosByOrder.add(entry.trim());
          continue;
        }
        if (entry is Map) {
          final map = Map<String, dynamic>.from(entry);
          final name = firstText(map, const [
            'stage', 'name', 'nombre', 'label', 'phase', 'fase',
            'development_stage', 'etapa', 'title',
          ]);
          final photo = _extractMainPhoto(map);
          if (name != null && photo != null) {
            photosByName[_stageKey(name)] = photo;
            namesFromPhotos.add(name);
          } else if (photo != null) {
            photosByOrder.add(photo);
          }
        }
      }
    }

    final stages = <PhytoStage>[];
    if (stagesRaw is List) {
      for (var index = 0; index < stagesRaw.length; index++) {
        final entry = stagesRaw[index];
        if (entry is String) {
          final name = entry.trim();
          if (name.isEmpty || _looksLikeImagePath(name)) continue;
          stages.add(
            PhytoStage(
              name: name,
              photo: photosByName[_stageKey(name)] ??
                  (index < photosByOrder.length ? photosByOrder[index] : null),
            ),
          );
          continue;
        }
        if (entry is Map) {
          final map = Map<String, dynamic>.from(entry);
          final name = firstText(map, const [
            'stage', 'name', 'nombre', 'label', 'phase', 'fase',
            'development_stage', 'etapa', 'title',
          ]);
          if (name == null) continue;
          stages.add(
            PhytoStage(
              name: name,
              photo: _extractMainPhoto(map) ??
                  photosByName[_stageKey(name)] ??
                  (index < photosByOrder.length ? photosByOrder[index] : null),
            ),
          );
        }
      }
    }

    if (stages.isEmpty) {
      final seen = <String>{};
      for (var i = 0; i < namesFromPhotos.length; i++) {
        final name = namesFromPhotos[i];
        if (!seen.add(_stageKey(name))) continue;
        stages.add(
          PhytoStage(
            name: name,
            photo: photosByName[_stageKey(name)] ??
                (i < photosByOrder.length ? photosByOrder[i] : null),
          ),
        );
      }
    }

    // Paridad con la app Android original: si una instalación antigua del
    // backend no incluye el arreglo de etapas, conserva las etapas operativas
    // conocidas. En enfermedades `orderedStages` oculta Terminal al capturar.
    if (stages.isEmpty) {
      final defaults = isDisease
          ? const ['Inicio', 'Desarrollo', 'Avanzado', 'Terminal']
          : const [
              'Huevecillo',
              'Larva/Joven',
              'Pupa',
              'Adulto',
              'Adulto con alas',
            ];
      stages.addAll(
        defaults.map((name) => PhytoStage(name: name)),
      );
    }

    final unique = <String, PhytoStage>{};
    for (final stage in stages) {
      unique.putIfAbsent(
        _stageKey(stage.name),
        () => PhytoStage(name: stage.name, photo: stage.photo),
      );
    }
    return unique.values.toList(growable: false);
  }

  PhytoCatalogItem mergeDetail(PhytoCatalogItem detail) {
    final detailByStage = <String, PhytoStage>{
      for (final stage in detail.stages) _stageKey(stage.name): stage,
    };
    final mergedStages = <PhytoStage>[];
    final seen = <String>{};

    for (final stage in stages) {
      final key = _stageKey(stage.name);
      final detailStage = detailByStage[key];
      mergedStages.add(
        PhytoStage(
          name: stage.name,
          // El detalle tiene prioridad porque normalmente ahí vienen las fotos
          // específicas de cada fase cargadas en producción.
          // Cada fase usa únicamente SU fotografía. Si esa fase no tiene
          // imagen cargada, la UI conserva el icono correspondiente en vez de
          // repetir la foto de otra etapa.
          photo: detailStage?.photo ?? stage.photo,
        ),
      );
      seen.add(key);
    }

    for (final stage in detail.stages) {
      final key = _stageKey(stage.name);
      if (seen.add(key)) mergedStages.add(stage);
    }

    return PhytoCatalogItem(
      id: id.isNotEmpty ? id : detail.id,
      name: name == 'Sin nombre' ? detail.name : name,
      type: type,
      cropId: cropId ?? detail.cropId,
      photo: detail.photo ?? photo,
      description: detail.description ?? description,
      stages: mergedStages.isNotEmpty ? mergedStages : detail.stages,
      raw: {...raw, ...detail.raw},
    );
  }

  List<PhytoStage> get orderedStages {
    final copy = List<PhytoStage>.from(stages);
    int order(String name) {
      final v = name.toLowerCase();
      if (isDisease) {
        if (v.contains('inicio')) return 0;
        if (v.contains('desarrollo')) return 1;
        if (v.contains('avanz')) return 2;
        return 99;
      }
      if (v.contains('huev')) return 0;
      if (v.contains('larva') || v.contains('joven')) return 1;
      if (v.contains('pupa')) return 2;
      if (v == 'adulto' || (v.contains('adulto') && !v.contains('alas'))) return 3;
      if (v.contains('adulto') && v.contains('alas')) return 4;
      return 99;
    }
    copy.sort((a, b) => order(a.name).compareTo(order(b.name)));
    if (isDisease) {
      return copy.where((s) {
        final v = s.name.toLowerCase();
        return v.contains('inicio') || v.contains('desarrollo') || v.contains('avanz');
      }).toList(growable: false);
    }
    return copy;
  }

  Map<String, Object?> toDbMap() => {
        'remote_id': id,
        'name': name,
        'type': type,
        'crop_id': cropId,
        'photo': photo,
        'description': description,
        'json': jsonEncode({...raw, '_v3_stages': stages.map((e) => {'name': e.name, 'photo': e.photo}).toList()}),
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      };

  factory PhytoCatalogItem.fromDb(Map<String, Object?> row) {
    final raw = Map<String, dynamic>.from(jsonDecode('${row['json']}') as Map);
    final encodedStages = raw.remove('_v3_stages');
    final base = PhytoCatalogItem.fromJson(raw);
    final stages = encodedStages is List
        ? encodedStages.whereType<Map>().map((e) => PhytoStage(
              name: '${e['name'] ?? ''}',
              photo: e['photo']?.toString(),
            )).where((e) => e.name.isNotEmpty).toList()
        : base.stages;
    return PhytoCatalogItem(
      id: '${row['remote_id']}',
      name: '${row['name']}',
      type: '${row['type']}',
      cropId: row['crop_id']?.toString(),
      photo: row['photo']?.toString(),
      description: row['description']?.toString(),
      stages: stages,
      raw: raw,
    );
  }
}

enum DiseasePresence { notPresent, present }

class DiseaseEvaluation {
  const DiseaseEvaluation(this.presence, {this.stage});
  final DiseasePresence presence;
  final String? stage;
}

class PendingCheckpoint {
  const PendingCheckpoint({
    required this.localId,
    required this.headerId,
    required this.targetId,
    required this.phytoIssueId,
    required this.phytoName,
    required this.phytoType,
    required this.stage,
    required this.presenceStatus,
    required this.qty,
    required this.latitude,
    required this.longitude,
    required this.notes,
    required this.photoPath,
    required this.capturedAt,
    required this.syncState,
    this.remoteId,
    this.lastError,
  });

  final String localId;
  final String? remoteId;
  final String headerId;
  final String targetId;
  final String? phytoIssueId;
  final String phytoName;
  final String phytoType;
  final String? stage;
  final String presenceStatus;
  final int qty;
  final double latitude;
  final double longitude;
  final String? notes;
  final String? photoPath;
  final DateTime capturedAt;
  final String syncState;
  final String? lastError;

  bool get isDisease => phytoType.toLowerCase().contains('enfermedad');
  bool get isNoPest => phytoIssueId == null;

  Map<String, Object?> toDbMap() => {
        'local_id': localId,
        'remote_id': remoteId,
        'header_id': headerId,
        'target_id': targetId,
        'phyto_issue_id': phytoIssueId,
        'phyto_name': phytoName,
        'phyto_type': phytoType,
        'stage': stage,
        'presence_status': presenceStatus,
        'qty': qty,
        'lat': latitude,
        'lon': longitude,
        'notes': notes,
        'photo_path': photoPath,
        'captured_at': capturedAt.toUtc().toIso8601String(),
        'sync_state': syncState,
        'last_error': lastError,
      };

  factory PendingCheckpoint.fromDb(Map<String, Object?> row) => PendingCheckpoint(
        localId: '${row['local_id']}',
        remoteId: row['remote_id']?.toString(),
        headerId: '${row['header_id']}',
        targetId: '${row['target_id']}',
        phytoIssueId: row['phyto_issue_id']?.toString(),
        phytoName: '${row['phyto_name'] ?? 'Sin plaga'}',
        phytoType: '${row['phyto_type'] ?? ''}',
        stage: row['stage']?.toString(),
        presenceStatus: '${row['presence_status'] ?? 'low'}',
        qty: int.tryParse('${row['qty']}') ?? 0,
        latitude: double.tryParse('${row['lat']}') ?? 0,
        longitude: double.tryParse('${row['lon']}') ?? 0,
        notes: row['notes']?.toString(),
        photoPath: row['photo_path']?.toString(),
        capturedAt: DateTime.tryParse('${row['captured_at']}') ?? DateTime.now(),
        syncState: '${row['sync_state'] ?? 'pending'}',
        lastError: row['last_error']?.toString(),
      );
}

String stageLabel(String raw) {
  final v = raw.trim().replaceAll('_', ' ');
  if (v.toLowerCase().contains('adulto') && v.toLowerCase().contains('alas')) return 'Adulto con alas';
  if (v.isEmpty) return 'General';
  return '${v[0].toUpperCase()}${v.substring(1)}';
}

import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../../core/app_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/app_header.dart';
import '../../core/widgets/authenticated_remote_image.dart';
import '../../core/widgets/gpa_loading_indicator.dart';
import '../../core/widgets/leaflet_map.dart';
import 'monitoring_models.dart';
import 'monitoring_repository.dart';

class MonitoringReportScreen extends StatefulWidget {
  const MonitoringReportScreen({super.key});

  @override
  State<MonitoringReportScreen> createState() => _MonitoringReportScreenState();
}

class _MonitoringReportScreenState extends State<MonitoringReportScreen> {
  static const MethodChannel _downloadsChannel = MethodChannel(
    'ciagro/downloads',
  );
  final repo = MonitoringRepository();
  late MonitoringHeader header;
  late Future<_ReportData> future;
  bool syncing = false;

  @override
  void initState() {
    super.initState();
    header = MonitoringHeader.fromJson(
      AppScope.read(context).selectedMonitoring ?? const <String, dynamic>{},
    );
    future = _load();
  }

  Future<_ReportData> _load() async {
    final remoteFuture = repo.remoteCheckpoints(header.id);
    final localFuture = repo.localCheckpoints(header.id);
    final targetsFuture = repo.refreshTargetPoints(header.id);
    final pendingFuture = repo.pendingCount(header.id);
    final catalogFuture = repo.catalogForCropCachedFirst(header.cropId);
    final polygonFuture = repo.plotPolygon(header);
    final polygonRaw = await polygonFuture;
    return _ReportData(
      remote: await remoteFuture,
      local: await localFuture,
      targets: await targetsFuture,
      pendingCount: await pendingFuture,
      pestTolerance: header.pestTolerance,
      catalog: await catalogFuture,
      polygon: polygonRaw
          .map((e) => MapPoint(e.lat, e.lon))
          .toList(growable: false),
    );
  }

  Future<void> _refresh() async {
    setState(() => future = _load());
    await future;
  }

  Future<void> _sync() async {
    if (syncing) return;
    setState(() => syncing = true);
    try {
      final result = await repo.syncPending(headerId: header.id);
      if (!mounted) return;
      _snack(result.message);
      await _refresh();
    } finally {
      if (mounted) setState(() => syncing = false);
    }
  }

  Future<void> _exportCsv(_ReportData data) async {
    final rows = <List<String>>[
      [
        'Punto',
        'Latitud',
        'Longitud',
        'Plaga / Enfermedad',
        'Tipo',
        'Fase',
        'Cantidad',
        'Severidad punto',
        'Fecha',
        'Imagen',
        'Comentario',
      ],
      ...data.displayRows.map(
        (row) => [
          '${row.pointNumber}',
          row.latitude == null ? '' : row.latitude!.toStringAsFixed(6),
          row.longitude == null ? '' : row.longitude!.toStringAsFixed(6),
          row.name,
          row.type,
          row.stage,
          row.quantity,
          row.severity,
          row.capturedAt,
          row.photo == null ? '0' : '1',
          row.notes,
        ],
      ),
    ];
    final csv =
        '\uFEFF${rows.map((row) => row.map(_csvEscape).join(',')).join('\r\n')}';
    final fileName =
        'reporte_${_safeFileName(header.plotName)}_${DateTime.now().millisecondsSinceEpoch}.csv';

    // Android: guarda en Descargas/Monitoreos y lanza una notificación del
    // sistema. Así el usuario sí sabe dónde quedó el archivo y puede abrirlo
    // desde su gestor de descargas, igual que en la app Android original.
    if (Platform.isAndroid) {
      try {
        final result = await _downloadsChannel.invokeMethod<dynamic>(
          'saveCsv',
          <String, dynamic>{'fileName': fileName, 'content': csv},
        );
        final location = result is Map
            ? '${result['location'] ?? 'Descargas/Monitoreos'}'
            : 'Descargas/Monitoreos';
        if (!mounted) return;
        _snack('CSV descargado correctamente en $location.');
        return;
      } on PlatformException catch (error) {
        if (mounted) {
          _snack(
            'No se pudo usar Descargas del sistema (${error.message ?? 'error desconocido'}). Se guardará una copia local.',
          );
        }
      }
    }

    // iOS/fallback: se conserva dentro de Documentos de la app.
    final dir = await getApplicationDocumentsDirectory();
    final file = File(p.join(dir.path, fileName));
    await file.writeAsString(csv, encoding: utf8, flush: true);
    if (!mounted) return;
    _snack('CSV guardado correctamente: ${file.path}');
  }

  Future<void> _showPhoto(_DisplayRecord row) async {
    final source = row.photo;
    if (source == null || source.trim().isEmpty) return;

    final local = File(source);
    final Widget image;
    if (await local.exists()) {
      image = Image.file(local, fit: BoxFit.contain, gaplessPlayback: true);
    } else {
      image = AuthenticatedRemoteImage(
        url: source,
        fit: BoxFit.contain,
        fallback: const _EvidenceUnavailable(),
      );
    }

    if (!mounted) return;
    await showDialog<void>(
      context: context,
      barrierColor: Colors.black87,
      builder: (_) => _ZoomablePhotoDialog(
        image: image,
        title: '${row.name} - Punto ${row.pointNumber}',
      ),
    );
  }

  void _showComment(_DisplayRecord row) {
    if (row.notes.trim().isEmpty) return;
    showDialog<void>(
      context: context,
      builder: (dialogContext) => Dialog(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(22, 18, 14, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        'Nota del hallazgo',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w900,
                          color: Color(0xFF202820),
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: 'Cerrar',
                      onPressed: () => Navigator.of(dialogContext).pop(),
                      icon: const Icon(
                        Icons.close,
                        color: Colors.black45,
                        size: 21,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.only(right: 10),
                  child: Text(
                    row.notes,
                    style: const TextStyle(
                      fontSize: 15,
                      height: 1.35,
                      color: Color(0xFF667066),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _openFullMap(_ReportData data) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(builder: (_) => _FullReportMapScreen(data: data)),
    );
  }

  Future<void> _openPointDetail(_ReportData data, String pointId) async {
    final number = int.tryParse(pointId);
    if (number == null) return;
    _PointReportView? point;
    for (final item in data.pointViews) {
      if (item.number == number) {
        point = item;
        break;
      }
    }
    if (point == null || !mounted) return;
    await _showPointDetailSheet(context, point);
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) AppScope.read(context).go(AppPage.monitoringList);
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFFBFCF8),
        body: Column(
          children: [
            AppHeader(
              title: 'Reporte fitosanitario',
              onBack: () => AppScope.read(context).go(AppPage.monitoringList),
            ),
            Expanded(
              child: RefreshIndicator(
                onRefresh: _refresh,
                child: FutureBuilder<_ReportData>(
                  future: future,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState != ConnectionState.done) {
                      return const Center(
                        child: GpaLoadingIndicator(text: 'Cargando reporte...'),
                      );
                    }
                    final data =
                        snapshot.data ??
                        const _ReportData(
                          remote: [],
                          local: [],
                          targets: [],
                          pendingCount: 0,
                          pestTolerance: 1,
                          catalog: [],
                          polygon: [],
                        );
                    final summary = data.summary;
                    final synchronized =
                        !snapshot.hasError && summary.pending == 0;
                    final captured = data.capturedPoints;
                    final reviewPending = data.reviewPendingPoints;

                    return ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(12, 14, 12, 32),
                      children: [
                        _HeaderCard(header: header),
                        const SizedBox(height: 14),
                        Row(
                          children: [
                            Expanded(
                              child: _ReportStatCard(
                                icon: Icons.my_location,
                                value: data.targets.length,
                                title: 'Puntos',
                                subtitle: 'Asignados',
                                accent: const Color(0xFF2E8B57),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: _ReportStatCard(
                                icon: Icons.check,
                                value: captured,
                                title: 'Capturados',
                                subtitle: data.targets.isEmpty
                                    ? '0% avance'
                                    : '${((captured / data.targets.length) * 100).round()}% avance',
                                accent: const Color(0xFF198754),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: _ReportStatCard(
                                icon: Icons.circle,
                                value: reviewPending,
                                title: 'Pendientes',
                                subtitle: 'Por revisar',
                                accent: const Color(0xFFFF9800),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 14),
                        _ReportMapCard(
                          points: data.mapPoints,
                          polygon: data.polygon,
                          vertexCount: data.polygon.length,
                          onOpenMap: () => _openFullMap(data),
                          onPointTap: (id) => _openPointDetail(data, id),
                        ),
                        const SizedBox(height: 14),
                        Card(
                          margin: EdgeInsets.zero,
                          color: Colors.white,
                          surfaceTintColor: Colors.transparent,
                          elevation: 2,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.all(10),
                            child: Row(
                              children: [
                                Expanded(
                                  child: FilledButton.icon(
                                    onPressed: () => _exportCsv(data),
                                    icon: const Icon(Icons.download),
                                    label: const Text('Descargar CSV'),
                                    style: FilledButton.styleFrom(
                                      backgroundColor: const Color(0xFF176E35),
                                      minimumSize: const Size.fromHeight(58),
                                      shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(15),
                                      ),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: FilledButton.icon(
                                    onPressed: syncing ? null : _sync,
                                    icon: syncing
                                        ? const GpaLoadingIndicator(
                                            size: 24,
                                            showText: false,
                                          )
                                        : Icon(
                                            synchronized
                                                ? Icons.check_circle_outline
                                                : Icons.sync,
                                          ),
                                    label: Text(
                                      syncing
                                          ? 'Sincronizando...'
                                          : synchronized
                                          ? 'Sincronizado'
                                          : 'Sincronizar (${summary.pending})',
                                    ),
                                    style: FilledButton.styleFrom(
                                      backgroundColor: synchronized
                                          ? const Color(0xFF198754)
                                          : const Color(0xFFFFA000),
                                      foregroundColor: Colors.white,
                                      minimumSize: const Size.fromHeight(58),
                                      shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(15),
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        if (summary.pending > 0) ...[
                          const SizedBox(height: 12),
                          Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFF5D8),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Text(
                              'Pendiente de enviar\nEste reporte tiene ${summary.pending} captura(s) pendiente(s) de sincronizar.',
                              style: const TextStyle(
                                color: Color(0xFFE19A00),
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ],
                        const SizedBox(height: 18),
                        const Text(
                          'Tabla de capturas',
                          style: TextStyle(
                            fontSize: 22,
                            color: AppTheme.darkGreen,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          'Desliza horizontalmente para consultar todas las columnas.',
                          style: TextStyle(color: Colors.black54, fontSize: 12),
                        ),
                        const SizedBox(height: 10),
                        if (data.displayRows.isEmpty)
                          const Card(
                            child: Padding(
                              padding: EdgeInsets.all(20),
                              child: Text(
                                'Aún no hay capturas registradas en este monitoreo.',
                                textAlign: TextAlign.center,
                              ),
                            ),
                          )
                        else
                          _CaptureTable(
                            rows: data.displayRows,
                            onPhoto: _showPhoto,
                            onComment: _showComment,
                          ),
                      ],
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _snack(String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
}

class _ZoomablePhotoDialog extends StatefulWidget {
  const _ZoomablePhotoDialog({required this.image, required this.title});

  final Widget image;
  final String title;

  @override
  State<_ZoomablePhotoDialog> createState() => _ZoomablePhotoDialogState();
}

class _ZoomablePhotoDialogState extends State<_ZoomablePhotoDialog> {
  final TransformationController _controller = TransformationController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _reset() {
    _controller.value = TransformationController().value;
    setState(() {});
  }

  void _zoom(double factor) {
    final current = _controller.value.getMaxScaleOnAxis();
    final target = (current * factor).clamp(1.0, 10.0);
    final relative = target / current;
    final matrix = _controller.value.clone()..scale(relative);
    _controller.value = matrix;
    setState(() {});
  }

  void _toggleDoubleTapZoom() {
    final current = _controller.value.getMaxScaleOnAxis();
    if (current > 1.05) {
      _reset();
    } else {
      _zoom(2.5);
    }
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    return Dialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 18),
      backgroundColor: Colors.black,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: SizedBox(
        width: size.width,
        height: size.height * .84,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 4),
              child: Row(
                children: [
                  IconButton(
                    onPressed: () => Navigator.of(context).pop(),
                    color: Colors.white,
                    icon: const Icon(Icons.close),
                  ),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      widget.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: _reset,
                    child: const Text('Restablecer'),
                  ),
                ],
              ),
            ),
            Expanded(
              child: Stack(
                children: [
                  Positioned.fill(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(18),
                      child: Container(
                        color: Colors.black,
                        alignment: Alignment.center,
                        child: GestureDetector(
                          behavior: HitTestBehavior.opaque,
                          onDoubleTap: _toggleDoubleTapZoom,
                          child: InteractiveViewer(
                            transformationController: _controller,
                            minScale: 1.0,
                            maxScale: 10.0,
                            panEnabled: true,
                            scaleEnabled: true,
                            trackpadScrollCausesScale: true,
                            boundaryMargin: const EdgeInsets.all(500),
                            clipBehavior: Clip.none,
                            child: SizedBox.expand(
                              child: Center(child: widget.image),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  Positioned(
                    right: 12,
                    top: 12,
                    child: Column(
                      children: [
                        _ZoomButton(
                          icon: Icons.add,
                          tooltip: 'Acercar',
                          onTap: () => _zoom(1.35),
                        ),
                        const SizedBox(height: 8),
                        _ZoomButton(
                          icon: Icons.remove,
                          tooltip: 'Alejar',
                          onTap: () => _zoom(1 / 1.35),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ZoomButton extends StatelessWidget {
  const _ZoomButton({
    required this.icon,
    required this.tooltip,
    required this.onTap,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.black.withValues(alpha: .72),
      shape: const CircleBorder(),
      child: IconButton(
        tooltip: tooltip,
        onPressed: onTap,
        color: Colors.white,
        icon: Icon(icon),
      ),
    );
  }
}

class _EvidenceUnavailable extends StatelessWidget {
  const _EvidenceUnavailable();

  @override
  Widget build(BuildContext context) => Container(
    alignment: Alignment.center,
    padding: const EdgeInsets.all(24),
    color: const Color(0xFFF3F5F1),
    child: const Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.broken_image_outlined, size: 42, color: Colors.black45),
        SizedBox(height: 10),
        Text(
          'No se pudo cargar esta evidencia.\nSi fue tomada en este teléfono, vuelve a sincronizar cuando tengas conexión.',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.black54),
        ),
      ],
    ),
  );
}

class _ReportData {
  const _ReportData({
    required this.remote,
    required this.local,
    required this.targets,
    required this.pendingCount,
    required this.pestTolerance,
    required this.catalog,
    required this.polygon,
  });

  final List<Map<String, dynamic>> remote;
  final List<PendingCheckpoint> local;
  final List<TargetPoint> targets;
  final int pendingCount;
  final int pestTolerance;
  final List<PhytoCatalogItem> catalog;
  final List<MapPoint> polygon;

  Map<String, PhytoCatalogItem> get _catalogById => {
    for (final item in catalog) item.id: item,
  };

  List<_DisplayRecord> get displayRows {
    final result = <_DisplayRecord>[];
    final catalogById = _catalogById;
    final remoteIds = <String>{};
    final pointNumbers = _pointNumberMap(targets);
    final pointByLocal = {for (final p in targets) p.id: p};
    final pointByServer = <String, TargetPoint>{};
    for (final point in targets) {
      final serverId = point.serverId;
      if (serverId != null && serverId.isNotEmpty)
        pointByServer[serverId] = point;
    }

    final localByTarget = <String, List<PendingCheckpoint>>{};
    for (final cp in local) {
      localByTarget.putIfAbsent(cp.targetId, () => []).add(cp);
    }
    final severityByLocalTarget = <String, String>{};
    for (final entry in localByTarget.entries) {
      severityByLocalTarget[entry.key] = _pestSeverity(
        entry.value,
        pestTolerance,
      );
    }

    // Igual que CIAGRO web: primero agrupa TODOS los hallazgos del punto por
    // pcp_oid. Solo si no existe pcp_oid cae a target/coordenadas.
    final remoteByPoint = <String, List<Map<String, dynamic>>>{};
    for (final item in remote) {
      final pointKey = _remotePointKey(item);
      if (pointKey != null) {
        remoteByPoint.putIfAbsent(pointKey, () => []).add(item);
      }
    }
    final severityByRemotePoint = <String, String>{};
    for (final entry in remoteByPoint.entries) {
      severityByRemotePoint[entry.key] = _remotePestSeverity(
        entry.value,
        catalogById,
        pestTolerance,
      );
    }

    // Mantiene una referencia a la captura local incluso después de sincronizar.
    // Sirve para mostrar la foto del teléfono mientras se repara un registro
    // histórico cuyo backend recibió el JSON pero no el archivo.
    final localByRemoteId = <String, PendingCheckpoint>{};
    for (final checkpoint in local) {
      final remoteId = checkpoint.remoteId?.trim();
      if (remoteId != null && remoteId.isNotEmpty) {
        localByRemoteId[remoteId] = checkpoint;
      }
    }

    for (final item in remote) {
      final id = flexibleId(item['id']) ?? '';
      if (id.isNotEmpty) remoteIds.add(id);
      final targetId = _remoteTargetId(item);
      final pointKey = _remotePointKey(item);
      final point = _findTargetForRemote(
        item,
        targetId,
        pointByServer,
        targets,
      );
      result.add(
        _remoteRow(
          item,
          point,
          pointNumber: (() {
            final pcpOid = int.tryParse('${item['pcp_oid'] ?? ''}');
            if (pcpOid != null && pcpOid > 0) return pcpOid;
            return _numberForTarget(point, pointNumbers);
          })(),
          pestSeverity: pointKey == null
              ? _remotePestSeverity([item], catalogById, pestTolerance)
              : (severityByRemotePoint[pointKey] ?? 'Sin plaga'),
          catalogById: catalogById,
          localMirror: id.isEmpty ? null : localByRemoteId[id],
        ),
      );
    }

    for (final cp in local) {
      if (cp.remoteId != null && remoteIds.contains(cp.remoteId)) continue;
      final point = pointByLocal[cp.targetId];
      final isNoPest = cp.isNoPest;
      final disease = cp.isDisease;
      result.add(
        _DisplayRecord(
          pointNumber: _numberForTarget(point, pointNumbers),
          name: isNoPest ? 'Sin plaga' : cp.phytoName,
          type: isNoPest ? '-' : (disease ? 'Enfermedad' : 'Plaga'),
          stage: isNoPest
              ? '-'
              : disease
              ? _diseasePhase(cp.stage, cp.presenceStatus)
              : (cp.stage == null || cp.stage!.trim().isEmpty
                    ? '-'
                    : stageLabel(cp.stage!)),
          quantity: isNoPest
              ? '0'
              : disease
              ? '—'
              : '${cp.qty}',
          severity: isNoPest
              ? 'Sin plaga'
              : disease
              ? _diseaseSeverity(cp.stage, cp.presenceStatus)
              : (severityByLocalTarget[cp.targetId] ?? 'Sin plaga'),
          capturedAt: _formatDate(cp.capturedAt),
          photo: cp.photoPath,
          notes: _cleanSeverityMetadata(cp.notes),
          syncState: cp.syncState,
          latitude: cp.latitude,
          longitude: cp.longitude,
        ),
      );
    }

    result.sort((a, b) {
      final point = a.pointNumber.compareTo(b.pointNumber);
      if (point != 0) return point;
      return a.capturedAt.compareTo(b.capturedAt);
    });
    return result;
  }

  int get capturedPoints {
    final byStatus = targets.where((point) => point.completed).length;
    final byCapture = displayRows
        .where((row) => row.pointNumber > 0)
        .map((row) => row.pointNumber)
        .toSet()
        .length;
    return byCapture > byStatus ? byCapture : byStatus;
  }

  int get reviewPendingPoints {
    final value = targets.length - capturedPoints;
    return value < 0 ? 0 : value;
  }

  _ReportSummary get summary {
    final rows = displayRows;
    return _ReportSummary(
      total: rows.length,
      pests: rows.where((e) => e.type == 'Plaga').length,
      diseases: rows.where((e) => e.type == 'Enfermedad').length,
      noPest: rows.where((e) => e.name == 'Sin plaga').length,
      pending: pendingCount,
    );
  }

  List<_PointReportView> get pointViews {
    final rows = displayRows;
    final numbers = _pointNumberMap(targets);
    final rowsByNumber = <int, List<_DisplayRecord>>{};
    for (final row in rows) {
      if (row.pointNumber <= 0) continue;
      rowsByNumber.putIfAbsent(row.pointNumber, () => []).add(row);
    }

    final result = <_PointReportView>[];
    final seen = <int>{};
    for (var index = 0; index < targets.length; index++) {
      final target = targets[index];
      final mapped = _numberForTarget(target, numbers);
      final number = mapped > 0 ? mapped : index + 1;
      final captures = rowsByNumber[number] ?? const <_DisplayRecord>[];
      final captured = target.completed || captures.isNotEmpty;
      result.add(
        _PointReportView(
          number: number,
          latitude: target.latitude,
          longitude: target.longitude,
          radiusM: target.radiusM,
          captured: captured,
          pestText: _pestIndexForRows(captures, captured: captured),
          diseaseText: _diseaseIndexForRows(captures, captured: captured),
          captures: captures,
        ),
      );
      seen.add(number);
    }

    // Compatibilidad con registros históricos cuyo target no conserva geom.
    for (final entry in rowsByNumber.entries) {
      if (seen.contains(entry.key)) continue;
      _DisplayRecord? firstWithGps;
      for (final row in entry.value) {
        if (row.latitude != null && row.longitude != null) {
          firstWithGps = row;
          break;
        }
      }
      if (firstWithGps == null) continue;
      result.add(
        _PointReportView(
          number: entry.key,
          latitude: firstWithGps.latitude ?? 0,
          longitude: firstWithGps.longitude ?? 0,
          radiusM: 5,
          captured: true,
          pestText: _pestIndexForRows(entry.value, captured: true),
          diseaseText: _diseaseIndexForRows(entry.value, captured: true),
          captures: entry.value,
        ),
      );
    }

    result.sort((a, b) => a.number.compareTo(b.number));
    return result;
  }

  List<MapPoint> get mapPoints => pointViews
      .where((point) => point.latitude != 0 || point.longitude != 0)
      .map(
        (point) => MapPoint(
          point.latitude,
          point.longitude,
          id: '${point.number}',
          label: 'Punto ${point.number}',
          markerText: '${point.number}',
          pestText: point.pestText,
          diseaseText: point.diseaseText,
          pestColor: _pestColorHex(point.pestText),
          diseaseColor: _diseaseColorHex(point.diseaseText),
        ),
      )
      .toList(growable: false);

  String? _serverEvidencePhoto(Map<String, dynamic> item) {
    for (final key in const [
      'photo',
      'photo_url',
      'image',
      'image_url',
      'attachment',
      'attachment_url',
      'file_url',
      'download_url',
    ]) {
      final value = photoUrlFrom(item[key]);
      if (value != null && value.trim().isNotEmpty) return value;
    }
    return null;
  }

  _DisplayRecord _remoteRow(
    Map<String, dynamic> item,
    TargetPoint? point, {
    required int pointNumber,
    required String pestSeverity,
    required Map<String, PhytoCatalogItem> catalogById,
    PendingCheckpoint? localMirror,
  }) {
    final issueRaw =
        item['phyto_issue'] ?? item['phyto_issue_id'] ?? item['phytosanitary'];
    final issue = issueRaw is Map
        ? Map<String, dynamic>.from(issueRaw)
        : const <String, dynamic>{};
    final issueId = flexibleId(issueRaw);
    final catalogItem = issueId == null ? null : catalogById[issueId];
    final noPest = _remoteIsNoPest(item, catalogById);
    final directIssueName = '${item['issue'] ?? ''}'.trim();
    final name = noPest
        ? 'Sin plaga'
        : directIssueName.isNotEmpty
        ? directIssueName
        : firstText(issue, const ['name', 'nombre', 'label']) ??
              catalogItem?.name ??
              'Fitosanitario sin nombre';
    final stage = item['stage']?.toString();
    final stageDisplay = item['stage_display']?.toString();
    final presence = '${item['presence_status'] ?? ''}'.toLowerCase();
    final qty = int.tryParse('${item['qty'] ?? 0}') ?? 0;
    final disease = _remoteIsDisease(item, catalogById);
    final geom = _geom(item);
    return _DisplayRecord(
      pointNumber: pointNumber,
      name: name,
      type: noPest ? '-' : (disease ? 'Enfermedad' : 'Plaga'),
      stage: noPest
          ? '-'
          : disease
          ? _diseasePhase(stage, presence)
          : (stageDisplay != null && stageDisplay.trim().isNotEmpty
                ? stageDisplay
                : (stage == null || stage.trim().isEmpty
                      ? '-'
                      : stageLabel(stage))),
      quantity: noPest
          ? '0'
          : disease
          ? '—'
          : '$qty',
      severity: noPest
          ? 'Sin plaga'
          : disease
          ? _diseaseSeverity(stage, presence)
          : pestSeverity,
      capturedAt: _formatDynamicDate(item['captured_at']),
      // No usamos photoUrlFrom(item) sobre todo el JSON: `photo_ref` puede ser
      // solo un nombre de archivo y antes se interpretaba como URL, provocando
      // el 404 rojo que se veía al abrir evidencias. Si el backend aún no tiene
      // `photo`, aprovechamos temporalmente la copia local del teléfono.
      photo: _serverEvidencePhoto(item) ?? localMirror?.photoPath,
      notes: _cleanSeverityMetadata(item['notes']?.toString()),
      syncState: 'synced',
      latitude: geom.$1 ?? point?.latitude,
      longitude: geom.$2 ?? point?.longitude,
    );
  }
}

class _HeaderCard extends StatelessWidget {
  const _HeaderCard({required this.header});
  final MonitoringHeader header;

  @override
  Widget build(BuildContext context) {
    final completed = header.isCompleted;
    return Container(
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF0E5B2B), Color(0xFF23783D)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
        boxShadow: const [
          BoxShadow(color: Colors.black12, blurRadius: 8, offset: Offset(0, 4)),
        ],
      ),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Reporte fitosanitario',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 23,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          SizedBox(height: 3),
                          Text(
                            'Monitoreo',
                            style: TextStyle(
                              color: Colors.white70,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 13,
                        vertical: 9,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(22),
                      ),
                      child: Text(
                        completed ? '✓ Completado' : 'En progreso',
                        style: const TextStyle(
                          color: Color(0xFF1F6A36),
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Container(
                      width: 80,
                      height: 80,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      alignment: Alignment.center,
                      clipBehavior: Clip.antiAlias,
                      child: AuthenticatedRemoteImage(
                        url: header.cropPhoto,
                        width: 80,
                        height: 80,
                        fit: BoxFit.cover,
                        fallback: Center(
                          child: Text(
                            _reportCropEmoji(header.cropName),
                            style: const TextStyle(fontSize: 43),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'PRODUCTOR',
                            style: TextStyle(
                              color: Colors.white60,
                              fontSize: 10,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          Text(
                            header.producerName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 20,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'CULTIVO',
                            style: TextStyle(
                              color: Colors.white60,
                              fontSize: 10,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          Text(
                            header.cropName,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: _HeroInfo(
                        icon: Icons.home_outlined,
                        label: 'RANCHO',
                        value: header.ranchName,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: _HeroInfo(
                        icon: Icons.my_location_outlined,
                        label: 'PARCELA',
                        value: header.plotName,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: const BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.vertical(bottom: Radius.circular(24)),
            ),
            child: const Row(
              children: [
                Expanded(
                  child: Text(
                    'Información del monitoreo',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.w900),
                  ),
                ),
                Icon(Icons.expand_more),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HeroInfo extends StatelessWidget {
  const _HeroInfo({
    required this.icon,
    required this.label,
    required this.value,
  });
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: Colors.white.withValues(alpha: .10),
      borderRadius: BorderRadius.circular(16),
    ),
    child: Row(
      children: [
        Icon(icon, color: Colors.white, size: 24),
        const SizedBox(width: 9),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: Colors.white60,
                  fontSize: 9,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _ReportStatCard extends StatelessWidget {
  const _ReportStatCard({
    required this.icon,
    required this.value,
    required this.title,
    required this.subtitle,
    required this.accent,
  });
  final IconData icon;
  final int value;
  final String title;
  final String subtitle;
  final Color accent;

  @override
  Widget build(BuildContext context) => Card(
    margin: EdgeInsets.zero,
    elevation: 2,
    color: Colors.white,
    surfaceTintColor: Colors.transparent,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 14, 8, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 22,
            backgroundColor: accent.withValues(alpha: .10),
            child: Icon(icon, color: accent, size: 22),
          ),
          const SizedBox(height: 9),
          Text(
            '$value',
            style: const TextStyle(
              color: AppTheme.darkGreen,
              fontSize: 26,
              fontWeight: FontWeight.w900,
            ),
          ),
          Text(
            title,
            maxLines: 1,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 7),
          Text(
            subtitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(color: accent, fontSize: 10),
          ),
        ],
      ),
    ),
  );
}

class _ReportMapCard extends StatelessWidget {
  const _ReportMapCard({
    required this.points,
    required this.polygon,
    required this.vertexCount,
    required this.onOpenMap,
    required this.onPointTap,
  });

  final List<MapPoint> points;
  final List<MapPoint> polygon;
  final int vertexCount;
  final VoidCallback onOpenMap;
  final ValueChanged<String> onPointTap;

  @override
  Widget build(BuildContext context) => Card(
    margin: EdgeInsets.zero,
    elevation: 2,
    color: Colors.white,
    surfaceTintColor: Colors.transparent,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Mapa del monitoreo',
                      style: TextStyle(
                        color: AppTheme.darkGreen,
                        fontSize: 21,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    SizedBox(height: 3),
                    Text(
                      'Toca un punto para consultar plagas y enfermedades.',
                      style: TextStyle(color: Colors.black54, fontSize: 12),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 9,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFFE8F7E8),
                  borderRadius: BorderRadius.circular(22),
                ),
                child: Text(
                  '$vertexCount vértices',
                  style: const TextStyle(
                    color: AppTheme.primary,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Stack(
            children: [
              LeafletMap(
                points: points,
                polygon: polygon,
                height: 370,
                onPointTap: onPointTap,
              ),
              Positioned(
                top: 10,
                right: 10,
                child: Material(
                  color: Colors.white,
                  elevation: 3,
                  shape: const CircleBorder(),
                  child: IconButton(
                    tooltip: 'Abrir mapa completo',
                    onPressed: onOpenMap,
                    icon: const Icon(
                      Icons.fullscreen,
                      color: AppTheme.primary,
                      size: 30,
                    ),
                  ),
                ),
              ),
              Positioned(
                left: 10,
                right: 10,
                bottom: 10,
                child: Row(
                  children: const [
                    Expanded(
                      child: _LegendBox(
                        title: 'Indice P - Plagas',
                        disease: false,
                      ),
                    ),
                    SizedBox(width: 6),
                    Expanded(
                      child: _LegendBox(
                        title: 'Indice E - Enfermedades',
                        disease: true,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 11),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: onOpenMap,
              icon: const Icon(Icons.fullscreen),
              label: const Text('Abrir mapa completo y tocar puntos'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppTheme.primary,
                minimumSize: const Size.fromHeight(50),
                side: const BorderSide(color: Color(0xFFB8C8B6)),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                textStyle: const TextStyle(fontWeight: FontWeight.w900),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class _FullReportMapScreen extends StatefulWidget {
  const _FullReportMapScreen({required this.data});

  final _ReportData data;

  @override
  State<_FullReportMapScreen> createState() => _FullReportMapScreenState();
}

class _FullReportMapScreenState extends State<_FullReportMapScreen> {
  String visualization = 'heat';
  bool presenceExpanded = true;

  _ReportData get data => widget.data;

  Future<void> _openPoint(BuildContext context, String id) async {
    final number = int.tryParse(id);
    if (number == null) return;

    _PointReportView? point;
    for (final item in data.pointViews) {
      if (item.number == number) {
        point = item;
        break;
      }
    }

    if (point == null) return;
    await _showPointDetailSheet(context, point);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFFBFCF8),
      body: SafeArea(
        child: Column(
          children: [
            Container(
              color: Colors.white,
              padding: const EdgeInsets.fromLTRB(12, 8, 14, 8),
              child: Row(
                children: [
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(
                      Icons.arrow_back,
                      color: AppTheme.darkGreen,
                      size: 30,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Mapa del monitoreo',
                          style: TextStyle(
                            color: AppTheme.darkGreen,
                            fontSize: 22,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        Text(
                          'Toca un punto para ver el detalle',
                          style: TextStyle(color: Colors.black54, fontSize: 13),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 9,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE8F7E8),
                      borderRadius: BorderRadius.circular(22),
                    ),
                    child: Text(
                      '${data.polygon.length} vertices',
                      style: const TextStyle(
                        color: AppTheme.primary,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: Stack(
                children: [
                  Positioned.fill(
                    child: LeafletMap(
                      points: data.mapPoints,
                      polygon: data.polygon,
                      height: MediaQuery.sizeOf(context).height,
                      phytoVisualization: visualization,
                      onPointTap: (id) => _openPoint(context, id),
                    ),
                  ),

                  // Presencia siempre queda flotando sobre el mapa y se puede
                  // contraer para liberar espacio.
                  Positioned(
                    top: 12,
                    left: 96,
                    right: 12,
                    child: _PresenceMapControl(
                      expanded: presenceExpanded,
                      visualization: visualization,
                      onToggleExpanded: () {
                        setState(() => presenceExpanded = !presenceExpanded);
                      },
                      onVisualizationChanged: (value) {
                        setState(() => visualization = value);
                      },
                    ),
                  ),

                  // Las leyendas P/E SOLO tienen sentido en modo Discos.
                  // Se mantienen dentro del mapa, pegadas abajo.
                  if (visualization == 'discs')
                    const Positioned(
                      left: 12,
                      right: 12,
                      bottom: 12,
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Expanded(
                            child: _LegendBox(
                              title: 'Indice P - Plagas',
                              disease: false,
                            ),
                          ),
                          SizedBox(width: 8),
                          Expanded(
                            child: _LegendBox(
                              title: 'Indice E - Enfermedades',
                              disease: true,
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PresenceMapControl extends StatelessWidget {
  const _PresenceMapControl({
    required this.expanded,
    required this.visualization,
    required this.onToggleExpanded,
    required this.onVisualizationChanged,
  });

  final bool expanded;
  final String visualization;
  final VoidCallback onToggleExpanded;
  final ValueChanged<String> onVisualizationChanged;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white.withValues(alpha: .94),
      elevation: 3,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onToggleExpanded,
        child: Padding(
          padding: EdgeInsets.fromLTRB(
            12,
            expanded ? 11 : 8,
            10,
            expanded ? 10 : 8,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 22,
                    height: 22,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: RadialGradient(
                        colors: [
                          Color(0xFFD82424),
                          Color(0xFFFF7A00),
                          Color(0xFFFFC107),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    'Presencia',
                    style: TextStyle(
                      color: AppTheme.darkGreen,
                      fontSize: 15,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(width: 5),
                  Text(
                    visualization == 'heat' ? '- Calor' : '- Discos',
                    style: const TextStyle(
                      color: Colors.black54,
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const Spacer(),
                  Icon(
                    expanded ? Icons.expand_less : Icons.expand_more,
                    color: Colors.black54,
                  ),
                ],
              ),
              if (expanded) ...[
                const SizedBox(height: 9),
                Row(
                  children: [
                    Expanded(
                      child: _PresenceModeButton(
                        label: 'Calor',
                        selected: visualization == 'heat',
                        onTap: () => onVisualizationChanged('heat'),
                      ),
                    ),
                    const SizedBox(width: 4),
                    Expanded(
                      child: _PresenceModeButton(
                        label: 'Discos',
                        selected: visualization == 'discs',
                        onTap: () => onVisualizationChanged('discs'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 9),
                const Divider(height: 1),
                const SizedBox(height: 7),
                const _PresenceLegendLine(
                  color: Color(0xFFD82424),
                  text: 'Critica',
                ),
                const _PresenceLegendLine(
                  color: Color(0xFFFF7A00),
                  text: 'Advertencia',
                ),
                const _PresenceLegendLine(
                  color: Color(0xFFFFC107),
                  text: 'Baja',
                ),
                const _PresenceLegendLine(
                  color: Color(0xFF18864B),
                  text: 'Sin monitorear',
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _PresenceModeButton extends StatelessWidget {
  const _PresenceModeButton({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? AppTheme.primary : const Color(0xFFF6F7F4),
      borderRadius: BorderRadius.circular(9),
      child: InkWell(
        borderRadius: BorderRadius.circular(9),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 7),
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: selected ? Colors.white : Colors.black87,
              fontSize: 12,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ),
    );
  }
}

class _PresenceLegendLine extends StatelessWidget {
  const _PresenceLegendLine({required this.color, required this.text});

  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 3),
    child: Row(
      children: [
        Container(
          width: 9,
          height: 9,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Text(
          text,
          style: const TextStyle(fontSize: 10.5, color: Colors.black54),
        ),
      ],
    ),
  );
}

class _LegendBox extends StatelessWidget {
  const _LegendBox({required this.title, required this.disease});

  final String title;
  final bool disease;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.fromLTRB(12, 10, 10, 10),
    decoration: BoxDecoration(
      color: Colors.white.withValues(alpha: .96),
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: const Color(0xFFE3E9E1)),
      boxShadow: const [
        BoxShadow(
          color: Color(0x12000000),
          blurRadius: 7,
          offset: Offset(0, 2),
        ),
      ],
    ),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            color: AppTheme.darkGreen,
            fontSize: 12,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 6),
        _legendLine(
          const Color(0xFF1BA64B),
          disease ? 'Sin presencia' : 'Sin plaga',
        ),
        _legendLine(
          const Color(0xFFFFC107),
          disease ? 'Baja' : 'Severidad menor',
        ),
        _legendLine(
          const Color(0xFFFF7A00),
          disease ? 'Media' : 'Severidad mayor',
        ),
        _legendLine(
          const Color(0xFFD82424),
          disease ? 'Alta' : 'Severidad alta',
        ),
      ],
    ),
  );

  Widget _legendLine(Color color, String text) => Padding(
    padding: const EdgeInsets.only(top: 3),
    child: Row(
      children: [
        Container(
          width: 9,
          height: 9,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 9.5,
              height: 1.15,
              color: Color(0xFF3F493F),
            ),
          ),
        ),
      ],
    ),
  );
}

String _reportCropEmoji(String crop) {
  final value = crop.toLowerCase();
  if (value.contains('maíz') || value.contains('maiz')) return '🌱';
  if (value.contains('trigo')) return '🌾';
  if (value.contains('fresa')) return '🍓';
  if (value.contains('tomate')) return '🍅';
  return '🌱';
}

class _CaptureTable extends StatelessWidget {
  const _CaptureTable({
    required this.rows,
    required this.onPhoto,
    required this.onComment,
  });
  final List<_DisplayRecord> rows;
  final ValueChanged<_DisplayRecord> onPhoto;
  final ValueChanged<_DisplayRecord> onComment;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: DataTable(
          headingRowColor: WidgetStatePropertyAll(Colors.green.shade800),
          headingTextStyle: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.w800,
          ),
          columnSpacing: 22,
          columns: const [
            DataColumn(label: Text('Punto')),
            DataColumn(label: Text('Plaga / Enfermedad')),
            DataColumn(label: Text('Tipo')),
            DataColumn(label: Text('Fase')),
            DataColumn(label: Text('Cantidad')),
            DataColumn(label: Text('Severidad punto')),
            DataColumn(label: Text('Fecha')),
            DataColumn(label: Text('Imagen')),
            DataColumn(label: Text('Comentario')),
          ],
          rows: rows
              .map((row) {
                return DataRow(
                  cells: [
                    DataCell(Text('${row.pointNumber}')),
                    DataCell(SizedBox(width: 150, child: Text(row.name))),
                    DataCell(Text(row.type)),
                    DataCell(Text(row.stage)),
                    DataCell(Text(row.quantity)),
                    DataCell(
                      Text(
                        row.severity,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                    DataCell(Text(row.capturedAt)),
                    DataCell(
                      TextButton(
                        onPressed: row.photo == null
                            ? null
                            : () => onPhoto(row),
                        child: Text(row.photo == null ? '📷 0' : '🖼️ 1'),
                      ),
                    ),
                    DataCell(
                      TextButton(
                        onPressed: row.notes.trim().isEmpty
                            ? null
                            : () => onComment(row),
                        child: Text(row.notes.trim().isEmpty ? '💬 0' : '💬'),
                      ),
                    ),
                  ],
                );
              })
              .toList(growable: false),
        ),
      ),
    );
  }
}

class _PointReportView {
  const _PointReportView({
    required this.number,
    required this.latitude,
    required this.longitude,
    required this.radiusM,
    required this.captured,
    required this.pestText,
    required this.diseaseText,
    required this.captures,
  });

  final int number;
  final double latitude;
  final double longitude;
  final double radiusM;
  final bool captured;
  final String pestText;
  final String diseaseText;
  final List<_DisplayRecord> captures;

  int get pestQuantity => captures
      .where((row) => row.type == 'Plaga')
      .fold<int>(0, (sum, row) => sum + (int.tryParse(row.quantity) ?? 0));

  String get mainDate {
    if (captures.isEmpty) return 'Sin captura';
    return captures
        .map((row) => row.capturedAt)
        .where((value) => value.trim().isNotEmpty)
        .fold<String>(
          '',
          (latest, value) =>
              latest.isEmpty || value.compareTo(latest) > 0 ? value : latest,
        );
  }
}

String _pestIndexForRows(List<_DisplayRecord> rows, {required bool captured}) {
  if (!captured) return 'Sin evaluar';
  final pests = rows
      .where((row) => row.type == 'Plaga')
      .toList(growable: false);
  if (pests.isEmpty) return 'Sin plaga';
  const order = <String, int>{
    'Sin plaga': 0,
    'Severidad menor': 1,
    'Severidad mayor': 2,
    'Supera severidad mayor': 3,
    'Severidad alta': 3,
  };
  var best = 'Sin plaga';
  var rank = 0;
  for (final row in pests) {
    final value = order[row.severity] ?? 1;
    if (value > rank) {
      rank = value;
      best = value >= 3 ? 'Severidad alta' : row.severity;
    }
  }
  return best;
}

String _diseaseIndexForRows(
  List<_DisplayRecord> rows, {
  required bool captured,
}) {
  if (!captured) return 'Sin evaluar';

  final diseases = rows
      .where((row) => row.type.trim().toLowerCase() == 'enfermedad')
      .toList(growable: false);

  if (diseases.isEmpty) return 'Sin presencia';

  var rank = 0;
  for (final row in diseases) {
    final value = row.severity.toLowerCase();
    final current = value.contains('alta')
        ? 3
        : value.contains('media')
        ? 2
        : value.contains('baja')
        ? 1
        : 0;
    if (current > rank) rank = current;
  }

  return switch (rank) {
    3 => 'Alta',
    2 => 'Media',
    1 => 'Baja',
    _ => 'Sin presencia',
  };
}

String _pestColorHex(String text) {
  final value = text.toLowerCase();
  if (value.contains('alta') || value.contains('supera')) return '#DC2626';
  if (value.contains('mayor')) return '#F97316';
  if (value.contains('menor')) return '#FACC15';
  if (value.contains('evaluar')) return '#9CA3AF';
  return '#16A34A';
}

String _diseaseColorHex(String text) {
  final value = text.toLowerCase();
  if (value.contains('alta')) return '#DC2626';
  if (value.contains('media')) return '#F97316';
  if (value.contains('baja')) return '#FACC15';
  if (value.contains('evaluar')) return '#9CA3AF';
  return '#16A34A';
}

Color _hexColor(String hex) {
  final clean = hex.replaceFirst('#', '');
  return Color(int.parse('FF$clean', radix: 16));
}

Future<void> _showPointDetailSheet(
  BuildContext context,
  _PointReportView point,
) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (sheetContext) => DraggableScrollableSheet(
      initialChildSize: .72,
      minChildSize: .45,
      maxChildSize: .94,
      expand: false,
      builder: (context, scrollController) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
        child: ListView(
          controller: scrollController,
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 28),
          children: [
            Center(
              child: Container(
                width: 90,
                height: 6,
                decoration: BoxDecoration(
                  color: Colors.black12,
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Punto ${point.number}',
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                TextButton(
                  onPressed: () => Navigator.pop(sheetContext),
                  child: const Text(
                    'Cerrar',
                    style: TextStyle(
                      color: AppTheme.darkGreen,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _IndexChip(
                  prefix: 'P',
                  text: point.pestText,
                  color: _hexColor(_pestColorHex(point.pestText)),
                ),
                _IndexChip(
                  prefix: 'E',
                  text: point.diseaseText,
                  color: _hexColor(_diseaseColorHex(point.diseaseText)),
                ),
                _IndexChip(
                  prefix: '✓',
                  text: point.captured ? 'Monitoreado' : 'Pendiente',
                  color: point.captured
                      ? const Color(0xFF16A34A)
                      : const Color(0xFFF59E0B),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              point.captures.isEmpty
                  ? 'Este punto todavía no tiene registros capturados.'
                  : 'Registros en este punto: ${point.captures.length} · Cantidad de plagas: ${point.pestQuantity}',
              style: const TextStyle(color: Colors.black87, fontSize: 15),
            ),
            const Divider(height: 28),
            if (point.captures.isNotEmpty) ...[
              const Text(
                'Detalle capturado',
                style: TextStyle(
                  color: AppTheme.darkGreen,
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 10),
              for (var index = 0; index < point.captures.length; index++) ...[
                _PointCaptureCard(index: index + 1, row: point.captures[index]),
                if (index != point.captures.length - 1)
                  const SizedBox(height: 10),
              ],
              const Divider(height: 30),
            ],
            Row(
              children: [
                Expanded(
                  child: _PointInfoTile(
                    label: 'Estado',
                    value: point.captured ? 'Monitoreado' : 'Pendiente',
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _PointInfoTile(
                    label: 'Fecha principal',
                    value: point.mainDate,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: _PointInfoTile(
                    label: 'Cantidad de plagas',
                    value: '${point.pestQuantity}',
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _PointInfoTile(
                    label: 'Radio',
                    value:
                        '${point.radiusM.toStringAsFixed(point.radiusM % 1 == 0 ? 0 : 1)} m',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            _PointInfoTile(
              label: 'Coordenadas GPS',
              value: point.latitude == 0 && point.longitude == 0
                  ? 'Sin coordenadas'
                  : '${point.latitude.toStringAsFixed(6)}, ${point.longitude.toStringAsFixed(6)}',
            ),
          ],
        ),
      ),
    ),
  );
}

class _IndexChip extends StatelessWidget {
  const _IndexChip({
    required this.prefix,
    required this.text,
    required this.color,
  });
  final String prefix;
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
    decoration: BoxDecoration(
      color: color.withValues(alpha: .10),
      border: Border.all(color: color.withValues(alpha: .22)),
      borderRadius: BorderRadius.circular(99),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 7),
        Text(
          '$prefix · $text',
          style: const TextStyle(fontWeight: FontWeight.w800),
        ),
      ],
    ),
  );
}

class _PointCaptureCard extends StatelessWidget {
  const _PointCaptureCard({required this.index, required this.row});
  final int index;
  final _DisplayRecord row;

  Future<void> _photo(BuildContext context) async {
    final source = row.photo;
    if (source == null || source.trim().isEmpty) return;

    final local = File(source);
    final Widget image;
    if (await local.exists()) {
      image = Image.file(
        local,
        fit: BoxFit.contain,
        gaplessPlayback: true,
        filterQuality: FilterQuality.high,
      );
    } else {
      image = AuthenticatedRemoteImage(
        url: source,
        fit: BoxFit.contain,
        fallback: const _EvidenceUnavailable(),
      );
    }

    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      barrierColor: Colors.black87,
      builder: (_) => _ZoomablePhotoDialog(
        image: image,
        title: '${row.name} - Punto ${row.pointNumber}',
      ),
    );
  }

  void _comment(BuildContext context) {
    if (row.notes.trim().isEmpty) return;
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Nota del hallazgo'),
        content: Text(row.notes),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cerrar'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDisease = row.type == 'Enfermedad';
    final accent = isDisease
        ? const Color(0xFF2E7D32)
        : _hexColor(_pestColorHex(row.severity));
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: const Color(0xFFE1E7DE)),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '$index. ${row.name}',
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: .10),
                  borderRadius: BorderRadius.circular(99),
                ),
                child: Text(
                  row.type == '-' ? 'Registro' : row.type,
                  style: TextStyle(color: accent, fontWeight: FontWeight.w800),
                ),
              ),
            ],
          ),
          if (row.notes.trim().isNotEmpty || row.photo != null) ...[
            const SizedBox(height: 9),
            Wrap(
              spacing: 8,
              children: [
                if (row.notes.trim().isNotEmpty)
                  ActionChip(
                    avatar: const Icon(Icons.chat_bubble_outline, size: 16),
                    label: const Text('Comentario'),
                    onPressed: () => _comment(context),
                  ),
                if (row.photo != null)
                  ActionChip(
                    avatar: const Icon(Icons.photo_outlined, size: 16),
                    label: const Text('Foto'),
                    onPressed: () => _photo(context),
                  ),
              ],
            ),
          ],
          const SizedBox(height: 9),
          _detailLine('Fase / presencia', row.stage),
          if (row.quantity != '—') _detailLine('Cantidad', row.quantity),
          _detailLine('Severidad', row.severity, valueColor: accent),
          _detailLine('Fecha', row.capturedAt),
        ],
      ),
    );
  }

  Widget _detailLine(String label, String value, {Color? valueColor}) =>
      Padding(
        padding: const EdgeInsets.only(top: 5),
        child: RichText(
          text: TextSpan(
            style: const TextStyle(color: Colors.black87, fontSize: 14),
            children: [
              TextSpan(
                text: '$label: ',
                style: const TextStyle(
                  color: AppTheme.darkGreen,
                  fontWeight: FontWeight.w900,
                ),
              ),
              TextSpan(
                text: value,
                style: TextStyle(
                  color: valueColor ?? Colors.black87,
                  fontWeight: valueColor == null
                      ? FontWeight.w400
                      : FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
      );
}

class _PointInfoTile extends StatelessWidget {
  const _PointInfoTile({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Container(
    constraints: const BoxConstraints(minHeight: 86),
    padding: const EdgeInsets.all(13),
    decoration: BoxDecoration(
      color: const Color(0xFFF7FAF4),
      borderRadius: BorderRadius.circular(16),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: AppTheme.darkGreen,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          value,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
      ],
    ),
  );
}

class _ReportSummary {
  const _ReportSummary({
    required this.total,
    required this.pests,
    required this.diseases,
    required this.noPest,
    required this.pending,
  });
  final int total;
  final int pests;
  final int diseases;
  final int noPest;
  final int pending;
}

class _DisplayRecord {
  const _DisplayRecord({
    required this.pointNumber,
    required this.name,
    required this.type,
    required this.stage,
    required this.quantity,
    required this.severity,
    required this.capturedAt,
    required this.photo,
    required this.notes,
    required this.syncState,
    required this.latitude,
    required this.longitude,
  });
  final int pointNumber;
  final String name;
  final String type;
  final String stage;
  final String quantity;
  final String severity;
  final String capturedAt;
  final String? photo;
  final String notes;
  final String syncState;
  final double? latitude;
  final double? longitude;
}

String _pestSeverity(List<PendingCheckpoint> checkpoints, int pestTolerance) {
  final pests = checkpoints
      .where((e) => !e.isDisease && !e.isNoPest)
      .toList(growable: false);

  final total = pests.fold<int>(0, (sum, e) => sum + (e.qty > 0 ? e.qty : 0));
  if (total <= 0) return 'Sin plaga';

  var rank = _pestSeverityRank(_pestSeverityFromTotal(total, pestTolerance));

  // CIAGRO web tambien respeta la severidad que ya venga en presence_status.
  for (final pest in pests) {
    final presenceRank = _presenceSeverityRank(pest.presenceStatus);
    if (presenceRank > rank) rank = presenceRank;
  }

  return _pestSeverityLabelForRank(rank);
}

String _pestSeverityFromTotal(int total, int tolerance) {
  if (total <= 0) return 'Sin plaga';

  final threshold = tolerance < 0 ? 0 : tolerance;

  if (threshold == 0) {
    return total == 1 ? 'Severidad mayor' : 'Severidad alta';
  }

  if (total <= threshold) return 'Severidad menor';
  if (total == threshold + 1) return 'Severidad mayor';
  return 'Severidad alta';
}

int _pestSeverityRank(String label) {
  final value = label.toLowerCase();
  if (value.contains('alta') || value.contains('supera')) return 3;
  if (value.contains('mayor')) return 2;
  if (value.contains('menor')) return 1;
  return 0;
}

int _presenceSeverityRank(dynamic raw) {
  final value = '${raw ?? ''}'.trim().toLowerCase();
  if (value == 'critical') return 3;
  if (value == 'warning') return 2;

  // El frontend web trata cualquier presence_status restante de un hallazgo
  // como low. Solo se llama para plagas con cantidad positiva.
  return 1;
}

String _pestSeverityLabelForRank(int rank) {
  if (rank >= 3) return 'Severidad alta';
  if (rank == 2) return 'Severidad mayor';
  if (rank == 1) return 'Severidad menor';
  return 'Sin plaga';
}

String _diseaseSeverity(String? stage, String presence) {
  final p = presence.trim().toLowerCase();

  if (p == 'critical' || p == 'critica') {
    return 'Alta';
  }
  if (p == 'warning' || p == 'advertencia') {
    return 'Media';
  }
  if (p == 'absent' ||
      p == 'ausente' ||
      p == 'none' ||
      p == 'sin presencia' ||
      p == '0') {
    return 'Sin presencia';
  }
  if (p == 'low' || p == 'baja') {
    return 'Baja';
  }

  final value = (stage ?? '').trim().toLowerCase();
  if (value.contains('avanz') ||
      value.contains('terminal') ||
      value.contains('alta')) {
    return 'Alta';
  }
  if (value.contains('desarrollo') || value.contains('media')) {
    return 'Media';
  }

  return 'Baja';
}

Map<String, int> _pointNumberMap(List<TargetPoint> targets) {
  final result = <String, int>{};
  final coordinateNumbers = <String, int>{};
  var next = 1;
  for (final point in targets) {
    final hasCoordinate = point.latitude != 0 || point.longitude != 0;
    final coordinateKey = hasCoordinate
        ? '${point.latitude.toStringAsFixed(6)},${point.longitude.toStringAsFixed(6)}'
        : 'id:${point.id}';
    final number = coordinateNumbers.putIfAbsent(coordinateKey, () => next++);
    result[point.id] = number;
    final serverId = point.serverId;
    if (serverId != null && serverId.isNotEmpty) result[serverId] = number;
  }
  return result;
}

int _numberForTarget(TargetPoint? point, Map<String, int> numbers) {
  if (point == null) return 0;
  return numbers[point.id] ??
      (point.serverId == null ? null : numbers[point.serverId!]) ??
      point.visibleNumber;
}

String? _remoteTargetId(Map<String, dynamic> item) =>
    flexibleId(item['target'] ?? item['target_point']);

String? _remotePointKey(Map<String, dynamic> item) {
  final pcpOid = int.tryParse('${item['pcp_oid'] ?? ''}');
  if (pcpOid != null && pcpOid > 0) return 'pcp:$pcpOid';

  final targetId = _remoteTargetId(item);
  if (targetId != null && targetId.isNotEmpty) return 'target:$targetId';

  final geom = item['geom'];
  if (geom is Map && geom['coordinates'] is List) {
    final coordinates = geom['coordinates'] as List;
    if (coordinates.length >= 2) {
      return 'coord:${coordinates[0]},${coordinates[1]}';
    }
  }

  return null;
}

TargetPoint? _findTargetForRemote(
  Map<String, dynamic> item,
  String? targetId,
  Map<String, TargetPoint> byServer,
  List<TargetPoint> targets,
) {
  if (targetId != null && byServer[targetId] != null) return byServer[targetId];
  final geom = _geom(item);
  final lat = geom.$1;
  final lon = geom.$2;
  if (lat == null || lon == null) return null;

  TargetPoint? best;
  var bestDistance = double.infinity;
  for (final point in targets) {
    final delta = (point.latitude - lat).abs() + (point.longitude - lon).abs();
    if (delta < bestDistance) {
      bestDistance = delta;
      best = point;
    }
  }
  // Misma tolerancia aproximada que usa Kotlin al reconciliar coordenadas.
  return bestDistance < 0.0004 ? best : null;
}

bool _remoteIsNoPest(
  Map<String, dynamic> item,
  Map<String, PhytoCatalogItem> catalogById,
) {
  final directType = '${item['issue_type'] ?? item['phyto_issue_type'] ?? ''}'
      .trim()
      .toLowerCase();

  // Si el backend ya declara el tipo, no es un registro "Sin plaga".
  if (directType == 'plaga' ||
      directType == 'pest' ||
      directType == 'enfermedad' ||
      directType == 'disease') {
    return false;
  }

  final directIssue = '${item['issue'] ?? ''}'.trim();
  final directIssueLower = directIssue.toLowerCase();

  if (directIssueLower.contains('sin plaga') ||
      directIssueLower.contains('sin_plaga') ||
      directIssueLower.contains('no plaga') ||
      directIssueLower == 'ausente') {
    return true;
  }

  final issueRaw =
      item['phyto_issue'] ?? item['phyto_issue_id'] ?? item['phytosanitary'];

  if (issueRaw is Map) {
    final issue = Map<String, dynamic>.from(issueRaw);
    final text = [
      firstText(issue, const ['name', 'nombre', 'label']) ?? '',
      firstText(issue, const ['type', 'tipo', 'issue_type']) ?? '',
      firstText(issue, const ['description', 'descripcion']) ?? '',
    ].join(' ').toLowerCase();

    if (text.contains('sin plaga') ||
        text.contains('sin_plaga') ||
        text.contains('no plaga')) {
      return true;
    }

    if (text.contains('plaga') ||
        text.contains('pest') ||
        text.contains('enfermedad') ||
        text.contains('disease')) {
      return false;
    }
  }

  final issueId = flexibleId(issueRaw);
  if (issueId != null) {
    final catalogItem = catalogById[issueId];
    if (catalogItem != null) return catalogItem.isNoPest;
  }

  // En el GeoJSON oficial un punto "Sin plaga" no trae issue/issue_type.
  return directIssue.isEmpty && issueRaw == null;
}

bool _remoteIsDisease(
  Map<String, dynamic> item,
  Map<String, PhytoCatalogItem> catalogById,
) {
  final directType =
      '${item['issue_type'] ?? item['phyto_issue_type'] ?? item['type'] ?? item['tipo'] ?? ''}'
          .trim()
          .toLowerCase();

  if (directType == 'enfermedad' ||
      directType == 'disease' ||
      directType.contains('enfermedad') ||
      directType.contains('disease')) {
    return true;
  }

  if (directType == 'plaga' || directType == 'pest') {
    return false;
  }

  final issueRaw =
      item['phyto_issue'] ?? item['phyto_issue_id'] ?? item['phytosanitary'];

  if (issueRaw is Map) {
    final issue = Map<String, dynamic>.from(issueRaw);
    final nestedType =
        '${issue['issue_type'] ?? issue['type'] ?? issue['tipo'] ?? ''}'
            .trim()
            .toLowerCase();

    if (nestedType.contains('enfermedad') || nestedType.contains('disease')) {
      return true;
    }

    if (nestedType == 'plaga' || nestedType == 'pest') {
      return false;
    }
  }

  final issueId = flexibleId(issueRaw);
  if (issueId != null && catalogById[issueId]?.isDisease == true) {
    return true;
  }

  // Fallback solo para registros historicos sin issue_type.
  final stage = '${item['stage'] ?? ''}'.toLowerCase();
  return stage.contains('inicio') ||
      stage.contains('desarrollo') ||
      stage.contains('avanz') ||
      stage.contains('terminal');
}

String _remotePestSeverity(
  List<Map<String, dynamic>> checkpoints,
  Map<String, PhytoCatalogItem> catalogById,
  int pestTolerance,
) {
  final pests = checkpoints
      .where(
        (item) =>
            !_remoteIsNoPest(item, catalogById) &&
            !_remoteIsDisease(item, catalogById),
      )
      .toList(growable: false);

  final total = pests.fold<int>(0, (sum, item) {
    final qty = int.tryParse('${item['qty'] ?? 0}') ?? 0;
    return sum + (qty > 0 ? qty : 0);
  });

  if (total <= 0) return 'Sin plaga';

  var rank = _pestSeverityRank(_pestSeverityFromTotal(total, pestTolerance));

  // Misma segunda regla del web: warning eleva al menos a mayor y critical
  // eleva a alta aunque el total por cantidad fuera menor.
  for (final pest in pests) {
    final presenceRank = _presenceSeverityRank(pest['presence_status']);
    if (presenceRank > rank) rank = presenceRank;
  }

  return _pestSeverityLabelForRank(rank);
}

String _diseasePhase(String? stage, String presence) {
  final p = presence.trim().toLowerCase();

  if (p == 'absent' ||
      p == 'ausente' ||
      p == 'none' ||
      p == 'sin presencia' ||
      p == '0') {
    return 'No presente';
  }

  final clean = stage?.trim();
  if (clean == null || clean.isEmpty) return 'Presente';

  final display = stageLabel(clean);
  return 'Presente / $display';
}

String _cleanSeverityMetadata(String? notes) => (notes ?? '')
    .replaceAll(RegExp(r'\[SEV_PUNTO:M=\d+\]'), '')
    .replaceAll(RegExp(r'\[SEV_PUNTO:m=\d+;M=\d+\]'), '')
    .trim();

String _formatDate(DateTime value) {
  final local = value.toLocal();
  String n(int v) => v.toString().padLeft(2, '0');
  return '${n(local.day)}-${n(local.month)}-${local.year} ${n(local.hour)}:${n(local.minute)}';
}

String _formatDynamicDate(dynamic value) {
  final parsed = DateTime.tryParse('${value ?? ''}');
  return parsed == null ? '${value ?? 'No registrado'}' : _formatDate(parsed);
}

(double?, double?) _geom(Map<String, dynamic> item) {
  final geom = item['geom'];
  if (geom is Map && geom['coordinates'] is List) {
    final c = geom['coordinates'] as List;
    if (c.length >= 2) {
      return (double.tryParse('${c[1]}'), double.tryParse('${c[0]}'));
    }
  }
  return (null, null);
}

String _safeFileName(String value) {
  final clean = value
      .trim()
      .replaceAll(RegExp(r'[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ_-]+'), '_')
      .replaceAll(RegExp(r'_+'), '_');
  return clean.isEmpty ? 'monitoreo' : clean;
}

String _csvEscape(String value) {
  final clean = value.replaceAll('\r', ' ').replaceAll('\n', ' ');
  if (clean.contains(',') || clean.contains('"')) {
    final escaped = clean.replaceAll('"', '""');
    return '"$escaped"';
  }
  return clean;
}

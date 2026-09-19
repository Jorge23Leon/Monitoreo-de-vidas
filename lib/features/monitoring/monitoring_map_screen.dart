import 'dart:async';

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';

import '../../core/app_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/app_header.dart';
import '../../core/widgets/leaflet_map.dart';
import '../../core/widgets/gpa_loading_indicator.dart';
import 'monitoring_models.dart';
import 'monitoring_repository.dart';
import 'monitoring_services.dart';

class MonitoringMapScreen extends StatefulWidget {
  const MonitoringMapScreen({super.key});

  @override
  State<MonitoringMapScreen> createState() => _MonitoringMapScreenState();
}

class _MonitoringMapScreenState extends State<MonitoringMapScreen> {
  final repo = MonitoringRepository();
  late MonitoringHeader header;
  late Future<List<TargetPoint>> pointsFuture;
  List<({double lat, double lon})> polygon = const [];
  MapPoint? currentLocation;
  double? currentAccuracy;
  int pending = 0;
  bool syncing = false;
  bool creatingPoint = false;
  bool changingStatus = false;
  bool paused = false;
  bool completed = false;
  Timer? _expiryTimer;
  StreamSubscription<Position>? _gpsSubscription;

  @override
  void initState() {
    super.initState();
    final raw =
        AppScope.read(context).selectedMonitoring ?? const <String, dynamic>{};
    header = MonitoringHeader.fromJson(raw);
    paused = header.isPaused;
    completed = header.isCompleted;
    pointsFuture = repo.targetPoints(header.id);
    _bootstrap();
    _expiryTimer = Timer.periodic(
      const Duration(seconds: 1),
      (_) => _checkExpiry(),
    );
  }

  @override
  void dispose() {
    _expiryTimer?.cancel();
    _gpsSubscription?.cancel();
    super.dispose();
  }

  Future<void> _checkExpiry() async {
    if (!mounted || completed || changingStatus) return;
    final end = DateTime.tryParse(header.endDate ?? '');
    if (end == null) return;
    final localEnd = end.toLocal();
    final endOfDay = DateTime(
      localEnd.year,
      localEnd.month,
      localEnd.day,
      23,
      59,
      59,
    );
    if (!DateTime.now().isAfter(endOfDay)) return;
    final closed = await repo.autoCloseIfExpired(header);
    if (!mounted || !closed) return;
    setState(() {
      completed = true;
      paused = false;
    });
    _snack(
      'El tiempo del monitoreo terminó. Se cerró localmente y se sincronizará cuando no queden capturas pendientes.',
    );
  }

  Future<void> _bootstrap() async {
    // Pinta primero SQLite y actualiza targets desde la API sin bloquear el
    // primer frame del mapa.
    unawaited(_refreshPointsFromServer());
    final closedByDate = await repo.autoCloseIfExpired(header);
    final poly = await repo.plotPolygon(header);
    if (mounted) {
      setState(() {
        polygon = poly;
        if (closedByDate) {
          completed = true;
          paused = false;
        }
      });
    }
    await _loadPending();
    await _updateCurrentLocation(silent: true);
    _startGpsTracking();
  }

  Future<void> _refreshPointsFromServer() async {
    final latest = await repo.refreshTargetPoints(header.id);
    if (!mounted) return;
    setState(() => pointsFuture = Future.value(latest));
  }

  Future<void> _loadPending() async {
    final value = await repo.pendingCount(header.id);
    if (mounted) setState(() => pending = value);
  }

  void _startGpsTracking() {
    _gpsSubscription?.cancel();
    _gpsSubscription = MonitoringLocationService.positionStream().listen(
      (position) {
        if (!mounted) return;
        setState(() {
          currentLocation = MapPoint(
            position.latitude,
            position.longitude,
            label: 'Tu ubicación',
          );
          currentAccuracy = position.accuracy;
        });
      },
      onError: (_) {
        // El botón de GPS sigue disponible para reintentar manualmente.
      },
    );
  }

  Future<void> _updateCurrentLocation({bool silent = false}) async {
    try {
      final position = await MonitoringLocationService.currentPosition();
      if (!mounted) return;
      setState(() {
        currentLocation = MapPoint(
          position.latitude,
          position.longitude,
          label: 'Tu ubicación',
        );
        currentAccuracy = position.accuracy;
      });
    } catch (error) {
      if (!silent && mounted) _snack(_message(error));
    }
  }

  Future<void> _refresh() async {
    setState(() => pointsFuture = repo.refreshTargetPoints(header.id));
    await pointsFuture;
    await _loadPending();
    await _updateCurrentLocation(silent: true);
    _startGpsTracking();
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

  Future<void> _newPoint() async {
    if (creatingPoint || completed) return;
    if (paused) {
      _snack(
        'El monitoreo está pausado. Toca Continuar antes de registrar otro punto.',
      );
      return;
    }
    setState(() => creatingPoint = true);
    try {
      // Si quedó un punto móvil sin finalizar, se retoma antes de crear otro.
      // Evita targets huérfanos y mantiene la numeración igual que Android.
      final knownPoints = await repo.targetPoints(header.id);
      TargetPoint? unfinishedLocal;
      for (final point in knownPoints) {
        if (point.id.startsWith('local_') && !point.completed) {
          unfinishedLocal = point;
          break;
        }
      }
      if (unfinishedLocal != null) {
        if (!mounted) return;
        final resume = await _confirm(
          'Punto pendiente',
          '${unfinishedLocal.label} todavía no se ha guardado. Debes terminar ese punto antes de crear uno nuevo. ¿Continuar ahora?',
        );
        if (resume && mounted) {
          AppScope.read(
            context,
          ).selectTargetPoint(unfinishedLocal.navigationRaw);
        }
        return;
      }

      final position = await MonitoringLocationService.capturePosition();
      final insideParcel =
          polygon.isEmpty ||
          MonitoringLocationService.pointInsidePolygon(
            position.latitude,
            position.longitude,
            polygon,
          );

      if (!insideParcel) {
        if (!mounted) return;
        await _showInfo(
          'Acércate a la parcela',
          'Tu ubicación GPS está fuera del polígono de la parcela. '
              'Acércate al área verde antes de registrar el punto. '
              'Precisión actual: ${position.accuracy.toStringAsFixed(0)} m.',
        );
        return;
      }

      if (!mounted) return;
      final ok = await _confirm(
        '¿Registrar este punto?',
        'Estás dentro de la parcela. Se guardará tu ubicación GPS real con '
            '${position.accuracy.toStringAsFixed(0)} m de precisión. '
            '¿Seguro que quieres registrar el punto aquí?',
      );
      if (!ok) return;

      final target = await repo.createLocalTarget(
        headerId: header.id,
        latitude: position.latitude,
        longitude: position.longitude,
      );

      // Kotlin inicia el header con el primer punto confirmado, no al abrir el mapa.
      final existing = await repo.localCheckpoints(header.id);
      if (existing.isEmpty && _statusKey(header.status) == 'pending') {
        await repo.startHeader(header.id);
      }

      if (!mounted) return;
      final app = AppScope.read(context);
      app.selectTargetPoint(target.navigationRaw);
    } catch (error) {
      if (mounted) _snack(_message(error));
    } finally {
      if (mounted) setState(() => creatingPoint = false);
    }
  }

  Future<void> _pause() async {
    if (changingStatus || completed) return;
    final ok = await _confirm(
      'Pausar monitoreo',
      'El monitoreo quedará en progreso con la marca PAUSADO y podrás continuar después.',
    );
    if (!ok) return;
    setState(() => changingStatus = true);
    try {
      await repo.pauseHeader(header.id);
      if (!mounted) return;
      setState(() => paused = true);
      _snack(
        'Monitoreo pausado. Si no hay internet, el cambio queda pendiente.',
      );
      AppScope.read(context).go(AppPage.monitoringList);
    } finally {
      if (mounted) setState(() => changingStatus = false);
    }
  }

  Future<void> _resume() async {
    if (changingStatus || completed) return;
    setState(() => changingStatus = true);
    try {
      await repo.resumeHeader(header.id);
      if (!mounted) return;
      setState(() => paused = false);
      _snack('Monitoreo reanudado.');
    } finally {
      if (mounted) setState(() => changingStatus = false);
    }
  }

  Future<void> _finish() async {
    if (changingStatus || completed) return;
    final points = await repo.targetPoints(header.id);
    final captured = points.where((point) => point.completed).length;
    if (captured <= 0) {
      _snack('Necesitas guardar mínimo 1 punto para terminar el monitoreo.');
      return;
    }
    final ok = await _confirm(
      'Finalizar monitoreo',
      'Has guardado $captured punto(s). Después de finalizar podrás consultar el reporte. Las capturas pendientes se conservarán localmente hasta sincronizar.',
    );
    if (!ok) return;
    setState(() => changingStatus = true);
    try {
      await repo.completeHeader(header.id);
      if (!mounted) return;
      setState(() {
        completed = true;
        paused = false;
      });
      _snack('Monitoreo finalizado localmente.');
      AppScope.read(context).go(AppPage.monitoringReport);
    } catch (error) {
      if (mounted) _snack(_message(error));
    } finally {
      if (mounted) setState(() => changingStatus = false);
    }
  }

  Future<String?> _askCancellationReason() async {
    // IMPORTANTE: no usamos un TextEditingController creado fuera del diálogo.
    // showDialog completa su Future cuando empieza el pop, pero la ruta todavía
    // puede seguir unos frames en la animación de salida. Disponer el controller
    // en ese instante dejaba al TextField vivo durante la transición y podía
    // provocar la pantalla roja del framework al tocar "Volver".
    String draftReason = '';
    String? errorText;

    return showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Cancelar monitoreo'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Solo puedes cancelar antes de registrar el primer punto. '
                'Escribe por qué no se realizará el monitoreo.',
              ),
              const SizedBox(height: 12),
              TextField(
                autofocus: true,
                maxLines: 4,
                minLines: 2,
                onChanged: (value) => draftReason = value,
                decoration: InputDecoration(
                  labelText: 'Motivo de cancelación',
                  hintText: 'Ej. Llovió o se realizó una aspersión',
                  errorText: errorText,
                  border: const OutlineInputBorder(),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                FocusManager.instance.primaryFocus?.unfocus();
                Navigator.of(dialogContext).pop();
              },
              child: const Text('Volver'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFFC62828),
              ),
              onPressed: () {
                final clean = draftReason.trim();
                if (clean.isEmpty) {
                  setDialogState(
                    () => errorText = 'Escribe el motivo para cancelar.',
                  );
                  return;
                }
                FocusManager.instance.primaryFocus?.unfocus();
                Navigator.of(dialogContext).pop(clean);
              },
              child: const Text('Cancelar monitoreo'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _cancelMonitoring() async {
    if (changingStatus || completed) return;

    final records = await repo.localCheckpoints(header.id);
    if (records.isNotEmpty) {
      _snack(
        'Este monitoreo ya tiene puntos registrados y ya no se puede cancelar.',
      );
      return;
    }

    final reason = await _askCancellationReason();
    if (reason == null || reason.trim().isEmpty || !mounted) return;

    setState(() => changingStatus = true);
    var cancelled = false;
    try {
      await repo.cancelHeader(header.id, reason: reason);
      if (!mounted) return;
      _snack('Monitoreo cancelado. El motivo quedó guardado.');
      cancelled = true;
    } catch (error) {
      if (mounted) _snack(_message(error));
    } finally {
      // Terminamos el estado de carga ANTES de cambiar de pantalla. Así no
      // queda un setState pendiente sobre MonitoringMapScreen mientras sale.
      if (mounted) setState(() => changingStatus = false);
    }

    if (!cancelled || !mounted) return;

    // Damos tiempo a que el diálogo/teclado terminen de salir antes de cambiar
    // la página controlada por AppScope. Evita desmontar dependencias durante la
    // transición del Overlay/Navigator.
    await Future<void>.delayed(const Duration(milliseconds: 300));
    if (!mounted) return;
    AppScope.read(context).go(AppPage.monitoringList);
  }

  Future<void> _handleBack() async {
    if (changingStatus || creatingPoint) return;
    if (completed || paused) {
      AppScope.read(context).go(AppPage.monitoringList);
      return;
    }

    // Paridad con Kotlin: Atrás dentro de un monitoreo activo NO cierra la app
    // ni salta pantallas. Si todavía no hay capturas se explica el requisito;
    // con capturas se abre el mismo flujo de confirmación para terminar.
    final points = await repo.targetPoints(header.id);
    final captured = points.where((point) => point.completed).length;
    if (!mounted) return;
    if (captured <= 0) {
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Captura requerida'),
          content: const Text(
            'Necesitas guardar mínimo 1 punto para terminar el monitoreo.',
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Aceptar'),
            ),
          ],
        ),
      );
      return;
    }
    await _finish();
  }

  Future<bool> _confirm(String title, String message) async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(title),
            content: Text(message),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Confirmar'),
              ),
            ],
          ),
        ) ??
        false;
  }

  Future<void> _showInfo(String title, String message) async {
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Entendido'),
          ),
        ],
      ),
    );
  }

  Future<void> _openFullscreenMap({
    required List<MapPoint> points,
    required List<MapPoint> polygonPoints,
  }) async {
    await showDialog<void>(
      context: context,
      useSafeArea: false,
      builder: (dialogContext) => Dialog.fullscreen(
        child: Scaffold(
          backgroundColor: const Color(0xFFFBFCF8),
          appBar: AppBar(
            backgroundColor: Colors.white,
            surfaceTintColor: Colors.white,
            leading: IconButton(
              tooltip: 'Cerrar mapa completo',
              onPressed: () => Navigator.of(dialogContext).pop(),
              icon: const Icon(Icons.arrow_back, color: AppTheme.darkGreen),
            ),
            title: const Text(
              'Mapa del monitoreo',
              style: TextStyle(
                color: AppTheme.darkGreen,
                fontWeight: FontWeight.w900,
              ),
            ),
            actions: [
              Padding(
                padding: const EdgeInsets.only(right: 12),
                child: Center(
                  child: Text(
                    currentAccuracy == null
                        ? 'GPS buscando...'
                        : 'GPS ${currentAccuracy!.toStringAsFixed(0)} m',
                    style: const TextStyle(
                      color: AppTheme.primary,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ),
            ],
          ),
          body: LayoutBuilder(
            builder: (context, constraints) => Stack(
              children: [
                LeafletMap(
                  points: points,
                  polygon: polygonPoints,
                  currentLocation: currentLocation,
                  currentAccuracy: currentAccuracy,
                  onMapTap: completed || paused
                      ? null
                      : () {
                          Navigator.of(dialogContext).pop();
                          Future<void>.microtask(_newPoint);
                        },
                  height: constraints.maxHeight,
                ),
                Positioned(
                  top: 14,
                  right: 14,
                  child: Material(
                    color: Colors.white,
                    elevation: 3,
                    shape: const CircleBorder(),
                    child: IconButton(
                      tooltip: 'Actualizar GPS',
                      onPressed: () => _updateCurrentLocation(),
                      icon: const Icon(
                        Icons.my_location,
                        color: AppTheme.primary,
                      ),
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

  @override
  Widget build(BuildContext context) {
    final app = AppScope.of(context);
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _handleBack();
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFFBFCF8),
        body: Column(
          children: [
            AppHeader(title: 'Mapa del monitoreo', onBack: _handleBack),
            Expanded(
              child: RefreshIndicator(
                onRefresh: _refresh,
                child: FutureBuilder<List<TargetPoint>>(
                  future: pointsFuture,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState != ConnectionState.done) {
                      return const Center(
                        child: GpaLoadingIndicator(text: 'Cargando mapa...'),
                      );
                    }
                    final points = snapshot.data ?? const <TargetPoint>[];
                    if (snapshot.hasError && points.isEmpty) {
                      return ListView(
                        padding: const EdgeInsets.all(24),
                        children: [
                          const SizedBox(height: 100),
                          const Icon(
                            Icons.map_outlined,
                            size: 70,
                            color: Colors.black26,
                          ),
                          const SizedBox(height: 18),
                          Text(
                            '${snapshot.error}',
                            textAlign: TextAlign.center,
                          ),
                        ],
                      );
                    }

                    final validPoints = points
                        .where((e) => e.latitude != 0 || e.longitude != 0)
                        .toList(growable: false);
                    final mapPoints = <MapPoint>[
                      for (var index = 0; index < validPoints.length; index++)
                        MapPoint(
                          validPoints[index].latitude,
                          validPoints[index].longitude,
                          label:
                              '${validPoints[index].label} · ${validPoints[index].completed ? 'Capturado' : 'Pendiente'}',
                          markerText:
                              '${validPoints[index].visibleNumber > 0 ? validPoints[index].visibleNumber : index + 1}',
                          markerColor: validPoints[index].completed
                              ? '#1BA64B'
                              : '#F59E0B',
                        ),
                    ];
                    final polygonPoints = polygon
                        .map((e) => MapPoint(e.lat, e.lon))
                        .toList(growable: false);
                    final completedPoints = points
                        .where((e) => e.completed)
                        .length;

                    return ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(12, 14, 12, 30),
                      children: [
                        _MonitoringSummary(
                          header: header,
                          total: points.length,
                          pendingSync: pending,
                          syncing: syncing,
                          paused: paused,
                          finished: completed,
                          changingStatus: changingStatus,
                          canCancel: completedPoints == 0,
                          onSync: _sync,
                          onPause: _pause,
                          onResume: _resume,
                          onFinish: _finish,
                          onCancel: _cancelMonitoring,
                          onReport: () => app.go(AppPage.monitoringReport),
                        ),
                        const SizedBox(height: 12),
                        Card(
                          margin: EdgeInsets.zero,
                          elevation: 2,
                          color: Colors.white,
                          surfaceTintColor: Colors.transparent,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.all(10),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    const Expanded(
                                      child: Text(
                                        'Mapa del monitoreo',
                                        style: TextStyle(
                                          color: AppTheme.darkGreen,
                                          fontSize: 20,
                                          fontWeight: FontWeight.w900,
                                        ),
                                      ),
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 10,
                                        vertical: 7,
                                      ),
                                      decoration: BoxDecoration(
                                        color: const Color(0xFFE9F7E7),
                                        borderRadius: BorderRadius.circular(20),
                                      ),
                                      child: Text(
                                        '${polygon.length} vértices',
                                        style: const TextStyle(
                                          color: AppTheme.primary,
                                          fontWeight: FontWeight.w900,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  currentAccuracy == null
                                      ? 'Toca el mapa para registrar con tu GPS real. Buscando precisión...'
                                      : 'Toca el mapa para registrar con tu GPS real · precisión ${currentAccuracy!.toStringAsFixed(0)} m.',
                                  style: const TextStyle(
                                    color: Colors.black54,
                                    fontSize: 12,
                                  ),
                                ),
                                const SizedBox(height: 10),
                                Stack(
                                  children: [
                                    LeafletMap(
                                      points: mapPoints,
                                      polygon: polygonPoints,
                                      currentLocation: currentLocation,
                                      currentAccuracy: currentAccuracy,
                                      onMapTap: completed || paused
                                          ? null
                                          : _newPoint,
                                      height: 390,
                                    ),
                                    Positioned(
                                      top: 10,
                                      right: 10,
                                      child: Row(
                                        children: [
                                          Material(
                                            color: Colors.white,
                                            elevation: 2,
                                            shape: const CircleBorder(),
                                            child: IconButton(
                                              tooltip: 'Abrir mapa completo',
                                              onPressed: () =>
                                                  _openFullscreenMap(
                                                    points: mapPoints,
                                                    polygonPoints:
                                                        polygonPoints,
                                                  ),
                                              icon: const Icon(
                                                Icons.fullscreen,
                                                color: AppTheme.darkGreen,
                                              ),
                                            ),
                                          ),
                                          const SizedBox(width: 8),
                                          Material(
                                            color: Colors.white,
                                            elevation: 2,
                                            shape: const CircleBorder(),
                                            child: IconButton(
                                              tooltip: 'Actualizar GPS',
                                              onPressed: () =>
                                                  _updateCurrentLocation(),
                                              icon: const Icon(
                                                Icons.my_location,
                                                color: AppTheme.primary,
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 12),
                                SizedBox(
                                  width: double.infinity,
                                  height: 52,
                                  child: FilledButton.icon(
                                    onPressed:
                                        completed || paused || creatingPoint
                                        ? null
                                        : _newPoint,
                                    icon: creatingPoint
                                        ? const GpaLoadingIndicator(
                                            size: 24,
                                            showText: false,
                                          )
                                        : const Icon(
                                            Icons.add_location_alt_outlined,
                                          ),
                                    label: Text(
                                      creatingPoint
                                          ? 'Obteniendo GPS...'
                                          : completedPoints == 0
                                          ? 'Iniciar monitoreo con primer punto'
                                          : 'Registrar nuevo punto',
                                    ),
                                    style: FilledButton.styleFrom(
                                      backgroundColor: const Color(0xFF176E35),
                                      shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(14),
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 14),
                        Row(
                          children: [
                            const Expanded(
                              child: Text(
                                'Puntos monitoreados',
                                style: TextStyle(
                                  color: AppTheme.darkGreen,
                                  fontSize: 19,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            ),
                            Text('$completedPoints/${points.length}'),
                          ],
                        ),
                        const SizedBox(height: 9),
                        if (points.isEmpty)
                          const Card(
                            child: Padding(
                              padding: EdgeInsets.all(20),
                              child: Text(
                                'Todavía no hay puntos. El primer punto se crea con tu GPS real dentro de la parcela.',
                                textAlign: TextAlign.center,
                              ),
                            ),
                          ),
                        ...points.map(
                          (point) => Padding(
                            padding: const EdgeInsets.only(bottom: 9),
                            child: _TargetCard(
                              point: point,
                              onTap: point.completed
                                  ? null
                                  : () => app.selectTargetPoint(
                                      point.navigationRaw,
                                    ),
                            ),
                          ),
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

  String _message(Object error) =>
      error.toString().replaceFirst('Exception: ', '');
}

class _MonitoringSummary extends StatelessWidget {
  const _MonitoringSummary({
    required this.header,
    required this.total,
    required this.pendingSync,
    required this.syncing,
    required this.paused,
    required this.finished,
    required this.changingStatus,
    required this.canCancel,
    required this.onSync,
    required this.onPause,
    required this.onResume,
    required this.onFinish,
    required this.onCancel,
    required this.onReport,
  });

  final MonitoringHeader header;
  final int total;
  final int pendingSync;
  final bool syncing;
  final bool paused;
  final bool finished;
  final bool changingStatus;
  final bool canCancel;
  final VoidCallback onSync;
  final VoidCallback onPause;
  final VoidCallback onResume;
  final VoidCallback onFinish;
  final VoidCallback onCancel;
  final VoidCallback onReport;

  @override
  Widget build(BuildContext context) {
    final label = finished
        ? 'Completado'
        : paused
        ? 'Pausado'
        : total == 0
        ? 'Pendiente'
        : 'En progreso';
    final accent = finished
        ? const Color(0xFF2E7D32)
        : paused
        ? const Color(0xFFD66B00)
        : const Color(0xFF176E35);
    return Card(
      margin: EdgeInsets.zero,
      elevation: 2,
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Padding(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Monitoreo - ${header.plotName}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppTheme.darkGreen,
                      fontSize: 21,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 11,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: accent.withValues(alpha: .11),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    label,
                    style: TextStyle(
                      color: accent,
                      fontWeight: FontWeight.w900,
                      fontSize: 11,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              '${header.programName} · ${header.cropName}',
              style: const TextStyle(
                color: Colors.black54,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              _remainingText(header.endDate),
              style: const TextStyle(color: Colors.black45, fontSize: 11),
            ),
            const SizedBox(height: 14),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _SmallStatus(
                  icon: pendingSync > 0
                      ? Icons.cloud_upload_outlined
                      : Icons.cloud_done_outlined,
                  text: pendingSync > 0
                      ? 'Pendiente de sincronizar'
                      : 'Sincronizado',
                ),
              ],
            ),
            if (!finished) ...[
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: changingStatus
                          ? null
                          : (paused ? onResume : onPause),
                      icon: Icon(paused ? Icons.play_arrow : Icons.pause),
                      label: Text(paused ? 'Continuar' : 'Pausar'),
                      style: FilledButton.styleFrom(
                        backgroundColor: paused
                            ? const Color(0xFF2E7D32)
                            : const Color(0xFFF28C00),
                        foregroundColor: Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: changingStatus ? null : onFinish,
                      icon: const Icon(Icons.check),
                      label: const Text('Terminar'),
                      style: FilledButton.styleFrom(
                        backgroundColor: const Color(0xFF176E35),
                      ),
                    ),
                  ),
                ],
              ),
              if (canCancel) ...[
                const SizedBox(height: 9),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: changingStatus ? null : onCancel,
                    icon: const Icon(Icons.cancel_outlined),
                    label: const Text('Cancelar monitoreo'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFFC62828),
                      side: const BorderSide(color: Color(0xFFC62828)),
                    ),
                  ),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}

class _SmallStatus extends StatelessWidget {
  const _SmallStatus({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
    decoration: BoxDecoration(
      color: const Color(0xFFF2F7EE),
      borderRadius: BorderRadius.circular(12),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: AppTheme.primary),
        const SizedBox(width: 6),
        Text(
          text,
          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800),
        ),
      ],
    ),
  );
}

class _TargetCard extends StatelessWidget {
  const _TargetCard({required this.point, this.onTap});
  final TargetPoint point;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final done = point.completed;
    return Card(
      margin: EdgeInsets.zero,
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ListTile(
        onTap: onTap,
        leading: CircleAvatar(
          backgroundColor: done
              ? const Color(0xFFE1F2DE)
              : const Color(0xFFFFF0D9),
          child: Icon(
            done ? Icons.check : Icons.location_on,
            color: done ? AppTheme.primary : const Color(0xFFD66B00),
          ),
        ),
        title: Text(
          point.label,
          style: const TextStyle(fontWeight: FontWeight.w900),
        ),
        subtitle: Text(
          done
              ? 'Capturado · ${point.serverId == null ? 'Pendiente de sincronizar' : 'Sincronizado'}'
              : 'Pendiente de captura · toca para continuar',
          style: const TextStyle(fontSize: 12),
        ),
        trailing: Icon(
          point.needsSync
              ? Icons.cloud_upload_outlined
              : Icons.cloud_done_outlined,
          color: done ? AppTheme.primary : Colors.black45,
        ),
      ),
    );
  }
}

String _statusKey(String raw) {
  final value = raw.toLowerCase().trim().replaceAll(' ', '_');
  if (value.contains('complet') || value.contains('finaliz'))
    return 'completed';
  if (value.contains('progress') || value.contains('progreso'))
    return 'in_progress';
  return 'pending';
}

String _remainingText(String? raw) {
  if (raw == null || raw.trim().isEmpty) {
    return 'Sin fecha estimada de fin';
  }

  final parsed = DateTime.tryParse(raw)?.toLocal();
  if (parsed == null) return 'Fin estimado: $raw';

  final end = DateTime(parsed.year, parsed.month, parsed.day, 23, 59, 59);

  final diff = end.difference(DateTime.now());
  final formatted =
      '${parsed.day.toString().padLeft(2, '0')}/${parsed.month.toString().padLeft(2, '0')}/${parsed.year}';

  if (diff.isNegative) {
    return 'Sesion vencida - fin $formatted';
  }

  final days = diff.inDays;
  final hours = diff.inHours.remainder(24);
  return 'Tiempo restante: ${days}d ${hours}h - fin $formatted';
}

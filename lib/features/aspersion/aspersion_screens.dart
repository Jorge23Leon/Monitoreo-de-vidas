import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/widgets/app_header.dart';
import '../../core/widgets/leaflet_map.dart';
import '../../core/widgets/state_views.dart';
import '../common/remote_list_screen.dart';
import '../common/repositories.dart';

class AspersionListScreen extends StatelessWidget {
  const AspersionListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final app = AppScope.read(context);
    final repo = CiagroRepository();
    final cia = '${app.selectedCia?['id'] ?? ''}';

    return RemoteListScreen(
      title: 'Aspersión',
      emptyText: 'No hay sesiones de aspersión para esta CIA.',
      loader: () => repo.aspersionSessions(cia: cia),
      onTap: app.selectAspersion,
      subtitleBuilder: (item) =>
          'Fecha: ${item['aspersion_date'] ?? item['date'] ?? '—'} · '
          'Estado: ${item['status'] ?? '—'} · Parcela: ${item['plot'] ?? '—'}',
    );
  }
}

class AspersionDetailScreen extends StatefulWidget {
  const AspersionDetailScreen({super.key});

  @override
  State<AspersionDetailScreen> createState() => _AspersionDetailScreenState();
}

class _AspersionDetailScreenState extends State<AspersionDetailScreen> {
  final repo = CiagroRepository();
  late Future<List<Map<String, dynamic>>> future;

  String get _selectedId {
    final selected = AppScope.read(context).selectedAspersion;
    return '${selected?['id'] ?? selected?['session_id'] ?? ''}';
  }

  @override
  void initState() {
    super.initState();
    final selected = AppScope.read(context).selectedAspersion;
    final id = '${selected?['id'] ?? selected?['session_id'] ?? ''}';
    future = _load(id);
  }

  Future<List<Map<String, dynamic>>> _load(String id) async {
    final detail = await repo.aspersionDetail(id);
    final stats = await repo.aspersionStats(id);
    return [detail, stats];
  }

  @override
  Widget build(BuildContext context) {
    final app = AppScope.read(context);
    return Scaffold(
      body: Column(
        children: [
          const AppHeader(title: 'Detalle de aspersión'),
          Expanded(
            child: FutureBuilder<List<Map<String, dynamic>>>(
              future: future,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const LoadingView();
                }
                if (snapshot.hasError) {
                  return ErrorView(
                    message: '${snapshot.error}',
                    onRetry: () => setState(() => future = _load(_selectedId)),
                  );
                }

                final rows = snapshot.data ?? const <Map<String, dynamic>>[];
                return ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    for (final block in rows)
                      for (final entry in block.entries)
                        Card(
                          child: ListTile(
                            title: Text(entry.key.replaceAll('_', ' ')),
                            subtitle: Text('${entry.value}'),
                          ),
                        ),
                    const SizedBox(height: 12),
                    FilledButton.icon(
                      onPressed: () => app.go(AppPage.aspersionMap),
                      icon: const Icon(Icons.map),
                      label: const Text('Ver mapa completo'),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class AspersionMapScreen extends StatefulWidget {
  const AspersionMapScreen({super.key});

  @override
  State<AspersionMapScreen> createState() => _AspersionMapScreenState();
}

class _AspersionMapScreenState extends State<AspersionMapScreen> {
  final repo = CiagroRepository();
  late Future<List<Map<String, dynamic>>> future;

  String _idFromSelection() {
    final selected = AppScope.read(context).selectedAspersion;
    return '${selected?['id'] ?? selected?['session_id'] ?? ''}';
  }

  @override
  void initState() {
    super.initState();
    final selected = AppScope.read(context).selectedAspersion;
    final id = '${selected?['id'] ?? selected?['session_id'] ?? ''}';
    future = repo.aspersionPoints(id);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          const AppHeader(title: 'Mapa de aspersión'),
          Expanded(
            child: FutureBuilder<List<Map<String, dynamic>>>(
              future: future,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const LoadingView();
                }
                if (snapshot.hasError) {
                  return ErrorView(
                    message: '${snapshot.error}',
                    onRetry: () => setState(
                      () => future = repo.aspersionPoints(_idFromSelection()),
                    ),
                  );
                }

                final points = <MapPoint>[];
                for (final item in snapshot.data ?? const <Map<String, dynamic>>[]) {
                  double? lat = double.tryParse('${item['latitude'] ?? item['lat'] ?? ''}');
                  double? lon = double.tryParse('${item['longitude'] ?? item['lon'] ?? ''}');
                  final geom = item['geom'];
                  if ((lat == null || lon == null) && geom is Map && geom['coordinates'] is List) {
                    final coordinates = geom['coordinates'] as List;
                    if (coordinates.length >= 2) {
                      lon = double.tryParse('${coordinates[0]}');
                      lat = double.tryParse('${coordinates[1]}');
                    }
                  }
                  if (lat != null && lon != null) {
                    points.add(
                      MapPoint(
                        lat,
                        lon,
                        label:
                            'Velocidad ${item['speed_kmh'] ?? '—'} km/h · Aplicado ${item['applied_rate_l'] ?? '—'}',
                      ),
                    );
                  }
                }

                return ListView(
                  padding: const EdgeInsets.all(14),
                  children: [
                    LeafletMap(
                      points: points,
                      height: MediaQuery.sizeOf(context).height * .68,
                    ),
                    const SizedBox(height: 10),
                    Text('${points.length} puntos de aspersión'),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

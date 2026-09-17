import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/widgets/app_header.dart';
import '../../core/widgets/leaflet_map.dart';
import '../../core/widgets/state_views.dart';
import '../common/remote_list_screen.dart';
import '../common/repositories.dart';

class NdviListScreen extends StatelessWidget {
  const NdviListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final app = AppScope.read(context);
    final repo = CiagroRepository();
    final cia = '${app.selectedCia?['id'] ?? ''}';

    return RemoteListScreen(
      title: 'NDVI',
      emptyText: 'No hay sesiones NDVI para esta CIA.',
      loader: () => repo.ndviSessions(cia: cia),
      onTap: app.selectNdvi,
      subtitleBuilder: (item) =>
          'Fecha: ${item['session_date'] ?? item['monitoring_date'] ?? item['date'] ?? item['created_at'] ?? '—'} · '
          'Parcela: ${item['plot'] ?? '—'}',
    );
  }
}

class NdviDetailScreen extends StatefulWidget {
  const NdviDetailScreen({super.key});

  @override
  State<NdviDetailScreen> createState() => _NdviDetailScreenState();
}

class _NdviDetailScreenState extends State<NdviDetailScreen> {
  final repo = CiagroRepository();
  late Future<List<Map<String, dynamic>>> future;

  String _selectedId() {
    final selected = AppScope.read(context).selectedNdvi;
    return '${selected?['id'] ?? selected?['session_id'] ?? ''}';
  }

  @override
  void initState() {
    super.initState();
    final selected = AppScope.read(context).selectedNdvi;
    final id = '${selected?['id'] ?? selected?['session_id'] ?? ''}';
    future = _load(id);
  }

  Future<List<Map<String, dynamic>>> _load(String id) async {
    return [
      await repo.ndviDetail(id),
      await repo.ndviStats(id),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final app = AppScope.read(context);
    return Scaffold(
      body: Column(
        children: [
          const AppHeader(title: 'Detalle NDVI'),
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
                    onRetry: () => setState(() => future = _load(_selectedId())),
                  );
                }

                return ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    for (final block in snapshot.data ?? const <Map<String, dynamic>>[])
                      for (final entry in block.entries)
                        Card(
                          child: ListTile(
                            title: Text(entry.key.replaceAll('_', ' ')),
                            subtitle: Text('${entry.value}'),
                          ),
                        ),
                    const SizedBox(height: 12),
                    FilledButton.icon(
                      onPressed: () => app.go(AppPage.ndviMap),
                      icon: const Icon(Icons.map),
                      label: const Text('Ver mapa NDVI'),
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

class NdviMapScreen extends StatefulWidget {
  const NdviMapScreen({super.key});

  @override
  State<NdviMapScreen> createState() => _NdviMapScreenState();
}

class _NdviMapScreenState extends State<NdviMapScreen> {
  final repo = CiagroRepository();
  late Future<List<Map<String, dynamic>>> future;

  String _selectedId() {
    final selected = AppScope.read(context).selectedNdvi;
    return '${selected?['id'] ?? selected?['session_id'] ?? ''}';
  }

  @override
  void initState() {
    super.initState();
    final selected = AppScope.read(context).selectedNdvi;
    final id = '${selected?['id'] ?? selected?['session_id'] ?? ''}';
    future = repo.ndviPoints(id);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          const AppHeader(title: 'Mapa NDVI'),
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
                      () => future = repo.ndviPoints(_selectedId()),
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

                  final value = double.tryParse(
                    '${item['ndvi'] ?? item['value'] ?? item['index_value'] ?? ''}',
                  );
                  if (lat != null && lon != null) {
                    points.add(
                      MapPoint(
                        lat,
                        lon,
                        value: value,
                        label: 'NDVI ${value?.toStringAsFixed(3) ?? '—'}',
                      ),
                    );
                  }
                }

                return ListView(
                  padding: const EdgeInsets.all(14),
                  children: [
                    LeafletMap(
                      points: points,
                      ndvi: true,
                      height: MediaQuery.sizeOf(context).height * .62,
                    ),
                    const SizedBox(height: 12),
                    const Wrap(
                      spacing: 10,
                      runSpacing: 6,
                      children: [
                        _Legend('Sin datos', Color(0xFF555555)),
                        _Legend('Bajo', Color(0xFFD73027)),
                        _Legend('Medio', Color(0xFFFEE08B)),
                        _Legend('Alto', Color(0xFF1A9850)),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Text('${points.length} puntos NDVI'),
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

class _Legend extends StatelessWidget {
  const _Legend(this.text, this.color);

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 14,
          height: 14,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(3),
          ),
        ),
        const SizedBox(width: 5),
        Text(text),
      ],
    );
  }
}

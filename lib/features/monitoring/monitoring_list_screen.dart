import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/app_header.dart';
import '../../core/widgets/authenticated_remote_image.dart';
import '../../core/widgets/gpa_loading_indicator.dart';
import 'monitoring_models.dart';
import 'monitoring_repository.dart';

class MonitoringListScreen extends StatefulWidget {
  const MonitoringListScreen({super.key});

  @override
  State<MonitoringListScreen> createState() => _MonitoringListScreenState();
}

class _MonitoringListScreenState extends State<MonitoringListScreen> {
  final repo = MonitoringRepository();
  late Future<List<MonitoringHeader>> future;
  String? producerId;
  String? ranchId;
  String? plotId;
  String? cycle;
  String? status;
  DateTime? startDate;
  DateTime? endDate;
  bool filtersExpanded = true;
  DateTime lastRefresh = DateTime.now();

  @override
  void initState() {
    super.initState();
    future = _load();
  }

  Future<List<MonitoringHeader>> _load() {
    final app = AppScope.read(context);
    final user = app.user;
    final ciaId = flexibleId(app.selectedCia?['id'] ?? app.selectedCia?['ext_id']);
    final directOperational = user?.isTechnician == true || user?.isGuest == true;
    return repo.headers(
      assignedTo: null,
      dataCentralId: directOperational ? null : ciaId,
      viewerUserId: directOperational ? user?.id : null,
    );
  }

  Future<void> _refresh() async {
    setState(() {
      future = _load();
      lastRefresh = DateTime.now();
    });
    await future;
  }

  List<MonitoringHeader> _filtered(List<MonitoringHeader> all) {
    return all.where((item) {
      if (producerId != null && item.producerId != producerId) return false;
      if (ranchId != null && item.ranchId != ranchId) return false;
      if (plotId != null && item.plotId != plotId) return false;
      if (cycle != null && _cycleKey(item) != cycle) return false;
      if (status != null && _statusKey(item.status) != status) return false;
      final date = _parseDate(item.activityDate ?? item.startDate);
      if (startDate != null && date != null && date.isBefore(_startOfDay(startDate!))) {
        return false;
      }
      if (endDate != null && date != null && date.isAfter(_endOfDay(endDate!))) {
        return false;
      }
      return true;
    }).toList(growable: false);
  }

  void _clearBelowProducer(String? value) {
    setState(() {
      producerId = value;
      ranchId = null;
      plotId = null;
    });
  }

  void _clearBelowRanch(String? value) {
    setState(() {
      ranchId = value;
      plotId = null;
    });
  }

  void _clearAll() {
    setState(() {
      producerId = null;
      ranchId = null;
      plotId = null;
      cycle = null;
      status = null;
      startDate = null;
      endDate = null;
    });
  }

  Future<void> _pickStart() async {
    final value = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      initialDate: startDate ?? DateTime.now(),
    );
    if (value != null && mounted) setState(() => startDate = value);
  }

  Future<void> _pickEnd() async {
    final value = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      initialDate: endDate ?? startDate ?? DateTime.now(),
    );
    if (value != null && mounted) setState(() => endDate = value);
  }

  Future<void> _handleBack() async {
    final app = AppScope.read(context);
    final user = app.user;
    final hierarchical = user?.isAdmin == true ||
        user?.isManager == true ||
        user?.isSupervisor == true;

    if (hierarchical && app.selectedCia != null) {
      app.go(AppPage.ciaSelection);
      return;
    }

    // Técnico/invitado: esta lista es la raíz del módulo. Igual que el flujo
    // que pediste para Android, el botón Atrás del sistema NO cierra la app.
    // Se conserva la sesión y la pantalla actual.
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Estás en la pantalla principal de monitoreos.'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  String _ciaHeading(AppController app) {
    final raw = app.selectedCia;
    if (raw == null) return 'Monitoreos fitosanitarios';
    String text(dynamic v) => v == null ? '' : '$v'.trim();
    final name = text(
      raw['commercial_name'] ?? raw['name'] ?? raw['nombre'] ?? raw['display_name'],
    );
    return name.isNotEmpty ? name : 'CIA seleccionada';
  }

  @override
  Widget build(BuildContext context) {
    final app = AppScope.of(context);
    final progressiveFilters = app.user?.isAdmin == true ||
        app.user?.isManager == true ||
        app.user?.isSupervisor == true;

    final canReturnToCia = progressiveFilters && app.selectedCia != null;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _handleBack();
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFFBFCF8),
        body: Column(
          children: [
            AppHeader(
              title: 'Monitoreo fitosanitario',
              onBack: canReturnToCia ? _handleBack : null,
            ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: _refresh,
              child: FutureBuilder<List<MonitoringHeader>>(
                future: future,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const Center(child: GpaLoadingIndicator(text: 'Cargando monitoreos...'));
                  }
                  if (snapshot.hasError) {
                    return _Message(
                      icon: Icons.cloud_off,
                      title: 'No se pudieron cargar los monitoreos',
                      message: '${snapshot.error}',
                      onPressed: _refresh,
                    );
                  }

                  final all = snapshot.data ?? const <MonitoringHeader>[];
                  final visible = _filtered(all);
                  if (all.isEmpty) {
                    return _Message(
                      icon: Icons.assignment_outlined,
                      title: 'Sin monitoreos',
                      message: app.user?.isTechnician == true || app.user?.isGuest == true
                          ? 'No tienes monitoreos fitosanitarios asignados.'
                          : 'No hay monitoreos para la CIA seleccionada.',
                      onPressed: _refresh,
                    );
                  }

                  return ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(14, 16, 14, 32),
                    children: [
                      Text(
                        _ciaHeading(app),
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontSize: 23,
                          fontWeight: FontWeight.w900,
                          color: Color(0xFF111111),
                        ),
                      ),
                      const SizedBox(height: 16),
                      if (progressiveFilters)
                        _ProgressiveFilters(
                          expanded: filtersExpanded,
                          all: all,
                          producerId: producerId,
                          ranchId: ranchId,
                          plotId: plotId,
                          cycle: cycle,
                          status: status,
                          startDate: startDate,
                          endDate: endDate,
                          onToggle: () => setState(() => filtersExpanded = !filtersExpanded),
                          onProducer: _clearBelowProducer,
                          onRanch: _clearBelowRanch,
                          onPlot: (value) => setState(() => plotId = value),
                          onCycle: (value) => setState(() => cycle = value),
                          onStatus: (value) => setState(() => status = value),
                          onStartDate: _pickStart,
                          onEndDate: _pickEnd,
                          onClearStart: () => setState(() => startDate = null),
                          onClearEnd: () => setState(() => endDate = null),
                          onClear: _clearAll,
                        ),
                      if (progressiveFilters) const SizedBox(height: 18),
                      _ListCountHeader(
                        count: visible.length,
                        updated: lastRefresh,
                        onRefresh: _refresh,
                      ),
                      const SizedBox(height: 12),
                      if (visible.isEmpty)
                        const Card(
                          child: Padding(
                            padding: EdgeInsets.all(22),
                            child: Text(
                              'No hay monitoreos que coincidan con los filtros.',
                              textAlign: TextAlign.center,
                            ),
                          ),
                        ),
                      ...visible.map(
                        (item) => Padding(
                          padding: const EdgeInsets.only(bottom: 14),
                          child: _MonitoringCard(
                            header: item,
                            onTap: () {
                              final state = _statusKey(item.status);
                              if (state == 'cancelled') {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text(
                                      'Este monitoreo está cancelado. No se puede abrir ni consultar información.',
                                    ),
                                  ),
                                );
                                return;
                              }
                              app.selectMonitoring(item.raw);
                              if (state == 'completed') {
                                app.go(AppPage.monitoringReport);
                              }
                            },
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
}

class _ProgressiveFilters extends StatelessWidget {
  const _ProgressiveFilters({
    required this.expanded,
    required this.all,
    required this.producerId,
    required this.ranchId,
    required this.plotId,
    required this.cycle,
    required this.status,
    required this.startDate,
    required this.endDate,
    required this.onToggle,
    required this.onProducer,
    required this.onRanch,
    required this.onPlot,
    required this.onCycle,
    required this.onStatus,
    required this.onStartDate,
    required this.onEndDate,
    required this.onClearStart,
    required this.onClearEnd,
    required this.onClear,
  });

  final bool expanded;
  final List<MonitoringHeader> all;
  final String? producerId;
  final String? ranchId;
  final String? plotId;
  final String? cycle;
  final String? status;
  final DateTime? startDate;
  final DateTime? endDate;
  final VoidCallback onToggle;
  final ValueChanged<String?> onProducer;
  final ValueChanged<String?> onRanch;
  final ValueChanged<String?> onPlot;
  final ValueChanged<String?> onCycle;
  final ValueChanged<String?> onStatus;
  final VoidCallback onStartDate;
  final VoidCallback onEndDate;
  final VoidCallback onClearStart;
  final VoidCallback onClearEnd;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final producers = _options(
      all.where((e) => e.producerId != null),
      (e) => e.producerId!,
      (e) => e.producerName,
    );
    final ranchSource = producerId == null
        ? all
        : all.where((e) => e.producerId == producerId).toList();
    final ranches = _options(
      ranchSource.where((e) => e.ranchId != null),
      (e) => e.ranchId!,
      (e) => e.ranchName,
    );
    final plotSource = ranchId == null
        ? ranchSource
        : ranchSource.where((e) => e.ranchId == ranchId).toList();
    final plots = _options(
      plotSource.where((e) => e.plotId != null),
      (e) => e.plotId!,
      // En parcela se prioriza el código, igual que la app Kotlin.
      (e) => e.plotName,
    );
    final cycles = <String, String>{};
    for (final item in plotId == null
        ? plotSource
        : plotSource.where((e) => e.plotId == plotId)) {
      final key = _cycleKey(item);
      if (key.isNotEmpty) cycles.putIfAbsent(key, () => item.programName);
    }

    final activeCount = <Object?>[
      producerId,
      ranchId,
      plotId,
      cycle,
      status,
      startDate,
      endDate,
    ].where((value) => value != null).length;
    final filterSummary = activeCount == 0
        ? 'Todos los monitoreos'
        : '$activeCount filtro${activeCount == 1 ? '' : 's'} aplicado${activeCount == 1 ? '' : 's'}';

    return Card(
      margin: EdgeInsets.zero,
      elevation: 3,
      shadowColor: Colors.black12,
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 16),
        child: Column(
          children: [
            InkWell(
              borderRadius: BorderRadius.circular(14),
              onTap: onToggle,
              child: Row(
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: const BoxDecoration(
                      color: Color(0xFFEAF6E8),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.menu, color: AppTheme.primary),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Filtros',
                          style: TextStyle(
                            color: Color(0xFF16421E),
                            fontSize: 20,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          filterSummary,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.black54,
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                  ),
                  TextButton(
                    onPressed: onToggle,
                    child: Text(expanded ? 'Cerrar' : 'Abrir'),
                  ),
                  Icon(expanded ? Icons.expand_less : Icons.expand_more),
                ],
              ),
            ),
            if (expanded) ...[
              const SizedBox(height: 16),
              _FilterDropdown(
                label: 'Productor',
                value: producerId,
                options: producers,
                allLabel: 'Todos los productores',
                onChanged: onProducer,
              ),
              const SizedBox(height: 10),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: _FilterDropdown(
                      label: 'Rancho',
                      value: ranchId,
                      options: ranches,
                      allLabel: producerId == null
                          ? 'Selecciona productor'
                          : 'Todos',
                      enabled: producerId != null,
                      onChanged: onRanch,
                    ),
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: _FilterDropdown(
                      label: 'Parcela',
                      value: plotId,
                      options: plots,
                      allLabel: ranchId == null
                          ? 'Selecciona rancho'
                          : 'Todas',
                      enabled: ranchId != null,
                      onChanged: onPlot,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              // Igual que Kotlin: Ciclo, Inicio y Fin comparten la misma fila,
              // pero ahora usan Expanded para que nunca se recorten los títulos.
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: _FilterDropdown(
                      label: 'Ciclo',
                      value: cycle,
                      options: cycles,
                      allLabel: 'Todos',
                      onChanged: onCycle,
                      leading: Icons.refresh,
                      compact: true,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _DateFilter(
                      label: 'Inicio',
                      value: startDate,
                      onTap: onStartDate,
                      onClear: onClearStart,
                      compact: true,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _DateFilter(
                      label: 'Fin',
                      value: endDate,
                      onTap: onEndDate,
                      onClear: onClearEnd,
                      compact: true,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: _FilterDropdown(
                      label: 'Estado',
                      value: status,
                      options: const {
                        'pending': 'Pendiente',
                        'in_progress': 'En proceso',
                        'completed': 'Completado',
                        'cancelled': 'Cancelado',
                      },
                      allLabel: 'Todos',
                      onChanged: onStatus,
                      leading: Icons.check,
                      compact: true,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: SizedBox(
                      height: 54,
                      child: OutlinedButton.icon(
                        onPressed: activeCount > 0 ? onClear : null,
                        icon: const Icon(Icons.filter_alt_off, size: 18),
                        label: const Text(
                          'Limpiar filtros',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppTheme.primary,
                          disabledForegroundColor: Colors.black26,
                          side: BorderSide(
                            color: activeCount > 0
                                ? const Color(0xFF777777)
                                : const Color(0xFFD6D6D6),
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                          textStyle: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Map<String, String> _options(
    Iterable<MonitoringHeader> source,
    String Function(MonitoringHeader) id,
    String Function(MonitoringHeader) label,
  ) {
    final map = <String, String>{};
    for (final item in source) {
      map.putIfAbsent(id(item), () => label(item));
    }
    return map;
  }
}

class _FilterDropdown extends StatelessWidget {
  const _FilterDropdown({
    required this.label,
    required this.value,
    required this.options,
    required this.allLabel,
    required this.onChanged,
    this.enabled = true,
    this.leading,
    this.compact = false,
  });

  final String label;
  final String? value;
  final Map<String, String> options;
  final String allLabel;
  final ValueChanged<String?> onChanged;
  final bool enabled;
  final IconData? leading;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final validValue = value != null && options.containsKey(value) ? value : null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 5),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: const Color(0xFF4A4A4A),
              fontSize: compact ? 12 : 13,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        DropdownButtonFormField<String>(
          initialValue: validValue,
          isExpanded: true,
          onChanged: enabled ? onChanged : null,
          icon: Icon(Icons.keyboard_arrow_down, size: compact ? 21 : 24),
          decoration: InputDecoration(
            filled: true,
            fillColor: enabled ? Colors.white : const Color(0xFFF4F4F4),
            prefixIcon: leading == null
                ? null
                : Icon(leading, size: compact ? 17 : 19),
            prefixIconConstraints: const BoxConstraints(
              minWidth: 36,
              minHeight: 36,
            ),
            contentPadding: EdgeInsets.symmetric(
              horizontal: compact ? 9 : 12,
              vertical: compact ? 13 : 15,
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: Color(0xFF777777)),
            ),
          ),
          items: [
            DropdownMenuItem<String>(
              value: null,
              child: Text(
                allLabel,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: compact ? 13 : 14,
                  fontWeight: FontWeight.w700,
                  color: enabled ? Colors.black87 : Colors.black38,
                ),
              ),
            ),
            ...options.entries.map(
              (entry) => DropdownMenuItem<String>(
                value: entry.key,
                child: Text(
                  entry.value,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: compact ? 13 : 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _DateFilter extends StatelessWidget {
  const _DateFilter({
    required this.label,
    required this.value,
    required this.onTap,
    required this.onClear,
    this.compact = false,
  });

  final String label;
  final DateTime? value;
  final VoidCallback onTap;
  final VoidCallback onClear;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 5),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: const Color(0xFF4A4A4A),
              fontSize: compact ? 12 : 13,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: onTap,
          child: InputDecorator(
            decoration: InputDecoration(
              prefixIcon: Icon(
                Icons.calendar_month_outlined,
                size: compact ? 18 : 20,
              ),
              prefixIconConstraints: const BoxConstraints(
                minWidth: 36,
                minHeight: 36,
              ),
              suffixIcon: value == null
                  ? Icon(Icons.keyboard_arrow_down, size: compact ? 18 : 20)
                  : IconButton(
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(minWidth: 32),
                      onPressed: onClear,
                      icon: Icon(Icons.close, size: compact ? 17 : 19),
                    ),
              suffixIconConstraints: const BoxConstraints(
                minWidth: 32,
                minHeight: 36,
              ),
              filled: true,
              fillColor: Colors.white,
              contentPadding: EdgeInsets.symmetric(
                horizontal: compact ? 7 : 10,
                vertical: compact ? 14 : 15,
              ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: const BorderSide(color: Color(0xFF777777)),
              ),
            ),
            child: Text(
              value == null ? 'Fecha' : _shortDate(value!),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: compact ? 13 : 14,
                fontWeight: FontWeight.w700,
                color: value == null ? Colors.black45 : Colors.black87,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ListCountHeader extends StatelessWidget {
  const _ListCountHeader({
    required this.count,
    required this.updated,
    required this.onRefresh,
  });

  final int count;
  final DateTime updated;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Monitoreos\ndisponibles: $count',
                  style: const TextStyle(
                    color: AppTheme.darkGreen,
                    fontSize: 20,
                    height: 1.25,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  'Actualizado: ${_shortDate(updated)}, ${updated.hour.toString().padLeft(2, '0')}:${updated.minute.toString().padLeft(2, '0')}',
                  style: const TextStyle(color: Colors.black45, fontSize: 12),
                ),
              ],
            ),
          ),
          FilledButton.tonalIcon(
            onPressed: onRefresh,
            icon: const Icon(Icons.refresh, size: 18),
            label: const Text('Actualizar'),
          ),
        ],
      );
}

class _MonitoringCard extends StatelessWidget {
  const _MonitoringCard({required this.header, required this.onTap});

  final MonitoringHeader header;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final state = _statusKey(header.status);
    final color = switch (state) {
      'completed' => const Color(0xFF2E7D32),
      'in_progress' => const Color(0xFFE18A00),
      'cancelled' => const Color(0xFFC62828),
      _ => const Color(0xFF607D8B),
    };
    final date = _parseDate(header.activityDate);

    return Card(
      margin: EdgeInsets.zero,
      elevation: 2,
      shadowColor: Colors.black12,
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: const BorderSide(color: Color(0xFFE3E9DF)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 90,
                  height: 90,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FBF4),
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: const Color(0xFFDCEAD5)),
                  ),
                  alignment: Alignment.center,
                  clipBehavior: Clip.antiAlias,
                  child: AuthenticatedRemoteImage(
                    url: header.cropPhoto,
                    width: 90,
                    height: 90,
                    fit: BoxFit.cover,
                    fallback: Center(
                      child: Text(
                        _cropEmoji(header.cropName),
                        style: const TextStyle(fontSize: 46),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 5,
                            ),
                            decoration: BoxDecoration(
                              color: color.withValues(alpha: .11),
                              borderRadius: BorderRadius.circular(99),
                            ),
                            child: Text(
                              header.isPaused ? 'Pausado' : _statusLabel(state),
                              style: TextStyle(
                                color: color,
                                fontSize: 11,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                          ),
                          const Spacer(),
                          if (date != null)
                            Text(
                              '${date.day} ${_monthShort(date.month)} ${date.year}',
                              style: const TextStyle(
                                color: Colors.black54,
                                fontSize: 11,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 9),
                      Text(
                        header.plotName,
                        style: const TextStyle(
                          color: AppTheme.darkGreen,
                          fontSize: 19,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 7),
                      Row(
                        children: [
                          Expanded(child: _TinyField('Ciclo', header.programName)),
                          const SizedBox(width: 8),
                          Expanded(child: _TinyField('Cultivo', header.cropName)),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '⌂ ${header.ranchName}   ·   ${header.producerName}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Colors.black54, fontSize: 12),
                      ),
                      if (state == 'cancelled' &&
                          header.cancellationReason != null) ...[
                        const SizedBox(height: 5),
                        Text(
                          'Motivo: ${header.cancellationReason}',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Color(0xFFC62828),
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: OutlinedButton(
                onPressed: state == 'cancelled' ? null : onTap,
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: Color(0xFF707070)),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: Text(
                  state == 'cancelled'
                      ? 'Monitoreo cancelado'
                      : state == 'completed'
                          ? 'Ver reporte'
                          : 'Abrir monitoreo',
                  style: TextStyle(
                    color: state == 'cancelled'
                        ? const Color(0xFFC62828)
                        : AppTheme.primary,
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TinyField extends StatelessWidget {
  const _TinyField(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: Colors.black45,
              fontSize: 10,
              letterSpacing: .3,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.black87,
              fontSize: 11,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      );
}

class _Message extends StatelessWidget {
  const _Message({
    required this.icon,
    required this.title,
    required this.message,
    required this.onPressed,
  });

  final IconData icon;
  final String title;
  final String message;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(30),
        children: [
          const SizedBox(height: 90),
          Icon(icon, size: 70, color: Colors.black26),
          const SizedBox(height: 16),
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 8),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 18),
          Center(
            child: OutlinedButton.icon(
              onPressed: onPressed,
              icon: const Icon(Icons.refresh),
              label: const Text('Actualizar'),
            ),
          ),
        ],
      );
}

String _statusKey(String raw) {
  final value = raw.toLowerCase().trim().replaceAll(' ', '_');
  if (value.contains('complet') || value.contains('finaliz')) return 'completed';
  if (value.contains('progress') || value.contains('progreso')) return 'in_progress';
  if (value.contains('cancel')) return 'cancelled';
  return 'pending';
}

String _statusLabel(String key) => switch (key) {
      'completed' => 'Completado',
      'in_progress' => 'En progreso',
      'cancelled' => 'Cancelado',
      _ => 'Pendiente',
    };

String _cycleKey(MonitoringHeader header) => header.programName.trim().toLowerCase();

DateTime? _parseDate(String? raw) {
  if (raw == null || raw.trim().isEmpty) return null;
  return DateTime.tryParse(raw)?.toLocal();
}

DateTime _startOfDay(DateTime d) => DateTime(d.year, d.month, d.day);
DateTime _endOfDay(DateTime d) => DateTime(d.year, d.month, d.day, 23, 59, 59, 999);

String _shortDate(DateTime d) =>
    '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';

String _monthShort(int month) => const [
      '', 'ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'
    ][month];

String _cropEmoji(String crop) {
  final value = crop.toLowerCase();
  if (value.contains('maíz') || value.contains('maiz')) return '🌽';
  if (value.contains('trigo')) return '🌾';
  if (value.contains('fresa')) return '🍓';
  if (value.contains('uva')) return '🍇';
  if (value.contains('tomate')) return '🍅';
  if (value.contains('limón') || value.contains('limon')) return '🍋';
  return '🌱';
}


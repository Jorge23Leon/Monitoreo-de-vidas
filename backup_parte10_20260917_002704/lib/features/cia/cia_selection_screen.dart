import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/storage/app_database.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/app_header.dart';
import '../../core/widgets/state_views.dart';
import '../common/repositories.dart';

class CiaSelectionScreen extends StatefulWidget {
  const CiaSelectionScreen({super.key});

  @override
  State<CiaSelectionScreen> createState() => _CiaSelectionScreenState();
}

class _CiaSelectionScreenState extends State<CiaSelectionScreen> {
  final repo = CiagroRepository();
  late Future<List<Map<String, dynamic>>> future;
  Map<String, dynamic>? parent;
  Map<String, dynamic>? cia;
  bool preferred = false;
  String? preferredId;

  @override
  void initState() {
    super.initState();
    future = _load();
    _loadPreferred();
  }

  Future<void> _loadPreferred() async {
    final id = await AppDatabase.instance.getMeta('preferred_cia_id');
    if (!mounted) return;
    setState(() => preferredId = id);
  }

  Future<List<Map<String, dynamic>>> _load() async {
    final parents = await repo.parentCias();
    final cias = await repo.cias();
    return [
      ...parents.map((e) => <String, dynamic>{...e, '_kind': 'parent'}),
      ...cias.map((e) => <String, dynamic>{...e, '_kind': 'cia'}),
    ];
  }

  String _id(Map<String, dynamic> item) =>
      '${item['id'] ?? item['ext_id'] ?? ''}';

  String label(Map<String, dynamic> item) =>
      '${item['commercial_name'] ?? item['name'] ?? item['nombre'] ?? item['display_name'] ?? 'CIA sin nombre'}';

  String? _childParentId(Map<String, dynamic> item) {
    final nested = item['data_central_main'] ??
        item['dataCentralMain'] ??
        item['datacentralmain'] ??
        item['main'] ??
        item['parent'];
    if (nested is Map && nested['id'] != null) return '${nested['id']}';
    final value = item['data_central_main_id'] ??
        item['dataCentralMainId'] ??
        item['data_central_main'];
    return value == null ? null : '$value';
  }

  Future<void> _continue(AppController app) async {
    final selected = cia;
    if (selected == null) return;
    await AppDatabase.instance.setMeta(
      'preferred_cia_id',
      preferred ? _id(selected) : null,
    );
    if (!mounted) return;
    app.selectCia(selected);
  }

  @override
  Widget build(BuildContext context) {
    final app = AppScope.of(context);
    final supervisor = app.user?.isSupervisor == true;

    return Scaffold(
      backgroundColor: Colors.white,
      body: Column(
        children: [
          const AppHeader(title: 'Selección de CIA'),
          Expanded(
            child: FutureBuilder<List<Map<String, dynamic>>>(
              future: future,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const LoadingView(text: 'Cargando organizaciones...');
                }
                if (snapshot.hasError) {
                  return ErrorView(
                    message: '${snapshot.error}',
                    onRetry: () => setState(() => future = _load()),
                  );
                }

                final all = snapshot.data ?? const <Map<String, dynamic>>[];
                final parents =
                    all.where((e) => e['_kind'] == 'parent').toList();
                var cias = all.where((e) => e['_kind'] == 'cia').toList();

                final assignedIds = app.user?.dataCentrals
                        .map((item) => '${item['id'] ?? ''}')
                        .where((id) => id.isNotEmpty)
                        .toSet() ??
                    const <String>{};
                if (assignedIds.isNotEmpty && app.user?.isAdmin != true) {
                  cias = cias
                      .where((item) => assignedIds.contains(_id(item)))
                      .toList();
                }

                final visibleCias = supervisor || parent == null
                    ? cias
                    : cias
                        .where((item) => _childParentId(item) == _id(parent!))
                        .toList();

                final canContinue = cia != null && (supervisor || parent != null);

                return ListView(
                  padding: const EdgeInsets.fromLTRB(14, 18, 14, 28),
                  children: [
                    Text(
                      supervisor
                          ? 'Seleccionar CIA hija'
                          : 'Seleccionar organización',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 25,
                        height: 1.12,
                        fontWeight: FontWeight.w900,
                        color: Color(0xFF1F331F),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: Text(
                        supervisor
                            ? 'Selecciona una de las CIAS hijas disponibles para consultar sus monitoreos.'
                            : 'Primero elige la CIA padre y después selecciona una CIA hija.',
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontSize: 14,
                          height: 1.45,
                          color: Color(0xFF666666),
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    if (!supervisor) ...[
                      _SelectionCard(
                        step: '1.',
                        title: 'Selecciona tu organización (CIA padre)',
                        child: _CiaDropdown(
                          value: parent,
                          items: parents,
                          hint: 'Selecciona una CIA padre',
                          asset: 'assets/images/img_cia_padre.png',
                          labelBuilder: label,
                          onChanged: (value) => setState(() {
                            parent = value;
                            cia = null;
                            preferred = false;
                          }),
                        ),
                      ),
                      const SizedBox(height: 14),
                    ],
                    _SelectionCard(
                      step: supervisor ? '1.' : '2.',
                      title: 'Selecciona una CIA hija',
                      child: (!supervisor && parent == null)
                          ? const _DisabledHint(
                              text:
                                  'Selecciona una CIA padre para mostrar sus CIAS hijas.',
                            )
                          : _CiaDropdown(
                              value: visibleCias.contains(cia) ? cia : null,
                              items: visibleCias,
                              hint: supervisor
                                  ? 'Selecciona una CIA hija asignada'
                                  : 'Selecciona una CIA hija',
                              asset: 'assets/images/img_cia_hija.png',
                              labelBuilder: label,
                              onChanged: (value) => setState(() {
                                cia = value;
                                preferred = value != null &&
                                    _id(value) == preferredId;
                              }),
                            ),
                    ),
                    const SizedBox(height: 14),
                    _StatusCard(
                      text: switch ((supervisor, parent, cia)) {
                        (true, _, null) =>
                          'Selecciona una CIA hija asignada para continuar.',
                        (false, null, _) =>
                          'Primero selecciona una CIA padre.',
                        (false, _, null) =>
                          'Selecciona una CIA hija del árbol para continuar.',
                        (_, _, final selected?) =>
                          'CIA hija seleccionada: ${label(selected)}',
                      },
                    ),
                    const SizedBox(height: 14),
                    _PreferredCard(
                      enabled: cia != null,
                      value: preferred,
                      selectedLabel: cia == null ? null : label(cia!),
                      onChanged: (value) => setState(() => preferred = value),
                    ),
                    const SizedBox(height: 22),
                    SizedBox(
                      height: 58,
                      child: FilledButton(
                        onPressed: canContinue ? () => _continue(app) : null,
                        style: FilledButton.styleFrom(
                          backgroundColor: AppTheme.primary,
                          disabledBackgroundColor: const Color(0xFFE1E1E1),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(30),
                          ),
                        ),
                        child: const Text(
                          'Continuar',
                          style: TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
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

class _SelectionCard extends StatelessWidget {
  const _SelectionCard({
    required this.step,
    required this.title,
    required this.child,
  });

  final String step;
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 2,
      shadowColor: Colors.black12,
      color: const Color(0xFFFBFDF8),
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(19),
        side: const BorderSide(color: Color(0xFFE0E6DB)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 16, 14, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '$step $title',
              style: const TextStyle(
                color: Color(0xFF1F3B23),
                fontSize: 16,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 14),
            child,
          ],
        ),
      ),
    );
  }
}

class _CiaDropdown extends StatelessWidget {
  const _CiaDropdown({
    required this.value,
    required this.items,
    required this.hint,
    required this.asset,
    required this.labelBuilder,
    required this.onChanged,
  });

  final Map<String, dynamic>? value;
  final List<Map<String, dynamic>> items;
  final String hint;
  final String asset;
  final String Function(Map<String, dynamic>) labelBuilder;
  final ValueChanged<Map<String, dynamic>?> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<Map<String, dynamic>>(
      initialValue: value,
      isExpanded: true,
      icon: const Icon(Icons.keyboard_arrow_down, color: Colors.black87),
      decoration: InputDecoration(
        filled: true,
        fillColor: Colors.white,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        prefixIcon: Padding(
          padding: const EdgeInsets.all(12),
          child: Image.asset(asset, width: 28, height: 28),
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFF707070), width: 1.2),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFF707070), width: 1.2),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppTheme.primary, width: 1.8),
        ),
      ),
      hint: Text(
        hint,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontWeight: FontWeight.w700),
      ),
      items: items
          .map(
            (item) => DropdownMenuItem<Map<String, dynamic>>(
              value: item,
              child: Text(
                labelBuilder(item),
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
          )
          .toList(growable: false),
      onChanged: onChanged,
    );
  }
}

class _DisabledHint extends StatelessWidget {
  const _DisabledHint({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 19),
        decoration: BoxDecoration(
          color: const Color(0xFFF5F8F1),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: const TextStyle(color: Color(0xFF777777), height: 1.4),
        ),
      );
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) => Card(
        margin: EdgeInsets.zero,
        color: const Color(0xFFEFF8E8),
        surfaceTintColor: Colors.transparent,
        elevation: 1,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(17),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 15),
          child: Row(
            children: [
              const Icon(
                Icons.info_outline,
                color: Color(0xFF4A8F2A),
                size: 23,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  text,
                  style: const TextStyle(
                    color: Color(0xFF3F5F35),
                    fontSize: 14,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
}

class _PreferredCard extends StatelessWidget {
  const _PreferredCard({
    required this.enabled,
    required this.value,
    required this.selectedLabel,
    required this.onChanged,
  });

  final bool enabled;
  final bool value;
  final String? selectedLabel;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => InkWell(
        borderRadius: BorderRadius.circular(17),
        onTap: enabled ? () => onChanged(!value) : null,
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: enabled ? const Color(0xFFFAFAFA) : const Color(0xFFF2F2F2),
            borderRadius: BorderRadius.circular(17),
          ),
          child: Row(
            children: [
              Image.asset(
                'assets/images/img_cia_preferente.png',
                width: 42,
                height: 42,
              ),
              const SizedBox(width: 10),
              Checkbox(
                value: value,
                onChanged: enabled
                    ? (v) => onChanged(v ?? false)
                    : null,
              ),
              const SizedBox(width: 4),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Seleccionar CIA hija preferente',
                      style: TextStyle(
                        color: enabled ? Colors.black87 : Colors.black38,
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      !enabled
                          ? 'Primero selecciona una CIA hija.'
                          : value
                              ? 'Se recordará: $selectedLabel'
                              : 'No se guardará como preferente.',
                      style: TextStyle(
                        color: enabled ? Colors.black54 : Colors.black38,
                        fontSize: 12,
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

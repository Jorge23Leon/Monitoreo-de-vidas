import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/app_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/app_header.dart';
import '../../core/widgets/authenticated_remote_image.dart';
import 'monitoring_models.dart';
import 'monitoring_repository.dart';
import 'monitoring_services.dart';

class MonitoringCheckpointScreen extends StatefulWidget {
  const MonitoringCheckpointScreen({super.key});

  @override
  State<MonitoringCheckpointScreen> createState() =>
      _MonitoringCheckpointScreenState();
}

enum _CatalogTab { pests, diseases }

class _MonitoringCheckpointScreenState
    extends State<MonitoringCheckpointScreen> {
  final repo = MonitoringRepository();
  final notesController = TextEditingController();
  final severityMajorController = TextEditingController(text: '5');

  late MonitoringHeader header;
  late TargetPoint target;
  late Future<List<PhytoCatalogItem>> catalogFuture;

  _CatalogTab tab = _CatalogTab.pests;
  PhytoCatalogItem? selected;
  List<PhytoCatalogItem> catalog = const [];
  final Map<String, int> quantities = {};
  final Set<String> generalPests = {};
  final Map<String, DiseaseEvaluation> diseases = {};
  final Set<String> previouslyRegistered = {};

  XFile? photo;
  bool saving = false;

  @override
  void initState() {
    super.initState();
    final app = AppScope.read(context);
    header = MonitoringHeader.fromJson(
      app.selectedMonitoring ?? const <String, dynamic>{},
    );
    target = TargetPoint.fromJson(
      app.selectedTargetPoint ?? const <String, dynamic>{},
    );
    catalogFuture = _load();
    _loadExisting();
  }

  @override
  void dispose() {
    notesController.dispose();
    severityMajorController.dispose();
    super.dispose();
  }

  Future<List<PhytoCatalogItem>> _load() async {
    final items = await repo.catalogForCrop(header.cropId);
    if (mounted) {
      setState(() {
        catalog = items;
        if (items.every((e) => !e.isPest) && items.any((e) => e.isDisease)) {
          tab = _CatalogTab.diseases;
        }
      });
    }
    return items;
  }

  Future<void> _loadExisting() async {
    final existing = await repo.targetLocalCheckpoints(target.id);
    if (!mounted) return;
    setState(() {
      previouslyRegistered.addAll(
        existing
            .map((e) => e.phytoIssueId)
            .whereType<String>()
            .where((e) => e.isNotEmpty),
      );
    });
  }

  List<PhytoCatalogItem> get visibleCatalog {
    final items = catalog.where((item) {
      return tab == _CatalogTab.pests ? item.isPest : item.isDisease;
    }).toList();
    items.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    return items;
  }

  int _quantityFor(PhytoCatalogItem item, PhytoStage stage) =>
      quantities['${item.id}|${stage.name}'] ?? 0;

  void _setQuantity(PhytoCatalogItem item, PhytoStage stage, int value) {
    setState(() {
      quantities['${item.id}|${stage.name}'] = value.clamp(0, 9999).toInt();
    });
  }

  int get totalPestQuantity => quantities.values.fold(0, (a, b) => a + b) +
      generalPests.length;

  int get pendingRecords =>
      quantities.values.where((value) => value > 0).length +
      generalPests.length +
      diseases.length;

  Future<void> _takePhoto() async {
    final result = await MonitoringPhotoService.takePhoto();
    if (result != null && mounted) setState(() => photo = result);
  }

  Future<void> _choosePhoto() async {
    final result = await MonitoringPhotoService.choosePhoto();
    if (result != null && mounted) setState(() => photo = result);
  }

  Future<void> _saveRecords() async {
    if (saving) return;
    if (pendingRecords == 0) {
      _snack('Selecciona una plaga o evalúa una enfermedad antes de guardar.');
      return;
    }

    for (final entry in diseases.entries) {
      if (entry.value.presence == DiseasePresence.present &&
          (entry.value.stage == null || entry.value.stage!.trim().isEmpty)) {
        final item = catalog.where((e) => e.id == entry.key).firstOrNull;
        _snack(
          'Selecciona Inicio, Desarrollo o Avanzado para ${item?.name ?? 'la enfermedad'}.',
        );
        return;
      }
    }

    final major = int.tryParse(severityMajorController.text.trim());
    if (totalPestQuantity > 0 && (major == null || major <= 0)) {
      _snack('La severidad mayor debe ser un número mayor a 0.');
      return;
    }

    final confirmed = await _confirm(
      'Guardar punto',
      'Se guardarán $pendingRecords registro(s) para ${target.label}. ¿Continuar?',
    );
    if (!confirmed) return;

    setState(() => saving = true);
    try {
      final photoPath = await MonitoringPhotoService.persistPhoto(
        photo,
        headerId: header.id,
        targetId: target.id,
      );
      final now = DateTime.now();
      // La severidad se usa únicamente para presentar el índice del punto.
      // Nunca se mezcla información técnica como [SEV_PUNTO:...] dentro del
      // comentario que verá el usuario o que se enviará al backend.
      final rawNotes = notesController.text.trim();
      final notes = rawNotes.isEmpty ? null : rawNotes;

      final records = <PendingCheckpoint>[];
      var index = 0;
      String localId() =>
          '${now.microsecondsSinceEpoch}_${target.id}_${index++}';

      for (final item in catalog.where((e) => e.isPest)) {
        final stages = item.orderedStages;
        if (stages.isEmpty && generalPests.contains(item.id)) {
          records.add(
            PendingCheckpoint(
              localId: localId(),
              headerId: header.id,
              targetId: target.id,
              phytoIssueId: item.id,
              phytoName: item.name,
              phytoType: item.type,
              stage: null,
              presenceStatus: 'warning',
              qty: 1,
              latitude: target.latitude,
              longitude: target.longitude,
              notes: notes,
              photoPath: photoPath,
              capturedAt: now,
              syncState: 'pending',
            ),
          );
        }
        for (final stage in stages) {
          final qty = _quantityFor(item, stage);
          if (qty <= 0) continue;
          records.add(
            PendingCheckpoint(
              localId: localId(),
              headerId: header.id,
              targetId: target.id,
              phytoIssueId: item.id,
              phytoName: item.name,
              phytoType: item.type,
              stage: stage.name,
              presenceStatus: qty >= 10 ? 'critical' : 'warning',
              qty: qty,
              latitude: target.latitude,
              longitude: target.longitude,
              notes: notes,
              photoPath: photoPath,
              capturedAt: now,
              syncState: 'pending',
            ),
          );
        }
      }

      for (final entry in diseases.entries) {
        final item = catalog.where((e) => e.id == entry.key).firstOrNull;
        if (item == null) continue;
        final evaluation = entry.value;
        final present = evaluation.presence == DiseasePresence.present;
        records.add(
          PendingCheckpoint(
            localId: localId(),
            headerId: header.id,
            targetId: target.id,
            phytoIssueId: item.id,
            phytoName: item.name,
            phytoType: item.type,
            stage: present ? evaluation.stage : null,
            presenceStatus: present ? 'warning' : 'low',
            qty: present ? 1 : 0,
            latitude: target.latitude,
            longitude: target.longitude,
            notes: notes,
            photoPath: photoPath,
            capturedAt: now,
            syncState: 'pending',
          ),
        );
      }

      if (records.isEmpty) {
        throw Exception('No hay capturas con información para guardar.');
      }

      await repo.saveLocalPoint(
        headerId: header.id,
        target: target,
        checkpoints: records,
      );

      if (!mounted) return;
      _snack('Punto guardado localmente. Usa Sincronizar cuando tengas conexión.');
      AppScope.read(context).go(AppPage.monitoringMap);
    } catch (error) {
      if (mounted) _snack(error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> _saveNoPest() async {
    if (saving) return;
    final confirmed = await _confirm(
      'Registrar sin plaga',
      '¿Confirmas que ${target.label} fue revisado y no presenta plagas ni enfermedades?',
    );
    if (!confirmed) return;

    setState(() => saving = true);
    try {
      final photoPath = await MonitoringPhotoService.persistPhoto(
        photo,
        headerId: header.id,
        targetId: target.id,
      );
      final now = DateTime.now();
      final record = PendingCheckpoint(
        localId: '${now.microsecondsSinceEpoch}_${target.id}_sin_plaga',
        headerId: header.id,
        targetId: target.id,
        phytoIssueId: null,
        phytoName: 'Sin plaga',
        phytoType: 'SIN_PLAGA',
        stage: null,
        presenceStatus: 'low',
        qty: 0,
        latitude: target.latitude,
        longitude: target.longitude,
        notes: notesController.text.trim().isEmpty
            ? null
            : notesController.text.trim(),
        photoPath: photoPath,
        capturedAt: now,
        syncState: 'pending',
      );
      await repo.saveLocalPoint(
        headerId: header.id,
        target: target,
        checkpoints: [record],
      );
      if (!mounted) return;
      _snack('Punto registrado sin plaga y guardado localmente.');
      AppScope.read(context).go(AppPage.monitoringMap);
    } catch (error) {
      if (mounted) _snack(error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => saving = false);
    }
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

  void _snack(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _showBackWarning() async {
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Guardar punto'),
        content: const Text(
          'Debes guardar el punto antes de regresar. Puedes guardarlo como Sin plaga o capturar una plaga/enfermedad y presionar Guardar registro.',
        ),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Entendido'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _showBackWarning();
      },
      child: Scaffold(
        bottomNavigationBar: _BottomActions(
          saving: saving,
          pendingRecords: pendingRecords,
          onNoPest: _saveNoPest,
          onSave: _saveRecords,
        ),
        body: Column(
          children: [
            AppHeader(
              title: 'Registro · ${target.label}',
              onBack: _showBackWarning,
            ),
            Expanded(
              child: FutureBuilder<List<PhytoCatalogItem>>(
                future: catalogFuture,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done &&
                      catalog.isEmpty) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  if (snapshot.hasError && catalog.isEmpty) {
                    return Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Text(
                          'No se pudo cargar el catálogo fitosanitario.\n${snapshot.error}',
                          textAlign: TextAlign.center,
                        ),
                      ),
                    );
                  }

                  return ListView(
                    padding: const EdgeInsets.fromLTRB(14, 14, 14, 28),
                    children: [
                      _PointSummary(
                        header: header,
                        target: target,
                      ),
                      const SizedBox(height: 14),
                      _CatalogTabs(
                        value: tab,
                        pestCount: catalog.where((e) => e.isPest).length,
                        diseaseCount: catalog.where((e) => e.isDisease).length,
                        onChanged: (value) => setState(() {
                          tab = value;
                          selected = null;
                        }),
                      ),
                      const SizedBox(height: 12),
                      _CatalogStrip(
                        items: visibleCatalog,
                        selected: selected,
                        registered: previouslyRegistered,
                        dirtyIds: _dirtyIds(),
                        onSelected: (value) =>
                            setState(() => selected = value),
                      ),
                      const SizedBox(height: 14),
                      if (selected != null)
                        selected!.isDisease
                            ? _DiseaseEditor(
                                item: selected!,
                                evaluation: diseases[selected!.id],
                                onChanged: (value) => setState(() {
                                  diseases[selected!.id] = value;
                                }),
                              )
                            : _PestEditor(
                                item: selected!,
                                quantities: quantities,
                                generalSelected:
                                    generalPests.contains(selected!.id),
                                onGeneralChanged: (value) => setState(() {
                                  if (value) {
                                    generalPests.add(selected!.id);
                                  } else {
                                    generalPests.remove(selected!.id);
                                  }
                                }),
                                onQuantity: _setQuantity,
                              ),
                      if (selected == null)
                        const _SelectHint(),
                      const SizedBox(height: 14),
                      if (totalPestQuantity > 0)
                        _SeverityCard(
                          controller: severityMajorController,
                          total: totalPestQuantity,
                        ),
                      if (totalPestQuantity > 0) const SizedBox(height: 14),
                      _PhotoCard(
                        photo: photo,
                        onCamera: _takePhoto,
                        onGallery: _choosePhoto,
                        onRemove: () => setState(() => photo = null),
                      ),
                      const SizedBox(height: 14),
                      Card(
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(14),
                          child: TextField(
                            controller: notesController,
                            minLines: 3,
                            maxLines: 6,
                            decoration: const InputDecoration(
                              labelText: 'Observaciones',
                              hintText: 'Agrega notas del punto si son necesarias...',
                              prefixIcon: Icon(Icons.notes),
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
      ),
    );
  }

  Set<String> _dirtyIds() {
    final result = <String>{...diseases.keys, ...generalPests};
    for (final entry in quantities.entries) {
      if (entry.value > 0) result.add(entry.key.split('|').first);
    }
    return result;
  }
}

class _PointSummary extends StatelessWidget {
  const _PointSummary({
    required this.header,
    required this.target,
  });

  final MonitoringHeader header;
  final TargetPoint target;

  @override
  Widget build(BuildContext context) {
    final number = target.visibleNumber > 0 ? target.visibleNumber : 1;
    return Card(
      margin: EdgeInsets.zero,
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      elevation: 3,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Container(
              width: 62,
              height: 62,
              decoration: const BoxDecoration(
                color: Color(0xFFEAF0FF),
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: const Icon(
                Icons.my_location,
                color: Color(0xFF174EA6),
                size: 31,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Punto ${number.toString().padLeft(2, '0')}',
                    style: const TextStyle(
                      color: Color(0xFF1D2430),
                      fontSize: 23,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '▧  Parcela: ${header.plotName}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Color(0xFF4F5663), fontSize: 13),
                  ),
                  Text(
                    '⌁  Cultivo: ${header.cropName}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Color(0xFF4F5663), fontSize: 13),
                  ),
                ],
              ),
            ),
            Container(
              width: 78,
              height: 64,
              decoration: BoxDecoration(
                color: const Color(0xFFF5FAF0),
                borderRadius: BorderRadius.circular(16),
              ),
              alignment: Alignment.center,
              child: Text(
                _checkpointCropEmoji(header.cropName),
                style: const TextStyle(fontSize: 40),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CatalogTabs extends StatelessWidget {
  const _CatalogTabs({
    required this.value,
    required this.pestCount,
    required this.diseaseCount,
    required this.onChanged,
  });

  final _CatalogTab value;
  final int pestCount;
  final int diseaseCount;
  final ValueChanged<_CatalogTab> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(5),
      decoration: BoxDecoration(
        color: const Color(0xFFF0F4EE),
        borderRadius: BorderRadius.circular(17),
      ),
      child: Row(
        children: [
          Expanded(
            child: _CatalogTabButton(
              selected: value == _CatalogTab.pests,
              icon: Icons.pest_control,
              label: 'Plagas ($pestCount)',
              onTap: () => onChanged(_CatalogTab.pests),
            ),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: _CatalogTabButton(
              selected: value == _CatalogTab.diseases,
              icon: Icons.coronavirus_outlined,
              label: 'Enfermedades ($diseaseCount)',
              onTap: () => onChanged(_CatalogTab.diseases),
            ),
          ),
        ],
      ),
    );
  }
}

class _CatalogTabButton extends StatelessWidget {
  const _CatalogTabButton({required this.selected, required this.icon, required this.label, required this.onTap});
  final bool selected;
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: selected ? const Color(0xFF176E35) : Colors.transparent,
        borderRadius: BorderRadius.circular(13),
        child: InkWell(
          borderRadius: BorderRadius.circular(13),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, size: 19, color: selected ? Colors.white : AppTheme.darkGreen),
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: selected ? Colors.white : AppTheme.darkGreen,
                      fontWeight: FontWeight.w900,
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
}

class _CatalogStrip extends StatelessWidget {
  const _CatalogStrip({
    required this.items,
    required this.selected,
    required this.registered,
    required this.dirtyIds,
    required this.onSelected,
  });

  final List<PhytoCatalogItem> items;
  final PhytoCatalogItem? selected;
  final Set<String> registered;
  final Set<String> dirtyIds;
  final ValueChanged<PhytoCatalogItem> onSelected;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(20),
          child: Text(
            'No hay elementos de este tipo relacionados con el cultivo.',
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    return SizedBox(
      height: 190,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: items.length,
        separatorBuilder: (_, _) => const SizedBox(width: 10),
        itemBuilder: (context, index) {
          final item = items[index];
          final active = selected?.id == item.id;
          final marked = registered.contains(item.id) || dirtyIds.contains(item.id);
          return SizedBox(
            width: 150,
            child: Material(
              color: active
                  ? const Color(0xFFF0F6FF)
                  : marked
                      ? const Color(0xFFF0F0F0)
                      : Colors.white,
              elevation: active ? 4 : 1,
              borderRadius: BorderRadius.circular(18),
              child: InkWell(
                borderRadius: BorderRadius.circular(18),
                onTap: () => onSelected(item),
                child: Container(
                  decoration: BoxDecoration(
                    border: Border.all(
                      color: active
                          ? const Color(0xFF0D47C5)
                          : marked
                              ? Colors.grey
                              : const Color(0xFFE0E3E8),
                      width: active ? 2 : 1,
                    ),
                    borderRadius: BorderRadius.circular(18),
                  ),
                  padding: const EdgeInsets.all(10),
                  child: Column(
                    children: [
                      Stack(
                        children: [
                          _RemoteCircleImage(
                            url: item.photo,
                            fallback: item.isDisease
                                ? Icons.coronavirus_outlined
                                : Icons.pest_control,
                          ),
                          if (marked || active)
                            Positioned(
                              top: 0,
                              right: 0,
                              child: CircleAvatar(
                                radius: 10,
                                backgroundColor: active
                                    ? const Color(0xFF0D47C5)
                                    : Colors.grey.shade700,
                                child: const Icon(
                                  Icons.check,
                                  size: 13,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 9),
                      Text(
                        item.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontWeight: FontWeight.w800,
                          fontSize: 14,
                        ),
                      ),
                      const Spacer(),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 9,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: item.isDisease
                              ? const Color(0xFFEAF4E7)
                              : const Color(0xFFFFECE6),
                          borderRadius: BorderRadius.circular(9),
                        ),
                        child: Text(
                          item.isDisease ? 'Enfermedad' : 'Plaga',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: item.isDisease
                                ? const Color(0xFF2E7D32)
                                : const Color(0xFF9B3B18),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _PestEditor extends StatelessWidget {
  const _PestEditor({
    required this.item,
    required this.quantities,
    required this.generalSelected,
    required this.onGeneralChanged,
    required this.onQuantity,
  });

  final PhytoCatalogItem item;
  final Map<String, int> quantities;
  final bool generalSelected;
  final ValueChanged<bool> onGeneralChanged;
  final void Function(PhytoCatalogItem, PhytoStage, int) onQuantity;

  @override
  Widget build(BuildContext context) {
    final stages = item.orderedStages;
    return Card(
      elevation: 2,
      color: const Color(0xFFFFFBF7),
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(22),
        side: const BorderSide(color: Color(0xFFF1D7C8)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              item.name,
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 4),
            const Text(
              'Registra la cantidad observada en cada etapa.',
              style: TextStyle(color: Colors.black54),
            ),
            const SizedBox(height: 12),
            if (stages.isEmpty)
              SwitchListTile(
                value: generalSelected,
                onChanged: onGeneralChanged,
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'Plaga observada',
                  style: TextStyle(fontWeight: FontWeight.w800),
                ),
                subtitle: const Text(
                  'Este elemento no tiene etapas configuradas; se guardará presencia general.',
                ),
              )
            else
              ...stages.map((stage) {
                final key = '${item.id}|${stage.name}';
                final qty = quantities[key] ?? 0;
                return _StageCounter(
                  stage: stage,
                  value: qty,
                  onMinus: qty <= 0
                      ? null
                      : () => onQuantity(item, stage, qty - 1),
                  onPlus: () => onQuantity(item, stage, qty + 1),
                );
              }),
          ],
        ),
      ),
    );
  }
}

class _StageCounter extends StatelessWidget {
  const _StageCounter({
    required this.stage,
    required this.value,
    required this.onMinus,
    required this.onPlus,
  });
  final PhytoStage stage;
  final int value;
  final VoidCallback? onMinus;
  final VoidCallback onPlus;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 98,
      child: Row(
        children: [
          _RemoteCircleImage(
            url: stage.photo,
            fallback: _stageIcon(stage.name),
            size: 72,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              stageLabel(stage.name),
              style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
            ),
          ),
          _CounterButton(icon: Icons.remove, onPressed: onMinus),
          SizedBox(
            width: 48,
            child: Text(
              '$value',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w900),
            ),
          ),
          _CounterButton(icon: Icons.add, onPressed: onPlus),
        ],
      ),
    );
  }
}

class _DiseaseEditor extends StatelessWidget {
  const _DiseaseEditor({
    required this.item,
    required this.evaluation,
    required this.onChanged,
  });
  final PhytoCatalogItem item;
  final DiseaseEvaluation? evaluation;
  final ValueChanged<DiseaseEvaluation> onChanged;

  @override
  Widget build(BuildContext context) {
    final present = evaluation?.presence == DiseasePresence.present;
    final stages = item.orderedStages;
    return Card(
      elevation: 2,
      color: const Color(0xFFFFFBF7),
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(22),
        side: const BorderSide(color: Color(0xFFF1D7C8)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              item.name,
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 4),
            const Text(
              'Indica si la enfermedad está presente y, si aplica, selecciona la fase.',
              style: TextStyle(color: Colors.black54),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: _ChoiceButton(
                    label: 'No presente',
                    icon: Icons.block,
                    selected:
                        evaluation?.presence == DiseasePresence.notPresent,
                    color: const Color(0xFF16A34A),
                    onTap: () => onChanged(
                      const DiseaseEvaluation(DiseasePresence.notPresent),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _ChoiceButton(
                    label: 'Presente',
                    icon: Icons.check_circle_outline,
                    selected: present,
                    color: const Color(0xFF0D47C5),
                    onTap: () => onChanged(
                      DiseaseEvaluation(
                        DiseasePresence.present,
                        stage: evaluation?.stage,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            if (present) ...[
              const SizedBox(height: 20),
              const Row(
                children: [
                  Text(
                    'Fase de la enfermedad',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                  ),
                  SizedBox(width: 6),
                  Icon(Icons.info_outline, size: 17, color: Color(0xFF6E7580)),
                ],
              ),
              const SizedBox(height: 4),
              const Text(
                'Selecciona la imagen que corresponde al estado observado.',
                style: TextStyle(fontSize: 12, color: Color(0xFF6E7580)),
              ),
              const SizedBox(height: 12),
              if (stages.isEmpty)
                const Text(
                  'No hay fases disponibles. Configura Inicio, Desarrollo y Avanzado en el catálogo.',
                  style: TextStyle(color: Colors.red),
                )
              else
                LayoutBuilder(
                  builder: (context, constraints) {
                    final columns = constraints.maxWidth < 330 ? 2 : 3;
                    const gap = 9.0;
                    final width =
                        (constraints.maxWidth - gap * (columns - 1)) / columns;
                    return Wrap(
                      spacing: gap,
                      runSpacing: gap,
                      children: stages.map((stage) {
                        return SizedBox(
                          width: width,
                          child: _DiseaseStageOption(
                            stage: stage,
                            selected: evaluation?.stage == stage.name,
                            onTap: () => onChanged(
                              DiseaseEvaluation(
                                DiseasePresence.present,
                                stage: stage.name,
                              ),
                            ),
                          ),
                        );
                      }).toList(growable: false),
                    );
                  },
                ),
            ],
          ],
        ),
      ),
    );
  }
}

class _DiseaseStageOption extends StatelessWidget {
  const _DiseaseStageOption({
    required this.stage,
    required this.selected,
    required this.onTap,
  });

  final PhytoStage stage;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final phaseColor = _diseaseStageColor(stage.name);
    return Material(
      color: selected ? phaseColor.withValues(alpha: 0.15) : Colors.white,
      elevation: selected ? 3 : 0,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          height: 164,
          padding: const EdgeInsets.all(7),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: selected ? phaseColor : const Color(0xFFD9DEE6),
              width: selected ? 2 : 1.4,
            ),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 82,
                height: 82,
                padding: const EdgeInsets.all(2),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: selected
                      ? phaseColor.withValues(alpha: 0.20)
                      : const Color(0xFFF0F4EE),
                  border: Border.all(
                    color: selected
                        ? phaseColor.withValues(alpha: 0.55)
                        : Colors.transparent,
                  ),
                ),
                child: _RemoteCircleImage(
                  url: stage.photo,
                  fallback: _stageIcon(stage.name),
                  size: 76,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                stageLabel(stage.name),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w900,
                  color: selected ? phaseColor : const Color(0xFF1D2430),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChoiceButton extends StatelessWidget {
  const _ChoiceButton({
    required this.label,
    required this.icon,
    required this.selected,
    required this.color,
    required this.onTap,
  });
  final String label;
  final IconData icon;
  final bool selected;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onTap,
      icon: Icon(icon),
      label: Text(label),
      style: OutlinedButton.styleFrom(
        foregroundColor: selected ? Colors.white : color,
        backgroundColor: selected ? color : Colors.white,
        side: BorderSide(color: color),
        padding: const EdgeInsets.symmetric(vertical: 14),
      ),
    );
  }
}

class _SeverityCard extends StatelessWidget {
  const _SeverityCard({required this.controller, required this.total});
  final TextEditingController controller;
  final int total;

  @override
  Widget build(BuildContext context) {
    final major = int.tryParse(controller.text) ?? 5;
    final ranges = SeverityRanges(major: major <= 0 ? 5 : major);
    return Card(
      color: const Color(0xFFFFFBEE),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Severidad del punto',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: controller,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Severidad mayor (M)',
                prefixIcon: Icon(Icons.speed),
              ),
            ),
            const SizedBox(height: 9),
            Text('Cantidad actual: $total'),
            Text(
              ranges.summary,
              style: const TextStyle(fontSize: 12, color: Colors.black54),
            ),
          ],
        ),
      ),
    );
  }
}

class _PhotoCard extends StatelessWidget {
  const _PhotoCard({
    required this.photo,
    required this.onCamera,
    required this.onGallery,
    required this.onRemove,
  });
  final XFile? photo;
  final VoidCallback onCamera;
  final VoidCallback onGallery;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Evidencia fotográfica',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 10),
            if (photo != null)
              Stack(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(16),
                    child: Image.file(
                      File(photo!.path),
                      height: 190,
                      width: double.infinity,
                      fit: BoxFit.cover,
                    ),
                  ),
                  Positioned(
                    top: 8,
                    right: 8,
                    child: IconButton.filled(
                      onPressed: onRemove,
                      icon: const Icon(Icons.close),
                    ),
                  ),
                ],
              ),
            if (photo != null) const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: FilledButton.tonalIcon(
                    onPressed: onCamera,
                    icon: const Icon(Icons.camera_alt),
                    label: Text(photo == null ? 'Tomar foto' : 'Repetir'),
                  ),
                ),
                const SizedBox(width: 10),
                IconButton.filledTonal(
                  tooltip: 'Galería',
                  onPressed: onGallery,
                  icon: const Icon(Icons.photo_library_outlined),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _RemoteCircleImage extends StatelessWidget {
  const _RemoteCircleImage({
    required this.url,
    required this.fallback,
    this.size = 86,
  });
  final String? url;
  final IconData fallback;
  final double size;

  @override
  Widget build(BuildContext context) {
    final fallbackWidget = Container(
      width: size,
      height: size,
      decoration: const BoxDecoration(
        color: Color(0xFFF0F4EE),
        shape: BoxShape.circle,
      ),
      child: Icon(fallback, size: size * 0.46, color: AppTheme.darkGreen),
    );

    return ClipOval(
      child: AuthenticatedRemoteImage(
        url: url,
        width: size,
        height: size,
        fit: BoxFit.cover,
        fallback: fallbackWidget,
      ),
    );
  }
}

class _CounterButton extends StatelessWidget {
  const _CounterButton({required this.icon, required this.onPressed});
  final IconData icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 42,
      height: 42,
      child: IconButton.outlined(
        onPressed: onPressed,
        icon: Icon(icon, size: 20),
      ),
    );
  }
}

class _SelectHint extends StatelessWidget {
  const _SelectHint();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFFF6F8F5),
        borderRadius: BorderRadius.circular(18),
      ),
      child: const Row(
        children: [
          Icon(Icons.touch_app_outlined, color: AppTheme.primary),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'Selecciona una tarjeta del catálogo para capturar sus etapas o presencia.',
            ),
          ),
        ],
      ),
    );
  }
}

class _BottomActions extends StatelessWidget {
  const _BottomActions({
    required this.saving,
    required this.pendingRecords,
    required this.onNoPest,
    required this.onSave,
  });
  final bool saving;
  final int pendingRecords;
  final VoidCallback onNoPest;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: Material(
        elevation: 12,
        color: Colors.white,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: saving ? null : onNoPest,
                  icon: const Icon(Icons.block),
                  label: const Text('Sin plaga'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: const Color(0xFF1D2430),
                    padding: const EdgeInsets.symmetric(vertical: 17),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                flex: 2,
                child: FilledButton.icon(
                  onPressed: saving ? null : onSave,
                  icon: saving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.save_outlined),
                  label: Text(
                    saving
                        ? 'Guardando...'
                        : 'Guardar registro${pendingRecords > 0 ? ' ($pendingRecords)' : ''}',
                  ),
                  style: FilledButton.styleFrom(
                    backgroundColor: const Color(0xFF176E35),
                    padding: const EdgeInsets.symmetric(vertical: 17),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

Color _diseaseStageColor(String value) {
  final stage = value.trim().toLowerCase();
  if (stage.contains('inicio')) return const Color(0xFFFACC15);
  if (stage.contains('desarrollo')) return const Color(0xFFF97316);
  if (stage.contains('avanz')) return const Color(0xFFDC2626);
  return const Color(0xFF6B7280);
}

String _checkpointCropEmoji(String crop) {
  final value = crop.toLowerCase();
  if (value.contains('maíz') || value.contains('maiz')) return '🌽';
  if (value.contains('trigo')) return '🌾';
  if (value.contains('fresa')) return '🍓';
  if (value.contains('tomate')) return '🍅';
  return '🌱';
}

IconData _stageIcon(String value) {
  final stage = value.toLowerCase();
  if (stage.contains('huev')) return Icons.circle_outlined;
  if (stage.contains('larva')) return Icons.bug_report_outlined;
  if (stage.contains('pupa')) return Icons.hexagon_outlined;
  if (stage.contains('adulto')) return Icons.pest_control;
  if (stage.contains('inicio')) return Icons.spa_outlined;
  if (stage.contains('desarrollo')) return Icons.eco_outlined;
  if (stage.contains('avanz')) return Icons.grass;
  return Icons.circle;
}

extension _FirstOrNullExtension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}

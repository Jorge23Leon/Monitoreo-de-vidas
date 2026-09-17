import 'package:flutter_test/flutter_test.dart';
import 'package:monitoreo_de_vidas_flutter/features/monitoring/monitoring_models.dart';

void main() {
  group('Catálogo fitosanitario', () {
    test('ordena las etapas de una plaga como la app Kotlin', () {
      final item = PhytoCatalogItem.fromJson({
        'id': 23,
        'name': 'Gusano cogollero',
        'type': 'Plaga',
        'default_crop': 7,
        'stages': [
          {'name': 'Adulto con alas'},
          {'name': 'Pupa'},
          {'name': 'Huevecillo'},
          {'name': 'Larva'},
          {'name': 'Adulto'},
        ],
      });

      expect(item.cropId, '7');
      expect(item.isPest, isTrue);
      expect(
        item.orderedStages.map((e) => stageLabel(e.name)).toList(),
        ['Huevecillo', 'Larva', 'Pupa', 'Adulto', 'Adulto con alas'],
      );
    });

    test('en enfermedad conserva solo inicio, desarrollo y avanzado', () {
      final item = PhytoCatalogItem.fromJson({
        'id': 81,
        'name': 'Roya del cultivo',
        'type': 'Enfermedad',
        'crop': 7,
        'phases': ['Terminal', 'Avanzado', 'Inicio', 'Desarrollo'],
      });

      expect(item.isDisease, isTrue);
      expect(
        item.orderedStages.map((e) => stageLabel(e.name)).toList(),
        ['Inicio', 'Desarrollo', 'Avanzado'],
      );
    });

    test('combina las fotos de fases que vienen en el detalle', () {
      final base = PhytoCatalogItem.fromJson({
        'id': 90,
        'name': 'Roya',
        'type': 'Enfermedad',
        'stages': ['Inicio', 'Desarrollo', 'Avanzado'],
      });
      final detail = PhytoCatalogItem.fromJson({
        'id': 90,
        'name': 'Roya',
        'type': 'Enfermedad',
        'stage_photos': [
          {'stage': 'Inicio', 'image_url': '/media/inicio.jpg'},
          {'stage': 'Desarrollo', 'image_url': '/media/desarrollo.jpg'},
          {'stage': 'Avanzado', 'image_url': '/media/avanzado.jpg'},
        ],
      });

      final merged = base.mergeDetail(detail);
      expect(merged.orderedStages.map((e) => e.photo).toList(), [
        '/media/inicio.jpg',
        '/media/desarrollo.jpg',
        '/media/avanzado.jpg',
      ]);
    });

    test('mantiene etapas operativas si una API antigua no manda stages', () {
      final pest = PhytoCatalogItem.fromJson({
        'id': 91,
        'name': 'Chicharrita del maíz',
        'type': 'Plaga',
      });
      expect(
        pest.orderedStages.map((e) => stageLabel(e.name)).toList(),
        ['Huevecillo', 'Larva', 'Pupa', 'Adulto', 'Adulto con alas'],
      );
    });

    test('reconoce Sin plaga', () {
      final item = PhytoCatalogItem.fromJson({
        'id': 999,
        'name': 'Sin plaga',
        'type': 'Plaga',
      });
      expect(item.isNoPest, isTrue);
      expect(item.isPest, isFalse);
    });
  });

  group('Checkpoint offline', () {
    test('serializa y recupera un registro de enfermedad ausente', () {
      final now = DateTime.utc(2026, 9, 7, 14, 0);
      final original = PendingCheckpoint(
        localId: 'local-1',
        headerId: 'header-1',
        targetId: 'target-1',
        phytoIssueId: '44',
        phytoName: 'Roya',
        phytoType: 'Enfermedad',
        stage: null,
        presenceStatus: 'low',
        qty: 0,
        latitude: 20.1,
        longitude: -101.2,
        notes: 'Sin síntomas',
        photoPath: null,
        capturedAt: now,
        syncState: 'pending',
      );
      final restored = PendingCheckpoint.fromDb(original.toDbMap());
      expect(restored.isDisease, isTrue);
      expect(restored.presenceStatus, 'low');
      expect(restored.qty, 0);
      expect(restored.stage, isNull);
      expect(restored.latitude, 20.1);
    });
  });
}

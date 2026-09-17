import 'dart:io';
import 'dart:math';

import 'package:geolocator/geolocator.dart';
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

class MonitoringLocationService {
  // Paridad con Kotlin: lectura visible hasta 60 m, captura solo con 35 m o mejor.
  static const double maxCaptureAccuracyMeters = 35;
  static const double maxReadableAccuracyMeters = 60;
  static const Duration maxCaptureAge = Duration(seconds: 15);
  static const Duration maxLastKnownAge = Duration(minutes: 2);

  static Future<void> _ensurePermission() async {
    final enabled = await Geolocator.isLocationServiceEnabled();
    if (!enabled) {
      throw Exception('Activa la ubicación/GPS para registrar el punto.');
    }

    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.denied) {
      throw Exception('La app necesita permiso de ubicación precisa.');
    }
    if (permission == LocationPermission.deniedForever) {
      throw Exception(
        'El permiso de ubicación está bloqueado. Actívalo desde Ajustes.',
      );
    }

    // En iOS el usuario puede dar ubicación aproximada aun teniendo permiso.
    // Para monitoreo de campo solicitamos precisión temporal completa.
    if (Platform.isIOS) {
      try {
        final accuracy = await Geolocator.getLocationAccuracy();
        if (accuracy == LocationAccuracyStatus.reduced) {
          await Geolocator.requestTemporaryFullAccuracy(
            purposeKey: 'CIAGROPreciseLocation',
          );
        }
      } catch (_) {
        // Si iOS no permite elevar la precisión, la validación de 35 m decide.
      }
    }
  }

  static const LocationSettings _preciseSettings = LocationSettings(
    accuracy: LocationAccuracy.bestForNavigation,
    distanceFilter: 0,
    timeLimit: Duration(seconds: 20),
  );

  static bool _isSane(Position position, {double maxAccuracy = maxReadableAccuracyMeters}) {
    if (!position.latitude.isFinite || !position.longitude.isFinite) return false;
    if (position.latitude < -90 || position.latitude > 90) return false;
    if (position.longitude < -180 || position.longitude > 180) return false;
    if (!position.accuracy.isFinite || position.accuracy <= 0) return false;
    if (position.accuracy > maxAccuracy) return false;
    final age = DateTime.now().difference(position.timestamp).abs();
    return age <= maxLastKnownAge;
  }

  static Future<Position> currentPosition() async {
    await _ensurePermission();
    try {
      final fresh = await Geolocator.getCurrentPosition(
        locationSettings: _preciseSettings,
      );
      if (_isSane(fresh)) return fresh;
    } catch (_) {
      // Se intenta la última lectura únicamente como respaldo visual.
    }

    final cached = await Geolocator.getLastKnownPosition();
    if (cached != null && _isSane(cached)) return cached;
    throw Exception(
      'Todavía no hay una lectura GPS confiable (60 m o mejor). '
      'Mantén la ubicación activada y espera unos segundos.',
    );
  }

  /// Para capturar se toman lecturas de alta precisión y se conserva la mejor.
  /// Igual que Kotlin, la coordenada guardada es SIEMPRE la lectura GPS real,
  /// nunca el punto tocado en el mapa.
  static Stream<Position> positionStream() async* {
    await _ensurePermission();
    await for (final position in Geolocator.getPositionStream(
      locationSettings: _preciseSettings,
    )) {
      if (_isSane(position)) yield position;
    }
  }

  static Future<Position> capturePosition() async {
    await _ensurePermission();
    Position? best;
    Object? lastError;

    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        final candidate = await Geolocator.getCurrentPosition(
          locationSettings: _preciseSettings,
        );
        if (_isSane(candidate, maxAccuracy: 200)) {
          if (best == null || candidate.accuracy < best.accuracy) {
            best = candidate;
          }
          final age = DateTime.now().difference(candidate.timestamp).abs();
          if (candidate.accuracy <= maxCaptureAccuracyMeters &&
              age <= maxCaptureAge) {
            return candidate;
          }
        }
      } catch (error) {
        lastError = error;
      }
    }

    if (best != null) {
      final age = DateTime.now().difference(best.timestamp).abs();
      if (age > maxCaptureAge) {
        throw Exception(
          'La mejor lectura GPS está desactualizada. Espera una nueva posición.',
        );
      }
      throw Exception(
        'La mejor precisión GPS disponible es de ${best.accuracy.toStringAsFixed(0)} m. '
        'Necesitas 35 m o mejor antes de registrar el punto.',
      );
    }

    throw Exception(
      'No se pudo obtener una lectura GPS válida. ${lastError ?? ''}'.trim(),
    );
  }

  static double distanceMeters(
    Position current,
    double latitude,
    double longitude,
  ) =>
      Geolocator.distanceBetween(
        current.latitude,
        current.longitude,
        latitude,
        longitude,
      );

  static bool pointInsidePolygon(
    double latitude,
    double longitude,
    List<({double lat, double lon})> polygon,
  ) {
    if (polygon.length < 3) return true;
    var inside = false;
    for (var i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      final xi = polygon[i].lon;
      final yi = polygon[i].lat;
      final xj = polygon[j].lon;
      final yj = polygon[j].lat;
      final denominator = (yj - yi).abs() < 1e-12 ? 1e-12 : (yj - yi);
      final intersects = ((yi > latitude) != (yj > latitude)) &&
          (longitude < (xj - xi) * (latitude - yi) / denominator + xi);
      if (intersects) inside = !inside;
    }
    return inside;
  }
}

class MonitoringPhotoService {
  static final _picker = ImagePicker();
  static const int maxImageBytes = 340 * 1024;
  static const int initialMaxSide = 2048;
  static const int minimumMaxSide = 320;

  static Future<XFile?> takePhoto() => _picker.pickImage(
        source: ImageSource.camera,
        imageQuality: 100,
        maxWidth: 2600,
        maxHeight: 2600,
        preferredCameraDevice: CameraDevice.rear,
      );

  static Future<XFile?> choosePhoto() => _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 100,
        maxWidth: 2600,
        maxHeight: 2600,
      );

  static Future<String?> persistPhoto(
    XFile? source, {
    required String headerId,
    required String targetId,
  }) async {
    if (source == null) return null;

    final bytes = await source.readAsBytes();
    final decoded = img.decodeImage(bytes);
    if (decoded == null) {
      throw Exception('La evidencia seleccionada no es una imagen válida.');
    }

    var working = img.bakeOrientation(decoded);
    final largestSide = max(working.width, working.height);
    if (largestSide > initialMaxSide) {
      final factor = initialMaxSide / largestSide;
      working = img.copyResize(
        working,
        width: max(1, (working.width * factor).round()),
        height: max(1, (working.height * factor).round()),
        interpolation: img.Interpolation.average,
      );
    }

    // JPG no conserva transparencia. Se compone sobre blanco para que PNG/HEIC
    // con alpha no termine con fondo oscuro.
    var normalized = img.Image(width: working.width, height: working.height);
    img.fill(normalized, color: img.ColorRgb8(255, 255, 255));
    img.compositeImage(normalized, working);

    List<int>? encoded;
    var quality = 96;
    while (quality >= 20) {
      final candidate = img.encodeJpg(normalized, quality: quality);
      if (candidate.length <= maxImageBytes) {
        encoded = candidate;
        break;
      }
      quality -= 6;
    }

    var maxSide = max(normalized.width, normalized.height);
    while (encoded == null && maxSide > minimumMaxSide) {
      maxSide = max(minimumMaxSide, (maxSide * 0.82).round());
      final factor = maxSide / max(normalized.width, normalized.height);
      normalized = img.copyResize(
        normalized,
        width: max(1, (normalized.width * factor).round()),
        height: max(1, (normalized.height * factor).round()),
        interpolation: img.Interpolation.average,
      );
      for (quality = 90; quality >= 18; quality -= 6) {
        final candidate = img.encodeJpg(normalized, quality: quality);
        if (candidate.length <= maxImageBytes) {
          encoded = candidate;
          break;
        }
      }
    }

    if (encoded == null || encoded.isEmpty || encoded.length > maxImageBytes) {
      throw Exception('No fue posible reducir la evidencia al límite de 340 KB.');
    }

    final dir = await getApplicationDocumentsDirectory();
    final media = Directory(
      p.join(dir.path, 'phyto_pending', _safe(headerId)),
    );
    await media.create(recursive: true);

    final now = DateTime.now().millisecondsSinceEpoch;
    final random = Random().nextInt(999999).toString().padLeft(6, '0');
    final destination = File(
      p.join(
        media.path,
        'h_${_safe(headerId)}_p_${_safe(targetId)}_${now}_$random.jpg',
      ),
    );
    await destination.writeAsBytes(encoded, flush: true);
    if (!await destination.exists() || await destination.length() > maxImageBytes) {
      throw Exception('La foto no se pudo guardar correctamente.');
    }
    return destination.path;
  }

  static String _safe(String input) =>
      input.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '_');
}

class SeverityRanges {
  const SeverityRanges({this.major = 5});
  final int major;

  int get minor => max(1, major ~/ 2);

  String metadata(String notes) {
    final clean = notes
        .replaceAll(RegExp(r'\[SEV_PUNTO:M=\d+\]'), '')
        .replaceAll(RegExp(r'\[SEV_PUNTO:m=\d+;M=\d+\]'), '')
        .trim();
    return clean.isEmpty
        ? '[SEV_PUNTO:M=$major]'
        : '[SEV_PUNTO:M=$major] $clean';
  }

  String get summary =>
      'Verde: 0 · Menor: 1-$minor · Mayor: ${minor + 1}-$major · Rojo: ${major + 1}+';
}

import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../api/api_client.dart';
import '../config.dart';

/// Carga imágenes del backend de CIAGRO usando la misma sesión JWT de la app.
///
/// También corrige URLs guardadas con hosts viejos de desarrollo (localhost,
/// Docker o trycloudflare), resuelve endpoints de attachments que responden JSON
/// y conserva una copia local para que el catálogo abra más rápido después.
class AuthenticatedImageLoader {
  AuthenticatedImageLoader._();

  static final Map<String, Uint8List> _memory = <String, Uint8List>{};
  static final Map<String, Future<Uint8List?>> _inFlight =
      <String, Future<Uint8List?>>{};

  static const int _maxRedirects = 4;
  static const int _maxImageBytes = 12 * 1024 * 1024;
  static const int _maxJsonBytes = 2 * 1024 * 1024;

  static Future<Uint8List?> load(String? raw) async {
    final normalized = normalizeUrl(raw);
    if (normalized == null) return null;

    final cached = _memory[normalized];
    if (cached != null) return cached;

    return _inFlight.putIfAbsent(normalized, () async {
      try {
        final disk = await _readDiskCache(normalized);
        if (disk != null) {
          _memory[normalized] = disk;
          return disk;
        }

        final bytes = await _downloadRecursive(
          normalized,
          visited: <String>{},
          depth: 0,
        );
        if (bytes != null) {
          _memory[normalized] = bytes;
          await _writeDiskCache(normalized, bytes);
        }
        return bytes;
      } finally {
        _inFlight.remove(normalized);
      }
    });
  }

  static String? normalizeUrl(String? raw) {
    final clean = raw?.trim().replaceAll('"', '');
    if (clean == null || clean.isEmpty) return null;

    final base = Uri.tryParse(AppConfig.normalizedBaseUrl);
    if (base == null || !base.hasScheme || base.host.isEmpty) return clean;

    final parsed = Uri.tryParse(clean);
    if (parsed == null) return null;

    if (!parsed.hasScheme) {
      final relative = clean.startsWith('/') ? clean.substring(1) : clean;
      return base.resolve(relative).toString();
    }

    if (parsed.scheme != 'http' && parsed.scheme != 'https') return null;

    if (_replaceHost(parsed.host)) {
      return Uri(
        scheme: base.scheme,
        host: base.host,
        port: base.hasPort ? base.port : null,
        path: parsed.path,
        query: parsed.hasQuery ? parsed.query : null,
        fragment: parsed.hasFragment ? parsed.fragment : null,
      ).toString();
    }

    return parsed.toString();
  }

  static Future<Uint8List?> _downloadRecursive(
    String url, {
    required Set<String> visited,
    required int depth,
  }) async {
    if (depth > _maxRedirects) return null;

    final normalized = normalizeUrl(url);
    if (normalized == null || !visited.add(normalized)) return null;

    final uri = Uri.tryParse(normalized);
    if (uri == null) return null;

    final currentBase = Uri.tryParse(AppConfig.normalizedBaseUrl);
    final sameBackend = currentBase != null &&
        uri.host.toLowerCase() == currentBase.host.toLowerCase() &&
        _effectivePort(uri) == _effectivePort(currentBase);

    final client = sameBackend
        ? ApiClient.instance.dio
        : ApiClient.instance.publicDio;

    try {
      final response = await client.getUri<List<int>>(
        uri,
        options: Options(
          responseType: ResponseType.bytes,
          headers: const {
            'Accept':
                'image/avif,image/webp,image/apng,image/jpeg,image/png,image/*,application/json,*/*;q=0.8',
          },
        ),
      );

      final rawBytes = response.data;
      if (rawBytes == null || rawBytes.isEmpty) return null;
      if (rawBytes.length > _maxImageBytes) return null;

      final bytes = Uint8List.fromList(rawBytes);
      final contentType = (response.headers.value(Headers.contentTypeHeader) ?? '')
          .toLowerCase();

      if (_looksLikeImage(bytes) || contentType.startsWith('image/')) {
        return _looksLikeImage(bytes) ? bytes : null;
      }

      if (bytes.length > _maxJsonBytes) return null;
      final jsonText = _tryDecodeText(bytes);
      if (jsonText == null) return null;
      final next = _imageUrlFromJsonText(jsonText);
      if (next == null) return null;

      return _downloadRecursive(
        next,
        visited: visited,
        depth: depth + 1,
      );
    } on DioException {
      return null;
    } catch (_) {
      return null;
    }
  }

  static String? _imageUrlFromJsonText(String text) {
    try {
      final decoded = jsonDecode(text);
      return _findImageReference(decoded);
    } catch (_) {
      return null;
    }
  }

  static String? _findImageReference(dynamic value) {
    if (value is String) {
      return _looksLikeImageReference(value) ? value.trim() : null;
    }

    if (value is List) {
      for (final entry in value) {
        final found = _findImageReference(entry);
        if (found != null) return found;
      }
      return null;
    }

    if (value is Map) {
      const preferred = <String>[
        'download_url',
        'file_url',
        'image_url',
        'photo_url',
        'absolute_url',
        'content_url',
        'original_url',
        'source_url',
        'thumbnail_url',
        'attachment_url',
        'download',
        'file',
        'image',
        'photo',
        'thumbnail',
        'path',
        'url',
        'href',
      ];

      for (final key in preferred) {
        final found = _findImageReference(value[key]);
        if (found != null) return found;
      }
      for (final entry in value.values) {
        final found = _findImageReference(entry);
        if (found != null) return found;
      }
    }

    return null;
  }

  static bool _looksLikeImageReference(String value) {
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

  static bool _looksLikeImage(Uint8List bytes) {
    if (bytes.length < 12) return false;

    // JPEG
    if (bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) return true;

    // PNG
    if (bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4E &&
        bytes[3] == 0x47) {
      return true;
    }

    // GIF
    final head6 = ascii.decode(bytes.sublist(0, 6), allowInvalid: true);
    if (head6 == 'GIF87a' || head6 == 'GIF89a') return true;

    // WEBP: RIFF....WEBP
    final riff = ascii.decode(bytes.sublist(0, 4), allowInvalid: true);
    final webp = ascii.decode(bytes.sublist(8, 12), allowInvalid: true);
    if (riff == 'RIFF' && webp == 'WEBP') return true;

    // AVIF/HEIF: ....ftypavif / ftypavis / ftypheic / ftypmif1
    final box = ascii.decode(bytes.sublist(4, 12), allowInvalid: true);
    if (box.startsWith('ftypavif') ||
        box.startsWith('ftypavis') ||
        box.startsWith('ftypheic') ||
        box.startsWith('ftypmif1')) {
      return true;
    }

    return false;
  }

  static String? _tryDecodeText(Uint8List bytes) {
    try {
      final text = utf8.decode(bytes).trim();
      if (text.startsWith('{') || text.startsWith('[')) return text;
    } catch (_) {}
    return null;
  }

  static bool _replaceHost(String host) {
    final value = host.toLowerCase();
    if (value.isEmpty) return true;
    if (value == 'localhost' ||
        value == '0.0.0.0' ||
        value == 'web' ||
        value == 'ciagro-web' ||
        value == 'host.docker.internal' ||
        value.endsWith('.trycloudflare.com') ||
        value.startsWith('127.') ||
        value.startsWith('10.') ||
        value.startsWith('192.168.')) {
      return true;
    }
    if (value.startsWith('172.')) {
      final parts = value.split('.');
      final second = parts.length > 1 ? int.tryParse(parts[1]) : null;
      if (second != null && second >= 16 && second <= 31) return true;
    }
    return false;
  }

  static int _effectivePort(Uri uri) {
    if (uri.hasPort) return uri.port;
    return uri.scheme == 'https' ? 443 : 80;
  }

  static Future<Directory> _cacheDirectory() async {
    final root = await getApplicationSupportDirectory();
    final directory = Directory('${root.path}${Platform.pathSeparator}catalogo_imagenes');
    if (!await directory.exists()) await directory.create(recursive: true);
    return directory;
  }

  static Future<Uint8List?> _readDiskCache(String url) async {
    try {
      final directory = await _cacheDirectory();
      final file = File('${directory.path}${Platform.pathSeparator}${_fnv1a64(url)}.img');
      if (!await file.exists()) return null;
      final bytes = await file.readAsBytes();
      if (bytes.isEmpty || bytes.length > _maxImageBytes || !_looksLikeImage(bytes)) {
        try {
          await file.delete();
        } catch (_) {}
        return null;
      }
      return bytes;
    } catch (_) {
      return null;
    }
  }

  static Future<void> _writeDiskCache(String url, Uint8List bytes) async {
    try {
      final directory = await _cacheDirectory();
      final file = File('${directory.path}${Platform.pathSeparator}${_fnv1a64(url)}.img');
      await file.writeAsBytes(bytes, flush: true);
    } catch (_) {
      // La imagen ya está en memoria; fallar el cache no debe romper la UI.
    }
  }

  static String _fnv1a64(String value) {
    const int offset = 0xcbf29ce484222325;
    const int prime = 0x100000001b3;
    var hash = offset;
    for (final byte in utf8.encode(value)) {
      hash ^= byte;
      hash = (hash * prime) & 0xFFFFFFFFFFFFFFFF;
    }
    return hash.toRadixString(16).padLeft(16, '0');
  }
}

class AuthenticatedRemoteImage extends StatefulWidget {
  const AuthenticatedRemoteImage({
    super.key,
    required this.url,
    required this.fallback,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
  });

  final String? url;
  final Widget fallback;
  final double? width;
  final double? height;
  final BoxFit fit;

  @override
  State<AuthenticatedRemoteImage> createState() =>
      _AuthenticatedRemoteImageState();
}

class _AuthenticatedRemoteImageState extends State<AuthenticatedRemoteImage> {
  late Future<Uint8List?> _future;

  @override
  void initState() {
    super.initState();
    _future = AuthenticatedImageLoader.load(widget.url);
  }

  @override
  void didUpdateWidget(covariant AuthenticatedRemoteImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.url != widget.url) {
      _future = AuthenticatedImageLoader.load(widget.url);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.url == null || widget.url!.trim().isEmpty) return widget.fallback;

    return FutureBuilder<Uint8List?>(
      future: _future,
      builder: (context, snapshot) {
        final bytes = snapshot.data;
        if (bytes == null) {
          if (snapshot.connectionState == ConnectionState.done) {
            return widget.fallback;
          }
          return Stack(
            alignment: Alignment.center,
            children: [
              widget.fallback,
              const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ],
          );
        }

        return Image.memory(
          bytes,
          width: widget.width,
          height: widget.height,
          fit: widget.fit,
          gaplessPlayback: true,
          errorBuilder: (_, _, _) => widget.fallback,
        );
      },
    );
  }
}

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

class MapPoint {
  const MapPoint(
    this.lat,
    this.lon, {
    this.id,
    this.label,
    this.value,
    this.markerText,
    this.markerColor,
    this.pestText,
    this.diseaseText,
    this.pestColor,
    this.diseaseColor,
  });

  final double lat;
  final double lon;
  final String? id;
  final String? label;
  final double? value;
  final String? markerText;
  final String? markerColor;

  /// Datos opcionales para el marcador dividido P/E del reporte.
  final String? pestText;
  final String? diseaseText;
  final String? pestColor;
  final String? diseaseColor;

  bool get hasSplitPhytoMarker => pestColor != null || diseaseColor != null;
}

class LeafletMap extends StatefulWidget {
  const LeafletMap({
    super.key,
    required this.points,
    this.polygon = const [],
    this.currentLocation,
    this.currentAccuracy,
    this.onMapTap,
    this.onPointTap,
    this.height = 420,
    this.ndvi = false,
  });

  final List<MapPoint> points;
  final List<MapPoint> polygon;
  final MapPoint? currentLocation;
  final double? currentAccuracy;
  final VoidCallback? onMapTap;
  final ValueChanged<String>? onPointTap;
  final double height;
  final bool ndvi;

  @override
  State<LeafletMap> createState() => _LeafletMapState();
}

class _LeafletMapState extends State<LeafletMap> {
  late final WebViewController controller;
  bool pageReady = false;

  @override
  void initState() {
    super.initState();
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..addJavaScriptChannel(
        'CiagroMapTap',
        onMessageReceived: (_) => widget.onMapTap?.call(),
      )
      ..addJavaScriptChannel(
        'CiagroPointTap',
        onMessageReceived: (message) {
          final id = message.message.trim();
          if (id.isNotEmpty) widget.onPointTap?.call(id);
        },
      )
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (_) {
            pageReady = true;
            _pushData();
          },
        ),
      )
      ..loadFlutterAsset('assets/leaflet/map.html');
  }

  @override
  void didUpdateWidget(covariant LeafletMap oldWidget) {
    super.didUpdateWidget(oldWidget);
    _pushData();
  }

  Future<void> _pushData() async {
    if (!pageReady) return;
    final data = {
      'points': widget.points
          .map(
            (p) => {
              'id': p.id,
              'lat': p.lat,
              'lon': p.lon,
              'label': p.label,
              'value': p.value,
              'markerText': p.markerText,
              'markerColor': p.markerColor,
              'pestText': p.pestText,
              'diseaseText': p.diseaseText,
              'pestColor': p.pestColor,
              'diseaseColor': p.diseaseColor,
              'splitPhyto': p.hasSplitPhytoMarker,
            },
          )
          .toList(),
      'polygon': widget.polygon.map((p) => [p.lat, p.lon]).toList(),
      'currentLocation': widget.currentLocation == null
          ? null
          : {
              'lat': widget.currentLocation!.lat,
              'lon': widget.currentLocation!.lon,
              'label': widget.currentLocation!.label ?? 'Tu ubicación',
              'accuracy': widget.currentAccuracy,
            },
      'ndvi': widget.ndvi,
      'interactive': widget.onMapTap != null,
      'pointInteractive': widget.onPointTap != null,
    };
    try {
      await controller.runJavaScript(
        'window.setCiagroData(${jsonEncode(data)});',
      );
    } catch (_) {
      // Si el WebView se está cerrando, no hay nada que reintentar aquí.
    }
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: widget.height,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: WebViewWidget(controller: controller),
      ),
    );
  }
}

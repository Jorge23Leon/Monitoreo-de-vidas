import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';
import '../../core/widgets/gpa_loading_indicator.dart';

class RearCameraScreen extends StatefulWidget {
  const RearCameraScreen({super.key});

  @override
  State<RearCameraScreen> createState() => _RearCameraScreenState();
}

class _RearCameraScreenState extends State<RearCameraScreen> {
  CameraController? _controller;
  bool _takingPicture = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initializeRearCamera();
  }

  Future<void> _initializeRearCamera() async {
    try {
      final cameras = await availableCameras();

      CameraDescription? rear;
      for (final camera in cameras) {
        if (camera.lensDirection == CameraLensDirection.back) {
          rear = camera;
          break;
        }
      }

      if (rear == null) {
        throw Exception(
          'Este dispositivo no reporta una cámara trasera disponible.',
        );
      }

      final controller = CameraController(
        rear,
        ResolutionPreset.high,
        enableAudio: false,
      );
      await controller.initialize();

      if (!mounted) {
        await controller.dispose();
        return;
      }

      setState(() {
        _controller = controller;
        _error = null;
      });
    } on CameraException catch (error) {
      if (!mounted) return;
      setState(() {
        _error = switch (error.code) {
          'CameraAccessDenied' =>
            'CIAGRO necesita permiso de cámara para tomar la evidencia.',
          'CameraAccessDeniedWithoutPrompt' =>
            'El permiso de cámara está bloqueado. Actívalo desde Ajustes.',
          'CameraAccessRestricted' =>
            'El uso de la cámara está restringido en este dispositivo.',
          _ =>
            'No se pudo abrir la cámara trasera: '
                '${error.description ?? error.code}',
        };
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _capture() async {
    final controller = _controller;
    if (controller == null ||
        !controller.value.isInitialized ||
        _takingPicture) {
      return;
    }

    setState(() => _takingPicture = true);
    try {
      final photo = await controller.takePicture();
      if (!mounted) return;
      Navigator.of(context).pop<XFile>(XFile(photo.path));
    } on CameraException catch (error) {
      if (!mounted) return;
      setState(() {
        _takingPicture = false;
        _error =
            'No se pudo tomar la fotografía: '
            '${error.description ?? error.code}';
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _takingPicture = false;
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: const Text(
          'Cámara trasera',
          style: TextStyle(fontWeight: FontWeight.w800),
        ),
      ),
      body: SafeArea(
        top: false,
        child: _error != null
            ? _CameraError(
                message: _error!,
                onRetry: () {
                  final previous = _controller;
                  setState(() {
                    _error = null;
                    _controller = null;
                  });
                  previous?.dispose();
                  _initializeRearCamera();
                },
              )
            : controller == null || !controller.value.isInitialized
            ? const Center(
                child: GpaLoadingIndicator(
                  text: 'Abriendo cámara trasera...',
                  showText: true,
                ),
              )
            : Stack(
                fit: StackFit.expand,
                children: [
                  Center(child: CameraPreview(controller)),
                  Positioned(
                    top: 14,
                    left: 16,
                    right: 16,
                    child: IgnorePointer(
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 9,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: .58),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.camera_rear_outlined,
                              color: Colors.white,
                              size: 19,
                            ),
                            SizedBox(width: 7),
                            Text(
                              'Evidencia con cámara trasera',
                              style: TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 24,
                    child: Center(
                      child: Material(
                        color: Colors.white,
                        shape: const CircleBorder(),
                        child: InkWell(
                          customBorder: const CircleBorder(),
                          onTap: _takingPicture ? null : _capture,
                          child: SizedBox(
                            width: 76,
                            height: 76,
                            child: _takingPicture
                                ? const Padding(
                                    padding: EdgeInsets.all(22),
                                    child: CircularProgressIndicator(
                                      strokeWidth: 3,
                                      color: AppTheme.primary,
                                    ),
                                  )
                                : const Icon(
                                    Icons.camera_alt_rounded,
                                    size: 36,
                                    color: AppTheme.primary,
                                  ),
                          ),
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

class _CameraError extends StatelessWidget {
  const _CameraError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.camera_alt_outlined,
            color: Colors.white70,
            size: 54,
          ),
          const SizedBox(height: 14),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.white, fontSize: 15),
          ),
          const SizedBox(height: 18),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('Reintentar'),
          ),
        ],
      ),
    ),
  );
}

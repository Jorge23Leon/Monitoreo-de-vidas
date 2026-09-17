import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// En pantallas raíz evita que Android cierre la aplicación sin confirmar.
class ExitAppGuard extends StatefulWidget {
  const ExitAppGuard({super.key, required this.child});

  final Widget child;

  @override
  State<ExitAppGuard> createState() => _ExitAppGuardState();
}

class _ExitAppGuardState extends State<ExitAppGuard> {
  bool _dialogOpen = false;

  Future<void> _confirmExit() async {
    if (_dialogOpen || !mounted) return;
    _dialogOpen = true;
    final exit = await showDialog<bool>(
          context: context,
          barrierDismissible: false,
          builder: (dialogContext) => AlertDialog(
            title: const Text('Salir de la aplicación'),
            content: const Text(
              '¿Estás seguro de que quieres salir de la app?',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                child: const Text('Salir'),
              ),
            ],
          ),
        ) ??
        false;
    _dialogOpen = false;
    if (exit) await SystemNavigator.pop();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _confirmExit();
      },
      child: widget.child,
    );
  }
}

/// Para pantallas auxiliares abiertas desde Login: Atrás siempre vuelve a Login.
class BackToLoginGuard extends StatelessWidget {
  const BackToLoginGuard({
    super.key,
    required this.child,
    required this.onBackToLogin,
  });

  final Widget child;
  final VoidCallback onBackToLogin;

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) onBackToLogin();
      },
      child: child,
    );
  }
}

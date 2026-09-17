import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import 'auth_repository.dart';

class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key});

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  final first = TextEditingController();
  final last = TextEditingController();
  final user = TextEditingController();
  final email = TextEditingController();
  final pass = TextEditingController();
  bool busy = false;
  String? error;

  @override
  void dispose() {
    first.dispose();
    last.dispose();
    user.dispose();
    email.dispose();
    pass.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final fields = <(TextEditingController, String)>[
      (first, 'Nombre'),
      (last, 'Apellidos'),
      (user, 'Usuario'),
      (email, 'Correo'),
      (pass, 'Contraseña'),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Crear cuenta'),
        leading: BackButton(
          onPressed: () => AppScope.of(context).go(AppPage.login),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          for (final field in fields)
            Padding(
              padding: const EdgeInsets.only(bottom: 14),
              child: TextField(
                controller: field.$1,
                obscureText: field.$2 == 'Contraseña',
                keyboardType:
                    field.$2 == 'Correo' ? TextInputType.emailAddress : TextInputType.text,
                decoration: InputDecoration(labelText: field.$2),
              ),
            ),
          if (error != null) ...[
            Text(error!, style: const TextStyle(color: Colors.red)),
            const SizedBox(height: 12),
          ],
          FilledButton(
            onPressed: busy ? null : _submit,
            child: busy
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Registrarme'),
          ),
        ],
      ),
    );
  }

  Future<void> _submit() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await AuthRepository().signup(
        username: user.text.trim(),
        email: email.text.trim(),
        password: pass.text,
        firstName: first.text.trim(),
        lastName: last.text.trim(),
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Cuenta creada. Ya puedes iniciar sesión.')),
        );
        AppScope.of(context).go(AppPage.login);
      }
    } catch (e) {
      if (mounted) {
        setState(() => error = e.toString());
      }
    } finally {
      if (mounted) {
        setState(() => busy = false);
      }
    }
  }
}

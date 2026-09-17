import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/widgets/gpa_loading_indicator.dart';
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
  final confirmPass = TextEditingController();

  bool busy = false;
  bool showPassword = false;
  bool showConfirmPassword = false;
  String? error;

  @override
  void dispose() {
    first.dispose();
    last.dispose();
    user.dispose();
    email.dispose();
    pass.dispose();
    confirmPass.dispose();
    super.dispose();
  }

  InputDecoration _decoration(
    String label, {
    Widget? suffix,
  }) {
    return InputDecoration(
      labelText: label,
      filled: true,
      fillColor: Colors.white,
      suffixIcon: suffix,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: const BorderSide(color: Color(0xFFB9B9B9)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: const BorderSide(color: Color(0xFF3E7A27), width: 2),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFFBFCF6),
      appBar: AppBar(
        backgroundColor: const Color(0xFFFBFCF6),
        surfaceTintColor: Colors.transparent,
        title: const Text('Crear cuenta'),
        leading: BackButton(
          onPressed: () => AppScope.of(context).go(AppPage.login),
        ),
      ),
      body: ListView(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 30),
        children: [
          TextField(
            controller: first,
            enabled: !busy,
            textInputAction: TextInputAction.next,
            decoration: _decoration('Nombre'),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: last,
            enabled: !busy,
            textInputAction: TextInputAction.next,
            decoration: _decoration('Apellidos'),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: user,
            enabled: !busy,
            textInputAction: TextInputAction.next,
            autocorrect: false,
            enableSuggestions: false,
            decoration: _decoration('Usuario'),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: email,
            enabled: !busy,
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.next,
            autocorrect: false,
            decoration: _decoration('Correo'),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: pass,
            enabled: !busy,
            obscureText: !showPassword,
            textInputAction: TextInputAction.next,
            autocorrect: false,
            enableSuggestions: false,
            decoration: _decoration(
              'Contraseña',
              suffix: IconButton(
                tooltip: showPassword ? 'Ocultar contraseña' : 'Ver contraseña',
                onPressed: busy
                    ? null
                    : () => setState(() => showPassword = !showPassword),
                icon: Icon(
                  showPassword
                      ? Icons.visibility_off_outlined
                      : Icons.visibility_outlined,
                ),
              ),
            ),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: confirmPass,
            enabled: !busy,
            obscureText: !showConfirmPassword,
            textInputAction: TextInputAction.done,
            autocorrect: false,
            enableSuggestions: false,
            onSubmitted: (_) {
              if (!busy) _submit();
            },
            decoration: _decoration(
              'Confirmar contraseña',
              suffix: IconButton(
                tooltip: showConfirmPassword
                    ? 'Ocultar contraseña'
                    : 'Ver contraseña',
                onPressed: busy
                    ? null
                    : () => setState(
                          () => showConfirmPassword = !showConfirmPassword,
                        ),
                icon: Icon(
                  showConfirmPassword
                      ? Icons.visibility_off_outlined
                      : Icons.visibility_outlined,
                ),
              ),
            ),
          ),
          if (error != null) ...[
            const SizedBox(height: 12),
            Text(
              error!,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.red,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
          const SizedBox(height: 18),
          SizedBox(
            height: 56,
            child: FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF3E6F31),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(28),
                ),
              ),
              onPressed: busy ? null : _submit,
              child: busy
                  ? const GpaLoadingIndicator(
                      size: 28,
                      showText: false,
                    )
                  : const Text(
                      'Registrarme',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
            ),
          ),
          const SizedBox(height: 14),
          TextButton(
            onPressed: busy ? null : () => AppScope.of(context).go(AppPage.login),
            child: const Text(
              '¿Ya tienes cuenta? Inicia sesión',
              style: TextStyle(
                color: Color(0xFF3E7A27),
                fontWeight: FontWeight.w700,
                fontSize: 15,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();

    final username = user.text.trim();
    final emailValue = email.text.trim();
    final password = pass.text;
    final confirmation = confirmPass.text;

    if (first.text.trim().isEmpty ||
        last.text.trim().isEmpty ||
        username.isEmpty ||
        emailValue.isEmpty ||
        password.isEmpty ||
        confirmation.isEmpty) {
      setState(() => error = 'Completa todos los campos.');
      return;
    }

    if (password != confirmation) {
      setState(() => error = 'Las contraseñas no coinciden.');
      return;
    }

    setState(() {
      busy = true;
      error = null;
    });

    try {
      await AuthRepository().signup(
        username: username,
        email: emailValue,
        password: password,
        firstName: first.text.trim(),
        lastName: last.text.trim(),
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Cuenta creada. Ya puedes iniciar sesión.'),
          ),
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

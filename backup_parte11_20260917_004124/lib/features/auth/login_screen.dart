import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/storage/token_storage.dart';
import '../../core/theme/app_theme.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final userController = TextEditingController();
  final passwordController = TextEditingController();
  bool remember = false;
  bool showPassword = false;
  String? formError;

  @override
  void initState() {
    super.initState();
    _loadSavedCredentials();
  }

  @override
  void dispose() {
    userController.dispose();
    passwordController.dispose();
    super.dispose();
  }

  Future<void> _loadSavedCredentials() async {
    final credentials = await TokenStorage.instance.loadCredentials();
    if (!mounted) return;
    setState(() {
      userController.text = credentials.username;
      passwordController.text = credentials.password;
      remember = credentials.remember;
    });
  }

  Future<void> _login(AppController app) async {
    FocusScope.of(context).unfocus();

    final username = userController.text.trim();
    final password = passwordController.text;

    if (username.isEmpty || password.isEmpty) {
      setState(() {
        formError = username.isEmpty && password.isEmpty
            ? 'Ingresa tu usuario y contraseña.'
            : username.isEmpty
                ? 'Ingresa tu usuario.'
                : 'Ingresa tu contraseña.';
      });
      return;
    }

    setState(() => formError = null);
    await app.login(username, password, remember);
  }

  @override
  Widget build(BuildContext context) {
    final app = AppScope.of(context);

    return Scaffold(
      resizeToAvoidBottomInset: true,
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [AppTheme.backgroundA, AppTheme.backgroundB],
          ),
        ),
        child: SafeArea(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final compactHeight = constraints.maxHeight < 700;
              final compactWidth = constraints.maxWidth < 360;
              final compact = compactHeight || compactWidth;

              final screenHorizontalPadding = compactWidth ? 10.0 : 18.0;
              final screenVerticalPadding = compact ? 10.0 : 20.0;
              final cardHorizontalPadding = compact ? 18.0 : 24.0;
              final cardVerticalPadding = compact ? 14.0 : 22.0;
              final menuSize = compact ? 44.0 : 52.0;
              final logoContainerSize = compact ? 76.0 : 96.0;
              final logoSize = compact ? 54.0 : 68.0;
              final titleSize = compact ? 29.0 : 34.0;
              final subtitleSize = compact ? 15.0 : 16.0;
              final topContentPadding = compact ? 12.0 : 24.0;
              final titleTopSpace = compact ? 8.0 : 14.0;
              final subtitleTopSpace = compact ? 4.0 : 8.0;
              final formTopSpace = compact ? 18.0 : 30.0;
              final fieldGap = compact ? 12.0 : 18.0;
              final buttonTopSpace = compact ? 8.0 : 14.0;

              return SingleChildScrollView(
                keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                padding: EdgeInsets.symmetric(
                  horizontal: screenHorizontalPadding,
                  vertical: screenVerticalPadding,
                ),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 520),
                    child: Card(
                      margin: EdgeInsets.zero,
                      elevation: 8,
                      shadowColor: Colors.black26,
                      color: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(compact ? 22 : 30),
                      ),
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: cardHorizontalPadding,
                          vertical: cardVerticalPadding,
                        ),
                        child: Stack(
                          children: [
                            Align(
                              alignment: Alignment.topRight,
                              child: PopupMenuButton<String>(
                                tooltip: 'Menú',
                                onSelected: (value) {
                                  if (value == 'info') {
                                    app.go(AppPage.info);
                                  } else if (value == 'contact') {
                                    app.go(AppPage.contact);
                                  }
                                },
                                itemBuilder: (_) => const [
                                  PopupMenuItem(
                                    value: 'info',
                                    child: Text('Información de la app'),
                                  ),
                                  PopupMenuItem(
                                    value: 'contact',
                                    child: Text('Contacto y soporte'),
                                  ),
                                ],
                                child: Material(
                                  color: AppTheme.softGreen,
                                  shape: const CircleBorder(),
                                  child: SizedBox(
                                    width: menuSize,
                                    height: menuSize,
                                    child: Icon(
                                      Icons.menu_rounded,
                                      size: compact ? 28 : 32,
                                      color: const Color(0xFF23512C),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            Padding(
                              padding: EdgeInsets.only(top: topContentPadding),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  Container(
                                    width: logoContainerSize,
                                    height: logoContainerSize,
                                    decoration: const BoxDecoration(
                                      color: AppTheme.softGreen,
                                      shape: BoxShape.circle,
                                    ),
                                    alignment: Alignment.center,
                                    child: Image.asset(
                                      'assets/images/gpa.webp',
                                      width: logoSize,
                                      height: logoSize,
                                      fit: BoxFit.contain,
                                      semanticLabel: 'Logo Grupo GPA',
                                    ),
                                  ),
                                  SizedBox(height: titleTopSpace),
                                  Text(
                                    'Bienvenido',
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      fontSize: titleSize,
                                      height: 1.08,
                                      fontWeight: FontWeight.w700,
                                      color: AppTheme.darkGreen,
                                    ),
                                  ),
                                  SizedBox(height: subtitleTopSpace),
                                  Text(
                                    'Ingresa tus datos para continuar.',
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      fontSize: subtitleSize,
                                      color: const Color(0xFF6B6B6B),
                                    ),
                                  ),
                                  SizedBox(height: formTopSpace),
                                  _FieldLabel(
                                    text: 'Usuario',
                                    compact: compact,
                                  ),
                                  const SizedBox(height: 6),
                                  TextField(
                                    controller: userController,
                                    enabled: !app.busy,
                                    textInputAction: TextInputAction.next,
                                    autofillHints: const [AutofillHints.username],
                                    autocorrect: false,
                                    enableSuggestions: false,
                                    decoration: _fieldDecoration(
                                      hintText: 'Ingresa tu usuario',
                                      compact: compact,
                                    ),
                                  ),
                                  SizedBox(height: fieldGap),
                                  _FieldLabel(
                                    text: 'Contraseña',
                                    compact: compact,
                                  ),
                                  const SizedBox(height: 6),
                                  TextField(
                                    controller: passwordController,
                                    enabled: !app.busy,
                                    obscureText: !showPassword,
                                    textInputAction: TextInputAction.done,
                                    autofillHints: const [AutofillHints.password],
                                    autocorrect: false,
                                    enableSuggestions: false,
                                    onSubmitted: (_) {
                                      if (!app.busy) {
                                        _login(app);
                                      }
                                    },
                                    decoration: _fieldDecoration(
                                      hintText: 'Ingresa tu contraseña',
                                      compact: compact,
                                      suffix: TextButton(
                                        onPressed: app.busy
                                            ? null
                                            : () => setState(
                                                  () => showPassword = !showPassword,
                                                ),
                                        child: Text(
                                          showPassword ? 'Ocultar' : 'Ver',
                                          style: const TextStyle(
                                            color: Color(0xFF447A30),
                                            fontWeight: FontWeight.w700,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
                                  SizedBox(height: compact ? 2 : 4),
                                  Row(
                                    crossAxisAlignment: CrossAxisAlignment.center,
                                    children: [
                                      Checkbox(
                                        value: remember,
                                        onChanged: app.busy
                                            ? null
                                            : (value) => setState(
                                                  () => remember = value ?? false,
                                                ),
                                      ),
                                      Expanded(
                                        child: Text(
                                          'Recordar usuario y contraseña',
                                          style: TextStyle(
                                            fontSize: compact ? 13 : 14,
                                            color: const Color(0xFF355A3B),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                  TextButton(
                                    onPressed: app.busy
                                        ? null
                                        : () => app.go(AppPage.contact),
                                    child: Text(
                                      'Olvidé mi contraseña',
                                      style: TextStyle(
                                        color: const Color(0xFF3E7A27),
                                        fontWeight: FontWeight.w700,
                                        fontSize: compact ? 14 : 15,
                                      ),
                                    ),
                                  ),
                                  TextButton(
                                    onPressed: app.busy
                                        ? null
                                        : () => app.go(AppPage.signup),
                                    child: Text(
                                      '¿No tienes cuenta? Regístrate',
                                      textAlign: TextAlign.center,
                                      style: TextStyle(
                                        color: const Color(0xFF3E7A27),
                                        fontWeight: FontWeight.w700,
                                        fontSize: compact ? 14 : 15,
                                      ),
                                    ),
                                  ),
                                  if (formError != null || app.message != null) ...[
                                    const SizedBox(height: 4),
                                    Text(
                                      formError ?? app.message!,
                                      textAlign: TextAlign.center,
                                      style: TextStyle(
                                        color: Theme.of(context).colorScheme.error,
                                        fontSize: compact ? 13 : 14,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ],
                                  SizedBox(height: buttonTopSpace),
                                  SizedBox(
                                    width: double.infinity,
                                    height: compact ? 50 : 54,
                                    child: FilledButton(
                                      onPressed: app.busy ? null : () => _login(app),
                                      style: FilledButton.styleFrom(
                                        backgroundColor: const Color(0xFF3F7F22),
                                        disabledBackgroundColor:
                                            const Color(0xFF3F7F22).withValues(alpha: 0.55),
                                        shape: RoundedRectangleBorder(
                                          borderRadius: BorderRadius.circular(28),
                                        ),
                                      ),
                                      child: app.busy
                                          ? const SizedBox(
                                              width: 22,
                                              height: 22,
                                              child: CircularProgressIndicator(
                                                strokeWidth: 2.2,
                                                color: Colors.white,
                                              ),
                                            )
                                          : Text(
                                              'Iniciar sesión',
                                              style: TextStyle(
                                                color: Colors.white,
                                                fontSize: compact ? 17 : 19,
                                                fontWeight: FontWeight.w700,
                                              ),
                                            ),
                                    ),
                                  ),
                                  SizedBox(height: compact ? 6 : 10),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  InputDecoration _fieldDecoration({
    required String hintText,
    required bool compact,
    Widget? suffix,
  }) {
    final radius = BorderRadius.circular(compact ? 15 : 18);
    const borderColor = Color(0xFF767676);

    return InputDecoration(
      hintText: hintText,
      filled: true,
      fillColor: Colors.white,
      contentPadding: EdgeInsets.symmetric(
        horizontal: compact ? 16 : 18,
        vertical: compact ? 15 : 17,
      ),
      suffixIcon: suffix,
      suffixIconConstraints: const BoxConstraints(minWidth: 64),
      border: OutlineInputBorder(
        borderRadius: radius,
        borderSide: const BorderSide(color: borderColor, width: 1.3),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: radius,
        borderSide: const BorderSide(color: borderColor, width: 1.3),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: radius,
        borderSide: const BorderSide(color: AppTheme.primary, width: 2),
      ),
      disabledBorder: OutlineInputBorder(
        borderRadius: radius,
        borderSide: const BorderSide(color: Color(0xFFC6C6C6)),
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel({required this.text, required this.compact});

  final String text;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Text(
        text,
        style: TextStyle(
          fontSize: compact ? 16 : 17,
          fontWeight: FontWeight.w700,
          color: AppTheme.darkGreen,
        ),
      ),
    );
  }
}

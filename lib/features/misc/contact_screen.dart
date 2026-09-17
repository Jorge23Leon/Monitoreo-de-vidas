import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/app_controller.dart';

const _background = Color(0xFFF4F8F1);
const _darkGreen = Color(0xFF184F22);
const _mediumGreen = Color(0xFF2E7D32);
const _lightGreen = Color(0xFF77C36D);
const _mainText = Color(0xFF1B2C1E);
const _secondaryText = Color(0xFF5E6D61);

class ContactScreen extends StatelessWidget {
  const ContactScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _background,
      body: SafeArea(
        bottom: false,
        child: Stack(
          children: [
            ListView(
              padding: const EdgeInsets.fromLTRB(18, 18, 18, 28),
              children: [
                const _HeroCard(
                  title: 'Contacto y soporte',
                  subtitle:
                      'Comunícate con el equipo de soporte si necesitas ayuda con el uso de la aplicación.',
                ),
                const SizedBox(height: 18),
                const _ContactCard(
                  icon: Icons.headset_mic_rounded,
                  title: 'Soporte técnico',
                  description:
                      'Aquí podemos brindarte soporte técnico relacionado con el uso de la aplicación, errores de acceso, fallas en el registro de monitoreos o dudas sobre la captura de información.',
                ),
                _ContactCard(
                  icon: Icons.email_rounded,
                  title: 'Correo de soporte',
                  description: 'tierrainteligente2@gmail.com',
                  actionText: 'Toca para enviar correo',
                  onTap: () => _openSupportEmail(context),
                ),
                _ContactCard(
                  icon: Icons.call_rounded,
                  title: 'Teléfono Grupo GPA',
                  description: '445 131 9493',
                  actionText: 'Toca para abrir teléfono',
                  onTap: () => _openExternal(
                    context,
                    Uri(scheme: 'tel', path: '4451319493'),
                    'No se pudo abrir la aplicación de teléfono',
                  ),
                ),
                _ContactCard(
                  icon: Icons.location_on_rounded,
                  title: 'Ubicación actual',
                  description:
                      'Grupo GPA Puerto Interior\nPuerto Interior, Guanajuato, C.P. 36275',
                  actionText: 'Toca para abrir ubicación en Google Maps',
                  onTap: () => _openExternal(
                    context,
                    Uri.parse('https://maps.app.goo.gl/Lvnj8WqVarPMrNkb7?g_st=aw'),
                    'No se pudo abrir Google Maps',
                  ),
                ),
                const _ContactCard(
                  icon: Icons.chat_bubble_outline_rounded,
                  title: 'Recomendación al solicitar ayuda',
                  description:
                      'Cuando reportes un problema, incluye tu usuario, el tipo de error, la pantalla donde ocurrió y una breve descripción de lo que estabas realizando. Esto ayuda a dar seguimiento más rápido.',
                ),
                const SizedBox(height: 18),
                const Text(
                  'Redes sociales',
                  style: TextStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.w700,
                    color: _mainText,
                  ),
                ),
                const SizedBox(height: 14),
                const _SocialSection(),
              ],
            ),
            Positioned(
              top: 0,
              right: 0,
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Material(
                  color: Colors.white,
                  elevation: 2,
                  shadowColor: Colors.black26,
                  shape: const CircleBorder(),
                  child: IconButton(
                    tooltip: 'Cerrar',
                    onPressed: () => AppScope.of(context).go(AppPage.login),
                    icon: const Icon(Icons.close_rounded, color: _mainText),
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

class _HeroCard extends StatelessWidget {
  const _HeroCard({required this.title, required this.subtitle});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: const LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [_darkGreen, _mediumGreen, _lightGreen],
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x33000000),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.all(20),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 28,
                    height: 1.05,
                    fontWeight: FontWeight.w900,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  subtitle,
                  style: const TextStyle(
                    fontSize: 14,
                    height: 1.45,
                    color: Colors.white,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 14),
          Container(
            width: 90,
            height: 90,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.white.withValues(alpha: 0.16),
            ),
            alignment: Alignment.center,
            child: Image.asset(
              'assets/images/gpa.webp',
              width: 64,
              height: 64,
              fit: BoxFit.contain,
              semanticLabel: 'Grupo GPA',
            ),
          ),
        ],
      ),
    );
  }
}

class _ContactCard extends StatelessWidget {
  const _ContactCard({
    required this.icon,
    required this.title,
    required this.description,
    this.actionText,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String description;
  final String? actionText;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final content = Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: const BoxDecoration(
              color: Color(0xFFEAF5E8),
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: Icon(icon, size: 24, color: _mediumGreen),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    height: 1.2,
                    fontWeight: FontWeight.w700,
                    color: _mainText,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: const TextStyle(
                    fontSize: 14,
                    height: 1.45,
                    color: _mainText,
                  ),
                ),
                if (onTap != null) ...[
                  const SizedBox(height: 6),
                  Text(
                    actionText ?? 'Toca para abrir',
                    style: const TextStyle(fontSize: 12, color: _mediumGreen),
                  ),
                ],
              ],
            ),
          ),
          if (onTap != null) ...[
            const SizedBox(width: 8),
            const Padding(
              padding: EdgeInsets.only(top: 2),
              child: Icon(
                Icons.keyboard_arrow_right_rounded,
                color: _secondaryText,
              ),
            ),
          ],
        ],
      ),
    );

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 7),
      elevation: 3,
      shadowColor: Colors.black26,
      color: Colors.white,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: onTap == null
          ? content
          : InkWell(
              onTap: onTap,
              child: content,
            ),
    );
  }
}

class _SocialSection extends StatelessWidget {
  const _SocialSection();

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SocialButton(
          asset: 'assets/images/ic_social_facebook.png',
          label: 'Facebook',
          url: Uri.parse('https://www.facebook.com/gpamex'),
        ),
        _SocialButton(
          asset: 'assets/images/ic_social_instagram.png',
          label: 'Instagram',
          url: Uri.parse('https://www.instagram.com/gpamex?igsh=Y2tkdnUyNXk5Zjkw'),
        ),
        _SocialButton(
          asset: 'assets/images/ic_social_youtube.png',
          label: 'YouTube',
          url: Uri.parse('https://youtube.com/@gpamex?si=IGtYRT6i3Yaj78kP'),
        ),
      ],
    );
  }
}

class _SocialButton extends StatelessWidget {
  const _SocialButton({
    required this.asset,
    required this.label,
    required this.url,
  });

  final String asset;
  final String label;
  final Uri url;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: () => _openExternal(context, url, 'No se pudo abrir $label'),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Column(
          children: [
            Container(
              width: 68,
              height: 68,
              padding: const EdgeInsets.all(12),
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white,
                boxShadow: [
                  BoxShadow(
                    color: Color(0x33000000),
                    blurRadius: 8,
                    offset: Offset(0, 3),
                  ),
                ],
              ),
              child: Image.asset(asset, width: 40, height: 40, fit: BoxFit.contain),
            ),
            const SizedBox(height: 8),
            Text(
              label,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 13, color: _secondaryText),
            ),
          ],
        ),
      ),
    );
  }
}

Future<void> _openSupportEmail(BuildContext context) async {
  final uri = Uri(
    scheme: 'mailto',
    path: 'tierrainteligente2@gmail.com',
    queryParameters: const {
      'subject': 'Soporte - Tierra Inteligente',
      'body': 'Hola, necesito ayuda con la aplicación Tierra Inteligente.\n\nUsuario:\nDetalle del problema:',
    },
  );

  await _openExternal(
    context,
    uri,
    'No se encontró una aplicación para enviar correo',
  );
}

Future<void> _openExternal(
  BuildContext context,
  Uri uri,
  String errorMessage,
) async {
  try {
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(errorMessage)),
      );
    }
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(errorMessage)),
      );
    }
  }
}

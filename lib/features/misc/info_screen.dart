import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/app_controller.dart';

const _background = Color(0xFFF4F8F0);
const _darkGreen = Color(0xFF1B5224);
const _mediumGreen = Color(0xFF2F7D32);
const _lightGreen = Color(0xFF97CB74);
const _mainText = Color(0xFF1B2C1E);
const _secondaryText = Color(0xFF5E6D61);

class InfoScreen extends StatelessWidget {
  const InfoScreen({super.key});

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
              children: const [
                _HeroCard(
                  title: 'Información',
                  subtitle:
                      'Conoce el objetivo de la aplicación y el enfoque tecnológico de Grupo GPA.',
                ),
                SizedBox(height: 18),
                _InfoCard(
                  icon: Icons.info_rounded,
                  title: 'Descripción de la app',
                  text:
                      'Esta aplicación está diseñada para apoyar el monitoreo agrícola de plagas y enfermedades en cultivos. Permite registrar información en campo, consultar puntos de monitoreo, capturar datos técnicos y organizar evidencia para facilitar el seguimiento del cultivo.',
                ),
                _InfoCard(
                  icon: Icons.flag_rounded,
                  title: 'Objetivo de la aplicación',
                  text:
                      'El objetivo principal es apoyar a técnicos y productores en la identificación, registro y seguimiento de plagas y enfermedades, ayudando a mejorar la toma de decisiones mediante información ordenada, precisa y disponible para consulta.',
                ),
                _InfoCard(
                  icon: Icons.business_rounded,
                  title: 'Información de Grupo GPA',
                  text:
                      'Grupo GPA es una empresa mexicana con sede en León, Guanajuato. Se enfoca en investigación, desarrollo, innovación, producción y comercialización, con especialidad en la industria metalmecánica y en la fabricación de maquinaria de automatización.',
                ),
                _InfoCard(
                  icon: Icons.settings_rounded,
                  title: 'Experiencia e innovación',
                  text:
                      'GPA ha evolucionado desde soluciones de automatización hacia la fabricación de máquinas y componentes complejos. Su trabajo se relaciona con sectores como la industria automotriz, ferroviaria, aeroespacial, agroindustrial y metalmecánica.',
                ),
                _InfoCard(
                  icon: Icons.eco_rounded,
                  title: 'Relación con el sector agroindustrial',
                  text:
                      'Dentro del enfoque agroindustrial, la digitalización permite fortalecer el registro de datos en campo, el seguimiento de cultivos y el análisis técnico para mejorar procesos agrícolas. Esta aplicación forma parte de esa visión: usar tecnología para organizar información y apoyar decisiones en campo.',
                ),
                SizedBox(height: 18),
                Text(
                  'Redes sociales',
                  style: TextStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.w700,
                    color: _mainText,
                  ),
                ),
                SizedBox(height: 14),
                _SocialSection(),
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

class _InfoCard extends StatelessWidget {
  const _InfoCard({
    required this.icon,
    required this.title,
    required this.text,
  });

  final IconData icon;
  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 7),
      elevation: 3,
      shadowColor: Colors.black26,
      color: Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Padding(
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
                    text,
                    style: const TextStyle(
                      fontSize: 14,
                      height: 1.45,
                      color: _secondaryText,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
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

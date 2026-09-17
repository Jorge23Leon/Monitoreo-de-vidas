import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../theme/app_theme.dart';

class AppHeader extends StatelessWidget {
  const AppHeader({super.key, this.title, this.onBack});

  final String? title;
  final VoidCallback? onBack;

  static const _months = <String>[
    '',
    'enero',
    'febrero',
    'marzo',
    'abril',
    'mayo',
    'junio',
    'julio',
    'agosto',
    'septiembre',
    'octubre',
    'noviembre',
    'diciembre',
  ];

  String _dateLabel() {
    final now = DateTime.now();
    return '${now.day} de ${_months[now.month]} de ${now.year}';
  }

  @override
  Widget build(BuildContext context) {
    final app = AppScope.of(context);
    final user = app.user;

    return Material(
      color: Colors.white,
      elevation: 0,
      child: SafeArea(
        bottom: false,
        child: SizedBox(
          height: 88,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(4, 4, 10, 2),
            child: Stack(
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (onBack != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: IconButton(
                          tooltip: 'Volver',
                          onPressed: onBack,
                          icon: const Icon(Icons.arrow_back, size: 28),
                        ),
                      ),
                    Expanded(
                      child: Align(
                        alignment: Alignment.topLeft,
                        child: Image.asset(
                          'assets/images/logo_agroindustry.png',
                          width: onBack == null ? 178 : 156,
                          height: 58,
                          fit: BoxFit.contain,
                          alignment: Alignment.centerLeft,
                        ),
                      ),
                    ),
                    const SizedBox(width: 46),
                  ],
                ),
                Positioned(
                  left: onBack == null ? 84 : 112,
                  right: 44,
                  bottom: 4,
                  child: Text(
                    user == null
                        ? ''
                        : 'Bienvenido ${user.displayName} - ${_dateLabel()}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.left,
                    style: const TextStyle(
                      color: Color(0xFF6D6D6D),
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                Positioned(
                  right: 0,
                  top: 9,
                  child: PopupMenuButton<String>(
                    tooltip: 'Menú',
                    icon: const Icon(Icons.menu, color: Colors.black, size: 34),
                    color: Colors.white,
                    surfaceTintColor: Colors.white,
                    onSelected: (value) async {
                      if (value == 'profile') app.go(AppPage.profile);
                      if (value == 'monitoring') app.go(AppPage.monitoringList);
                      if (value == 'cia') app.go(AppPage.ciaSelection);
                      if (value == 'admin') app.go(AppPage.adminHome);
                      if (value == 'logout') {
                        final ok = await showDialog<bool>(
                              context: context,
                              builder: (context) => AlertDialog(
                                title: const Text('Cerrar sesión'),
                                content: const Text(
                                  '¿Estás seguro de que quieres cerrar sesión?',
                                ),
                                actions: [
                                  TextButton(
                                    onPressed: () => Navigator.pop(context, false),
                                    child: const Text('Cancelar'),
                                  ),
                                  FilledButton(
                                    onPressed: () => Navigator.pop(context, true),
                                    child: const Text('Cerrar sesión'),
                                  ),
                                ],
                              ),
                            ) ??
                            false;
                        if (ok) await app.logout();
                      }
                    },
                    itemBuilder: (_) => [
                      const PopupMenuItem(
                        value: 'profile',
                        child: _MenuEntry(
                          icon: Icons.person_outline,
                          label: 'Perfil del usuario',
                        ),
                      ),
                      const PopupMenuItem(
                        value: 'monitoring',
                        child: _MenuEntry(
                          icon: Icons.pest_control_outlined,
                          label: 'Monitoreos',
                        ),
                      ),
                      if (user?.isAdmin == true ||
                          user?.isManager == true ||
                          user?.isSupervisor == true)
                        const PopupMenuItem(
                          value: 'cia',
                          child: _MenuEntry(
                            icon: Icons.business_outlined,
                            label: 'Cambiar de CIA',
                          ),
                        ),
                      if (user?.isAdmin == true)
                        const PopupMenuItem(
                          value: 'admin',
                          child: _MenuEntry(
                            icon: Icons.build_outlined,
                            label: 'Panel de trabajo',
                          ),
                        ),
                      const PopupMenuDivider(),
                      const PopupMenuItem(
                        value: 'logout',
                        child: _MenuEntry(
                          icon: Icons.logout,
                          label: 'Cerrar sesión',
                          danger: true,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _MenuEntry extends StatelessWidget {
  const _MenuEntry({
    required this.icon,
    required this.label,
    this.danger = false,
  });

  final IconData icon;
  final String label;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final color = danger ? const Color(0xFFB3261E) : AppTheme.darkGreen;
    return Row(
      children: [
        Icon(icon, color: color, size: 20),
        const SizedBox(width: 10),
        Text(
          label,
          style: TextStyle(color: color, fontWeight: FontWeight.w700),
        ),
      ],
    );
  }
}

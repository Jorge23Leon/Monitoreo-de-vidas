import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/widgets/app_header.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final user = AppScope.of(context).user;
    final firstLetter = user == null || user.username.trim().isEmpty
        ? '?'
        : user.username.trim()[0].toUpperCase();

    return Scaffold(
      body: Column(
        children: [
          const AppHeader(title: 'Perfil'),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(18),
              children: [
                CircleAvatar(
                  radius: 42,
                  child: Text(
                    user != null && user.firstName.trim().isNotEmpty
                        ? user.firstName.trim()[0].toUpperCase()
                        : firstLetter,
                    style: const TextStyle(fontSize: 30),
                  ),
                ),
                const SizedBox(height: 14),
                Center(
                  child: Text(
                    user?.displayName ?? '',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                ),
                const SizedBox(height: 16),
                for (final row in <(String, String)>[
                  ('Usuario', user?.username ?? ''),
                  ('Correo', user?.email ?? ''),
                  ('Rol', user?.roleName ?? ''),
                  ('Nivel', '${user?.level ?? ''}'),
                ])
                  Card(
                    child: ListTile(
                      title: Text(row.$1),
                      subtitle: Text(row.$2),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

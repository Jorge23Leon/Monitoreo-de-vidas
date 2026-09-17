import 'package:flutter_test/flutter_test.dart';
import 'package:monitoreo_de_vidas_flutter/core/models/session_user.dart';

void main() {
  test('normaliza rol administrador', () {
    const user = SessionUser(
      id: '1',
      username: 'jorge',
      firstName: 'Jorge',
      lastName: '',
      email: '',
      roleName: 'Administrador',
      level: 5,
    );
    expect(user.isAdmin, isTrue);
    expect(user.canSeeAspersion, isTrue);
  });

  test('interpreta la respuesta real de users/me', () {
    final user = SessionUser.fromJson({
      'id': 'abc',
      'username': 'jorge',
      'email': 'jorge@example.com',
      'user_role': {'name': 'Gerente', 'level': 4},
      'requires_password_change': false,
      'individual': {
        'first_name': 'Jorge',
        'last_name': 'León',
      },
      'datacentrals': [
        {'id': 'cia-1', 'name': 'CIA 1'},
      ],
    });

    expect(user.displayName, 'Jorge León');
    expect(user.isManager, isTrue);
    expect(user.canSeeAspersion, isTrue);
    expect(user.dataCentrals.single['id'], 'cia-1');
  });
}

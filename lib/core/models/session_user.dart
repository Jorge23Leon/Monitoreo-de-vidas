class SessionUser {
  const SessionUser({
    required this.id,
    required this.username,
    required this.firstName,
    required this.lastName,
    required this.email,
    required this.roleName,
    required this.level,
    this.requiresPasswordChange = false,
    this.dataCentrals = const [],
  });

  final String id;
  final String username;
  final String firstName;
  final String lastName;
  final String email;
  final String roleName;
  final int level;
  final bool requiresPasswordChange;
  final List<Map<String, dynamic>> dataCentrals;

  factory SessionUser.fromJson(Map<String, dynamic> json) {
    // El backend actual de CIAGRO devuelve el rol dentro de `user_role`
    // y los datos personales dentro de `individual`.
    final role = json['user_role'] ?? json['role'];
    final individual = json['individual'];

    String roleName = '';
    int level = 1;
    if (role is Map) {
      roleName = (role['name'] ?? role['role_name'] ?? '').toString();
      level = int.tryParse('${role['level'] ?? 1}') ?? 1;
    } else {
      roleName = (json['role_name'] ?? role ?? '').toString();
      level = int.tryParse('${json['level'] ?? 1}') ?? 1;
    }

    String firstName = (json['first_name'] ?? '').toString();
    String lastName = (json['last_name'] ?? '').toString();
    String personalEmail = '';
    if (individual is Map) {
      firstName = (individual['first_name'] ?? firstName).toString();
      lastName = (individual['last_name'] ?? lastName).toString();
      personalEmail = (individual['personal_email'] ?? '').toString();
    }

    final rawDataCentrals = json['datacentrals'];
    final dataCentrals = rawDataCentrals is List
        ? rawDataCentrals
            .whereType<Map>()
            .map((item) => Map<String, dynamic>.from(item))
            .toList(growable: false)
        : const <Map<String, dynamic>>[];

    return SessionUser(
      id: (json['id'] ?? json['ext_id'] ?? '').toString(),
      username: (json['username'] ?? '').toString(),
      firstName: firstName,
      lastName: lastName,
      email: (json['email'] ?? personalEmail).toString(),
      roleName: roleName,
      level: level,
      requiresPasswordChange: json['requires_password_change'] == true,
      dataCentrals: dataCentrals,
    );
  }

  String get displayName {
    final fullName = [firstName, lastName]
        .where((value) => value.trim().isNotEmpty)
        .join(' ')
        .trim();
    return fullName.isEmpty ? username : fullName;
  }

  String get normalizedRole {
    var value = roleName.toLowerCase().trim();
    const from = 'áéíóú';
    const to = 'aeiou';
    for (var i = 0; i < from.length; i++) {
      value = value.replaceAll(from[i], to[i]);
    }
    value = value
        .replaceAll('_', ' ')
        .replaceAll('.', '')
        .replaceAll(RegExp(r'\s+'), ' ');

    if (<String>{
      'superadmin',
      'super admin',
      'super administrador',
      'admin',
      'administrador',
    }.contains(value)) {
      return 'admin';
    }
    if (value == 'gerente') return 'gerente';
    if (<String>{'ingy supervision', 'ing y supervision', 'supervisor'}.contains(value)) {
      return 'supervisor';
    }
    if (<String>{'tecnico', 'tecnicos'}.contains(value)) return 'tecnico';
    if (value == 'invitado') return 'invitado';
    return value;
  }

  bool get isAdmin => level >= 5 || normalizedRole == 'admin';
  bool get isManager => level == 4 || normalizedRole == 'gerente';
  bool get isSupervisor => level == 3 || normalizedRole == 'supervisor';
  bool get isTechnician => level == 2 || normalizedRole == 'tecnico';
  bool get isGuest => level <= 1 || normalizedRole == 'invitado';
  bool get canSeeAspersion => isAdmin || isManager;
}

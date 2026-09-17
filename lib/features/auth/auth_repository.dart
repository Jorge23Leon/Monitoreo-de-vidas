import '../../core/api/api_client.dart';
import '../../core/models/session_user.dart';
import '../../core/storage/token_storage.dart';

class AuthRepository {
  final _api = ApiClient.instance;

  Future<void> login(String username, String password) async {
    final response = await _api.publicDio.post('api/v1/auth/login/', data: {
      'username': username,
      'password': password,
    });
    final data = Map<String, dynamic>.from(response.data as Map);
    final access = data['access']?.toString();
    final refresh = data['refresh']?.toString();
    if (access == null || refresh == null) {
      throw Exception(data['detail']?.toString() ?? 'El servidor no devolvió los tokens de sesión.');
    }
    await TokenStorage.instance.saveTokens(access: access, refresh: refresh);
  }

  Future<SessionUser> me() async {
    final response = await _api.dio.get('api/v1/users/me/');
    return SessionUser.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<void> logout() async {
    final refresh = await TokenStorage.instance.refreshToken;
    if (refresh != null) {
      try { await _api.dio.post('api/v1/auth/logout/', data: {'refresh': refresh}); } catch (_) {}
    }
    await TokenStorage.instance.clearTokens();
  }

  Future<void> signup({required String username, required String email, required String password, required String firstName, required String lastName}) async {
    await _api.publicDio.post('api/v1/auth/signup/', data: {
      'username': username,
      'email': email,
      'password': password,
      'first_name': firstName,
      'last_name': lastName,
    });
  }
}

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TokenStorage {
  TokenStorage._();
  static final instance = TokenStorage._();
  static const _storage = FlutterSecureStorage();

  static const _accessKey = 'ciagro_access_token';
  static const _refreshKey = 'ciagro_refresh_token';
  static const _usernameKey = 'ciagro_saved_username';
  static const _passwordKey = 'ciagro_saved_password';
  static const _rememberKey = 'ciagro_remember_credentials';

  Future<String?> get accessToken => _storage.read(key: _accessKey);
  Future<String?> get refreshToken => _storage.read(key: _refreshKey);

  Future<void> saveTokens({required String access, required String refresh}) async {
    await _storage.write(key: _accessKey, value: access);
    await _storage.write(key: _refreshKey, value: refresh);
  }

  Future<void> saveAccessToken(String access) =>
      _storage.write(key: _accessKey, value: access);

  Future<void> clearTokens() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }

  Future<void> saveCredentials(String username, String password, bool remember) async {
    await _storage.write(key: _rememberKey, value: remember ? '1' : '0');
    if (remember) {
      await _storage.write(key: _usernameKey, value: username);
      await _storage.write(key: _passwordKey, value: password);
    } else {
      await _storage.delete(key: _usernameKey);
      await _storage.delete(key: _passwordKey);
    }
  }

  Future<({String username, String password, bool remember})> loadCredentials() async {
    final remember = (await _storage.read(key: _rememberKey)) == '1';
    return (
      username: remember ? (await _storage.read(key: _usernameKey) ?? '') : '',
      password: remember ? (await _storage.read(key: _passwordKey) ?? '') : '',
      remember: remember,
    );
  }
}

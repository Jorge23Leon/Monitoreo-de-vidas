import 'dart:async';
import 'package:dio/dio.dart';
import '../config.dart';
import '../storage/token_storage.dart';

class ApiClient {
  ApiClient._() {
    dio = Dio(BaseOptions(
      baseUrl: AppConfig.normalizedBaseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 45),
      sendTimeout: const Duration(seconds: 30),
      headers: {'Accept': 'application/json'},
    ));
    publicDio = Dio(BaseOptions(
      baseUrl: AppConfig.normalizedBaseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
    ));
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await TokenStorage.instance.accessToken;
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        final request = error.requestOptions;
        if (error.response?.statusCode != 401 || request.extra['retriedAfterRefresh'] == true) {
          return handler.next(error);
        }
        final refreshed = await _refreshAccessToken();
        if (!refreshed) return handler.next(error);
        final token = await TokenStorage.instance.accessToken;
        request.headers['Authorization'] = 'Bearer $token';
        request.extra['retriedAfterRefresh'] = true;
        try {
          final response = await dio.fetch<dynamic>(request);
          handler.resolve(response);
        } catch (_) {
          handler.next(error);
        }
      },
    ));
  }

  static final instance = ApiClient._();
  late final Dio dio;
  late final Dio publicDio;
  Future<bool>? _refreshInFlight;

  Future<bool> _refreshAccessToken() {
    _refreshInFlight ??= _doRefresh().whenComplete(() => _refreshInFlight = null);
    return _refreshInFlight!;
  }

  Future<bool> _doRefresh() async {
    final refresh = await TokenStorage.instance.refreshToken;
    if (refresh == null || refresh.isEmpty) return false;
    try {
      final response = await publicDio.post('api/v1/auth/refresh/', data: {'refresh': refresh});
      final access = response.data is Map ? response.data['access']?.toString() : null;
      if (access == null || access.isEmpty) return false;
      await TokenStorage.instance.saveAccessToken(access);
      return true;
    } catch (_) {
      await TokenStorage.instance.clearTokens();
      return false;
    }
  }

  static List<Map<String, dynamic>> unpackResults(dynamic data) {
    if (data is List) {
      return data.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
    }
    if (data is Map && data['results'] is List) {
      return (data['results'] as List).whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
    }
    return const [];
  }
}

class AppConfig {
  static const apiBaseUrl = String.fromEnvironment(
    'CIAGRO_API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8500/',
  );

  static String get normalizedBaseUrl {
    final value = apiBaseUrl.trim();
    return value.endsWith('/') ? value : '$value/';
  }
}

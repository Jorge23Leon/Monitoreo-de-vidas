class AppConfig {
  static const apiBaseUrl = String.fromEnvironment(
    'CIAGRO_API_BASE_URL',
    defaultValue: 'https://ciagro.bapta.mx/',
  );

  static String get normalizedBaseUrl {
    final value = apiBaseUrl.trim();
    return value.endsWith('/') ? value : '$value/';
  }
}

import 'package:flutter/material.dart';

class AppTheme {
  static const primary = Color(0xFF2F7D20);
  static const darkGreen = Color(0xFF183D20);
  static const softGreen = Color(0xFFEAF4E4);
  static const backgroundA = Color(0xFFF6FAF2);
  static const backgroundB = Color(0xFFE6F0DF);

  static ThemeData get light {
    final scheme = ColorScheme.fromSeed(
      seedColor: primary,
      brightness: Brightness.light,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: Colors.white,
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFFFAFAFA),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFD7DDD4)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFD7DDD4)),
        ),
      ),
    );
  }
}

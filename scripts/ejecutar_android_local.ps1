$ErrorActionPreference = "Stop"
flutter pub get
flutter run --dart-define=CIAGRO_API_BASE_URL=http://10.0.2.2:8500/

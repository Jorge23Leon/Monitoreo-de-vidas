#!/usr/bin/env bash
set -euo pipefail
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Este script debe ejecutarse en macOS porque iOS requiere Xcode."
  exit 1
fi
command -v flutter >/dev/null || { echo "Flutter no esta en PATH"; exit 1; }
command -v pod >/dev/null || { echo "CocoaPods no esta instalado"; exit 1; }
flutter pub get
cd ios
pod install --repo-update
cd ..
flutter doctor
flutter analyze
flutter test
echo "iOS preparado. Abre ios/Runner.xcworkspace o ejecuta flutter run desde VS Code."

$ErrorActionPreference = "Stop"
$Tunnel = "https://dramatically-von-boost-savannah.trycloudflare.com/"

Write-Host "== CIAGRO Flutter PARTE 4 ==" -ForegroundColor Green
Write-Host "Backend: $Tunnel"

flutter clean
flutter pub get
flutter devices
flutter run --dart-define=CIAGRO_API_BASE_URL=$Tunnel

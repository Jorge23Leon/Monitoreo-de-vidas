$ErrorActionPreference = "Stop"
Write-Host "== Flutter ==" -ForegroundColor Cyan
flutter --version
Write-Host "== Doctor ==" -ForegroundColor Cyan
flutter doctor -v
Write-Host "== Clean/Get ==" -ForegroundColor Cyan
flutter clean
flutter pub get
Write-Host "== Analyze ==" -ForegroundColor Cyan
flutter analyze
Write-Host "== Tests ==" -ForegroundColor Cyan
flutter test
Write-Host "== Devices ==" -ForegroundColor Cyan
flutter devices
Write-Host "Validacion terminada." -ForegroundColor Green

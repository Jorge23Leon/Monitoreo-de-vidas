$ErrorActionPreference = "Stop"
Write-Host "=== CIAGRO Monitoreo V3 ===" -ForegroundColor Green

$flutterCommand = Get-Command flutter -ErrorAction SilentlyContinue
if (-not $flutterCommand) {
    Write-Host "ERROR: Flutter no esta instalado o no esta en PATH." -ForegroundColor Red
    Write-Host "Instala Flutter estable y agrega C:\src\flutter\bin al PATH."
    exit 1
}

$flutterBin = Split-Path -Parent $flutterCommand.Source
$flutterRoot = Split-Path -Parent $flutterBin
$flutterRootForProperties = $flutterRoot -replace '\\', '/'

$sdkCandidates = @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    (Join-Path $env:LOCALAPPDATA "Android\Sdk")
) | Where-Object { $_ -and (Test-Path $_) }
$androidSdk = $sdkCandidates | Select-Object -First 1

$localProperties = @("flutter.sdk=$flutterRootForProperties")
if ($androidSdk) {
    $androidSdkForProperties = $androidSdk -replace '\\', '/'
    $localProperties += "sdk.dir=$androidSdkForProperties"
} else {
    Write-Host "AVISO: No encontre Android SDK. Instala Android Studio/SDK antes de ejecutar Android." -ForegroundColor Yellow
}
$localProperties | Set-Content -Path "android\local.properties" -Encoding utf8

flutter --version
flutter doctor
flutter pub get
flutter analyze
flutter test

Write-Host "" 
Write-Host "V3 preparada. Abre Run and Debug y elige 'CIAGRO V3 - Android emulador (backend local)'." -ForegroundColor Green

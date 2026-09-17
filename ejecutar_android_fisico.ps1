param(
    [Parameter(Mandatory=$true)]
    [string]$IpPc
)
$ErrorActionPreference = "Stop"
$url = "http://${IpPc}:8500/"
Write-Host "Backend: $url" -ForegroundColor Cyan
flutter pub get
flutter run --dart-define=CIAGRO_API_BASE_URL=$url

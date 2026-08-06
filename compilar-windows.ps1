$ErrorActionPreference = "Stop"

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectDir

$knownJavaHomes = @(
    $env:JAVA_HOME,
    "$env:ProgramFiles\Android\Android Studio\jbr",
    "$env:ProgramFiles\Android Studio\jbr",
    "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
) | Where-Object {
    $_ -and (Test-Path (Join-Path $_ "bin\java.exe"))
}

$javaHome = $knownJavaHomes | Select-Object -First 1

if (-not $javaHome) {
    $searchRoots = @(
        "$env:ProgramFiles\Android",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:LOCALAPPDATA\Programs",
        "$env:USERPROFILE\.jdks"
    ) | Where-Object { Test-Path $_ } | Select-Object -Unique

    foreach ($root in $searchRoots) {
        $javaExe = Get-ChildItem `
            -Path $root `
            -Filter java.exe `
            -File `
            -Recurse `
            -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\(jbr|jdk[^\\]*)\\bin\\java\.exe$' } |
            Select-Object -First 1

        if ($javaExe) {
            $javaHome = Split-Path (Split-Path $javaExe.FullName -Parent) -Parent
            break
        }
    }
}

if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    throw "No se encontró Java. En Android Studio selecciona Gradle JDK (jbr-17 o JDK 17) y vuelve a ejecutar este archivo."
}

$javaBin = Join-Path $javaHome "bin"
$env:JAVA_HOME = $javaHome
$env:Path = "$javaBin;$env:Path"

[Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$userPathParts = @($userPath -split ';' | Where-Object { $_ })
if ($javaBin -notin $userPathParts) {
    $updatedUserPath = (@($javaBin) + $userPathParts) -join ';'
    [Environment]::SetEnvironmentVariable("Path", $updatedUserPath, "User")
}

$androidSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
if (-not (Test-Path $androidSdk)) {
    throw "No se encontró el Android SDK en $androidSdk. Revisa su ubicación en Android Studio."
}

$sdkForGradle = $androidSdk.Replace('\', '/')
Set-Content `
    -Path (Join-Path $projectDir "local.properties") `
    -Value "sdk.dir=$sdkForGradle" `
    -Encoding ASCII

Write-Host "JAVA_HOME configurado en: $javaHome" -ForegroundColor Green
& (Join-Path $javaBin "java.exe") -version

& (Join-Path $projectDir "gradlew.bat") clean testDebugUnitTest assembleDebug
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Compilación terminada correctamente." -ForegroundColor Green
Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk"

# CIAGRO Monitoreo V3 â€” Flutter + Visual Studio Code

Esta es la **nueva versiÃ³n Flutter del proyecto Android de Monitoreo de vidas/CIAGRO**, preparada como proyecto completo para abrir directamente en **Visual Studio Code** y ejecutar en Android.

No necesitas ejecutar `flutter create`. La carpeta `android/`, Gradle, `MainActivity`, manifiesto, permisos y configuraciones de VS Code ya vienen incluidas.

## Versiones de base

- Flutter: 3.47.2 (stable)
- Dart: 3.13.x
- Android Gradle Plugin: 8.11.1
- Gradle: 8.13
- Kotlin Gradle Plugin: 2.2.20
- Java/JDK: 17
- Android minSdk: 24
- Application ID: `com.example.monitoreodeplagas`
- App: `3.0.0+3`

## 1. Abrir en Visual Studio Code

Descomprime el ZIP y abre **la carpeta `Monitoreo-de-vidas-Flutter-V3`**, no solamente `lib`.

TambiÃ©n puedes abrir directamente:

```text
CIAGRO-Monitoreo-V3.code-workspace
```

Instala las extensiones recomendadas de **Flutter** y **Dart** cuando VS Code las solicite.

## 2. Preparar el proyecto

Abre PowerShell en la raÃ­z del proyecto:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\preparar_vscode.ps1
```

El script NO ejecuta `flutter create`. Genera Ãºnicamente `android/local.properties` con las rutas de tu PC, verifica Flutter, descarga dependencias y ejecuta `flutter analyze` + `flutter test`.

TambiÃ©n puedes hacerlo manualmente:

```powershell
flutter --version
flutter doctor
flutter pub get
flutter analyze
flutter test
```

## 3. Backend local

Para el emulador Android, tu backend de la PC en `8500` se ve como:

```text
http://10.0.2.2:8500/
```

La configuraciÃ³n de VS Code **CIAGRO V3 - Android emulador (backend local)** ya manda esa URL con `--dart-define`.

Si usas un celular fÃ­sico, ejecuta con la IP LAN de tu computadora:

```powershell
flutter run --dart-define=CIAGRO_API_BASE_URL=http://192.168.X.X:8500/
```

Si usas Cloudflare Tunnel:

```powershell
flutter run --dart-define=CIAGRO_API_BASE_URL=https://TU-TUNEL.trycloudflare.com/
```

## 4. Ejecutar desde VS Code

1. Arranca un emulador Android o conecta tu celular con depuraciÃ³n USB.
2. En la barra inferior de VS Code selecciona el dispositivo Android.
3. Ve a **Run and Debug / Ejecutar y depurar**.
4. Selecciona **CIAGRO V3 - Android emulador (backend local)**.
5. Presiona **F5**.

TambiÃ©n puedes ejecutar:

```powershell
.\scripts\ejecutar_android_local.ps1
```

## 5. Validaciones

Desde VS Code o terminal:

```powershell
flutter analyze
flutter test
flutter build apk --debug --dart-define=CIAGRO_API_BASE_URL=http://10.0.2.2:8500/
```

Existe ademÃ¡s una validaciÃ³n de estructura que no requiere Flutter:

```powershell
python tool\validar_estructura.py
```

## Funcionalidad incluida

- Login JWT y refresh automÃ¡tico.
- SesiÃ³n y almacenamiento seguro.
- Perfil y roles.
- SelecciÃ³n de CIA.
- Pantalla de mÃ³dulos.
- Monitoreo de plagas/enfermedades: flujo offline-first alineado con Kotlin (listado, filtros, GPS libre, parcela, captura, plagas/enfermedades, evidencia, severidad, sincronizaciÃ³n, reporte y CSV).
- AspersiÃ³n: sesiones, detalle y mapa.
- NDVI: sesiones, detalle, mapa y escala de NDVI.
- Panel administrativo base.
- SQLite con las 24 tablas del esquema Room v39 original.
- Leaflet integrado localmente.
- Permisos Android de internet, ubicaciÃ³n y cÃ¡mara.

## Nota sobre una â€œversiÃ³n completaâ€

El **proyecto de compilaciÃ³n sÃ­ estÃ¡ completo**: puedes abrirlo en VS Code y no falta la envoltura Android. La app Kotlin original tiene 181 archivos Kotlin y lÃ³gica de negocio muy extensa. Esta entrega concentra la paridad funcional en **monitoreo de plagas y enfermedades**, que es el mÃ³dulo objetivo. AspersiÃ³n, NDVI y pantallas administrativas permanecen fuera del alcance de esta migraciÃ³n 1:1 y pueden seguir evolucionÃ¡ndose por separado.

## APK

DespuÃ©s de una compilaciÃ³n debug exitosa, normalmente estarÃ¡ en:

```text
build\app\outputs\flutter-apk\app-debug.apk
```


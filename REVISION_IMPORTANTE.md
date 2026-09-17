# Revisión importante — CIAGRO Monitoreo V3

## Qué estaba pasando con el ZIP anterior

La carpeta `Monitoreo-de-vidas-Flutter` que se estaba ejecutando era una primera migración. Tenía errores reales de sintaxis en Dart (paréntesis, corchetes, llaves y una cadena de texto sin cerrar) en archivos como `app.dart`, selección de CIA, listas remotas, monitoreo, aspersión, NDVI y pantallas misc. También conservaba un `widget_test.dart` de la plantilla de Flutter que buscaba una clase `MyApp` inexistente.

Eso explica los errores de `flutter analyze` y `flutter test`. No era un fallo de tu instalación de Flutter.

## Qué cambia en V3

Esta V3 usa archivos Dart formateados y separados por responsabilidades. Incluye:

- Login y refresh JWT.
- Lectura del backend real con `user_role` e `individual`.
- Selección de CIA.
- Monitoreos filtrados por la CIA seleccionada.
- Target points de monitoreo.
- Catálogo de plagas y enfermedades por cultivo.
- Plagas con etapas y cantidades.
- Enfermedades con No presente / Presente y fase.
- Registro `Sin plaga`.
- GPS y validación de distancia.
- Cámara / galería.
- Guardado local SQLite y cola offline.
- Sincronización de checkpoints y foto multipart.
- Estados `low`, `warning`, `critical` compatibles con el proyecto Kotlin.
- Estructura Android, iOS y VS Code.

## Validación hecha antes de entregar

Se comprobó:

- estructura de Android/Flutter/VS Code;
- referencias locales de archivos;
- balance de paréntesis `()`, corchetes `[]`, llaves `{}` y cadenas en todos los archivos Dart;
- que no queden referencias a `MyApp` ni valores antiguos `medium/high` en monitoreo;
- que la captura use `warning/critical` y soporte `SIN_PLAGA`.

La compilación final `flutter analyze`, `flutter test` y `flutter build apk` debe ejecutarse en tu PC porque ahí ya tienes Flutter 3.47.2 y Android SDK.

## Comandos que debes ejecutar

Abre **esta carpeta V3** en VS Code y, desde la raíz (donde está `pubspec.yaml`), ejecuta:

```powershell
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=CIAGRO_API_BASE_URL=http://10.0.2.2:8500/
```

Si `flutter doctor` todavía indica `cmdline-tools component is missing`, instala `Android SDK Command-line Tools (latest)` desde Android Studio > SDK Manager > SDK Tools y luego ejecuta:

```powershell
flutter doctor --android-licenses
```


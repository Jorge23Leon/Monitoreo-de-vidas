# Correcciones de compilación — 2026-09-07

Cambios aplicados sobre el ZIP exacto entregado por el usuario:

- Gradle Wrapper: 8.13 -> 8.14.3.
- Android compileSdk fijado en 37.
- Se conserva `android.newDsl=false` para evitar el cambio de DSL de AGP 9.
- Se añade `android.suppressUnsupportedCompileSdk=37` mientras el proyecto usa AGP 8.x.
- Eliminado `minLines: 2` de un widget `Text` en `monitoring_checkpoint_screen.dart`.
- Corregido `await` dentro del bloque try de `monitoring_repository.dart`.
- Limpiados avisos de analyzer por imports, underscores y escapes de comillas.

Validación local disponible en este entorno:

`python tool/validar_estructura.py` -> OK.

La validación definitiva de Flutter debe hacerse en Windows con Flutter 3.47.2:

```powershell
flutter clean
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=CIAGRO_API_BASE_URL=http://10.0.2.2:8500/
```

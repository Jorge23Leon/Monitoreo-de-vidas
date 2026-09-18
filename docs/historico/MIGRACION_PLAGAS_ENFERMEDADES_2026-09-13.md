# Migración Kotlin -> Flutter: registro de plagas y enfermedades

Revisión hecha contra:
- Android/Kotlin: `Monitoreo-de-vidas(20260913-183641)`
- Flutter base: `Monitoreo-de-vidas-Flutter-V3-COMPLETO(3)`
- Backend Django: `ciagro(10)`

## Alcance
Se revisó únicamente el flujo de monitoreo fitosanitario: mapa/GPS, creación de punto,
catálogo por cultivo, plagas, enfermedades, etapas/fases, cantidades, severidad,
comentarios, evidencia fotográfica, persistencia offline y sincronización con Django.

## Paridad funcional aplicada
- El punto se crea con GPS real; el toque del mapa nunca se usa como coordenada.
- Captura permitida solo con precisión <= 35 m y lectura reciente (15 s).
- Lecturas de mapa mayores a 60 m se descartan.
- Se hacen hasta 3 intentos de alta precisión y se conserva la mejor lectura.
- Seguimiento GPS continuo en el mapa mientras la pantalla está abierta.
- Validación de punto dentro del polígono de la parcela.
- El checkpoint conserva la coordenada GPS del target creado, igual al flujo Android actual.
- Catálogo filtrado por cultivo.
- No se inventan etapas cuando el backend no trae etapas reales.
- Enfermedades: No presente => qty=0, stage=null; Presente => qty=1 y fase obligatoria.
- Fases de enfermedad disponibles: Inicio, Desarrollo y Avanzado. Terminal se excluye de la captura nueva.
- Orden de plagas igual a Kotlin: Huevecillo -> Larva/Joven -> Pupa -> Adulto -> Adulto con alas.
- `huevecillo/huevesillo` se normaliza a `huevesillo`, que es el choice real del backend.
- También se normalizan ninfa, adulto_alas, inicio, desarrollo, avanzado y terminal.
- "Sin plaga" se sincroniza con phyto_issue=null, stage=null, qty=0, presence_status=low.
- Persistencia offline primero; target -> checkpoint -> foto -> estado de header al sincronizar.
- El header no se completa remotamente mientras queden targets/checkpoints pendientes.
- Se conserva remoteId del checkpoint antes de subir foto para evitar duplicados en reintentos.
- Evidencia convertida a JPG, orientación normalizada y límite real <= 340 KiB.
- Cámara trasera preferida.
- En iOS se solicita ubicación precisa temporal cuando el usuario dio precisión reducida.

## Backend verificado
Rutas usadas por Flutter coinciden con Django:
- `/api/v1/monitoring/phyto/target-points/create/`
- `/api/v1/monitoring/phyto/checkpoints/create/`
- `/api/v1/monitoring/phyto/checkpoints/<uuid>/update/`
- `/api/v1/monitoring/phyto/headers/<uuid>/update/`

Choices reales de `PhytoCheckPoint.stage` en backend:
`huevesillo`, `larva`, `ninfa`, `pupa`, `adulto`, `adulto_alas`, `inicio`, `desarrollo`, `avanzado`, `terminal`.

`presence_status`: `low`, `warning`, `critical`.

## Versiones recomendadas para esta entrega
Se prioriza compilación estable sobre una migración riesgosa a AGP 9 en el mismo cambio funcional.

- Flutter: >= 3.47.0
- Dart: >= 3.12.0 < 4.0.0
- Android compileSdk/targetSdk: 36
- Android minSdk: 24
- Java/JDK: 17 o 21 (bytecode Java/Kotlin 17)
- Android Gradle Plugin: 8.11.2
- Gradle wrapper: 8.14.3
- Kotlin Gradle Plugin: 2.2.21
- iOS deployment target: 13.0
- geolocator: 14.0.3
- image_picker: 1.2.3
- image: 4.9.2
- sqflite: 2.4.3
- dio: 5.11.1

Flutter puede advertir que AGP 8/Kotlin 2.2 serán retirados en una versión futura. Eso es una advertencia,
no el error que impedía compilar. AGP 9 cambia a Built-in Kotlin y conviene migrarlo en una rama separada
después de validar el flujo de campo y todos los plugins.

## Android + Kotlin al mismo tiempo
El build Debug de Flutter usa el sufijo `.flutterdev`, así que puede convivir con la APK Kotlin original
en el mismo teléfono para comparar pantalla por pantalla.

## iOS
Se agregaron permisos de cámara, fotos, ubicación When In Use y solicitud temporal de precisión completa.
El Podfile usa `BYPASS_PERMISSION_LOCATION_ALWAYS=1` porque la app no necesita ubicación permanente en background.

Para producción/App Store se recomienda usar API HTTPS y eliminar `NSAllowsArbitraryLoads` una vez que el backend
productivo tenga dominio TLS definitivo.

## Validación final en una PC con Flutter
```powershell
flutter --version
flutter doctor -v
flutter clean
flutter pub get
flutter analyze
flutter test
flutter devices
flutter run -d R5CY71CK38D --dart-define=CIAGRO_API_BASE_URL=http://192.168.1.157:8500/
```

La revisión en este entorno incluyó estructura, delimitadores Dart, rutas backend, modelos/serializers y permisos.
La compilación Flutter real debe hacerse en una máquina con el SDK de Flutter instalado.

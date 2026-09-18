# Parte 1 - Login, Información y Soporte

Actualización basada directamente en las pantallas Kotlin originales de `Monitoreo-de-vidas(20260913-183641)`.

## Alcance

Solo se modificó esta primera etapa visual/funcional:

- Login responsive.
- Información de la aplicación / Grupo GPA.
- Contacto y soporte.
- Acciones externas de correo, teléfono, Google Maps y redes sociales.

No se modificó en esta etapa el flujo de monitoreo, mapa, GPS, checkpoints ni sincronización.

## Archivos principales modificados

- `lib/features/auth/login_screen.dart`
- `lib/features/misc/info_screen.dart`
- `lib/features/misc/contact_screen.dart`
- `lib/features/misc/misc_screens.dart`
- `lib/app.dart`
- `pubspec.yaml`

## Dependencia agregada

```yaml
url_launcher: ^6.3.2
```

Se usa para abrir correo, teléfono, Google Maps, Facebook, Instagram y YouTube tanto en Android como en iOS.

## Prueba rápida

```powershell
flutter clean
flutter pub get
flutter analyze
flutter run -d R5CY71CK38D --dart-define=CIAGRO_API_BASE_URL=https://existing-gibson-device-urls.trycloudflare.com/
```

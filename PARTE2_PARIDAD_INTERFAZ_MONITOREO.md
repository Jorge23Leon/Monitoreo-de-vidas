# CIAGRO Monitoreo Flutter — Parte 2: paridad visual y flujo operativo

Fecha: 2026-09-13

Esta entrega es acumulativa sobre la Parte 1. Conserva Login, Información, Soporte y toda la lógica de monitoreo existente.

## Flujo principal ajustado

- Admin/Gerente/Supervisor: Login → CIA padre → CIA hija → Monitoreos fitosanitarios.
- Técnico/Invitado: Login → Monitoreos fitosanitarios.
- Se elimina el paso obligatorio por la pantalla genérica de módulos para este producto enfocado a plagas y enfermedades.
- Filtros progresivos: Productor → Rancho → Parcela.
- Filtros secundarios: Ciclo, fecha inicial, fecha final y Estado.
- Monitoreo completado → Reporte.
- Monitoreo activo → Mapa → Nuevo punto GPS → Registro de plagas/enfermedades.

## Cambios visuales

- Encabezado GPA Agroindustry alineado con Android/Kotlin.
- Selección de CIA en dos tarjetas numeradas, mensaje de estado, CIA preferente y botón Continuar.
- Lista de monitoreos con panel de filtros colapsable y tarjetas similares a Kotlin.
- Reporte con hero verde, estado, Productor/Cultivo/Rancho/Parcela, indicadores, mapa y botones CSV/Sincronizar.
- Mapa operativo con instrucciones, precisión GPS visible y acciones de monitoreo.
- Registro de punto con cabecera Punto/Parcela/Cultivo, tabs Plagas/Enfermedades y barra inferior de guardado.
- Mapa base cambiado a Esri World Imagery + etiquetas para aproximarse al mapa satelital de Kotlin.

## Reglas funcionales conservadas

- GPS real; tocar el mapa no altera la coordenada capturada.
- `LocationAccuracy.bestForNavigation`.
- Captura permitida con precisión de 35 m o mejor.
- Se escoge la mejor de hasta 3 lecturas para registrar.
- Validación dentro del polígono cuando existe.
- Punto local → checkpoints → evidencia → header durante sincronización.
- Evidencia fotográfica <= 340 KiB.
- Enfermedad No presente/Presente con fase obligatoria al estar presente.
- Guardado offline y posterior sincronización.
- Protección contra duplicación de checkpoints cuando falla la evidencia.

## Versiones

- Flutter >= 3.47.0 (probado por el usuario con 3.47.2)
- Dart >= 3.12.0 (usuario: 3.13.2)
- Android compile/target SDK 36
- minSdk 24
- AGP 8.11.x / Gradle 8.14.x / Kotlin 2.2.x

## Prueba recomendada

```powershell
flutter clean
flutter pub get
flutter analyze
flutter run -d R5CY71CK38D --dart-define=CIAGRO_API_BASE_URL=https://TU-TUNEL.trycloudflare.com/
```

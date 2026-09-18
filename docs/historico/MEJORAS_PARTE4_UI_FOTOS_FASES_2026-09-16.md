# PARTE 4 — UI, fotos de producción y fases

Base: `Monitoreo-de-vidas-Flutter-PARTE3-MAPA-INDICES-DETALLE-NOMBRES`.

## Cambios

- Filtros reorganizados para móvil: Productor, Rancho y Parcela en filas completas; Ciclo/Estado y fechas se adaptan al ancho; se eliminó el carrusel horizontal que recortaba campos.
- Catálogo fitosanitario con carga autenticada de imágenes usando la misma sesión JWT de CIAGRO.
- Compatibilidad con URLs relativas, attachments que responden JSON y URLs viejas de `trycloudflare`, localhost o Docker, reemplazándolas por el backend actual.
- Cache local de imágenes del catálogo para mejorar la segunda carga.
- Hidratación del detalle fitosanitario cuando el listado no trae fotos de etapas/fases.
- Fases de enfermedad Inicio, Desarrollo y Avanzado ahora se muestran como tarjetas con fotografía, nombre y color de estado, siguiendo la interfaz Android de referencia.
- Se mantiene la lógica de cantidades por etapa para plagas.
- Se eliminó la restricción al guardar que obligaba a permanecer a una distancia máxima del target. No se agregó separación mínima/máxima entre puntos de monitoreo.
- Se conserva la captura GPS precisa al crear un punto y la validación de que el GPS esté dentro de la parcela.

## Probar con el túnel actual

```powershell
cd "C:\Users\leons\Downloads\Monitoreo-de-vidas-Flutter-PARTE3-MAPA-INDICES-DETALLE-NOMBRES"
flutter clean
flutter pub get
flutter run --dart-define=CIAGRO_API_BASE_URL=https://dramatically-von-boost-savannah.trycloudflare.com/
```

Si tienes más de un dispositivo conectado, usa `flutter devices` y agrega `-d ID_DEL_DISPOSITIVO`.

# Corrección Parte 2 — carga, puntos y navegación

Versión: **3.1.1+5**

## Cambios

- `target-points` se consulta con `?header=<uuid>` en vez de descargar todos los puntos del servidor.
- El mapa operativo abre primero con los puntos guardados en SQLite y refresca desde la API en segundo plano.
- El polígono intenta cargar directamente el detalle de la parcela antes de recurrir al listado paginado completo.
- Las consultas independientes de la lista de monitoreos se ejecutan en paralelo.
- `visited` del backend se interpreta como punto capturado, además de `Completado` local.
- El reporte calcula Capturados también a partir de checkpoints reales, no solo del status del target.
- El contador Pendientes del reporte representa puntos aún no revisados; los pendientes de sincronización siguen mostrándose aparte.
- Los marcadores del mapa se muestran más grandes, numerados y con color: verde capturado, naranja pendiente.
- Si un target histórico no trae `geom` pero su checkpoint sí trae GPS, el reporte usa la coordenada del checkpoint.
- El botón Atrás en la lista vuelve a Selección de CIA para roles jerárquicos; técnico/invitado recibe confirmación antes de salir.

## Flujo de regreso

- Registro de punto → protege el punto sin guardar.
- Mapa → conserva la regla Kotlin para pausar/finalizar antes de salir.
- Reporte → lista de monitoreos.
- Lista de monitoreos → selección de CIA (Admin/Gerente/Supervisor).

# Parte 3 - Mapa, índices P/E, detalle y nombres

Versión: **3.1.2+6**

Cambios principales:

- El mapa del reporte usa marcadores divididos **P / E**, como Android/Kotlin.
- P muestra el índice/severidad de plagas y E el índice de enfermedades.
- Se puede abrir el mapa a pantalla completa desde el reporte.
- Al tocar un punto se abre una hoja de detalle con capturas, fase/presencia, cantidad, severidad, fecha, comentario y foto.
- El mapa compacto también permite tocar los puntos para ver su detalle.
- Se eliminan identificadores técnicos/códigos de la interfaz del flujo CIA -> monitoreo -> reporte. Los UUID siguen existiendo internamente para sincronizar, pero no se muestran al usuario.
- Los fallbacks de Productor, Rancho, Parcela y Monitoreo ya no imprimen IDs.

La lógica de backend, SQLite, sincronización, GPS y captura no se cambia en esta iteración.

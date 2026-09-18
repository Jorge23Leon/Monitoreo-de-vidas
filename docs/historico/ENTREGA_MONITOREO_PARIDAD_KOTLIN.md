# Entrega Flutter — Monitoreo de plagas y enfermedades

Esta versión toma el proyecto Android/Kotlin como referencia funcional para el módulo fitosanitario.

## Flujo implementado

- Login/sesión y navegación por roles.
- Selección CIA para roles administrativos/supervisión.
- Listado fitosanitario con filtros Productor → Rancho → Parcela → Estado.
- Listado operativo de técnico/invitado basado en permisos del token, sin forzar `assigned_to`.
- Caché offline por usuario para no mezclar monitoreos de sesiones distintas.
- Ventana offline aproximada de 30 días, protección de pendientes/en proceso y respaldo mínimo de 5 monitoreos recientes.
- Inicio del header únicamente al confirmar el primer punto.
- Creación libre de puntos usando GPS real del dispositivo.
- Precisión máxima de captura: 35 m.
- Validación del GPS dentro del polígono de la parcela cuando el polígono está disponible.
- Pausar / continuar / finalizar y cierre automático por fecha.
- Mínimo de un punto guardado para finalizar manualmente.
- Registro de plagas por etapa y cantidad.
- Registro de plagas sin etapas como presencia general cuando el catálogo realmente no tiene etapa utilizable.
- Registro de enfermedades como No presente / Presente.
- Enfermedad presente limitada a Inicio / Desarrollo / Avanzado.
- Registro Sin plaga.
- Evidencia por cámara o galería.
- Observaciones y metadatos de severidad por punto.
- Guardado SQLite offline en transacción atómica.
- IDs locales separados de IDs remotos.
- Sincronización en orden: Target → Checkpoint → Foto → estado final del header.
- Persistencia inmediata del ID remoto de checkpoint para no duplicar POST si falla la foto.
- El header remoto no se marca completed mientras existan targets/checkpoints/fotos pendientes.
- Reporte combinando información remota y local pendiente.
- Resolución del nombre/tipo fitosanitario remoto contra catálogo cuando la API devuelve solo el ID.
- Tabla: Punto, Plaga/Enfermedad, Tipo, Fase, Cantidad, Severidad del punto, Fecha, Imagen y Comentario.
- Exportación CSV.
- Navegación de mapa/reporte y bloqueo de regreso desde un punto sin guardar, como Kotlin.

## Validaciones realizadas en este entorno

- `python tool/validar_estructura.py` → OK.
- Imports relativos Dart → OK.
- XML de Android y JSON de VS Code → OK.
- Escaneo estructural de llaves/paréntesis/corchetes de todos los `.dart` → OK.

Este entorno no contiene el SDK de Flutter/Dart, por lo que la validación final debe ejecutarse en tu PC:

```powershell
flutter clean
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=CIAGRO_API_BASE_URL=http://10.0.2.2:8500/
```

Para un celular físico sustituye `10.0.2.2` por la IP LAN de la computadora que ejecuta el backend.

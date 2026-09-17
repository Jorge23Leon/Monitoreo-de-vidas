# PARTE5 - Filtros iguales a Android

Este parche modifica únicamente `lib/features/monitoring/monitoring_list_screen.dart` y está pensado para aplicarse encima de la versión PARTE3 a la que ya se le aplicó PARTE4.

## Resultado

Los filtros quedan en una sola columna, como la app Android:

1. Productor
2. Rancho
3. Parcela
4. Ciclo
5. Estado
6. Inicio
7. Fin
8. Limpiar filtros

No cambia la lógica de selección dependiente Productor -> Rancho -> Parcela, ni las fechas, estados, ciclos, refresco o listado de monitoreos. Solo corrige la distribución visual del panel.

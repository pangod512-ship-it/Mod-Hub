# Historial de cambios

## 1.1.0 estable

Disponible para Minecraft Forge 1.12.2, 1.16.5, 1.18.2, 1.19.2 y 1.20.1, con un JAR independiente para cada versión.

### Correcciones

- En 1.20.1, una coincidencia de Modrinth sin información de tipo ya no sustituye una estimación local válida por «Sin determinar».
- Los ports conservan las restricciones explícitas a un jugador. Un resultado de ese tipo ya no se interpreta como ausencia de datos ni se transforma en una estimación «Ambos» para multijugador. Las correcciones manuales y declaraciones locales mantienen su prioridad.
- En 1.20.1, los iconos se validan antes de crear texturas: hasta 4 MiB y 2048 × 2048 píxeles, con salida normalizada a 128 × 128 conservando proporción y transparencia. Si el archivo no es válido, se utiliza el icono sustituto.
- El respaldo preparado para CurseForge descarta entradas de caché inválidas y recupera los archivos pendientes; también reconstruye una caché sin tabla de entradas. CurseForge continúa desactivado como proveedor hasta disponer de acceso autorizado y validar su API real.

### Organización del código y pruebas

- El núcleo compartido reúne búsqueda, filtros, tipos, correcciones, ajustes y consultas externas; cada adaptador conserva la integración con su versión de Forge. El núcleo funciona con Java 8 y se incluye dentro de cada JAR.
- El modelo distingue una restricción a un jugador de un tipo desconocido, de modo que la clasificación y el respaldo respetan esa diferencia.
- La carga de iconos de 1.20.1 reutiliza el lector limitado del núcleo, que ahora admite recursos virtuales de Forge. Se evita mantener dos rutas de decodificación con límites distintos.
- Se añadieron regresiones para respuestas sin tipo, restricciones a un jugador, recuperación de cachés e imágenes inválidas o excesivas.
- Pasaron 54 comprobaciones del núcleo, 37 de clasificación y 50 de Modrinth. El JAR de 1.20.1 pasó 323 comprobaciones de interfaz con Forge 47.4.10. Los cinco JAR se probaron también en modpacks antes de publicarse.

## 1.0.0

Primera versión pública estable para Minecraft 1.20.1 con Forge: pantalla de mods, búsqueda, filtro por tipo, detalles, iconos, configuración compatible, correcciones manuales e identificación opcional mediante Modrinth con caché local.

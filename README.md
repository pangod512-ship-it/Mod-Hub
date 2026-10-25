# Mod Hub

Mod Hub mejora la pantalla de mods de Minecraft Forge: permite buscar por nombre, identificador o autor, filtrar por tipo y consultar versiones, iconos y descripciones.

- Paneles transparentes durante el juego y desplazamiento independiente de la lista y los detalles.
- Botones para abrir la carpeta de mods, configurar el mod seleccionado cuando ofrece una pantalla compatible y volver.
- Correcciones manuales del tipo de mod, guardadas por instancia.
- Identificación opcional mediante Modrinth, en segundo plano y con caché para trabajar sin conexión.
- Los tipos sin información suficiente aparecen como **Sin determinar**. Las estimaciones locales se marcan con `~`.

Autor: **pangod512**. Licencia: **Todos los derechos reservados**; consultar [LICENSE.txt](LICENSE.txt) y [CREDITS.txt](CREDITS.txt).

## Versiones

La versión pública **1.0.0** para Minecraft 1.20.1 permanece disponible en [GitHub Releases](https://github.com/pangod512-ship-it/Mod-Hub/releases/tag/v1.0.0).

La familia **1.1.1 está disponible como releases estables** para cinco versiones de Minecraft. Cada versión utiliza su propio JAR; no son intercambiables.

| Minecraft | Forge de compilación | Java para jugar | Estado |
|---|---|---|---|
| 1.12.2 | 14.23.5.2859 | 8 | Estable |
| 1.16.5 | 36.2.34 | 8 | Estable |
| 1.18.2 | 40.3.0 | 17 | Estable |
| 1.19.2 | 43.5.0 | 17 | Estable |
| 1.20.1 | 47.4.10 | 17 | Estable |

Los cinco JAR se probaron en modpacks antes de esta publicación. Fabric, NeoForge y Minecraft 1.21.1 no están incluidos.


## Descargas por versión de Minecraft

Cada versión tiene su release estable y su JAR. Las pre-releases anteriores se conservan como historial.

- **1.12.2:** [Mod Hub 1.1.1 - 1.12.2](https://github.com/pangod512-ship-it/Mod-Hub/releases/tag/v1.1.1-mc1.12.2-stable)
- **1.16.5:** [Mod Hub 1.1.1 - 1.16.5](https://github.com/pangod512-ship-it/Mod-Hub/releases/tag/v1.1.1-mc1.16.5-stable)
- **1.18.2:** [Mod Hub 1.1.1 - 1.18.2](https://github.com/pangod512-ship-it/Mod-Hub/releases/tag/v1.1.1-mc1.18.2-stable)
- **1.19.2:** [Mod Hub 1.1.1 - 1.19.2](https://github.com/pangod512-ship-it/Mod-Hub/releases/tag/v1.1.1-mc1.19.2-stable)
- **1.20.1:** [Mod Hub 1.1.1 - 1.20.1](https://github.com/pangod512-ship-it/Mod-Hub/releases/tag/v1.1.1-mc1.20.1-stable)

[Versión estable 1.0.0 para 1.20.1](https://github.com/pangod512-ship-it/Mod-Hub/releases/tag/v1.0.0).

Consulta las correcciones y los cambios de organización del código en [CHANGELOG.md](CHANGELOG.md).

## Instalar

1. Instala Forge para tu versión de Minecraft.
2. Coloca únicamente el JAR correspondiente en la carpeta `mods` de esa instancia.
3. Abre Minecraft y pulsa **Mods**.

Mod Hub funciona en el cliente y no necesita otra biblioteca del proyecto instalada. No activa, desactiva, actualiza ni elimina otros mods.

## Clasificación y privacidad

Las correcciones manuales tienen prioridad sobre las declaraciones explícitas del mod, seguidas de Modrinth y de la estimación local. Una categoría, dependencia o nombre no demuestra que un mod sea de cliente o servidor.

Modrinth viene activado de forma predeterminada. Puedes desactivarlo en la configuración de Mod Hub. Se envían huellas SHA-512 de archivos para buscar coincidencias, sin subir JAR, rutas personales ni identificadores de la cuenta. Solo se consultan archivos nuevos o resultados pendientes según la caché y los tiempos de reintento.

Los ajustes y las correcciones se guardan en `config/modhub-settings.json` y `config/modhub-overrides.json` de cada instancia. Las cachés se separan por proveedor y contexto de Minecraft/cargador. Se conserva el formato de los ajustes y correcciones de 1.0.0.

CurseForge **no está disponible como proveedor en estas versiones**. Su activación requiere acceso autorizado y verificar los datos explícitos de cliente/servidor de su API. No hay claves privadas incorporadas ni un servidor intermediario.

## Compilar

El núcleo está en `core/`, sin dependencias de Minecraft o Forge, y se compila y prueba con Java 8 y Gson 2.8.0. Cada adaptación incluye ese núcleo dentro de su JAR. La adaptación de 1.20.1 está en `src/main`; los demás ports, en `adapters/forge-<versión>`.

En Windows con PowerShell, instala JDK 8 y JDK 17 de Eclipse Temurin. El script permite indicar sus rutas mediante `MODHUB_JAVA8_HOME` y `MODHUB_JAVA17_HOME`, o mediante sus parámetros `-Java8Home` y `-Java17Home`:

```powershell
./tools/build-ports.ps1 -Target all
./tools/build-ports.ps1 -Target 1.12.2
```

Las dependencias oficiales se descargan en la primera compilación. Los wrappers fijan Gradle y cada adaptación fija ForgeGradle, Forge y mappings. Los JAR quedan en `adapters/forge-<versión>/build/libs/`; el de 1.20.1, en `build/multiversion/1.20.1/libs/`.

En otros sistemas, usa el wrapper de la adaptación con el JDK adecuado: Gradle 4.9/Java 8 para 1.12.2; Gradle 7.3.3/Java 17 y toolchain Java 8 para 1.16.5; Gradle 8.8/Java 17 para 1.18.2, 1.19.2 y 1.20.1. El comando `runClient` abre una instancia de desarrollo aislada.

Para comprobar el núcleo, usa el wrapper raíz con JDK 17 y un JDK 8 de Temurin disponible para la toolchain:

```powershell
./gradlew.bat -p core check
```

Los avisos de terceros del MDK se conservan en `third_party/forge-mdk/`. Minecraft y Forge pertenecen a sus respectivos autores; Mod Hub no es un producto oficial de Mojang Studios ni Microsoft.

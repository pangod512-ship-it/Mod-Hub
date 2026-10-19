# Mod Hub

**Minecraft Java 1.20.1 · Forge 47.4.10–47.x · Client only**

Created by **pangod512**. First stable public release: **1.0.0**.

Mod Hub replaces Forge's mod list with a searchable, transparent interface. Find installed mods, filter by type, read descriptions, open the mods folder and access a mod's configuration when available.

## Features

- Search by mod name, ID or author.
- Filter by Client, Server, Both or Undetermined.
- Scrollable list and details, mod logos, version and author information.
- Manual type corrections saved per instance.
- Optional Modrinth file identification with a persistent local cache.
- A vanilla-style interface that adapts to the window size.

Types describe compatibility and may be uncertain. They do not prove that a mod is required on both sides. This release does not enable, disable, update, download or export other mods.

## Install

1. Use Minecraft Java 1.20.1 with Forge 47.4.10 or a later 47.x version.
2. Close the game and place `modhub-1.0.0.jar` in your instance's `mods` folder.
3. Keep only one Mod Hub JAR installed. Start the game and open its Mods screen.

Install on the client only. No additional mod dependency is required. Fabric and NeoForge are not supported by this release. Compatibility across every Forge 47.x build is not individually certified.

## Modrinth requests and privacy

Modrinth identification is enabled by default for new instances and can be disabled in Mod Hub's configuration. A saved choice to disable it is respected.

When uncached files need identification, the mod sends SHA-512 file hashes and, when needed, public project identifiers to the Modrinth API. It does not upload JAR files or local file paths. As with other internet connections, the service receives the connection's IP address. Results are cached locally; cached results remain usable offline. Files that change are identified again when needed.

Modrinth is an external service; this project is not affiliated with or endorsed by Modrinth. See [Modrinth's privacy policy](https://modrinth.com/legal/privacy).

## Build from source

Use a JDK 17, configured through JAVA_HOME or PATH. An internet connection is needed for the first dependency download. Gradle is included through its wrapper; no separate MDK installation is needed.

Windows:

```powershell
.\gradlew.bat build
```

Linux/macOS:

```sh
sh ./gradlew build
```

Output: `build/libs/modhub-1.0.0.jar`.

## License and credits

All Rights Reserved. Public source access does not grant an open-source license. See `LICENSE.txt` for the permission to install and run official, unmodified releases and for redistribution restrictions. See `CREDITS.txt` for acknowledgements. Original Forge MDK notices are preserved in `third_party/forge-mdk/`.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

---

## Español

Mod Hub mejora la pantalla de mods de Forge con búsqueda, filtro por tipo, detalles y accesos a configuración. Se instala solo en el cliente con Minecraft 1.20.1 y Forge 47.4.10 o una versión posterior de la rama 47. No necesita otros mods.

Modrinth está activado por defecto para identificar archivos pendientes mediante su huella; se puede desactivar desde la configuración y sus resultados se guardan en caché. Los tipos son orientativos y permiten corrección manual. No se suben archivos JAR ni rutas personales a Modrinth.

Autor: **pangod512**. Licencia: **todos los derechos reservados**; consultar `LICENSE.txt`. Este proyecto no es un producto oficial de Minecraft.

package com.pangod512.modhub.client;

import com.pangod512.modhub.core.*;
import cpw.mods.niofs.union.UnionPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.jarjar.nio.pathfs.PathFileSystem;

/**
 * File identity only: this does not classify a mod or decide whether to export it. A nested
 * source's path is a virtual archive root, not an independent file to copy.
 */
public record ModSource(Kind kind, Optional<Path> path, String fileName, String location) {
  public enum Kind {
    INTEGRATED("Integrado"),
    JAR("Archivo JAR"),
    NESTED("Incluido en otro mod"),
    DEVELOPMENT("Carpeta de desarrollo"),
    UNKNOWN("No disponible");

    public final String label;

    Kind(String label) {
      this.label = label;
    }
  }

  public static ModSource from(IModInfo info) {
    if ("minecraft".equals(info.getModId()) || "forge".equals(info.getModId())) {
      return new ModSource(Kind.INTEGRATED, Optional.empty(), "Integrado", "");
    }
    try {
      if (info.getOwningFile() == null || info.getOwningFile().getFile() == null) return unknown();
      Path path = info.getOwningFile().getFile().getFilePath();
      if (path == null) return unknown();
      // Development mods can combine resource and class directories in a union root.
      for (int depth = 0;
          depth < 8 && path instanceof UnionPath union && path.equals(path.getRoot());
          depth++) {
        path = union.getFileSystem().getPrimaryPath();
      }
      if (path.getFileSystem() instanceof PathFileSystem nested) {
        Path entry = nested.getTarget();
        return new ModSource(Kind.NESTED, Optional.of(path), name(entry), describe(entry, 0));
      }
      if ("file".equalsIgnoreCase(path.getFileSystem().provider().getScheme())) {
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.isRegularFile(absolute)
            && name(absolute).toLowerCase(Locale.ROOT).endsWith(".jar")) {
          return new ModSource(
              Kind.JAR, Optional.of(absolute), name(absolute), absolute.toString());
        }
        if (Files.isDirectory(absolute)) {
          return new ModSource(
              Kind.DEVELOPMENT, Optional.of(absolute), name(absolute), absolute.toString());
        }
      }
    } catch (RuntimeException exception) {
      // An unavailable/virtual source must not prevent the mod list from opening.
      com.mojang.logging.LogUtils.getLogger()
          .debug("No se pudo identificar el archivo de {}", info.getModId(), exception);
    }
    return unknown();
  }

  private static ModSource unknown() {
    return new ModSource(Kind.UNKNOWN, Optional.empty(), "No disponible", "");
  }

  private static String name(Path path) {
    Path name = path.getFileName();
    return name == null || name.toString().isBlank() ? "Sin nombre" : name.toString();
  }

  private static String describe(Path path, int depth) {
    if (depth >= 16) return "Ubicación anidada";
    if (path instanceof UnionPath union) {
      return describe(union.getFileSystem().getPrimaryPath(), depth + 1)
          + (path.equals(path.getRoot()) ? "" : "!" + path);
    }
    if (path.getFileSystem() instanceof PathFileSystem nested) {
      return describe(nested.getTarget(), depth + 1)
          + (path.equals(path.getRoot()) ? "" : "!" + path);
    }
    return "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme())
        ? path.toAbsolutePath().normalize().toString()
        : path.toUri().toString();
  }
}

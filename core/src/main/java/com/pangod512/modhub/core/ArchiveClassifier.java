package com.pangod512.modhub.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads entry names only, never extracts archives or executes mod classes. */
public final class ArchiveClassifier {
  private static final Set<String> GAME_DATA =
      new HashSet<>(
          Arrays.asList(
              "recipes",
              "loot_tables",
              "advancements",
              "structures",
              "worldgen",
              "dimension",
              "dimension_type",
              "forge"));

  private ArchiveClassifier() {}

  public static ModClassification inspect(Path file, String id, boolean singleMod) {
    if (file == null || !Files.isRegularFile(file))
      return ModClassification.unknown("No hay archivo JAR legible.");
    Set<String> assets = new HashSet<>(), data = new HashSet<>(), gameplay = new HashSet<>();
    try (ZipFile zip = new ZipFile(file.toFile())) {
      Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
      int count = 0;
      while (entries.hasMoreElements()) {
        if (++count > 100000) throw new IOException("Demasiadas entradas");
        String[] parts = entries.nextElement().getName().replace('\\', '/').split("/", 4);
        if (parts.length >= 3 && parts[0].equals("assets")) assets.add(parts[1]);
        if (parts.length >= 4 && parts[0].equals("data")) {
          data.add(parts[1]);
          if (GAME_DATA.contains(parts[2])) gameplay.add(parts[1]);
        }
      }
    } catch (IOException | RuntimeException failure) {
      return ModClassification.unknown("No se pudo analizar el archivo.");
    }
    return classify(assets, data, gameplay, null, id, singleMod);
  }

  public static ModClassification classify(
      Set<String> assets,
      Set<String> data,
      Set<String> gameplay,
      String error,
      String id,
      boolean singleMod) {
    if (error != null) return ModClassification.unknown(error);
    if (gameplay.contains(id) || singleMod && !gameplay.isEmpty())
      return new ModClassification(
          ModType.BOTH,
          ModClassification.Origin.ESTIMATED,
          "Contiene datos de juego; tipo estimado, revisable manualmente.");
    return ModClassification.unknown(
        "Los recursos del archivo no permiten confirmar su compatibilidad de cliente/servidor.");
  }
}

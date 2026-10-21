package com.pangod512.modhub.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModrinthSettings {
  private final Path file;
  private volatile boolean enabled = true;
  private volatile boolean curseforgeEnabled;
  private String warning;

  public ModrinthSettings(Path file) {
    this.file = file;
    if (!Files.exists(file)) return;
    try {
      if (Files.size(file) > 4096) throw new IOException("Configuración demasiado grande");
      JsonObject root =
          new JsonParser()
              .parse(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (!root.get("modrinthEnabled").isJsonPrimitive()
          || !root.getAsJsonPrimitive("modrinthEnabled").isBoolean()) {
        throw new IOException("Opción no válida");
      }
      enabled = root.get("modrinthEnabled").getAsBoolean();
      curseforgeEnabled =
          root.has("curseforgeEnabled")
              && root.get("curseforgeEnabled").isJsonPrimitive()
              && root.getAsJsonPrimitive("curseforgeEnabled").isBoolean()
              && root.get("curseforgeEnabled").getAsBoolean();
    } catch (IOException | RuntimeException | StackOverflowError failure) {
      enabled = false;
      warning =
          "No se pudo leer la configuración. Las consultas están desactivadas hasta que elijas una"
              + " opción.";
    }
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean curseforgeEnabled() {
    return curseforgeEnabled;
  }

  public String warning() {
    return warning;
  }

  public void save(boolean value) throws IOException {
    JsonObject root = new JsonObject();
    root.addProperty("modrinthEnabled", value);
    root.addProperty("curseforgeEnabled", curseforgeEnabled);
    ModrinthCache.writeJson(file, root);
    enabled = value;
    warning = null;
  }

  public void saveCurseforge(boolean value) throws IOException {
    JsonObject root = new JsonObject();
    root.addProperty("modrinthEnabled", enabled);
    root.addProperty("curseforgeEnabled", value);
    ModrinthCache.writeJson(file, root);
    curseforgeEnabled = value;
    warning = null;
  }
}

package com.pangod512.modhub.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Per-instance manual choices. Reads never create or repair a user's file. */
public final class OverrideStore {
  public static final class Choice {
    private final ModType type;
    private final String version;

    @Override
    public boolean equals(Object value) {
      if (this == value) return true;
      if (!(value instanceof Choice)) return false;
      Choice other = (Choice) value;
      return java.util.Objects.equals(this.type, other.type)
          && java.util.Objects.equals(this.version, other.version);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(type, version);
    }

    public Choice(ModType type, String version) {
      this.type = type;
      this.version = version;
    }

    public ModType type() {
      return type;
    }

    public String version() {
      return version;
    }
  }

  private final Path file;
  private Map<String, Choice> choices = new HashMap<>();
  private boolean damaged;
  private String warning;

  public OverrideStore(Path file) {
    this.file = file;
    if (!Files.exists(file)) return;
    try {
      if (Files.size(file) > 1_048_576) throw new IOException("Archivo demasiado grande");
      JsonObject root =
          new JsonParser()
              .parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (!root.get("schema").isJsonPrimitive()
          || !root.getAsJsonPrimitive("schema").isNumber()
          || !root.get("schema").getAsString().equals("1"))
        throw new IOException("Versión de formato desconocida");
      Map<String, Choice> loaded = new HashMap<>();
      for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("overrides").entrySet()) {
        if (!entry.getKey().matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}"))
          throw new IOException("ID no válido");
        JsonObject value = entry.getValue().getAsJsonObject();
        ModType type = ModType.valueOf(value.get("type").getAsString());
        if (type == ModType.ALL) throw new IOException("Tipo no válido");
        if (!value.get("version").isJsonPrimitive()
            || !value.getAsJsonPrimitive("version").isString()) {
          throw new IOException("Versión del mod no válida");
        }
        loaded.put(entry.getKey(), new Choice(type, value.get("version").getAsString()));
      }
      choices = loaded;
    } catch (IOException | RuntimeException | StackOverflowError exception) {
      damaged = true;
      warning =
          "No se pudieron leer las correcciones. Se ignoraron; el archivo original se conserva.";
      CoreLog.warn("No se pudo leer {}", file, exception);
    }
  }

  public String warning() {
    return warning;
  }

  public Map<String, Choice> snapshot() {
    return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(choices));
  }

  public ModClassification apply(String id, String version, ModClassification automatic) {
    Choice choice = choices.get(id);
    if (choice == null) return automatic;
    String reason = "Corrección guardada por el usuario para este identificador de mod.";
    if (!choice.version().equals(version)) {
      reason +=
          " Aviso: se eligió con la versión "
              + choice.version()
              + "; ahora está instalada "
              + version
              + ". Revisa la elección.";
    }
    return new ModClassification(choice.type(), ModClassification.Origin.MANUAL, reason);
  }

  /** Null restores automatic detection. Memory changes only after a successful write. */
  public void save(String id, String version, ModType type) throws IOException {
    if (!id.matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}") || type == ModType.ALL) {
      throw new IOException("Corrección no válida");
    }
    Map<String, Choice> updated = new HashMap<>(choices);
    if (type == null) updated.remove(id);
    else updated.put(id, new Choice(type, version));
    JsonObject root = new JsonObject();
    root.addProperty("schema", 1);
    JsonObject overrides = new JsonObject();
    updated.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              JsonObject value = new JsonObject();
              value.addProperty("type", entry.getValue().type().name());
              value.addProperty("version", entry.getValue().version());
              overrides.add(entry.getKey(), value);
            });
    root.add("overrides", overrides);
    Path parent = file.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    if (damaged && Files.exists(file)) {
      Path backup =
          file.resolveSibling(
              file.getFileName() + ".corrupt-" + java.util.UUID.randomUUID() + ".bak");
      Files.copy(file, backup);
    }
    Path temporary = Files.createTempFile(parent, "modhub-overrides-", ".tmp");
    try {
      Files.write(
          temporary,
          new GsonBuilder()
              .setPrettyPrinting()
              .create()
              .toJson(root)
              .getBytes(StandardCharsets.UTF_8));
      AtomicFiles.replace(temporary, file);
    } finally {
      Files.deleteIfExists(temporary);
    }
    choices = updated;
    warning =
        damaged
            ? "Se guardó una copia del archivo dañado antes de escribir las nuevas correcciones."
            : null;
    damaged = false;
  }
}

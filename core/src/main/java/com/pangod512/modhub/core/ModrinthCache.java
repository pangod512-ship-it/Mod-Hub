package com.pangod512.modhub.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/** Results belong to file contents, never to a file name or a mod ID. */
public final class ModrinthCache {
  public static final long MISS_SECONDS = 7 * 24 * 60 * 60;

  public static final class Entry {
    private final String project;
    private final String version;
    private final String environment;
    private final boolean versionSpecific;
    private final long checkedAt;

    @Override
    public boolean equals(Object value) {
      if (this == value) return true;
      if (!(value instanceof Entry)) return false;
      Entry other = (Entry) value;
      return java.util.Objects.equals(this.project, other.project)
          && java.util.Objects.equals(this.version, other.version)
          && java.util.Objects.equals(this.environment, other.environment)
          && this.versionSpecific == other.versionSpecific
          && this.checkedAt == other.checkedAt;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(project, version, environment, versionSpecific, checkedAt);
    }

    public Entry(
        String project,
        String version,
        String environment,
        boolean versionSpecific,
        long checkedAt) {
      this.project = project;
      this.version = version;
      this.environment = environment;
      this.versionSpecific = versionSpecific;
      this.checkedAt = checkedAt;
    }

    public String project() {
      return project;
    }

    public String version() {
      return version;
    }

    public String environment() {
      return environment;
    }

    public boolean versionSpecific() {
      return versionSpecific;
    }

    public long checkedAt() {
      return checkedAt;
    }

    public boolean found() {
      return !project.isEmpty();
    }

    public boolean reusable(long now) {
      return found() && !environment.equals("unknown")
          || (now >= checkedAt && now - checkedAt < MISS_SECONDS);
    }
  }

  private final Path file;
  private final CompatibilityContext context;
  private final Map<String, Entry> entries = new HashMap<>();
  private long retryAfter;
  private boolean damaged;

  public ModrinthCache(Path file) {
    this(file, java.time.Instant.now().getEpochSecond());
  }

  public ModrinthCache(Path file, long now) {
    this(file, now, new CompatibilityContext("1.20.1", "forge"));
  }

  public ModrinthCache(Path file, long now, CompatibilityContext context) {
    this.file = file;
    this.context = context;
    if (!Files.exists(file)) return;
    try {
      if (Files.size(file) > 4_194_304) throw new IOException("Caché demasiado grande");
      JsonObject root =
          new JsonParser()
              .parse(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (root.has("context")
          ? !context.cacheKey().equals(root.get("context").getAsString())
          : !context.cacheKey().equals("1.20.1-forge")) return;
      if (!root.get("schema").getAsString().equals("1"))
        throw new IOException("Formato desconocido");
      long loadedRetry = root.get("retryAfter").getAsLong();
      Map<String, Entry> loaded = new HashMap<>();
      for (Map.Entry<String, com.google.gson.JsonElement> item :
          root.getAsJsonObject("entries").entrySet()) {
        if (!item.getKey().matches("[0-9a-f]{128}") || loaded.size() >= 5000)
          throw new IOException("Huella no válida");
        JsonObject value = item.getValue().getAsJsonObject();
        Entry entry =
            new Entry(
                value.get("project").getAsString(),
                value.get("version").getAsString(),
                value.get("environment").getAsString(),
                value.get("versionSpecific").getAsBoolean(),
                value.get("checkedAt").getAsLong());
        if ((!entry.project().isEmpty()
                && (!entry.project().matches("[a-zA-Z0-9]{8}")
                    || !entry.version().matches("[a-zA-Z0-9]{8}")))
            || entry.checkedAt() < 0
            || !ModrinthClient.ENVIRONMENTS.contains(entry.environment()))
          throw new IOException("Entrada no válida");
        loaded.put(item.getKey(), entry);
      }
      entries.putAll(loaded);
      // A bad clock or edited file must not disable queries indefinitely.
      retryAfter = Math.max(0, Math.min(loadedRetry, now + 86400));
    } catch (IOException | RuntimeException | StackOverflowError failure) {
      damaged = true;
      CoreLog.warn("Mod Hub: caché de Modrinth ignorada; se conserva el original: {}", file);
    }
  }

  public Entry get(String hash) {
    return entries.get(hash);
  }

  public long retryAfter() {
    return retryAfter;
  }

  public void deferUntil(long time) {
    retryAfter = time;
  }

  public void put(String hash, Entry entry) {
    if (entries.size() >= 5000 && !entries.containsKey(hash)) {
      String oldest =
          entries.entrySet().stream()
              .min(java.util.Comparator.comparingLong(e -> e.getValue().checkedAt()))
              .get()
              .getKey();
      entries.remove(oldest);
    }
    entries.put(hash, entry);
  }

  public void save() throws IOException {
    JsonObject root = new JsonObject(), data = new JsonObject();
    root.addProperty("schema", 1);
    root.addProperty("context", context.cacheKey());
    root.addProperty("retryAfter", retryAfter);
    entries.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            item -> {
              Entry entry = item.getValue();
              JsonObject value = new JsonObject();
              value.addProperty("project", entry.project());
              value.addProperty("version", entry.version());
              value.addProperty("environment", entry.environment());
              value.addProperty("versionSpecific", entry.versionSpecific());
              value.addProperty("checkedAt", entry.checkedAt());
              data.add(item.getKey(), value);
            });
    root.add("entries", data);
    if (damaged && Files.exists(file)) {
      Files.copy(
          file,
          file.resolveSibling(
              file.getFileName() + ".corrupt-" + java.util.UUID.randomUUID() + ".bak"));
      damaged = false;
    }
    writeJson(file, root);
  }

  static void writeJson(Path file, JsonObject root) throws IOException {
    Path parent = file.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, "modhub-", ".tmp");
    try {
      Files.write(
          temp,
          new GsonBuilder()
              .setPrettyPrinting()
              .create()
              .toJson(root)
              .getBytes(StandardCharsets.UTF_8));
      AtomicFiles.replace(temp, file);
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}

package com.pangod512.modhub.core;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.*;

/** Runs exclusively on the classification worker, publishes immutable snapshots. */
public final class ModrinthResolver {
  public static final class Target {
    private final String id;
    private final Path file;

    @Override
    public boolean equals(Object value) {
      if (this == value) return true;
      if (!(value instanceof Target)) return false;
      Target other = (Target) value;
      return java.util.Objects.equals(this.id, other.id)
          && java.util.Objects.equals(this.file, other.file);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(id, file);
    }

    public Target(String id, Path file) {
      this.id = id;
      this.file = file;
    }

    public String id() {
      return id;
    }

    public Path file() {
      return file;
    }
  }

  public static final class Snapshot {
    private final Map<String, ModClassification> classifications;
    private final Map<String, String> notes;
    private final String status;

    @Override
    public boolean equals(Object value) {
      if (this == value) return true;
      if (!(value instanceof Snapshot)) return false;
      Snapshot other = (Snapshot) value;
      return java.util.Objects.equals(this.classifications, other.classifications)
          && java.util.Objects.equals(this.notes, other.notes)
          && java.util.Objects.equals(this.status, other.status);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(classifications, notes, status);
    }

    public Snapshot(
        Map<String, ModClassification> classifications, Map<String, String> notes, String status) {
      this.classifications =
          java.util.Collections.unmodifiableMap(new java.util.HashMap<>(classifications));
      this.notes = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(notes));
      this.status = status;
    }

    public Map<String, ModClassification> classifications() {
      return classifications;
    }

    public Map<String, String> notes() {
      return notes;
    }

    public String status() {
      return status;
    }
  }

  private final Path cachePath;
  private final ModrinthClient client;
  private final LongSupplier clock;
  private final Consumer<Snapshot> publish;

  public ModrinthResolver(
      Path cachePath, ModrinthClient client, LongSupplier clock, Consumer<Snapshot> publish) {
    this.cachePath = cachePath;
    this.client = client;
    this.clock = clock;
    this.publish = publish;
  }

  public Snapshot resolve(List<Target> targets, BooleanSupplier online) {
    ModrinthCache cache = new ModrinthCache(cachePath, clock.getAsLong(), client.context());
    Map<String, List<String>> byHash = new LinkedHashMap<>();
    Map<String, ModClassification> classifications = new HashMap<>();
    Map<String, String> notes = new HashMap<>();
    // Read file bytes on every launch so same-name/same-size updates cannot reuse a wrong result.
    Map<Path, String> fingerprints = new HashMap<>();
    for (Target target : targets) {
      try {
        String hash = fingerprints.get(target.file());
        if (hash == null) {
          hash = fingerprint(target.file());
          fingerprints.put(target.file(), hash);
        }
        byHash.computeIfAbsent(hash, ignored -> new ArrayList<>()).add(target.id());
      } catch (IOException | RuntimeException failure) {
        notes.put(
            target.id(), "Modrinth: no se pudo leer el archivo. Se mantiene la detección local.");
      }
    }
    long now = clock.getAsLong();
    List<String> pending = new ArrayList<>();
    for (Map.Entry<String, List<String>> item : byHash.entrySet()) {
      ModrinthCache.Entry entry = cache.get(item.getKey());
      if (entry != null && entry.reusable(now))
        apply(entry, item.getValue(), classifications, notes, true);
      else pending.add(item.getKey());
    }
    String status =
        !online.getAsBoolean()
            ? "Consultas desactivadas; se utiliza la caché disponible."
            : pending.isEmpty()
                ? "Caché al día; no hacen falta consultas."
                : "Consultando los archivos nuevos o pendientes en Modrinth...";
    Set<String> queried = new HashSet<>();
    for (String hash : pending) {
      for (String id : byHash.get(hash)) notes.put(id, "Modrinth: " + status);
    }
    publish.accept(new Snapshot(classifications, notes, status));
    if (!pending.isEmpty() && online.getAsBoolean() && now >= cache.retryAfter()) {
      for (int offset = 0; offset < pending.size() && online.getAsBoolean(); offset += 100) {
        try {
          // At most two requests per batch, paced below the public per-IP API limit.
          if (offset > 0) Thread.sleep(1000);
          List<String> batch = pending.subList(offset, Math.min(offset + 100, pending.size()));
          Map<String, ModrinthCache.Entry> found = client.lookup(batch, clock.getAsLong());
          for (Map.Entry<String, ModrinthCache.Entry> item : found.entrySet()) {
            cache.put(item.getKey(), item.getValue());
            apply(item.getValue(), byHash.get(item.getKey()), classifications, notes, false);
            queried.add(item.getKey());
          }
          cache.deferUntil(0);
          boolean saved = persist(cache);
          publish.accept(
              new Snapshot(classifications, notes, "Actualizando los tipos desde Modrinth..."));
          status =
              saved
                  ? "Consulta terminada; resultados guardados en caché."
                  : "Tipos disponibles en esta sesión. No se pudo guardar la caché; revisa los"
                        + " permisos de config.";
        } catch (IOException failure) {
          long cooldown =
              failure instanceof ModrinthClient.ApiFailure
                  ? ((ModrinthClient.ApiFailure) failure).retrySeconds
                  : 900;
          cache.deferUntil(clock.getAsLong() + Math.max(60, Math.min(86400, cooldown)));
          persist(cache);
          status =
              "Modrinth no está disponible. Se conservan la caché y la detección local; se"
                  + " reintentará más adelante.";
          CoreLog.info("Mod Hub: consulta aplazada: {}", failure.getMessage());
          break;
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          status = "Consulta interrumpida; se conserva la detección local.";
          break;
        }
      }
    } else if (!pending.isEmpty() && online.getAsBoolean()) {
      status = "Consulta aplazada temporalmente tras un fallo de conexión o un límite de Modrinth.";
    }
    if (!online.getAsBoolean()) status = "Consultas desactivadas; se utiliza la caché disponible.";
    for (String hash : pending) {
      if (!queried.contains(hash)) {
        for (String id : byHash.get(hash))
          notes.put(id, "Modrinth: " + status + " Se mantiene la detección local.");
      }
    }
    Snapshot result = new Snapshot(classifications, notes, status);
    publish.accept(result);
    return result;
  }

  private static void apply(
      ModrinthCache.Entry entry,
      List<String> ids,
      Map<String, ModClassification> values,
      Map<String, String> notes,
      boolean cached) {
    for (String id : ids) {
      if (entry.found()) {
        values.put(id, ModrinthClient.classification(entry));
        notes.put(
            id,
            "Modrinth: "
                + (cached
                    ? "resultado guardado en caché."
                    : "archivo identificado en esta consulta."));
      } else
        notes.put(
            id,
            "Modrinth: no se encontró una coincidencia compatible exacta. Se mantiene la detección"
                + " local; se revisará después de 7 días.");
    }
  }

  private boolean persist(ModrinthCache cache) {
    try {
      cache.save();
      return true;
    } catch (IOException | RuntimeException failure) {
      // Never claim persistence when it failed. Keep the usable result for this session.
      CoreLog.warn("Mod Hub: no se pudo guardar la caché de Modrinth: {}", failure.getMessage());
      return false;
    }
  }

  public static String fingerprint(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-512");
      try (java.io.InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted()) throw new IOException("Lectura interrumpida");
          digest.update(buffer, 0, count);
        }
      }
      return Io.hex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}

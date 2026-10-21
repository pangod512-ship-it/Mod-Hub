package com.pangod512.modhub.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Independent, contextual CF cache and cooldown. Never queries for a type already found by
 * Modrinth.
 */
public final class CurseForgeFallback {
  private final CurseForgeClient client;
  private final Path cacheFile;
  private final CompatibilityContext context;

  public CurseForgeFallback(CurseForgeClient client, Path config, CompatibilityContext context) {
    this.client = client;
    this.context = context;
    cacheFile = config.resolve("modhub-curseforge-cache-" + context.cacheKey() + ".json");
  }

  public Map<String, ModClassification> resolve(
      List<ModrinthResolver.Target> targets,
      Map<String, ModClassification> primary,
      boolean enabled,
      long now) {
    Map<String, ModClassification> result = new HashMap<>();
    JsonObject root = new JsonObject(), entries = new JsonObject();
    long retryAfter = 0;
    if (Files.exists(cacheFile))
      try {
        if (Files.size(cacheFile) > 4194304) throw new IOException("Caché demasiado grande");
        JsonObject loaded =
            new JsonParser()
                .parse(
                    new String(
                        Files.readAllBytes(cacheFile), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        if ("curseforge".equals(loaded.get("provider").getAsString())
            && context.cacheKey().equals(loaded.get("context").getAsString())) {
          entries = loaded.getAsJsonObject("entries");
          retryAfter = Math.min(now + 86400, loaded.get("retryAfter").getAsLong());
        }
      } catch (IOException | RuntimeException | StackOverflowError ignored) {
        entries = new JsonObject();
      }
    List<ModrinthResolver.Target> pending = new ArrayList<>();
    Map<String, String> hashes = new HashMap<>();
    for (ModrinthResolver.Target target : targets) {
      if (!ClassificationPolicy.needsFallback(primary.get(target.id()))) continue;
      try {
        String hash = ModrinthResolver.fingerprint(target.file());
        hashes.put(target.id(), hash);
        JsonObject cached = entries.has(hash) ? entries.getAsJsonObject(hash) : null;
        if (cached != null
            && now >= cached.get("checkedAt").getAsLong()
            && now - cached.get("checkedAt").getAsLong() < 604800) {
          ModType type = ModType.valueOf(cached.get("type").getAsString());
          if (type == ModType.ALL) throw new IllegalArgumentException("Tipo no válido");
          result.put(
              target.id(),
              new ModClassification(
                  type,
                  ModClassification.Origin.CURSEFORGE,
                  "Resultado de la caché de CurseForge."));
        } else pending.add(target);
      } catch (IOException | RuntimeException ignored) {
        /* Local classification remains available. */
      }
    }
    if (!enabled || client == null || now < retryAfter || pending.isEmpty()) return result;
    for (int i = 0; i < pending.size(); i += 100)
      try {
        if (i > 0) Thread.sleep(1000);
        List<ModrinthResolver.Target> batch = pending.subList(i, Math.min(i + 100, pending.size()));
        Map<String, ModClassification> found = client.lookup(batch, context);
        for (ModrinthResolver.Target target : batch) {
          ModClassification value = found.get(target.id());
          if (value == null)
            value = ModClassification.unknown("Sin tipo confirmado en CurseForge.");
          result.put(target.id(), value);
          JsonObject cached = new JsonObject();
          cached.addProperty("checkedAt", now);
          cached.addProperty("type", value.type().name());
          entries.add(hashes.get(target.id()), cached);
        }
      } catch (IOException failure) {
        retryAfter =
            now
                + Math.max(
                    60,
                    Math.min(
                        86400,
                        failure instanceof ModrinthClient.ApiFailure
                            ? ((ModrinthClient.ApiFailure) failure).retrySeconds
                            : 900));
        break;
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
      }
    root.addProperty("provider", "curseforge");
    root.addProperty("context", context.cacheKey());
    root.addProperty("retryAfter", retryAfter);
    root.add("entries", entries);
    if (entries.entrySet().size() > 5000) {
      List<String> keys = new ArrayList<>();
      for (Map.Entry<String, JsonElement> entry : entries.entrySet()) keys.add(entry.getKey());
      final JsonObject cacheEntries = entries;
      Collections.sort(
          keys,
          Comparator.comparingLong(
              key -> {
                try {
                  return cacheEntries.getAsJsonObject(key).get("checkedAt").getAsLong();
                } catch (RuntimeException bad) {
                  return Long.MIN_VALUE;
                }
              }));
      for (int i = 0; i < keys.size() - 5000; i++) entries.remove(keys.get(i));
    }
    try {
      ModrinthCache.writeJson(cacheFile, root);
    } catch (IOException | RuntimeException ignored) {
      /* Do not discard usable in-memory results. */
    }
    return result;
  }
}

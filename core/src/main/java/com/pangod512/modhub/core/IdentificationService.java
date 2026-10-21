package com.pangod512.modhub.core;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Loader-independent background identification, settings and immutable publication. */
public final class IdentificationService {
  private final Path config;
  private final CompatibilityContext context;
  private final String version;
  private final ModrinthSettings settings;
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "ModHub-identification");
            t.setDaemon(true);
            return t;
          });
  private volatile ModrinthResolver.Snapshot snapshot =
      new ModrinthResolver.Snapshot(
          Collections.emptyMap(), Collections.emptyMap(), "Preparando la identificación...");
  private List<ModrinthResolver.Target> targets = Collections.emptyList();
  private CompletableFuture<?> task;
  private boolean rerun;

  public IdentificationService(Path config, CompatibilityContext context, String version) {
    this.config = config;
    this.context = context;
    this.version = version;
    settings = new ModrinthSettings(config.resolve("modhub-settings.json"));
  }

  public ModrinthSettings settings() {
    return settings;
  }

  public ModrinthResolver.Snapshot snapshot() {
    return snapshot;
  }

  public void start(List<ModrinthResolver.Target> files) {
    targets = Collections.unmodifiableList(new ArrayList<>(files));
    refresh();
  }

  public synchronized void refresh() {
    if (task != null && !task.isDone()) {
      rerun = true;
      return;
    }
    task =
        CompletableFuture.runAsync(
            () -> {
              try {
                Path cache =
                    config.resolve(
                        context.cacheKey().equals("1.20.1-forge")
                            ? "modhub-modrinth-cache.json"
                            : "modhub-modrinth-cache-" + context.cacheKey() + ".json");
                ModrinthResolver resolver =
                    new ModrinthResolver(
                        cache,
                        ModrinthClient.production(version, context),
                        () -> java.time.Instant.now().getEpochSecond(),
                        value -> snapshot = value);
                ModrinthResolver.Snapshot primary = resolver.resolve(targets, settings::enabled);
                // The CF transport remains absent until access and environment metadata are
                // validated.
                Map<String, ModClassification> fallback = Collections.emptyMap();
                if (CurseForgeAccess.available())
                  fallback =
                      new CurseForgeFallback(null, config, context)
                          .resolve(
                              targets,
                              primary.classifications(),
                              settings.curseforgeEnabled(),
                              java.time.Instant.now().getEpochSecond());
                Map<String, ModClassification> combined = new HashMap<>(primary.classifications());
                for (Map.Entry<String, ModClassification> entry : fallback.entrySet())
                  if (ClassificationPolicy.needsFallback(combined.get(entry.getKey())))
                    combined.put(entry.getKey(), entry.getValue());
                snapshot =
                    new ModrinthResolver.Snapshot(combined, primary.notes(), primary.status());
              } catch (RuntimeException failure) {
                snapshot =
                    new ModrinthResolver.Snapshot(
                        snapshot.classifications(),
                        snapshot.notes(),
                        "Identificación incompleta; se conserva la detección local.");
                CoreLog.warn("Mod Hub: identificación incompleta", failure);
              } finally {
                synchronized (this) {
                  task = null;
                  if (rerun) {
                    rerun = false;
                    refresh();
                  }
                }
              }
            },
            worker);
  }
}

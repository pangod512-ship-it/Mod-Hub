package com.pangod512.modhub.client;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.*;

/** Runs exclusively on the classification worker, publishes immutable snapshots. */
public final class ModrinthResolver {
    public record Target(String id, Path file) {}
    public record Snapshot(Map<String, ModClassification> classifications, Map<String, String> notes, String status) {
        public Snapshot { classifications = Map.copyOf(classifications); notes = Map.copyOf(notes); }
    }
    private final Path cachePath;
    private final ModrinthClient client;
    private final LongSupplier clock;
    private final Consumer<Snapshot> publish;
    public ModrinthResolver(Path cachePath, ModrinthClient client, LongSupplier clock, Consumer<Snapshot> publish) {
        this.cachePath = cachePath; this.client = client; this.clock = clock; this.publish = publish;
    }

    public Snapshot resolve(List<Target> targets, BooleanSupplier online) {
        ModrinthCache cache = new ModrinthCache(cachePath, clock.getAsLong());
        Map<String, List<String>> byHash = new LinkedHashMap<>();
        Map<String, ModClassification> classifications = new HashMap<>();
        Map<String, String> notes = new HashMap<>();
        // Read file bytes on every launch so same-name/same-size updates cannot reuse a wrong result.
        Map<Path, String> fingerprints = new HashMap<>();
        for (Target target : targets) {
            try {
                String hash = fingerprints.get(target.file());
                if (hash == null) { hash = fingerprint(target.file()); fingerprints.put(target.file(), hash); }
                byHash.computeIfAbsent(hash, ignored -> new ArrayList<>()).add(target.id());
            } catch (IOException | RuntimeException failure) {
                notes.put(target.id(), "Modrinth: no se pudo leer el archivo. Se mantiene la detección local.");
            }
        }
        long now = clock.getAsLong();
        List<String> pending = new ArrayList<>();
        for (var item : byHash.entrySet()) {
            ModrinthCache.Entry entry = cache.get(item.getKey());
            if (entry != null && entry.reusable(now)) apply(entry, item.getValue(), classifications, notes, true);
            else pending.add(item.getKey());
        }
        String status = !online.getAsBoolean() ? "Consultas desactivadas; se utiliza la caché disponible."
                : pending.isEmpty() ? "Caché al día; no hacen falta consultas."
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
                    for (var item : found.entrySet()) {
                        cache.put(item.getKey(), item.getValue());
                        apply(item.getValue(), byHash.get(item.getKey()), classifications, notes, false);
                        queried.add(item.getKey());
                    }
                    cache.deferUntil(0);
                    boolean saved = persist(cache);
                    publish.accept(new Snapshot(classifications, notes, "Actualizando los tipos desde Modrinth..."));
                    status = saved ? "Consulta terminada; resultados guardados en caché."
                            : "Tipos disponibles en esta sesión. No se pudo guardar la caché; revisa los permisos de config.";
                } catch (IOException failure) {
                    long cooldown = failure instanceof ModrinthClient.ApiFailure api ? api.retrySeconds : 900;
                    cache.deferUntil(clock.getAsLong() + Math.max(60, Math.min(86400, cooldown)));
                    persist(cache);
                    status = "Modrinth no está disponible. Se conservan la caché y la detección local; se reintentará más adelante.";
                    com.mojang.logging.LogUtils.getLogger().info("Mod Hub: consulta aplazada: {}", failure.getMessage());
                    break;
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt(); status = "Consulta interrumpida; se conserva la detección local."; break;
                }
            }
        } else if (!pending.isEmpty() && online.getAsBoolean()) {
            status = "Consulta aplazada temporalmente tras un fallo de conexión o un límite de Modrinth.";
        }
        if (!online.getAsBoolean()) status = "Consultas desactivadas; se utiliza la caché disponible.";
        for (String hash : pending) {
            if (!queried.contains(hash)) {
                for (String id : byHash.get(hash)) notes.put(id, "Modrinth: " + status + " Se mantiene la detección local.");
            }
        }
        Snapshot result = new Snapshot(classifications, notes, status);
        publish.accept(result); return result;
    }

    private static void apply(ModrinthCache.Entry entry, List<String> ids, Map<String, ModClassification> values,
                              Map<String, String> notes, boolean cached) {
        for (String id : ids) {
            if (entry.found()) {
                values.put(id, ModrinthClient.classification(entry));
                notes.put(id, "Modrinth: " + (cached ? "resultado guardado en caché." : "archivo identificado en esta consulta."));
            } else notes.put(id, "Modrinth: no se encontró una coincidencia compatible exacta. Se mantiene la detección local; se revisará después de 7 días.");
        }
    }
    private boolean persist(ModrinthCache cache) {
        try { cache.save(); return true; }
        catch (IOException | RuntimeException failure) {
            // Never claim persistence when it failed. Keep the usable result for this session.
            com.mojang.logging.LogUtils.getLogger().warn("Mod Hub: no se pudo guardar la caché de Modrinth: {}", failure.getMessage());
            return false;
        }
    }

    public static String fingerprint(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Lectura interrumpida");
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

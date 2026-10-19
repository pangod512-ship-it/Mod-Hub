package com.pangod512.modhub.client;

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
    public record Entry(String project, String version, String environment, boolean versionSpecific, long checkedAt) {
        public boolean found() { return !project.isEmpty(); }
        public boolean reusable(long now) { return found() || (now >= checkedAt && now - checkedAt < MISS_SECONDS); }
    }
    private final Path file;
    private final Map<String, Entry> entries = new HashMap<>();
    private long retryAfter;
    private boolean damaged;

    public ModrinthCache(Path file) {
        this(file, java.time.Instant.now().getEpochSecond());
    }
    public ModrinthCache(Path file, long now) {
        this.file = file;
        if (!Files.exists(file)) return;
        try {
            if (Files.size(file) > 4_194_304) throw new IOException("Caché demasiado grande");
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.get("schema").getAsString().equals("1")) throw new IOException("Formato desconocido");
            long loadedRetry = root.get("retryAfter").getAsLong();
            Map<String, Entry> loaded = new HashMap<>();
            for (var item : root.getAsJsonObject("entries").entrySet()) {
                if (!item.getKey().matches("[0-9a-f]{128}") || loaded.size() >= 5000) throw new IOException("Huella no válida");
                JsonObject value = item.getValue().getAsJsonObject();
                Entry entry = new Entry(value.get("project").getAsString(), value.get("version").getAsString(),
                        value.get("environment").getAsString(), value.get("versionSpecific").getAsBoolean(), value.get("checkedAt").getAsLong());
                if ((!entry.project().isEmpty() && (!entry.project().matches("[a-zA-Z0-9]{8}")
                        || !entry.version().matches("[a-zA-Z0-9]{8}"))) || entry.checkedAt() < 0
                        || !ModrinthClient.ENVIRONMENTS.contains(entry.environment())) throw new IOException("Entrada no válida");
                loaded.put(item.getKey(), entry);
            }
            entries.putAll(loaded);
            // A bad clock or edited file must not disable queries indefinitely.
            retryAfter = Math.max(0, Math.min(loadedRetry, now + 86400));
        } catch (IOException | RuntimeException | StackOverflowError failure) {
            damaged = true;
            com.mojang.logging.LogUtils.getLogger().warn("Mod Hub: caché de Modrinth ignorada; se conserva el original: {}", file);
        }
    }

    public Entry get(String hash) { return entries.get(hash); }
    public long retryAfter() { return retryAfter; }
    public void deferUntil(long time) { retryAfter = time; }
    public void put(String hash, Entry entry) {
        if (entries.size() >= 5000 && !entries.containsKey(hash)) {
            String oldest = entries.entrySet().stream().min(java.util.Comparator.comparingLong(e -> e.getValue().checkedAt()))
                    .orElseThrow().getKey();
            entries.remove(oldest);
        }
        entries.put(hash, entry);
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject(), data = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("retryAfter", retryAfter);
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(item -> {
            Entry entry = item.getValue();
            JsonObject value = new JsonObject();
            value.addProperty("project", entry.project()); value.addProperty("version", entry.version());
            value.addProperty("environment", entry.environment()); value.addProperty("versionSpecific", entry.versionSpecific());
            value.addProperty("checkedAt", entry.checkedAt()); data.add(item.getKey(), value);
        });
        root.add("entries", data);
        if (damaged && Files.exists(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt-" + java.util.UUID.randomUUID() + ".bak"));
            damaged = false;
        }
        writeJson(file, root);
    }

    static void writeJson(Path file, JsonObject root) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "modhub-", ".tmp");
        try {
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException failure) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}

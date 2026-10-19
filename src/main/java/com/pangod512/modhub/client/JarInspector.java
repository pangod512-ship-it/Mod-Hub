package com.pangod512.modhub.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

/** Lists archive entries on a worker thread; never loads classes or extracts files. */
public final class JarInspector {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ModHub-classification");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<String, CompletableFuture<Signals>> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> GAME_DATA = Set.of("recipes", "loot_tables", "advancements", "structures",
            "worldgen", "dimension", "dimension_type", "forge");

    private static final class Signals {
        final Set<String> assets = new HashSet<>(), data = new HashSet<>(), gameplay = new HashSet<>();
        int entries;
        String error;
        void accept(String name) throws IOException {
            if (++entries > 100_000) throw new IOException("Demasiadas entradas");
            String[] parts = name.replace('\\', '/').split("/", 4);
            if (parts.length >= 3 && parts[0].equals("assets")) assets.add(parts[1]);
            if (parts.length >= 4 && parts[0].equals("data")) {
                data.add(parts[1]);
                if (GAME_DATA.contains(parts[2])) gameplay.add(parts[1]);
            }
        }
    }

    private JarInspector() {}

    public static CompletableFuture<ModClassification> analyze(ModRow row) {
        if (row.source().path().isEmpty()) {
            return CompletableFuture.completedFuture(ModClassification.unknown("No hay un archivo legible para analizar."));
        }
        Path path = row.source().path().get();
        String key;
        try {
            key = path.toUri() + "|" + Files.getLastModifiedTime(path) + "|"
                    + (row.source().kind() == ModSource.Kind.JAR ? Files.size(path) : 0);
        } catch (IOException | RuntimeException exception) {
            return CompletableFuture.completedFuture(ModClassification.unknown("No se pudo acceder al archivo del mod."));
        }
        return CACHE.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> scan(row), WORKER))
                .thenApply(signals -> estimate(signals, row));
    }

    private static Signals scan(ModRow row) {
        ModSource source = row.source();
        Signals signals = new Signals();
        try {
            Path path = source.path().orElseThrow();
            if (source.kind() == ModSource.Kind.JAR) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (!entry.isDirectory()) signals.accept(entry.getName());
                    }
                }
            } else if (source.kind() == ModSource.Kind.NESTED) {
                // The identity path is a virtual archive file. Forge provides the content
                // through its already-open SecureJar resource root, which must not be closed here.
                Path root = row.info().getOwningFile().getFile().findResource();
                walk(root, root, signals);
            } else {
                walk(path, path, signals);
            }
        } catch (IOException | RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("No se pudo analizar el archivo {}", source.fileName(), exception);
            signals.error = "No se pudo completar el análisis del archivo; revisa el tipo manualmente.";
        }
        return signals;
    }

    private static void walk(Path root, Path start, Signals signals) throws IOException {
        try (var paths = Files.walk(start, 32)) {
            var iterator = paths.iterator();
            int visited = 0;
            while (iterator.hasNext()) {
                if (++visited > 100_000) throw new IOException("Demasiadas rutas");
                Path entry = iterator.next();
                if (Files.isRegularFile(entry)) signals.accept(root.relativize(entry).toString());
            }
        }
    }

    private static ModClassification estimate(Signals signals, ModRow row) {
        if (signals.error != null) return ModClassification.unknown(signals.error);
        String id = row.info().getModId();
        var owner = row.info().getOwningFile();
        boolean oneMod = owner != null && owner.getMods() != null && owner.getMods().size() == 1;
        if (signals.gameplay.contains(id) || oneMod && !signals.gameplay.isEmpty()) {
            return new ModClassification(ModType.BOTH, ModClassification.Origin.ESTIMATED,
                    "Contiene recetas, botín u otros datos de juego. Se propone Ambos; requiere confirmación antes de exportar.");
        }
        if ((signals.assets.contains(id) || oneMod && !signals.assets.isEmpty()) && signals.data.isEmpty()) {
            return ModClassification.unknown(
                    "Contiene recursos visuales sin datos de juego, pero esto también ocurre en bibliotecas y mods de servidor. Esa señal sola no permite decidir Solo cliente.");
        }
        return ModClassification.unknown(signals.data.isEmpty()
                ? "No hay señales suficientes. Las referencias a clases de cliente no determinan el lado de un mod."
                : "Los datos son ambiguos, solo etiquetas o pertenecen a otro mod del mismo JAR. Revisa el tipo manualmente.");
    }
}

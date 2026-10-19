package com.pangod512.modhub.client;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.jarjar.nio.pathfs.PathFileSystem;

public final class ModrinthService {
    private static ModrinthService instance;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ModHub-Modrinth"); thread.setDaemon(true); return thread;
    });
    private final ModrinthSettings settings;
    private final Path cachePath;
    private List<ModrinthResolver.Target> targets = List.of();
    private volatile ModrinthResolver.Snapshot snapshot = new ModrinthResolver.Snapshot(Map.of(), Map.of(), "Preparando la identificación...");
    private CompletableFuture<?> task;
    private boolean rerun;

    private ModrinthService() {
        Path config = FMLPaths.CONFIGDIR.get();
        settings = new ModrinthSettings(config.resolve("modhub-settings.json"));
        cachePath = config.resolve("modhub-modrinth-cache.json");
    }
    public static synchronized ModrinthService get() {
        if (instance == null) instance = new ModrinthService();
        return instance;
    }
    public ModrinthSettings settings() { return settings; }
    public ModrinthResolver.Snapshot snapshot() { return snapshot; }

    public void start() {
        List<ModrinthResolver.Target> files = new ArrayList<>();
        for (var info : ModList.get().getMods()) {
            if (info.getModId().equals("modhub")) continue;
            try {
                ModSource source = ModSource.from(info);
                // A project describes the entire package: do not assign its side to unrelated mods sharing one file.
                if (info.getOwningFile() == null || info.getOwningFile().getMods().size() != 1) continue;
                Path file = source.path().orElse(null);
                if (source.kind() == ModSource.Kind.NESTED && file != null && file.getFileSystem() instanceof PathFileSystem nested) {
                    file = nested.getTarget();
                } else if (source.kind() != ModSource.Kind.JAR) continue;
                if (file != null) files.add(new ModrinthResolver.Target(info.getModId(), file));
            } catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().debug("Mod Hub: archivo no consultable: {}", info.getModId());
            }
        }
        targets = List.copyOf(files);
        refresh();
    }

    public synchronized void refresh() {
        if (task != null && !task.isDone()) { rerun = true; return; }
        task = CompletableFuture.runAsync(() -> {
            try {
                String version = ModList.get().getModContainerById("modhub").orElseThrow().getModInfo().getVersion().toString();
                new ModrinthResolver(cachePath, ModrinthClient.production(version),
                        () -> java.time.Instant.now().getEpochSecond(), value -> snapshot = value).resolve(targets, settings::enabled);
            } catch (RuntimeException failure) {
                snapshot = new ModrinthResolver.Snapshot(snapshot.classifications(), snapshot.notes(),
                        "No se pudo completar o guardar la consulta. Se conservan los resultados disponibles y la detección local.");
                com.mojang.logging.LogUtils.getLogger().warn("Mod Hub: identificación remota incompleta", failure);
            } finally {
                synchronized (this) {
                    task = null;
                    if (rerun) { rerun = false; refresh(); }
                }
            }
        }, worker);
    }
}

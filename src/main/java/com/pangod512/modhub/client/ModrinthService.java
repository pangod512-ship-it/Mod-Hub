package com.pangod512.modhub.client;

import com.pangod512.modhub.core.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.jarjar.nio.pathfs.PathFileSystem;

public final class ModrinthService {
  private static ModrinthService instance;
  private final IdentificationService delegate =
      new IdentificationService(
          FMLPaths.CONFIGDIR.get(),
          new CompatibilityContext("1.20.1", "forge"),
          ModList.get()
              .getModContainerById("modhub")
              .orElseThrow()
              .getModInfo()
              .getVersion()
              .toString());

  public static synchronized ModrinthService get() {
    if (instance == null) instance = new ModrinthService();
    return instance;
  }

  public ModrinthSettings settings() {
    return delegate.settings();
  }

  public ModrinthResolver.Snapshot snapshot() {
    return delegate.snapshot();
  }

  public void start() {
    List<ModrinthResolver.Target> files = new ArrayList<>();
    for (var info : ModList.get().getMods()) {
      if (info.getModId().equals("modhub")) continue;
      try {
        ModSource source = ModSource.from(info);
        // Do not apply a package's side to multiple mods sharing a JAR.
        if (info.getOwningFile() == null || info.getOwningFile().getMods().size() != 1) continue;
        Path file = source.path().orElse(null);
        if (source.kind() == ModSource.Kind.NESTED
            && file != null
            && file.getFileSystem() instanceof PathFileSystem nested) {
          file = nested.getTarget();
        } else if (source.kind() != ModSource.Kind.JAR) continue;
        if (file != null) files.add(new ModrinthResolver.Target(info.getModId(), file));
      } catch (RuntimeException failure) {
        com.mojang.logging.LogUtils.getLogger()
            .debug("Mod Hub: archivo no consultable: {}", info.getModId());
      }
    }
    delegate.start(files);
  }

  public void refresh() {
    delegate.refresh();
  }
}

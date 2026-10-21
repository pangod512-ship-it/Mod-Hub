package com.pangod512.modhub.forge1192;

import com.pangod512.modhub.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent.Opening;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

@Mod.EventBusSubscriber(modid = "modhub", value = Dist.CLIENT)
public final class ClientBootstrap {
  static IdentificationService identification;
  static OverrideStore overrides;
  static final Map<String, CompletableFuture<ModClassification>> local = new HashMap<>();
  static final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "ModHub-local");
            t.setDaemon(true);
            return t;
          });

  static void register() {
    net.minecraftforge.fml.ModLoadingContext.get()
        .registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () ->
                new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                    (minecraft, parent) -> new HubScreen(parent, true)));
  }

  static void start() {
    identification =
        new IdentificationService(
            FMLPaths.CONFIGDIR.get(), new CompatibilityContext("1.19.2", "forge"), "1.1.0");
    overrides = new OverrideStore(FMLPaths.CONFIGDIR.get().resolve("modhub-overrides.json"));
    List<ModrinthResolver.Target> targets = new ArrayList<>();
    for (IModInfo mod : ModList.get().getMods()) {
      ModDescriptor d = descriptor(mod);
      if (d.file() != null
          && Files.isRegularFile(d.file())
          && !d.id().equals("modhub")
          && mod.getOwningFile().getMods().size() == 1)
        targets.add(new ModrinthResolver.Target(d.id(), d.file()));
    }
    identification.start(targets);
  }

  @SubscribeEvent
  public static void open(Opening event) {
    if (event.getNewScreen() instanceof ModListScreen && identification != null)
      event.setNewScreen(new HubScreen(Minecraft.getInstance().screen, false));
  }

  static ModDescriptor descriptor(IModInfo mod) {
    String author =
        mod.getConfig().getConfigElement("authors").map(Object::toString).orElse("No indicado");
    Path file = null;
    try {
      if (mod.getOwningFile() != null) file = mod.getOwningFile().getFile().getFilePath();
    } catch (RuntimeException ignored) {
    }
    return new ModDescriptor(
        mod.getModId(),
        mod.getDisplayName(),
        mod.getVersion().toString(),
        author,
        mod.getDescription(),
        file);
  }

  static ModClassification classification(IModInfo mod) {
    String id = mod.getModId();
    ModClassification value = ModClassification.unknown("Sin declaración explícita.");
    if (id.equals("modhub"))
      value =
          new ModClassification(
              ModType.CLIENT, ModClassification.Origin.DECLARED, "Mod de cliente.");
    else if (id.equals("minecraft") || id.equals("forge"))
      value =
          new ModClassification(
              ModType.BOTH, ModClassification.Origin.DECLARED, "Componente integrado.");
    else {
      boolean declared = false;
      try {
        declared =
            mod.getOwningFile() != null
                && Boolean.TRUE.equals(
                    mod.getOwningFile()
                        .getConfig()
                        .getConfigElement("clientSideOnly")
                        .orElse(false));
      } catch (RuntimeException ignored) {
      }
      if (declared)
        value =
            new ModClassification(
                ModType.CLIENT,
                ModClassification.Origin.DECLARED,
                "Declaración explícita de cliente.");
      else {
        CompletableFuture<ModClassification> future = local.get(id);
        if (future == null) {
          ModDescriptor d = descriptor(mod);
          boolean single = mod.getOwningFile() != null && mod.getOwningFile().getMods().size() == 1;
          future =
              CompletableFuture.supplyAsync(
                  () -> ArchiveClassifier.inspect(d.file(), id, single), worker);
          local.put(id, future);
        }
        if (future.isDone() && !future.isCompletedExceptionally()) value = future.getNow(value);
      }
    }
    value =
        ClassificationPolicy.resolve(
            value, identification.snapshot().classifications().get(id), null);
    return overrides.apply(id, mod.getVersion().toString(), value);
  }

  static java.util.Optional<
          java.util.function.BiFunction<
              Minecraft,
              net.minecraft.client.gui.screens.Screen,
              net.minecraft.client.gui.screens.Screen>>
      config(IModInfo mod) {
    return net.minecraftforge.client.ConfigScreenHandler.getScreenFactoryFor(mod);
  }
}

package com.pangod512.modhub.forge112;

import com.pangod512.modhub.core.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(
    modid = "modhub",
    name = "Mod Hub",
    version = "1.1.0",
    clientSideOnly = true,
    acceptableRemoteVersions = "*",
    guiFactory = "com.pangod512.modhub.forge112.ConfigFactory")
public final class ModHub {
  static IdentificationService identification;
  static OverrideStore overrides;
  static Path modsDirectory;
  static final Map<String, ModClassification> declarations = new HashMap<>();
  static final Map<String, CompletableFuture<ModClassification>> analyses = new HashMap<>();
  static final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "ModHub-local");
            t.setDaemon(true);
            return t;
          });

  @Mod.EventHandler
  public void preInit(FMLPreInitializationEvent event) {
    Path config = event.getModConfigurationDirectory().toPath();
    modsDirectory = config.toAbsolutePath().getParent().resolve("mods");
    overrides = new OverrideStore(config.resolve("modhub-overrides.json"));
    identification =
        new IdentificationService(config, new CompatibilityContext("1.12.2", "forge"), "1.1.0");
    for (net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData annotation :
        event.getAsmData().getAll("net.minecraftforge.fml.common.Mod")) {
      Map<String, Object> data = annotation.getAnnotationInfo();
      boolean client = Boolean.TRUE.equals(data.get("clientSideOnly")),
          server = Boolean.TRUE.equals(data.get("serverSideOnly"));
      if (client != server && data.get("modid") instanceof String)
        declarations.put(
            (String) data.get("modid"),
            new ModClassification(
                client ? ModType.CLIENT : ModType.SERVER,
                ModClassification.Origin.DECLARED,
                "Declaración explícita de Forge."));
    }
  }

  @Mod.EventHandler
  public void init(FMLInitializationEvent event) {
    MinecraftForge.EVENT_BUS.register(this);
    List<ModrinthResolver.Target> targets = new ArrayList<>();
    for (ModContainer mod : Loader.instance().getActiveModList()) {
      int owners = 0;
      for (ModContainer candidate : Loader.instance().getActiveModList())
        if (Objects.equals(candidate.getSource(), mod.getSource())) owners++;
      if (owners == 1
          && mod.getSource() != null
          && mod.getSource().isFile()
          && !mod.getModId().equals("modhub"))
        targets.add(new ModrinthResolver.Target(mod.getModId(), mod.getSource().toPath()));
    }
    identification.start(targets);
  }

  @SubscribeEvent
  public void open(GuiOpenEvent event) {
    if (event.getGui() instanceof GuiModList)
      event.setGui(new HubScreen(Minecraft.getMinecraft().currentScreen));
  }

  static ModDescriptor descriptor(ModContainer mod) {
    net.minecraftforge.fml.common.ModMetadata metadata = mod.getMetadata();
    String author =
        metadata == null || metadata.authorList == null || metadata.authorList.isEmpty()
            ? "No indicado"
            : String.join(", ", metadata.authorList);
    return new ModDescriptor(
        mod.getModId(),
        mod.getName(),
        mod.getDisplayVersion(),
        author,
        metadata == null ? "" : metadata.description,
        mod.getSource() == null ? null : mod.getSource().toPath());
  }

  static ModClassification classification(ModContainer mod) {
    String id = mod.getModId();
    ModClassification local = ModClassification.unknown("Sin declaración explícita.");
    if (id.equals("modhub"))
      local =
          new ModClassification(
              ModType.CLIENT, ModClassification.Origin.DECLARED, "Mod de cliente.");
    else if (id.equalsIgnoreCase("minecraft")
        || id.equalsIgnoreCase("forge")
        || id.equalsIgnoreCase("fml")
        || id.equalsIgnoreCase("mcp"))
      local =
          new ModClassification(
              ModType.BOTH, ModClassification.Origin.DECLARED, "Componente integrado.");
    else if (declarations.containsKey(id)) local = declarations.get(id);
    else {
      CompletableFuture<ModClassification> analysis = analyses.get(id);
      if (analysis == null) {
        ModDescriptor descriptor = descriptor(mod);
        int owners = 0;
        for (ModContainer candidate : Loader.instance().getActiveModList())
          if (Objects.equals(candidate.getSource(), mod.getSource())) owners++;
        final boolean single = owners == 1;
        analysis =
            CompletableFuture.supplyAsync(
                () -> ArchiveClassifier.inspect(descriptor.file(), id, single), worker);
        analyses.put(id, analysis);
      }
      if (analysis.isDone() && !analysis.isCompletedExceptionally()) local = analysis.getNow(local);
    }
    local =
        ClassificationPolicy.resolve(
            local, identification.snapshot().classifications().get(id), null);
    return overrides.apply(id, mod.getDisplayVersion(), local);
  }
}

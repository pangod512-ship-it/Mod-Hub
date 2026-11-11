package com.pangod512.modhub.probe;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "modhub", value = Dist.CLIENT)
public final class PortProbe {
  private static int ticks, stage, checks;
  private static boolean done;
  private static int languageIndex;
  private static boolean languageRequested;
  private static final String[] LANGUAGES = {"en_us", "es_mx", "fr_fr", "ja_jp", "en_us"};
  private static Screen parent;
  private static final Path OUTPUT = Paths.get("../build/port-probe");

  private static Object field(Object obj, String name) throws Exception {
    Field f = obj.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(obj);
  }

  private static void check(boolean value, String name) {
    if (!value) throw new AssertionError(name);
    checks++;
  }

  private static boolean languageChecks(Minecraft mc) {
    if (languageIndex == LANGUAGES.length) return true;
    String locale = LANGUAGES[languageIndex];
    if (!languageRequested) {
      mc.options.languageCode = locale;
      mc.getLanguageManager().setSelected(mc.getLanguageManager().getLanguage(locale));
      languageRequested = true;
      mc.reloadResourcePacks();
      return false;
    }
    boolean spanish = locale.startsWith("es_");
    check(mc.options.languageCode.equals(locale), "Minecraft retains " + locale);
    check(net.minecraft.client.resources.language.I18n.get("modhub.client").equals(spanish ? "Cliente" : "Client"), "Native fallback in " + locale);
    check(com.pangod512.modhub.core.ModType.CLIENT.label().equals(spanish ? "Cliente" : "Client"), "Client label in " + locale);
    check(com.pangod512.modhub.core.ModType.SERVER.label().equals(spanish ? "Servidor" : "Server"), "Server label in " + locale);
    check(!com.pangod512.modhub.core.ModType.UNKNOWN.hasBadge(), "Unknown has no badge in " + locale);
    languageIndex++;
    languageRequested = false;
    return false;
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent e) {
    if (done || e.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
    Minecraft mc = Minecraft.getInstance();
    try {
      if (ticks > 2400) throw new AssertionError("Timeout " + stage + " " + mc.screen);
      if (stage == 0) {
        if (mc.getOverlay() != null) return;
        if (mc.screen != null
            && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))
          mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
        if (!languageChecks(mc)) return;
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        parent = mc.screen;
        mc.setScreen(new net.minecraftforge.client.gui.ModListScreen(parent));
        stage++;
        return;
      }
      if (stage == 1) {
        check(mc.screen.getClass().getName().endsWith(".HubScreen"), "Menú Forge reemplazado");
        check(((List<?>) field(mc.screen, "visible")).size() >= 3, "Inventario cargado");
        check(field(mc.screen, "selected") != null, "Selección inicial");
        net.minecraft.client.gui.components.EditBox search=(net.minecraft.client.gui.components.EditBox)field(mc.screen,"search");
        search.setValue("no_such_modhub_fixture_123");
        check(((List<?>)field(mc.screen,"visible")).isEmpty() && field(mc.screen,"selected")==null,"Búsqueda vacía también limpia los detalles");
        search.setValue("modhub");
        check(((List<?>)field(mc.screen,"visible")).size()==1,"Búsqueda por identificador");
        check(((net.minecraftforge.forgespi.language.IModInfo)field(mc.screen,"selected")).getModId().equals("modhub"),"Selección sigue el filtro");
        search.setValue("");
        Files.createDirectories(OUTPUT);
        try (com.mojang.blaze3d.platform.NativeImage image =
            net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
          image.writeToFile(OUTPUT.resolve("main.png"));
        }
        stage++;
        return;
      }
      if (stage >= 2 && stage <= 4) {
        mc.options.guiScale().set(stage - 1);
        mc.resizeDisplay();
        check(mc.screen.width >= 320 && mc.screen.height >= 240, "Escala " + (stage - 1));
        check((Integer) field(mc.screen, "left") > 100, "Lista con ancho válido");
        stage++;
        return;
      }
      if (stage == 5) {
        net.minecraftforge.forgespi.language.IModInfo mod =
            net.minecraftforge.fml.ModList.get().getMods().stream()
                .filter(m -> m.getModId().equals("modhub"))
                .findFirst()
                .get();
        Class<?> bootstrap = Class.forName("com.pangod512.modhub.forge1192.ClientBootstrap");
        Field f = bootstrap.getDeclaredField("overrides");
        f.setAccessible(true);
        com.pangod512.modhub.core.OverrideStore store =
            (com.pangod512.modhub.core.OverrideStore) f.get(null);
        store.save("modhub", "1.1.1", com.pangod512.modhub.core.ModType.SERVER);
        check(
            new com.pangod512.modhub.core.OverrideStore(Paths.get("config/modhub-overrides.json"))
                    .apply(
                        "modhub", "1.1.1", com.pangod512.modhub.core.ModClassification.unknown(""))
                    .type()
                == com.pangod512.modhub.core.ModType.SERVER,
            "Corrección persistente");
        store.save("modhub", "1.1.1", null);
        mc.screen.onClose();
        check(mc.screen == parent, "Volver a pantalla anterior");
        finish(mc, null);
      }
    } catch (Throwable failure) {
      finish(mc, failure);
    }
  }

  private static void finish(Minecraft mc, Throwable failure) {
    done = true;
    try {
      Files.createDirectories(OUTPUT);
      Files.write(
          OUTPUT.resolve("results.txt"),
          (failure == null ? "PASSED: " + checks : "FAILED: " + failure)
              .getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
    }
    if (failure != null) failure.printStackTrace();
    mc.stop();
  }
}

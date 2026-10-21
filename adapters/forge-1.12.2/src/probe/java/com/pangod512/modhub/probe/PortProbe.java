package com.pangod512.modhub.probe;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Optional development fixture; excluded from all distributable JARs. */
@Mod(
    modid = "modhub_probe",
    name = "Mod Hub private validation",
    version = "1",
    clientSideOnly = true,
    acceptableRemoteVersions = "*")
@Mod.EventBusSubscriber(modid = "modhub_probe", value = Side.CLIENT)
public final class PortProbe {
  private static int ticks, stage, checks;
  private static boolean done;
  private static GuiScreen parent;
  private static final Path OUTPUT = Paths.get("../build/port-probe");

  private static Object field(Object object, String name) throws Exception {
    Field f = object.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(object);
  }

  private static void check(boolean value, String name) {
    if (!value) throw new AssertionError(name);
    checks++;
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (done || event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
    Minecraft mc = Minecraft.getMinecraft();
    try {
      if (ticks > 2400) throw new AssertionError("Timeout " + stage + " " + mc.currentScreen);
      if (stage == 0) {
        if (mc.currentScreen != null
            && mc.currentScreen
                .getClass()
                .getName()
                .equals("xaero.common.gui.GuiWidgetUpdateAll")) {
          mc.displayGuiScreen(new GuiMainMenu());
          return;
        }
        if (!(mc.currentScreen instanceof GuiMainMenu)) return;
        mc.gameSettings.guiScale = 2;
        parent = mc.currentScreen;
        mc.displayGuiScreen(new GuiModList(parent));
        stage++;
        return;
      }
      if (stage == 1) {
        check(
            mc.currentScreen.getClass().getName().endsWith(".HubScreen"),
            "Sustitución del menú Forge");
        List<?> mods = (List<?>) field(mc.currentScreen, "mods");
        check(mods.size() >= 4, "Inventario de mods cargado");
        check(field(mc.currentScreen, "selected") != null, "Selección inicial");
        Field query = mc.currentScreen.getClass().getDeclaredField("query");
        query.setAccessible(true);
        Method refresh = mc.currentScreen.getClass().getDeclaredMethod("refresh");
        refresh.setAccessible(true);
        query.set(mc.currentScreen, "no_such_modhub_fixture_123");
        refresh.invoke(mc.currentScreen);
        check(((List<?>) field(mc.currentScreen, "mods")).isEmpty(), "Búsqueda sin coincidencias");
        check(
            field(mc.currentScreen, "selected") == null,
            "Sin detalles de un mod oculto por la búsqueda");
        query.set(mc.currentScreen, "modhub");
        refresh.invoke(mc.currentScreen);
        check(
            ((List<?>) field(mc.currentScreen, "mods")).size() >= 1, "Búsqueda por identificador");
        check(
            ((net.minecraftforge.fml.common.ModContainer) field(mc.currentScreen, "selected"))
                .getModId()
                .equals("modhub"),
            "Selección sigue el filtro");
        check(
            ((GuiButton) field(mc.currentScreen, "configure")).visible,
            "Configuración cuando existe una pantalla compatible");
        query.set(mc.currentScreen, "");
        refresh.invoke(mc.currentScreen);
        Files.createDirectories(OUTPUT);
        javax.imageio.ImageIO.write(
            net.minecraft.util.ScreenShotHelper.createScreenshot(
                mc.displayWidth, mc.displayHeight, mc.getFramebuffer()),
            "png",
            OUTPUT.resolve("main.png").toFile());
        stage++;
        return;
      }
      if (stage >= 2 && stage <= 4) {
        int scale = stage - 1;
        mc.gameSettings.guiScale = scale;
        mc.displayGuiScreen(mc.currentScreen);
        check(mc.currentScreen.width >= 320 && mc.currentScreen.height >= 240, "Escala " + scale);
        check((Integer) field(mc.currentScreen, "leftWidth") > 100, "Ancho de lista " + scale);
        stage++;
        return;
      }
      if (stage == 5) {
        Class<?> hub = Class.forName("com.pangod512.modhub.forge112.ModHub");
        Field store = hub.getDeclaredField("overrides");
        store.setAccessible(true);
        com.pangod512.modhub.core.OverrideStore overrides =
            (com.pangod512.modhub.core.OverrideStore) store.get(null);
        overrides.save("modhub", "1.1.0", com.pangod512.modhub.core.ModType.SERVER);
        check(
            new com.pangod512.modhub.core.OverrideStore(Paths.get("config/modhub-overrides.json"))
                    .apply(
                        "modhub", "1.1.0", com.pangod512.modhub.core.ModClassification.unknown(""))
                    .type()
                == com.pangod512.modhub.core.ModType.SERVER,
            "Persistencia de corrección");
        overrides.save("modhub", "1.1.0", null);
        mc.displayGuiScreen(parent);
        check(mc.currentScreen == parent, "Volver conserva pantalla anterior");
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
    mc.shutdown();
  }
}

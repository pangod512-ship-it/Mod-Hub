package com.pangod512.modhub.probe;

import com.pangod512.modhub.client.ModHubScreen;
import com.pangod512.modhub.client.ModHubSettingsScreen;
import com.pangod512.modhub.client.ModRow;
import com.pangod512.modhub.core.ModClassification;
import com.pangod512.modhub.core.ModType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;

/** Development-only fixture. Its source set is excluded from distributable artifacts. */
public final class ModernProbe {
  private static int stage;
  private static int checks;
  private static long started;
  private static boolean finished;
  private static Screen parent;
  private static ModHubScreen hub;
  private static int captures;
  private static int languageIndex;
  private static final String[] LANGUAGES = {"en_us", "es_mx", "fr_fr", "ja_jp", "en_us"};

  private ModernProbe() {}

  private static void check(boolean condition, String description) {
    if (!condition) throw new AssertionError(description);
    checks++;
  }

  private static Field field(Object value, String name) throws Exception {
    Field field = value.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static void chooseLanguage(Minecraft minecraft, String locale) {
    minecraft.getLanguageManager().setSelected(locale);
    minecraft.options.languageCode = locale;
    minecraft.reloadResourcePacks();
  }

  private static void openList(Minecraft minecraft) throws Exception {
    String loader = System.getProperty("modhub.probe.loader");
    String name =
        loader.equals("neoforge") && !System.getProperty("modhub.probe.mc").equals("1.20.1")
            ? "net.neoforged.neoforge.client.gui.ModListScreen"
            : "net.minecraftforge.client.gui.ModListScreen";
    Screen list = (Screen) Class.forName(name).getConstructor(Screen.class).newInstance(parent);
    minecraft.setScreen(list);
    check(minecraft.screen instanceof ModHubScreen, "Native Mods menu opens Mod Hub");
    hub = (ModHubScreen) minecraft.screen;
    @SuppressWarnings("unchecked")
    List<ModRow> rows = (List<ModRow>) field(hub, "allMods").get(hub);
    ModRow own =
        rows.stream()
            .filter(row -> row.info().getModId().equals("modhub"))
            .findFirst()
            .orElseThrow();
    Method select = ModHubScreen.class.getDeclaredMethod("select", ModRow.class);
    select.setAccessible(true);
    select.invoke(hub, own);
  }

  @SuppressWarnings("unchecked")
  private static void inventory() throws Exception {
    List<ModRow> rows = (List<ModRow>) field(hub, "allMods").get(hub);
    check(!rows.isEmpty(), "Loaded inventory is available");
    check(
        rows.stream().anyMatch(row -> row.info().getModId().equals("modhub")),
        "Mod Hub is in the inventory");
    ModRow template = rows.get(0);
    List<ModRow> fixtures = new ArrayList<>();
    fixtures.add(
        new ModRow(
            template.info(),
            "Unknown fixture",
            "1",
            "Fixture",
            ModClassification.unknown("fixture"),
            template.source()));
    fixtures.add(
        new ModRow(
            template.info(),
            "Client fixture",
            "1",
            "Fixture",
            new ModClassification(ModType.CLIENT, ModClassification.Origin.DECLARED, "fixture"),
            template.source()));
    field(hub, "allMods").set(hub, fixtures);
    field(hub, "filter").set(hub, ModType.UNKNOWN);
    Method refresh = ModHubScreen.class.getDeclaredMethod("refreshRows", boolean.class);
    refresh.setAccessible(true);
    refresh.invoke(hub, false);
    Object table = field(hub, "table").get(hub);
    ContainerEventHandler list = (ContainerEventHandler) table;
    check(list.children().size() == 1, "Unknown filter retains the undetermined mod");
    EditBox search = (EditBox) field(hub, "search").get(hub);
    search.setValue("no_such_fixture_123");
    check(list.children().isEmpty(), "Search filters the actual table");
    search.setValue("");
    check(list.children().size() == 1, "Clearing search restores the unknown filter");
    check(!ModType.UNKNOWN.hasBadge(), "Undetermined category has no badge");
    field(hub, "allMods").set(hub, rows);
    field(hub, "filter").set(hub, ModType.ALL);
    refresh.invoke(hub, false);
    ModRow own =
        rows.stream()
            .filter(row -> row.info().getModId().equals("modhub"))
            .findFirst()
            .orElseThrow();
    check(
        own.descriptor().description().equals(I18n.get("modhub.description")),
        "Own description follows the active language");
    Method select = ModHubScreen.class.getDeclaredMethod("select", ModRow.class);
    select.setAccessible(true);
    select.invoke(hub, own);
    Button config = (Button) field(hub, "configButton").get(hub);
    check(config.visible && config.active, "Mod Hub registers its native configuration screen");
    Field callback =
        Arrays.stream(Button.class.getDeclaredFields())
            .filter(candidate -> candidate.getType() == Button.OnPress.class)
            .findFirst()
            .orElseThrow();
    callback.setAccessible(true);
    ((Button.OnPress) callback.get(config)).onPress(config);
  }

  private static void settings(Minecraft minecraft, boolean spanish) {
    String prefix = spanish ? "Consultar Modrinth: " : "Query Modrinth: ";
    check(minecraft.screen instanceof ModHubSettingsScreen, "Settings screen opens");
    check(
        minecraft.screen.children().stream()
            .filter(widget -> widget instanceof Button)
            .map(widget -> ((Button) widget).getMessage().getString())
            .anyMatch(text -> text.startsWith(prefix)),
        "Settings follows the active Minecraft language");
    check(
        minecraft.screen.children().stream()
            .filter(widget -> widget instanceof Button)
            .map(widget -> ((Button) widget).getMessage().getString())
            .noneMatch(text -> text.contains("CurseForge")),
        "Discarded CurseForge control is absent");
  }

  private static void capture(Minecraft minecraft, String name) throws Exception {
    Path output =
        Path.of(System.getProperty("modhub.probe.output")).getParent().resolve(name + ".png");
    Files.createDirectories(output.getParent());
    java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> save =
        image -> {
          try (image) {
            image.writeToFile(output);
          } catch (Exception failure) {
            throw new IllegalStateException("Cannot save probe screenshot", failure);
          } finally {
            captures--;
          }
        };
    captures++;
    Class<?> screenshot = Class.forName("net.minecraft.client.Screenshot");
    Method take =
        Arrays.stream(screenshot.getDeclaredMethods())
            .filter(method -> Modifier.isStatic(method.getModifiers()))
            .filter(
                method ->
                    Arrays.equals(
                            method.getParameterTypes(),
                            new Class<?>[] {com.mojang.blaze3d.pipeline.RenderTarget.class})
                        && method.getReturnType() == com.mojang.blaze3d.platform.NativeImage.class)
            .findFirst()
            .orElse(null);
    if (take != null) {
      save.accept(
          (com.mojang.blaze3d.platform.NativeImage)
              take.invoke(null, minecraft.getMainRenderTarget()));
    } else {
      Method asynchronous =
          Arrays.stream(screenshot.getDeclaredMethods())
              .filter(method -> Modifier.isStatic(method.getModifiers()))
              .filter(
                  method ->
                      Arrays.equals(
                          method.getParameterTypes(),
                          new Class<?>[] {
                            com.mojang.blaze3d.pipeline.RenderTarget.class,
                            java.util.function.Consumer.class
                          }))
              .findFirst()
              .orElseThrow();
      asynchronous.invoke(null, minecraft.getMainRenderTarget(), save);
    }
  }

  public static void onFrame() {
    if (finished) return;
    Minecraft minecraft = Minecraft.getInstance();
    if (started == 0) started = System.currentTimeMillis();
    try {
      if (System.currentTimeMillis() - started > 240_000)
        throw new AssertionError(
            "Probe timeout at stage " + stage + "; screen=" + minecraft.screen);
      if (minecraft.getOverlay() != null) return;
      if (minecraft.screen != null
          && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
        minecraft.setScreen(new TitleScreen());
        return;
      }
      if (stage == 0) {
        if (!(minecraft.screen instanceof TitleScreen)) return;
        parent = minecraft.screen;
        stage = 1;
        chooseLanguage(minecraft, LANGUAGES[languageIndex]);
      } else if (stage == 1) {
        String locale = LANGUAGES[languageIndex];
        boolean spanish = locale.startsWith("es_");
        check(
            minecraft.getLanguageManager().getSelected().equals(locale),
            "Minecraft retains " + locale);
        check(
            I18n.get("modhub.client").equals(spanish ? "Cliente" : "Client"),
            "Client label in " + locale);
        check(
            I18n.get("modhub.server").equals(spanish ? "Servidor" : "Server"),
            "Server label in " + locale);
        check(
            ModType.CLIENT.label().equals(spanish ? "Cliente" : "Client"),
            "Translation bridge in " + locale);
        openList(minecraft);
        stage = 2;
      } else if (stage == 2) {
        capture(minecraft, languageIndex + "-" + LANGUAGES[languageIndex] + "-inventory");
        inventory();
        stage = 3;
      } else if (stage == 3) {
        capture(minecraft, languageIndex + "-" + LANGUAGES[languageIndex] + "-settings");
        settings(minecraft, LANGUAGES[languageIndex].startsWith("es_"));
        minecraft.screen.onClose();
        check(minecraft.screen == hub, "Back returns to Mod Hub");
        stage = 4;
      } else if (captures == 0) {
        languageIndex++;
        if (languageIndex == LANGUAGES.length) finish(minecraft, null);
        else {
          stage = 1;
          chooseLanguage(minecraft, LANGUAGES[languageIndex]);
        }
      }
    } catch (Throwable failure) {
      finish(minecraft, failure);
    }
  }

  private static void finish(Minecraft minecraft, Throwable failure) {
    finished = true;
    if (failure != null) failure.printStackTrace();
    try {
      Path output = Path.of(System.getProperty("modhub.probe.output"));
      Files.createDirectories(output.getParent());
      Files.writeString(
          output,
          (failure == null ? "PASSED: " + checks : "FAILED at " + stage + ": " + failure)
              + System.lineSeparator(),
          StandardCharsets.UTF_8);
    } catch (Exception error) {
      error.printStackTrace();
    }
    minecraft.stop();
  }
}

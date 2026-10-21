package com.pangod512.modhub.smoke;

import com.pangod512.modhub.core.*;

import com.pangod512.modhub.client.ModHubScreen;
import com.pangod512.modhub.client.ModRow;
import com.pangod512.modhub.core.ModType;
import com.pangod512.modhub.core.ModClassification;
import com.pangod512.modhub.core.OverrideStore;
import com.pangod512.modhub.client.ModrinthService;
import com.pangod512.modhub.core.ModrinthResolver;
import com.pangod512.modhub.core.ModrinthClient;
import com.pangod512.modhub.core.ModrinthCache;
import com.pangod512.modhub.core.ModrinthSettings;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod("modhub_smoke")
public final class UiSmokeMod {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("modhub.smoke.output"));
    private final List<String> results = new ArrayList<>();
    private ModHubScreen screen;
    private Screen parent;
    private List<ModRow> original;
    private String pendingScreenshot;
    private int step, ticks;
    private boolean finished;
    private OverrideStore realOverrides;
    private ModrinthResolver.Snapshot realRemote;
    private int matrixIndex;
    private RealModRows realRows;

    public UiSmokeMod() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        if (++ticks > 2400) {
            finish(new AssertionError("Tiempo de espera agotado; paso " + step + ", pantalla "
                    + (minecraft.screen == null ? "null" : minecraft.screen.getClass().getName())
                    + ", overlay " + (minecraft.getOverlay() == null ? "null" : minecraft.getOverlay().getClass().getName())));
            return;
        }
        if (step == 0 && ticks % 200 == 0) {
            LogUtils.getLogger().info("UI_SMOKE_WAIT: pantalla={}, overlay={}",
                    minecraft.screen == null ? "null" : minecraft.screen.getClass().getName(),
                    minecraft.getOverlay() == null ? "null" : minecraft.getOverlay().getClass().getName());
        }
        if (ticks % 15 != 0 || pendingScreenshot != null || minecraft.getOverlay() != null) return;
        try {
            if (step == 0) {
                if (minecraft.screen instanceof net.minecraftforge.client.gui.LoadingErrorScreen loading) {
                    var errorsField = loading.getClass().getDeclaredField("modLoadErrors");
                    errorsField.setAccessible(true);
                    var errors = (List<?>) errorsField.get(loading);
                    check(errors.isEmpty(), "La instancia de prueba no presenta errores de carga: " + errors);
                    var warningsField = loading.getClass().getDeclaredField("modLoadWarnings");
                    warningsField.setAccessible(true);
                    for (var warning : (List<net.minecraftforge.fml.ModLoadingWarning>) warningsField.get(loading)) {
                        LogUtils.getLogger().info("UI_SMOKE_LOADING_WARNING: {}", warning.formatToString());
                    }
                    pressCurrent(net.minecraftforge.common.ForgeI18n.parseMessage("fml.button.continue.launch"));
                    return;
                }
                if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
                    minecraft.setScreen(new TitleScreen());
                }
                if (!(minecraft.screen instanceof TitleScreen)) return;
                verifyRuntime();
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                parent = minecraft.screen;
                minecraft.setScreen(new ModListScreen(parent));
                check(minecraft.screen instanceof ModHubScreen, "El boton Mods abre la pantalla propia");
                screen = (ModHubScreen) minecraft.screen;
                original = field("allMods", List.class);
                results.addAll(InventoryChecks.verify(original, output));
                pendingScreenshot = "01-principal.png";
            } else if (step == 1) {
                if (!field("analyses", java.util.Map.class).isEmpty()) return;
                var loaded = field("allMods", List.class);
                original = loaded;
                ModRow content = ((List<ModRow>) loaded).stream().filter(row -> row.info().getModId().equals("fixture_content"))
                        .findFirst().orElseThrow();
                check(content.type() == ModType.BOTH && content.classification().origin() == ModClassification.Origin.ESTIMATED,
                        "Un mod cargado con datos de juego se actualiza a Ambos estimado sin bloquear la pantalla");
                check(((List<ModRow>) loaded).stream().filter(row -> row.info().getModId().equals("fixture_visual"))
                                .findFirst().orElseThrow().type() == ModType.UNKNOWN,
                        "Los recursos de un JAR compartido no inventan Solo cliente");
                screen.setFocused(table());
                check(screen.keyPressed(70, 0, 2) && search().isFocused(), "Ctrl+F enfoca la busqueda usando los modificadores del evento");
                search().setValue("Ejemplo contenido");
                results.addAll(TypeColumnChecks.verify(screen, table()));
                search().setValue("minecraft");
                check(table().children().size() == 1, "Busqueda por ID");
                if (Boolean.getBoolean("modhub.smoke.vanillaLayout")) results.addAll(TypeColumnChecks.verifyCounter(screen, true));
                search().setValue("Mod Hub");
                check(table().children().size() == 1, "Busqueda por nombre");
                check(configButton().visible, "Mod Hub ofrece su pantalla de configuracion");
                configButton().onPress();
                check(minecraft.screen.getClass().getSimpleName().equals("ModHubSettingsScreen"), "Se abre la configuracion real de Modrinth");
                boolean wasEnabled = ModrinthService.get().settings().enabled();
                pressCurrent("Consultar Modrinth: " + (wasEnabled ? "Sí" : "No"));
                check(ModrinthService.get().settings().enabled() != wasEnabled, "La interfaz permite activar o desactivar las consultas");
                check(new ModrinthSettings(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modhub-settings.json")).enabled() != wasEnabled,
                        "La opcion cambiada desde la pantalla persiste en disco");
                pressCurrent("Consultar Modrinth: " + (wasEnabled ? "No" : "Sí"));
                minecraft.screen.onClose();
                check(minecraft.screen == screen, "Volver desde ajustes conserva la pantalla de mods");
                search().setValue("fixture_unknown");
                check(!configButton().visible, "Configurar se oculta si no existe pantalla compatible");
                search().setValue("Mod Hub");
                results.addAll(TypeColumnChecks.verify(screen, table()));
                pendingScreenshot = "02-busqueda.png";
            } else if (step == 2) {
                search().setValue("zzzz_no_existe");
                check(table().children().isEmpty(), "Busqueda sin resultados");
                check(!configButton().visible && field("selected", ModRow.class) == null,
                        "Sin resultados no queda una seleccion oculta");
                if (Boolean.getBoolean("modhub.smoke.vanillaLayout")) results.addAll(TypeColumnChecks.verifyCounter(screen, true));
                pendingScreenshot = "03-sin-resultados.png";
            } else if (step == 3) {
                search().setValue("");
                chooseFilter(ModType.CLIENT);
                check(!table().children().isEmpty(), "Filtro Solo cliente");
                check(field("selected", ModRow.class).type() == ModType.CLIENT, "Tipo fiable del propio Mod Hub");
                chooseFilter(ModType.SERVER);
                check(table().children().isEmpty(), "No se inventa clasificacion de servidor");
                chooseFilter(ModType.ALL);
                check(table().children().size() == original.size(), "Filtro Todos restaura la lista");
                field("typeButton", Button.class).onPress();
                screen.keyPressed(256, 0, 0);
                check(minecraft.screen == screen && !field("filterOpen", Boolean.class),
                        "Escape cierra el filtro sin cerrar la pantalla");
            } else if (step == 4) {
                ModRow template = original.get(0);
                List<ModRow> manyMods = new ArrayList<>();
                for (int i = 0; i < 160; i++) {
                    manyMods.add(new ModRow(template.info(), String.format("Mod de prueba %02d con nombre largo", i),
                            "1.20.1-123456789", "Autor de prueba con nombre largo", ModType.values()[1 + i % 4],
                            template.source()));
                }
                setField("allMods", manyMods);
                screen.resize(minecraft, screen.width, screen.height);
                check(table().children().size() == 160, "Lista de 160 mods");
                check(table().getMaxScroll() > 0, "La tabla tiene desplazamiento");
                check(table().mouseScrolled(table().getLeft() + 20, table().getTop() + 20, -4),
                        "La rueda desplaza la tabla");
                check(table().getScrollAmount() > 0, "La posicion de desplazamiento cambia");
                check(table().mouseClicked(table().getLeft() + 40, table().getTop() + 15, 0),
                        "Seleccion de una fila con el raton");
                check(table().getSelected() != null, "La fila pulsada queda seleccionada");
                results.addAll(TypeColumnChecks.verify(screen, table()));
                pendingScreenshot = "04-lista-larga.png";
            } else if (step == 5) {
                int typeCount = 0;
                for (ModType type : ModType.values()) {
                    if (type == ModType.ALL) continue;
                    chooseFilter(type);
                    typeCount += table().children().size();
                    check(table().children().size() == 40, "Filtro " + type.label + " con datos de prueba");
                }
                check(typeCount == 160, "Los filtros no pierden ni duplican mods");
                chooseFilter(ModType.ALL);
                setField("allMods", original);
                screen.resize(minecraft, screen.width, screen.height);
                search().setValue("Mod Hub");
                ModList.get().getModContainerById("modhub").orElseThrow().registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(ConfigTestScreen::new));
                search().setValue("Mod Hub");
                check(configButton().visible, "Configurar aparece si el mod ofrece pantalla");
                configButton().onPress();
                check(minecraft.screen instanceof ConfigTestScreen, "Se abre la configuracion compatible");
            } else if (step == 6) {
                minecraft.screen.onClose();
                check(minecraft.screen == screen, "Regreso de la configuracion a Mod Hub");
                check(search().getValue().equals("Mod Hub"), "Se conserva la busqueda al regresar");
                check(field("selected", ModRow.class).info().getModId().equals("modhub"), "Se conserva la seleccion");
                pendingScreenshot = "05-configurar.png";
            } else if (step == 7) {
                button("Volver").onPress();
                check(minecraft.screen == parent, "Volver regresa al menu anterior");
                Object logos = field("logos", Object.class);
                Field cache = logos.getClass().getDeclaredField("logos");
                cache.setAccessible(true);
                check(((java.util.Map<?, ?>) cache.get(logos)).isEmpty(), "Los iconos se liberan al cerrar");
                minecraft.setScreen(new ModListScreen(parent));
                check(minecraft.screen instanceof ModHubScreen, "Se puede volver a abrir");
                screen = (ModHubScreen) minecraft.screen;
                minecraft.options.guiScale().set(3);
                minecraft.resizeDisplay();
                checkLayout();
                AbstractScrollWidget details = field("details", AbstractScrollWidget.class);
                double tablePosition = table().getScrollAmount();
                check(screen.mouseScrolled(details.getX() + 20, details.getY() + 40, -3),
                        "La rueda desplaza los detalles");
                var scrollGetter = AbstractScrollWidget.class.getDeclaredMethod("scrollAmount");
                scrollGetter.setAccessible(true);
                check((double) scrollGetter.invoke(details) > 0, "El panel derecho tiene desplazamiento independiente");
                check(table().getScrollAmount() == tablePosition, "Desplazar detalles no mueve la tabla");
                screen.resize(minecraft, screen.width, screen.height);
                results.addAll(TypeColumnChecks.verify(screen, table()));
                pendingScreenshot = "06-compacta.png";
            } else if (step == 8) {
                minecraft.options.guiScale().set(1);
                minecraft.resizeDisplay();
                checkLayout();
                results.addAll(TypeColumnChecks.verify(screen, table()));
                pendingScreenshot = "07-amplia.png";
            } else if (step == 9) {
                minecraft.getWindow().setWindowed(1920, 984);
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
                checkLayout();
                results.addAll(TypeColumnChecks.verify(screen, table()));
                pendingScreenshot = "08-tipo-escala4.png";
            } else if (step == 10) {
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                search().setValue("Mod Hub");
                ModList.get().getModContainerById("modhub").orElseThrow().registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory(p -> {
                            throw new IllegalStateException("Fallo intencionado de la prueba");
                        }));
                configButton().onPress();
                check(minecraft.screen == screen && field("configError", String.class) != null,
                        "Una configuracion defectuosa no cierra el juego");
                screen.keyPressed(256, 0, 0);
                check(minecraft.screen == parent, "Escape regresa al menu anterior");
            } else if (step == 11) {
                minecraft.setScreen(new ModListScreen(parent));
                screen = (ModHubScreen) minecraft.screen;
                search().setValue("Mod Hub");
                field("correctionButton", Button.class).onPress();
                check(minecraft.screen.getClass().getSimpleName().equals("TypeOverrideScreen"), "Se abre el selector de correccion manual");
                pressCurrent("Ambos");
                check(minecraft.screen == screen && field("selected", ModRow.class).type() == ModType.BOTH,
                        "Elegir Ambos actualiza la fila y regresa a la lista");
                check(field("selected", ModRow.class).classification().origin() == ModClassification.Origin.MANUAL,
                        "La eleccion de la interfaz muestra origen Manual");
                pendingScreenshot = "09-clasificacion-manual.png";
            } else if (step == 12) {
                minecraft.setScreen(new ModHubScreen(parent));
                screen = (ModHubScreen) minecraft.screen;
                search().setValue("Mod Hub");
                check(field("selected", ModRow.class).type() == ModType.BOTH,
                        "Reabrir la pantalla recarga la correccion desde disco");
                chooseFilter(ModType.BOTH);
                check(table().children().size() == 1, "El filtro Ambos incluye la correccion manual y respeta la busqueda");
                field("correctionButton", Button.class).onPress();
                pressCurrent("Restablecer detección");
                check(table().children().isEmpty() && !field("correctionButton", Button.class).visible,
                        "Restablecer quita del filtro al mod que ya no coincide sin dejar seleccion oculta");
                chooseFilter(ModType.ALL);
                search().setValue("Mod Hub");
                check(field("selected", ModRow.class).type() == ModType.CLIENT
                                && field("selected", ModRow.class).classification().origin() == ModClassification.Origin.DECLARED,
                        "Restablecer recupera Solo cliente / Declarado");
            } else if (step == 13) {
                realOverrides = field("overrides", OverrideStore.class);
                Path corrupt = output.resolve("fixtures/ui-corrupt.json");
                Files.writeString(corrupt, "{roto");
                setField("overrides", new OverrideStore(corrupt));
                screen.resize(minecraft, screen.width, screen.height);
                check(field("overrides", OverrideStore.class).warning() != null, "El menu permanece abierto con aviso por JSON dañado");
                pendingScreenshot = "10-aviso-correcciones.png";
            } else if (step == 14) {
                Path blocker = output.resolve("fixtures/ui-not-directory");
                Files.writeString(blocker, "bloqueo");
                setField("overrides", new OverrideStore(blocker.resolve("modhub-overrides.json")));
                field("correctionButton", Button.class).onPress();
                pressCurrent("Solo servidor");
                check(minecraft.screen != screen && minecraft.screen.getClass().getSimpleName().equals("TypeOverrideScreen"),
                        "Si guardar falla, el selector permanece abierto y conserva el tipo anterior");
                var errorField = minecraft.screen.getClass().getDeclaredField("error");
                errorField.setAccessible(true);
                check(errorField.get(minecraft.screen) != null && field("selected", ModRow.class).type() == ModType.CLIENT,
                        "El fallo de escritura muestra un mensaje y no modifica la clasificacion");
                minecraft.screen.onClose();
                setField("overrides", realOverrides);
                screen.resize(minecraft, screen.width, screen.height);
                persistenceProbe();
            } else if (step == 15) {
                // Inject a deterministic remote reply: the game itself still renders and updates the real table.
                realRemote = ModrinthService.get().snapshot();
                var value = ModrinthClient.classification(new ModrinthCache.Entry("proj0001", "vers0001", "server_only", true, 1));
                setRemote(new ModrinthResolver.Snapshot(java.util.Map.of("fixture_unknown", value),
                        java.util.Map.of("fixture_unknown", "Modrinth: resultado guardado en caché."), "Respuesta de prueba"));
                search().setValue("fixture_unknown");
                screen.tick();
                check(field("selected", ModRow.class).type() == ModType.SERVER
                                && field("selected", ModRow.class).classification().origin() == ModClassification.Origin.MODRINTH,
                        "Una respuesta remota actualiza la tabla en el juego con origen Modrinth");
                chooseFilter(ModType.SERVER);
                check(table().children().size() == 1, "El filtro Tipo utiliza el resultado remoto");
                pendingScreenshot = "11-modrinth.png";
            } else if (step == 16) {
                field("correctionButton", Button.class).onPress();
                pressCurrent("Solo cliente");
                chooseFilter(ModType.ALL);
                check(field("selected", ModRow.class).type() == ModType.CLIENT
                                && field("selected", ModRow.class).classification().origin() == ModClassification.Origin.MANUAL,
                        "La correccion manual desde la interfaz gana a Modrinth");
                field("correctionButton", Button.class).onPress(); pressCurrent("Restablecer detección");
                check(field("selected", ModRow.class).type() == ModType.SERVER
                                && field("selected", ModRow.class).classification().origin() == ModClassification.Origin.MODRINTH,
                        "Restablecer recupera el tipo de Modrinth sin hacer una consulta adicional");
                setRemote(realRemote); screen.tick();
                screen.onClose();
                minecraft.setScreen(new com.pangod512.modhub.client.ModHubSettingsScreen(parent));
                pendingScreenshot = "12-opciones-modrinth.png";
            } else if (step == 17) {
                minecraft.screen.onClose();
                check(minecraft.screen == parent, "La configuracion de Modrinth se dibuja y vuelve al menu anterior");
                minecraft.setScreen(new ModHubScreen(parent));
                screen = (ModHubScreen) minecraft.screen;
            } else if (step == 18) {
                int window = matrixIndex / 4, scale = matrixIndex % 4 + 1;
                minecraft.getWindow().setWindowed(window == 0 ? 1280 : 1920, window == 0 ? 720 : 984);
                minecraft.options.guiScale().set(scale); minecraft.resizeDisplay();
                checkLayout();
                if (table().getSelected() != null) results.addAll(TypeColumnChecks.verify(screen, table()));
                if (Boolean.getBoolean("modhub.smoke.vanillaLayout")) results.addAll(TypeColumnChecks.verifyCounter(screen, false));
                if (Boolean.getBoolean("modhub.smoke.vanilla")) {
                    for (var widget : screen.children()) {
                        if (widget instanceof Button) check(widget.getClass() == Button.class, "Boton vanilla en escala " + scale);
                    }
                }
                pendingScreenshot = "matriz-" + (window == 0 ? "1280x720" : "1920x984") + "-escala" + scale + ".png";
                if (++matrixIndex < 8) return;
            } else if (step == 19) {
                String realFolder = System.getProperty("modhub.smoke.realMods", "");
                if (!realFolder.isEmpty()) {
                    realRows = new RealModRows(Path.of(realFolder));
                    check(realRows.rows.size() > 50, "La tabla recibe mas de 50 mods con metadatos reales sin ejecutar sus clases");
                    field("analyses", java.util.Map.class).clear();
                    setField("allMods", realRows.rows);
                    screen.resize(minecraft, screen.width, screen.height);
                    search().setValue("farmer");
                    check(!table().children().isEmpty(), "La busqueda funciona con nombres reales del modpack");
                    search().setValue("");
                    results.addAll(TypeColumnChecks.verify(screen, table()));
                    pendingScreenshot = "modpack-real.png";
                }
            } else if (step == 20) {
                screen.onClose();
                if (realRows != null) realRows.close();
                finish(null);
                return;
            }
            step++;
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private void setRemote(ModrinthResolver.Snapshot value) throws Exception {
        var delegate = ModrinthService.class.getDeclaredField("delegate"); delegate.setAccessible(true);
        var field = IdentificationService.class.getDeclaredField("snapshot"); field.setAccessible(true); field.set(delegate.get(ModrinthService.get()), value);
    }

    private void render(ScreenEvent.Render.Post event) {
        if (pendingScreenshot == null || event.getScreen() != minecraft.screen || finished) return;
        try {
            Files.createDirectories(output);
            event.getGuiGraphics().flush();
            try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(output.resolve(pendingScreenshot));
            }
            pendingScreenshot = null;
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private void chooseFilter(ModType type) throws Exception {
        Button button = field("typeButton", Button.class);
        button.onPress();
        int y = button.getY() + button.getHeight() + 2;
        int optionHeight = Math.min(20, (screen.height - y - 8) / ModType.values().length);
        screen.mouseClicked(button.getX() + 8, y + 2 + type.ordinal() * optionHeight + optionHeight / 2, 0);
        check(field("filter", ModType.class) == type, "Seleccion de filtro " + type.label);
    }

    private void pressCurrent(String label) {
        minecraft.screen.children().stream().filter(widget -> widget instanceof Button)
                .map(widget -> (Button) widget).filter(widget -> widget.getMessage().getString().equals(label))
                .findFirst().orElseThrow().onPress();
    }

    private void persistenceProbe() throws Exception {
        Path config = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modhub-overrides.json");
        OverrideStore store = new OverrideStore(config);
        String phase = System.getProperty("modhub.smoke.persistence", "none");
        if (phase.equals("write")) {
            store.save("modhub_persistence_probe", Long.toString(ProcessHandle.current().pid()), ModType.SERVER);
            check(store.snapshot().containsKey("modhub_persistence_probe"), "Primera ejecucion guarda una correccion para el reinicio");
        } else if (phase.equals("read")) {
            var choice = store.snapshot().get("modhub_persistence_probe");
            check(choice != null && choice.type() == ModType.SERVER
                            && !choice.version().equals(Long.toString(ProcessHandle.current().pid())),
                    "Segunda JVM recupera la correccion guardada antes de cerrar Minecraft");
            store.save("modhub_persistence_probe", "", null);
        }
    }

    private void verifyRuntime() {
        String forge = ModList.get().getModContainerById("forge").orElseThrow()
                .getModInfo().getVersion().toString();
        String version = ModList.get().getModContainerById("modhub").orElseThrow()
                .getModInfo().getVersion().toString();
        check(forge.equals(System.getProperty("modhub.smoke.expectedForge")), "Forge activo: " + forge);
        check(version.equals(System.getProperty("modhub.smoke.expectedVersion")), "Mod Hub activo: " + version);
        results.add("Fecha UTC: " + java.time.Instant.now());
        if (System.getProperty("modhub.smoke.jar") != null) {
            Path loaded = ModList.get().getModFileById("modhub").getFile().getFilePath();
            check(Files.isRegularFile(loaded) && loaded.toString().endsWith(".jar"),
                    "Mod Hub se carga desde un JAR, sin compilar sus fuentes");
            results.add("JAR original: " + System.getProperty("modhub.smoke.jar"));
            results.add("SHA-256 original: " + System.getProperty("modhub.smoke.sha256"));
            results.add("JAR adaptado por ForgeGradle: " + loaded);
        }
    }

    private void checkLayout() {
        screen.children().stream().filter(widget -> widget instanceof AbstractWidget)
                .map(widget -> (AbstractWidget) widget).forEach(widget -> {
                    check(widget.getX() >= 0 && widget.getY() >= 0
                                    && widget.getX() + widget.getWidth() <= screen.width
                                    && widget.getY() + widget.getHeight() <= screen.height,
                            "Control dentro de pantalla " + screen.width + "x" + screen.height);
                });
    }

    private EditBox search() throws Exception { return field("search", EditBox.class); }
    private Button button(String label) {
        return screen.children().stream().filter(widget -> widget instanceof Button)
                .map(widget -> (Button) widget).filter(widget -> widget.getMessage().getString().equals(label))
                .findFirst().orElseThrow();
    }
    private Button configButton() throws Exception { return field("configButton", Button.class); }
    private AbstractSelectionList<?> table() throws Exception { return field("table", AbstractSelectionList.class); }

    private <T> T field(String name, Class<T> type) throws Exception {
        Field field = ModHubScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(screen));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = ModHubScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        results.add("OK: " + message);
    }

    private void finish(Throwable failure) {
        finished = true;
        try {
            if (failure != null) {
                results.add("FAIL: " + failure);
                LogUtils.getLogger().error("UI_SMOKE_FAIL", failure);
            } else {
                results.add("UI_SMOKE_PASS");
                long checks = results.stream().filter(line -> line.startsWith("OK: ")).count();
                LogUtils.getLogger().info("UI_SMOKE_PASS: {} comprobaciones", checks);
            }
            Files.createDirectories(output);
            Files.write(output.resolve("resultado.txt"), results, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            LogUtils.getLogger().error("No se pudo guardar el resultado de las pruebas", exception);
        }
        minecraft.stop();
    }

    private static final class ConfigTestScreen extends Screen {
        private final Screen parent;
        ConfigTestScreen(Screen parent) {
            super(Component.literal("Configuración de prueba"));
            this.parent = parent;
        }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.drawCenteredString(font, title, width / 2, height / 2, 0xFFFFFF);
        }
    }
}

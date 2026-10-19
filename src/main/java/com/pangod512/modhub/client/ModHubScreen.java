package com.pangod512.modhub.client;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

public final class ModHubScreen extends Screen {
    private static final int ACCENT = 0xFF96CD70;
    private static final int TEXT = 0xFFEEEFEA;
    private static final int MUTED = 0xFFB3BAB8;
    private final Screen parent;
    private List<ModRow> allMods;
    private final OverrideStore overrides = new OverrideStore(FMLPaths.CONFIGDIR.get().resolve("modhub-overrides.json"));
    private final java.util.Map<String, ModClassification> automatic = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.concurrent.CompletableFuture<ModClassification>> analyses = new java.util.HashMap<>();
    private final ModrinthService modrinth = ModrinthService.get();
    private ModrinthResolver.Snapshot remote;
    private final ModLogos logos = new ModLogos();
    private final PanoramaRenderer panorama = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private ModTable table;
    private DetailsPanel details;
    private EditBox search;
    private Button typeButton;
    private Button configButton;
    private Button correctionButton;
    private ModRow selected;
    private ModType filter = ModType.ALL;
    private String query = "";
    private String configError;
    private boolean filterOpen;
    private int filterIndex;
    private int margin, tableWidth, tableTop, listBottom, detailX, detailWidth;

    public ModHubScreen(Screen parent) {
        super(Component.literal("Mod Hub"));
        this.parent = parent;
        this.allMods = ModList.get().getMods().stream().map(ModRow::from)
                .sorted(Comparator.comparing(row -> ModRow.normalize(row.name()))).toList();
        for (ModRow row : allMods) {
            ModClassification classification = row.classification();
            if (!classification.confirmed()) {
                classification = ModClassification.unknown("Analizando el archivo en segundo plano...");
                analyses.put(row.info().getModId(), JarInspector.analyze(row));
            }
            automatic.put(row.info().getModId(), classification);
        }
        applyClassifications();
    }

    @Override
    protected void init() {
        double oldScroll = table == null ? 0 : table.getScrollAmount();
        margin = width >= 700 ? 14 : 10;
        int available = width - margin * 2 - 8;
        detailWidth = Math.max(126, (int) (available * 0.33));
        tableWidth = available - detailWidth;
        detailX = margin + tableWidth + 8;
        tableTop = 36;
        listBottom = height - 46;
        filterOpen = false;

        table = addRenderableWidget(new ModTable(tableWidth - 4, tableTop + 23, listBottom - 2));
        table.setLeftPos(margin + 2);
        details = addRenderableWidget(new DetailsPanel(detailX + 2, tableTop, detailWidth - 12, listBottom - tableTop - 32));
        correctionButton = addRenderableWidget(vanillaButton(detailX + 6, listBottom - 27, detailWidth - 20, 20,
                Component.literal("Corregir tipo"), button -> {
                    if (selected != null) minecraft.setScreen(new TypeOverrideScreen(this, selected));
                }));

        int filterWidth = Math.min(134, Math.max(96, tableWidth / 3));
        search = new EditBox(font, margin, 10, tableWidth - filterWidth - 6, 20,
                Component.literal("Buscar mods por nombre, autor o ID"));
        search.setBordered(true);
        search.setMaxLength(128);
        search.setTextColor(TEXT);
        search.setTextColorUneditable(MUTED);
        search.setHint(Component.literal("Buscar mods..."));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            refreshRows(false);
        });
        addRenderableWidget(search);
        typeButton = addRenderableWidget(vanillaButton(margin + tableWidth - filterWidth, 10,
                filterWidth, 20, Component.empty(), button -> {
                    filterOpen = !filterOpen;
                    filterIndex = filter.ordinal();
                }));
        updateFilterLabel();

        int buttonWidth = (width - margin * 2 - 16) / 3;
        int footerY = height - 30;
        addRenderableWidget(vanillaButton(margin, footerY, buttonWidth, 20,
                Component.literal("Abrir carpeta de mods"), button ->
                        Util.getPlatform().openFile(FMLPaths.MODSDIR.get().toFile())));
        configButton = addRenderableWidget(vanillaButton(margin + buttonWidth + 8, footerY,
                buttonWidth, 20, Component.literal("Configurar"), button -> openConfig()));
        addRenderableWidget(vanillaButton(margin + (buttonWidth + 8) * 2, footerY,
                width - margin - (margin + (buttonWidth + 8) * 2), 20,
                Component.literal("Volver"), button -> onClose()));

        refreshRows(true);
        table.setScrollAmount(oldScroll);
    }

    static Button vanillaButton(int x, int y, int width, int height, Component text, Button.OnPress action) {
        return Button.builder(text, action).bounds(x, y, width, height).build();
    }

    private void updateFilterLabel() {
        typeButton.setMessage(Component.literal("Tipo: " + filter.label + " ▾"));
    }

    private void refreshRows(boolean preserveSelection) {
        ModRow previous = selected;
        table.clearRows();
        for (ModRow row : allMods) {
            if (row.matches(query, filter)) table.append(row);
        }
        var entry = table.children().stream().filter(item -> previous != null
                        && item.row.info().getModId().equals(previous.info().getModId()))
                .findFirst().orElse(table.children().isEmpty() ? null : table.children().get(0));
        table.setSelected(entry);
        if (!preserveSelection) table.setScrollAmount(0);
    }

    private void select(ModRow row) {
        boolean changed = !java.util.Objects.equals(selected, row);
        selected = row;
        if (changed) configError = null;
        if (details != null) details.refresh(!changed);
        if (correctionButton != null) correctionButton.visible = correctionButton.active = row != null;
        if (configButton != null) {
            configButton.visible = row != null && ConfigScreenHandler.getScreenFactoryFor(row.info()).isPresent();
            configButton.active = configButton.visible;
        }
    }

    private void openConfig() {
        if (selected == null) return;
        try {
            ConfigScreenHandler.getScreenFactoryFor(selected.info()).ifPresent(factory -> {
                Screen config = factory.apply(minecraft, this);
                if (config == null) throw new IllegalStateException("El mod no devolvio una pantalla.");
                minecraft.setScreen(config);
            });
        } catch (Exception exception) {
            LogUtils.getLogger().error("No se pudo abrir la configuracion de {}", selected.info().getModId(), exception);
            configError = "No se pudo abrir la configuración de este mod.";
            details.refresh();
        }
    }

    @Override
    public void tick() {
        search.tick();
        boolean changed = remote != modrinth.snapshot();
        if (changed) remote = modrinth.snapshot();
        var iterator = analyses.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getValue().isDone()) continue;
            ModClassification result;
            try {
                result = entry.getValue().join();
            } catch (RuntimeException exception) {
                result = ModClassification.unknown("No se pudo analizar el archivo; puedes corregir el tipo manualmente.");
            }
            automatic.put(entry.getKey(), result);
            iterator.remove();
            changed = true;
        }
        if (changed) {
            applyClassifications();
            refreshRows(true);
        }
    }

    private void applyClassifications() {
        remote = modrinth.snapshot();
        allMods = allMods.stream().map(row -> row.withClassification(overrides.apply(row.info().getModId(),
                row.version(), ModrinthClient.combine(automatic.getOrDefault(row.info().getModId(), row.classification()),
                        remote.classifications().get(row.info().getModId()))))).toList();
    }

    void saveType(String id, String version, ModType type) throws java.io.IOException {
        overrides.save(id, version, type);
        applyClassifications();
        refreshRows(true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the panorama/world visible. Screen.renderBackground would draw opaque dirt here.
        if (minecraft.level == null) panorama.render(partialTick, 1.0F);
        drawResultCount(graphics);
        if (overrides.warning() != null) {
            clippedText(graphics, "Aviso: revisa los detalles", detailX + 2, listBottom + 4, detailWidth - 4, 0xFFFFBE65);
        }
        graphics.drawString(font, "Detalles del mod", detailX + 7, 16, MUTED);

        frame(graphics, margin, tableTop, tableWidth, listBottom - tableTop, 0x88000000);
        graphics.hLine(margin + 1, margin + tableWidth - 2, tableTop + 22, 0xFFA0A0A0);
        drawTableHeader(graphics);
        if (table.children().isEmpty()) {
            graphics.drawCenteredString(font, "Sin resultados", margin + tableWidth / 2, tableTop + 42, TEXT);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (filterOpen) renderFilter(graphics, mouseX, mouseY);
    }

    private boolean wideTable() {
        return tableWidth >= 400;
    }

    private void drawResultCount(GuiGraphics graphics) {
        if (!query.isBlank() || filter != ModType.ALL) {
            graphics.drawString(font, table.children().size() + " resultados", margin, listBottom + 4, MUTED);
        }
    }

    private int typeColumnWidth() {
        return wideTable() ? 86 : Math.min(86, Math.max(58, table.getRowWidth() / 3));
    }

    private int typeColumnX() {
        return table.getRowLeft() + table.getRowWidth() - typeColumnWidth() - 4;
    }

    private void drawTableHeader(GuiGraphics graphics) {
        int x = margin + 8;
        graphics.drawString(font, "Nombre", table.getRowLeft() + 33, tableTop + 7, MUTED, false);
        if (wideTable()) {
            graphics.drawString(font, "Versión", x + (int) (tableWidth * .40), tableTop + 7, MUTED, false);
            graphics.drawString(font, "Autor", x + (int) (tableWidth * .57), tableTop + 7, MUTED, false);
        }
        graphics.drawString(font, "Tipo", typeColumnX() + 4, tableTop + 7, MUTED, false);
    }

    private void renderFilter(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        int x = typeButton.getX(), y = typeButton.getY() + typeButton.getHeight() + 2;
        int optionHeight = Math.min(20, (height - y - 8) / ModType.values().length);
        int menuWidth = Math.max(134, typeButton.getWidth());
        frame(graphics, x, y, menuWidth, ModType.values().length * optionHeight + 4, 0xF0000000);
        for (ModType type : ModType.values()) {
            int rowY = y + 2 + type.ordinal() * optionHeight;
            boolean hovered = mouseX >= x && mouseX < x + menuWidth && mouseY >= rowY && mouseY < rowY + optionHeight;
            if (hovered || type.ordinal() == filterIndex) {
                graphics.fill(x + 2, rowY, x + menuWidth - 2, rowY + optionHeight, 0xFF606060);
            }
            graphics.drawString(font, type.label, x + 8, rowY + (optionHeight - 8) / 2,
                    type == filter ? ACCENT : type.color, false);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (filterOpen && !typeButton.isMouseOver(mouseX, mouseY)) {
            int x = typeButton.getX(), y = typeButton.getY() + typeButton.getHeight() + 2;
            int optionHeight = Math.min(20, (height - y - 8) / ModType.values().length);
            if (button == 0 && mouseX >= x && mouseX < x + Math.max(134, typeButton.getWidth()) && mouseY >= y + 2) {
                int index = (int) (mouseY - y - 2) / optionHeight;
                if (index >= 0 && index < ModType.values().length) applyFilter(ModType.values()[index]);
            }
            filterOpen = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applyFilter(ModType type) {
        filter = type;
        updateFilterLabel();
        refreshRows(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (filterOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) filterOpen = false;
            else if (keyCode == GLFW.GLFW_KEY_DOWN) filterIndex = (filterIndex + 1) % ModType.values().length;
            else if (keyCode == GLFW.GLFW_KEY_UP) filterIndex = Math.floorMod(filterIndex - 1, ModType.values().length);
            else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyFilter(ModType.values()[filterIndex]);
                filterOpen = false;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || hasControlDown())) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return filterOpen || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        logos.close();
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
        graphics.renderOutline(x, y, w, h, 0xFFA0A0A0);
    }

    private void clippedText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        String result = font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text,
                Math.max(0, maxWidth - font.width("…"))) + "…";
        graphics.drawString(font, result, x, y, color);
    }

    private void badge(GuiGraphics graphics, ModClassification classification, int x, int y, int maxWidth) {
        ModType type = classification.type();
        String label = classification.label();
        int badgeWidth = Math.min(font.width(label) + 8, maxWidth);
        graphics.fill(x, y, x + badgeWidth, y + 14, 0xA0202928);
        graphics.renderOutline(x, y, badgeWidth, 14, 0x60000000 | (type.color & 0xFFFFFF));
        clippedText(graphics, label, x + 4, y + 3, badgeWidth - 8, type.color);
    }

    private final class ModTable extends ObjectSelectionList<ModTable.RowEntry> {
        ModTable(int listWidth, int top, int bottom) {
            super(ModHubScreen.this.minecraft, listWidth, ModHubScreen.this.height, top, bottom, 34);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
            setRenderSelection(true);
            centerListVertically = false;
        }

        void clearRows() { clearEntries(); }
        void append(ModRow row) { addEntry(new RowEntry(row)); }
        @Override public int getRowWidth() { return getWidth() - 10; }
        @Override public int getRowLeft() { return super.getRowLeft(); }
        @Override protected int getScrollbarPosition() { return getRight() - 5; }

        @Override
        public void setSelected(RowEntry entry) {
            super.setSelected(entry);
            select(entry == null ? null : entry.row);
        }

        private final class RowEntry extends ObjectSelectionList.Entry<RowEntry> {
            final ModRow row;
            RowEntry(ModRow row) { this.row = row; }

            @Override
            public Component getNarration() {
                return Component.literal(row.name() + ", " + row.version() + ", " + row.author() + ", "
                        + row.classification().label() + ", " + row.classification().origin().label);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return false;
                setSelected(this);
                return true;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                int contentHeight = rowHeight - 1;
                logos.draw(graphics, row, x + 5, y + (contentHeight - 22) / 2, 22);
                int nameX = x + 33;
                int typeX = typeColumnX();
                int badgeY = y + (contentHeight - 14) / 2;
                if (wideTable()) {
                    int versionX = margin + 8 + (int) (tableWidth * .40);
                    int authorX = margin + 8 + (int) (tableWidth * .57);
                    int textY = y + (contentHeight - font.lineHeight) / 2;
                    clippedText(graphics, row.name(), nameX, textY, versionX - nameX - 8, TEXT);
                    clippedText(graphics, row.version(), versionX, textY, authorX - versionX - 8, MUTED);
                    clippedText(graphics, row.author(), authorX, textY, typeX - authorX - 7, MUTED);
                } else {
                    int nameWidth = typeX - nameX - 8;
                    clippedText(graphics, row.name(), nameX, y + 4, nameWidth, TEXT);
                    clippedText(graphics, row.version() + " · " + row.author(), nameX, y + 17, nameWidth, MUTED);
                }
                badge(graphics, row.classification(), typeX, badgeY, typeColumnWidth());
            }
        }
    }

    private final class DetailsPanel extends AbstractScrollWidget {
        private record DetailLine(FormattedCharSequence text, int color, boolean centered) {}
        private final List<DetailLine> lines = new ArrayList<>();
        private int iconSize;
        private int contentHeight;

        DetailsPanel(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Detalles del mod"));
        }

        void refresh() {
            refresh(false);
        }

        void refresh(boolean preserveScroll) {
            double previousScroll = preserveScroll ? scrollAmount() : 0;
            lines.clear();
            setScrollAmount(0);
            iconSize = height < 180 ? 36 : 56;
            if (selected == null) {
                addLines("Selecciona un mod para ver sus detalles.", MUTED, false);
                if (overrides.warning() != null) addLines(overrides.warning(), 0xFFFFBE65, false);
                contentHeight = lines.size() * 12 + 20;
                return;
            }
            addLines(selected.name(), TEXT, true);
            blank();
            addLines("Versión: " + selected.version(), ACCENT, true);
            addLines("Autor: " + selected.author(), MUTED, true);
            blank();
            addLines("Tipo: " + selected.classification().label(), selected.type().color, false);
            blank();
            String description = selected.info().getDescription();
            addLines(description == null || description.isBlank() ? "Este mod no incluye una descripción." : description,
                    TEXT, false);
            blank();
            addLines("ID: " + selected.info().getModId(), MUTED, false);
            blank();
            ModSource source = selected.source();
            addLines("Archivo: " + source.fileName(), MUTED, false);
            if (source.kind() != ModSource.Kind.INTEGRATED) {
                addLines("Carga: " + source.kind().label, MUTED, false);
            }
            if (source.kind() == ModSource.Kind.NESTED) {
                addLines("Ubicación: " + source.location(), MUTED, false);
            }
            if (overrides.warning() != null) {
                blank();
                addLines(overrides.warning(), 0xFFFFBE65, false);
            }
            if (configError != null) {
                blank();
                addLines(configError, 0xFFFF9990, false);
            }
            contentHeight = iconSize + 24 + lines.size() * 12;
            setScrollAmount(previousScroll);
        }

        private void blank() {
            lines.add(new DetailLine(Component.empty().getVisualOrderText(), MUTED, false));
        }

        private void addLines(String text, int color, boolean centered) {
            for (FormattedCharSequence line : font.split(Component.literal(text), Math.max(24, width - 22))) {
                lines.add(new DetailLine(line, color, centered));
            }
        }

        @Override protected int getInnerHeight() { return contentHeight; }
        @Override protected double scrollRate() { return 24; }
        @Override protected void renderBackground(GuiGraphics graphics) {
            // Vanilla text-box border, with transparency to preserve the panorama/world.
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x88000000);
            graphics.renderOutline(getX(), getY(), width, height, isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int y = getY() + 8;
            if (selected != null) {
                logos.draw(graphics, selected, getX() + (width - iconSize) / 2, y, iconSize);
                y += iconSize + 12;
            }
            for (DetailLine line : lines) {
                if (line.centered) graphics.drawCenteredString(font, line.text, getX() + width / 2, y, line.color);
                else graphics.drawString(font, line.text, getX() + 8, y, line.color, false);
                y += 12;
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, selected == null ? getMessage()
                    : Component.literal(selected.name() + ". " + selected.info().getDescription()));
        }
    }
}

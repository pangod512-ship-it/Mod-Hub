package com.pangod512.modhub.client;

import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;

final class TypeOverrideScreen extends Screen {
    private final ModHubScreen parent;
    private final ModRow row;
    private final PanoramaRenderer panorama = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private String error;

    TypeOverrideScreen(ModHubScreen parent, ModRow row) {
        super(Component.literal("Corregir tipo"));
        this.parent = parent;
        this.row = row;
    }

    @Override protected void init() {
        int buttonWidth = Math.min(300, width - 32);
        int x = (width - buttonWidth) / 2;
        int y = Math.max(44, (height - 144) / 2);
        for (ModType type : new ModType[] {ModType.CLIENT, ModType.SERVER, ModType.BOTH, ModType.UNKNOWN}) {
            addRenderableWidget(ModHubScreen.vanillaButton(x, y, buttonWidth, 20, Component.literal(type.label), button -> save(type)));
            y += 24;
        }
        addRenderableWidget(ModHubScreen.vanillaButton(x, y, buttonWidth, 20,
                Component.literal("Restablecer detección"), button -> save(null)));
        addRenderableWidget(ModHubScreen.vanillaButton(x, y + 24, buttonWidth, 20,
                Component.literal("Volver"), button -> onClose()));
    }

    private void save(ModType type) {
        try {
            parent.saveType(row.info().getModId(), row.version(), type);
            onClose();
        } catch (IOException | RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("No se pudo guardar el tipo de {}", row.info().getModId(), exception);
            error = "No se pudo guardar. Revisa los permisos de la carpeta config.";
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) panorama.render(partialTick, 1.0F);
        graphics.fill(0, 0, width, height, 0xC0101416);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFF96CD70);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(row.name(), width - 32), width / 2, 26, 0xFFEEEFEA);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (error != null) {
            int y = height - 32;
            for (var line : font.split(Component.literal(error), width - 24)) {
                graphics.drawCenteredString(font, line, width / 2, y, 0xFFFF9990);
                y += 10;
            }
        }
    }
}

package com.pangod512.modhub.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;

public final class ModHubSettingsScreen extends Screen {
    private final Screen parent;
    private final ModrinthService service = ModrinthService.get();
    private final PanoramaRenderer panorama = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private String error;
    public ModHubSettingsScreen(Screen parent) { super(Component.literal("Configuración de Mod Hub")); this.parent = parent; }
    private Component label() { return Component.literal("Consultar Modrinth: " + (service.settings().enabled() ? "Sí" : "No")); }
    @Override protected void init() {
        int w = Math.min(300, width - 32), x = (width - w) / 2;
        addRenderableWidget(new HubButton(x, 48, w, 22, label(), button -> {
            try {
                service.settings().save(!service.settings().enabled());
                button.setMessage(label()); error = null; service.refresh();
            } catch (java.io.IOException | RuntimeException failure) { error = "No se pudo guardar la opción."; }
        }));
        addRenderableWidget(new HubButton(x, height - 32, w, 22, Component.literal("Volver"), button -> onClose()));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) panorama.render(partialTick, 1.0F);
        graphics.fill(0, 0, width, height, 0xC0101416);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFF96CD70);
        String text = "Identifica archivos nuevos o actualizados y guarda sus tipos para usarlos sin conexión. "
                + "Solo envía huellas de archivos; no sube los mods. Al desactivar, se conserva la caché. "
                + "Una consulta que ya esté en curso puede terminar.";
        text += "\n\n" + service.snapshot().status();
        if (service.settings().warning() != null) text += "\n" + service.settings().warning();
        if (error != null) text += "\n" + error;
        int y = 82;
        for (var line : font.split(Component.literal(text), Math.min(400, width - 32))) {
            if (y > height - 48) break;
            graphics.drawCenteredString(font, line, width / 2, y, 0xFFEEEFEA); y += 11;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

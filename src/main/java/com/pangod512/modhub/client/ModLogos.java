package com.pangod512.modhub.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.pangod512.modhub.core.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.forgespi.language.IModInfo;

final class ModLogos implements AutoCloseable {
  private record Logo(ResourceLocation texture, int width, int height) {}

  private final Map<String, Optional<Logo>> logos = new HashMap<>();

  private Optional<Logo> load(IModInfo mod) {
    if (mod.getLogoFile().isEmpty() || mod.getOwningFile() == null) return Optional.empty();
    try (InputStream stream =
        Files.newInputStream(mod.getOwningFile().getFile().findResource(mod.getLogoFile().get()))) {
      NativeImage image = NativeImage.read(stream);
      DynamicTexture texture = new DynamicTexture(image);
      texture.setFilter(mod.getLogoBlur(), false);
      ResourceLocation id =
          Minecraft.getInstance().getTextureManager().register("modhub_logo", texture);
      return Optional.of(new Logo(id, image.getWidth(), image.getHeight()));
    } catch (Exception exception) {
      LogUtils.getLogger().debug("No se pudo cargar el icono de {}", mod.getModId(), exception);
      return Optional.empty();
    }
  }

  void draw(GuiGraphics graphics, ModRow mod, int x, int y, int size) {
    Optional<Logo> logo = logos.computeIfAbsent(mod.info().getModId(), ignored -> load(mod.info()));
    if (logo.isPresent()) {
      Logo image = logo.get();
      float scale = Math.min((float) size / image.width, (float) size / image.height);
      int drawWidth = Math.max(1, Math.round(image.width * scale));
      int drawHeight = Math.max(1, Math.round(image.height * scale));
      RenderSystem.enableBlend();
      graphics.blit(
          image.texture,
          x + (size - drawWidth) / 2,
          y + (size - drawHeight) / 2,
          drawWidth,
          drawHeight,
          0,
          0,
          image.width,
          image.height,
          image.width,
          image.height);
      RenderSystem.disableBlend();
    } else {
      int color = 0xFF000000 | (mod.info().getModId().hashCode() & 0x005F5F5F) | 0x00303030;
      graphics.fill(x, y, x + size, y + size, color);
      graphics.renderOutline(x, y, size, size, 0xFF8A9693);
      String initial = mod.name().isBlank() ? "?" : mod.name().substring(0, 1);
      graphics.drawCenteredString(
          Minecraft.getInstance().font, initial, x + size / 2, y + (size - 8) / 2, 0xFFE6EBE7);
    }
  }

  @Override
  public void close() {
    logos
        .values()
        .forEach(
            logo ->
                logo.ifPresent(
                    image -> Minecraft.getInstance().getTextureManager().release(image.texture)));
    logos.clear();
  }
}

package com.pangod512.modhub.forge112;

import com.pangod512.modhub.core.*;
import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraftforge.fml.common.ModContainer;

final class TypeScreen extends GuiScreen {
  private final GuiScreen parent;
  private final ModContainer mod;
  private String error = "";

  TypeScreen(GuiScreen parent, ModContainer mod) {
    this.parent = parent;
    this.mod = mod;
  }

  public void initGui() {
    buttonList.clear();
    int y = 40;
    for (ModType type : ModType.values()) {
      buttonList.add(
          new GuiButton(
              type.ordinal(),
              width / 2 - 110,
              y,
              220,
              20,
              type == ModType.ALL ? "Detección automática" : type.label));
      y += 24;
    }
    buttonList.add(new GuiButton(99, width / 2 - 110, height - 30, 220, 20, "Volver"));
  }

  protected void actionPerformed(GuiButton button) {
    if (button.id == 99) {
      mc.displayGuiScreen(parent);
      return;
    }
    try {
      ModHub.overrides.save(
          mod.getModId(),
          mod.getDisplayVersion(),
          button.id == 0 ? null : ModType.values()[button.id]);
      mc.displayGuiScreen(parent);
    } catch (IOException failure) {
      error = "No se pudo guardar la corrección.";
    }
  }

  protected void keyTyped(char typed, int key) throws IOException {
    if (key == 1) mc.displayGuiScreen(parent);
    else super.keyTyped(typed, key);
  }

  public void drawScreen(int x, int y, float partial) {
    drawDefaultBackground();
    drawCenteredString(fontRenderer, "Corregir tipo: " + mod.getName(), width / 2, 15, 0x96CD70);
    drawCenteredString(fontRenderer, error, width / 2, height - 44, 0xffaaaa);
    super.drawScreen(x, y, partial);
  }
}

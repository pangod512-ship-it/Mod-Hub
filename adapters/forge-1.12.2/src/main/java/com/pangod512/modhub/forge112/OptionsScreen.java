package com.pangod512.modhub.forge112;

import com.pangod512.modhub.core.*;
import java.io.IOException;
import net.minecraft.client.gui.*;

public final class OptionsScreen extends GuiScreen {
  private final GuiScreen parent;
  private String error = "";

  public OptionsScreen(GuiScreen parent) {
    this.parent = parent;
  }

  public void initGui() {
    buttonList.clear();
    buttonList.add(
        new GuiButton(
            0,
            width / 2 - 130,
            48,
            260,
            20,
            "Consultar Modrinth: " + (ModHub.identification.settings().enabled() ? "Sí" : "No")));
    GuiButton cf = new GuiButton(1, width / 2 - 130, 76, 260, 20, "CurseForge: no disponible");
    cf.enabled = CurseForgeAccess.available();
    buttonList.add(cf);
    buttonList.add(new GuiButton(2, width / 2 - 100, height - 30, 200, 20, "Volver"));
  }

  protected void actionPerformed(GuiButton button) {
    try {
      if (button.id == 0) {
        ModHub.identification.settings().save(!ModHub.identification.settings().enabled());
        ModHub.identification.refresh();
        initGui();
      }
      if (button.id == 1 && CurseForgeAccess.available()) {
        ModHub.identification
            .settings()
            .saveCurseforge(!ModHub.identification.settings().curseforgeEnabled());
        ModHub.identification.refresh();
      }
      if (button.id == 2) mc.displayGuiScreen(parent);
    } catch (IOException failure) {
      error = "No se pudo guardar la opción.";
    }
  }

  protected void keyTyped(char typed, int key) throws IOException {
    if (key == 1) mc.displayGuiScreen(parent);
    else super.keyTyped(typed, key);
  }

  public void drawScreen(int x, int y, float partial) {
    drawDefaultBackground();
    drawCenteredString(fontRenderer, "Configuración de Mod Hub", width / 2, 16, 0x96CD70);
    int lineY = 110;
    for (String line :
        fontRenderer.listFormattedStringToWidth(
            "Las consultas se realizan en segundo plano. Se envían huellas de archivos, no los JAR"
                + " ni rutas personales. La caché permite consultar resultados sin conexión. "
                + CurseForgeAccess.reason()
                + " "
                + error,
            Math.max(60, width - 40))) {
      if (lineY > height - 45) break;
      drawCenteredString(fontRenderer, line, width / 2, lineY, 0xeeeeee);
      lineY += 11;
    }
    super.drawScreen(x, y, partial);
  }
}

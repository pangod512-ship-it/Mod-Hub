package com.pangod512.modhub.forge112;

import com.pangod512.modhub.core.*;
import java.io.*;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.*;
import net.minecraftforge.fml.common.*;
import org.lwjgl.input.*;

public final class HubScreen extends GuiScreen {
  private final GuiScreen parent;
  private GuiTextField search;
  private ModType filter = ModType.ALL;
  private List<ModContainer> mods = new ArrayList<>();
  private ModContainer selected;
  private int listOffset, detailOffset, leftWidth, listTop = 64, listBottom;
  private GuiButton configure, correct;
  private final Map<String, ResourceLocation> logos = new HashMap<>();
  private final Set<String> missingLogos = new HashSet<>();
  private String query = "", error = "";

  public HubScreen(GuiScreen parent) {
    this.parent = parent;
  }

  public void initGui() {
    leftWidth = (width - 30) * 2 / 3;
    listBottom = height - 47;
    search = new GuiTextField(0, fontRenderer, 10, 22, Math.max(30, leftWidth - 112), 20);
    search.setMaxStringLength(160);
    search.setText(query);
    buttonList.clear();
    buttonList.add(new GuiButton(0, leftWidth - 96, 22, 106, 20, "Tipo: " + filter.label));
    int w = Math.max(40, (width - 40) / 3);
    buttonList.add(new GuiButton(1, 10, height - 27, w, 20, "Abrir carpeta de mods"));
    configure = new GuiButton(2, 20 + w, height - 27, w, 20, "Configurar");
    buttonList.add(configure);
    buttonList.add(new GuiButton(3, width - w - 10, height - 27, w, 20, "Volver"));
    correct =
        new GuiButton(
            4,
            leftWidth + 20,
            listBottom - 22,
            Math.max(40, width - leftWidth - 30),
            20,
            "Corregir tipo");
    buttonList.add(correct);
    refresh();
  }

  private void refresh() {
    mods.clear();
    for (ModContainer mod : Loader.instance().getActiveModList())
      if (ModHub.descriptor(mod).matches(query, filter, ModHub.classification(mod))) mods.add(mod);
    mods.sort(Comparator.comparing(ModContainer::getName, String.CASE_INSENSITIVE_ORDER));
    if (selected == null || !mods.contains(selected))
      selected = mods.isEmpty() ? null : mods.get(0);
    listOffset =
        Math.max(
            0,
            Math.min(
                listOffset, Math.max(0, mods.size() - Math.max(1, (listBottom - listTop) / 40))));
    IModGuiFactory factory =
        selected == null ? null : FMLClientHandler.instance().getGuiFactoryFor(selected);
    configure.visible = factory != null && factory.hasConfigGui();
    correct.enabled = selected != null;
  }

  public void updateScreen() {
    search.updateCursorCounter();
    refresh();
  }

  protected void actionPerformed(GuiButton button) {
    if (button.id == 0) {
      filter = ModType.values()[(filter.ordinal() + 1) % ModType.values().length];
      button.displayString = "Tipo: " + filter.label;
      listOffset = 0;
      refresh();
    }
    if (button.id == 1)
      try {
        java.awt.Desktop.getDesktop().open(ModHub.modsDirectory.toFile());
      } catch (Exception failure) {
        error = "No se pudo abrir la carpeta.";
      }
    if (button.id == 2 && selected != null)
      try {
        IModGuiFactory factory = FMLClientHandler.instance().getGuiFactoryFor(selected);
        if (factory != null && factory.hasConfigGui())
          mc.displayGuiScreen(factory.createConfigGui(this));
      } catch (RuntimeException failure) {
        error = "No se pudo abrir la configuración.";
      }
    if (button.id == 3) mc.displayGuiScreen(parent);
    if (button.id == 4 && selected != null) mc.displayGuiScreen(new TypeScreen(this, selected));
  }

  protected void keyTyped(char typed, int key) throws IOException {
    if (key == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (key == Keyboard.KEY_F && isCtrlKeyDown()) {
      search.setFocused(true);
      return;
    }
    if (search.textboxKeyTyped(typed, key)) {
      query = search.getText();
      listOffset = 0;
      refresh();
    } else super.keyTyped(typed, key);
  }

  protected void mouseClicked(int x, int y, int button) throws IOException {
    search.mouseClicked(x, y, button);
    if (button == 0 && x >= 10 && x <= leftWidth + 10 && y >= listTop && y < listBottom) {
      int index = listOffset + (y - listTop) / 40;
      if (index < mods.size()) {
        selected = mods.get(index);
        detailOffset = 0;
      }
    }
    super.mouseClicked(x, y, button);
  }

  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel == 0) return;
    int x = Mouse.getEventX() * width / mc.displayWidth,
        y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
    if (y < listTop || y >= listBottom) return;
    if (x <= leftWidth + 10) listOffset += wheel > 0 ? -1 : 1;
    else detailOffset = Math.max(0, detailOffset + (wheel > 0 ? -2 : 2));
    refresh();
  }

  private void logo(ModContainer mod, int x, int y, int size) {
    String id = mod.getModId();
    ResourceLocation texture = logos.get(id);
    if (texture == null && !missingLogos.contains(id)) {
      missingLogos.add(id);
      try {
        String name = mod.getMetadata().logoFile;
        if (name != null
            && !name.isEmpty()
            && mod.getSource() != null
            && mod.getSource().isFile()) {
          byte[] data = LogoData.read(mod.getSource().toPath(), name);
          java.awt.image.BufferedImage image =
              javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
          texture =
              mc.getTextureManager()
                  .getDynamicTextureLocation("modhub-" + id, new DynamicTexture(image));
          logos.put(id, texture);
        }
      } catch (IOException | RuntimeException ignored) {
        /* An unreadable logo must not prevent the mod list from rendering. */
      }
    }
    if (texture != null) {
      GlStateManager.color(1, 1, 1, 1);
      mc.getTextureManager().bindTexture(texture);
      drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
    } else {
      drawRect(x, y, x + size, y + size, 0xff505f42);
      drawCenteredString(
          fontRenderer,
          mod.getName().isEmpty() ? "?" : mod.getName().substring(0, 1),
          x + size / 2,
          y + (size - 8) / 2,
          0xeeeeee);
    }
  }

  public void drawScreen(int x, int y, float partial) {
    if (mc.world == null) drawDefaultBackground();
    drawString(fontRenderer, "Mod Hub · " + mods.size() + " mods", 10, 8, 0x96cd70);
    drawRect(8, 48, leftWidth + 12, listBottom, 0x99000000);
    drawRect(leftWidth + 18, 48, width - 8, listBottom, 0x99000000);
    drawString(fontRenderer, "Nombre", 42, 51, 0xbbbbbb);
    drawString(fontRenderer, "Tipo", Math.max(42, leftWidth - 97), 51, 0xbbbbbb);
    search.drawTextBox();
    if (search.getText().isEmpty() && !search.isFocused())
      drawString(fontRenderer, "Buscar mods...", 14, 28, 0x888888);
    int count = Math.max(1, (listBottom - listTop) / 40);
    for (int row = 0; row < count && listOffset + row < mods.size(); row++) {
      ModContainer mod = mods.get(listOffset + row);
      int yy = listTop + row * 40;
      if (mod == selected)
        drawRect(10, yy, leftWidth + 10, Math.min(yy + 39, listBottom), 0x665a7f3d);
      logo(mod, 14, yy + 4, 26);
      ModDescriptor d = ModHub.descriptor(mod);
      drawString(
          fontRenderer,
          fontRenderer.trimStringToWidth(d.name(), Math.max(10, leftWidth - 150)),
          44,
          yy + 5,
          0xffffff);
      drawString(
          fontRenderer,
          fontRenderer.trimStringToWidth(
              d.version() + " · " + d.author(), Math.max(10, leftWidth - 150)),
          44,
          yy + 20,
          0xaaaaaa);
      ModClassification type = ModHub.classification(mod);
      drawString(
          fontRenderer,
          fontRenderer.trimStringToWidth(type.label(), 100),
          leftWidth - 97,
          yy + 12,
          type.type().color);
    }
    if (listOffset > 0) drawString(fontRenderer, "↑", leftWidth, 51, 0xffffff);
    if (listOffset + count < mods.size())
      drawString(fontRenderer, "↓", leftWidth, listBottom - 10, 0xffffff);
    if (!query.isEmpty() || filter != ModType.ALL)
      drawString(fontRenderer, mods.size() + " mods", 10, listBottom + 3, 0x96cd70);
    if (selected != null) {
      int dx = leftWidth + 24, dw = Math.max(20, width - dx - 15);
      ModDescriptor d = ModHub.descriptor(selected);
      List<String> lines = new ArrayList<>();
      lines.addAll(fontRenderer.listFormattedStringToWidth(d.name(), dw));
      lines.add("");
      lines.addAll(fontRenderer.listFormattedStringToWidth("Versión: " + d.version(), dw));
      lines.addAll(fontRenderer.listFormattedStringToWidth("Autor: " + d.author(), dw));
      lines.add("");
      lines.addAll(
          fontRenderer.listFormattedStringToWidth(
              "Tipo: " + ModHub.classification(selected).label(), dw));
      lines.add("");
      lines.addAll(fontRenderer.listFormattedStringToWidth(d.description(), dw));
      lines.add("");
      lines.addAll(fontRenderer.listFormattedStringToWidth("ID: " + d.id(), dw));
      int visible = Math.max(1, (listBottom - listTop - 28) / 11);
      detailOffset = Math.min(detailOffset, Math.max(0, lines.size() - visible));
      for (int i = detailOffset; i < lines.size() && i < detailOffset + visible; i++)
        drawString(fontRenderer, lines.get(i), dx, listTop + (i - detailOffset) * 11, 0xeeeeee);
    }
    if (!error.isEmpty()) drawCenteredString(fontRenderer, error, width / 2, 8, 0xffaaaa);
    super.drawScreen(x, y, partial);
  }

  public boolean doesGuiPauseGame() {
    return false;
  }

  public void onGuiClosed() {
    for (ResourceLocation logo : logos.values()) mc.getTextureManager().deleteTexture(logo);
    logos.clear();
    missingLogos.clear();
  }
}

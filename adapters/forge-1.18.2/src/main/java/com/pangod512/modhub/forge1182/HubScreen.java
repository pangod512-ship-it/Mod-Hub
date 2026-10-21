package com.pangod512.modhub.forge1182;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pangod512.modhub.core.*;
import java.util.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

public final class HubScreen extends Screen {
  private final Screen parent;
  private final boolean options;
  private EditBox search;
  private ModType filter = ModType.ALL;
  private final List<IModInfo> visible = new ArrayList<>();
  private IModInfo selected;
  private int offset, detailOffset, left, bottom;
  private String query = "", error = "";
  private boolean choosingType;
  private Button configure;
  private final Map<String, net.minecraft.resources.ResourceLocation> logos = new HashMap<>();
  private final Set<String> missingLogos = new HashSet<>();

  private net.minecraft.resources.ResourceLocation logo(IModInfo mod) {
    String id = mod.getModId();
    if (logos.containsKey(id)) return logos.get(id);
    if (missingLogos.contains(id)) return null;
    try {
      String name =
          ((net.minecraftforge.fml.loading.moddiscovery.ModInfo) mod).getLogoFile().orElse(null);
      byte[] data = LogoData.read(ClientBootstrap.descriptor(mod).file(), name);
      com.mojang.blaze3d.platform.NativeImage image =
          com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(data));
      net.minecraft.resources.ResourceLocation location =
          minecraft
              .getTextureManager()
              .register(
                  "modhub_" + id, new net.minecraft.client.renderer.texture.DynamicTexture(image));
      logos.put(id, location);
      return location;
    } catch (Exception failure) {
      missingLogos.add(id);
      return null;
    }
  }

  public void removed() {
    for (net.minecraft.resources.ResourceLocation logo : logos.values())
      minecraft.getTextureManager().release(logo);
    logos.clear();
    missingLogos.clear();
    super.removed();
  }

  public HubScreen(Screen parent, boolean options) {
    super(text("Mod Hub"));
    this.parent = parent;
    this.options = options;
  }

  private static Component text(String s) {
    return new net.minecraft.network.chat.TextComponent(s);
  }

  private Button button(int x, int y, int w, String label, Button.OnPress press) {
    return addRenderableWidget(new Button(x, y, w, 20, text(label), press));
  }

  protected void init() {
    left = (width - 30) * 2 / 3;
    bottom = height - 47;
    if (options) {
      button(
          width / 2 - 125,
          45,
          250,
          "Consultar Modrinth: "
              + (ClientBootstrap.identification.settings().enabled() ? "Sí" : "No"),
          b -> {
            try {
              ClientBootstrap.identification
                  .settings()
                  .save(!ClientBootstrap.identification.settings().enabled());
              ClientBootstrap.identification.refresh();
              b.setMessage(
                  text(
                      "Consultar Modrinth: "
                          + (ClientBootstrap.identification.settings().enabled() ? "Sí" : "No")));
            } catch (java.io.IOException e) {
              error = "No se pudo guardar.";
            }
          });
      button(width / 2 - 125, 75, 250, "CurseForge: no disponible", b -> {}).active =
          CurseForgeAccess.available();
      button(width / 2 - 100, height - 28, 200, "Volver", b -> onClose());
      return;
    }
    search = new EditBox(font, 10, 22, Math.max(30, left - 112), 20, text("Buscar mods"));
    search.setMaxLength(160);
    search.setSuggestion(query.isEmpty()?"Buscar mods...":null);
    search.setValue(query);
    search.setResponder(
        s -> {
          query = s;
          search.setSuggestion(s.isEmpty()?"Buscar mods...":null);
          offset = 0;
          refresh();
        });
    addRenderableWidget(search);
    button(
        left - 96,
        22,
        106,
        "Tipo: " + filter.label,
        b -> {
          filter = ModType.values()[(filter.ordinal() + 1) % ModType.values().length];
          b.setMessage(text("Tipo: " + filter.label));
          offset = 0;
          refresh();
        });
    int w = Math.max(40, (width - 40) / 3);
    button(
        10,
        height - 27,
        w,
        "Abrir carpeta de mods",
        b -> {
          try {
            java.awt.Desktop.getDesktop()
                .open(net.minecraftforge.fml.loading.FMLPaths.MODSDIR.get().toFile());
          } catch (Exception e) {
            error = "No se pudo abrir la carpeta.";
          }
        });
    configure =
        button(
            20 + w,
            height - 27,
            w,
            "Configurar",
            b -> {
              if (selected != null)
                try {
                  ClientBootstrap.config(selected)
                      .ifPresent(f -> minecraft.setScreen(f.apply(minecraft, this)));
                } catch (RuntimeException e) {
                  error = "No se pudo abrir la configuración.";
                }
            });
    button(width - w - 10, height - 27, w, "Volver", b -> onClose());
    button(
        left + 20,
        bottom - 22,
        Math.max(40, width - left - 30),
        "Corregir tipo",
        b -> {
          if (selected != null) choosingType = !choosingType;
        });
    refresh();
  }

  private void refresh() {
    if (options || search == null) return;
    visible.clear();
    for (IModInfo mod : ModList.get().getMods())
      if (ClientBootstrap.descriptor(mod)
          .matches(query, filter, ClientBootstrap.classification(mod))) visible.add(mod);
    visible.sort(Comparator.comparing(IModInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER));
    if (selected == null || !visible.contains(selected))
      selected = visible.isEmpty() ? null : visible.get(0);
    offset =
        Math.max(
            0, Math.min(offset, Math.max(0, visible.size() - Math.max(1, (bottom - 64) / 40))));
    if (configure != null)
      configure.visible = selected != null && ClientBootstrap.config(selected).isPresent();
  }

  public void tick() {
    if (search != null) search.tick();
    refresh();
  }

  public boolean isPauseScreen() {
    return false;
  }

  public void onClose() {
    minecraft.setScreen(parent);
  }

  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key == 256) {
      if (choosingType) choosingType = false;
      else onClose();
      return true;
    }
    if (search != null && key == 70 && hasControlDown()) {
      search.setFocus(true);
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  public boolean mouseClicked(double x, double y, int mouse) {
    if (!options && mouse == 0 && choosingType && selected != null) {
      int index = ((int) y - 64) / 22;
      if (x >= left + 20 && x <= width - 10 && y >= 64 && index < ModType.values().length) {
        try {
          ClientBootstrap.overrides.save(
              selected.getModId(),
              selected.getVersion().toString(),
              index == 0 ? null : ModType.values()[index]);
          choosingType = false;
          refresh();
        } catch (java.io.IOException e) {
          error = "No se pudo guardar.";
        }
        return true;
      }
    }
    if (!options && mouse == 0 && x >= 10 && x <= left + 10 && y >= 64 && y < bottom) {
      int index = offset + ((int) y - 64) / 40;
      if (index < visible.size()) {
        selected = visible.get(index);
        detailOffset = 0;
        choosingType = false;
        refresh();
        return true;
      }
    }
    return super.mouseClicked(x, y, mouse);
  }

  public boolean mouseScrolled(double x, double y, double delta) {
    if (!options && y >= 64 && y < bottom) {
      if (x <= left + 10) offset += delta > 0 ? -1 : 1;
      else detailOffset = Math.max(0, detailOffset + (delta > 0 ? -2 : 2));
      refresh();
      return true;
    }
    return super.mouseScrolled(x, y, delta);
  }

  private void line(PoseStack pose, String s, int x, int y, int color) {
    font.draw(pose, s, x, y, color);
  }

  private String clip(String s, int w) {
    return font.plainSubstrByWidth(s, Math.max(8, w));
  }

  public void render(PoseStack pose, int x, int y, float partial) {
    if (minecraft.level == null) renderBackground(pose);
    if (options) {
      fill(pose, 0, 0, width, height, 0x99101416);
      drawCenteredString(pose, font, "Configuración de Mod Hub", width / 2, 15, 0x96cd70);
      int yy = 110;
      for (net.minecraft.util.FormattedCharSequence formatted :
          font.split(
              text(
                  "Identificación en segundo plano y caché sin conexión. "
                      + CurseForgeAccess.reason()
                      + " "
                      + error),
              Math.max(40, width - 40))) {
        if (yy > height - 45) break;
        font.draw(pose, formatted, 20, yy, 0xeeeeee);
        yy += 11;
      }
      super.render(pose, x, y, partial);
      return;
    }
    line(pose, "Mod Hub · " + visible.size() + " mods", 10, 8, 0x96cd70);
    fill(pose, 8, 48, left + 12, bottom, 0x99000000);
    fill(pose, left + 18, 48, width - 8, bottom, 0x99000000);
    line(pose, "Nombre", 42, 51, 0xbbbbbb);
    line(pose, "Tipo", left - 97, 51, 0xbbbbbb);
    int count = Math.max(1, (bottom - 64) / 40);
    for (int i = 0; i < count && offset + i < visible.size(); i++) {
      IModInfo mod = visible.get(offset + i);
      int yy = 64 + i * 40;
      ModDescriptor d = ClientBootstrap.descriptor(mod);
      if (mod == selected) fill(pose, 10, yy, left + 10, Math.min(yy + 39, bottom), 0x665a7f3d);
      net.minecraft.resources.ResourceLocation icon = logo(mod);
      if (icon != null) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, icon);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        blit(pose, 14, yy + 4, 0, 0, 26, 26, 26, 26);
      } else {
        fill(pose, 14, yy + 4, 40, yy + 30, 0xff505f42);
        line(pose, d.name().isEmpty() ? "?" : d.name().substring(0, 1), 22, yy + 12, 0xffffff);
      }
      line(pose, clip(d.name(), left - 150), 44, yy + 5, 0xffffff);
      line(pose, clip(d.version() + " · " + d.author(), left - 150), 44, yy + 20, 0xaaaaaa);
      ModClassification type = ClientBootstrap.classification(mod);
      line(pose, clip(type.label(), 100), left - 97, yy + 12, type.type().color);
    }
    if (!query.isEmpty() || filter != ModType.ALL)
      line(pose, visible.size() + " mods", 10, bottom + 3, 0x96cd70);
    if (selected != null) {
      int dx = left + 24, dw = Math.max(20, width - dx - 15), yy = 64;
      if (choosingType) {
        for (ModType t : ModType.values()) {
          fill(pose, dx, yy, width - 12, yy + 19, 0xff353c32);
          line(pose, t == ModType.ALL ? "Detección automática" : t.label, dx + 4, yy + 5, 0xeeeeee);
          yy += 22;
        }
      } else {
        ModDescriptor d = ClientBootstrap.descriptor(selected);
        String detail =
            d.name()
                + "\n\nVersión: "
                + d.version()
                + "\nAutor: "
                + d.author()
                + "\n\nTipo: "
                + ClientBootstrap.classification(selected).label()
                + "\n\n"
                + d.description()
                + "\n\nID: "
                + d.id();
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
            font.split(text(detail), dw);
        int capacity = Math.max(1, (bottom - 64 - 28) / 11);
        detailOffset = Math.min(detailOffset, Math.max(0, lines.size() - capacity));
        for (int i = detailOffset; i < lines.size() && i < detailOffset + capacity; i++) {
          font.draw(pose, lines.get(i), dx, yy, 0xeeeeee);
          yy += 11;
        }
      }
    }
    if (!error.isEmpty()) drawCenteredString(pose, font, error, width / 2, 8, 0xffaaaa);
    super.render(pose, x, y, partial);
  }
}

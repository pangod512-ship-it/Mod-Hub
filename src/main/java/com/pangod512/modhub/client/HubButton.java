package com.pangod512.modhub.client;

import com.pangod512.modhub.core.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class HubButton extends Button {
  HubButton(int x, int y, int width, int height, Component label, OnPress action) {
    super(x, y, width, height, label, action, DEFAULT_NARRATION);
  }

  @Override
  protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    int x = getX(), y = getY();
    boolean highlighted = isHoveredOrFocused();
    int border = highlighted ? 0xFF8DBD69 : 0xFF696D70;
    graphics.fill(x, y, x + width, y + height, 0xEE0D1011);
    graphics.fillGradient(
        x + 2,
        y + 2,
        x + width - 2,
        y + height - 2,
        highlighted ? 0xEA394A32 : 0xEA34383A,
        highlighted ? 0xEA202D1D : 0xEA1C2022);
    graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, border);
    graphics.hLine(x + 3, x + width - 4, y + 2, 0x557D8384);
    var font = Minecraft.getInstance().font;
    List<FormattedCharSequence> lines = font.split(getMessage(), Math.max(12, width - 10));
    int count = Math.min(2, lines.size());
    int lineY = y + (height - count * font.lineHeight) / 2 + 1;
    for (int i = 0; i < count; i++) {
      graphics.drawCenteredString(
          font, lines.get(i), x + width / 2, lineY, active ? 0xFFF3F3F0 : 0xFF838787);
      lineY += font.lineHeight;
    }
  }
}

package com.pangod512.modhub.forge112;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

public final class ConfigFactory implements IModGuiFactory {
  public void initialize(Minecraft minecraft) {}

  public boolean hasConfigGui() {
    return true;
  }

  public GuiScreen createConfigGui(GuiScreen parent) {
    return new OptionsScreen(parent);
  }

  public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
    return java.util.Collections.emptySet();
  }
}

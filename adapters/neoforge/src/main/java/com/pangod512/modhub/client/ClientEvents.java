package com.pangod512.modhub.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ModListScreen;

@EventBusSubscriber(modid = "modhub", value = Dist.CLIENT)
public final class ClientEvents {
  private ClientEvents() {}

  @SubscribeEvent
  public static void onScreenOpening(ScreenEvent.Opening event) {
    if (event.getNewScreen() instanceof ModListScreen) {
      event.setNewScreen(new ModHubScreen(event.getCurrentScreen()));
    }
  }
}

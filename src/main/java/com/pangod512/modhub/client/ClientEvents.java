package com.pangod512.modhub.client;

import com.pangod512.modhub.ModHub;
import com.pangod512.modhub.core.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = ModHub.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.CLIENT)
public final class ClientEvents {
  private ClientEvents() {}

  @SubscribeEvent
  public static void onScreenOpening(ScreenEvent.Opening event) {
    if (event.getNewScreen() instanceof ModListScreen) {
      event.setNewScreen(new ModHubScreen(event.getCurrentScreen()));
    }
  }
}

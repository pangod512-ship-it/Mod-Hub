package com.pangod512.modhub;

import com.pangod512.modhub.client.ModHubSettingsScreen;
import com.pangod512.modhub.client.ModrinthService;
import com.pangod512.modhub.core.Messages;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(ModHub.MOD_ID)
public final class ModHub {
  public static final String MOD_ID = "modhub";

  public ModHub(IEventBus bus, ModContainer container) {
    bus.addListener(
        (FMLClientSetupEvent event) ->
            event.enqueueWork(
                () -> {
                  Messages.install(key -> net.minecraft.client.resources.language.I18n.get(key));
                  container.registerExtensionPoint(
                      IConfigScreenFactory.class,
                      (owner, parent) -> new ModHubSettingsScreen(parent));
                  ModrinthService.get().start();
                }));
  }
}

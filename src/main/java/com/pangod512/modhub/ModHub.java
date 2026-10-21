package com.pangod512.modhub;

import com.mojang.logging.LogUtils;
import com.pangod512.modhub.core.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ModHub.MOD_ID)
public final class ModHub {
  public static final String MOD_ID = "modhub";
  private static final Logger LOGGER = LogUtils.getLogger();

  public ModHub(FMLJavaModLoadingContext context) {
    context.getModEventBus().addListener(this::onClientSetup);
  }

  private void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          net.minecraftforge.fml.ModList.get()
              .getModContainerById(MOD_ID)
              .orElseThrow()
              .registerExtensionPoint(
                  net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                  () ->
                      new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                          (minecraft, parent) ->
                              new com.pangod512.modhub.client.ModHubSettingsScreen(parent)));
          com.pangod512.modhub.client.ModrinthService.get().start();
        });
    LOGGER.info("Mod Hub: cliente preparado.");
  }
}

package com.pangod512.modhub.forge1192;

@net.minecraftforge.fml.common.Mod("modhub")
public final class ModHub {
  public ModHub() {
    if (net.minecraftforge.fml.loading.FMLEnvironment.dist
        == net.minecraftforge.api.distmarker.Dist.CLIENT) {
      ClientBootstrap.register();
      net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get()
          .getModEventBus()
          .addListener(this::setup);
    }
  }

  private void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
    event.enqueueWork(ClientBootstrap::start);
  }
}

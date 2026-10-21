package com.pangod512.modhub.client;

import com.pangod512.modhub.ModHub;
import com.pangod512.modhub.core.*;
import net.minecraftforge.forgespi.language.IModInfo;

public final class ModClassifier {
  private ModClassifier() {}

  public static ModClassification declared(IModInfo mod) {
    if (ModHub.MOD_ID.equals(mod.getModId())) {
      return new ModClassification(
          ModType.CLIENT,
          ModClassification.Origin.DECLARED,
          "Mod Hub es un mod de interfaz exclusivo del cliente.");
    }
    if ("minecraft".equals(mod.getModId()) || "forge".equals(mod.getModId())) {
      return new ModClassification(
          ModType.BOTH,
          ModClassification.Origin.DECLARED,
          "Componente integrado de Minecraft/Forge, disponible en cliente y servidor.");
    }
    // Neither displayTest nor a dependency's side declares the whole mod's side.
    try {
      if (mod.getOwningFile() != null
          && mod.getOwningFile().getConfig() != null
          && Boolean.TRUE.equals(
              mod.getOwningFile().getConfig().getConfigElement("clientSideOnly").orElse(false))) {
        return new ModClassification(
            ModType.CLIENT,
            ModClassification.Origin.DECLARED,
            "El archivo declara clientSideOnly=true en sus metadatos de Forge.");
      }
    } catch (RuntimeException exception) {
      return ModClassification.unknown("No se pudieron leer los metadatos de clasificación.");
    }
    return ModClassification.unknown("No hay una declaración explícita del lado del mod.");
  }
}

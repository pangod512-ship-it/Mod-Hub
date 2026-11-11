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
          Messages.tr(
              "modhub.mod_hub_is_a_client_only_interface_mod",
              "Mod Hub es un mod de interfaz exclusivo del cliente."));
    }
    if ("minecraft".equals(mod.getModId()) || "forge".equals(mod.getModId())) {
      return new ModClassification(
          ModType.BOTH,
          ModClassification.Origin.DECLARED,
          Messages.tr(
              "modhub.built_in_minecraft_forge_component_available_on_client_and_server",
              "Componente integrado de Minecraft/Forge, disponible en cliente y servidor."));
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
            Messages.tr(
                "modhub.the_file_declares_clientsideonly_true_in_its_forge_metadata",
                "El archivo declara clientSideOnly=true en sus metadatos de Forge."));
      }
    } catch (RuntimeException exception) {
      return ModClassification.unknown(
          Messages.tr(
              "modhub.could_not_read_the_classification_metadata",
              "No se pudieron leer los metadatos de clasificación."));
    }
    return ModClassification.unknown(
        Messages.tr(
            "modhub.the_mod_does_not_explicitly_declare_its_side",
            "No hay una declaración explícita del lado del mod."));
  }
}

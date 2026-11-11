package com.pangod512.modhub.core;

/**
 * Deployment gate: no credential is bundled and the API's environment contract is not yet verified.
 */
public final class CurseForgeAccess {
  private CurseForgeAccess() {}

  public static boolean available() {
    return false;
  }

  public static String reason() {
    return Messages.tr(
        "modhub.curseforge_unavailable_api_access_and_environment_data_have_not_been_v",
        "CurseForge no disponible: acceso API y datos de entorno pendientes de validar.");
  }
}

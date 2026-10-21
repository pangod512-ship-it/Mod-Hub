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
    return "CurseForge no disponible: acceso API y datos de entorno pendientes de validar.";
  }
}

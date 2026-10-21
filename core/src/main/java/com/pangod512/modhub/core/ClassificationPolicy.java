package com.pangod512.modhub.core;

public final class ClassificationPolicy {
  private ClassificationPolicy() {}

  public static ModClassification resolve(
      ModClassification local, ModClassification modrinth, ModClassification curseforge) {
    if (local.origin() == ModClassification.Origin.MANUAL
        || local.origin() == ModClassification.Origin.DECLARED) return local;
    if (modrinth != null && modrinth.type() != ModType.UNKNOWN) return modrinth;
    if (curseforge != null && curseforge.type() != ModType.UNKNOWN) return curseforge;
    return local;
  }

  public static boolean needsFallback(ModClassification value) {
    return value == null || value.type() == ModType.UNKNOWN;
  }
}

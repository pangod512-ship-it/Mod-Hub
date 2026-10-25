package com.pangod512.modhub.core;

public final class ModClassification {
  private final ModType type;
  private final Origin origin;
  private final String reason;
  private final boolean singleplayerOnly;

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof ModClassification)) return false;
    ModClassification other = (ModClassification) value;
    return java.util.Objects.equals(this.type, other.type)
        && java.util.Objects.equals(this.origin, other.origin)
        && java.util.Objects.equals(this.reason, other.reason)
        && this.singleplayerOnly == other.singleplayerOnly;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(type, origin, reason, singleplayerOnly);
  }

  public ModClassification(ModType type, Origin origin, String reason) {
    this(type, origin, reason, false);
  }

  ModClassification(ModType type, Origin origin, String reason, boolean singleplayerOnly) {
    this.type = type;
    this.origin = origin;
    this.reason = reason;
    this.singleplayerOnly = singleplayerOnly;
  }

  public boolean singleplayerOnly() {
    return singleplayerOnly;
  }

  public ModType type() {
    return type;
  }

  public Origin origin() {
    return origin;
  }

  public String reason() {
    return reason;
  }

  public enum Origin {
    MANUAL("Manual"),
    DECLARED("Declarado"),
    MODRINTH("Modrinth"),
    CURSEFORGE("CurseForge"),
    ESTIMATED("Estimado"),
    UNKNOWN("Sin determinar");
    public final String label;

    Origin(String label) {
      this.label = label;
    }
  }

  public static ModClassification unknown(String reason) {
    return new ModClassification(ModType.UNKNOWN, Origin.UNKNOWN, reason);
  }

  /** Estimates are proposals: a future export must require confirmation. */
  public boolean confirmed() {
    return origin == Origin.MANUAL || origin == Origin.DECLARED;
  }

  public String label() {
    return type.label + (origin == Origin.ESTIMATED ? " ~" : "");
  }
}

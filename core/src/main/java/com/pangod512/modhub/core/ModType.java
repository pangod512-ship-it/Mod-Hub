package com.pangod512.modhub.core;

public enum ModType {
  ALL("Todos", 0xFFDDDDDD),
  CLIENT("Cliente", 0xFF65D9E7),
  SERVER("Servidor", 0xFFFFBE65),
  BOTH("Ambos", 0xFF90D978),
  UNKNOWN("Sin determinar", 0xFFB6BBC4);

  public final String label;
  public final int color;

  public String label() {
    switch (this) {
      case ALL:
        return Messages.tr("modhub.all", label);
      case CLIENT:
        return Messages.tr("modhub.client", label);
      case SERVER:
        return Messages.tr("modhub.server", label);
      case BOTH:
        return Messages.tr("modhub.both", label);
      default:
        return Messages.tr("modhub.undetermined", label);
    }
  }

  public boolean hasBadge() {
    return this != UNKNOWN && this != ALL;
  }

  ModType(String label, int color) {
    this.label = label;
    this.color = color;
  }
}

package com.pangod512.modhub.core;

public enum ModType {
  ALL("Todos", 0xFFDDDDDD),
  CLIENT("Solo cliente", 0xFF65D9E7),
  SERVER("Solo servidor", 0xFFFFBE65),
  BOTH("Ambos", 0xFF90D978),
  UNKNOWN("Sin determinar", 0xFFB6BBC4);

  public final String label;
  public final int color;

  ModType(String label, int color) {
    this.label = label;
    this.color = color;
  }
}

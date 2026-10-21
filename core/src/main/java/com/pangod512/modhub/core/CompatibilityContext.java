package com.pangod512.modhub.core;

/** Identifies a provider cache partition and the exact runtime compatibility query. */
public final class CompatibilityContext {
  private final String minecraft, loader;

  public CompatibilityContext(String minecraft, String loader) {
    if (!minecraft.matches("[0-9]+(?:\\.[0-9]+){1,2}") || !loader.matches("[a-z]+")) {
      throw new IllegalArgumentException("Invalid compatibility context");
    }
    this.minecraft = minecraft;
    this.loader = loader;
  }

  public String minecraft() {
    return minecraft;
  }

  public String loader() {
    return loader;
  }

  public String cacheKey() {
    return minecraft + "-" + loader;
  }
}

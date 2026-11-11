package com.pangod512.modhub.core;

import java.util.function.Function;

/** Uses the active Minecraft resource language without coupling the core to a game version. */
public final class Messages {
  private static volatile Function<String, String> translator = key -> key;

  private Messages() {}

  public static void install(Function<String, String> value) {
    translator = java.util.Objects.requireNonNull(value);
  }

  public static String tr(String key, String fallback) {
    String result = translator.apply(key);
    return result == null || result.equals(key) ? fallback : result;
  }
}

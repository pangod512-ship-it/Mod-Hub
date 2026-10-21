package com.pangod512.modhub.core;

/** Logging without depending on a loader's logging facade or Minecraft classes. */
final class CoreLog {
  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("ModHub");

  private CoreLog() {}

  static void warn(String text, Object... values) {
    LOG.warning(message(text, values));
  }

  static void info(String text, Object... values) {
    LOG.info(message(text, values));
  }

  private static String message(String text, Object[] values) {
    for (Object value : values) {
      int i = text.indexOf("{}");
      if (i >= 0) text = text.substring(0, i) + String.valueOf(value) + text.substring(i + 2);
    }
    return text;
  }
}

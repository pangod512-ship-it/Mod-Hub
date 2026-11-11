package com.pangod512.modhub.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public final class LanguageChecks {
  private static void check(boolean value, String description) {
    if (!value) throw new AssertionError(description);
  }

  private static JsonObject load(Path root, String locale) throws Exception {
    try (Reader reader =
        Files.newBufferedReader(root.resolve(locale + ".json"), StandardCharsets.UTF_8)) {
      return new JsonParser().parse(reader).getAsJsonObject();
    }
  }

  private static void use(JsonObject values) {
    Messages.install(key -> values.has(key) ? values.get(key).getAsString() : key);
  }

  private static void awaitStatus(IdentificationService service, String expected) throws Exception {
    long deadline = System.nanoTime() + 3_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (service.snapshot().status().equals(expected)) return;
      Thread.sleep(10);
    }
    throw new AssertionError(
        "Status did not follow the active language: " + service.snapshot().status());
  }

  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args[0]);
    JsonObject english = load(root, "en_us"), spanish = load(root, "es_es");
    Set<String> keys = new HashSet<>();
    english.entrySet().forEach(entry -> keys.add(entry.getKey()));
    for (String locale :
        new String[] {"en_gb", "es_es", "es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve"}) {
      JsonObject values = load(root, locale);
      Set<String> localeKeys = new HashSet<>();
      values.entrySet().forEach(entry -> localeKeys.add(entry.getKey()));
      check(keys.equals(localeKeys), "Missing translations in " + locale);
      if (locale.startsWith("es_"))
        check(spanish.equals(values), "Shared Spanish vocabulary in " + locale);
    }
    ModClassification client =
        new ModClassification(ModType.CLIENT, ModClassification.Origin.DECLARED, "fixture");
    use(english);
    check(client.label().equals("Client"), "English client label");
    check(ModType.SERVER.label().equals("Server"), "English server label");
    check(ModType.UNKNOWN.label().equals("Undetermined"), "English unknown filter label");
    use(spanish);
    check(client.label().equals("Cliente"), "Language reload updates an existing classification");
    check(ModType.SERVER.label().equals("Servidor"), "Short Spanish server label");
    check(!ModType.UNKNOWN.hasBadge(), "Unknown has no table badge");
    check(
        ModType.CLIENT.hasBadge() && ModType.SERVER.hasBadge() && ModType.BOTH.hasBadge(),
        "Known types retain badges");
    check(
        ModType.valueOf("UNKNOWN") == ModType.UNKNOWN,
        "Stored classification identifier remains compatible");
    ModDescriptor descriptor =
        new ModDescriptor("test", "Test", "1", "Author", "Description", null);
    ModDescriptor own = new ModDescriptor("modhub", "Mod Hub", "1", "Author", "Metadata", null);
    check(
        own.description().equals(spanish.get("modhub.description").getAsString()),
        "Own description uses the active Spanish translation");
    use(english);
    check(
        own.description().equals(english.get("modhub.description").getAsString()),
        "Existing description follows a language change");
    check(descriptor.description().equals("Description"), "Third-party descriptions remain intact");
    use(spanish);
    check(
        descriptor.matches("", ModType.UNKNOWN, ModClassification.unknown("fixture")),
        "Unknown remains filterable");
    check(
        !descriptor.matches("", ModType.CLIENT, ModClassification.unknown("fixture")),
        "Unknown does not enter client filter");
    check(
        Messages.tr("modhub.missing", "fallback").equals("fallback"), "Missing resource fallback");
    Path config = Files.createTempDirectory("modhub-language-check-");
    use(english);
    IdentificationService service =
        new IdentificationService(config, new CompatibilityContext("1.20.1", "forge"), "fixture");
    service.settings().save(false);
    service.start(java.util.Collections.emptyList());
    awaitStatus(service, "Queries disabled; using the available cache.");
    use(spanish);
    awaitStatus(service, "Consultas desactivadas; se utiliza la caché disponible.");
    Messages.install(key -> key);
    System.out.println(
        "LANGUAGE_CHECKS_PASSED: "
            + keys.size()
            + " keys; 9 locales; live labels and unknown filter");
  }
}

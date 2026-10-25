package com.pangod512.modhub.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Contract checks with fixtures: these do not claim live CurseForge API availability. */
public final class CoreChecks {
  private static int checks;
  private static Path root;
  private static final CompatibilityContext CTX = new CompatibilityContext("1.12.2", "forge");

  private static void check(boolean condition, String name) {
    if (!condition) throw new AssertionError(name);
    checks++;
  }

  private static ModClassification type(ModType type, ModClassification.Origin origin) {
    return new ModClassification(type, origin, "fixture");
  }

  private static String repeat(char c, int n) {
    char[] s = new char[n];
    Arrays.fill(s, c);
    return new String(s);
  }

  private static JsonObject file(Path path, String mc, String loader, String... environments)
      throws Exception {
    JsonObject f = new JsonObject();
    f.addProperty("gameId", 432);
    f.addProperty("fileFingerprint", CurseForgeClient.fingerprint(path));
    JsonArray versions = new JsonArray();
    versions.add(mc);
    versions.add(loader);
    for (String env : environments) versions.add(env);
    f.add("gameVersions", versions);
    JsonObject hash = new JsonObject();
    hash.addProperty("algo", 1);
    hash.addProperty(
        "value", Io.hex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path))));
    JsonArray hashes = new JsonArray();
    hashes.add(hash);
    f.add("hashes", hashes);
    return f;
  }

  private static String body(JsonObject... files) {
    JsonObject root = new JsonObject(), data = new JsonObject();
    JsonArray matches = new JsonArray();
    for (JsonObject file : files) {
      JsonObject match = new JsonObject();
      match.add("file", file);
      matches.add(match);
    }
    data.add("exactMatches", matches);
    root.add("data", data);
    return root.toString();
  }

  private static CurseForgeClient client(String json, AtomicInteger count) {
    return new CurseForgeClient(
        f -> {
          count.incrementAndGet();
          return new ModrinthClient.Response(200, json, 60);
        });
  }

  public static void main(String[] args) throws Exception {
    root = Paths.get(args[0]);
    Files.createDirectories(root);
    root = Files.createTempDirectory(root, "run-");
    ModClassification manual = type(ModType.UNKNOWN, ModClassification.Origin.MANUAL),
        declared = type(ModType.CLIENT, ModClassification.Origin.DECLARED),
        mr = type(ModType.SERVER, ModClassification.Origin.MODRINTH),
        cf = type(ModType.BOTH, ModClassification.Origin.CURSEFORGE),
        estimate = type(ModType.BOTH, ModClassification.Origin.ESTIMATED);
    check(
        ClassificationPolicy.resolve(manual, mr, cf) == manual,
        "Manual incluso Sin determinar conserva prioridad");
    check(
        ClassificationPolicy.resolve(declared, mr, cf) == declared,
        "Declaración precede proveedores");
    check(ClassificationPolicy.resolve(estimate, mr, cf) == mr, "Modrinth precede CurseForge");
    check(
        ClassificationPolicy.resolve(estimate, ModClassification.unknown(""), cf) == cf,
        "CurseForge respalda tipo desconocido");
    check(
        ClassificationPolicy.resolve(estimate, null, null) == estimate,
        "Sin conexión conserva estimación");
    check(
        ModrinthClient.combine(estimate, ModClassification.unknown("Sin entorno publicado"))
            == estimate,
        "Archivo reconocido sin tipo no borra la estimación local en 1.20.1");
    check(
        ModrinthClient.combine(estimate, mr) == mr,
        "Tipo remoto conocido reemplaza la estimación local en 1.20.1");
    ModClassification singleplayer = ModrinthClient.classification(
        new ModrinthCache.Entry("project1", "version1", "singleplayer_only", true, 1000));
    check(ModrinthClient.combine(estimate, singleplayer) == singleplayer,
        "Restricción explícita a un jugador no se convierte en Ambos");
    check(ClassificationPolicy.resolve(estimate, singleplayer, cf) == singleplayer,
        "Los ports respetan la restricción a un jugador antes de estimaciones y respaldo");
    check(!ClassificationPolicy.needsFallback(singleplayer),
        "Restricción explícita a un jugador no pide un tipo contradictorio al respaldo");
    check(
        !CurseForgeAccess.available(),
        "Sin contrato autorizado CurseForge no se anuncia disponible");
    Path overridesPath = root.resolve("overrides.json");
    OverrideStore overrides = new OverrideStore(overridesPath);
    overrides.save("FML", "8", ModType.UNKNOWN);
    check(
        new OverrideStore(overridesPath).apply("FML", "8", declared).origin()
            == ModClassification.Origin.MANUAL,
        "Identificadores históricos de Forge admiten corrección");
    ModDescriptor descriptor = new ModDescriptor("id", "Módulo", "v", "Áutor", "", null);
    check(descriptor.matches("modulo", ModType.ALL, declared), "Búsqueda normaliza acentos");
    check(!descriptor.matches("modulo", ModType.SERVER, declared), "Filtro de tipo independiente");
    ModDescriptor missing = new ModDescriptor("legacy", null, null, null, null, null);
    check(
        !missing.name().isEmpty() && missing.description() != null,
        "Metadatos históricos ausentes no rompen la interfaz");
    Path settingsPath = root.resolve("settings.json");
    ModrinthSettings settings = new ModrinthSettings(settingsPath);
    check(settings.enabled() && !settings.curseforgeEnabled(), "Valores predeterminados separados");
    settings.saveCurseforge(true);
    settings.save(false);
    settings = new ModrinthSettings(settingsPath);
    check(
        !settings.enabled() && settings.curseforgeEnabled(),
        "Guardar Modrinth no cambia CurseForge");
    settings.saveCurseforge(false);
    check(!new ModrinthSettings(settingsPath).enabled(), "Guardar CurseForge no cambia Modrinth");
    Files.write(settingsPath, "{\"modrinthEnabled\":true}".getBytes(StandardCharsets.UTF_8));
    check(
        new ModrinthSettings(settingsPath).enabled()
            && !new ModrinthSettings(settingsPath).curseforgeEnabled(),
        "Migración de ajustes 1.0.0");
    Files.write(settingsPath, "broken".getBytes(StandardCharsets.UTF_8));
    check(!new ModrinthSettings(settingsPath).enabled(), "Ajustes dañados no activan red");
    Path sample = root.resolve("sample.jar");
    Files.write(sample, "hello".getBytes(StandardCharsets.US_ASCII));
    check(CurseForgeClient.fingerprint(sample) == 2788266382L, "Vector MurmurHash2 seed 1 hello");
    Path white = root.resolve("whitespace.jar");
    Files.write(white, "h e\tl\nl\ro".getBytes(StandardCharsets.US_ASCII));
    check(
        CurseForgeClient.fingerprint(sample) == CurseForgeClient.fingerprint(white),
        "Huella ignora solo espacios ASCII admitidos");
    List<ModrinthResolver.Target> targets =
        Collections.singletonList(new ModrinthResolver.Target("sample", sample));
    AtomicInteger calls = new AtomicInteger();
    JsonObject known = file(sample, "1.12.2", "Forge", "Client");
    check(
        client(body(known), calls).lookup(targets, CTX).get("sample").type() == ModType.CLIENT,
        "Coincidencia exacta con entorno explícito");
    check(
        client(body(file(sample, "1.12.2", "Forge")), calls)
                .lookup(targets, CTX)
                .get("sample")
                .type()
            == ModType.UNKNOWN,
        "Archivo reconocido sin entorno sigue desconocido");
    check(
        client(body(file(sample, "1.20.1", "Forge", "Client")), calls)
            .lookup(targets, CTX)
            .isEmpty(),
        "MC incompatible no clasifica");
    check(
        client(body(file(sample, "1.12.2", "Fabric", "Client")), calls)
            .lookup(targets, CTX)
            .isEmpty(),
        "Cargador incompatible no clasifica");
    JsonObject bad = file(sample, "1.12.2", "Forge", "Client");
    bad.getAsJsonArray("hashes").get(0).getAsJsonObject().addProperty("value", repeat('0', 40));
    check(
        client(body(bad), calls).lookup(targets, CTX).isEmpty(),
        "Murmur coincidente sin SHA correcto se rechaza");
    check(
        client(body(known, file(sample, "1.12.2", "Forge", "Server"), known), calls)
                .lookup(targets, CTX)
                .get("sample")
                .type()
            == ModType.UNKNOWN,
        "Conflicto no se revierte con tercera coincidencia");
    try {
      client("[]", calls).lookup(targets, CTX);
      throw new AssertionError("JSON inválido");
    } catch (IOException expected) {
      checks++;
    }
    for (int status : new int[] {401, 403, 429}) {
      CurseForgeClient failure =
          new CurseForgeClient(f -> new ModrinthClient.Response(status, "", 120));
      try {
        failure.lookup(targets, CTX);
        throw new AssertionError("HTTP " + status);
      } catch (ModrinthClient.ApiFailure expected) {
        check(expected.retrySeconds == 120, "Cooldown HTTP " + status);
      }
    }
    Path cfConfig = root.resolve("cf");
    calls.set(0);
    CurseForgeFallback fallback = new CurseForgeFallback(client(body(known), calls), cfConfig, CTX);
    fallback.resolve(targets, Collections.singletonMap("sample", mr), true, 1000);
    check(calls.get() == 0, "Tipo de Modrinth evita consulta CF");
    fallback.resolve(targets, Collections.emptyMap(), false, 1000);
    check(calls.get() == 0, "CF desactivado no consulta");
    check(
        fallback.resolve(targets, Collections.emptyMap(), true, 1000).get("sample").type()
                == ModType.CLIENT
            && calls.get() == 1,
        "CF consulta archivo pendiente");
    fallback.resolve(targets, Collections.emptyMap(), true, 1001);
    check(calls.get() == 1, "Caché evita repetir consulta");
    check(
        new CurseForgeFallback(null, cfConfig, CTX)
                .resolve(targets, Collections.emptyMap(), false, 1001)
                .get("sample")
                .type()
            == ModType.CLIENT,
        "Caché CF funciona sin conexión");
    check(
        new CurseForgeFallback(null, cfConfig, new CompatibilityContext("1.16.5", "forge"))
            .resolve(targets, Collections.emptyMap(), false, 1001)
            .isEmpty(),
        "CF no comparte caché entre contextos");
    fallback.resolve(targets, Collections.emptyMap(), true, 606000);
    check(calls.get() == 2, "CF revalida resultado caducado");
    Files.write(sample, "new!!".getBytes(StandardCharsets.US_ASCII));
    fallback.resolve(targets, Collections.emptyMap(), true, 606001);
    check(calls.get() == 3, "Archivo modificado misma longitud vuelve a consultar");
    AtomicInteger failures = new AtomicInteger();
    CurseForgeFallback limited =
        new CurseForgeFallback(
            new CurseForgeClient(
                f -> {
                  failures.incrementAndGet();
                  return new ModrinthClient.Response(429, "", 120);
                }),
            root.resolve("limited"),
            CTX);
    limited.resolve(targets, Collections.emptyMap(), true, 1000);
    limited.resolve(targets, Collections.emptyMap(), true, 1001);
    check(failures.get() == 1, "Cooldown persistente limita reintentos");
    limited.resolve(targets, Collections.emptyMap(), true, 1121);
    check(failures.get() == 2, "Reintento después del límite");
    String hash = repeat('a', 128);
    Path cachePath = root.resolve("mr.json");
    ModrinthCache cache = new ModrinthCache(cachePath, 1000, CTX);
    cache.put(hash, new ModrinthCache.Entry("project1", "version1", "client_only", true, 1000));
    cache.save();
    check(new ModrinthCache(cachePath, 1001, CTX).get(hash) != null, "Caché MR del mismo contexto");
    check(
        new ModrinthCache(cachePath, 1001, new CompatibilityContext("1.20.1", "forge")).get(hash)
            == null,
        "Caché MR de otro contexto ignorada");
    ModrinthCache.Entry unresolved =
        new ModrinthCache.Entry("project1", "version1", "unknown", false, 1000);
    check(!unresolved.reusable(606000), "Archivo identificado sin tipo vuelve a intentarse");
    String version =
        "{\""
            + hash
            + "\":{\"id\":\"version1\",\"project_id\":\"project1\",\"loaders\":[\"forge\"],\"game_versions\":[\"1.12.2\"],\"environment\":\"client_only\",\"files\":[{\"hashes\":{\"sha512\":\""
            + hash
            + "\"}}]}}";
    ModrinthClient.Transport transport =
        (method, path, request) -> new ModrinthClient.Response(200, version, 60);
    check(
        new ModrinthClient(transport, CTX)
            .lookup(Collections.singletonList(hash), 1000)
            .get(hash)
            .found(),
        "Consulta MR parametrizada acepta 1.12.2");
    // Incompatible versions produce a miss; the fixture project endpoint is never needed here.
    check(
        !new ModrinthClient(transport, new CompatibilityContext("1.16.5", "forge"))
            .lookup(Collections.singletonList(hash), 1000)
            .get(hash)
            .found(),
        "MR rechaza versión distinta");
    Path damagedConfig = root.resolve("cf-damaged-entry");
    JsonObject damagedRoot = new JsonObject(), damagedEntries = new JsonObject();
    JsonObject damagedEntry = new JsonObject();
    damagedEntry.addProperty("checkedAt", 1000);
    damagedEntry.addProperty("type", "INVALID");
    damagedEntries.add(ModrinthResolver.fingerprint(sample), damagedEntry);
    damagedRoot.addProperty("provider", "curseforge");
    damagedRoot.addProperty("context", CTX.cacheKey());
    damagedRoot.addProperty("retryAfter", 0);
    damagedRoot.add("entries", damagedEntries);
    ModrinthCache.writeJson(
        damagedConfig.resolve("modhub-curseforge-cache-" + CTX.cacheKey() + ".json"), damagedRoot);
    calls.set(0);
    Map<String, ModClassification> recovered =
        new CurseForgeFallback(client(body(file(sample, "1.12.2", "Forge", "Client")), calls),
            damagedConfig, CTX).resolve(targets, Collections.emptyMap(), true, 1001);
    check(
        calls.get() == 1 && recovered.get("sample") != null
            && recovered.get("sample").type() == ModType.CLIENT,
        "Entrada CF dañada vuelve a consultarse en lugar de bloquear ese archivo");
    damagedRoot.remove("entries");
    ModrinthCache.writeJson(
        damagedConfig.resolve("modhub-curseforge-cache-" + CTX.cacheKey() + ".json"), damagedRoot);
    recovered = new CurseForgeFallback(
        client(body(file(sample, "1.12.2", "Forge", "Client")), calls), damagedConfig, CTX)
        .resolve(targets, Collections.emptyMap(), true, 1002);
    check(calls.get() == 2 && recovered.get("sample").type() == ModType.CLIENT,
        "Caché CF sin tabla de entradas se reconstruye");
    logoChecks();
    Files.write(
        root.resolve("results.txt"),
        (checks + " checks passed on " + System.getProperty("java.version"))
            .getBytes(StandardCharsets.UTF_8));
    System.out.println(
        "CORE_CHECKS_PASSED: " + checks + "; Java " + System.getProperty("java.version"));
  }

  private static void logoChecks() throws Exception {
    java.awt.image.BufferedImage logo = new java.awt.image.BufferedImage(
        16, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    logo.setRGB(0, 0, 0xff123456);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(logo, "png", png);
    java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(
        new ByteArrayInputStream(LogoData.read(new ByteArrayInputStream(png.toByteArray()))));
    check(decoded.getWidth() == 128 && decoded.getHeight() == 128,
        "Icono de un recurso virtual se limita antes de crear la textura nativa");
    check(decoded.getRGB(0, 0) == 0 && decoded.getRGB(0, 32) != 0,
        "Icono rectangular conserva proporción y transparencia");
    png.reset();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(
        2049, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", png);
    try {
      LogoData.read(new ByteArrayInputStream(png.toByteArray()));
      throw new AssertionError("Icono con dimensiones excesivas");
    } catch (IOException expected) { checks++; }
    try {
      LogoData.read(new ByteArrayInputStream(new byte[4194305]));
      throw new AssertionError("Icono con tamaño excesivo");
    } catch (IOException expected) { checks++; }
    try {
      LogoData.read(new ByteArrayInputStream(new byte[] {1, 2, 3}));
      throw new AssertionError("Icono con formato inválido");
    } catch (IOException expected) { checks++; }
  }
}

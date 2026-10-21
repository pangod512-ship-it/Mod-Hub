package com.pangod512.modhub.smoke;

import com.pangod512.modhub.core.*;

import com.google.gson.*;
import com.pangod512.modhub.client.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic failures/cache checks; the optional real audit only reads installed JARs. */
public final class ModrinthChecks {
    private static final List<String> RESULTS = new ArrayList<>();
    private static final AtomicLong NOW = new AtomicLong(java.time.Instant.now().getEpochSecond());
    private static final class Fake implements ModrinthClient.Transport {
        int calls, status = 200;
        long retry = 1800;
        boolean offline, malformed, versionEnvironment = true;
        String env = "client_only", loader = "forge", game = "1.20.1";
        String projectEnvironment = "[\"server_only\"]";
        Set<String> missing = new HashSet<>();
        @Override public ModrinthClient.Response request(String method, String path, String body) throws IOException {
            calls++;
            if (offline) throw new IOException("Sin conexión de prueba");
            if (status != 200) return new ModrinthClient.Response(status, "", retry);
            if (malformed) return new ModrinthClient.Response(200, "{roto", 0);
            if (method.equals("GET")) return new ModrinthClient.Response(200,
                    "[{\"id\":\"proj0001\",\"project_type\":\"mod\",\"environment\":" + projectEnvironment + "}]", 0);
            JsonObject result = new JsonObject();
            for (var hash : JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("hashes")) {
                String value = hash.getAsString();
                if (!missing.contains(value)) result.add(value, version(value));
            }
            return new ModrinthClient.Response(200, result.toString(), 0);
        }
        JsonObject version(String hash) {
            JsonObject value = new JsonObject();
            value.addProperty("id", "vers0001"); value.addProperty("project_id", "proj0001");
            value.add("loaders", JsonParser.parseString("[\"" + loader + "\"]"));
            value.add("game_versions", JsonParser.parseString("[\"" + game + "\"]"));
            value.add("files", JsonParser.parseString("[{\"hashes\":{\"sha512\":\"" + hash + "\"}}]"));
            if (versionEnvironment) value.addProperty("environment", env);
            return value;
        }
    }

    public static void main(String[] args) throws Exception {
        Path output = Files.createDirectories(Path.of(args[0]));
        try {
            verify(Files.createTempDirectory(output, "fixtures-"));
            if (args.length > 1) audit(Path.of(args[1]), output);
            RESULTS.add("MODRINTH_CHECKS_PASS");
        } catch (Throwable failure) { RESULTS.add("FAIL: " + failure); throw failure; }
        finally { Files.write(output.resolve("resultado.txt"), RESULTS); }
        System.out.println("MODRINTH_CHECKS_PASS: " + RESULTS.stream().filter(s -> s.startsWith("OK:")).count());
    }
    private static ModrinthResolver.Snapshot resolve(Path cache, Fake fake, List<ModrinthResolver.Target> targets, boolean enabled) {
        return new ModrinthResolver(cache, new ModrinthClient(fake), NOW::get, ignored -> {}).resolve(targets, () -> enabled);
    }
    private static void verify(Path dir) throws Exception {
        Path jar = dir.resolve("mod.jar"); Files.writeString(jar, "versión A");
        String hash = ModrinthResolver.fingerprint(jar);
        List<ModrinthResolver.Target> targets = List.of(new ModrinthResolver.Target("example", jar));
        Path cache = dir.resolve("cache.json"); Fake fake = new Fake();
        var first = resolve(cache, fake, targets, true);
        check(first.classifications().get("example").type() == ModType.CLIENT && fake.calls == 1,
                "Primera consulta identifica el archivo exacto y usa el entorno de su version");
        check(first.classifications().get("example").origin() == ModClassification.Origin.MODRINTH,
                "La fuente se muestra como Modrinth");
        check(Files.exists(cache), "La primera respuesta se guarda en disco");
        resolve(cache, fake, targets, true);
        check(fake.calls == 1, "Otro inicio con los mismos archivos no hace peticiones");
        Path renamed = dir.resolve("renombrado.jar"); Files.copy(jar, renamed);
        var renamedTargets = List.of(new ModrinthResolver.Target("renamed", renamed));
        check(resolve(cache, fake, renamedTargets, true).classifications().get("renamed").type() == ModType.CLIENT && fake.calls == 1,
                "Un cambio de nombre conserva el resultado por contenido");
        var stamp = Files.getLastModifiedTime(jar); Files.writeString(jar, "versión B"); Files.setLastModifiedTime(jar, stamp);
        fake.env = "server_only";
        check(resolve(cache, fake, targets, true).classifications().get("example").type() == ModType.SERVER && fake.calls == 2,
                "Actualizar con igual nombre, longitud y fecha detecta una huella nueva");
        fake.offline = true;
        check(resolve(cache, fake, targets, false).classifications().get("example").type() == ModType.SERVER && fake.calls == 2,
                "Sin consultas activadas se reutiliza la cache y no se abre conexion");
        check(resolve(cache, fake, targets, true).classifications().get("example").type() == ModType.SERVER && fake.calls == 2,
                "Con internet ausente y cache completa tampoco se intenta la red");
        check(resolve(cache, fake, List.of(), true).classifications().isEmpty() && fake.calls == 2,
                "Los mods eliminados no reaparecen ni provocan consultas");
        check(resolve(dir.resolve("disabled.json"), fake, targets, false).classifications().isEmpty() && fake.calls == 2,
                "Sin cache y con opcion desactivada se conserva la clasificacion local");

        Path missingCache = dir.resolve("missing.json"); Fake missing = new Fake();
        missing.missing.add(ModrinthResolver.fingerprint(jar));
        check(resolve(missingCache, missing, targets, true).classifications().isEmpty(), "Un archivo no publicado no recibe un tipo inventado");
        resolve(missingCache, missing, targets, true);
        check(missing.calls == 1, "No encontrado se guarda para evitar consultas en cada inicio");
        NOW.addAndGet(ModrinthCache.MISS_SECONDS + 1); resolve(missingCache, missing, targets, true);
        check(missing.calls == 2, "No encontrado puede revisarse despues de siete dias");

        Path failureCache = dir.resolve("offline.json"); Fake failure = new Fake(); failure.offline = true;
        var offline = resolve(failureCache, failure, targets, true);
        check(offline.classifications().isEmpty() && offline.status().contains("no está disponible"),
                "Un fallo de red deja la deteccion local operativa");
        check(new ModrinthCache(failureCache).get(ModrinthResolver.fingerprint(jar)) == null,
                "Un error de red no se guarda como no encontrado");
        resolve(failureCache, failure, targets, true);
        check(failure.calls == 1, "El periodo de espera tras un fallo sobrevive al reinicio");
        NOW.addAndGet(901); failure.offline = false;
        check(resolve(failureCache, failure, targets, true).classifications().containsKey("example") && failure.calls == 2,
                "La consulta se recupera al terminar el periodo de espera");
        Fake rate = new Fake(); rate.status = 429;
        Path rateCache = dir.resolve("rate.json"); resolve(rateCache, rate, targets, true);
        NOW.addAndGet(901); resolve(rateCache, rate, targets, true);
        check(rate.calls == 1, "HTTP 429 respeta el tiempo de reintento del servidor");
        Fake invalid = new Fake(); invalid.malformed = true;
        check(resolve(dir.resolve("malformed.json"), invalid, targets, true).classifications().isEmpty(),
                "Una respuesta JSON invalida no rompe la pantalla ni inventa un resultado");
        Fake wrongLoader = new Fake(); wrongLoader.loader = "fabric";
        check(resolve(dir.resolve("fabric.json"), wrongLoader, targets, true).classifications().isEmpty(),
                "Una coincidencia ajena a Forge no se aplica");
        Fake wrongGame = new Fake(); wrongGame.game = "1.21.1";
        check(resolve(dir.resolve("game.json"), wrongGame, targets, true).classifications().isEmpty(),
                "Una coincidencia ajena a Minecraft 1.20.1 no se aplica");
        ModrinthClient mismatched = new ModrinthClient((method, path, body) -> {
            JsonObject root = new JsonObject(); root.add(hash, new Fake().version("0".repeat(128)));
            return new ModrinthClient.Response(200, root.toString(), 0);
        });
        check(!mismatched.lookup(List.of(hash), NOW.get()).get(hash).found(), "La respuesta debe incluir la misma huella en sus archivos");
        Fake oldApi = new Fake(); oldApi.versionEnvironment = false;
        var general = new ModrinthClient(oldApi).lookup(List.of(hash), NOW.get()).get(hash);
        check(general.found() && !general.versionSpecific() && general.environment().equals("server_only") && oldApi.calls == 2,
                "Si falta el entorno de version se distingue el dato general del proyecto");
        oldApi.projectEnvironment = "[\"client_only\",\"server_only\"]";
        check(new ModrinthClient(oldApi).lookup(List.of(hash), NOW.get()).get(hash).environment().equals("unknown"),
                "Entornos distintos entre versiones no se reducen a un tipo general inventado");

        for (String env : ModrinthClient.ENVIRONMENTS) {
            var value = ModrinthClient.classification(new ModrinthCache.Entry("proj0001", "vers0001", env, true, NOW.get()));
            check(value.type() != ModType.ALL && !value.confirmed(), "Entorno " + env + " conserva una explicacion sin autorizar exportaciones");
        }
        var remote = ModrinthClient.classification(new ModrinthCache.Entry("proj0001", "vers0001", "server_only", true, NOW.get()));
        var manual = new ModClassification(ModType.CLIENT, ModClassification.Origin.MANUAL, "Manual");
        var declared = new ModClassification(ModType.CLIENT, ModClassification.Origin.DECLARED, "Declarado");
        var estimated = new ModClassification(ModType.BOTH, ModClassification.Origin.ESTIMATED, "Propuesta");
        check(ModrinthClient.combine(manual, remote).equals(manual), "La correccion manual gana a Modrinth");
        check(ModrinthClient.combine(declared, remote).type() == ModType.CLIENT
                        && ModrinthClient.combine(declared, remote).reason().contains("Aviso:"),
                "Un conflicto conserva la declaracion local y se avisa");
        check(ModrinthClient.combine(estimated, remote).equals(remote), "Modrinth tiene prioridad sobre la estimacion local");
        check(ModrinthClient.combine(estimated, null).equals(estimated), "Sin coincidencia la estimacion local permanece intacta");
        var single = ModrinthClient.classification(new ModrinthCache.Entry("proj0001", "vers0001", "singleplayer_only", true, NOW.get()));
        check(ModrinthClient.combine(estimated, single).type() == ModType.UNKNOWN, "Solo un jugador no se convierte en Ambos para multijugador");
        OverrideStore choices = new OverrideStore(dir.resolve("overrides.json")); choices.save("example", "1", ModType.BOTH);
        check(choices.apply("example", "1", remote).origin() == ModClassification.Origin.MANUAL, "Las correcciones persistentes ganan al resultado remoto");

        Path corrupt = dir.resolve("corrupt.json"); Files.writeString(corrupt, "{dañado");
        resolve(corrupt, new Fake(), targets, false);
        check(Files.readString(corrupt).equals("{dañado"), "Leer una cache dañada sin red conserva el original");
        resolve(corrupt, new Fake(), targets, true);
        try (var files = Files.list(dir)) { check(files.anyMatch(p -> p.getFileName().toString().startsWith("corrupt.json.corrupt-")), "Reemplazar la cache dañada conserva un respaldo"); }
        Path blocked = dir.resolve("blocked"); Files.writeString(blocked, "bloqueo");
        var unsaved = resolve(blocked.resolve("cache.json"), new Fake(), targets, true);
        check(unsaved.classifications().containsKey("example") && unsaved.status().contains("No se pudo guardar"),
                "Un fallo al guardar conserva los tipos en sesion y no afirma haberlos guardado");
        Path settingsPath = dir.resolve("settings.json"); ModrinthSettings settings = new ModrinthSettings(settingsPath);
        check(settings.enabled(), "La consulta autorizada esta activada inicialmente");
        settings.save(false); check(!new ModrinthSettings(settingsPath).enabled(), "Desactivar Modrinth persiste entre inicios");
        Files.writeString(settingsPath, "{roto");
        check(!new ModrinthSettings(settingsPath).enabled(), "Una configuracion dañada desactiva las consultas sin cerrar el juego");

        Fake batches = new Fake(); List<ModrinthResolver.Target> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) { Path path = dir.resolve("many-" + i + ".jar"); Files.writeString(path, "mod" + i); many.add(new ModrinthResolver.Target("id" + i, path)); }
        check(resolve(dir.resolve("many.json"), batches, many, true).classifications().size() == 101 && batches.calls == 2,
                "101 archivos se consultan en lotes limitados y sin duplicar peticiones");
        Fake shared = new Fake();
        check(resolve(dir.resolve("shared.json"), shared, List.of(new ModrinthResolver.Target("one", jar), new ModrinthResolver.Target("two", jar)), true)
                        .classifications().size() == 2 && shared.calls == 1, "Una huella compartida se consulta una sola vez");

        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<ModrinthResolver.Snapshot> updates = new CopyOnWriteArrayList<>();
        ModrinthClient slow = new ModrinthClient((method, path, body) -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Timeout de prueba"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            return new Fake().request(method, path, body);
        });
        var background = CompletableFuture.supplyAsync(() -> new ModrinthResolver(dir.resolve("slow.json"), slow, NOW::get, updates::add).resolve(targets, () -> true));
        try { check(entered.await(5, TimeUnit.SECONDS) && !background.isDone() && !updates.isEmpty(),
                "Los resultados iniciales estan publicados mientras la consulta lenta sigue en el trabajador"); }
        finally { release.countDown(); }
        check(background.get(10, TimeUnit.SECONDS).classifications().containsKey("example"), "La consulta lenta termina sin perder la actualizacion");
    }

    private static void audit(Path mods, Path output) throws Exception {
        List<ModrinthResolver.Target> targets = new ArrayList<>();
        try (var files = Files.list(mods)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                targets.add(new ModrinthResolver.Target(path.getFileName().toString(), path));
            }
        }
        var resolver = new ModrinthResolver(output.resolve("modpack-cache.json"), ModrinthClient.production("0.4.1-validation"),
                () -> java.time.Instant.now().getEpochSecond(), ignored -> {});
        var result = resolver.resolve(targets, () -> true);
        List<String> report = new ArrayList<>(List.of("Archivo\tTipo\tOrigen\tMotivo\tConsulta"));
        for (var target : targets) {
            var value = result.classifications().get(target.id());
            report.add(target.id() + "\t" + (value == null ? "Sin coincidencia" : value.type().label) + "\t"
                    + (value == null ? "Local" : value.origin().label) + "\t" + (value == null ? "" : value.reason())
                    + "\t" + result.notes().getOrDefault(target.id(), ""));
        }
        Files.write(output.resolve("modpack-modrinth.tsv"), report);
        RESULTS.add("Consulta real: " + targets.size() + " JAR; coincidencias: " + result.classifications().size() + "; " + result.status());
        check(!result.classifications().isEmpty(), "La API publica reconoce archivos reales del modpack sin modificarlos");
        var cached = resolver.resolve(targets, () -> false);
        check(cached.classifications().equals(result.classifications()), "La coleccion real conserva sus resultados al usar solo cache");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); RESULTS.add("OK: " + message); }
}

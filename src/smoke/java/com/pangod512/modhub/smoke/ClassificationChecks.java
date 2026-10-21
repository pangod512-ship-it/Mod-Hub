package com.pangod512.modhub.smoke;

import com.pangod512.modhub.core.*;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.pangod512.modhub.client.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public final class ClassificationChecks {
    private static final List<String> RESULTS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path output = Files.createDirectories(Path.of(args[0]));
        try {
            verify(Files.createTempDirectory(output, "fixtures-"));
            RESULTS.addAll(InventoryChecks.verify(List.of(), Files.createTempDirectory(output, "inventory-")));
            if (args.length > 1) audit(Path.of(args[1]), output);
            RESULTS.add("CLASSIFICATION_CHECKS_PASS");
        } catch (Throwable failure) {
            RESULTS.add("FAIL: " + failure);
            throw failure;
        } finally {
            Files.write(output.resolve("resultado.txt"), RESULTS, StandardCharsets.UTF_8);
        }
        System.out.println("CLASSIFICATION_CHECKS_PASS: " + RESULTS.stream().filter(s -> s.startsWith("OK:")).count());
    }

    private static void verify(Path fixtures) throws Exception {
        Files.createDirectories(fixtures);
        Path gameplay = jar(fixtures.resolve("gameplay.jar"), "data/sample/recipes/item.json", "assets/sample/lang/en_us.json");
        Path visuals = jar(fixtures.resolve("visuals.jar"), "assets/sample/textures/thing.png");
        Path tags = jar(fixtures.resolve("tags.jar"), "data/sample/tags/items/example.json", "assets/sample/lang/en_us.json");
        Path classes = jar(fixtures.resolve("classes.jar"), "net/minecraft/client/Fake.class");
        ModRow normal = row(info("sample", gameplay, Map.of(), true), gameplay);
        check(ModClassifier.declared(normal.info()).type() == ModType.UNKNOWN, "Metadatos vacios no se convierten en Ambos");
        check(ModClassifier.declared(info("sample", gameplay, Map.of("displayTest", "IGNORE_SERVER_VERSION"), true)).type() == ModType.UNKNOWN,
                "displayTest no clasifica el mod");
        ModClassification declared = ModClassifier.declared(info("sample", gameplay, Map.of("clientSideOnly", true), true));
        check(declared.type() == ModType.CLIENT && declared.confirmed(), "clientSideOnly explicito produce Solo cliente / Declarado");
        check(ModClassifier.declared(info("sample", gameplay, Map.of("clientSideOnly", "true"), true)).type() == ModType.UNKNOWN,
                "Una cadena de texto no suplanta el booleano declarado");
        ModClassification data = analyze(normal);
        check(data.type() == ModType.BOTH && data.origin() == ModClassification.Origin.ESTIMATED && !data.confirmed(),
                "Datos de juego proponen Ambos, sin confirmar exportacion");
        check(analyze(row(info("sample", tags, Map.of(), true), tags)).type() == ModType.UNKNOWN,
                "Etiquetas y recursos ambiguos no bastan para decidir");
        check(analyze(row(info("sample", classes, Map.of(), true), classes)).type() == ModType.UNKNOWN,
                "Referencias de cliente por si solas no deciden el tipo");
        check(analyze(row(info("other", gameplay, Map.of(), false), gameplay)).type() == ModType.UNKNOWN,
                "No se atribuyen datos ajenos a otro mod del mismo JAR");
        ModClassification visual = analyze(row(info("sample", visuals, Map.of(), true), visuals));
        check(visual.type() == ModType.UNKNOWN && !visual.confirmed(), "Recursos visuales solos no convierten bibliotecas ni mods de servidor en Solo cliente");
        Path invalid = fixtures.resolve("invalid.jar");
        Files.writeString(invalid, "no es un zip");
        check(analyze(row(info("sample", invalid, Map.of(), true), invalid)).type() == ModType.UNKNOWN,
                "Un JAR ilegible queda Sin determinar");

        Path config = fixtures.resolve("instance-a/config/modhub-overrides.json");
        OverrideStore store = new OverrideStore(config);
        check(!Files.exists(config), "Leer sin correcciones no crea archivos");
        store.save("sample", "1.0", ModType.SERVER);
        check(store.apply("sample", "1.0", declared).type() == ModType.SERVER,
                "La correccion manual gana a la declaracion");
        check(store.apply("sample", "1.0", data).origin() == ModClassification.Origin.MANUAL,
                "La correccion manual gana a la estimacion");
        OverrideStore reopened = new OverrideStore(config);
        check(reopened.apply("sample", "1.0", data).type() == ModType.SERVER, "Las correcciones sobreviven a recargar el archivo");
        check(reopened.apply("sample", "2.0", data).reason().contains("Aviso:"), "Una version distinta conserva la eleccion y avisa");
        check(new OverrideStore(fixtures.resolve("instance-b/config/modhub-overrides.json")).snapshot().isEmpty(),
                "Dos instancias mantienen correcciones independientes");
        reopened.save("mod_ausente", "1.0", ModType.CLIENT);
        reopened.save("sample", "1.0", null);
        check(reopened.apply("sample", "1.0", data).equals(data), "Restablecer recupera la clasificacion automatica");
        check(new OverrideStore(config).snapshot().containsKey("mod_ausente"), "Se conservan elecciones de mods ausentes");
        Files.writeString(config, "{archivo dañado");
        byte[] damagedBytes = Files.readAllBytes(config);
        OverrideStore damaged = new OverrideStore(config);
        check(damaged.warning() != null && damaged.snapshot().isEmpty(), "Un JSON dañado se ignora con aviso");
        check(Arrays.equals(damagedBytes, Files.readAllBytes(config)), "Leer un archivo dañado no lo sobrescribe");
        damaged.save("sample", "1.0", ModType.BOTH);
        try (var backups = Files.list(config.getParent())) {
            check(backups.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")), "Corregir un JSON dañado conserva una copia de respaldo");
        }
        Files.writeString(config, "{\"schema\":1,\"overrides\":{\"sample\":{\"type\":\"ALL\",\"version\":\"1\"}}}");
        check(new OverrideStore(config).warning() != null, "Un tipo invalido no se aplica");
        Path blocker = fixtures.resolve("not-a-directory");
        Files.writeString(blocker, "bloqueo");
        OverrideStore blocked = new OverrideStore(blocker.resolve("modhub-overrides.json"));
        boolean failed = false;
        try { blocked.save("sample", "1", ModType.CLIENT); } catch (java.io.IOException expected) { failed = true; }
        check(failed && blocked.snapshot().isEmpty(), "Un fallo al guardar no altera la eleccion en memoria");

        long start = System.nanoTime();
        List<java.util.concurrent.CompletableFuture<ModClassification>> futures = new ArrayList<>();
        for (int i = 0; i < 150; i++) futures.add(JarInspector.analyze(normal));
        long submissionMillis = (System.nanoTime() - start) / 1_000_000;
        boolean consistent = true;
        for (var future : futures) consistent &= future.get(20, TimeUnit.SECONDS).equals(data);
        check(consistent, "150 solicitudes concurrentes recuperan resultados coherentes del cache");
        RESULTS.add("Tiempo de 150 solicitudes con cache: " + submissionMillis + " ms (informativo, sin umbral artificial)");
    }

    private static void audit(Path mods, Path output) throws Exception {
        List<String> report = new ArrayList<>(List.of("Archivo\tID\tTipo propuesto\tOrigen\tConfirmado\tMotivo"));
        List<String> skipped = new ArrayList<>();
        try (var files = Files.list(mods)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                try (JarFile jar = new JarFile(path.toFile())) {
                    var metadata = jar.getJarEntry("META-INF/mods.toml");
                    if (metadata == null) { skipped.add(path.getFileName() + ": sin mods.toml (posible biblioteca u otro cargador)"); continue; }
                    String text;
                    try (var input = jar.getInputStream(metadata)) { text = new String(input.readNBytes(1_048_576), StandardCharsets.UTF_8); }
                    Config parsed = new TomlParser().parse(text);
                    List<Config> definitions = parsed.get("mods");
                    if (definitions == null) { skipped.add(path.getFileName() + ": sin definiciones de mods"); continue; }
                    for (Config definition : definitions) {
                        String id = definition.get("modId");
                        Object clientOnly = parsed.get("clientSideOnly");
                        IModInfo info = info(id, path, clientOnly == null ? Map.of() : Map.of("clientSideOnly", clientOnly), definitions.size() == 1);
                        ModRow row = row(info, path);
                        ModClassification classification = row.classification();
                        if (!classification.confirmed()) classification = analyze(row);
                        report.add(path.getFileName() + "\t" + id + "\t" + classification.type().label + "\t"
                                + classification.origin().label + "\t" + classification.confirmed() + "\t" + classification.reason());
                    }
                } catch (RuntimeException | java.io.IOException failure) {
                    skipped.add(path.getFileName() + ": " + failure.getClass().getSimpleName());
                }
            }
        }
        Files.write(output.resolve("modpack-clasificacion.tsv"), report);
        Files.write(output.resolve("modpack-no-analizados.txt"), skipped);
        check(report.size() > 1, "Coleccion real leida sin cargar sus clases ni modificar sus archivos");
        RESULTS.add("Mods declarados analizados en JAR principales: " + (report.size() - 1));
        RESULTS.add("JAR sin mods.toml o no analizados: " + skipped.size());
    }

    private static ModClassification analyze(ModRow row) throws Exception { return JarInspector.analyze(row).get(20, TimeUnit.SECONDS); }

    private static ModRow row(IModInfo info, Path path) {
        return new ModRow(info, info.getModId(), "1.0", "Prueba", ModClassifier.declared(info), ModSource.from(info));
    }

    private static IModInfo info(String id, Path path, Map<String, Object> metadata, boolean oneMod) {
        IConfigurable config = new IConfigurable() {
            @SuppressWarnings("unchecked") public <T> Optional<T> getConfigElement(String... key) { return Optional.ofNullable((T) metadata.get(String.join(".", key))); }
            public List<? extends IConfigurable> getConfigList(String... key) { return List.of(); }
        };
        IModFile file = proxy(IModFile.class, Map.of("getFilePath", path));
        Map<String, Object> ownerValues = new HashMap<>(Map.of("getFile", file, "getConfig", config));
        IModFileInfo owner = proxy(IModFileInfo.class, ownerValues);
        IModInfo info = proxy(IModInfo.class, Map.of("getModId", id, "getOwningFile", owner,
                "getVersion", new DefaultArtifactVersion("1.0"), "getConfig", config));
        ownerValues.put("getMods", oneMod ? List.of(info) : List.of(info, info));
        return info;
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> values.get(method.getName())));
    }

    private static Path jar(Path path, String... names) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String name : names) { zip.putNextEntry(new ZipEntry(name)); zip.write("{}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
        return path;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        RESULTS.add("OK: " + message);
    }
}

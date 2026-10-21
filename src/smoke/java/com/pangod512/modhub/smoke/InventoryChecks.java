package com.pangod512.modhub.smoke;

import com.pangod512.modhub.core.*;

import com.pangod512.modhub.client.ModRow;
import com.pangod512.modhub.client.ModSource;
import cpw.mods.jarhandling.SecureJar;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

/** Real file-system fixtures, including Forge's jar-in-jar and union providers. */
final class InventoryChecks {
    static List<String> verify(List<ModRow> rows, Path output) throws Exception {
        List<String> checks = new ArrayList<>();
        List<String> inventory = new ArrayList<>(List.of("ID\tCarga\tArchivo\tUbicacion"));
        for (ModRow row : rows) {
            ModSource source = row.source();
            boolean integrated = row.info().getModId().equals("minecraft") || row.info().getModId().equals("forge");
            check(integrated ? source.kind() == ModSource.Kind.INTEGRATED
                            : source.kind() != ModSource.Kind.UNKNOWN && source.path().isPresent(),
                    "Archivo asociado al mod cargado " + row.info().getModId(), checks);
            inventory.add(row.info().getModId() + "\t" + source.kind() + "\t" + source.fileName()
                    + "\t" + source.location().replace('\t', ' ').replace('\n', ' '));
        }
        Files.createDirectories(output);
        Files.write(output.resolve("inventario.tsv"), inventory);
        Path fixtures = Files.createDirectories(output.resolve("fixtures"));
        Path outer = fixtures.resolve("contenedor con espacios.jar");
        byte[] grandchild = zip("fixture.txt", new byte[] {1});
        byte[] inner = zip(Map.of("META-INF/jarjar/tercero.jar", grandchild,
                "data/anidado/recipes/prueba.json", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.write(outer, zip("META-INF/jarjar/interno.jar", inner));

        ModSource jar = ModSource.from(info("fixture", outer));
        check(jar.kind() == ModSource.Kind.JAR && jar.path().orElseThrow().equals(outer.toAbsolutePath()),
                "JAR normal con espacios conserva su ruta exacta", checks);
        check(ModSource.from(info("otro_id", outer)).path().equals(jar.path()),
                "Dos identificadores pueden pertenecer al mismo JAR", checks);
        Path duplicate = Files.createDirectories(fixtures.resolve("otra carpeta")).resolve(outer.getFileName());
        Files.write(duplicate, grandchild);
        check(!ModSource.from(info("duplicado", duplicate)).path().equals(jar.path()),
                "Archivos con el mismo nombre en carpetas distintas no se confunden", checks);
        check(ModSource.from(info("desarrollo", fixtures)).kind() == ModSource.Kind.DEVELOPMENT,
                "Las carpetas de desarrollo no se presentan como JAR", checks);
        check(ModSource.from(info("faltante", fixtures.resolve("no-existe.jar"))).kind() == ModSource.Kind.UNKNOWN,
                "Una ruta inexistente no se inventa como archivo valido", checks);
        check(ModSource.from(proxy(IModInfo.class, Map.of("getModId", "sin_archivo"))).kind() == ModSource.Kind.UNKNOWN,
                "Metadatos sin archivo no impiden abrir la lista", checks);
        check(ModSource.from(info("minecraft", outer)).kind() == ModSource.Kind.INTEGRATED
                        && ModSource.from(info("forge", outer)).path().isEmpty(),
                "Minecraft y Forge se muestran integrados y sin archivo exportable", checks);

        SecureJar secure = SecureJar.from(outer);
        try (FileSystem union = secure.getRootPath().getFileSystem()) {
            Path entry = secure.getPath("META-INF", "jarjar", "interno.jar");
            String innerHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512").digest(inner));
            check(com.pangod512.modhub.core.ModrinthResolver.fingerprint(entry).equals(innerHash),
                    "La huella del JAR incluido corresponde a sus bytes, sin extraerlo ni usar el contenedor", checks);
            try (FileSystem nested = nested(entry)) {
                ModSource source = ModSource.from(info("anidado", nested.getPath("/")));
                check(source.kind() == ModSource.Kind.NESTED && source.fileName().equals("interno.jar"),
                        "Jar-in-jar real se identifica como incluido en otro mod", checks);
                check(source.location().contains(outer.getFileName().toString())
                                && source.location().contains("META-INF") && source.location().contains("interno.jar"),
                        "Jar-in-jar conserva la ubicacion del contenedor y de la entrada", checks);
                SecureJar nestedArchive = SecureJar.from(nested.getPath("/"));
                try (FileSystem contentRoot = nestedArchive.getRootPath().getFileSystem()) {
                    var nestedInfo = info("anidado", nested.getPath("/"), nestedArchive.getRootPath());
                    var nestedRow = new ModRow(nestedInfo, "Anidado", "1", "Prueba",
                            com.pangod512.modhub.core.ModClassification.unknown("Prueba"), source);
                    var nestedClassification = com.pangod512.modhub.client.JarInspector.analyze(nestedRow).get(20, java.util.concurrent.TimeUnit.SECONDS);
                    check(nestedClassification.origin() == com.pangod512.modhub.core.ModClassification.Origin.ESTIMATED,
                            "Los datos de un JAR anidado se analizan sin extraerlo: " + nestedClassification.reason(), checks);
                }
                try (FileSystem deeper = nested(nested.getPath("/META-INF/jarjar/tercero.jar"))) {
                    Path thirdEntry = ((net.minecraftforge.jarjar.nio.pathfs.PathFileSystem) deeper).getTarget();
                    String thirdHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512").digest(grandchild));
                    check(com.pangod512.modhub.core.ModrinthResolver.fingerprint(thirdEntry).equals(thirdHash),
                            "La huella funciona tambien con dos niveles reales de anidacion", checks);
                    ModSource third = ModSource.from(info("mas_anidado", deeper.getPath("/")));
                    check(third.kind() == ModSource.Kind.NESTED && third.fileName().equals("tercero.jar")
                                    && third.location().contains("interno.jar")
                                    && third.location().contains(outer.getFileName().toString()),
                            "Dos niveles de anidacion conservan la cadena de contenedores", checks);
                }
            }
        }
        return checks;
    }

    private static FileSystem nested(Path entry) throws Exception {
        URI uri = new URI("jij:" + entry.toAbsolutePath().toUri().getRawSchemeSpecificPart());
        return FileSystems.newFileSystem(uri, Map.of("packagePath", entry));
    }

    private static byte[] zip(String name, byte[] data) throws Exception {
        return zip(Map.of(name, data));
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static IModInfo info(String id, Path path) {
        return info(id, path, path);
    }

    private static IModInfo info(String id, Path path, Path root) {
        IModFile file = proxy(IModFile.class, Map.of("getFilePath", path, "findResource", root));
        IModFileInfo owner = proxy(IModFileInfo.class, Map.of("getFile", file));
        return proxy(IModInfo.class, Map.of("getModId", id, "getOwningFile", owner));
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> values.get(method.getName())));
    }

    private static void check(boolean condition, String message, List<String> checks) {
        if (!condition) throw new AssertionError(message);
        checks.add("OK: " + message);
    }
}

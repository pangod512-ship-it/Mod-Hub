package com.pangod512.modhub.smoke;

import com.pangod512.modhub.core.*;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.pangod512.modhub.client.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import net.minecraftforge.forgespi.language.*;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/** UI-only real metadata fixture. Reads installed JARs without loading their classes. */
final class RealModRows implements AutoCloseable {
    final List<ModRow> rows = new ArrayList<>();
    private final List<FileSystem> archives = new ArrayList<>();
    RealModRows(Path folder) throws Exception {
        try (var paths = Files.list(folder)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                FileSystem archive = FileSystems.newFileSystem(path); archives.add(archive);
                Path metadata = archive.getPath("/META-INF/mods.toml");
                if (!Files.exists(metadata)) continue;
                Config root = new TomlParser().parse(Files.readString(metadata));
                List<Config> definitions = root.get("mods");
                if (definitions == null) continue;
                List<IModInfo> infos = new ArrayList<>();
                IModFile file = IModFile.class.cast(Proxy.newProxyInstance(IModFile.class.getClassLoader(), new Class<?>[] {IModFile.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getFilePath" -> path;
                            case "findResource" -> archive.getPath("/", (String[]) args[0]);
                            default -> null;
                        }));
                IModFileInfo owner = proxy(IModFileInfo.class, Map.of("getFile", file, "getMods", infos, "getConfig", config(root)));
                for (Config definition : definitions) {
                    String id = definition.get("modId");
                    String name = definition.getOrElse("displayName", id);
                    String version = definition.getOrElse("version", "Sin indicar");
                    if (version.contains("${")) version = "Versión del archivo";
                    String author = definition.getOrElse("authors", "No indicado");
                    IModInfo info = proxy(IModInfo.class, Map.of("getModId", id, "getDisplayName", name,
                            "getVersion", new DefaultArtifactVersion(version), "getDescription", definition.getOrElse("description", ""),
                            "getLogoFile", Optional.ofNullable(definition.get("logoFile")), "getLogoBlur", false,
                            "getOwningFile", owner, "getConfig", config(definition)));
                    infos.add(info);
                    rows.add(new ModRow(info, name, version, author, ModClassifier.declared(info), ModSource.from(info)));
                }
            }
        }
        rows.sort(Comparator.comparing(ModRow::name, String.CASE_INSENSITIVE_ORDER));
    }
    private static IConfigurable config(Config value) {
        return new IConfigurable() {
            public <T> Optional<T> getConfigElement(String... key) { return Optional.ofNullable(value.get(String.join(".", key))); }
            public List<? extends IConfigurable> getConfigList(String... key) { return List.of(); }
        };
    }
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> values.get(method.getName())));
    }
    @Override public void close() throws Exception { for (FileSystem archive : archives) archive.close(); }
}

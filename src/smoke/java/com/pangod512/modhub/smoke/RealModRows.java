package com.pangod512.modhub.smoke;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.pangod512.modhub.client.*;
import com.pangod512.modhub.core.*;
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
      for (Path path :
          paths.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
        FileSystem archive = FileSystems.newFileSystem(path);
        archives.add(archive);
        Path metadata = archive.getPath("/META-INF/mods.toml");
        Config root = readMetadata(archive, metadata);
        if (root == null) continue;
        List<Config> definitions = root.get("mods");
        if (definitions == null) continue;
        List<IModInfo> infos = new ArrayList<>();
        IModFile file =
            IModFile.class.cast(
                Proxy.newProxyInstance(
                    IModFile.class.getClassLoader(),
                    new Class<?>[] {IModFile.class},
                    (proxy, method, args) ->
                        switch (method.getName()) {
                          case "getFilePath" -> path;
                          case "findResource" -> archive.getPath("/", (String[]) args[0]);
                          default -> null;
                        }));
        IModFileInfo owner =
            proxy(
                IModFileInfo.class,
                Map.of("getFile", file, "getMods", infos, "getConfig", config(root)));
        for (Config definition : definitions) {
          String id = definition.get("modId");
          String name = text(definition.get("displayName"), id);
          String missingVersion = Messages.tr("modhub.not_specified_6b6fcd99", "No indicada");
          String version = text(definition.get("version"), missingVersion);
          if (version.contains("${")) version = missingVersion;
          String author =
              text(definition.get("authors"), Messages.tr("modhub.not_specified", "No indicado"));
          IModInfo info =
              proxy(
                  IModInfo.class,
                  Map.of(
                      "getModId",
                      id,
                      "getDisplayName",
                      name,
                      "getVersion",
                      new DefaultArtifactVersion(version),
                      "getDescription",
                      definition.getOrElse("description", ""),
                      "getLogoFile",
                      Optional.ofNullable(definition.get("logoFile")),
                      "getLogoBlur",
                      false,
                      "getOwningFile",
                      owner,
                      "getConfig",
                      config(definition)));
          infos.add(info);
          rows.add(
              new ModRow(
                  info, name, version, author, ModClassifier.declared(info), ModSource.from(info)));
        }
      }
    }
    rows.sort(Comparator.comparing(ModRow::name, String.CASE_INSENSITIVE_ORDER));
  }

  private static String text(Object value, String fallback) {
    if (value instanceof Collection<?> values) {
      String joined =
          values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
      return joined.isBlank() ? fallback : joined;
    }
    return value == null || value.toString().isBlank() ? fallback : value.toString();
  }

  private static Config readMetadata(FileSystem archive, Path modern) throws Exception {
    if (Files.exists(modern)) return new TomlParser().parse(Files.readString(modern));
    Path legacy = archive.getPath("/mcmod.info");
    if (!Files.exists(legacy)) return null;
    com.google.gson.JsonElement parsed =
        com.google.gson.JsonParser.parseString(Files.readString(legacy));
    com.google.gson.JsonArray mods =
        parsed.isJsonArray()
            ? parsed.getAsJsonArray()
            : parsed.getAsJsonObject().getAsJsonArray("modList");
    if (mods == null) return null;
    List<Config> definitions = new ArrayList<>();
    for (var element : mods) {
      if (!element.isJsonObject()) continue;
      var entry = element.getAsJsonObject();
      if (!entry.has("modid") || !entry.get("modid").isJsonPrimitive()) continue;
      Config definition = Config.inMemory();
      definition.set("modId", entry.get("modid").getAsString());
      for (String key : new String[] {"name", "version", "description", "logoFile"}) {
        if (entry.has(key) && entry.get(key).isJsonPrimitive())
          definition.set(key.equals("name") ? "displayName" : key, entry.get(key).getAsString());
      }
      if (entry.has("authorList") && entry.get("authorList").isJsonArray()) {
        List<String> authors = new ArrayList<>();
        for (var author : entry.getAsJsonArray("authorList"))
          if (author.isJsonPrimitive()) authors.add(author.getAsString());
        definition.set("authors", authors);
      }
      definitions.add(definition);
    }
    Config root = Config.inMemory();
    root.set("mods", definitions);
    return root;
  }

  private static IConfigurable config(Config value) {
    return new IConfigurable() {
      public <T> Optional<T> getConfigElement(String... key) {
        return Optional.ofNullable(value.get(String.join(".", key)));
      }

      public List<? extends IConfigurable> getConfigList(String... key) {
        return List.of();
      }
    };
  }

  private static <T> T proxy(Class<T> type, Map<String, Object> values) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> values.get(method.getName())));
  }

  @Override
  public void close() throws Exception {
    for (FileSystem archive : archives) archive.close();
  }
}

package com.pangod512.modhub.core;

import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public read-only API. Only SHA-512 hashes are sent; no archives, paths or mod IDs. */
public final class ModrinthClient {
  public static final Set<String> ENVIRONMENTS =
      new java.util.HashSet<>(
          java.util.Arrays.asList(
              "client_only",
              "server_only",
              "dedicated_server_only",
              "client_and_server",
              "client_only_server_optional",
              "server_only_client_optional",
              "singleplayer_only",
              "client_or_server",
              "client_or_server_prefers_both",
              "unknown"));

  public static final class Response {
    private final int status;
    private final String body;
    private final long retrySeconds;

    @Override
    public boolean equals(Object value) {
      if (this == value) return true;
      if (!(value instanceof Response)) return false;
      Response other = (Response) value;
      return this.status == other.status
          && java.util.Objects.equals(this.body, other.body)
          && this.retrySeconds == other.retrySeconds;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(status, body, retrySeconds);
    }

    public Response(int status, String body, long retrySeconds) {
      this.status = status;
      this.body = body;
      this.retrySeconds = retrySeconds;
    }

    public int status() {
      return status;
    }

    public String body() {
      return body;
    }

    public long retrySeconds() {
      return retrySeconds;
    }
  }

  @FunctionalInterface
  public interface Transport {
    Response request(String method, String path, String body) throws IOException;
  }

  public static final class ApiFailure extends IOException {
    public final long retrySeconds;

    ApiFailure(String message, long retrySeconds) {
      super(message);
      this.retrySeconds = retrySeconds;
    }
  }

  private final Transport transport;
  private final CompatibilityContext context;

  public ModrinthClient(Transport transport) {
    this(transport, new CompatibilityContext("1.20.1", "forge"));
  }

  public ModrinthClient(Transport transport, CompatibilityContext context) {
    this.transport = transport;
    this.context = context;
  }

  public CompatibilityContext context() {
    return context;
  }

  public static ModrinthClient production(String version) {
    return production(version, new CompatibilityContext("1.20.1", "forge"));
  }

  public static ModrinthClient production(String version, CompatibilityContext context) {
    return new ModrinthClient(
        (method, path, body) -> {
          HttpURLConnection connection =
              (HttpURLConnection)
                  URI.create("https://api.modrinth.com/v2" + path).toURL().openConnection();
          connection.setConnectTimeout(5000);
          connection.setReadTimeout(8000);
          connection.setInstanceFollowRedirects(false);
          connection.setRequestMethod(method);
          connection.setRequestProperty("User-Agent", "pangod512-ship-it/Mod-Hub/" + version);
          connection.setRequestProperty("Accept", "application/json");
          try {
            if (body != null) {
              byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
              connection.setDoOutput(true);
              connection.setFixedLengthStreamingMode(bytes.length);
              connection.setRequestProperty("Content-Type", "application/json");
              try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
              }
            }
            int status = connection.getResponseCode();
            long retry = 900;
            try {
              retry = Long.parseLong(connection.getHeaderField("Retry-After"));
            } catch (NumberFormatException ignored) {
              try {
                retry = Long.parseLong(connection.getHeaderField("X-Ratelimit-Reset"));
              } catch (NumberFormatException alsoIgnored) {
                /* Keep the 15-minute cooldown when rate-limit headers are missing or invalid. */
              }
            }
            String text = "";
            if (status == 200) {
              try (java.io.InputStream input = connection.getInputStream()) {
                byte[] bytes = Io.readLimited(input, 4_194_304);
                if (bytes.length > 4_194_304) throw new IOException("Respuesta demasiado grande");
                text = new String(bytes, StandardCharsets.UTF_8);
              }
            }
            return new Response(status, text, Math.max(60, Math.min(86400, retry)));
          } finally {
            connection.disconnect();
          }
        },
        context);
  }

  public Map<String, ModrinthCache.Entry> lookup(List<String> hashes, long now) throws IOException {
    if (hashes.isEmpty()) return java.util.Collections.emptyMap();
    if (hashes.size() > 100 || hashes.stream().anyMatch(h -> !h.matches("[0-9a-f]{128}")))
      throw new IOException("Lote no válido");
    try {
      JsonObject request = new JsonObject();
      JsonArray array = new JsonArray();
      hashes.forEach(array::add);
      request.add("hashes", array);
      request.addProperty("algorithm", "sha512");
      JsonObject versions =
          json(transport.request("POST", "/version_files", request.toString())).getAsJsonObject();
      Map<String, JsonObject> matches = new LinkedHashMap<>();
      Set<String> projectIds = new LinkedHashSet<>();
      for (String hash : hashes) {
        if (!versions.has(hash)) continue;
        JsonObject version = versions.getAsJsonObject(hash);
        if (!id(version, "id")
            || !id(version, "project_id")
            || !contains(version.getAsJsonArray("loaders"), context.loader())
            || !contains(version.getAsJsonArray("game_versions"), context.minecraft())) continue;
        boolean exactFile = false;
        for (JsonElement file : version.getAsJsonArray("files")) {
          JsonObject sums = file.getAsJsonObject().getAsJsonObject("hashes");
          if (hash.equals(string(sums, "sha512"))) exactFile = true;
        }
        if (!exactFile) continue;
        matches.put(hash, version);
        if (string(version, "environment").isEmpty())
          projectIds.add(version.get("project_id").getAsString());
      }
      Map<String, JsonObject> projects = new HashMap<>();
      if (!projectIds.isEmpty()) {
        JsonArray ids = new JsonArray();
        projectIds.forEach(ids::add);
        String query = URLEncoder.encode(ids.toString(), "UTF-8");
        for (JsonElement value :
            json(transport.request("GET", "/projects?ids=" + query, null)).getAsJsonArray()) {
          JsonObject project = value.getAsJsonObject();
          if (id(project, "id") && "mod".equals(string(project, "project_type")))
            projects.put(string(project, "id"), project);
        }
      }
      Map<String, ModrinthCache.Entry> results = new HashMap<>();
      for (String hash : hashes) {
        JsonObject version = matches.get(hash);
        if (version == null) {
          // Missing and incompatible results are temporary, never a guessed side.
          results.put(hash, new ModrinthCache.Entry("", "", "unknown", false, now));
          continue;
        }
        String env = string(version, "environment");
        boolean specific = !env.isEmpty();
        if (!specific) env = projectEnvironment(projects.get(string(version, "project_id")));
        if (!ENVIRONMENTS.contains(env)) env = "unknown";
        results.put(
            hash,
            new ModrinthCache.Entry(
                string(version, "project_id"), string(version, "id"), env, specific, now));
      }
      return results;
    } catch (RuntimeException | StackOverflowError failure) {
      throw new IOException("Respuesta de Modrinth no válida", failure);
    }
  }

  private static JsonElement json(Response response) throws IOException {
    if (response.status() != 200)
      throw new ApiFailure("Modrinth HTTP " + response.status(), response.retrySeconds());
    return new JsonParser().parse(response.body());
  }

  private static boolean id(JsonObject value, String key) {
    return string(value, key).matches("[a-zA-Z0-9]{8}");
  }

  private static String string(JsonObject value, String key) {
    return value != null
            && value.has(key)
            && value.get(key).isJsonPrimitive()
            && value.getAsJsonPrimitive(key).isString()
        ? value.get(key).getAsString()
        : "";
  }

  private static boolean contains(JsonArray values, String wanted) {
    if (values == null) return false;
    for (JsonElement value : values)
      if (value.isJsonPrimitive() && wanted.equals(value.getAsString())) return true;
    return false;
  }

  private static String projectEnvironment(JsonObject project) {
    if (project == null) return "unknown";
    if (project.has("environment") && project.get("environment").isJsonArray()) {
      Set<String> values = new HashSet<>();
      for (JsonElement value : project.getAsJsonArray("environment"))
        values.add(value.getAsString());
      return values.size() == 1 ? values.iterator().next() : "unknown";
    }
    switch (string(project, "client_side") + "/" + string(project, "server_side")) {
      case "required/unsupported":
        return "client_only";
      case "unsupported/required":
        return "server_only";
      case "required/required":
        return "client_and_server";
      case "required/optional":
        return "client_only_server_optional";
      case "optional/required":
        return "server_only_client_optional";
      default:
        return "unknown";
    }
  }

  public static ModClassification classification(ModrinthCache.Entry entry) {
    ModType type;
    switch (entry.environment()) {
      case "client_only":
        type = ModType.CLIENT;
        break;
      case "server_only":
      case "dedicated_server_only":
        type = ModType.SERVER;
        break;
      case "client_and_server":
      case "client_only_server_optional":
      case "server_only_client_optional":
      case "client_or_server":
      case "client_or_server_prefers_both":
        type = ModType.BOTH;
        break;
      default:
        type = ModType.UNKNOWN;
        break;
    }
    String explanation;
    switch (entry.environment()) {
      case "client_only":
        explanation = "Funciona únicamente en el cliente.";
        break;
      case "server_only":
        explanation = "Funciona en el servidor; también puede usarse en un jugador.";
        break;
      case "dedicated_server_only":
        explanation = "Funciona únicamente en un servidor dedicado.";
        break;
      case "client_and_server":
        explanation = "Debe instalarse tanto en el cliente como en el servidor.";
        break;
      case "client_only_server_optional":
        explanation =
            "Obligatorio en cliente; opcional en servidor. Ambos indica compatibilidad, no"
                + " obligación en los dos.";
        break;
      case "server_only_client_optional":
        explanation =
            "Obligatorio en servidor; opcional en cliente. Ambos indica compatibilidad, no"
                + " obligación en los dos.";
        break;
      case "client_or_server":
        explanation =
            "Funciona en cualquiera de los dos lados por separado; no es necesario instalarlo en"
                + " ambos.";
        break;
      case "client_or_server_prefers_both":
        explanation = "Funciona por separado en cada lado y mejora si se instala en ambos.";
        break;
      case "singleplayer_only":
        explanation = "Solo funciona en un jugador. No se asigna un tipo para multijugador.";
        break;
      default:
        explanation = "La información publicada no permite determinar el tipo.";
        break;
    }
    return new ModClassification(
        type,
        ModClassification.Origin.MODRINTH,
        (entry.versionSpecific()
                ? "Datos de la versión exacta identificada por su huella. "
                : "Datos generales del proyecto; podrían variar entre versiones. ")
            + explanation
            + " Proyecto: "
            + entry.project()
            + "; versión: "
            + entry.version()
            + ".");
  }

  /** Local declarations win; contradictory remote evidence remains visible. */
  public static ModClassification combine(ModClassification local, ModClassification remote) {
    if (remote == null) return local;
    if (local.origin() == ModClassification.Origin.MANUAL) return local;
    if (local.origin() == ModClassification.Origin.DECLARED) {
      if (remote.type() != ModType.UNKNOWN && remote.type() != local.type()) {
        return new ModClassification(
            local.type(),
            local.origin(),
            local.reason()
                + " Aviso: Modrinth indica "
                + remote.type().label
                + ". Se conserva la declaración local; revisa la diferencia.");
      }
      return local;
    }
    return remote;
  }
}

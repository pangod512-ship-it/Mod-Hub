package com.pangod512.modhub.client;

import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public read-only API. Only SHA-512 hashes are sent; no archives, paths or mod IDs. */
public final class ModrinthClient {
    public static final Set<String> ENVIRONMENTS = Set.of("client_only", "server_only", "dedicated_server_only",
            "client_and_server", "client_only_server_optional", "server_only_client_optional", "singleplayer_only",
            "client_or_server", "client_or_server_prefers_both", "unknown");
    public record Response(int status, String body, long retrySeconds) {}
    @FunctionalInterface public interface Transport { Response request(String method, String path, String body) throws IOException; }
    public static final class ApiFailure extends IOException {
        public final long retrySeconds;
        ApiFailure(String message, long retrySeconds) { super(message); this.retrySeconds = retrySeconds; }
    }
    private final Transport transport;

    public ModrinthClient(Transport transport) { this.transport = transport; }
    public static ModrinthClient production(String version) {
        return new ModrinthClient((method, path, body) -> {
            HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.modrinth.com/v2" + path).toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false); connection.setRequestMethod(method);
            connection.setRequestProperty("User-Agent", "pangod512-ship-it/Modisto_hub_beta_0.0.1/" + version);
            connection.setRequestProperty("Accept", "application/json");
            try {
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                    connection.setRequestProperty("Content-Type", "application/json");
                    try (var output = connection.getOutputStream()) { output.write(bytes); }
                }
                int status = connection.getResponseCode();
                long retry = 900;
                try { retry = Long.parseLong(connection.getHeaderField("Retry-After")); }
                catch (NumberFormatException ignored) {
                    try { retry = Long.parseLong(connection.getHeaderField("X-Ratelimit-Reset")); }
                    catch (NumberFormatException alsoIgnored) { /* Default cooldown. */ }
                }
                String text = "";
                if (status == 200) {
                    try (var input = connection.getInputStream()) {
                        byte[] bytes = input.readNBytes(4_194_305);
                        if (bytes.length > 4_194_304) throw new IOException("Respuesta demasiado grande");
                        text = new String(bytes, StandardCharsets.UTF_8);
                    }
                }
                return new Response(status, text, Math.max(60, Math.min(86400, retry)));
            } finally { connection.disconnect(); }
        });
    }

    public Map<String, ModrinthCache.Entry> lookup(List<String> hashes, long now) throws IOException {
        if (hashes.isEmpty()) return Map.of();
        if (hashes.size() > 100 || hashes.stream().anyMatch(h -> !h.matches("[0-9a-f]{128}"))) throw new IOException("Lote no válido");
        try {
            JsonObject request = new JsonObject(); JsonArray array = new JsonArray();
            hashes.forEach(array::add); request.add("hashes", array); request.addProperty("algorithm", "sha512");
            JsonObject versions = json(transport.request("POST", "/version_files", request.toString())).getAsJsonObject();
            Map<String, JsonObject> matches = new LinkedHashMap<>();
            Set<String> projectIds = new LinkedHashSet<>();
            for (String hash : hashes) {
                if (!versions.has(hash)) continue;
                JsonObject version = versions.getAsJsonObject(hash);
                if (!id(version, "id") || !id(version, "project_id") || !contains(version.getAsJsonArray("loaders"), "forge")
                        || !contains(version.getAsJsonArray("game_versions"), "1.20.1")) continue;
                boolean exactFile = false;
                for (JsonElement file : version.getAsJsonArray("files")) {
                    JsonObject sums = file.getAsJsonObject().getAsJsonObject("hashes");
                    if (hash.equals(string(sums, "sha512"))) exactFile = true;
                }
                if (!exactFile) continue;
                matches.put(hash, version);
                if (string(version, "environment").isEmpty()) projectIds.add(version.get("project_id").getAsString());
            }
            Map<String, JsonObject> projects = new HashMap<>();
            if (!projectIds.isEmpty()) {
                JsonArray ids = new JsonArray(); projectIds.forEach(ids::add);
                String query = URLEncoder.encode(ids.toString(), StandardCharsets.UTF_8);
                for (JsonElement value : json(transport.request("GET", "/projects?ids=" + query, null)).getAsJsonArray()) {
                    JsonObject project = value.getAsJsonObject();
                    if (id(project, "id") && "mod".equals(string(project, "project_type"))) projects.put(string(project, "id"), project);
                }
            }
            Map<String, ModrinthCache.Entry> results = new HashMap<>();
            for (String hash : hashes) {
                JsonObject version = matches.get(hash);
                if (version == null) {
                    // Missing and incompatible results are temporary, never a guessed side.
                    results.put(hash, new ModrinthCache.Entry("", "", "unknown", false, now)); continue;
                }
                String env = string(version, "environment");
                boolean specific = !env.isEmpty();
                if (!specific) env = projectEnvironment(projects.get(string(version, "project_id")));
                if (!ENVIRONMENTS.contains(env)) env = "unknown";
                results.put(hash, new ModrinthCache.Entry(string(version, "project_id"), string(version, "id"), env, specific, now));
            }
            return results;
        } catch (RuntimeException | StackOverflowError failure) { throw new IOException("Respuesta de Modrinth no válida", failure); }
    }

    private static JsonElement json(Response response) throws IOException {
        if (response.status() != 200) throw new ApiFailure("Modrinth HTTP " + response.status(), response.retrySeconds());
        return JsonParser.parseString(response.body());
    }
    private static boolean id(JsonObject value, String key) { return string(value, key).matches("[a-zA-Z0-9]{8}"); }
    private static String string(JsonObject value, String key) {
        return value != null && value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isString()
                ? value.get(key).getAsString() : "";
    }
    private static boolean contains(JsonArray values, String wanted) {
        if (values == null) return false;
        for (JsonElement value : values) if (value.isJsonPrimitive() && wanted.equals(value.getAsString())) return true;
        return false;
    }
    private static String projectEnvironment(JsonObject project) {
        if (project == null) return "unknown";
        if (project.has("environment") && project.get("environment").isJsonArray()) {
            Set<String> values = new HashSet<>();
            for (JsonElement value : project.getAsJsonArray("environment")) values.add(value.getAsString());
            return values.size() == 1 ? values.iterator().next() : "unknown";
        }
        return switch (string(project, "client_side") + "/" + string(project, "server_side")) {
            case "required/unsupported" -> "client_only";
            case "unsupported/required" -> "server_only";
            case "required/required" -> "client_and_server";
            case "required/optional" -> "client_only_server_optional";
            case "optional/required" -> "server_only_client_optional";
            default -> "unknown";
        };
    }

    public static ModClassification classification(ModrinthCache.Entry entry) {
        ModType type = switch (entry.environment()) {
            case "client_only" -> ModType.CLIENT;
            case "server_only", "dedicated_server_only" -> ModType.SERVER;
            case "client_and_server", "client_only_server_optional", "server_only_client_optional",
                    "client_or_server", "client_or_server_prefers_both" -> ModType.BOTH;
            default -> ModType.UNKNOWN;
        };
        String explanation = switch (entry.environment()) {
            case "client_only" -> "Funciona únicamente en el cliente.";
            case "server_only" -> "Funciona en el servidor; también puede usarse en un jugador.";
            case "dedicated_server_only" -> "Funciona únicamente en un servidor dedicado.";
            case "client_and_server" -> "Debe instalarse tanto en el cliente como en el servidor.";
            case "client_only_server_optional" -> "Obligatorio en cliente; opcional en servidor. Ambos indica compatibilidad, no obligación en los dos.";
            case "server_only_client_optional" -> "Obligatorio en servidor; opcional en cliente. Ambos indica compatibilidad, no obligación en los dos.";
            case "client_or_server" -> "Funciona en cualquiera de los dos lados por separado; no es necesario instalarlo en ambos.";
            case "client_or_server_prefers_both" -> "Funciona por separado en cada lado y mejora si se instala en ambos.";
            case "singleplayer_only" -> "Solo funciona en un jugador. No se asigna un tipo para multijugador.";
            default -> "La información publicada no permite determinar el tipo.";
        };
        return new ModClassification(type, ModClassification.Origin.MODRINTH,
                (entry.versionSpecific() ? "Datos de la versión exacta identificada por su huella. "
                        : "Datos generales del proyecto; podrían variar entre versiones. ") + explanation
                        + " Proyecto: " + entry.project() + "; versión: " + entry.version() + ".");
    }

    /** Local declarations win; contradictory remote evidence remains visible. */
    public static ModClassification combine(ModClassification local, ModClassification remote) {
        if (remote == null) return local;
        if (local.origin() == ModClassification.Origin.MANUAL) return local;
        if (local.origin() == ModClassification.Origin.DECLARED) {
            if (remote.type() != ModType.UNKNOWN && remote.type() != local.type()) {
                return new ModClassification(local.type(), local.origin(), local.reason()
                        + " Aviso: Modrinth indica " + remote.type().label + ". Se conserva la declaración local; revisa la diferencia.");
            }
            return local;
        }
        return remote;
    }
}

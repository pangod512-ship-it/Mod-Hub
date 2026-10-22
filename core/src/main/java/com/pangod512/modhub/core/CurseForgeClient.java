package com.pangod512.modhub.core;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Injectable API transport. No production transport exists until the deployment gate is validated.
 */
public final class CurseForgeClient {
  public interface Transport {
    ModrinthClient.Response request(List<Long> fingerprints) throws IOException;
  }

  private final Transport transport;

  public CurseForgeClient(Transport transport) {
    this.transport = transport;
  }

  public Map<String, ModClassification> lookup(
      List<ModrinthResolver.Target> targets, CompatibilityContext context) throws IOException {
    Map<Long, List<ModrinthResolver.Target>> byFingerprint = new HashMap<>();
    for (ModrinthResolver.Target target : targets) {
      long fingerprint = fingerprint(target.file());
      byFingerprint.computeIfAbsent(fingerprint, k -> new ArrayList<>()).add(target);
    }
    Map<String, ModClassification> result = new HashMap<>();
    Set<String> conflicts = new HashSet<>();
    if (byFingerprint.isEmpty()) return result;
    if (byFingerprint.size() > 100) throw new IOException("Lote demasiado grande");
    ModrinthClient.Response response = transport.request(new ArrayList<>(byFingerprint.keySet()));
    if (response.status() != 200)
      throw new ModrinthClient.ApiFailure(
          "CurseForge HTTP " + response.status(), response.retrySeconds());
    try {
      JsonObject data =
          new JsonParser().parse(response.body()).getAsJsonObject().getAsJsonObject("data");
      for (JsonElement matchValue : data.getAsJsonArray("exactMatches")) {
        JsonObject file = matchValue.getAsJsonObject().getAsJsonObject("file");
        List<ModrinthResolver.Target> candidates =
            byFingerprint.get(file.get("fileFingerprint").getAsLong());
        if (candidates == null || file.get("gameId").getAsInt() != 432) continue;
        Set<String> versions = new HashSet<>();
        for (JsonElement v : file.getAsJsonArray("gameVersions"))
          versions.add(v.getAsString().toLowerCase(Locale.ROOT));
        if (!versions.contains(context.minecraft()) || !versions.contains(context.loader()))
          continue;
        for (ModrinthResolver.Target target : candidates) {
          // Murmur fingerprints may collide: require a cryptographic hash of the exact file too.
          if (!verifyHash(file.getAsJsonArray("hashes"), target.file())) continue;
          boolean client = versions.contains("client"), server = versions.contains("server");
          ModType type =
              client && server
                  ? ModType.BOTH
                  : client ? ModType.CLIENT : server ? ModType.SERVER : ModType.UNKNOWN;
          ModClassification classification =
              new ModClassification(
                  type,
                  ModClassification.Origin.CURSEFORGE,
                  "Archivo exacto; solo se utilizan etiquetas explícitas de entorno.");
          ModClassification prior = result.get(target.id());
          if (prior != null && prior.type() != type) conflicts.add(target.id());
          if (conflicts.contains(target.id()))
            classification =
                ModClassification.unknown("Coincidencias contradictorias en CurseForge.");
          result.put(target.id(), classification);
        }
      }
    } catch (RuntimeException | StackOverflowError failure) {
      throw new IOException("Respuesta de CurseForge no válida", failure);
    }
    return result;
  }

  private static boolean verifyHash(JsonArray hashes, Path file) throws IOException {
    if (hashes == null) return false;
    for (JsonElement value : hashes) {
      JsonObject hash = value.getAsJsonObject();
      int algo = hash.get("algo").getAsInt();
      if (algo != 1 && algo != 2) continue;
      try {
        MessageDigest digest = MessageDigest.getInstance(algo == 1 ? "SHA-1" : "MD5");
        try (InputStream in = Files.newInputStream(file)) {
          byte[] buffer = new byte[65536];
          int n;
          while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        if (Io.hex(digest.digest()).equalsIgnoreCase(hash.get("value").getAsString())) return true;
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException(impossible);
      }
    }
    return false;
  }

  /** Unsigned MurmurHash2, seed 1; skips ASCII whitespace in two bounded-memory passes. */
  public static long fingerprint(Path file) throws IOException {
    int length = 0;
    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
      int b;
      while ((b = in.read()) != -1)
        if (!whitespace(b)) {
          if (length == Integer.MAX_VALUE) throw new IOException("Archivo demasiado grande");
          length++;
        }
    }
    int hash = 1 ^ length, word = 0, shift = 0;
    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
      int b;
      while ((b = in.read()) != -1) {
        if (whitespace(b)) continue;
        word |= b << shift;
        shift += 8;
        if (shift == 32) {
          word *= 0x5bd1e995;
          word ^= word >>> 24;
          word *= 0x5bd1e995;
          hash = hash * 0x5bd1e995 ^ word;
          word = 0;
          shift = 0;
        }
      }
    }
    if (shift > 0) {
      hash ^= word;
      hash *= 0x5bd1e995;
    }
    hash ^= hash >>> 13;
    hash *= 0x5bd1e995;
    hash ^= hash >>> 15;
    return hash & 0xffffffffL;
  }

  private static boolean whitespace(int b) {
    return b == 9 || b == 10 || b == 13 || b == 32;
  }
}

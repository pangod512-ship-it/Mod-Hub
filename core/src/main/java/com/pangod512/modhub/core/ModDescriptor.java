package com.pangod512.modhub.core;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;

/** Portable inventory entry. The adapter owns configuration screens and textures. */
public final class ModDescriptor {
  private final String id, name, version, author, description;
  private final Path file;

  public ModDescriptor(
      String id, String name, String version, String author, String description, Path file) {
    this.id = id == null ? "unknown" : id;
    this.name = name == null || name.isEmpty() ? this.id : name;
    this.version = version == null || version.isEmpty() ? "No indicada" : version;
    this.author = author == null || author.isEmpty() ? "No indicado" : author;
    this.description = description == null ? "" : description;
    this.file = file;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String version() {
    return version;
  }

  public String author() {
    return author;
  }

  public String description() {
    return description;
  }

  public Path file() {
    return file;
  }

  public boolean matches(String query, ModType filter, ModClassification classification) {
    return (filter == ModType.ALL || classification.type() == filter)
        && normalize(name + " " + id + " " + author).contains(normalize(query.trim()));
  }

  public static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }
}

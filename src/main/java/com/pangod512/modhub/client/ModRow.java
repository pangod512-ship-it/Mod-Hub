package com.pangod512.modhub.client;

import java.text.Normalizer;
import java.util.Locale;
import net.minecraftforge.forgespi.language.IModInfo;

public record ModRow(IModInfo info, String name, String version, String author, ModClassification classification, ModSource source) {
    public ModRow(IModInfo info, String name, String version, String author, ModType type, ModSource source) {
        this(info, name, version, author,
                new ModClassification(type, ModClassification.Origin.MANUAL, "Datos de prueba."), source);
    }

    public ModType type() { return classification.type(); }
    public ModRow withClassification(ModClassification value) {
        return new ModRow(info, name, version, author, value, source);
    }
    static ModRow from(IModInfo info) {
        String author = info.getConfig().getConfigElement("authors")
                .map(Object::toString).filter(s -> !s.isBlank()).orElse("No indicado");
        return new ModRow(info, clean(info.getDisplayName()), info.getVersion().toString(),
                clean(author), ModClassifier.declared(info), ModSource.from(info));
    }

    boolean matches(String query, ModType filter) {
        return (filter == ModType.ALL || type() == filter)
                && normalize(name + " " + info.getModId() + " " + author).contains(normalize(query.trim()));
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String clean(String text) {
        return text.replaceAll("§.", "").replaceAll("[\\r\\n\\t]+", " ");
    }
}

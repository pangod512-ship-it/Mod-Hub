package com.pangod512.modhub.client;

public record ModClassification(ModType type, Origin origin, String reason) {
    public enum Origin {
        MANUAL("Manual"), DECLARED("Declarado"), MODRINTH("Modrinth"), ESTIMATED("Estimado"), UNKNOWN("Sin determinar");
        public final String label;
        Origin(String label) { this.label = label; }
    }

    public static ModClassification unknown(String reason) {
        return new ModClassification(ModType.UNKNOWN, Origin.UNKNOWN, reason);
    }

    /** Estimates are proposals: a future export must require confirmation. */
    public boolean confirmed() { return origin == Origin.MANUAL || origin == Origin.DECLARED; }
    public String label() { return type.label + (origin == Origin.ESTIMATED ? " ~" : ""); }
}

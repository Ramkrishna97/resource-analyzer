package com.example.pageanalyzer;

public enum SummaryLevel {
    LOW(0.86, "Keep nearly all details, examples, and supporting context."),
    MEDIUM(0.50, "Keep the main points and useful supporting details."),
    HIGH(0.20, "Keep only the core ideas, decisions, and essential facts.");

    private final double targetRatio;
    private final String guidance;

    SummaryLevel(double targetRatio, String guidance) {
        this.targetRatio = targetRatio;
        this.guidance = guidance;
    }

    public double targetRatio() { return targetRatio; }
    public String guidance() { return guidance; }

    public static SummaryLevel from(String value) {
        try { return value == null ? MEDIUM : valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return MEDIUM; }
    }
}

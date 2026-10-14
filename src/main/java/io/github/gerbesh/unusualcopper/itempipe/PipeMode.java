package io.github.gerbesh.unusualcopper.itempipe;

public enum PipeMode {
    AUTO, EXTRACT, INSERT, DISABLED;
    public PipeMode next() { return values()[(ordinal() + 1) % values().length]; }
    public String translationKey() { return "pipe.unusualcopper.mode." + name().toLowerCase(java.util.Locale.ROOT); }
}

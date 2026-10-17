package io.github.gerbesh.unusualcopper.machine;

/** Static, bounded recipes for E5A. No external inventory can reserve these materials. */
public final class ProductionOrderPolicy {
    public static final int MAX_QUEUE = 4096;
    private ProductionOrderPolicy() {}

    public record Plan(String recipe, int ticks, int cue) {
        public Plan {
            if (recipe == null || recipe.isBlank() || ticks < 1 || cue < 0)
                throw new IllegalArgumentException("Invalid production plan");
        }
    }

    public static Plan plan(int pattern) {
        return switch (pattern) {
            case 0 -> new Plan("resonant_coil", 80, 240);
            case 1 -> new Plan("reinforced_frame", 120, 1800);
            default -> throw new IllegalArgumentException("Unsupported pattern");
        };
    }

    public static int queue(int queued, int requested) {
        if (queued < 0 || queued > MAX_QUEUE || requested < 0)
            throw new IllegalArgumentException("Invalid order size");
        return (int)Math.min(MAX_QUEUE, (long)queued + requested);
    }
}

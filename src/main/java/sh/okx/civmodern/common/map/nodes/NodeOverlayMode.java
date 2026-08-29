package sh.okx.civmodern.common.map.nodes;

/**
 * How the node territory layer is drawn over the map: at full strength, faded so the map
 * underneath stays readable, or not at all. The map button cycles these in declaration order.
 */
public enum NodeOverlayMode {
    ON(1f),
    TRANSLUCENT(0.4f),
    OFF(0f);

    private final float inkMultiplier;

    NodeOverlayMode(float inkMultiplier) {
        this.inkMultiplier = inkMultiplier;
    }

    /**
     * Scales every colour the overlay draws — fills, seams, grid, and bastion markers alike —
     * on top of the user's configured fill opacity, so TRANSLUCENT fades the whole layer as one
     * rather than leaving opaque seams over a see-through fill.
     */
    public float inkMultiplier() {
        return inkMultiplier;
    }

    public boolean isVisible() {
        return this != OFF;
    }

    public NodeOverlayMode next() {
        NodeOverlayMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    /** Tolerant of unknown strings, as the rest of the config is: anything unrecognised is ON. */
    public static NodeOverlayMode fromString(String value) {
        if (value != null) {
            for (NodeOverlayMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }
        return ON;
    }
}

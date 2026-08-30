package sh.okx.civmodern.common.map.nodes;

/**
 * How the node territory layer is drawn over the map: at full strength, faded so the map
 * underneath stays readable, or not at all. Toggles cycle these in declaration order. Each
 * visible mode's opacity is a config slider of its own — the mode carries no strength itself.
 */
public enum NodeOverlayMode {
    ON,
    TRANSLUCENT,
    OFF;

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

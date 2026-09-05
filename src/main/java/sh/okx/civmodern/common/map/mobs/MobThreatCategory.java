package sh.okx.civmodern.common.map.mobs;

/**
 * How a mob behaves towards the player before anything provokes it. Used only to seed a mob's
 * default minimap visibility in {@link sh.okx.civmodern.common.CivMapConfig} - the per-mob
 * toggle in the minimap mob config screen is what actually governs rendering afterwards.
 */
public enum MobThreatCategory {
    /** Attacks on sight, unprovoked - zombies, skeletons, creepers, ... */
    HOSTILE(true, 0xFFFF5555),
    /** Passive until provoked (an anger/target mechanic), then fights back - wolves, bees, endermen, ... */
    NEUTRAL(true, 0xFFFFAA00),
    /** Never attacks the player - cows, villagers, ... */
    PASSIVE(false, 0xFF55FF55);

    private final boolean defaultVisible;
    private final int colour;

    MobThreatCategory(boolean defaultVisible, int colour) {
        this.defaultVisible = defaultVisible;
        this.colour = colour;
    }

    public boolean isDefaultVisible() {
        return defaultVisible;
    }

    /** ARGB, used both for the minimap marker tint and the config screen's category tag. */
    public int colour() {
        return colour;
    }
}

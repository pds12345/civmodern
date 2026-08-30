package sh.okx.civmodern.common.radar;

/** How a player is marked for radar name colouring. Unlisted players behave the same as NEUTRAL. */
public enum PlayerRelation {
    FRIENDLY(0xFF55FF55),
    NEUTRAL(-1),
    HOSTILE(0xFFFF5555);

    private final int colour;

    PlayerRelation(int colour) {
        this.colour = colour;
    }

    /** ARGB colour for the player's name in the radar. */
    public int colour() {
        return colour;
    }

    public String toDatabaseKey() {
        return name().toLowerCase();
    }

    public static PlayerRelation fromDatabaseKey(String key) {
        for (PlayerRelation relation : values()) {
            if (relation.toDatabaseKey().equals(key)) {
                return relation;
            }
        }
        return NEUTRAL;
    }
}

package sh.okx.civmodern.common.map.snitches;

import net.minecraft.util.Mth;

import java.time.Duration;
import java.time.Instant;

/**
 * One of the player's own snitches, as listed by JukeAlert's {@code /jalist}.
 *
 * @param name      the snitch's name, empty when it has none
 * @param dormantAt when it will go dormant, derived from the countdown shown at {@code seenAt}
 * @param seenAt    when it was last seen in the list
 */
public record Snitch(int x, int y, int z, String name, String group, SnitchType type, Instant dormantAt, Instant seenAt) {

    /** Time until dormancy; negative once it has passed. */
    public Duration remaining(Instant now) {
        return Duration.between(now, dormantAt);
    }

    public boolean isDormant(Instant now) {
        return !now.isBefore(dormantAt);
    }

    /** How much of the type's full lifespan has been used up: 0 freshly refreshed, 1 dormant. */
    public float lifeConsumed(Instant now) {
        double remaining = remaining(now).toSeconds();
        return (float) Mth.clamp(1.0 - remaining / type.lifespan().toSeconds(), 0.0, 1.0);
    }

    /** Black with a full life ahead, brightening to pure red as dormancy approaches. */
    public int outlineColour(Instant now) {
        int red = Math.round(255f * lifeConsumed(now));
        return 0xFF000000 | red << 16;
    }
}

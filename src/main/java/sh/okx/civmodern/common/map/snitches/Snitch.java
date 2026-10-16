package sh.okx.civmodern.common.map.snitches;

import net.minecraft.util.Mth;

import java.time.Duration;
import java.time.Instant;

/**
 * One of the player's own snitches, as listed by JukeAlert's {@code /jalist}. Its lifecycle
 * state is never stored: it is worked out from the two timestamps whenever it is asked for, so
 * a snitch going dormant or being culled costs nothing until something looks at it.
 *
 * @param name      the snitch's name, empty when it has none
 * @param dormantAt when it goes dormant: read from the countdown for an active snitch, estimated
 *                  as {@code cullAt} minus the type's dormancy for one that was already dormant
 * @param cullAt    when JukeAlert culls (deletes) it: read from the countdown for a dormant
 *                  snitch, estimated as {@code dormantAt} plus the type's dormancy for an active one
 * @param seenAt    when it was last seen in the list
 */
public record Snitch(int x, int y, int z, String name, String group, SnitchType type,
                     Instant dormantAt, Instant cullAt, Instant seenAt) {

    /** Magenta, unchanging: a dormant snitch no longer alerts, but walking into it revives it. */
    public static final int DORMANT_OUTLINE = 0xFFFF00FF;

    public enum State {
        /** Recording and alerting. */
        ACTIVE,
        /** Still placed, but not alerting or logging until a group member refreshes it. */
        DORMANT,
        /** Past its cull time, so JukeAlert has most likely deleted it. */
        CULLED
    }

    /** A snitch listed with "Will go dormant in ...". */
    public static Snitch active(int x, int y, int z, String name, String group, SnitchType type, Instant dormantAt, Instant seenAt) {
        return new Snitch(x, y, z, name, group, type, dormantAt, dormantAt.plus(type.dormancy()), seenAt);
    }

    /** A snitch listed with "Will cull in ...". */
    public static Snitch dormant(int x, int y, int z, String name, String group, SnitchType type, Instant cullAt, Instant seenAt) {
        return new Snitch(x, y, z, name, group, type, cullAt.minus(type.dormancy()), cullAt, seenAt);
    }

    public State state(Instant now) {
        if (now.isBefore(dormantAt)) {
            return State.ACTIVE;
        }
        return now.isBefore(cullAt) ? State.DORMANT : State.CULLED;
    }

    public boolean isCulled(Instant now) {
        return !now.isBefore(cullAt);
    }

    /** Time until dormancy; negative once it has passed. */
    public Duration remaining(Instant now) {
        return Duration.between(now, dormantAt);
    }

    /** Time until culling; negative once it has passed. */
    public Duration untilCull(Instant now) {
        return Duration.between(now, cullAt);
    }

    /** How much of the type's active lifespan has been used up: 0 freshly refreshed, 1 dormant. */
    public float lifeConsumed(Instant now) {
        double remaining = remaining(now).toSeconds();
        return (float) Mth.clamp(1.0 - remaining / type.lifespan().toSeconds(), 0.0, 1.0);
    }

    /**
     * Black with a full life ahead, brightening to pure red as dormancy approaches, then
     * {@link #DORMANT_OUTLINE}. Culled snitches are not drawn, so they have no outline of their own.
     */
    public int outlineColour(Instant now) {
        if (!now.isBefore(dormantAt)) {
            return DORMANT_OUTLINE;
        }
        int red = Math.round(255f * lifeConsumed(now));
        return 0xFF000000 | red << 16;
    }
}

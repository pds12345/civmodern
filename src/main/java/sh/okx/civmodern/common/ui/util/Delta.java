/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.util;

/**
 * Trying to give this utility class a
 * sensible name makes me mald
 */
public final class Delta {

    private Delta() {}

    /**
     * Compute an additive interpolator for smoothly approaching the
     * target value given the current value and some interpolation
     * delta
     *
     * @param current The current value
     * @param target  The target value to approach
     * @param delta   The interpolation delta - this is usually the frame delta,
     *                optionally multiplied by some factor
     * @return The computed interpolator, to be added to the current value
     */
    public static float compute(float current, float target, float delta) {
        float diff = target - current;
        delta = diff * delta;

        return Math.abs(delta) > Math.abs(diff) ? diff : delta;
    }

    /**
     * Compute an additive interpolator for smoothly approaching the
     * target value given the current value and some interpolation
     * delta
     *
     * @param current The current value
     * @param target  The target value to approach
     * @param delta   The interpolation delta - this is usually the frame delta,
     *                optionally multiplied by some factor
     * @return The computed interpolator, to be added to the current value
     */
    public static double compute(double current, double target, double delta) {
        double diff = target - current;
        delta = diff * delta;

        return Math.abs(delta) > Math.abs(diff) ? diff : delta;
    }
}

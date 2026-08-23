/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.core;

/**
 * An easing function which can smoothly move
 * an interpolation value from 0 to 1
 */
public interface Easing {

    Easing LINEAR = x -> x;

    Easing SINE = x -> {
        return (float) (Math.sin(x * Math.PI - Math.PI / 2) * 0.5 + 0.5);
    };

    Easing QUADRATIC = x -> {
        return x < 0.5 ? 2 * x * x : (float) (1 - Math.pow(-2 * x + 2, 2) / 2);
    };

    Easing CUBIC = x -> {
        return x < 0.5 ? 4 * x * x * x : (float) (1 - Math.pow(-2 * x + 2, 3) / 2);
    };

    Easing QUARTIC = x -> {
        return x < 0.5 ? 8 * x * x * x * x : (float) (1 - Math.pow(-2 * x + 2, 4) / 2);
    };

    Easing EXPO = x -> {
        if (x == 0) return 0;
        if (x == 1) return 1;

        return x < 0.5
                ? (float) Math.pow(2, 20 * x - 10) / 2
                : (2 - (float) Math.pow(2, -20 * x + 10)) / 2;
    };

    float apply(float x);

}

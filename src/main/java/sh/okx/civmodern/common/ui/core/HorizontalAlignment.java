/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.core;

import java.util.Locale;

public enum HorizontalAlignment {
    LEFT, CENTER, RIGHT;

    public int align(int componentWidth, int span) {
        return switch (this) {
            case LEFT -> 0;
            case CENTER -> span / 2 - componentWidth / 2;
            case RIGHT -> span - componentWidth;
        };
    }

}

/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.container;

import sh.okx.civmodern.common.ui.core.Sizing;
import sh.okx.civmodern.common.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

public final class UIContainers {

    private UIContainers() {}

    // ------
    // Layout
    // ------

    public static GridLayout grid(Sizing horizontalSizing, Sizing verticalSizing, int rows, int columns) {
        return new GridLayout(horizontalSizing, verticalSizing, rows, columns);
    }

    public static FlowLayout verticalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.VERTICAL);
    }

    public static FlowLayout horizontalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.HORIZONTAL);
    }

    public static FlowLayout ltrTextFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.LTR_TEXT);
    }

    // ------
    // Scroll
    // ------

    // ----------------
    // Utility wrappers
    // ----------------

}

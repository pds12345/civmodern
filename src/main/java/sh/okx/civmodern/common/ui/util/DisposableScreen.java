/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.util;

import net.minecraft.client.gui.screens.Screen;

/**
 * Screens that wish to be notified when the players navigates back to
 * the game instead of to another screen may implement this interface
 * for a more reliable alternative to {@link Screen#removed()}
 */
public interface DisposableScreen {

    /**
     * Invoked when a best-effort algorithm has determined
     * that the player is navigating to return to the game instead of opening
     * another screen - ensured to be called too often than too rarely
     */
    void dispose();

}

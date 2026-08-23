/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.mixins;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

/**
 * Lets {@code LabelComponent} reuse vanilla's clickable-style search to answer "which
 * {@link Style} is under the cursor" without actually running a click: the scanner is
 * redirected so the finder records the hit instead of dispatching it.
 */
@Mixin(ActiveTextCollector.ClickableStyleFinder.class)
public interface ClickableStyleFinderAccessor {
    @Mutable
    @Accessor("styleScanner")
    void civmodern$setStyleScanner(Consumer<Style> setStyleCallback);

    @Accessor("result")
    void civmodern$setResult(Style style);
}

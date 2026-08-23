/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.mixins;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractWidget.class)
public interface AbstractWidgetAccessor {

    @Accessor("height")
    void civmodern$setHeight(int height);

    @Accessor("width")
    void civmodern$setWidth(int width);

    @Accessor("x")
    void civmodern$setX(int x);

    @Accessor("y")
    void civmodern$setY(int y);

    @Accessor("tooltip")
    WidgetTooltipHolder civmodern$getTooltip();
}

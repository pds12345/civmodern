/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.mixins;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {

    @Invoker("renderTooltip")
    void civmodern$drawTooltipImmediately(Font textRenderer, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, @Nullable Identifier texture);

    @Accessor("pose")
    Matrix3x2fStack civmodern$getPose();

    @Mutable
    @Accessor("pose")
    void civmodern$setPose(Matrix3x2fStack matrices);

    @Accessor("scissorStack")
    GuiGraphics.ScissorStack civmodern$getScissorStack();

    @Mutable
    @Accessor("scissorStack")
    void civmodern$setScissorStack(GuiGraphics.ScissorStack scissorStack);

    @Accessor("deferredTooltip")
    void civmodern$setDeferredTooltip(Runnable drawer);

    @Accessor("deferredTooltip")
    Runnable civmodern$getDeferredTooltip();

    @Accessor("mouseX")
    int civmodern$getMouseX();

    @Accessor("mouseY")
    int civmodern$getMouseY();
}

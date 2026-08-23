/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.mixins;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import sh.okx.civmodern.common.ui.util.MatrixStackTransformer;

/**
 * Backs the {@link MatrixStackTransformer} interface injected onto {@link GuiGraphics},
 * which the vendored owo-ui uses for its push/pop/translate/scale helpers.
 *
 * <p>Named apart from the existing {@code GuiGraphicsMixin} so both can target GuiGraphics.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMatrixMixin implements MatrixStackTransformer {

    @Shadow public abstract Matrix3x2fStack pose();

    @Override
    public Matrix3x2fStack getMatrixStack() {
        return this.pose();
    }
}

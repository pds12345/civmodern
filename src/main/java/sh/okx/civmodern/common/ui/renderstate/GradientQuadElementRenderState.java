/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.renderstate;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import sh.okx.civmodern.common.ui.core.Color;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record GradientQuadElementRenderState(
    RenderPipeline pipeline,
    Matrix3x2f pose,
    ScreenRectangle bounds,
    ScreenRectangle scissorArea,
    Color colorTL,
    Color colorTR,
    Color colorBL,
    Color colorBR
) implements GuiElementRenderState {

    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.left(), (float) this.bounds.top()).setColor(this.colorTL.argb());
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.left(), (float) this.bounds.bottom()).setColor(this.colorBL.argb());
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.right(), (float) this.bounds.bottom()).setColor(this.colorBR.argb());
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.right(), (float) this.bounds.top()).setColor(this.colorTR.argb());
    }

    @Override
    public RenderPipeline pipeline() {
        return this.pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return this.scissorArea;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return this.scissorArea != null ? this.scissorArea.intersection(this.bounds) : this.bounds;
    }
}

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
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record RingElementRenderState(
    RenderPipeline pipeline,
    Matrix3x2f pose,
    ScreenRectangle scissorArea,
    int centerX,
    int centerY,
    double angleFrom,
    double angleTo,
    int segments,
    double innerRadius,
    double outerRadius,
    Color innerColor,
    Color outerColor
) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer vertices) {
        double angleStep = Math.toRadians(this.angleTo - this.angleFrom) / this.segments;
        int inColor = this.innerColor.argb();
        int outColor = this.outerColor.argb();

        for (int i = 0; i <= this.segments; i++) {
            double theta = Math.toRadians(this.angleFrom) + i * angleStep;

            vertices.addVertexWith2DPose(this.pose, (float) (this.centerX - Math.cos(theta) * this.outerRadius), (float) (this.centerY - Math.sin(theta) * this.outerRadius))
                .setColor(outColor);
            vertices.addVertexWith2DPose(this.pose, (float) (this.centerX - Math.cos(theta) * this.innerRadius), (float) (this.centerY - Math.sin(theta) * this.innerRadius))
                .setColor(inColor);
        }
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
    public ScreenRectangle bounds() {
        var screenRect = new ScreenRectangle(
            new ScreenPosition((int) (this.centerX - this.outerRadius), (int) (this.centerY - this.outerRadius)),
            (int) Math.ceil(this.outerRadius * 2),
            (int) Math.ceil(this.outerRadius * 2)
        ).transformMaxBounds(this.pose);

        return this.scissorArea != null ? this.scissorArea.intersection(screenRect) : screenRect;
    }
}

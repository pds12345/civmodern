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
import org.joml.Matrix3x2f;
import org.joml.Vector2d;

public record LineElementRenderState(
    RenderPipeline pipeline,
    Matrix3x2f pose,
    ScreenRectangle scissorArea,
    int x0,
    int y0,
    int x1,
    int y1,
    double thiccness,
    Color color
) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer vertices) {
        var offset = new Vector2d(this.x1 - this.x0, this.y1 - this.y0).perpendicular().normalize().mul(this.thiccness * .5d);

        int vColor = this.color.argb();
        vertices.addVertexWith2DPose(this.pose, (float) (x0 + offset.x), (float) (y0 + offset.y)).setColor(vColor);
        vertices.addVertexWith2DPose(this.pose, (float) (x0 - offset.x), (float) (y0 - offset.y)).setColor(vColor);
        vertices.addVertexWith2DPose(this.pose, (float) (x1 - offset.x), (float) (y1 - offset.y)).setColor(vColor);
        vertices.addVertexWith2DPose(this.pose, (float) (x1 + offset.x), (float) (y1 + offset.y)).setColor(vColor);
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle bounds() {
        return new ScreenRectangle(this.x0, this.y0, this.x1 - this.x0, this.y1 - this.y0);
    }
}

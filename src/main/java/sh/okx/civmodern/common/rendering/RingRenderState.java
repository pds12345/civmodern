package sh.okx.civmodern.common.rendering;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

/**
 * A flat ring, drawn as one quad per segment. The circular minimap's border. Uses the same
 * segment count as the picture mask so the ring's inner edge lands exactly on the map's edge.
 */
public record RingRenderState(
    Matrix3x2f pose,
    ScreenRectangle scissorArea,
    float centreX,
    float centreY,
    float innerRadius,
    float outerRadius,
    int segments,
    int colour
) implements GuiElementRenderState {

    @Override
    public void buildVertices(VertexConsumer vertices) {
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vertices.addVertexWith2DPose(pose, centreX + outerRadius * c0, centreY + outerRadius * s0).setColor(colour);
            vertices.addVertexWith2DPose(pose, centreX + innerRadius * c0, centreY + innerRadius * s0).setColor(colour);
            vertices.addVertexWith2DPose(pose, centreX + innerRadius * c1, centreY + innerRadius * s1).setColor(colour);
            vertices.addVertexWith2DPose(pose, centreX + outerRadius * c1, centreY + outerRadius * s1).setColor(colour);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return CivModernPipelines.GUI_QUADS;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle bounds() {
        int size = (int) Math.ceil(outerRadius * 2) + 2;
        return new ScreenRectangle((int) Math.floor(centreX - outerRadius) - 1, (int) Math.floor(centreY - outerRadius) - 1, size, size)
            .transformMaxBounds(pose);
    }
}

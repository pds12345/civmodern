package sh.okx.civmodern.common.map.nodes;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;
import sh.okx.civmodern.common.rendering.CivModernPipelines;

import java.util.Arrays;

/**
 * Every rectangle the node overlay draws in a frame, submitted as one GUI element.
 *
 * <p>A frame of territory is thousands of small fills — the chunk-grid dashes above all — and
 * {@code guiGraphics.fill} costs a whole render-state element apiece. That per-element overhead,
 * not the pixels, is what made dragging the map stutter at the zoom levels that draw the grid.
 * One element with all the quads is one buffer build and one draw.
 *
 * <p>Quads are drawn in the order they were added, so translucent ink layers within the overlay
 * exactly as the individual fills did.
 */
final class NodeOverlayQuadBatch implements GuiElementRenderState {

    private final Matrix3x2f pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;

    /** x0, y0, x1, y1, colour per quad. */
    private int[] quads = new int[5 * 512];
    private int size;

    NodeOverlayQuadBatch(Matrix3x2f pose, ScreenRectangle scissor, ScreenRectangle bounds) {
        this.pose = pose;
        this.scissor = scissor;
        this.bounds = bounds;
    }

    void add(int x0, int y0, int x1, int y1, int colour) {
        if (colour == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        if (size == quads.length) {
            quads = Arrays.copyOf(quads, quads.length * 2);
        }
        quads[size] = x0;
        quads[size + 1] = y0;
        quads[size + 2] = x1;
        quads[size + 3] = y1;
        quads[size + 4] = colour;
        size += 5;
    }

    boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < size; i += 5) {
            float x0 = quads[i];
            float y0 = quads[i + 1];
            float x1 = quads[i + 2];
            float y1 = quads[i + 3];
            int colour = quads[i + 4];
            consumer.addVertexWith2DPose(pose, x0, y0).setColor(colour);
            consumer.addVertexWith2DPose(pose, x0, y1).setColor(colour);
            consumer.addVertexWith2DPose(pose, x1, y1).setColor(colour);
            consumer.addVertexWith2DPose(pose, x1, y0).setColor(colour);
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
    public ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}

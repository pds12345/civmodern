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

    /**
     * Float coordinates, deliberately: the map texture pans at sub-pixel precision, and quads
     * rounded to whole pixels re-snap against it by a pixel as the map drags — the wobble. At
     * float precision an overlay edge sits at exactly the same screen position as the map texel
     * boundary it marks, so both rasterize identically and the layer rides the map.
     */
    private float[] coords = new float[4 * 512];
    private int[] colours = new int[512];
    private int count;

    NodeOverlayQuadBatch(Matrix3x2f pose, ScreenRectangle scissor, ScreenRectangle bounds) {
        this.pose = pose;
        this.scissor = scissor;
        this.bounds = bounds;
    }

    void add(float x0, float y0, float x1, float y1, int colour) {
        if (colour == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        if (count == colours.length) {
            coords = Arrays.copyOf(coords, coords.length * 2);
            colours = Arrays.copyOf(colours, colours.length * 2);
        }
        int c = count * 4;
        coords[c] = x0;
        coords[c + 1] = y0;
        coords[c + 2] = x1;
        coords[c + 3] = y1;
        colours[count] = colour;
        count++;
    }

    boolean isEmpty() {
        return count == 0;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < count; i++) {
            int c = i * 4;
            float x0 = coords[c];
            float y0 = coords[c + 1];
            float x1 = coords[c + 2];
            float y1 = coords[c + 3];
            int colour = colours[i];
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

package sh.okx.civmodern.common.map.snitches;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3x2f;
import sh.okx.civmodern.common.rendering.CivModernRenderTypes;

/**
 * Draws the map screen's snitches into an offscreen picture and fades that picture as a whole.
 *
 * <p>Snitches overlap freely - a player with hundreds has clusters many icons deep - so fading
 * each icon as it is drawn would leave a dense cluster as opaque as ever, and the map beneath it
 * just as hidden. Drawing them all at full strength into one picture and then scaling the
 * finished picture's alpha gives one uniform layer, no matter how many icons share a pixel.
 *
 * <p>Registered as a special GUI element alongside {@code BlitRenderer}; each renderer class
 * owns one offscreen texture, which is why this is a class of its own rather than another
 * {@code BlitRenderState}. The picture is composited by the GUI with premultiplied alpha, which
 * the fade preserves.
 */
public final class SnitchLayerRenderer extends PictureInPictureRenderer<SnitchLayerRenderState> {

    public SnitchLayerRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<SnitchLayerRenderState> getRenderStateClass() {
        return SnitchLayerRenderState.class;
    }

    @Override
    protected void renderToTexture(SnitchLayerRenderState state, PoseStack poseStack) {
        // The picture is the screen at physical resolution, drawn under our own pose rather
        // than the one handed in (which is set up for the 3D previews vanilla draws this way):
        // GUI pixels scaled up to physical ones, then the map's pose.
        Matrix3x2f picturePose = new Matrix3x2f().scale(state.guiScale()).mul(state.guiPose());
        SnitchRenderer.buildLayer(this.bufferSource, picturePose, state.snitches(), state.now(),
            state.originX(), state.originZ(), state.blocksPerPixel(), state.iconScale(), state.screenWidth(), state.screenHeight());
        this.bufferSource.endBatch();

        float opacity = Math.min(1f, Math.max(0f, state.opacity()));
        if (opacity < 1f) {
            // Over the finished layer: one quad covering the picture, whose alpha scales every
            // pixel already there (see CivModernPipelines.LAYER_FADE).
            float width = state.screenWidth() * state.guiScale();
            float height = state.screenHeight() * state.guiScale();
            int colour = Math.round(opacity * 255f) << 24 | 0xFFFFFF;
            VertexConsumer fade = this.bufferSource.getBuffer(CivModernRenderTypes.LAYER_FADE);
            fade.addVertex(0, 0, 0).setColor(colour);
            fade.addVertex(0, height, 0).setColor(colour);
            fade.addVertex(width, height, 0).setColor(colour);
            fade.addVertex(width, 0, 0).setColor(colour);
            this.bufferSource.endBatch();
        }
    }

    @Override
    protected String getTextureLabel() {
        return "civmodern-snitches";
    }
}

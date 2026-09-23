package sh.okx.civmodern.common.map.screen;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.map.MapCache;
import sh.okx.civmodern.common.map.Minimap;
import sh.okx.civmodern.common.map.snitches.Snitch;
import sh.okx.civmodern.common.map.snitches.SnitchLayerRenderState;
import sh.okx.civmodern.common.map.snitches.SnitchType;

import java.time.Instant;
import java.util.List;

/** The size preview for snitch icons. See {@link IconSizePreviewScreen}. */
public class SnitchSizePreviewScreen extends IconSizePreviewScreen {

    private final Snitch previewSnitch;

    public SnitchSizePreviewScreen(Screen parent, CivMapConfig config, MapCache mapCache, Minimap minimap, KeyMapping minimapZoomKey) {
        super(parent, config, mapCache, minimap, minimapZoomKey, "snitchpreview",
            config.getSnitchBaseZoom(), config.getSnitchZoomLogBase(),
            config.getMinimapSnitchBaseZoom(), config.getMinimapSnitchZoomLogBase());
        // A freshly placed jukebox: full life ahead, so the outline is plain black.
        Instant now = Instant.now();
        this.previewSnitch = new Snitch(blockX, blockY, blockZ,
            Component.translatable("civmodern.screen.snitchpreview.snitchname").getString(), "",
            SnitchType.JUKEBOX, now.plus(SnitchType.JUKEBOX.lifespan()), now);
    }

    /** Through the same offscreen layer as the map screen, drawn solid whatever the layer mode. */
    @Override
    protected void renderMapIcon(GuiGraphics guiGraphics, float x, float y, float iconScale) {
        Window window = Minecraft.getInstance().getWindow();
        guiGraphics.guiRenderState.submitPicturesInPictureState(new SnitchLayerRenderState(
            new Matrix3x2f(guiGraphics.pose()), window.getGuiScale(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(),
            List.of(new SnitchLayerRenderState.Placement(previewSnitch, x, y)), iconScale, Instant.now(), 1f,
            guiGraphics.scissorStack.peek()));
    }

    @Override
    protected void renderMinimap(GuiGraphics guiGraphics, float delta, float baseZoom, float zoomLogBase) {
        minimap.renderSnitchPreview(guiGraphics, delta, previewSnitch, baseZoom, zoomLogBase);
    }

    @Override
    protected void apply(float mapBaseZoom, float mapZoomLogBase, float minimapBaseZoom, float minimapZoomLogBase) {
        config.setSnitchBaseZoom(mapBaseZoom);
        config.setSnitchZoomLogBase(mapZoomLogBase);
        config.setMinimapSnitchBaseZoom(minimapBaseZoom);
        config.setMinimapSnitchZoomLogBase(minimapZoomLogBase);
    }
}

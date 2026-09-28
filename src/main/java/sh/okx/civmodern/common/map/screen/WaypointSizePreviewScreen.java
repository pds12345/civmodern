package sh.okx.civmodern.common.map.screen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.map.MapCache;
import sh.okx.civmodern.common.map.Minimap;
import sh.okx.civmodern.common.map.waypoints.Waypoint;

/** The size preview for waypoint icons and their labels. See {@link IconSizePreviewScreen}. */
public class WaypointSizePreviewScreen extends IconSizePreviewScreen {

    private static final int PREVIEW_COLOUR = 0xFF0000;

    private final Waypoint previewWaypoint;

    public WaypointSizePreviewScreen(Screen parent, CivMapConfig config, MapCache mapCache, Minimap minimap, KeyMapping minimapZoomKey) {
        super(parent, config, mapCache, minimap, minimapZoomKey, "waypointpreview",
            config.getWaypointBaseZoom(), config.getWaypointZoomLogBase(),
            config.getMinimapIconBaseZoom(), config.getMinimapIconZoomLogBase());
        this.previewWaypoint = new Waypoint(Component.translatable("civmodern.screen.waypointpreview.waypointname").getString(),
            blockX, blockY, blockZ, "waypoint", PREVIEW_COLOUR);
    }

    /** Icon and label drawn exactly as MapScreen draws a real waypoint. */
    @Override
    protected void renderMapIcon(GuiGraphics guiGraphics, float x, float y, float iconScale) {
        Matrix3x2fStack matrices = guiGraphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(iconScale, iconScale);
        previewWaypoint.render2D(guiGraphics);
        matrices.translate(0, -16);
        Component label = Component.literal(previewWaypoint.name());
        guiGraphics.drawString(this.font, label, -this.font.width(label) / 2, 0, -1, false);
        guiGraphics.fill(-this.font.width(label) / 2, -1, this.font.width(label) / 2, 9, 1056964608);
        matrices.popMatrix();
    }

    @Override
    protected void renderMinimap(GuiGraphics guiGraphics, float delta, float baseZoom, float zoomLogBase) {
        minimap.renderWaypointPreview(guiGraphics, delta, previewWaypoint, baseZoom, zoomLogBase);
    }

    @Override
    protected void apply(float mapBaseZoom, float mapZoomLogBase, float minimapBaseZoom, float minimapZoomLogBase) {
        config.setWaypointBaseZoom(mapBaseZoom);
        config.setWaypointZoomLogBase(mapZoomLogBase);
        config.setMinimapIconBaseZoom(minimapBaseZoom);
        config.setMinimapIconZoomLogBase(minimapZoomLogBase);
    }
}

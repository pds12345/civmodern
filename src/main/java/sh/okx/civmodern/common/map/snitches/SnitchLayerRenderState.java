package sh.okx.civmodern.common.map.snitches;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.time.Instant;
import java.util.List;

/**
 * One frame of the map screen's snitch layer, handed to {@link SnitchLayerRenderer} to draw
 * offscreen. Covers the whole screen: {@code x0..x1} and {@code y0..y1} are the screen in GUI
 * pixels, and the picture is composited back at identity, so the blit pose is left at its
 * default and the map's own pose is carried as {@link #guiPose()} instead.
 *
 * @param guiPose    the GUI pose the icons are placed under - the map screen's, at the point it
 *                   draws its icons
 * @param guiScale   the window's GUI scale, to render the picture at physical resolution
 * @param originX    world block X at the left edge of the screen
 * @param originZ    world block Z at the top edge of the screen
 * @param blocksPerPixel world blocks per GUI pixel. Not named {@code scale}: the interface's
 *                   {@code scale()} is the compositor's magnification, overridden below to 1, and
 *                   a record component of that name would be shadowed by it.
 * @param iconScale  the map's icon scaling at its current zoom
 * @param opacity    how strongly the finished layer is composited, 0-1; 1 leaves it as drawn
 */
public record SnitchLayerRenderState(
    Matrix3x2f guiPose,
    int guiScale,
    int screenWidth,
    int screenHeight,
    double originX,
    double originZ,
    float blocksPerPixel,
    float iconScale,
    List<Snitch> snitches,
    Instant now,
    float opacity,
    @Nullable ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState {

    public SnitchLayerRenderState(Matrix3x2f guiPose, int guiScale, int screenWidth, int screenHeight,
                                  double originX, double originZ, float blocksPerPixel, float iconScale,
                                  List<Snitch> snitches, Instant now, float opacity, @Nullable ScreenRectangle scissorArea) {
        this(guiPose, guiScale, screenWidth, screenHeight, originX, originZ, blocksPerPixel, iconScale, snitches, now, opacity,
            scissorArea, PictureInPictureRenderState.getBounds(0, 0, screenWidth, screenHeight, scissorArea));
    }

    @Override
    public int x0() {
        return 0;
    }

    @Override
    public int x1() {
        return screenWidth;
    }

    @Override
    public int y0() {
        return 0;
    }

    @Override
    public int y1() {
        return screenHeight;
    }

    @Override
    public float scale() {
        return 1;
    }
}

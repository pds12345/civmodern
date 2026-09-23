package sh.okx.civmodern.common.map.snitches;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.time.Instant;
import java.util.List;

/**
 * One frame of a snitch layer, handed to {@link SnitchLayerRenderer} to draw offscreen. The
 * picture covers the GUI rectangle at ({@code x}, {@code y}) of {@code width} by {@code height}:
 * the whole screen for the map, the map area for the minimap. It is composited back at identity,
 * so the blit pose is left at its default and the caller's own pose is carried as
 * {@link #guiPose()} instead.
 *
 * @param guiPose    the GUI pose the placements are under - the caller's, at the point it draws
 *                   its icons
 * @param guiScale   the window's GUI scale, to render the picture at physical resolution
 * @param placements every snitch to draw, already positioned and culled by the caller
 * @param iconScale  the caller's icon scaling at its current zoom
 * @param opacity    how strongly the finished layer is composited, 0-1; 1 leaves it as drawn
 */
public record SnitchLayerRenderState(
    Matrix3x2f guiPose,
    int guiScale,
    int x,
    int y,
    int width,
    int height,
    List<Placement> placements,
    float iconScale,
    Instant now,
    float opacity,
    @Nullable ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState {

    /** A snitch and where its centre goes, in GUI pixels under {@link #guiPose()}. */
    public record Placement(Snitch snitch, float x, float y) {
    }

    public SnitchLayerRenderState(Matrix3x2f guiPose, int guiScale, int x, int y, int width, int height,
                                  List<Placement> placements, float iconScale, Instant now, float opacity,
                                  @Nullable ScreenRectangle scissorArea) {
        this(guiPose, guiScale, x, y, width, height, placements, iconScale, now, opacity,
            scissorArea, PictureInPictureRenderState.getBounds(x, y, x + width, y + height, scissorArea));
    }

    @Override
    public int x0() {
        return x;
    }

    @Override
    public int x1() {
        return x + width;
    }

    @Override
    public int y0() {
        return y;
    }

    @Override
    public int y1() {
        return y + height;
    }

    /** The compositor's magnification of the picture, not a map scale; the picture is 1:1. */
    @Override
    public float scale() {
        return 1;
    }
}

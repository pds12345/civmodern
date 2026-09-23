package sh.okx.civmodern.common.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2d;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.ColourProvider;
import sh.okx.civmodern.common.events.PostRenderGameOverlayEvent;
import sh.okx.civmodern.common.map.mobs.MinimapMobTypes;
import sh.okx.civmodern.common.map.mobs.MobThreatCategory;
import sh.okx.civmodern.common.map.nodes.NodeCache;
import sh.okx.civmodern.common.map.nodes.NodeOverlayRenderer;
import sh.okx.civmodern.common.map.screen.WaypointSizePreviewScreen;
import sh.okx.civmodern.common.map.snitches.Snitch;
import sh.okx.civmodern.common.map.snitches.SnitchRenderer;
import sh.okx.civmodern.common.map.snitches.Snitches;
import sh.okx.civmodern.common.map.waypoints.PlayerWaypoint;
import sh.okx.civmodern.common.map.waypoints.PlayerWaypoints;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.map.waypoints.Waypoints;
import sh.okx.civmodern.common.rendering.BlitRenderState;
import sh.okx.civmodern.common.rendering.ChevronRenderState;
import sh.okx.civmodern.common.rendering.CivModernPipelines;
import sh.okx.civmodern.common.rendering.CivModernRenderTypes;
import sh.okx.civmodern.common.rendering.RingRenderState;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static sh.okx.civmodern.common.map.RegionAtlasTexture.SIZE;

public class Minimap {

    private final Waypoints waypoints;
    private final PlayerWaypoints playerWaypoints;
    private final Snitches snitches;
    private final MapCache cache;
    private final NodeCache nodes;
    private final CivMapConfig config;
    private final ColourProvider provider;

    private static final RegionAtlasTexture blank = new RegionAtlasTexture();
    /** On-screen size (before {@code iconScale}) a mob's icon is drawn at, in GUI pixels. */
    private static final float MOB_ICON_DISPLAY_SIZE = 10f;
    /** Y-levels above/below the player a mob's icon fades out over; beyond this it is invisible. */
    private static final float MOB_Y_FADE_RANGE = 20f;
    /**
     * Polygon sides for the circular minimap. A multiple of 8 so a vertex lands on each corner of
     * the square, otherwise the mask leaves a sliver of map showing at the corners.
     */
    private static final int CIRCLE_SEGMENTS = 128;
    /** Off-map waypoints are pinned to the outline at this fraction of their normal size. */
    private static final float EDGE_MARKER_SCALE = 0.5f;

    static {
        RenderSystem.queueFencedTask(blank::init);
    }


    public Minimap(Waypoints waypoints, PlayerWaypoints playerWaypoints, Snitches snitches, MapCache cache, NodeCache nodes, CivMapConfig config, ColourProvider provider) {
        this.waypoints = waypoints;
        this.playerWaypoints = playerWaypoints;
        this.snitches = snitches;
        this.cache = cache;
        this.nodes = nodes;
        this.config = config;
        this.provider = provider;
    }

    public void onRender(PostRenderGameOverlayEvent event) {
        if (!config.isMinimapEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        // The preview screen draws the minimap itself with only its own waypoint; the HUD copy
        // would otherwise show every real waypoint underneath it.
        boolean previewing = mc.screen instanceof WaypointSizePreviewScreen;
        if (previewing || mc.options.hideGui || mc.debugEntries.isOverlayVisible() || !(!mc.options.keyPlayerList.isDown() || mc.isLocalServer() && mc.player.connection.getListedOnlinePlayers().size() <= 1 && objective == null)) {
            event.guiGraphics().guiRenderState.submitPicturesInPictureState(new BlitRenderState(event.guiGraphics(), 0, 0, 0, 0, event.guiGraphics().pose(),
                ((source, stack) -> {})));
            return;
        }

        render(event.guiGraphics(), event.deltaTick(), waypoints.getWaypoints(), true,
            config.getMinimapIconBaseZoom(), config.getMinimapIconZoomLogBase());
    }

    /**
     * Draws the minimap with only {@code waypoint} on it, at its configured size and position and
     * regardless of the minimap-enabled toggle. Mobs, snitched players and node territory are left
     * off so the icon is unobstructed. Used by {@link WaypointSizePreviewScreen}, which also
     * supplies the scaling numbers so it can preview unsaved values.
     */
    public void renderPreview(GuiGraphics graphics, float delta, Waypoint waypoint, float iconBaseZoom, float iconZoomLogBase) {
        render(graphics, delta, List.of(waypoint), false, iconBaseZoom, iconZoomLogBase);
    }

    /** Where the minimap's map area (inside the 2px border) sits on screen, in GUI pixels. */
    public record Placement(int x, int y, int size) {
    }

    public Placement placement() {
        int size = config.getMinimapSize();
        int offsetX = config.getMinimapX() + 2;
        int offsetY = config.getMinimapY() + 2;
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return switch (config.getMinimapAlignment()) {
            case TOP_LEFT -> new Placement(offsetX, offsetY, size);
            case TOP_RIGHT -> new Placement(width - offsetX - size, offsetY, size);
            case BOTTOM_RIGHT -> new Placement(width - offsetX - size, height - offsetY - size, size);
            default -> new Placement(offsetX, height - offsetY - size, size);
        };
    }

    /**
     * @param live whether this is the real HUD minimap: honours the waypoint toggle and draws
     *             the chevron, mobs, snitched players and node territory. False for the preview.
     */
    private void render(GuiGraphics graphics, float delta, List<Waypoint> waypointList, boolean live, float iconBaseZoom, float iconZoomLogBase) {
        Minecraft mc = Minecraft.getInstance();
        float zoom = config.getMinimapZoom();

        float size = config.getMinimapSize();

        Matrix3x2fStack matrices = graphics.pose();

        matrices.pushMatrix();

        Placement placement = placement();
        int translateX = placement.x();
        int translateY = placement.y();

        matrices.translate(translateX, translateY);

        // The camera entity, not mc.player: while spectating, only the camera moves client-side.
        Entity player = MapFocus.entity();
        float px = (float) Mth.lerp(delta, player.xo, player.getX());
        float pz = (float) Mth.lerp(delta, player.zo, player.getZ());
        int playerBX = player.getBlockX();
        int playerBY = player.getBlockY();
        int playerBZ = player.getBlockZ();

        // Rotating mode turns the map so the player's facing direction points up. On a north-up map
        // their facing is yaw degrees clockwise from down, so the map needs 180 - yaw to bring it
        // up. The tiles then have to cover the square at any angle: draw a larger square, side
        // size * sqrt(2), centred on the same point. What falls outside the outline is clipped by
        // the picture's scissor (square) or the mask (circle), so the outline itself never moves.
        boolean rotating = config.isMinimapRotating();
        float yaw = player.getViewYRot(delta) % 360f;
        float rotation = rotating ? (float) Math.toRadians(180f - yaw) : 0f;
        float cos = (float) Math.cos(rotation);
        float sin = (float) Math.sin(rotation);
        int drawOffset = rotating ? (int) Math.ceil(size * (Math.sqrt(2) - 1) / 2) : 0;
        float drawSize = size + 2 * drawOffset;
        int drawX = translateX - drawOffset;
        int drawY = translateY - drawOffset;
        RegionAtlasTexture.Rotation tileRotation = rotating ? new RegionAtlasTexture.Rotation(rotation, drawSize / 2, drawSize / 2) : null;

        float x = px - (drawSize * zoom) / 2;
        float y = pz - (drawSize * zoom) / 2;

        float drawnX = 0;
        float drawnY = 0;
        List<BlitRenderState.Renderer> renderers = new ArrayList<>();
        for (float screenX = 0; screenX < (drawSize * zoom) + SIZE; screenX += SIZE) {
            float tmp = 0;
            for (float screenY = 0; screenY < (drawSize * zoom) + SIZE; screenY += SIZE) {
                float realX = x + screenX;
                float realY = y + screenY;

                float renderX = realX - floatMod(realX, SIZE);
                float renderY = realY - floatMod(realY, SIZE);

                RegionKey key = new RegionKey(Math.floorDiv((int) renderX, SIZE), Math.floorDiv((int) renderY, SIZE));
                RegionAtlasTexture texture = cache.getTexture(key);
                float xOff = (renderX - x) + 4096;
                float yOff = (renderY - y) + 4096;

                texture = texture == null ? blank : texture;
                renderers.add(texture.drawLinear(graphics, drawnX, drawnY, zoom, screenX == 0 ? SIZE - xOff : 0, screenY == 0 ? SIZE - yOff : 0, SIZE, SIZE, Math.max(0, drawSize * zoom - drawnX), Math.max(0, drawSize * zoom - drawnY), drawX, drawY, tileRotation));
                drawnY += screenY == 0 ? yOff : SIZE;
                tmp += xOff;
            }
            drawnY = 0;
            drawnX += screenX == 0 ? tmp / 2 : SIZE;
        }

        boolean circular = config.isMinimapCircular();
        // Node territory over the tiles, under the waypoints and chevron — as on the map screen.
        OverlayMode nodeMode = config.getMinimapNodeOverlayMode();
        boolean drawNodes = live && nodeMode.isVisible() && nodes != null
            && AbstractCivModernMod.getInstance().getNodeApi().isAvailable();

        if (circular) {
            if (drawNodes) {
                // Into the picture rather than the GUI, so the corner mask clips it too: a GUI
                // element can only be scissored to a rectangle. The picture is in physical pixels,
                // hence the extra GUI-scale factor on the pose.
                int guiScale = mc.getWindow().getGuiScale();
                // Same enlarged, rotated viewport as the tiles, turned about the map's centre.
                Matrix3x2f picturePose = new Matrix3x2f().scale(guiScale).mul(matrices)
                    .translate(size / 2, size / 2).rotate(rotation).translate(-drawSize / 2, -drawSize / 2);
                GuiElementRenderState batch = NodeOverlayRenderer.build(picturePose, null, null,
                    nodes, config, nodeMode, x, y, (int) drawSize, (int) drawSize, zoom);
                if (batch != null) {
                    renderers.add((source, stack) -> {
                        source.endBatch(); // tiles first: the buffer source flushes in no fixed order
                        batch.buildVertices(source.getBuffer(CivModernRenderTypes.PICTURE_QUADS));
                    });
                }
            }
            renderers.add(circleMask(size, translateX, translateY));
        }

        matrices.translate(-2, -2);
        int borderColour = 0xff000000 | provider.getBorderColour();
        if (circular) {
            graphics.guiRenderState.submitGuiElement(new RingRenderState(new Matrix3x2f(matrices), graphics.scissorStack.peek(),
                2 + size / 2f, 2 + size / 2f, size / 2f, size / 2f + 2, CIRCLE_SEGMENTS, borderColour));
        } else {
            graphics.fill(0, 0, (int) (size + 4), (int) (size + 4), borderColour);
        }
        // The composited picture is clipped to the outline, so rotated tiles never show outside it.
        ScreenRectangle mapArea = new ScreenRectangle(translateX, translateY, (int) size, (int) size);
        ScreenRectangle outerScissor = graphics.scissorStack.peek();
        ScreenRectangle pictureScissor = outerScissor != null ? outerScissor.intersection(mapArea) : mapArea;
        graphics.guiRenderState.submitPicturesInPictureState(new BlitRenderState(graphics, 0, 0, translateX + config.getMinimapSize(), translateY + config.getMinimapSize(), matrices,
            pictureScissor, ((source, stack) -> renderers.forEach(r -> r.render(source, stack)))));

        // Back onto the map area: the pose sat at the border's corner, 2px out, for the border and
        // the picture. Everything below is placed in map pixels (the tiles and mask were drawn at
        // absolute screen coordinates), so without this the icons and chevron all sit 2px up and
        // left of the terrain, and edge markers straddle the outline unevenly.
        matrices.translate(2, 2);

        if (drawNodes && !circular) {
            matrices.pushMatrix();
            // Same enlarged, rotated viewport as the tiles, turned about the map's centre and
            // scissored to the square.
            matrices.translate(size / 2, size / 2);
            matrices.rotate(rotation);
            matrices.translate(-drawSize / 2, -drawSize / 2);
            NodeOverlayRenderer.render(graphics, nodes, config, nodeMode, x, y, (int) drawSize, (int) drawSize, zoom, mapArea);
            matrices.popMatrix();
        }

        if (config.isShowMinimapCoords()) {
            graphics.drawCenteredString(mc.font, "%d, %s, %d".formatted(playerBX, playerBY, playerBZ), (int) (size / 2), (int) size + 4, -1);
        }

        // Same formula as the map screen, but against the minimap's own base zoom/log base since
        // its zoom (blocks per pixel) ranges over different values.
        float iconScale = WaypointScaling.scale(zoom, iconBaseZoom, iconZoomLogBase);

        if (live && config.isMinimapMobsEnabled()) {
            matrices.pushMatrix();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity) || entity instanceof Player || !entity.isAlive()) {
                    continue;
                }
                EntityType<?> type = entity.getType();
                MobThreatCategory category = MinimapMobTypes.categoryOf(type);
                if (category == null || !config.isMinimapMobVisible(type)) {
                    continue;
                }

                float dy = (float) Math.abs(entity.getY() - player.getY());
                float alpha = 1f - Mth.clamp(dy / MOB_Y_FADE_RANGE, 0f, 1f);
                if (alpha <= 0f) {
                    continue;
                }

                float ex = (float) Mth.lerp(delta, entity.xo, entity.getX());
                float ez = (float) Mth.lerp(delta, entity.zo, entity.getZ());
                Vector2d at = onMap((ex - x) / zoom, (ez - y) / zoom, drawOffset, size, cos, sin);
                double tx = at.x;
                double ty = at.y;
                if (outside(tx, ty, size, circular)) {
                    continue;
                }

                matrices.pushMatrix();
                matrices.translate((float) tx, (float) ty);
                float displayScale = iconScale * (MOB_ICON_DISPLAY_SIZE / MinimapMobTypes.ICON_SIZE);
                matrices.scale(displayScale, displayScale);
                // Full colour - only alpha is adjusted, so the mob's actual icon art shows through.
                int tint = ((int) (alpha * 0xFF) << 24) | 0xFFFFFF;
                int half = MinimapMobTypes.ICON_SIZE / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, MinimapMobTypes.iconLocation(type), -half, -half, 0, 0,
                    MinimapMobTypes.ICON_SIZE, MinimapMobTypes.ICON_SIZE, MinimapMobTypes.ICON_SIZE, MinimapMobTypes.ICON_SIZE, tint);
                matrices.popMatrix();
            }
            matrices.popMatrix();
        }

        if (live && config.isPlayerWaypointsEnabled()) {
            // TODO fix the player rendering above the chevron
            // todo fading
            for (PlayerWaypoint waypoint : this.playerWaypoints.getWaypoints()) {
                // TODO cycle between players on the same snitch
                double wx = waypoint.x() + 0.5;
                double wz = waypoint.z() + 0.5;
                Vector2d at = onMap((wx - x) / zoom, (wz - y) / zoom, drawOffset, size, cos, sin);
                double tx = at.x;
                double ty = at.y;
                if (outside(tx, ty, size, circular)) {
                    continue;
                }
                matrices.pushMatrix();
                matrices.translate((float) tx, (float) ty);
                matrices.scale(iconScale, iconScale);

                boolean old = waypoint.timestamp().until(Instant.now(), ChronoUnit.MINUTES) >= 10;
                int colour = (old ? 0x77 : 0xFF) << 24 | 0xFFFFFF;
                waypoint.render(graphics, colour);
                matrices.popMatrix();
            }
        }

        // Own snitches, placed and culled like the snitched players above; no edge markers.
        if (live && config.isSnitchesEnabled() && snitches != null) {
            Instant now = Instant.now();
            for (Snitch snitch : snitches.getSnitches()) {
                Vector2d at = onMap((snitch.x() + 0.5 - x) / zoom, (snitch.z() + 0.5 - y) / zoom, drawOffset, size, cos, sin);
                if (outside(at.x, at.y, size, circular)) {
                    continue;
                }
                matrices.pushMatrix();
                matrices.translate((float) at.x, (float) at.y);
                matrices.scale(iconScale, iconScale);
                SnitchRenderer.render(graphics, snitch, now);
                matrices.popMatrix();
            }
        }

        if (!live || config.isWaypointRenderingEnabled()) {
            Map<String, List<Waypoint>> waypointByIcon = new HashMap<>();
            for (Waypoint waypoint : waypointList) {
                if (!waypoint.visible()) {
                    continue;
                }
                waypointByIcon.computeIfAbsent(waypoint.icon(), k -> new ArrayList<>()).add(waypoint);
            }
            matrices.pushMatrix();
            for (List<Waypoint> waypointGroup : waypointByIcon.values()) {
                for (Waypoint waypoint : waypointGroup) {
                    double wx = waypoint.x() + 0.5;
                    double wz = waypoint.z() + 0.5;
                    Vector2d at = onMap((wx - x) / zoom, (wz - y) / zoom, drawOffset, size, cos, sin);
                    double tx = at.x;
                    double ty = at.y;
                    float scale = iconScale;
                    if (outside(tx, ty, size, circular)) {
                        if (!config.isMinimapEdgeWaypoints()) {
                            continue;
                        }
                        // Off the map: a half-size marker centred on the outline, where the tiles
                        // meet the border, on the line from the centre towards the waypoint.
                        scale *= EDGE_MARKER_SCALE;
                        Vector2d edge = onEdge(tx, ty, size, circular);
                        tx = edge.x;
                        ty = edge.y;
                    }
                    matrices.pushMatrix();
                    matrices.translate((float) tx, (float) ty);
                    matrices.scale(scale, scale);

                    waypoint.render2D(graphics);
                    matrices.popMatrix();
                }
            }
            matrices.popMatrix();
        }

        // The preview's waypoint sits exactly where the chevron would, so leave the chevron out there.
        if (live) {
            matrices.pushMatrix();
            matrices.translate(size / 2, size / 2);
            // On a rotating map the player always faces up, so the chevron stays pointing up.
            matrices.rotate(rotating ? (float) Math.PI : (float) Math.toRadians(yaw));
            matrices.scale(4, 4);
            int chevronColour = provider.getChevronColour() | 0xFF000000;
            matrices.translate(0, 0.75f);
            graphics.guiRenderState.submitGuiElement(new ChevronRenderState(
                CivModernPipelines.GUI_TRIANGLE_STRIP_BLEND,
                new Matrix3x2f(graphics.pose()),
                graphics.scissorStack.peek(),
                chevronColour));
            matrices.popMatrix();
        }

        // On a rotating map north is wherever the player is not facing, so label the outline.
        // A fixed map is always north-up and needs no labels.
        if (rotating) {
            drawCardinals(graphics, mc.font, size, circular, cos, sin);
        }

        matrices.popMatrix();
    }

    /** World directions of the cardinal labels, in map pixels on a north-up map: north is -z. */
    private static final String[] CARDINAL_LABELS = {"N", "E", "S", "W"};
    private static final int[] CARDINAL_DX = {0, 1, 0, -1};
    private static final int[] CARDINAL_DY = {-1, 0, 1, 0};

    /**
     * Draws N/E/S/W centred on the outline, each on the line from the map's centre in that
     * direction after the map's rotation, so they turn with the terrain while the letters stay
     * upright. A translucent backing keeps them legible over the border and any terrain.
     */
    private static void drawCardinals(GuiGraphics graphics, Font font, float size, boolean circular, float cos, float sin) {
        double r = size / 2.0;
        for (int i = 0; i < CARDINAL_LABELS.length; i++) {
            String label = CARDINAL_LABELS[i];
            int dx = CARDINAL_DX[i];
            int dy = CARDINAL_DY[i];
            // Same rotation onMap applies to icon positions.
            double rx = dx * cos - dy * sin;
            double ry = dx * sin + dy * cos;
            Vector2d at = onEdge(r + rx, r + ry, size, circular);
            // Drawn at the origin of a pose moved by the exact position rather than at rounded
            // coordinates, so the label glides along the outline instead of stepping pixel by pixel.
            Matrix3x2fStack matrices = graphics.pose();
            matrices.pushMatrix();
            matrices.translate((float) at.x, (float) at.y);
            // font.width counts the glyph plus its 1px spacing; centre on the glyph itself.
            int width = font.width(label) - 1;
            int left = -width / 2 - 1;
            // Capitals are 7 rows tall; centre those on the outline, with a 1px margin all round.
            int top = -3;
            graphics.fill(left - 1, top - 1, left + width + 2, top + 8, 0xA0000000);
            graphics.drawString(font, label, left + 1, top, i == 0 ? 0xFFFF5555 : 0xFFFFFFFF, false);
            matrices.popMatrix();
        }
    }

    private float floatMod(float x, float y) {
        // x mod y behaving the same way as Math.floorMod but with floats
        return (x - (float) Math.floor(x / y) * y);
    }

    /**
     * Where a point {@code tx}, {@code ty} pixels from the tile origin lands on the minimap, in
     * pixels from the outline's top-left, after the map's rotation about its centre. Icons stay
     * upright; only their positions turn.
     */
    private static Vector2d onMap(double tx, double ty, int drawOffset, float size, float cos, float sin) {
        double r = size / 2.0;
        double dx = tx - drawOffset - r;
        double dy = ty - drawOffset - r;
        return new Vector2d(r + dx * cos - dy * sin, r + dx * sin + dy * cos);
    }

    /**
     * Where the line from the map's centre towards ({@code tx}, {@code ty}) meets the outline. On a
     * square that is the ray's exit through whichever side it hits; on a circle, the circumference.
     */
    private static Vector2d onEdge(double tx, double ty, float size, boolean circular) {
        double r = size / 2.0;
        double dx = tx - r;
        double dy = ty - r;
        double t = circular
            ? r / Math.sqrt(dx * dx + dy * dy)
            : r / Math.max(Math.abs(dx), Math.abs(dy));
        return new Vector2d(r + dx * t, r + dy * t);
    }

    /** Whether an icon centred at ({@code tx}, {@code ty}) in map pixels falls off the minimap. */
    private static boolean outside(double tx, double ty, float size, boolean circular) {
        if (!circular) {
            return tx < 0 || ty < 0 || tx > size || ty > size;
        }
        double r = size / 2.0;
        double dx = tx - r;
        double dy = ty - r;
        return dx * dx + dy * dy > r * r;
    }

    /**
     * Clears everything outside the inscribed circle of the {@code size} square to transparent:
     * one quad per segment between the circle and the square's edge along the same rays. Drawn
     * last, into the picture, with the same transform the tiles use. The quads are opaque white;
     * the pipeline's blend function is what zeroes the pixels (see
     * {@link CivModernPipelines#MINIMAP_MASK}).
     */
    private static BlitRenderState.Renderer circleMask(float size, int translateX, int translateY) {
        return (source, stack) -> {
            // Everything drawn so far has to be on the texture before it is overwritten.
            source.endBatch();
            VertexConsumer buffer = source.getBuffer(CivModernRenderTypes.MINIMAP_MASK);
            stack.pushPose();
            stack.setIdentity();
            int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            stack.scale(guiScale, guiScale, 1);
            stack.translate(translateX, translateY, 0);
            float r = size / 2f;
            for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
                double a0 = 2 * Math.PI * i / CIRCLE_SEGMENTS;
                double a1 = 2 * Math.PI * (i + 1) / CIRCLE_SEGMENTS;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                // Where the same ray meets the square's edge.
                float e0 = r / Math.max(Math.abs(c0), Math.abs(s0));
                float e1 = r / Math.max(Math.abs(c1), Math.abs(s1));
                buffer.addVertex(stack.last(), r + r * c0, r + r * s0, 0).setColor(0xFFFFFFFF);
                buffer.addVertex(stack.last(), r + r * c1, r + r * s1, 0).setColor(0xFFFFFFFF);
                buffer.addVertex(stack.last(), r + e1 * c1, r + e1 * s1, 0).setColor(0xFFFFFFFF);
                buffer.addVertex(stack.last(), r + e0 * c0, r + e0 * s0, 0).setColor(0xFFFFFFFF);
            }
            stack.popPose();
        };
    }

    public void cycleZoom() {
        float zoom = config.getMinimapZoom();
        zoom /= 2;
        if (zoom < 0.5f) {
            zoom = 16f;
        }
        config.setMinimapZoom(zoom);
    }
}

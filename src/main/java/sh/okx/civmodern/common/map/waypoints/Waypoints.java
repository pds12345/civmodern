package sh.okx.civmodern.common.map.waypoints;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.events.WorldRenderLastEvent;
import sh.okx.civmodern.common.rendering.CivModernRenderTypes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Waypoints {

    private final Int2ObjectMap<Int2ObjectMap<Int2ObjectMap<Waypoint>>> waypoints = new Int2ObjectOpenHashMap<>();
    private Waypoint target;
    private final Connection connection;

    public Waypoints(Connection connection) {
        this.connection = connection;
        // TODO waypoint on death
        load();
    }

    private void load() {
        synchronized (this.connection) {
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT name, x, y, z, icon, colour, visible, column_visible FROM waypoints");

                while (resultSet.next()) {
                    this.putInMemory(new Waypoint(
                        resultSet.getString("name"),
                        resultSet.getInt("x"),
                        resultSet.getInt("y"),
                        resultSet.getInt("z"),
                        resultSet.getString("icon"),
                        resultSet.getInt("colour"),
                        resultSet.getBoolean("visible"),
                        resultSet.getBoolean("column_visible")
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void putInMemory(Waypoint waypoint) {
        this.waypoints.computeIfAbsent(waypoint.x(), k -> new Int2ObjectOpenHashMap<>())
            .computeIfAbsent(waypoint.z(), k -> new Int2ObjectOpenHashMap<>())
            .put(waypoint.y(), waypoint);
    }

    private void removeFromMemory(Waypoint waypoint) {
        Int2ObjectMap<Int2ObjectMap<Waypoint>> wx = this.waypoints.get(waypoint.x());
        if (wx != null) {
            Int2ObjectMap<Waypoint> wz = wx.get(waypoint.z());
            if (wz != null) {
                wz.remove(waypoint.y());
            }
        }
    }

    /** Adds/updates the waypoint in memory and writes it through to the database immediately. */
    public void addWaypoint(Waypoint waypoint) {
        putInMemory(waypoint);

        synchronized (this.connection) {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO waypoints (name, x, y, z, icon, colour, visible, column_visible) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT DO UPDATE SET name = ?, icon = ?, colour = ?, visible = ?, column_visible = ?")) {
                statement.setString(1, waypoint.name());
                statement.setInt(2, waypoint.x());
                statement.setInt(3, waypoint.y());
                statement.setInt(4, waypoint.z());
                statement.setString(5, waypoint.icon());
                statement.setInt(6, waypoint.colour());
                statement.setBoolean(7, waypoint.visible());
                statement.setBoolean(8, waypoint.columnVisible());
                statement.setString(9, waypoint.name());
                statement.setString(10, waypoint.icon());
                statement.setInt(11, waypoint.colour());
                statement.setBoolean(12, waypoint.visible());
                statement.setBoolean(13, waypoint.columnVisible());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setVisible(Waypoint waypoint, boolean visible) {
        addWaypoint(new Waypoint(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.icon(), waypoint.colour(), visible, waypoint.columnVisible()));
    }

    public void setColumnVisible(Waypoint waypoint, boolean columnVisible) {
        addWaypoint(new Waypoint(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.icon(), waypoint.colour(), waypoint.visible(), columnVisible));
    }

    /** Removes the waypoint from memory and deletes it from the database immediately. */
    public void removeWaypoint(Waypoint waypoint) {
        removeFromMemory(waypoint);

        synchronized (this.connection) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM waypoints WHERE x = ? AND y = ? AND z = ?")) {
                statement.setInt(1, waypoint.x());
                statement.setInt(2, waypoint.y());
                statement.setInt(3, waypoint.z());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Waypoint getTarget() {
        return target;
    }

    public void setTarget(Waypoint target) {
        this.target = target;
    }

    public List<Waypoint> getWaypoints() {
        List<Waypoint> list = new ArrayList<>();
        for (Int2ObjectMap<Int2ObjectMap<Waypoint>> map : waypoints.values()) {
            for (Int2ObjectMap<Waypoint> i : map.values()) {
                list.addAll(i.values());
            }
        }
        if (target != null) {
            list.add(target);
        }
        return list;
    }

    public List<Waypoint> getWaypoints(int x, int y, int z, int distance) {
        List<Waypoint> nearbyWaypoints = new ArrayList<>();

        for (Waypoint waypoint : getWaypoints()) {
            int dx = Math.abs(waypoint.x() - x);
            int dz = Math.abs(waypoint.z() - z);

            if (dx * dx + dz * dz < distance * distance) {
                nearbyWaypoints.add(waypoint);
            }
        }

        return nearbyWaypoints;
    }

    public void onRender(WorldRenderLastEvent event) { // TODO separate this into its own class
        if (!AbstractCivModernMod.getInstance().getConfig().isWaypointRenderingEnabled()) {
            return;
        }
        if (Minecraft.getInstance().options.hideGui) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        int renderDistance = AbstractCivModernMod.getInstance().getConfig().getWaypointRenderDistance();
        List<Waypoint> nearbyWaypoints = getWaypoints(player.getBlockX(), player.getBlockY(), player.getBlockZ(), renderDistance);
        nearbyWaypoints.removeIf(waypoint -> !waypoint.visible());

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        PoseStack matrices = event.stack();
        Vec3 pos = camera.position();
        // Columns only pair with real, visible waypoints - not the ephemeral crosshair/preview
        // target appended below - so this runs before that append.
        renderColumns(nearbyWaypoints, matrices, event.source(), pos, player.level().getMinY(), player.level().getMaxY());

        if (getTarget() != null) {
            nearbyWaypoints.add(getTarget());
        }
        Collections.sort(nearbyWaypoints, (c, r) ->
            Integer.compare(((r.x() - player.getBlockX()) * (r.x() - player.getBlockX())) + ((r.z() - player.getBlockZ()) * (r.z() - player.getBlockZ())),
                ((c.x() - player.getBlockX()) * (c.x() - player.getBlockX())) + ((c.z() - player.getBlockZ()) * (c.z() - player.getBlockZ()))));

        for (Waypoint waypoint : nearbyWaypoints) {
            double x = waypoint.x() + 0.5 - pos.x;
            double y = waypoint.y() + 0.5 - pos.y;
            double z = waypoint.z() + 0.5 - pos.z;
            float distance = (float) Mth.length(x, y, z);
            if (distance <= 0.5f) {
                continue;
            }
            matrices.pushPose();
            float maxDistance = (Minecraft.getInstance().options.simulationDistance().get() * 16) - 2;
            float adjustedDistance = distance;
            if (distance > maxDistance) {
                x = x / distance * maxDistance;
                y = y / distance * maxDistance;
                z = z / distance * maxDistance;
                adjustedDistance = maxDistance;
            }
            matrices.translate(x, y, z);

            adjustedDistance = (adjustedDistance * 0.1f + 1) * 0.0266f;
            matrices.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
            matrices.scale(-adjustedDistance, -adjustedDistance, -adjustedDistance);

            int k = (int) (getTransparency(distance, 0.11f) * 255.0F) << 24;
            waypoint.render(CivModernRenderTypes.TEXT, event.source(), matrices.last().pose(), 8, k);
            waypoint.render(CivModernRenderTypes.TEXT2, event.source(), matrices.last().pose(), 8, k);
            matrices.popPose();
        }
        MultiBufferSource source = event.source();
        int rendered = 0;
        Collections.reverse(nearbyWaypoints);
        for (int i = 0; i < nearbyWaypoints.size() && rendered < 100; i++, rendered++) {
            Waypoint waypoint = nearbyWaypoints.get(i);
            double waypointDistance = Mth.length(waypoint.x() - player.getBlockX(), waypoint.y() - player.getBlockY(), waypoint.z() - player.getBlockZ());
            if (!isPointedAt(waypoint, waypointDistance, Minecraft.getInstance().getCameraEntity(), event.tickDelta())) {
                continue;
            }
            double x = waypoint.x() + 0.5 - pos.x;
            double y = waypoint.y() + 0.5 - pos.y;
            double z = waypoint.z() + 0.5 - pos.z;
            float distance = (float) Mth.length(x, y, z);
            if (distance <= 0.5f) {
                continue;
            }
            matrices.pushPose();
            float maxDistance = (Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * 4) - 2;
            float adjustedDistance = distance;
            if (distance > maxDistance) {
                x = x / distance * maxDistance;
                y = y / distance * maxDistance;
                z = z / distance * maxDistance;
                adjustedDistance = maxDistance;
            }
            matrices.translate(x, y, z);

            adjustedDistance = (adjustedDistance * 0.1f + 1) * 0.0266f;
            matrices.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
            matrices.scale(-adjustedDistance, -adjustedDistance, -adjustedDistance);

            Font font = Minecraft.getInstance().font;

            String str = waypoint.name() + (waypoint.name().isBlank() ? "" : " ") + "(" + (int) waypointDistance + ")";

            matrices.translate(0, -20, 0);
            Matrix4f last = matrices.last().pose();

            int k = (int) (getTransparency(distance, 0.44f) * 63.0F) << 24;
            int k2 = (int) (getTransparency(distance, 0.11f) * 255.0F) << 24;

            float bgOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25f);
            int bgColor = (int) (bgOpacity * 255.0f) << 24;
            font.drawInBatch(str, -font.width(str) / 2f, 0, 0x20FFFFFF, false, last, source, Font.DisplayMode.NORMAL, k, 0);
            font.drawInBatch(str, -font.width(str) / 2f, 0, k2 | 0xFFFFFF, false, last, source, Font.DisplayMode.SEE_THROUGH, bgColor, 0);
            matrices.popPose();
        }
    }

    // Nested-shell radii for the waypoint column - a hollow vertical box (bedrock to sky limit)
    // around each waypoint's 1m cell, sized so the outer shell alone pokes just past that cell,
    // letting it peek out beside an opaque wall the player is standing behind. All three shells
    // share one configured alpha; the nesting itself is what makes the centre read as more opaque.
    private static final float COLUMN_OUTER_HALF_WIDTH = 0.6f;
    private static final float COLUMN_MIDDLE_HALF_WIDTH = 0.4f;
    private static final float COLUMN_INNER_HALF_WIDTH = 0.25f;

    private void renderColumns(List<Waypoint> waypoints, PoseStack matrices, MultiBufferSource source, Vec3 pos, int minY, int maxY) {
        if (!AbstractCivModernMod.getInstance().getConfig().isColumnsEnabled()) {
            return;
        }
        Matrix4f pose = matrices.last().pose();
        VertexConsumer buffer = source.getBuffer(CivModernRenderTypes.COLUMN);
        float bottom = (float) (minY - pos.y);
        float top = (float) (maxY - pos.y);
        int alpha = (int) (AbstractCivModernMod.getInstance().getConfig().getColumnOpacity() / 100f * 255f);
        for (Waypoint waypoint : waypoints) {
            if (!waypoint.columnVisible()) {
                continue;
            }
            float x = (float) (waypoint.x() + 0.5 - pos.x);
            float z = (float) (waypoint.z() + 0.5 - pos.z);
            int argb = (waypoint.colour() & 0xFFFFFF) | (alpha << 24);
            renderColumnShell(buffer, pose, x, bottom, top, z, COLUMN_OUTER_HALF_WIDTH, argb);
            renderColumnShell(buffer, pose, x, bottom, top, z, COLUMN_MIDDLE_HALF_WIDTH, argb);
            renderColumnShell(buffer, pose, x, bottom, top, z, COLUMN_INNER_HALF_WIDTH, argb);
        }
    }

    /** One hollow box (four side quads, no top/bottom caps) centred at (x, z), from y=bottom to y=top. */
    private void renderColumnShell(VertexConsumer buffer, Matrix4f pose, float x, float bottom, float top, float z, float halfWidth, int argb) {
        float x0 = x - halfWidth;
        float x1 = x + halfWidth;
        float z0 = z - halfWidth;
        float z1 = z + halfWidth;
        columnQuad(buffer, pose, x0, bottom, z0, x1, bottom, z0, x1, top, z0, x0, top, z0, argb);
        columnQuad(buffer, pose, x1, bottom, z1, x0, bottom, z1, x0, top, z1, x1, top, z1, argb);
        columnQuad(buffer, pose, x0, bottom, z1, x0, bottom, z0, x0, top, z0, x0, top, z1, argb);
        columnQuad(buffer, pose, x1, bottom, z0, x1, bottom, z1, x1, top, z1, x1, top, z0, argb);
    }

    private void columnQuad(VertexConsumer buffer, Matrix4f pose,
        float x1, float y1, float z1, float x2, float y2, float z2,
        float x3, float y3, float z3, float x4, float y4, float z4, int argb) {
        buffer.addVertex(pose, x1, y1, z1).setColor(argb);
        buffer.addVertex(pose, x2, y2, z2).setColor(argb);
        buffer.addVertex(pose, x3, y3, z3).setColor(argb);
        buffer.addVertex(pose, x4, y4, z4).setColor(argb);
    }

    private float getTransparency(float distance, float clamp) {
        if (distance < 3) {
            return Mth.clamp((distance - 0.5f) / 2.5f, clamp, 1);
        } else {
            return 1;
        }
    }

    private boolean isPointedAt(Waypoint waypoint, double distance, Entity cameraEntity, float partialTicks) {
        Vec3 cameraPos = cameraEntity.getEyePosition(partialTicks);
        double degrees = 4D + Math.min(4D / distance, 4D);
        double angle = degrees * 0.0174533D;
        double size = Math.sin(angle) * distance * 2;
        Vec3 cameraPosPlusDirection = cameraEntity.getViewVector(partialTicks);
        Vec3 cameraPosPlusDirectionTimesDistance = cameraPos.add(cameraPosPlusDirection.x * distance, cameraPosPlusDirection.y * distance, cameraPosPlusDirection.z * distance);
        AABB axisalignedbb = new AABB((waypoint.x() + 0.5F) - size, (waypoint.y() + 0.5F) - size, (waypoint.z() + 0.5F) - size, (waypoint.x() + 0.5F) + size, (waypoint.y() + 0.5F) + size, (waypoint.z() + 0.5F) + size);
        Optional<Vec3> raytraceresult = axisalignedbb.clip(cameraPos, cameraPosPlusDirectionTimesDistance);
        if (axisalignedbb.contains(cameraPos))
            return distance >= 1.0D;
        return raytraceresult.isPresent();
    }
}

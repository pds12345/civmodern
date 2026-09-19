package sh.okx.civmodern.common.map.waypoints;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.time.Instant;
import java.util.function.Function;

/**
 * @param createdAt when the waypoint was first stored, or {@code null} for waypoints that predate
 *                  the timestamp columns (they are left null forever rather than backdated)
 * @param updatedAt when it was last written, or {@code null} for the same reason
 */
public record Waypoint(String name, int x, int y, int z, String icon, int colour, boolean visible, boolean columnVisible,
                       @Nullable Instant createdAt, @Nullable Instant updatedAt) {

    // Timestamps are owned by the store: Waypoints fills them in when it writes, so callers build
    // waypoints without them.
    public Waypoint(String name, int x, int y, int z, String icon, int colour, boolean visible, boolean columnVisible) {
        this(name, x, y, z, icon, colour, visible, columnVisible, null, null);
    }

    // Ephemeral/preview waypoints (targeting crosshairs, new-waypoint previews, etc.) construct
    // through this and are always visible; only persisted waypoints carry real visible/column flags.
    public Waypoint(String name, int x, int y, int z, String icon, int colour) {
        this(name, x, y, z, icon, colour, true, true);
    }

    public Waypoint withName(String name) {
        return new Waypoint(name, x, y, z, icon, colour, visible, columnVisible, createdAt, updatedAt);
    }

    public Waypoint withVisible(boolean visible) {
        return new Waypoint(name, x, y, z, icon, colour, visible, columnVisible, createdAt, updatedAt);
    }

    public Waypoint withColumnVisible(boolean columnVisible) {
        return new Waypoint(name, x, y, z, icon, colour, visible, columnVisible, createdAt, updatedAt);
    }

    public Waypoint withTimestamps(@Nullable Instant createdAt, @Nullable Instant updatedAt) {
        return new Waypoint(name, x, y, z, icon, colour, visible, columnVisible, createdAt, updatedAt);
    }

    public boolean samePosition(Waypoint other) {
        return x == other.x && y == other.y && z == other.z;
    }

    public void render(Function<Identifier, RenderType> type, MultiBufferSource source, Matrix4f pose, int f, int k) {
        int colour = this.colour | k;
        VertexConsumer buffer = source.getBuffer(type.apply(this.resourceLocation()));
        buffer.addVertex(pose, -f, f, 0).setLight(0xff).setUv(0, 1).setColor(colour);
        buffer.addVertex(pose, f, f, 0).setLight(0xff).setUv(1, 1).setColor(colour);
        buffer.addVertex(pose, f, -f, 0).setLight(0xff).setUv(1, 0).setColor(colour);
        buffer.addVertex(pose, -f, -f, 0).setLight(0xff).setUv(0, 0).setColor(colour);
    }

    public void render2D(GuiGraphics guiGraphics) {
        render2D(guiGraphics, 0xff);
    }

    public void render2D(GuiGraphics guiGraphics, int transparency) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, resourceLocation(), -8, -8, 0, 0, 16, 16, 16, 16, transparency << 24 | colour());
    }

    public Identifier resourceLocation() {
        return Identifier.fromNamespaceAndPath("civmodern", "map/" + this.icon + ".png");
    }
}

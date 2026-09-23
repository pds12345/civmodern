package sh.okx.civmodern.common.map.snitches;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import sh.okx.civmodern.common.rendering.CivModernRenderTypes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Draws a snitch centred on the current pose: its block texture, framed by the life-remaining outline. */
public final class SnitchRenderer {

    /** Icon size in GUI pixels before the map's icon scaling; a little under a waypoint's 16. */
    public static final int ICON_SIZE = 12;
    public static final int HALF = ICON_SIZE / 2;

    private SnitchRenderer() {
    }

    public static void render(GuiGraphics guiGraphics, Snitch snitch, Instant now) {
        // (x, y, u, v, destWidth, destHeight, uWidth, vHeight, texWidth, texHeight, colour): the
        // 16px block texture scaled down to the icon size.
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, snitch.type().texture(), -HALF, -HALF, 0, 0,
            ICON_SIZE, ICON_SIZE, 16, 16, 16, 16, -1);
        guiGraphics.renderOutline(-HALF - 1, -HALF - 1, ICON_SIZE + 2, ICON_SIZE + 2, snitch.outlineColour(now));
    }

    /**
     * Draws every snitch in view into an offscreen picture: the layer {@link SnitchLayerRenderer}
     * fades as a whole. The same shapes as {@link #render}, emitted as vertices because a picture
     * is drawn through buffers rather than {@code GuiGraphics}.
     *
     * @param pose      maps GUI pixels to picture pixels
     * @param originX   world block X at the left edge of the screen
     * @param originZ   world block Z at the top edge of the screen
     * @param scale     world blocks per GUI pixel
     * @param iconScale the map's icon scaling at its current zoom
     */
    public static void buildLayer(MultiBufferSource.BufferSource source, Matrix3x2f pose, List<Snitch> snitches, Instant now,
                                  double originX, double originZ, float scale, float iconScale, int screenWidth, int screenHeight) {
        // Only what is on screen: zoomed out, the list holds the whole world's snitches.
        float margin = (HALF + 1) * iconScale;
        Matrix3x2f local = new Matrix3x2f();

        // Outlines first, flushed before the block faces, so an icon always sits over a
        // neighbour's outline rather than under it; the buffer source flushes in no fixed order.
        VertexConsumer outlines = source.getBuffer(CivModernRenderTypes.PICTURE_QUADS);
        for (Snitch snitch : snitches) {
            float gx = (float) ((snitch.x() + 0.5 - originX) / scale);
            float gy = (float) ((snitch.z() + 0.5 - originZ) / scale);
            if (gx < -margin || gy < -margin || gx > screenWidth + margin || gy > screenHeight + margin) {
                continue;
            }
            pose.translate(gx, gy, local).scale(iconScale, local);
            int colour = snitch.outlineColour(now);
            // The four 1px strips renderOutline draws around the icon.
            quad(outlines, local, -HALF - 1, -HALF - 1, HALF + 1, -HALF, colour);
            quad(outlines, local, -HALF - 1, HALF, HALF + 1, HALF + 1, colour);
            quad(outlines, local, -HALF - 1, -HALF, -HALF, HALF, colour);
            quad(outlines, local, HALF, -HALF, HALF + 1, HALF, colour);
        }
        source.endBatch();

        for (SnitchType type : SnitchType.values()) {
            VertexConsumer faces = source.getBuffer(CivModernRenderTypes.SNITCH_ICON.apply(type.texture()));
            for (Snitch snitch : snitches) {
                if (snitch.type() != type) {
                    continue;
                }
                float gx = (float) ((snitch.x() + 0.5 - originX) / scale);
                float gy = (float) ((snitch.z() + 0.5 - originZ) / scale);
                if (gx < -margin || gy < -margin || gx > screenWidth + margin || gy > screenHeight + margin) {
                    continue;
                }
                pose.translate(gx, gy, local).scale(iconScale, local);
                faces.addVertexWith2DPose(local, -HALF, -HALF).setUv(0, 0).setColor(-1);
                faces.addVertexWith2DPose(local, -HALF, HALF).setUv(0, 1).setColor(-1);
                faces.addVertexWith2DPose(local, HALF, HALF).setUv(1, 1).setColor(-1);
                faces.addVertexWith2DPose(local, HALF, -HALF).setUv(1, 0).setColor(-1);
            }
            source.endBatch();
        }
    }

    private static void quad(VertexConsumer consumer, Matrix3x2f pose, float x0, float y0, float x1, float y1, int colour) {
        consumer.addVertexWith2DPose(pose, x0, y0).setColor(colour);
        consumer.addVertexWith2DPose(pose, x0, y1).setColor(colour);
        consumer.addVertexWith2DPose(pose, x1, y1).setColor(colour);
        consumer.addVertexWith2DPose(pose, x1, y0).setColor(colour);
    }

    public static List<Component> tooltip(Snitch snitch, Instant now) {
        Component name = snitch.name().isEmpty()
            ? Component.translatable("civmodern.snitches.unnamed").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)
            : Component.literal(snitch.name()).withStyle(ChatFormatting.GOLD);
        Component life = snitch.isDormant(now)
            ? Component.translatable("civmodern.snitches.dormant").withStyle(ChatFormatting.RED)
            : Component.translatable("civmodern.snitches.timeleft", formatRemaining(snitch.remaining(now))).withStyle(ChatFormatting.AQUA);
        // When this row was last recorded from /jalist: local time to the minute, plus how long ago.
        // Every snitch on a page is recorded together, so this is effectively the page's refresh time.
        String seen = SEEN_FORMAT.format(snitch.seenAt().atZone(ZoneId.systemDefault()));
        // Never negative, so a clock set back between capture and now cannot print "-3m ago".
        Duration sinceSeen = now.isBefore(snitch.seenAt()) ? Duration.ZERO : Duration.between(snitch.seenAt(), now);
        Component updated = Component.translatable("civmodern.snitches.updated", seen, formatRemaining(sinceSeen))
            .withStyle(ChatFormatting.DARK_GRAY);
        return List.of(
            name,
            Component.translatable("civmodern.snitches.type." + snitch.type().name().toLowerCase()).withStyle(ChatFormatting.GRAY),
            Component.translatable("civmodern.snitches.coords", snitch.x(), snitch.y(), snitch.z()).withStyle(ChatFormatting.GRAY),
            Component.translatable("civmodern.snitches.group", snitch.group()).withStyle(ChatFormatting.YELLOW),
            life,
            updated
        );
    }

    /** e.g. {@code 2026-09-19 20:14}. */
    private static final DateTimeFormatter SEEN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** "213h 6m" above an hour, "6m 40s" below, "40s" below a minute. */
    static String formatRemaining(Duration remaining) {
        long hours = remaining.toHours();
        int minutes = remaining.toMinutesPart();
        int seconds = remaining.toSecondsPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}

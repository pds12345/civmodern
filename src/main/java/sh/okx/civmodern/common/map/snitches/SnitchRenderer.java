package sh.okx.civmodern.common.map.snitches;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

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

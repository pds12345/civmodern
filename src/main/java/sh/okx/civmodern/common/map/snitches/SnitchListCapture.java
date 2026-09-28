package sh.okx.civmodern.common.map.snitches;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads JukeAlert's {@code /jalist} GUI while it is open. The server sends every slot's item with
 * its name and lore attached, so nothing needs hovering; only paging is manual, and each page is
 * captured as it appears. Format as observed on the server:
 * <pre>
 *   title: "Your snitches"
 *   item:  minecraft:note_block or minecraft:jukebox, display name = snitch name (empty if none)
 *   lore:  "Location: world 1186, 63, -2184"
 *          "Group: FortunaBorder-SN"
 *          "Will go dormant in 213 h 6m 40s"   (zero-valued units are omitted)
 *          "Right click to send waypoint"
 * </pre>
 * Only "world" locations are kept, and only while the player is in the overworld, since the
 * store is per dimension.
 */
public final class SnitchListCapture {

    static final String TITLE = "Your snitches";
    static final String WORLD = "world";
    private static final Pattern LOCATION = Pattern.compile("^Location: (\\S+) (-?\\d+), (-?\\d+), (-?\\d+)$");
    private static final Pattern GROUP = Pattern.compile("^Group: (.*)$");
    private static final Pattern DORMANT = Pattern.compile("^Will go dormant in (.+)$");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])");

    private boolean open;
    private boolean warnedDimension;
    private List<String> lastSignature = List.of();
    private final Set<Long> recorded = new HashSet<>();

    public void tick(Snitches snitches) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || !TITLE.equals(screen.getTitle().getString())) {
            if (open) {
                finish(mc);
            }
            return;
        }
        open = true;
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            if (!warnedDimension) {
                warnedDimension = true;
                mc.player.displayClientMessage(Component.translatable("civmodern.snitches.wrongdimension"), false);
            }
            return;
        }

        Instant now = Instant.now();
        List<Snitch> found = new ArrayList<>();
        List<String> signature = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            Snitch snitch = parse(stack, now);
            if (snitch != null) {
                found.add(snitch);
                signature.add(slot.index + ":" + snitch.x() + "," + snitch.y() + "," + snitch.z() + ":" + snitch.dormantAt().getEpochSecond());
            }
        }
        // Pages fill in over a few ticks, so the same page is seen several times as it grows;
        // upserting by position makes re-recording harmless, but skip identical frames.
        if (found.isEmpty() || signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        snitches.record(found);
        for (Snitch snitch : found) {
            recorded.add(((long) snitch.x() << 42) ^ ((long) snitch.y() << 21) ^ (snitch.z() & 0x1FFFFF));
        }
    }

    private void finish(Minecraft mc) {
        if (mc.player != null && !recorded.isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("civmodern.snitches.recorded", recorded.size()), false);
        }
        open = false;
        warnedDimension = false;
        lastSignature = List.of();
        recorded.clear();
    }

    /** {@code null} for anything that isn't a snitch entry in the expected format. */
    static @Nullable Snitch parse(ItemStack stack, Instant now) {
        SnitchType type = SnitchType.fromItem(stack.getItem());
        if (type == null) {
            return null;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return null;
        }
        String world = null;
        int x = 0, y = 0, z = 0;
        String group = null;
        Duration remaining = null;
        for (Component line : lore.lines()) {
            String text = line.getString().trim();
            Matcher location = LOCATION.matcher(text);
            if (location.matches()) {
                world = location.group(1);
                x = Integer.parseInt(location.group(2));
                y = Integer.parseInt(location.group(3));
                z = Integer.parseInt(location.group(4));
                continue;
            }
            Matcher groupMatcher = GROUP.matcher(text);
            if (groupMatcher.matches()) {
                group = groupMatcher.group(1).trim();
                continue;
            }
            Matcher dormant = DORMANT.matcher(text);
            if (dormant.matches()) {
                remaining = parseDuration(dormant.group(1));
            }
        }
        if (!WORLD.equals(world) || group == null || remaining == null) {
            return null;
        }
        return new Snitch(x, y, z, stack.getHoverName().getString().trim(), group, type, now.plus(remaining), now);
    }

    /** "213 h 6m 40s", "213 h 6m", "40s"... any mix of d/h/m/s parts in any order. */
    static @Nullable Duration parseDuration(String text) {
        Matcher matcher = DURATION_PART.matcher(text);
        Duration total = Duration.ZERO;
        boolean any = false;
        while (matcher.find()) {
            any = true;
            long amount = Long.parseLong(matcher.group(1));
            total = switch (matcher.group(2)) {
                case "d" -> total.plusDays(amount);
                case "h" -> total.plusHours(amount);
                case "m" -> total.plusMinutes(amount);
                default -> total.plusSeconds(amount);
            };
        }
        return any ? total : null;
    }
}

package sh.okx.civmodern.common.map.snitches;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import sh.okx.civmodern.common.AbstractCivModernMod;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads JukeAlert's {@code /jalist} GUI. Format as observed on the server:
 * <pre>
 *   title: "Your snitches", a 54-slot chest
 *   slots 0-44: one snitch each, minecraft:note_block or minecraft:jukebox,
 *               display name = snitch name (empty if none)
 *     lore: "Location: world 1186, 63, -2184"
 *           "Group: FortunaBorder-SN"
 *           "Will go dormant in 213 h 6m 40s"   (active; zero-valued units are omitted)
 *        or "Will cull in 301 h 11m 13s"         (dormant)
 *           "Right click to send waypoint"
 *   slot 45: arrow "Go to previous page", absent on the first page
 *   slot 47: oak door "Exit"
 *   slot 53: arrow "Go to next page", absent on the last page
 * </pre>
 *
 * <p>The list is only trusted whole. A <em>pass</em> runs from the GUI opening until it has been
 * closed for {@link #CLOSE_GRACE_TICKS}, and it is complete once the server has shown both a page
 * with no previous arrow (the first) and one with no next arrow (the last): paging moves one
 * page at a time, so getting from one to the other means every page in between was sent too. A
 * complete pass replaces the stored set, so snitches the server has removed drop out; anything
 * less is discarded.
 *
 * <p>Snitches are read as the server's packets arrive ({@link #onContent}, {@link #onSlot}), not
 * by looking at the screen each tick: a fast clicker (or {@link JalistAutoPager}) can get two
 * pages in one tick, so a page could be overwritten before a tick-based reader saw it. The
 * arrows are judged only while no click is waiting on the server ({@link #onClick}): clicking
 * the next arrow makes the client show it lifted onto the cursor straight away, so until the
 * server answers, every page looks like the last.
 *
 * <p>Only "world" locations are kept, and only while the player is in the overworld, since the
 * store is per dimension.
 */
public final class SnitchListCapture {

    static final String TITLE = "Your snitches";
    static final String WORLD = "world";
    static final int PAGE_SLOTS = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int NEXT_SLOT = 53;
    static final String PREVIOUS_NAME = "Go to previous page";
    static final String NEXT_NAME = "Go to next page";
    /**
     * How long the GUI must stay closed before the pass ends. Should the server reopen a fresh
     * window per page, the close and the open can land in different ticks.
     */
    private static final int CLOSE_GRACE_TICKS = 10;
    /** Ticks without a page packet before a page built up slot by slot counts as complete. */
    static final int SETTLE_TICKS = 2;
    /** How long a click may go unanswered before the arrows are judged without its answer. */
    private static final int CLICK_ANSWER_TICKS = 20;

    private static final Pattern LOCATION = Pattern.compile("^Location: (\\S+) (-?\\d+), (-?\\d+), (-?\\d+)$");
    private static final Pattern GROUP = Pattern.compile("^Group: (.*)$");
    private static final Pattern LIFETIME = Pattern.compile("^Will (go dormant|cull) in (.+)$");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])");

    private long tick;

    // ---- the current pass; reset by start()
    private boolean open;
    private int closedTicks;
    /** Out of the overworld, or an entry that failed to parse: the pass can never be trusted. */
    private boolean invalid;
    private boolean sawFirstPage;
    private boolean sawLastPage;
    /** Saw any page after the first, so the player was paging rather than glancing at page 1. */
    private boolean sawLaterPage;
    /** Whether the page had a next arrow when the arrows were last judged. */
    private boolean next;
    /**
     * A click in the GUI the server has not answered yet. Until it has, the screen shows the
     * client's guess at the click's result: the arrow lifted onto the cursor, its slot empty.
     */
    private boolean clickPending;
    private long clickTick;
    /** The GUI's menu as last seen, to judge a page still settling when the GUI closes. */
    private @Nullable AbstractContainerMenu lastMenu;
    private final long[] pageKeys = new long[PAGE_SLOTS];
    private final Map<Long, Snitch> found = new HashMap<>();
    /** Bumped whenever the server changes which snitches are on the page. */
    private int pageChanges;
    /** Packets of any kind for this GUI in the pass, so an empty list still counts as arrived. */
    private int packets;
    private long lastPacketTick;
    /** A slot packet arrived since the arrows were last evaluated. */
    private boolean unsettled;

    // ---------------------------------------------------------------- packets (render thread)

    /** A whole-window update: every slot is the server's state, so judge the page at once. */
    public void onContent(int containerId) {
        AbstractContainerScreen<?> screen = jalistScreen(containerId);
        if (screen == null) {
            return;
        }
        ensureOpen();
        boolean changed = false;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.index < PAGE_SLOTS && slot.container != Minecraft.getInstance().player.getInventory()) {
                changed |= readSlot(slot.index, slot.getItem());
            }
        }
        packetArrived(changed);
        lastMenu = screen.getMenu();
        evaluateArrows(lastMenu);
    }

    /** One slot changed. A page may arrive as many of these, so the arrows wait to settle. */
    public void onSlot(int containerId, int slotIndex) {
        AbstractContainerScreen<?> screen = jalistScreen(containerId);
        if (screen == null || slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()) {
            return;
        }
        Slot slot = screen.getMenu().slots.get(slotIndex);
        if (slot.container == Minecraft.getInstance().player.getInventory()) {
            return;
        }
        ensureOpen();
        boolean changed = false;
        if (slotIndex < PAGE_SLOTS) {
            changed = readSlot(slotIndex, slot.getItem());
        }
        packetArrived(changed);
        unsettled = true;
    }

    /**
     * The player (or the pager) clicked in the GUI; the arrows wait for the server's answer.
     * Not for a click the client predicts changes nothing (an empty slot, empty-handed): the
     * server has nothing to correct, so it may never answer, and the screen is already right.
     */
    public void onClick(int containerId, int slotIndex) {
        AbstractContainerScreen<?> screen = jalistScreen(containerId);
        if (!open || screen == null) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        boolean emptySlot = slotIndex < 0 || slotIndex >= menu.slots.size() || menu.slots.get(slotIndex).getItem().isEmpty();
        if (emptySlot && menu.getCarried().isEmpty()) {
            return;
        }
        clickPending = true;
        clickTick = tick;
    }

    private void packetArrived(boolean changed) {
        clickPending = false;
        lastPacketTick = tick;
        packets++;
        if (changed) {
            pageChanges++;
        }
    }

    /**
     * Judges the page by its arrows as the screen shows them. Only called once the server's
     * view and the screen agree: after a whole-window packet, or once slot packets have gone
     * quiet with no click unanswered. The screen rather than the packets, because a server
     * that only sends what changed never sends "the next slot is now empty" on the last page:
     * the client already shows it empty, having lifted the arrow onto the cursor.
     */
    private void evaluateArrows(AbstractContainerMenu menu) {
        unsettled = false;
        if (!isArrow(slotItem(menu, PREVIOUS_SLOT), PREVIOUS_NAME)) {
            sawFirstPage = true;
        } else {
            sawLaterPage = true;
        }
        next = isArrow(slotItem(menu, NEXT_SLOT), NEXT_NAME);
        if (!next) {
            sawLastPage = true;
        }
    }

    /** @return whether the snitch in this slot is a different one than before */
    private boolean readSlot(int index, ItemStack stack) {
        if (stack.isEmpty() || SnitchType.fromItem(stack.getItem()) == null) {
            return swapKey(index, 0);
        }
        Snitch snitch = parse(stack, Instant.now());
        if (snitch == null) {
            if (!invalid) {
                AbstractCivModernMod.LOGGER.warn("Unreadable /jalist entry in slot {}: {} {}", index, stack, stack.get(DataComponents.LORE));
            }
            invalid = true;
            return swapKey(index, 0);
        }
        if (!WORLD.equals(locationWorld(stack))) {
            return swapKey(index, 0);
        }
        long key = BlockPos.asLong(snitch.x(), snitch.y(), snitch.z());
        found.put(key, snitch);
        return swapKey(index, key);
    }

    private boolean swapKey(int index, long key) {
        boolean changed = pageKeys[index] != key;
        pageKeys[index] = key;
        return changed;
    }

    // ---------------------------------------------------------------- ticks

    public void tick(Snitches snitches) {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreen<?> screen = jalistScreen(-1);
        if (screen == null) {
            if (!open) {
                return;
            }
            // Closed before a page built up slot by slot had gone quiet: judge it as it was
            // left, unless a click was still waiting on the server, when the screen may only
            // have been showing the client's guess.
            if (closedTicks == 0 && unsettled && !clickPending && lastMenu != null) {
                evaluateArrows(lastMenu);
            }
            if (++closedTicks >= CLOSE_GRACE_TICKS) {
                finish(mc, snitches);
            }
            return;
        }
        ensureOpen();
        closedTicks = 0;
        lastMenu = screen.getMenu();
        // A click the server never answers (one on an empty slot, say) stops holding things up.
        if (clickPending && tick - clickTick >= CLICK_ANSWER_TICKS) {
            clickPending = false;
        }
        if (unsettled && !clickPending && tick - lastPacketTick >= SETTLE_TICKS) {
            evaluateArrows(lastMenu);
        }
    }

    private void ensureOpen() {
        if (open) {
            closedTicks = 0;
            return;
        }
        open = true;
        closedTicks = 0;
        invalid = false;
        sawFirstPage = false;
        sawLastPage = false;
        sawLaterPage = false;
        next = false;
        clickPending = false;
        lastMenu = null;
        unsettled = false;
        Arrays.fill(pageKeys, 0);
        found.clear();
        pageChanges = 0;
        packets = 0;
        lastPacketTick = tick;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.level.dimension() != Level.OVERWORLD) {
            invalid = true;
            mc.player.displayClientMessage(Component.translatable("civmodern.snitches.wrongdimension"), false);
        }
    }

    private void finish(Minecraft mc, Snitches snitches) {
        open = false;
        if (mc.player == null) {
            return;
        }
        if (invalid) {
            // Out of the overworld was already reported when the pass began.
            if (mc.level != null && mc.level.dimension() == Level.OVERWORLD) {
                mc.player.displayClientMessage(Component.translatable("civmodern.snitches.unreadable").withStyle(ChatFormatting.YELLOW), false);
            }
        } else if (sawFirstPage && sawLastPage) {
            snitches.replaceAll(found.values());
            mc.player.displayClientMessage(Component.translatable("civmodern.snitches.recorded", found.size()).withStyle(ChatFormatting.GRAY), false);
        } else if (sawLaterPage) {
            mc.player.displayClientMessage(Component.translatable("civmodern.snitches.incomplete").withStyle(ChatFormatting.GRAY), false);
        }
        found.clear();
    }

    /** Abandons any pass in progress, e.g. on leaving the world. */
    public void reset() {
        open = false;
        lastMenu = null;
        found.clear();
    }

    // ---------------------------------------------------------------- for JalistAutoPager

    /** Whether a pass is in progress, the GUI possibly closed but within the grace period. */
    public boolean isOpen() {
        return open;
    }

    /** Whether the server's current page has a next arrow, once the page has settled. */
    public boolean hasNextPage() {
        return next;
    }

    /** Whether the server has sent anything for the GUI yet this pass. */
    public boolean hasPage() {
        return open && packets > 0;
    }

    public int pageChanges() {
        return pageChanges;
    }

    /** Ticks since the server last touched the GUI; a page is settled after a few quiet ones. */
    public long quietTicks() {
        return tick - lastPacketTick;
    }

    public boolean isSettled() {
        return !unsettled && !clickPending && quietTicks() >= SETTLE_TICKS;
    }

    // ---------------------------------------------------------------- parsing

    /** The open {@code /jalist} screen, provided its menu is {@code containerId} (or any, for -1). */
    static @Nullable AbstractContainerScreen<?> jalistScreen(int containerId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen) || !TITLE.equals(screen.getTitle().getString())) {
            return null;
        }
        if (containerId != -1 && screen.getMenu().containerId != containerId) {
            return null;
        }
        return screen;
    }

    private static ItemStack slotItem(AbstractContainerMenu menu, int index) {
        return index < menu.slots.size() ? menu.slots.get(index).getItem() : ItemStack.EMPTY;
    }

    static boolean isArrow(ItemStack stack, String name) {
        return stack.is(Items.ARROW) && name.equals(stack.getHoverName().getString().trim());
    }

    private static @Nullable String locationWorld(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return null;
        }
        for (Component line : lore.lines()) {
            Matcher location = LOCATION.matcher(line.getString().trim());
            if (location.matches()) {
                return location.group(1);
            }
        }
        return null;
    }

    /**
     * {@code null} for a note block or jukebox that isn't a snitch entry in the expected format.
     * Entries in other worlds parse fine; the caller decides whether to keep them.
     */
    static @Nullable Snitch parse(ItemStack stack, Instant now) {
        SnitchType type = SnitchType.fromItem(stack.getItem());
        if (type == null) {
            return null;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return null;
        }
        boolean located = false;
        int x = 0, y = 0, z = 0;
        String group = null;
        String stage = null;
        Duration remaining = null;
        for (Component line : lore.lines()) {
            String text = line.getString().trim();
            Matcher location = LOCATION.matcher(text);
            if (location.matches()) {
                located = true;
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
            Matcher lifetime = LIFETIME.matcher(text);
            if (lifetime.matches()) {
                stage = lifetime.group(1);
                remaining = parseDuration(lifetime.group(2));
            }
        }
        if (!located || group == null || (stage != null && remaining == null)) {
            return null;
        }
        String name = stack.getHoverName().getString().trim();
        if (stage == null) {
            // JukeAlert prints neither countdown in the instant between running out and culling.
            return new Snitch(x, y, z, name, group, type, now, now, now);
        }
        Instant at = now.plus(remaining);
        return stage.equals("cull")
            ? Snitch.dormant(x, y, z, name, group, type, at, now)
            : Snitch.active(x, y, z, name, group, type, at, now);
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

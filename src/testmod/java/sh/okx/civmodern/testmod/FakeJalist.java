package sh.okx.civmodern.testmod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Development only: a server-side stand-in for JukeAlert's {@code /jalist}, registered on the
 * integrated server of a singleplayer world by the separate testmod (see
 * {@link CivModernTestMod}), which never ships in the release jar, so the
 * snitch capture and the auto-pager can be tested without a real server. It mirrors the GUI in
 * {@code SnitchListCapture}'s javadoc: 45 snitches a page, the arrows in slots 45 and 53, the
 * exit door in 47. It runs JukeAlert's lifecycle on its own fake snitches, too: an active
 * snitch past its dormancy time is listed as dormant, and a dormant one past its cull time is
 * gone.
 *
 * <ul>
 *   <li>{@code /jalist} opens it.</li>
 *   <li>{@code /civmodern_fakejalist a|b|c} picks the data set and restarts every countdown. "a"
 *   is 100 snitches over 3 pages, including one 20 seconds from dormancy and one 20 seconds from
 *   being culled. "b" keeps the first 50 of "a" and adds 10 new ones on group "VariantB", over 2
 *   pages, so a refresh after switching shows the old 50 disappearing. "c" is "a" grown to 450
 *   snitches over 10 pages, slow enough to leave part-way by hand. "huge" is 20,000 snitches
 *   spread over about +-20,000 blocks, the three test snitches included, for profiling large
 *   networks; a refresh pages through it in about two minutes.</li>
 *   <li>{@code /civmodern_fakejalist long} adds six snitches to whichever data set is current:
 *   long names on short groups, short names on long groups, both long, and a short-and-short
 *   control, all on groups containing "Long".</li>
 *   <li>{@code /civmodern_fakejalist content|slots} picks how a page change reaches the client:
 *   one whole-window packet (the default, as JukeAlert is believed to do) or slot by slot.</li>
 * </ul>
 */
public final class FakeJalist {

    private static final int PER_PAGE = 45;
    /** Snitches in the "huge" data set: 445 pages, about two minutes to refresh at 250 ms a page. */
    private static final int HUGE_COUNT = 20000;
    private static final Duration DORMANCY = Duration.ofDays(14);

    private record Entry(BlockPos pos, String name, String group, boolean jukebox, Instant dormantAt, Instant cullAt) {
    }

    private static String variant = "a";
    private static boolean slotUpdates = false;
    private static List<Entry> variantA;
    private static List<Entry> entries;

    private FakeJalist() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jalist").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (entries == null) {
                generate(player.blockPosition());
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Menu(id, inventory),
                Component.literal("Your snitches")));
            return 1;
        }));
        dispatcher.register(Commands.literal("civmodern_fakejalist")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(
                    "Usage: /civmodern_fakejalist a|b|c|huge (data set, restarts countdowns), long (adds snitches with long names/groups) or content|slots (how pages arrive). Now: "
                        + variant + ", " + (slotUpdates ? "slots" : "content")), false);
                return 1;
            })
            .then(Commands.literal("a").executes(context -> select(context.getSource(), "a")))
            .then(Commands.literal("b").executes(context -> select(context.getSource(), "b")))
            .then(Commands.literal("c").executes(context -> select(context.getSource(), "c")))
            .then(Commands.literal("huge").executes(context -> select(context.getSource(), "huge")))
            .then(Commands.literal("content").executes(context -> mode(context.getSource(), false)))
            .then(Commands.literal("slots").executes(context -> mode(context.getSource(), true)))
            .then(Commands.literal("long").executes(context -> addLongNames(context.getSource()))));
    }

    private static int select(CommandSourceStack source, String chosen) {
        variant = chosen;
        generate(BlockPos.containing(source.getPosition()));
        source.sendSuccess(() -> Component.literal("Fake /jalist: variant " + chosen + ", " + entries.size() + " snitches, countdowns restarted"), false);
        return 1;
    }

    /**
     * Adds snitches whose names, groups, or both are too long for the snitch manager's columns,
     * plus a short-and-short control. Every group contains "Long", so a group search for it
     * finds them all.
     */
    private static int addLongNames(CommandSourceStack source) {
        BlockPos centre = BlockPos.containing(source.getPosition());
        if (entries == null) {
            generate(centre);
        }
        String[][] namesAndGroups = {
            {"Northern-Border-Watchtower-Main-Gate-Entrance", "LongNames"},
            {"Fortuna-Farm-Initial-Surface-Expansion-East-Wing", "LongGroup-Fortuna-Farm-Expansion-East"},
            {"Short", "LongGroup-Northern-Border-Watchtower-Network"},
            {"Very Long Snitch Name With Spaces In It For Testing", "Long Group Name With Spaces For Testing"},
            // Wide glyphs: fewer characters fit, so these cut off sooner than their length suggests.
            {"WWWWWWWWWWWWWWWWWWWWWWWWWWWWWW", "LongWWWWWWWWWWWWWWWWWWWW"},
            {"Short", "LongNames"},
        };
        Instant now = Instant.now();
        entries = new ArrayList<>(entries);
        for (int i = 0; i < namesAndGroups.length; i++) {
            // A row of their own: "a" fills z 0 to 144 and "b" adds z -48, and a shared spot would merge them.
            entries.add(active(centre.offset(i * 16, 0, -96), namesAndGroups[i][0], namesAndGroups[i][1], i % 2 == 0,
                now.plus(Duration.ofHours(200 + i))));
        }
        source.sendSuccess(() -> Component.literal("Fake /jalist: added " + namesAndGroups.length
            + " snitches with long names and/or groups, " + entries.size() + " in all"), false);
        return 1;
    }

    private static int mode(CommandSourceStack source, boolean slots) {
        slotUpdates = slots;
        source.sendSuccess(() -> Component.literal("Fake /jalist: pages arrive " + (slots ? "slot by slot" : "as one whole-window packet")), false);
        return 1;
    }

    /** Lays the snitches out on a grid around {@code centre}; "b" reuses "a"'s first 50, "c" extends "a". */
    private static void generate(BlockPos centre) {
        Instant now = Instant.now();
        variantA = new ArrayList<>();
        variantA.add(active(centre.offset(0, 0, 0), "Test-GoesDormant", "TestGroup", false, now.plusSeconds(20)));
        variantA.add(dormant(centre.offset(16, 0, 0), "Test-GetsCulled", "TestGroup", false, now.plusSeconds(20)));
        variantA.add(dormant(centre.offset(32, 0, 0), "Test-Dormant", "TestGroup", true, now.plus(Duration.ofDays(3))));
        if (variant.equals("huge")) {
            // A wide, sparse spread like a big network's: a 142-wide grid, 282 blocks apart,
            // about +-20,000 blocks around the centre, so the map and minimap only ever see a few.
            for (int i = 3; i < HUGE_COUNT; i++) {
                BlockPos pos = centre.offset(-20000 + (i % 142) * 282, 0, -20000 + (i / 142) * 282);
                variantA.add(active(pos, i % 7 == 0 ? "" : "Huge-" + i, "HugeGroup-" + (i % 20), i % 3 == 0,
                    now.plus(Duration.ofHours(i % 900)).plusSeconds(i * 37L % 3600)));
            }
            entries = variantA;
            return;
        }
        int count = variant.equals("c") ? 450 : 100;
        for (int i = 3; i < count; i++) {
            BlockPos pos = centre.offset((i % 10) * 16, 0, (i / 10) * 16);
            String name = i % 7 == 0 ? "" : "Fake-" + i;
            variantA.add(active(pos, name, i % 2 == 0 ? "FakeGroup-A" : "FakeGroup-B", i % 3 == 0,
                now.plus(Duration.ofHours((i % 100) * 9L)).plusSeconds(i * 37L)));
        }
        if (!variant.equals("b")) {
            entries = variantA;
        } else {
            entries = new ArrayList<>(variantA.subList(0, 50));
            for (int i = 0; i < 10; i++) {
                entries.add(active(centre.offset(i * 16, 0, -48), "VariantB-" + i, "VariantB", i % 2 == 0,
                    now.plus(Duration.ofHours(100 + i))));
            }
        }
    }

    private static Entry active(BlockPos pos, String name, String group, boolean jukebox, Instant dormantAt) {
        return new Entry(pos, name, group, jukebox, dormantAt, dormantAt.plus(DORMANCY));
    }

    private static Entry dormant(BlockPos pos, String name, String group, boolean jukebox, Instant cullAt) {
        return new Entry(pos, name, group, jukebox, cullAt.minus(DORMANCY), cullAt);
    }

    /** What JukeAlert would list now: culled snitches are deleted, so they are left out. */
    private static List<Entry> listed() {
        Instant now = Instant.now();
        return entries.stream().filter(entry -> now.isBefore(entry.cullAt())).toList();
    }

    private static final class Menu extends ChestMenu {
        private final SimpleContainer container;
        private int page;
        private boolean hasNext;

        Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(54));
        }

        private Menu(int id, Inventory inventory, SimpleContainer container) {
            super(MenuType.GENERIC_9x6, id, inventory, container, 6);
            this.container = container;
            fill(0);
        }

        private void fill(int page) {
            this.page = page;
            container.clearContent();
            List<Entry> listed = listed();
            int start = page * PER_PAGE;
            int end = Math.min(listed.size(), start + PER_PAGE);
            Instant now = Instant.now();
            for (int i = start; i < end; i++) {
                container.setItem(i - start, item(listed.get(i), now));
            }
            if (page > 0) {
                container.setItem(45, named(new ItemStack(Items.ARROW), "Go to previous page"));
            }
            ItemStack exit = named(new ItemStack(Items.OAK_DOOR), "Exit");
            exit.set(DataComponents.LORE, new ItemLore(List.of(plain("Click to exit GUI", ChatFormatting.AQUA))));
            container.setItem(47, exit);
            hasNext = end < listed.size();
            if (hasNext) {
                container.setItem(53, named(new ItemStack(Items.ARROW), "Go to next page"));
            }
        }

        // Nothing can be taken out, as with a cancelled Bukkit click: the server answers the
        // client's predicted pickup by resending the slots, as the real GUI does.
        @Override
        public void clicked(int slot, int button, ClickType clickType, Player player) {
            if (slot == 53 && hasNext) {
                turnTo(page + 1);
            } else if (slot == 45 && page > 0) {
                turnTo(page - 1);
            } else if (slot == 47 && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
        }

        private void turnTo(int page) {
            fill(page);
            if (!slotUpdates) {
                sendAllDataToRemote();
            }
            // Otherwise the server tick's broadcastChanges sends each changed slot on its own.
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private static ItemStack item(Entry entry, Instant now) {
        ItemStack stack = new ItemStack(entry.jukebox() ? Items.JUKEBOX : Items.NOTE_BLOCK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(entry.name()).withStyle(ChatFormatting.GOLD));
        boolean dormant = !now.isBefore(entry.dormantAt());
        String countdown = dormant
            ? "Will cull in " + format(Duration.between(now, entry.cullAt()))
            : "Will go dormant in " + format(Duration.between(now, entry.dormantAt()));
        BlockPos pos = entry.pos();
        MutableComponent location = plain("Location: ", ChatFormatting.AQUA)
            .append(Component.literal("world ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(String.valueOf(pos.getX())).withStyle(ChatFormatting.RED))
            .append(Component.literal(", ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(String.valueOf(pos.getY())).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(", ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(String.valueOf(pos.getZ())).withStyle(ChatFormatting.BLUE));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            location,
            plain("Group: " + entry.group(), ChatFormatting.YELLOW),
            plain(countdown, ChatFormatting.AQUA),
            plain("Right click to send waypoint", ChatFormatting.GOLD))));
        return stack;
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, plain(name, ChatFormatting.GOLD));
        return stack;
    }

    private static MutableComponent plain(String text, ChatFormatting colour) {
        return Component.literal(text).withStyle(style -> style.withColor(colour).withItalic(false));
    }

    /** JukeAlert's format: "301 h 11m 13s", hours not split into days, zero parts left out. */
    private static String format(Duration duration) {
        long hours = duration.toHours();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        List<String> parts = new ArrayList<>();
        if (hours > 0) {
            parts.add(hours + " h");
        }
        if (minutes > 0) {
            parts.add(minutes + "m");
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }
}

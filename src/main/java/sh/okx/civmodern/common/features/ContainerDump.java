package sh.okx.civmodern.common.features;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.events.ClientTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic: {@code /civmodern_containerdump} arms a one-shot recorder for the next container
 * screen. While it is open, every distinct page of the top container (each time its contents
 * change, e.g. after a "next page" click) is appended to {@code civmodern/container-dump.txt}
 * in the game directory: the title, then per slot the item, its name and each lore line, both
 * as plain text and as the component tree so colours and italics are visible. Disarms when the
 * screen closes. Used to learn the exact shape of server GUIs before writing a parser for them.
 */
public class ContainerDump {

    private boolean armed;
    private boolean sawScreen;
    private List<String> lastSignature = List.of();

    public void arm() {
        this.armed = true;
        this.sawScreen = false;
        this.lastSignature = List.of();
    }

    @Subscribe
    public void onTick(ClientTickEvent event) {
        if (!armed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            if (sawScreen) {
                armed = false;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("Container dump written to " + dumpFile()), false);
                }
            }
            return;
        }
        sawScreen = true;

        List<Slot> topSlots = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (mc.player == null || slot.container != mc.player.getInventory()) {
                topSlots.add(slot);
            }
        }

        List<String> signature = new ArrayList<>();
        boolean anyItem = false;
        for (Slot slot : topSlots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            anyItem = true;
            signature.add(slot.index + ":" + describe(stack));
        }
        if (!anyItem || signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;

        StringBuilder out = new StringBuilder();
        out.append("==== ").append(Instant.now()).append(" ====\n");
        out.append("screen: ").append(screen.getClass().getName()).append('\n');
        out.append("title plain: ").append(screen.getTitle().getString()).append('\n');
        out.append("title tree:  ").append(screen.getTitle()).append('\n');
        out.append("top slots: ").append(topSlots.size()).append('\n');
        for (Slot slot : topSlots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            out.append("-- slot ").append(slot.index).append(" x").append(stack.getCount())
                .append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append('\n');
            out.append("   name plain: ").append(stack.getHoverName().getString()).append('\n');
            out.append("   name tree:  ").append(stack.getHoverName()).append('\n');
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                int i = 0;
                for (Component line : lore.lines()) {
                    out.append("   lore[").append(i).append("] plain: ").append(line.getString()).append('\n');
                    out.append("   lore[").append(i).append("] tree:  ").append(line).append('\n');
                    i++;
                }
            }
        }
        out.append('\n');

        try {
            Path file = dumpFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            AbstractCivModernMod.LOGGER.error("Failed to write container dump", e);
        }
        AbstractCivModernMod.LOGGER.info("Container dump page recorded ({} items)", signature.size());
    }

    private static String describe(ItemStack stack) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
            .append('|').append(stack.getHoverName().getString());
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                sb.append('|').append(line.getString());
            }
        }
        return sb.toString();
    }

    private static Path dumpFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("civmodern").resolve("container-dump.txt");
    }
}

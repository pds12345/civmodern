package sh.okx.civmodern.common.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.radar.PlayerRelation;
import sh.okx.civmodern.common.radar.PlayerRelationEntry;
import sh.okx.civmodern.common.radar.PlayerRelations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Three lists (friendly/neutral/hostile) of players, each rendered as the same kind of scrollable
 * table {@link sh.okx.civmodern.common.map.screen.WaypointManagerScreen} uses for waypoints.
 * Clicking a row removes that player from the open list; "+ Add player" adds one to it.
 */
public class PlayerRelationsScreen extends Screen {

    private static final int ROW_HEIGHT = 20;

    private static final int HEADER_COLOUR = 0xFFAAAAAA;
    private static final int NAME_COLOUR = 0xFFFFFFFF;
    private static final int STRIPE_COLOUR = 0x14FFFFFF;
    private static final int HOVER_COLOUR = 0x33FFFFFF;
    private static final int DELETE_HINT_COLOUR = 0xFFFF5555;

    private final Screen parent;
    private final PlayerRelations relations;

    private PlayerRelation selected = PlayerRelation.FRIENDLY;
    private double scroll;

    private Button friendlyButton;
    private Button neutralButton;
    private Button hostileButton;

    public PlayerRelationsScreen(Screen parent, PlayerRelations relations) {
        super(Component.translatable("civmodern.screen.playerrelations.title"));
        this.parent = parent;
        this.relations = relations;
    }

    @Override
    protected void init() {
        int tabWidth = 110;
        int gap = 4;
        int addGap = 16;
        int addWidth = 110;
        int totalWidth = tabWidth * 3 + gap * 2 + addGap + addWidth;
        int left = (this.width - totalWidth) / 2;
        int y = 24;

        friendlyButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.friendly"),
                button -> select(PlayerRelation.FRIENDLY))
            .pos(left, y).size(tabWidth, 20).build();
        neutralButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.neutral"),
                button -> select(PlayerRelation.NEUTRAL))
            .pos(left + tabWidth + gap, y).size(tabWidth, 20).build();
        hostileButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.hostile"),
                button -> select(PlayerRelation.HOSTILE))
            .pos(left + (tabWidth + gap) * 2, y).size(tabWidth, 20).build();
        addRenderableWidget(friendlyButton);
        addRenderableWidget(neutralButton);
        addRenderableWidget(hostileButton);
        updateTabButtons();

        addRenderableWidget(Button.builder(Component.translatable("civmodern.screen.playerrelations.add"),
                button -> Minecraft.getInstance().setScreen(new AddPlayerScreen(this, relations, selected)))
            .pos(left + tabWidth * 3 + gap * 2 + addGap, y).size(addWidth, 20).build());
    }

    private void select(PlayerRelation category) {
        this.selected = category;
        this.scroll = 0;
        updateTabButtons();
    }

    private void updateTabButtons() {
        friendlyButton.active = selected != PlayerRelation.FRIENDLY;
        neutralButton.active = selected != PlayerRelation.NEUTRAL;
        hostileButton.active = selected != PlayerRelation.HOSTILE;
    }

    private List<PlayerRelationEntry> sortedEntries() {
        List<PlayerRelationEntry> list = new ArrayList<>(relations.getByRelation(selected));
        list.sort(Comparator.comparing(PlayerRelationEntry::username, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    // ------------------------------------------------------------- geometry

    private int tableWidth() {
        return Math.min(300, this.width - 40);
    }

    private int tableLeft() {
        return (this.width - tableWidth()) / 2;
    }

    private int rowsTop() {
        return 64;
    }

    private int rowsBottom() {
        return this.height - 24;
    }

    private double maxScroll(int rowCount) {
        return Math.max(0, rowCount * ROW_HEIGHT - (rowsBottom() - rowsTop()));
    }

    // ------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(guiGraphics);

        List<PlayerRelationEntry> list = sortedEntries();
        int left = tableLeft();
        int right = left + tableWidth();
        int top = rowsTop();
        int bottom = rowsBottom();
        scroll = Mth.clamp(scroll, 0, maxScroll(list.size()));

        guiGraphics.drawCenteredString(font, this.title, this.width / 2, 8, NAME_COLOUR);
        guiGraphics.drawString(font, "Name", left + 6, top - 12, HEADER_COLOUR);
        guiGraphics.fill(left, top - 3, right, top - 2, HEADER_COLOUR);

        if (list.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("civmodern.screen.playerrelations.empty"),
                this.width / 2, (top + bottom) / 2 - font.lineHeight / 2, HEADER_COLOUR);
            return;
        }

        int hovered = rowAt(mouseX, mouseY);

        guiGraphics.enableScissor(left, top, right, bottom);
        for (int i = 0; i < list.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - (int) scroll;
            if (rowY + ROW_HEIGHT <= top || rowY >= bottom) {
                continue;
            }
            if (i == hovered) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, HOVER_COLOUR);
            } else if (i % 2 == 1) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, STRIPE_COLOUR);
            }

            PlayerRelationEntry entry = list.get(i);
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;

            String name = font.plainSubstrByWidth(entry.username(), right - left - 12 - 16);
            guiGraphics.drawString(font, name, left + 6, textY, NAME_COLOUR);
            guiGraphics.drawString(font, "X", right - 12, textY, DELETE_HINT_COLOUR);
        }
        guiGraphics.disableScissor();

        int content = list.size() * ROW_HEIGHT;
        int viewport = bottom - top;
        if (content > viewport) {
            int barHeight = Math.max(10, viewport * viewport / content);
            int barY = top + (int) ((viewport - barHeight) * (scroll / maxScroll(list.size())));
            guiGraphics.fill(right - 2, top, right, bottom, STRIPE_COLOUR);
            guiGraphics.fill(right - 2, barY, right, barY + barHeight, 0x88FFFFFF);
        }
    }

    /** @return the row index under the mouse, or -1 when the mouse is outside the table. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < tableLeft() || mouseX >= tableLeft() + tableWidth()
            || mouseY < rowsTop() || mouseY >= rowsBottom()) {
            return -1;
        }
        return (int) ((mouseY - rowsTop() + scroll) / ROW_HEIGHT);
    }

    // ------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        int row = rowAt(event.x(), event.y());
        List<PlayerRelationEntry> list = sortedEntries();
        if (row < 0 || row >= list.size()) {
            return false;
        }
        relations.remove(list.get(row).username());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        scroll = Mth.clamp(scroll - scrollY * ROW_HEIGHT, 0, maxScroll(sortedEntries().size()));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

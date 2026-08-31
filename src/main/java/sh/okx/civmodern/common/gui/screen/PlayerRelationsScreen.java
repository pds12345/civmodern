package sh.okx.civmodern.common.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
 * Six pages of players - All, Friendly, Cordial, Neutral, Suspicious, Hostile - each rendered as
 * the same kind of scrollable table {@link sh.okx.civmodern.common.map.screen.WaypointManagerScreen}
 * uses for waypoints. Every row has five fixed columns (Friendly/Cordial/Neutral/Suspicious/Hostile,
 * left to right) with a single-letter "move to" button in whichever four don't match the player's
 * current relation, plus a delete button; "+ Add player" adds a new entry to the open list. A
 * player's name is coloured by their relation on every page, All included.
 */
public class PlayerRelationsScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 16;
    private static final int MOVE_BUTTON_WIDTH = 20;
    private static final int DELETE_BUTTON_WIDTH = 20;
    private static final int BUTTON_GAP = 4;
    private static final int RIGHT_MARGIN = 6;

    private static final int HEADER_COLOUR = 0xFFAAAAAA;
    private static final int NAME_COLOUR = 0xFFFFFFFF;
    private static final int STRIPE_COLOUR = 0x14FFFFFF;
    private static final int HOVER_COLOUR = 0x33FFFFFF;
    private static final int BUTTON_COLOUR = 0x22FFFFFF;
    private static final int BUTTON_HOVER_COLOUR = 0x44FFFFFF;
    private static final int DELETE_COLOUR = 0xFFFF5555;

    /** Left-to-right column order: Friendly, Cordial, Neutral, Suspicious, Hostile. */
    private static final PlayerRelation[] COLUMNS = {
        PlayerRelation.FRIENDLY, PlayerRelation.CORDIAL, PlayerRelation.NEUTRAL,
        PlayerRelation.SUSPICIOUS, PlayerRelation.HOSTILE};

    private final Screen parent;
    private final PlayerRelations relations;

    /** null means the All page. */
    private PlayerRelation selected = null;
    private double scroll;

    private Button allButton;
    private Button friendlyButton;
    private Button cordialButton;
    private Button neutralButton;
    private Button suspiciousButton;
    private Button hostileButton;
    private Button addButton;

    public PlayerRelationsScreen(Screen parent, PlayerRelations relations) {
        super(Component.translatable("civmodern.screen.playerrelations.title"));
        this.parent = parent;
        this.relations = relations;
    }

    @Override
    protected void init() {
        int tabWidth = 68;
        int gap = 4;
        int rowGap = 4;
        int topY = 24;
        int bottomY = topY + 20 + rowGap;

        int addGap = 16;
        int addWidth = 100;
        int topWidth = tabWidth + addGap + addWidth;
        int topLeft = (this.width - topWidth) / 2;

        allButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.all"),
                button -> select(null))
            .pos(topLeft, topY).size(tabWidth, 20).build();
        addButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.add"),
                button -> Minecraft.getInstance().setScreen(new AddPlayerScreen(this, relations, selected)))
            .pos(topLeft + tabWidth + addGap, topY).size(addWidth, 20).build();
        addButton.setTooltip(Tooltip.create(Component.translatable("civmodern.screen.playerrelations.add.needslist")));
        addRenderableWidget(allButton);
        addRenderableWidget(addButton);

        int bottomWidth = tabWidth * 5 + gap * 4;
        int bottomLeft = (this.width - bottomWidth) / 2;

        friendlyButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.friendly"),
                button -> select(PlayerRelation.FRIENDLY))
            .pos(bottomLeft, bottomY).size(tabWidth, 20).build();
        cordialButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.cordial"),
                button -> select(PlayerRelation.CORDIAL))
            .pos(bottomLeft + (tabWidth + gap), bottomY).size(tabWidth, 20).build();
        neutralButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.neutral"),
                button -> select(PlayerRelation.NEUTRAL))
            .pos(bottomLeft + (tabWidth + gap) * 2, bottomY).size(tabWidth, 20).build();
        suspiciousButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.suspicious"),
                button -> select(PlayerRelation.SUSPICIOUS))
            .pos(bottomLeft + (tabWidth + gap) * 3, bottomY).size(tabWidth, 20).build();
        hostileButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.hostile"),
                button -> select(PlayerRelation.HOSTILE))
            .pos(bottomLeft + (tabWidth + gap) * 4, bottomY).size(tabWidth, 20).build();
        addRenderableWidget(friendlyButton);
        addRenderableWidget(cordialButton);
        addRenderableWidget(neutralButton);
        addRenderableWidget(suspiciousButton);
        addRenderableWidget(hostileButton);

        updateTabButtons();
    }

    private void select(PlayerRelation category) {
        this.selected = category;
        this.scroll = 0;
        updateTabButtons();
    }

    private void updateTabButtons() {
        allButton.active = selected != null;
        friendlyButton.active = selected != PlayerRelation.FRIENDLY;
        cordialButton.active = selected != PlayerRelation.CORDIAL;
        neutralButton.active = selected != PlayerRelation.NEUTRAL;
        suspiciousButton.active = selected != PlayerRelation.SUSPICIOUS;
        hostileButton.active = selected != PlayerRelation.HOSTILE;
        // "Add player" needs a specific list to add to - not meaningful from the All page.
        addButton.active = selected != null;
    }

    private List<PlayerRelationEntry> sortedEntries() {
        List<PlayerRelationEntry> list = new ArrayList<>();
        if (selected == null) {
            for (PlayerRelation relation : PlayerRelation.values()) {
                list.addAll(relations.getByRelation(relation));
            }
        } else {
            list.addAll(relations.getByRelation(selected));
        }
        list.sort(Comparator.comparing(PlayerRelationEntry::username, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    // ------------------------------------------------------------- geometry

    private int tableWidth() {
        return Math.min(380, this.width - 40);
    }

    private int tableLeft() {
        return (this.width - tableWidth()) / 2;
    }

    private int rowsTop() {
        return 88;
    }

    private int rowsBottom() {
        return this.height - 24;
    }

    private double maxScroll(int rowCount) {
        return Math.max(0, rowCount * ROW_HEIGHT - (rowsBottom() - rowsTop()));
    }

    private int deleteLeft(int right) {
        return right - RIGHT_MARGIN - DELETE_BUTTON_WIDTH;
    }

    /** Left edge of the column at the given {@link #COLUMNS} index. */
    private int columnLeft(int right, int column) {
        int columnsFromRight = COLUMNS.length - 1 - column;
        return deleteLeft(right) - BUTTON_GAP - (MOVE_BUTTON_WIDTH + BUTTON_GAP) * columnsFromRight - MOVE_BUTTON_WIDTH;
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

        int deleteLeft = deleteLeft(right);
        int nameRight = columnLeft(right, 0) - 8;

        int hoveredRow = rowAt(mouseX, mouseY);

        guiGraphics.enableScissor(left, top, right, bottom);
        for (int i = 0; i < list.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - (int) scroll;
            if (rowY + ROW_HEIGHT <= top || rowY >= bottom) {
                continue;
            }
            if (i == hoveredRow) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, HOVER_COLOUR);
            } else if (i % 2 == 1) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, STRIPE_COLOUR);
            }

            PlayerRelationEntry entry = list.get(i);
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            int buttonY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
            boolean rowHovered = i == hoveredRow;

            String name = font.plainSubstrByWidth(entry.username(), nameRight - left - 6);
            guiGraphics.drawString(font, name, left + 6, textY, entry.relation().colour());

            for (int column = 0; column < COLUMNS.length; column++) {
                PlayerRelation columnRelation = COLUMNS[column];
                if (columnRelation == entry.relation()) {
                    continue; // this player's current list - nothing to move them to here
                }
                int columnX = columnLeft(right, column);
                drawRowButton(guiGraphics, columnX, buttonY, MOVE_BUTTON_WIDTH,
                    Component.literal(columnRelation.name().substring(0, 1)),
                    rowHovered && mouseX >= columnX && mouseX < columnX + MOVE_BUTTON_WIDTH, NAME_COLOUR);
            }
            drawRowButton(guiGraphics, deleteLeft, buttonY, DELETE_BUTTON_WIDTH,
                Component.literal("X"),
                rowHovered && mouseX >= deleteLeft && mouseX < deleteLeft + DELETE_BUTTON_WIDTH, DELETE_COLOUR);
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

    private void drawRowButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, boolean hovered, int textColour) {
        guiGraphics.fill(x, y, x + width, y + BUTTON_HEIGHT, hovered ? BUTTON_HOVER_COLOUR : BUTTON_COLOUR);
        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, label, textX, textY, textColour);
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

        int right = tableLeft() + tableWidth();
        double mouseX = event.x();
        PlayerRelationEntry entry = list.get(row);

        int deleteLeft = deleteLeft(right);
        if (mouseX >= deleteLeft && mouseX < deleteLeft + DELETE_BUTTON_WIDTH) {
            relations.remove(entry.username());
            return true;
        }

        for (int column = 0; column < COLUMNS.length; column++) {
            PlayerRelation columnRelation = COLUMNS[column];
            if (columnRelation == entry.relation()) {
                continue;
            }
            int columnX = columnLeft(right, column);
            if (mouseX >= columnX && mouseX < columnX + MOVE_BUTTON_WIDTH) {
                relations.setRelation(entry.username(), columnRelation);
                return true;
            }
        }

        return false;
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

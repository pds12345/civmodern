package sh.okx.civmodern.common.map.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.map.snitches.JalistAutoPager;
import sh.okx.civmodern.common.map.snitches.Snitch;
import sh.okx.civmodern.common.map.snitches.SnitchRenderer;
import sh.okx.civmodern.common.map.snitches.Snitches;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Three pages of snitches - Active, Dormant, Culled - each the same kind of scrollable table
 * {@link sh.okx.civmodern.common.gui.screen.PlayerRelationsScreen} uses, with a delete button
 * per row. Each page is sorted by how soon its snitches next change state, most urgent first;
 * culled ones by how recently they were culled. "Refresh all" pages through {@code /jalist}
 * with {@link JalistAutoPager}; "Delete all" forgets every snitch locally.
 */
public class SnitchManagerScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 16;
    private static final int DELETE_BUTTON_WIDTH = 20;
    private static final int RIGHT_MARGIN = 6;
    private static final int TIME_WIDTH = 64;
    private static final int COORDS_WIDTH = 104;
    private static final int GROUP_WIDTH = 100;
    private static final int COLUMN_GAP = 8;

    private static final int HEADER_COLOUR = 0xFFAAAAAA;
    private static final int NAME_COLOUR = 0xFFFFFFFF;
    private static final int UNNAMED_COLOUR = 0xFF888888;
    private static final int GROUP_COLOUR = 0xFFFFFF55;
    private static final int STRIPE_COLOUR = 0x14FFFFFF;
    private static final int HOVER_COLOUR = 0x33FFFFFF;
    private static final int BUTTON_COLOUR = 0x22FFFFFF;
    private static final int BUTTON_HOVER_COLOUR = 0x44FFFFFF;
    private static final int DELETE_COLOUR = 0xFFFF5555;

    /** The state lists are re-sorted at most this often, as snitches age from one into the next. */
    private static final long RESORT_MS = 1000;

    private final Screen parent;
    private final Snitches snitches;
    private final JalistAutoPager pager;

    private Snitch.State selected = Snitch.State.ACTIVE;
    private double scroll;

    private final Map<Snitch.State, Button> tabs = new EnumMap<>(Snitch.State.class);
    private final Map<Snitch.State, List<Snitch>> lists = new EnumMap<>(Snitch.State.class);
    private int listsVersion = -1;
    private long listsBuiltMs;

    public SnitchManagerScreen(Screen parent, Snitches snitches, JalistAutoPager pager) {
        super(Component.translatable("civmodern.screen.snitchmanager.title"));
        this.parent = parent;
        this.snitches = snitches;
        this.pager = pager;
    }

    @Override
    protected void init() {
        int tabWidth = 80;
        int gap = 4;
        int topY = 24;
        int bottomY = topY + 20 + gap;

        int tabsLeft = (this.width - (tabWidth * 3 + gap * 2)) / 2;
        int i = 0;
        for (Snitch.State state : Snitch.State.values()) {
            Snitch.State tab = state;
            Button button = Button.builder(Component.empty(), b -> select(tab))
                .pos(tabsLeft + (tabWidth + gap) * i++, topY).size(tabWidth, 20).build();
            tabs.put(state, addRenderableWidget(button));
        }

        int actionWidth = 120;
        int actionsLeft = (this.width - (actionWidth * 2 + gap)) / 2;
        Button refresh = Button.builder(Component.translatable("civmodern.screen.snitchmanager.refresh"), b -> confirmRefresh())
            .pos(actionsLeft, bottomY).size(actionWidth, 20).build();
        Minecraft mc = Minecraft.getInstance();
        boolean overworld = mc.level != null && mc.level.dimension() == Level.OVERWORLD;
        refresh.active = overworld && !pager.isRunning();
        refresh.setTooltip(Tooltip.create(Component.translatable(overworld
            ? "civmodern.screen.snitchmanager.refresh.tooltip"
            : "civmodern.snitches.wrongdimension")));
        addRenderableWidget(refresh);
        addRenderableWidget(Button.builder(Component.translatable("civmodern.screen.snitchmanager.deleteall"), b -> confirmDeleteAll())
            .pos(actionsLeft + actionWidth + gap, bottomY).size(actionWidth, 20).build());

        rebuildLists();
        refreshTabs();
    }

    private void confirmRefresh() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                Minecraft.getInstance().setScreen(null);
                pager.start();
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, Component.translatable("civmodern.screen.snitchmanager.refresh.confirm.title"),
            Component.translatable("civmodern.screen.snitchmanager.refresh.confirm.message")));
    }

    private void confirmDeleteAll() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                snitches.clear();
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.translatable("civmodern.screen.snitchmanager.deleteall.confirm.title"),
            Component.translatable("civmodern.screen.snitchmanager.deleteall.confirm.message", snitches.size())));
    }

    private void select(Snitch.State state) {
        this.selected = state;
        this.scroll = 0;
        refreshTabs();
    }

    private void refreshTabs() {
        for (Map.Entry<Snitch.State, Button> tab : tabs.entrySet()) {
            Snitch.State state = tab.getKey();
            tab.getValue().active = state != selected;
            tab.getValue().setMessage(Component.translatable("civmodern.screen.snitchmanager." + state.name().toLowerCase(), lists.get(state).size()));
        }
    }

    /**
     * Splits and sorts the snitches by state, when they changed or have had time to age.
     * @return whether the lists were rebuilt
     */
    private boolean rebuildLists() {
        long nowMs = System.currentTimeMillis();
        if (listsVersion == snitches.version() && nowMs - listsBuiltMs < RESORT_MS) {
            return false;
        }
        listsVersion = snitches.version();
        listsBuiltMs = nowMs;
        Instant now = Instant.ofEpochMilli(nowMs);
        for (Snitch.State state : Snitch.State.values()) {
            lists.put(state, new ArrayList<>());
        }
        for (Snitch snitch : snitches.getSnitches()) {
            lists.get(snitch.state(now)).add(snitch);
        }
        lists.get(Snitch.State.ACTIVE).sort(Comparator.comparing(Snitch::dormantAt));
        lists.get(Snitch.State.DORMANT).sort(Comparator.comparing(Snitch::cullAt));
        lists.get(Snitch.State.CULLED).sort(Comparator.comparing(Snitch::cullAt).reversed());
        return true;
    }

    private List<Snitch> entries() {
        if (rebuildLists()) {
            refreshTabs();
        }
        return lists.get(selected);
    }

    // ------------------------------------------------------------- geometry

    private int tableWidth() {
        return Math.min(460, this.width - 40);
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

    private int timeRight(int right) {
        return deleteLeft(right) - COLUMN_GAP;
    }

    private int coordsLeft(int right) {
        return timeRight(right) - TIME_WIDTH - COLUMN_GAP - COORDS_WIDTH;
    }

    private int groupLeft(int right) {
        return coordsLeft(right) - COLUMN_GAP - GROUP_WIDTH;
    }

    // ------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(guiGraphics);

        List<Snitch> list = entries();
        int left = tableLeft();
        int right = left + tableWidth();
        int top = rowsTop();
        int bottom = rowsBottom();
        scroll = Mth.clamp(scroll, 0, maxScroll(list.size()));

        int groupLeft = groupLeft(right);
        int coordsLeft = coordsLeft(right);
        int timeRight = timeRight(right);
        int deleteLeft = deleteLeft(right);

        guiGraphics.drawCenteredString(font, this.title, this.width / 2, 8, NAME_COLOUR);
        guiGraphics.drawString(font, Component.translatable("civmodern.screen.snitchmanager.name"), left + 6, top - 12, HEADER_COLOUR);
        guiGraphics.drawString(font, Component.translatable("civmodern.screen.snitchmanager.group"), groupLeft, top - 12, HEADER_COLOUR);
        guiGraphics.drawString(font, Component.translatable("civmodern.screen.snitchmanager.coords"), coordsLeft, top - 12, HEADER_COLOUR);
        Component timeHeader = Component.translatable("civmodern.screen.snitchmanager.time." + selected.name().toLowerCase());
        guiGraphics.drawString(font, timeHeader, timeRight - font.width(timeHeader), top - 12, HEADER_COLOUR);
        guiGraphics.fill(left, top - 3, right, top - 2, HEADER_COLOUR);

        if (list.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("civmodern.screen.snitchmanager.empty"),
                this.width / 2, (top + bottom) / 2 - font.lineHeight / 2, HEADER_COLOUR);
            return;
        }

        Instant now = Instant.now();
        int hoveredRow = rowAt(mouseX, mouseY);

        guiGraphics.enableScissor(left, top, right, bottom);
        // Only the rows in view are drawn, so a long list costs no more than a short one.
        int first = Math.max(0, (int) (scroll / ROW_HEIGHT));
        int last = Math.min(list.size(), first + (bottom - top) / ROW_HEIGHT + 2);
        for (int i = first; i < last; i++) {
            int rowY = top + i * ROW_HEIGHT - (int) scroll;
            if (i == hoveredRow) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, HOVER_COLOUR);
            } else if (i % 2 == 1) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, STRIPE_COLOUR);
            }

            Snitch snitch = list.get(i);
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            int buttonY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;

            String name = snitch.name().isEmpty()
                ? Component.translatable("civmodern.snitches.unnamed").getString()
                : snitch.name();
            guiGraphics.drawString(font, font.plainSubstrByWidth(name, groupLeft - COLUMN_GAP - left - 6), left + 6, textY,
                snitch.name().isEmpty() ? UNNAMED_COLOUR : NAME_COLOUR);
            guiGraphics.drawString(font, font.plainSubstrByWidth(snitch.group(), GROUP_WIDTH), groupLeft, textY, GROUP_COLOUR);
            guiGraphics.drawString(font, font.plainSubstrByWidth(snitch.x() + ", " + snitch.y() + ", " + snitch.z(), COORDS_WIDTH), coordsLeft, textY, NAME_COLOUR);
            String time = timeText(snitch, now);
            guiGraphics.drawString(font, time, timeRight - font.width(time), textY, NAME_COLOUR);

            drawRowButton(guiGraphics, deleteLeft, buttonY, DELETE_BUTTON_WIDTH, Component.literal("X"),
                i == hoveredRow && mouseX >= deleteLeft && mouseX < deleteLeft + DELETE_BUTTON_WIDTH, DELETE_COLOUR);
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

    /** Time left in the current stage, or how long ago a culled snitch was culled. */
    private String timeText(Snitch snitch, Instant now) {
        Duration duration = switch (snitch.state(now)) {
            case ACTIVE -> snitch.remaining(now);
            case DORMANT -> snitch.untilCull(now);
            case CULLED -> snitch.untilCull(now).negated();
        };
        return SnitchRenderer.formatRemaining(duration);
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
        List<Snitch> list = entries();
        if (row < 0 || row >= list.size()) {
            return false;
        }
        int deleteLeft = deleteLeft(tableLeft() + tableWidth());
        if (event.x() >= deleteLeft && event.x() < deleteLeft + DELETE_BUTTON_WIDTH) {
            snitches.delete(list.get(row));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        scroll = Mth.clamp(scroll - scrollY * ROW_HEIGHT, 0, maxScroll(entries().size()));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

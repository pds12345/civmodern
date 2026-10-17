package sh.okx.civmodern.common.map.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.gui.SearchFilter;
import sh.okx.civmodern.common.map.snitches.JalistAutoPager;
import sh.okx.civmodern.common.map.snitches.Snitch;
import sh.okx.civmodern.common.map.snitches.SnitchRenderer;
import sh.okx.civmodern.common.map.snitches.Snitches;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.map.waypoints.Waypoints;
import sh.okx.civmodern.common.mixins.CheckboxAccessor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Three pages of snitches - Active, Dormant, Culled - each the same kind of scrollable table
 * {@link sh.okx.civmodern.common.gui.screen.PlayerRelationsScreen} uses. Each page can be
 * searched by snitch name or by group, and sorted by time left in the current stage (the
 * default; for culled snitches, how long ago), by name, or by distance from the player; pressing
 * the active sort again reverses it, as in {@link WaypointManagerScreen}.
 *
 * <p>A row has an eye toggle that hides the snitch from the map and minimap, a waypoint button
 * that appears while hovering the name and makes a highlighted waypoint at the snitch, and a
 * delete button. Right-clicking a group offers to filter by it. "Refresh all" pages through
 * {@code /jalist} with {@link JalistAutoPager}; "Delete all" forgets every snitch locally.
 */
public class SnitchManagerScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 16;
    private static final int TOGGLE_SIZE = 16;
    private static final Identifier TOGGLE_TEXTURE = Identifier.fromNamespaceAndPath("civmodern", "gui/visibility.png");
    private static final Identifier WAYPOINT_TEXTURE = Identifier.fromNamespaceAndPath("civmodern", "map/waypoint.png");
    private static final int WAYPOINT_BUTTON_SIZE = 16;
    private static final int DELETE_BUTTON_WIDTH = 20;
    private static final int RIGHT_MARGIN = 6;
    private static final int TIME_WIDTH = 64;
    private static final int COORDS_WIDTH = 104;
    private static final int GROUP_WIDTH = 100;
    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 4;
    private static final int TABS_Y = 24;
    private static final int SEARCH_Y = TABS_Y + 20 + ROW_GAP;
    private static final int ACTIONS_Y = SEARCH_Y + 20 + ROW_GAP;
    private static final int ROWS_TOP = ACTIONS_Y + 20 + 16;

    private static final int HEADER_COLOUR = 0xFFAAAAAA;
    private static final int NAME_COLOUR = 0xFFFFFFFF;
    private static final int UNNAMED_COLOUR = 0xFF888888;
    private static final int GROUP_COLOUR = 0xFFFFFF55;
    private static final int HIDDEN_COLOUR = 0x88AAAAAA;
    private static final int STRIPE_COLOUR = 0x14FFFFFF;
    private static final int HOVER_COLOUR = 0x33FFFFFF;
    private static final int BUTTON_COLOUR = 0x22FFFFFF;
    private static final int BUTTON_HOVER_COLOUR = 0x44FFFFFF;
    private static final int DELETE_COLOUR = 0xFFFF5555;
    private static final int MENU_BACKGROUND = 0xF0100010;
    private static final int MENU_BORDER = 0xFF5000A0;
    /** Gold, as JukeAlert prints snitch names. */
    private static final int WAYPOINT_COLOUR = 0xFFAA00;

    /** The state lists are rebuilt at most this often, as snitches age from one into the next. */
    private static final long REBUILD_MS = 1000;

    private enum SortMode {
        TIME, NAME, DISTANCE
    }

    private final Screen parent;
    private final Snitches snitches;
    private final Waypoints waypoints;
    private final JalistAutoPager pager;

    // Kept across init(), which runs again on resize and on coming back from a confirm dialog.
    private Snitch.State selected = Snitch.State.ACTIVE;
    private SortMode sortMode = SortMode.TIME;
    private final Map<SortMode, Boolean> ascending = new EnumMap<>(Map.of(SortMode.TIME, true, SortMode.NAME, true, SortMode.DISTANCE, true));
    private boolean searchGroups;
    private String search = "";
    private double scroll;

    private final Map<Snitch.State, Button> tabs = new EnumMap<>(Snitch.State.class);
    private final Map<SortMode, Button> sortButtons = new EnumMap<>(SortMode.class);
    private EditBox searchBox;
    private Checkbox nameCheckbox;
    private Checkbox groupCheckbox;

    private final Map<Snitch.State, List<Snitch>> lists = new EnumMap<>(Snitch.State.class);
    private int listsVersion = -1;
    private long listsBuiltMs;

    /** The open "Filter by this group" menu's group and corner, or null when it is closed. */
    private @Nullable String menuGroup;
    private int menuX;
    private int menuY;

    public SnitchManagerScreen(Screen parent, Snitches snitches, Waypoints waypoints, JalistAutoPager pager) {
        super(Component.translatable("civmodern.screen.snitchmanager.title"));
        this.parent = parent;
        this.snitches = snitches;
        this.waypoints = waypoints;
        this.pager = pager;
    }

    @Override
    protected void init() {
        int left = tableLeft();
        int width = tableWidth();

        int tabWidth = (width - ROW_GAP * 2) / 3;
        int i = 0;
        for (Snitch.State state : Snitch.State.values()) {
            Button button = Button.builder(Component.empty(), b -> select(state))
                .pos(left + (tabWidth + ROW_GAP) * i++, TABS_Y).size(tabWidth, 20).build();
            tabs.put(state, addRenderableWidget(button));
        }

        // Search box, then the Name / Group pair acting as radio buttons.
        nameCheckbox = Checkbox.builder(Component.translatable("civmodern.screen.snitchmanager.search.name"), font)
            .selected(!searchGroups).onValueChange((box, value) -> setSearchGroups(false)).build();
        groupCheckbox = Checkbox.builder(Component.translatable("civmodern.screen.snitchmanager.search.group"), font)
            .selected(searchGroups).onValueChange((box, value) -> setSearchGroups(true)).build();
        int right = left + width;
        groupCheckbox.setPosition(right - groupCheckbox.getWidth(), SEARCH_Y);
        nameCheckbox.setPosition(groupCheckbox.getX() - ROW_GAP - nameCheckbox.getWidth(), SEARCH_Y);
        searchBox = new EditBox(font, left, SEARCH_Y, nameCheckbox.getX() - ROW_GAP * 2 - left, 20, Component.translatable("civmodern.screen.search"));
        searchBox.setHint(Component.translatable("civmodern.screen.search.hint").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(64);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            scroll = 0;
            listsVersion = -1;
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(nameCheckbox);
        addRenderableWidget(groupCheckbox);

        // Sorts, then the two whole-list actions.
        int actionWidth = (width - ROW_GAP * 4) / 5;
        int x = left;
        for (SortMode mode : SortMode.values()) {
            Button button = Button.builder(Component.empty(), b -> sortBy(mode))
                .pos(x, ACTIONS_Y).size(actionWidth, 20).build();
            sortButtons.put(mode, addRenderableWidget(button));
            x += actionWidth + ROW_GAP;
        }
        Button refresh = Button.builder(Component.translatable("civmodern.screen.snitchmanager.refresh"), b -> confirmRefresh())
            .pos(x, ACTIONS_Y).size(actionWidth, 20).build();
        Minecraft mc = Minecraft.getInstance();
        boolean overworld = mc.level != null && mc.level.dimension() == Level.OVERWORLD;
        refresh.active = overworld && !pager.isRunning();
        refresh.setTooltip(Tooltip.create(Component.translatable(overworld
            ? "civmodern.screen.snitchmanager.refresh.tooltip"
            : "civmodern.snitches.wrongdimension")));
        addRenderableWidget(refresh);
        x += actionWidth + ROW_GAP;
        addRenderableWidget(Button.builder(Component.translatable("civmodern.screen.snitchmanager.deleteall"), b -> confirmDeleteAll())
            .pos(x, ACTIONS_Y).size(actionWidth, 20).build());

        listsVersion = -1;
        rebuildLists();
        refreshButtons();
    }

    private void setSearchGroups(boolean groups) {
        // Clicking the ticked box would untick it, leaving neither; tick both back to the choice.
        searchGroups = groups;
        ((CheckboxAccessor) nameCheckbox).civmodern$setSelected(!groups);
        ((CheckboxAccessor) groupCheckbox).civmodern$setSelected(groups);
        scroll = 0;
        listsVersion = -1;
    }

    /** Right-click on a group: search for exactly that group. */
    private void filterByGroup(String group) {
        setSearchGroups(true);
        searchBox.setValue(group);
    }

    private void sortBy(SortMode mode) {
        if (sortMode == mode) {
            ascending.put(mode, !ascending.get(mode));
        } else {
            sortMode = mode;
        }
        listsVersion = -1;
        refreshButtons();
    }

    private void confirmRefresh() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                Minecraft.getInstance().setScreen(null);
                // Back to this screen, its tab, search and sort intact, once the list is replaced.
                pager.start(() -> Minecraft.getInstance().setScreen(this));
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
        refreshButtons();
    }

    private void refreshButtons() {
        for (Map.Entry<Snitch.State, Button> tab : tabs.entrySet()) {
            Snitch.State state = tab.getKey();
            tab.getValue().active = state != selected;
            tab.getValue().setMessage(Component.translatable("civmodern.screen.snitchmanager." + state.name().toLowerCase(), lists.get(state).size()));
        }
        for (Map.Entry<SortMode, Button> sort : sortButtons.entrySet()) {
            SortMode mode = sort.getKey();
            Component label = Component.translatable("civmodern.screen.snitchmanager.sort." + mode.name().toLowerCase())
                .append(ascending.get(mode) ? " ▲" : " ▼");
            sort.getValue().setMessage(mode == sortMode ? label.copy().withStyle(ChatFormatting.YELLOW) : label);
        }
    }

    /**
     * Splits the matching snitches by state and sorts them, when anything changed or they have
     * had time to age. Tab counts are of the matches, so a search shows where its hits are.
     *
     * @return whether the lists were rebuilt
     */
    private boolean rebuildLists() {
        long nowMs = System.currentTimeMillis();
        if (listsVersion == snitches.version() && nowMs - listsBuiltMs < REBUILD_MS) {
            return false;
        }
        listsVersion = snitches.version();
        listsBuiltMs = nowMs;
        Instant now = Instant.ofEpochMilli(nowMs);
        String query = SearchFilter.normalise(search);
        for (Snitch.State state : Snitch.State.values()) {
            lists.put(state, new ArrayList<>());
        }
        for (Snitch snitch : snitches.getSnitches()) {
            if (SearchFilter.matches(searchGroups ? snitch.group() : snitch.name(), query)) {
                lists.get(snitch.state(now)).add(snitch);
            }
        }
        for (Map.Entry<Snitch.State, List<Snitch>> list : lists.entrySet()) {
            Comparator<Snitch> comparator = comparator(list.getKey());
            list.getValue().sort(ascending.get(sortMode) ? comparator : comparator.reversed());
        }
        return true;
    }

    private Comparator<Snitch> comparator(Snitch.State state) {
        return switch (sortMode) {
            // Ascending is soonest to change first, or for culled snitches most recently culled.
            case TIME -> switch (state) {
                case ACTIVE -> Comparator.comparing(Snitch::dormantAt);
                case DORMANT -> Comparator.comparing(Snitch::cullAt);
                case CULLED -> Comparator.comparing(Snitch::cullAt).reversed();
            };
            // Unnamed snitches after the named ones; position keeps the order stable.
            case NAME -> Comparator.comparing((Snitch snitch) -> snitch.name().isEmpty())
                .thenComparing(Snitch::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Snitch::x)
                .thenComparing(Snitch::z)
                .thenComparing(Snitch::y);
            case DISTANCE -> {
                LocalPlayer player = Minecraft.getInstance().player;
                double px = player == null ? 0 : player.getX();
                double py = player == null ? 0 : player.getY();
                double pz = player == null ? 0 : player.getZ();
                yield Comparator.comparingDouble(snitch -> distanceSq(snitch, px, py, pz));
            }
        };
    }

    private static double distanceSq(Snitch snitch, double px, double py, double pz) {
        double dx = snitch.x() + 0.5 - px;
        double dy = snitch.y() + 0.5 - py;
        double dz = snitch.z() + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private List<Snitch> entries() {
        if (rebuildLists()) {
            refreshButtons();
        }
        return lists.get(selected);
    }

    /** Makes a waypoint at the snitch, or reuses one already there, and highlights it. */
    private void createWaypoint(Snitch snitch) {
        Waypoint waypoint = waypoints.get(snitch.x(), snitch.y(), snitch.z());
        boolean existed = waypoint != null;
        if (!existed) {
            String name = snitch.name().isEmpty()
                ? Component.translatable("civmodern.snitches.waypointname", snitch.group()).getString()
                : snitch.name();
            waypoint = waypoints.addWaypoint(new Waypoint(name, snitch.x(), snitch.y(), snitch.z(), "waypoint", WAYPOINT_COLOUR));
        }
        // Highlighting is a separate red target marker at the spot, as from the waypoint modals.
        waypoints.setTarget(new Waypoint("", waypoint.x(), waypoint.y(), waypoint.z(), "target", 0xFF0000));
        Minecraft.getInstance().getToastManager().addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            Component.translatable(existed ? "civmodern.snitches.waypoint.existing" : "civmodern.snitches.waypoint.created"),
            Component.literal(waypoint.name() + " (" + snitch.x() + ", " + snitch.y() + ", " + snitch.z() + ")")));
    }

    // ------------------------------------------------------------- geometry

    private int tableWidth() {
        return Math.min(460, this.width - 40);
    }

    private int tableLeft() {
        return (this.width - tableWidth()) / 2;
    }

    private int rowsBottom() {
        return this.height - 24;
    }

    private double maxScroll(int rowCount) {
        return Math.max(0, rowCount * ROW_HEIGHT - (rowsBottom() - ROWS_TOP));
    }

    private int toggleLeft() {
        return tableLeft() + 3;
    }

    private int nameLeft() {
        return toggleLeft() + TOGGLE_SIZE + 6;
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

    /** The waypoint button sits at the right end of the name cell, shown while it is hovered. */
    private int waypointButtonLeft(int right) {
        return groupLeft(right) - COLUMN_GAP - WAYPOINT_BUTTON_SIZE;
    }

    private static boolean within(double value, int start, int size) {
        return value >= start && value < start + size;
    }

    // ------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(guiGraphics);

        List<Snitch> list = entries();
        int left = tableLeft();
        int right = left + tableWidth();
        int top = ROWS_TOP;
        int bottom = rowsBottom();
        scroll = Mth.clamp(scroll, 0, maxScroll(list.size()));

        int nameLeft = nameLeft();
        int groupLeft = groupLeft(right);
        int coordsLeft = coordsLeft(right);
        int timeRight = timeRight(right);
        int deleteLeft = deleteLeft(right);
        int waypointLeft = waypointButtonLeft(right);

        guiGraphics.drawCenteredString(font, this.title, this.width / 2, 8, NAME_COLOUR);
        int headerY = top - 12;
        guiGraphics.drawString(font, Component.translatable("civmodern.screen.snitchmanager.name"), nameLeft, headerY, HEADER_COLOUR);
        guiGraphics.drawString(font, Component.translatable("civmodern.screen.snitchmanager.group"), groupLeft, headerY, HEADER_COLOUR);
        guiGraphics.drawString(font, Component.translatable("civmodern.screen.snitchmanager.coords"), coordsLeft, headerY, HEADER_COLOUR);
        Component timeHeader = Component.translatable("civmodern.screen.snitchmanager.time." + selected.name().toLowerCase());
        guiGraphics.drawString(font, timeHeader, timeRight - font.width(timeHeader), headerY, HEADER_COLOUR);
        guiGraphics.fill(left, top - 3, right, top - 2, HEADER_COLOUR);

        if (list.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable(search.isBlank()
                    ? "civmodern.screen.snitchmanager.empty" : "civmodern.screen.snitchmanager.nomatch"),
                this.width / 2, (top + bottom) / 2 - font.lineHeight / 2, HEADER_COLOUR);
            return;
        }

        Instant now = Instant.now();
        int hoveredRow = menuGroup != null ? -1 : rowAt(mouseX, mouseY);
        List<Component> tooltip = null;

        guiGraphics.enableScissor(left, top, right, bottom);
        // Only the rows in view are drawn, so a long list costs no more than a short one.
        int first = Math.max(0, (int) (scroll / ROW_HEIGHT));
        int last = Math.min(list.size(), first + (bottom - top) / ROW_HEIGHT + 2);
        for (int i = first; i < last; i++) {
            int rowY = top + i * ROW_HEIGHT - (int) scroll;
            boolean rowHovered = i == hoveredRow;
            if (rowHovered) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, HOVER_COLOUR);
            } else if (i % 2 == 1) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, STRIPE_COLOUR);
            }

            Snitch snitch = list.get(i);
            boolean hidden = snitches.isHidden(snitch);
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            int buttonY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;

            // The same eye as the waypoint manager's, from its native 20x20 down to 16.
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TOGGLE_TEXTURE, toggleLeft(), rowY + (ROW_HEIGHT - TOGGLE_SIZE) / 2,
                0, hidden ? 20 : 0, TOGGLE_SIZE, TOGGLE_SIZE, 20, 20, 20, 40, -1);
            if (rowHovered && within(mouseX, toggleLeft(), TOGGLE_SIZE)) {
                tooltip = List.of(Component.translatable(hidden ? "civmodern.screen.snitchmanager.show" : "civmodern.screen.snitchmanager.hide"));
            }

            boolean nameHovered = rowHovered && within(mouseX, nameLeft, groupLeft - COLUMN_GAP - nameLeft);
            int nameRight = (nameHovered ? waypointLeft - 4 : groupLeft - COLUMN_GAP);
            String name = snitch.name().isEmpty()
                ? Component.translatable("civmodern.snitches.unnamed").getString()
                : snitch.name();
            int nameColour = hidden ? HIDDEN_COLOUR : snitch.name().isEmpty() ? UNNAMED_COLOUR : NAME_COLOUR;
            String shownName = font.plainSubstrByWidth(name, nameRight - nameLeft);
            guiGraphics.drawString(font, shownName, nameLeft, textY, nameColour);
            if (nameHovered) {
                boolean onButton = within(mouseX, waypointLeft, WAYPOINT_BUTTON_SIZE);
                // The cell narrows to make room for the button, so a name can be cut off only while hovered.
                if (!onButton && shownName.length() < name.length()) {
                    tooltip = List.of(Component.literal(name));
                }
                guiGraphics.fill(waypointLeft - 1, buttonY, waypointLeft + WAYPOINT_BUTTON_SIZE + 1, buttonY + BUTTON_HEIGHT,
                    onButton ? BUTTON_HOVER_COLOUR : BUTTON_COLOUR);
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WAYPOINT_TEXTURE, waypointLeft, buttonY, 0, 0,
                    WAYPOINT_BUTTON_SIZE, WAYPOINT_BUTTON_SIZE, 16, 16, 0xFF000000 | WAYPOINT_COLOUR);
                if (onButton) {
                    tooltip = List.of(Component.translatable("civmodern.screen.snitchmanager.waypoint"));
                }
            }

            String shownGroup = font.plainSubstrByWidth(snitch.group(), GROUP_WIDTH);
            guiGraphics.drawString(font, shownGroup, groupLeft, textY, hidden ? HIDDEN_COLOUR : GROUP_COLOUR);
            if (rowHovered && within(mouseX, groupLeft, GROUP_WIDTH)) {
                Component hint = Component.translatable("civmodern.screen.snitchmanager.group.tooltip").withStyle(ChatFormatting.GRAY);
                // A cut-off group gets its full name above the hint.
                tooltip = shownGroup.length() < snitch.group().length()
                    ? List.of(Component.literal(snitch.group()), hint)
                    : List.of(hint);
            }
            int numberColour = hidden ? HIDDEN_COLOUR : NAME_COLOUR;
            guiGraphics.drawString(font, font.plainSubstrByWidth(snitch.x() + ", " + snitch.y() + ", " + snitch.z(), COORDS_WIDTH), coordsLeft, textY, numberColour);
            String time = timeText(snitch, now);
            guiGraphics.drawString(font, time, timeRight - font.width(time), textY, numberColour);

            drawRowButton(guiGraphics, deleteLeft, buttonY, DELETE_BUTTON_WIDTH, Component.literal("X"),
                rowHovered && within(mouseX, deleteLeft, DELETE_BUTTON_WIDTH), DELETE_COLOUR);
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

        if (tooltip != null) {
            guiGraphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    /** The table is drawn behind the widgets; the group menu goes over everything. */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        if (menuGroup != null) {
            int width = menuWidth();
            int height = menuHeight();
            guiGraphics.fill(menuX - 1, menuY - 1, menuX + width + 1, menuY + height + 1, MENU_BORDER);
            guiGraphics.fill(menuX, menuY, menuX + width, menuY + height, MENU_BACKGROUND);
            if (onMenu(mouseX, mouseY)) {
                guiGraphics.fill(menuX, menuY, menuX + width, menuY + height, HOVER_COLOUR);
            }
            guiGraphics.drawString(font, menuLabel(), menuX + 4, menuY + 4, NAME_COLOUR);
        }
    }

    private Component menuLabel() {
        return Component.translatable("civmodern.screen.snitchmanager.filtergroup");
    }

    private int menuWidth() {
        return font.width(menuLabel()) + 8;
    }

    private int menuHeight() {
        return font.lineHeight + 7;
    }

    private boolean onMenu(double mouseX, double mouseY) {
        return within(mouseX, menuX, menuWidth()) && within(mouseY, menuY, menuHeight());
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
            || mouseY < ROWS_TOP || mouseY >= rowsBottom()) {
            return -1;
        }
        return (int) ((mouseY - ROWS_TOP + scroll) / ROW_HEIGHT);
    }

    // ------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        // An open menu takes the next click, whether on its one entry or anywhere else to dismiss it.
        if (menuGroup != null) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && onMenu(event.x(), event.y())) {
                filterByGroup(menuGroup);
            }
            menuGroup = null;
            return true;
        }
        if (super.mouseClicked(event, bl)) {
            return true;
        }
        int row = rowAt(event.x(), event.y());
        List<Snitch> list = entries();
        if (row < 0 || row >= list.size()) {
            return false;
        }
        Snitch snitch = list.get(row);
        int right = tableLeft() + tableWidth();
        double mouseX = event.x();

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (within(mouseX, groupLeft(right), GROUP_WIDTH)) {
                menuGroup = snitch.group();
                menuX = (int) Math.min(mouseX, this.width - menuWidth() - 2);
                menuY = (int) Math.min(event.y(), this.height - menuHeight() - 2);
                return true;
            }
            return false;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (within(mouseX, toggleLeft(), TOGGLE_SIZE)) {
            snitches.setHidden(snitch, !snitches.isHidden(snitch));
            return true;
        }
        if (within(mouseX, waypointButtonLeft(right), WAYPOINT_BUTTON_SIZE)) {
            createWaypoint(snitch);
            return true;
        }
        if (within(mouseX, deleteLeft(right), DELETE_BUTTON_WIDTH)) {
            snitches.delete(snitch);
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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && menuGroup != null) {
            menuGroup = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

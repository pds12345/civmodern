package sh.okx.civmodern.common.map.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.map.waypoints.Waypoints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every waypoint in one scrollable table — name, position, and the coloured diamond the map
 * draws — sorted alphabetically or by distance from the player, toggled with the buttons above
 * the table. Clicking a row opens the same edit modal the map screen uses, and the table is
 * re-read from {@link Waypoints} every frame, so edits and deletes show at once.
 */
public class WaypointManagerScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int ICON_COLUMN = 26;
    private static final int X_COLUMN = 52;
    private static final int Y_COLUMN = 44;
    private static final int Z_COLUMN = 52;

    private static final int HEADER_COLOUR = 0xFFAAAAAA;
    private static final int NAME_COLOUR = 0xFFFFFFFF;
    private static final int NUMBER_COLOUR = 0xFFCCCCCC;
    private static final int STRIPE_COLOUR = 0x14FFFFFF;
    private static final int HOVER_COLOUR = 0x33FFFFFF;

    private static final int SORT_BUTTON_Y = 24;
    private static final int SORT_BUTTON_HEIGHT = 20;
    private static final int HEADER_ROW_Y = 56;

    private enum SortMode {
        ALPHA, DISTANCE
    }

    private final Screen parent;
    private final Waypoints waypoints;

    private EditWaypointModal editModal;
    private double scroll;

    private SortMode sortMode = SortMode.ALPHA;
    private boolean alphaAscending = true;
    private boolean distanceAscending = true;
    private Button distanceButton;
    private Button alphaButton;

    public WaypointManagerScreen(Screen parent, Waypoints waypoints) {
        super(Component.translatable("civmodern.screen.waypoints.title"));
        this.parent = parent;
        this.waypoints = waypoints;
    }

    @Override
    protected void init() {
        editModal = new EditWaypointModal(waypoints);
        // There is no map behind this screen to pick coordinates from.
        editModal.setCoordsPickerEnabled(false);
        addRenderableWidget(editModal);

        int buttonWidth = (tableWidth() - 4) / 2;
        int left = tableLeft();
        distanceButton = Button.builder(Component.empty(), button -> {
            if (sortMode == SortMode.DISTANCE) {
                distanceAscending = !distanceAscending;
            } else {
                sortMode = SortMode.DISTANCE;
            }
            updateSortButtons();
        }).pos(left, SORT_BUTTON_Y).size(buttonWidth, SORT_BUTTON_HEIGHT).build();
        addRenderableWidget(distanceButton);

        alphaButton = Button.builder(Component.empty(), button -> {
            if (sortMode == SortMode.ALPHA) {
                alphaAscending = !alphaAscending;
            } else {
                sortMode = SortMode.ALPHA;
            }
            updateSortButtons();
        }).pos(left + buttonWidth + 4, SORT_BUTTON_Y).size(buttonWidth, SORT_BUTTON_HEIGHT).build();
        addRenderableWidget(alphaButton);

        updateSortButtons();
    }

    private void updateSortButtons() {
        distanceButton.setMessage(sortButtonLabel("Distance", sortMode == SortMode.DISTANCE, distanceAscending));
        alphaButton.setMessage(sortButtonLabel("Alpha", sortMode == SortMode.ALPHA, alphaAscending));
    }

    private static Component sortButtonLabel(String title, boolean active, boolean ascending) {
        Component text = Component.literal(title + " " + (ascending ? "▲" : "▼"));
        return active ? text.copy().withStyle(ChatFormatting.YELLOW) : text;
    }

    private List<Waypoint> sortedWaypoints() {
        List<Waypoint> list = new ArrayList<>(waypoints.getWaypoints());
        if (sortMode == SortMode.DISTANCE) {
            LocalPlayer player = Minecraft.getInstance().player;
            double px = player == null ? 0 : player.getX();
            double py = player == null ? 0 : player.getY();
            double pz = player == null ? 0 : player.getZ();
            Comparator<Waypoint> comparator = Comparator.comparingDouble(w -> distanceSq(w, px, py, pz));
            list.sort(distanceAscending ? comparator : comparator.reversed());
        } else {
            Comparator<Waypoint> comparator = Comparator.comparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Waypoint::x)
                .thenComparing(Waypoint::z)
                .thenComparing(Waypoint::y);
            list.sort(alphaAscending ? comparator : comparator.reversed());
        }
        return list;
    }

    private static double distanceSq(Waypoint waypoint, double px, double py, double pz) {
        double dx = waypoint.x() + 0.5 - px;
        double dy = waypoint.y() - py;
        double dz = waypoint.z() + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------- geometry

    private int tableWidth() {
        return Math.min(440, this.width - 40);
    }

    private int tableLeft() {
        return (this.width - tableWidth()) / 2;
    }

    private int rowsTop() {
        return 70;
    }

    private int rowsBottom() {
        return this.height - 24;
    }

    private double maxScroll(int rowCount) {
        return Math.max(0, rowCount * ROW_HEIGHT - (rowsBottom() - rowsTop()));
    }

    // ------------------------------------------------------------- rendering

    /**
     * The table is drawn here rather than in {@link #render}: the screen renders its background
     * before its widgets, so this is what keeps every row underneath the edit modal.
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(guiGraphics);

        List<Waypoint> list = sortedWaypoints();
        int left = tableLeft();
        int tableRight = left + tableWidth();
        int top = rowsTop();
        int bottom = rowsBottom();
        scroll = Mth.clamp(scroll, 0, maxScroll(list.size()));

        guiGraphics.drawCenteredString(font, this.title, this.width / 2, 12, NAME_COLOUR);

        // Column edges, right to left. Numbers are right-aligned to these, spreadsheet style.
        int zRight = tableRight - 6;
        int yRight = zRight - Z_COLUMN;
        int xRight = yRight - Y_COLUMN;
        int nameLeft = left + ICON_COLUMN;
        int nameRight = xRight - X_COLUMN;

        guiGraphics.drawString(font, "Name", nameLeft, HEADER_ROW_Y, HEADER_COLOUR);
        guiGraphics.drawString(font, "X", xRight - font.width("X"), HEADER_ROW_Y, HEADER_COLOUR);
        guiGraphics.drawString(font, "Y", yRight - font.width("Y"), HEADER_ROW_Y, HEADER_COLOUR);
        guiGraphics.drawString(font, "Z", zRight - font.width("Z"), HEADER_ROW_Y, HEADER_COLOUR);
        guiGraphics.fill(left, top - 3, tableRight, top - 2, HEADER_COLOUR);

        if (list.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("civmodern.screen.waypoints.empty"),
                this.width / 2, (top + bottom) / 2 - font.lineHeight / 2, HEADER_COLOUR);
            return;
        }

        int hovered = editModal.isVisible() ? -1 : rowAt(mouseX, mouseY);

        guiGraphics.enableScissor(left, top, tableRight, bottom);
        for (int i = 0; i < list.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - (int) scroll;
            if (rowY + ROW_HEIGHT <= top || rowY >= bottom) {
                continue;
            }
            if (i == hovered) {
                guiGraphics.fill(left, rowY, tableRight, rowY + ROW_HEIGHT, HOVER_COLOUR);
            } else if (i % 2 == 1) {
                guiGraphics.fill(left, rowY, tableRight, rowY + ROW_HEIGHT, STRIPE_COLOUR);
            }

            Waypoint waypoint = list.get(i);
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;

            // The very diamond the map draws: same texture, tinted with the waypoint's colour.
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, waypoint.resourceLocation(),
                left + 5, rowY + 2, 0, 0, 16, 16, 16, 16, 0xFF000000 | waypoint.colour());

            String name = font.plainSubstrByWidth(waypoint.name(), nameRight - nameLeft - 8);
            guiGraphics.drawString(font, name, nameLeft, textY, NAME_COLOUR);

            String x = Integer.toString(waypoint.x());
            String y = Integer.toString(waypoint.y());
            String z = Integer.toString(waypoint.z());
            guiGraphics.drawString(font, x, xRight - font.width(x), textY, NUMBER_COLOUR);
            guiGraphics.drawString(font, y, yRight - font.width(y), textY, NUMBER_COLOUR);
            guiGraphics.drawString(font, z, zRight - font.width(z), textY, NUMBER_COLOUR);
        }
        guiGraphics.disableScissor();

        int content = list.size() * ROW_HEIGHT;
        int viewport = bottom - top;
        if (content > viewport) {
            int barHeight = Math.max(10, viewport * viewport / content);
            int barY = top + (int) ((viewport - barHeight) * (scroll / maxScroll(list.size())));
            guiGraphics.fill(tableRight - 2, top, tableRight, bottom, STRIPE_COLOUR);
            guiGraphics.fill(tableRight - 2, barY, tableRight, barY + barHeight, 0x88FFFFFF);
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
        // While the modal is up it owns the screen; clicks beside it do not reach the rows.
        if (editModal.isVisible() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        int row = rowAt(event.x(), event.y());
        List<Waypoint> list = sortedWaypoints();
        if (row < 0 || row >= list.size()) {
            return false;
        }
        editModal.setWaypoint(list.get(row));
        editModal.setVisible(true);
        // Rows are not widgets, so nothing else claims the click's focus; direct is safe here.
        setFocused(editModal);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        scroll = Mth.clamp(scroll - scrollY * ROW_HEIGHT, 0, maxScroll(sortedWaypoints().size()));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && editModal.isVisible()) {
            editModal.setVisible(false);
            editModal.setWaypoint(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

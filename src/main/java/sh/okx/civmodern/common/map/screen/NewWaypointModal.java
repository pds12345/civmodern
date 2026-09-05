package sh.okx.civmodern.common.map.screen;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import sh.okx.civmodern.common.gui.widget.ColourTextEditBox;
import sh.okx.civmodern.common.gui.widget.HsbColourPicker;
import net.minecraft.client.input.MouseButtonEvent;
import sh.okx.civmodern.common.gui.widget.ImageButton;
import sh.okx.civmodern.common.gui.widget.VisibilityToggleButton;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.map.waypoints.Waypoints;

import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class NewWaypointModal extends Modal<FlowLayout> {

    private static final int MODAL_WIDTH = 200;
    private static final int MODAL_HEIGHT = 120;

    private final Waypoints waypoints;

    private TextBoxComponent xBox;
    private TextBoxComponent yBox;
    private TextBoxComponent zBox;
    private ColourTextEditBox colourBox;

    private TextBoxComponent nameBox;

    private Button doneButton;
    private Button cancelButton;
    private ImageButton highlightButton;
    private VisibilityToggleButton visibilityButton;

    private HsbColourPicker colourPicker;
    private int colour = 0xFF0000;
    private int previewColour = colour;
    // Whether the waypoint being built will be created visible; consumed by done(), since there
    // is no persisted Waypoint yet for the toggle to act on directly.
    private boolean createVisible = true;

    private boolean targeting = false;

    private Runnable onDone;
    private Runnable onCancel;
    private boolean coordsPickerEnabled = true;

    public NewWaypointModal(Waypoints waypoints) {
        super(OwoUIAdapter.createWithoutScreen(Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - MODAL_WIDTH / 2, 48, MODAL_WIDTH, MODAL_HEIGHT, UIContainers::verticalFlow));
        super.layout.rootComponent.allowOverflow(true);
        this.waypoints = waypoints;
    }

    public void setOnDone(Runnable onDone) {
        this.onDone = onDone;
    }

    /** Called after the modal has hidden itself in response to the cancel button. */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    /** The target-style picker only makes sense when a map is behind the modal. */
    public void setCoordsPickerEnabled(boolean coordsPickerEnabled) {
        this.coordsPickerEnabled = coordsPickerEnabled;
    }

    public void open(String name, int x, int y, int z) {
        // This instance is shared across several entry points; a stale callback from a previous
        // open() must not fire for one that never asked for it.
        this.onDone = null;
        this.onCancel = null;
        this.createVisible = true;

        // Random hue at full saturation/brightness, so every waypoint starts vivid rather than red.
        this.colour = Mth.hsvToRgb(ThreadLocalRandom.current().nextFloat(), 1.0f, 1.0f) & 0xFFFFFF;
        this.previewColour = this.colour;

        Pattern inputFilter = Pattern.compile("^-?[0-9]*$");
        Predicate<String> numFilter = s -> inputFilter.matcher(s).matches();

        doneButton = Button.builder(CommonComponents.GUI_DONE, button -> {
            this.done();
        }).build();
        cancelButton = Button.builder(CommonComponents.GUI_CANCEL, button -> {
            this.cancel();
        }).build();
        highlightButton = new ImageButton(0, 0, 20, 20, Identifier.fromNamespaceAndPath("civmodern", "gui/target.png"), imbg -> {
            if (isHighlighted()) {
                this.waypoints.setTarget(null);
            } else {
                try {
                    this.waypoints.setTarget(new Waypoint("", getX(), getY(), getZ(), "target", 0xFF0000));
                } catch (NumberFormatException ignored) {
                }
            }
            updateHighlightButton();
        });
        updateHighlightButton();
        visibilityButton = new VisibilityToggleButton(0, 0, 20, 20, () -> this.createVisible, btn -> this.createVisible = !this.createVisible);
        ImageButton moveButton = new ImageButton(0, 0, 20, 20, Identifier.fromNamespaceAndPath("civmodern", "gui/move.png"), imbg -> {
            this.visible = false;
            this.targeting = true;
        });
        ImageButton copyButton = new ImageButton(0, 0, 20, 20, Identifier.fromNamespaceAndPath("civmodern", "gui/copy.png"), imbg -> {
            StringBuilder builder = new StringBuilder("[");
            if (!this.nameBox.getValue().isBlank()) {
                builder.append("name:%s,".formatted(this.nameBox.getValue()));
            }
            builder.append("x:%s,y:%s,z:%s]".formatted(this.xBox.getValue(), this.yBox.getValue(), this.zBox.getValue()));
            Minecraft.getInstance().keyboardHandler.setClipboard(builder.toString());
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("civmodern.map.copy", Component.literal(builder.toString())).withColor(0x379FA3), false);
        });
        // Nothing is persisted yet, so there is nothing to delete - discard the in-progress
        // waypoint the same way Cancel does.
        ImageButton deleteButton = new ImageButton(0, 0, 20, 20, Identifier.fromNamespaceAndPath("civmodern", "gui/delete.png"), imbg -> {
            this.cancel();
        });

        xBox = UIComponents.textBox(Sizing.fixed(50), Integer.toString(x));
        xBox.setFilter(numFilter);
        xBox.onChanged().subscribe(value -> this.updateDone());
        yBox = UIComponents.textBox(Sizing.fixed(32), Integer.toString(y));
        yBox.setFilter(numFilter);
        yBox.onChanged().subscribe(value -> this.updateDone());
        zBox = UIComponents.textBox(Sizing.fixed(50), Integer.toString(z));
        zBox.setFilter(numFilter);
        zBox.onChanged().subscribe(value -> this.updateDone());

        colourBox = new ColourTextEditBox(Sizing.fixed(55), () -> colour, c -> {
            this.colour = c;
            this.previewColour = c;
        });

        colourPicker = new HsbColourPicker(
            0,
            0,
            20,
            20,
            this.colour,
            (colour) -> {
                colourBox.setColourFromInt(colour);
                this.colour = colour;
                this.previewColour = colour;
            },
            preview -> {
                this.previewColour = Objects.requireNonNullElse(preview, colour);
            },
            () -> {
            }
        );
        colourPicker.setRVisible(false);

        nameBox = UIComponents.textBox(Sizing.expand(), name);

        this.layout.rootComponent.clearChildren();
        this.layout.rootComponent
            .child(
                UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(24))
                    .child(UIComponents.label(Component.literal("Name")).margins(Insets.right(8)))
                    .child(nameBox)
                    .verticalAlignment(VerticalAlignment.CENTER)
                    .margins(Insets.horizontal(4).withTop(4))
            )
            .child(
                UIContainers.horizontalFlow(Sizing.fill(), Sizing.content())
                    .child(buildCoordsRow())
                    .margins(Insets.horizontal(4).withTop(4))
            )
            .child(
                UIContainers.horizontalFlow(Sizing.fill(), Sizing.content())
                    .child(buildButtonsRow(highlightButton, visibilityButton, moveButton, copyButton, deleteButton))
                    .margins(Insets.horizontal(4).withTop(4))
            )
            .child(
                UIContainers.horizontalFlow(Sizing.fill(), Sizing.content())
                    .child(doneButton.horizontalSizing(Sizing.fixed(45)).margins(Insets.right(3).withTop(1)).positioning(Positioning.relative(0, 0)))
                    .child(cancelButton.horizontalSizing(Sizing.fixed(45)).margins(Insets.right(3).withTop(1)))
                    .child(colourBox)
                    .child(colourPicker.margins(Insets.top(1).withLeft(2)))
                    .horizontalAlignment(HorizontalAlignment.RIGHT)
                    .margins(Insets.horizontal(4).withTop(4)))
            .surface(Surface.DARK_PANEL)
            .padding(Insets.of(6));

        this.layout.inflateAndMount();
        colourBox.moveCursorToStart(false);
        focusNameBox();
    }

    /** The X/Y/Z labels and boxes, inline on one row, the same right-margin as everywhere else in the modal. */
    private ParentUIComponent buildCoordsRow() {
        return UIContainers.horizontalFlow(Sizing.content(), Sizing.content())
            .child(UIComponents.label(Component.literal("X")).margins(Insets.right(4)))
            .child(xBox.margins(Insets.right(6)))
            .child(UIComponents.label(Component.literal("Y")).margins(Insets.right(4)))
            .child(yBox.margins(Insets.right(6)))
            .child(UIComponents.label(Component.literal("Z")).margins(Insets.right(4)))
            .child(zBox)
            .verticalAlignment(VerticalAlignment.CENTER);
    }

    /** The action buttons, on their own row between the coordinates and Done/Cancel/colour. */
    private FlowLayout buildButtonsRow(ImageButton highlightButton, VisibilityToggleButton visibilityButton, ImageButton moveButton, ImageButton copyButton, ImageButton deleteButton) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content())
            .child(highlightButton.margins(Insets.right(3)))
            .child(visibilityButton.margins(Insets.right(3)));
        if (coordsPickerEnabled) {
            row.child(moveButton.margins(Insets.right(3)));
        } else {
            // No map behind this screen to pick coordinates from (see QuickWaypointScreen) -
            // leave the move button's slot blank instead of letting copy/delete shift into it,
            // so the row still lines up with the map screen's version of this modal.
            row.child(UIContainers.horizontalFlow(Sizing.fixed(20), Sizing.fixed(20)).margins(Insets.right(3)));
        }
        row.child(copyButton.margins(Insets.right(3)))
            .child(deleteButton);
        return row;
    }

    /** Whether the position currently in the fields is the map's current highlight. */
    private boolean isHighlighted() {
        Waypoint target = this.waypoints.getTarget();
        if (target == null) {
            return false;
        }
        try {
            return target.x() == getX() && target.y() == getY() && target.z() == getZ();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Keeps the highlight button's toggled look in sync, since the highlight can also be
     * cleared elsewhere (e.g. the map's right-click "Clear highlighted waypoint" menu).
     */
    private void updateHighlightButton() {
        highlightButton.setToggled(isHighlighted());
    }

    /**
     * owo-internal focus only; the hosting screen must also make this modal its focused
     * child (setFocused) or keystrokes never reach it.
     */
    public void focusNameBox() {
        if (nameBox != null) {
            this.layout.rootComponent.focusHandler().focus(nameBox, UIComponent.FocusSource.KEYBOARD_CYCLE);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (visible && highlightButton != null) {
            updateHighlightButton();
        }
        super.render(guiGraphics, mouseX, mouseY, delta);
        if (visible) {
            this.colourPicker.setRVisible(true);
            this.colourPicker.renderWidget(guiGraphics, mouseX, mouseY, delta);
            this.colourPicker.setRVisible(false);
        }
    }

    @Override
    public boolean isMouseOver(double d, double e) {
        if (!visible) {
            return false;
        }
        if (super.isMouseOver(d, e)) {
            return true;
        }
        return this.colourPicker.isMouseOver(d, e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (!visible) {
            return false;
        }
        return this.colourPicker.mouseClicked(event, bl) || super.mouseClicked(event, bl);
    }

    @Override
    public void mouseMoved(double d, double e) {
        if (!visible) {
            return;
        }
        this.colourPicker.mouseMoved(d, e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double f, double g) {
        if (!visible) {
            return false;
        }
        if (this.colourPicker.mouseDragged(event, f, g)) {
            return true;
        }
        return super.mouseDragged(event, f, g);
    }

    public void updateDone() {
        try {
            Integer.parseInt(this.xBox.getValue());
            Integer.parseInt(this.yBox.getValue());
            Integer.parseInt(this.zBox.getValue());

            this.doneButton.active = true;
        } catch (NumberFormatException ex) {
            this.doneButton.active = false;
        }
    }

    public void done() {
        try {
            int x = Integer.parseInt(this.xBox.getValue());
            int y = Integer.parseInt(this.yBox.getValue());
            int z = Integer.parseInt(this.zBox.getValue());
            waypoints.addWaypoint(new Waypoint(this.nameBox.getValue(), x, y, z, "waypoint", this.colour, this.createVisible));
            setVisible(false);
            if (this.onDone != null) {
                this.onDone.run();
            }
        } catch (NumberFormatException ignored) {

        }
    }

    public void cancel() {
        setVisible(false);
        if (this.onCancel != null) {
            this.onCancel.run();
        }
    }

    public boolean isTargeting() {
        return targeting;
    }

    public void setTargetResult(int x, int y, int z) {
        this.xBox.setValue(Integer.toString(x));
        this.yBox.setValue(Integer.toString(y));
        this.zBox.setValue(Integer.toString(z));
        this.targeting = false;
        this.visible = true;
    }

    public int getX() {
        return Integer.parseInt(this.xBox.getValue());
    }

    public int getZ() {
        return Integer.parseInt(this.zBox.getValue());
    }

    public int getY() {
        return Integer.parseInt(this.yBox.getValue());
    }

    public int getPreviewColour() {
        return previewColour;
    }
}

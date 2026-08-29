package sh.okx.civmodern.common.map.screen;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.map.waypoints.Waypoints;

import java.util.ArrayList;
import java.util.List;

/** The click-menu for the single highlighted waypoint dropped via "Highlight position" / the target button. */
public class HighlightContextMenu extends Modal<FlowLayout> {

    private final Waypoints waypoints;
    private final NewWaypointModal newWaypointModal;
    private final Runnable focusNewWaypointModal;
    private final List<ScalableLabelComponent> options = new ArrayList<>();

    public HighlightContextMenu(Waypoints waypoints, NewWaypointModal newWaypointModal, Runnable focusNewWaypointModal) {
        super(OwoUIAdapter.createWithoutScreen(0, 0, 132, 25, UIContainers::verticalFlow));
        this.waypoints = waypoints;
        this.newWaypointModal = newWaypointModal;
        this.focusNewWaypointModal = focusNewWaypointModal;
    }

    public void open(Waypoint highlight, int x, int y) {
        this.options.clear();
        this.layout.rootComponent.clearChildren();

        ScalableLabelComponent clear = new ScalableLabelComponent(Component.literal("Clear highlighted waypoint"), c -> {
            this.setVisible(false);
            this.waypoints.setTarget(null);
        });
        options.add(clear);
        ScalableLabelComponent convert = new ScalableLabelComponent(Component.literal("Convert to permanent waypoint"), c -> {
            this.setVisible(false);
            this.newWaypointModal.open("", highlight.x(), highlight.y(), highlight.z());
            // Only clear the highlight once the waypoint is actually created, so cancelling
            // leaves it in place.
            this.newWaypointModal.setOnDone(() -> this.waypoints.setTarget(null));
            this.newWaypointModal.setVisible(true);
            this.focusNewWaypointModal.run();
        });
        options.add(convert);

        this.layout.rootComponent
            .child(clear.textHeight(7).margins(Insets.of(1, 0, 2, 2)))
            .child(UIComponents.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofRgb(0x60605f)).margins(Insets.of(1, 2, 0, 0)))
            .child(convert.textHeight(7).margins(Insets.horizontal(2)))
            .padding(Insets.both(2, 3))
            .surface(Surface.TOOLTIP);

        int boxWidth = this.layout.width();
        int boxHeight = this.layout.height();
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int clampedX = Math.max(2, Math.min(x, screenWidth - boxWidth - 2));
        int clampedY = Math.max(2, Math.min(y, screenHeight - boxHeight - 2));

        this.layout.moveAndResize(clampedX, clampedY, boxWidth, boxHeight);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (!visible) {
            return false;
        }
        for (ScalableLabelComponent component : options) {
            if (component.onMouseClick(event.x(), event.y(), event.button())) {
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }
}

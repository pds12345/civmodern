package sh.okx.civmodern.common.map.screen;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
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

public class PositionContextMenu extends Modal<FlowLayout> {

    private final Waypoints waypoints;
    private final NewWaypointModal newWaypointModal;
    private final Runnable focusNewWaypointModal;
    private final List<ScalableLabelComponent> options = new ArrayList<>();

    public PositionContextMenu(Waypoints waypoints, NewWaypointModal newWaypointModal, Runnable focusNewWaypointModal) {
        super(OwoUIAdapter.createWithoutScreen(0, 0, 80, 46, UIContainers::verticalFlow));
        this.waypoints = waypoints;
        this.newWaypointModal = newWaypointModal;
        this.focusNewWaypointModal = focusNewWaypointModal;
    }

    public void open(int targetX, Short targetY, int targetZ, int x, int z) {
        this.options.clear();
        this.layout.rootComponent.clearChildren();
        ScalableLabelComponent createWaypoint = new ScalableLabelComponent(Component.literal("Create waypoint"), c -> {
            this.setVisible(false);
            newWaypointModal.open("", targetX, targetY != null ? targetY + 2 : Minecraft.getInstance().player.getBlockY() + 1, targetZ);
            newWaypointModal.setVisible(true);
            focusNewWaypointModal.run();
        });
        options.add(createWaypoint);
        ScalableLabelComponent teleportHere = new ScalableLabelComponent(Component.literal("Teleport here").withColor(targetY == null ? 0xff777777 : 0xffffffff), c -> {
            if (targetY != null) {
                Minecraft.getInstance().setScreen(null);
                Minecraft.getInstance().player.connection.sendCommand("teleport " + targetX + " " + (targetY + 1) + " " + targetZ);
            }
        });
        options.add(teleportHere);
        ScalableLabelComponent highlightPosition = new ScalableLabelComponent(Component.literal("Highlight position"), c -> {
            this.setVisible(false);
            this.waypoints.setTarget(new Waypoint("", targetX, targetY == null ? 64 : targetY, targetZ, "target", 0xFF0000));
        });
        options.add(highlightPosition);
        ScalableLabelComponent copyToClipboard = new ScalableLabelComponent(Component.literal("Copy to clipboard").withColor(targetY == null ? 0xff777777 : 0xffffffff), c -> {
            if (targetY != null) {
                Minecraft.getInstance().setScreen(null);
                String copied = "[x:%s,y:%s,z:%s]".formatted(targetX, targetY, targetZ);
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("civmodern.map.copy", Component.literal(copied)).withColor(0x379FA3), false);
                Minecraft.getInstance().keyboardHandler.setClipboard(copied);
            }
        });
        options.add(copyToClipboard);
        this.layout.rootComponent
            .child(createWaypoint.textHeight(7).margins(Insets.of(1, 0, 2, 2)))
            .child(UIComponents.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofRgb(0x60605f)).margins(Insets.of(1, 2, 0, 0)))
            .child(teleportHere.hoverEffect(targetY != null).textHeight(7).margins(Insets.horizontal(2)))
            .child(UIComponents.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofRgb(0x60605f)).margins(Insets.of(1, 2, 0, 0)))
            .child(highlightPosition.textHeight(7).margins(Insets.horizontal(2)))
            .child(UIComponents.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofRgb(0x60605f)).margins(Insets.of(1, 2, 0, 0)))
            .child(copyToClipboard.hoverEffect(targetY != null).textHeight(7).margins(Insets.horizontal(2)))
            .padding(Insets.both(2, 3))
            .surface(Surface.TOOLTIP);

        int boxWidth = this.layout.width();
        int boxHeight = this.layout.height();
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int clampedX = Math.max(2, Math.min(x, screenWidth - boxWidth - 2));
        int clampedZ = Math.max(2, Math.min(z, screenHeight - boxHeight - 2));

        this.layout.moveAndResize(clampedX, clampedZ, boxWidth, boxHeight);
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

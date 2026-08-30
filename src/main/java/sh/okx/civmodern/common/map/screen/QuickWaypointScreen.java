package sh.okx.civmodern.common.map.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import sh.okx.civmodern.common.map.waypoints.Waypoints;

/**
 * The map screen's new-waypoint modal, shown directly over the game so a waypoint can be
 * dropped at the player's feet without opening the map. Closes as soon as the waypoint is
 * created (or on ESC).
 */
public class QuickWaypointScreen extends Screen {

    private final Waypoints waypoints;

    public QuickWaypointScreen(Waypoints waypoints) {
        super(Component.translatable("civmodern.screen.newwaypoint.title"));
        this.waypoints = waypoints;
    }

    @Override
    protected void init() {
        NewWaypointModal modal = new NewWaypointModal(waypoints);
        modal.setCoordsPickerEnabled(false);
        LocalPlayer player = Minecraft.getInstance().player;
        modal.open("", player.getBlockX(), player.getBlockY() + 1, player.getBlockZ());
        modal.setOnDone(this::onClose);
        modal.setOnCancel(this::onClose);
        modal.setVisible(true);
        addRenderableWidget(modal);
        // Route keyboard input to the modal from the start, so typing works without a click.
        // setInitialFocus would be the idiomatic call, but it needs the widget to implement
        // nextFocusPath(), which Modal doesn't — so set the focused child directly.
        setFocused(modal);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // Just the dark translucent tint, not the vanilla blur — the game stays visible behind it.
        renderTransparentBackground(guiGraphics);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

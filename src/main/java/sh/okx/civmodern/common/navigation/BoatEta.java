package sh.okx.civmodern.common.navigation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec2;
import sh.okx.civmodern.common.map.snitches.SnitchRenderer;
import sh.okx.civmodern.common.mixins.AbstractBoatAccessor;

import java.time.Duration;
import java.util.List;

/**
 * Estimated time of arrival along the auto-boat route, for the HUD.
 *
 * <p>Only while the route is being followed in a boat that is on the water: the moment the route
 * ends, the player leaves the boat, or the boat runs aground, there is nothing to estimate and
 * {@link #lines(AutoNavigation)} is empty. The estimate assumes the boat's ordinary speed on water
 * and a straight line for each leg, so it reads a little low around detours and a lot low on ice.
 */
public final class BoatEta {

    /** A boat's cruising speed on open water, in blocks per second. */
    public static final double BOAT_SPEED = 8.0;

    private BoatEta() {
    }

    /**
     * The lines to show, top to bottom: the time to the next route point and to the last one, or
     * just one "ETA" line when they are the same point. Empty when there is nothing to show.
     */
    public static List<Component> lines(AutoNavigation navigation) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || navigation.getDestinations().isEmpty() || !(player.getVehicle() instanceof AbstractBoat boat)) {
            return List.of();
        }
        AbstractBoat.Status status = ((AbstractBoatAccessor) boat).civmodern$getStatus();
        if (status != AbstractBoat.Status.IN_WATER && status != AbstractBoat.Status.UNDER_WATER
            && status != AbstractBoat.Status.UNDER_FLOWING_WATER) {
            return List.of();
        }

        // Each leg as the crow flies, from the boat through every queued point in turn.
        double fromX = boat.getX();
        double fromZ = boat.getZ();
        double nextLeg = -1;
        double total = 0;
        for (Vec2 destination : navigation.getDestinations()) {
            double leg = Math.hypot(destination.x - fromX, destination.y - fromZ);
            if (nextLeg < 0) {
                nextLeg = leg;
            }
            total += leg;
            fromX = destination.x;
            fromZ = destination.y;
        }

        if (navigation.getDestinations().size() == 1) {
            return List.of(Component.translatable("civmodern.boat.eta", format(nextLeg)));
        }
        return List.of(
            Component.translatable("civmodern.boat.eta.next", format(nextLeg)),
            Component.translatable("civmodern.boat.eta.final", format(total))
        );
    }

    private static String format(double blocks) {
        return SnitchRenderer.formatRemaining(Duration.ofSeconds(Math.round(blocks / BOAT_SPEED)));
    }
}

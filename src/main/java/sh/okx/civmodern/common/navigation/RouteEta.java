package sh.okx.civmodern.common.navigation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec2;
import sh.okx.civmodern.common.map.snitches.SnitchRenderer;
import sh.okx.civmodern.common.mixins.AbstractBoatAccessor;

import java.time.Duration;
import java.util.List;

/**
 * Estimated time of arrival along the auto-navigation route, for the HUD.
 *
 * <p>Only while the route is being followed in a water vehicle that is actually in the water: the
 * moment the route ends, the player dismounts, or the vehicle is out of the water, there is
 * nothing to estimate and {@link #lines(AutoNavigation)} is empty. The estimate assumes the
 * vehicle's ordinary cruising speed and a straight line for each leg, so it reads a little low
 * around detours and, for a boat, a lot low on ice.
 */
public final class RouteEta {

    /** A boat's cruising speed on open water, in blocks per second. */
    public static final double BOAT_SPEED = 8.0;
    /** A ridden nautilus's cruising speed underwater, in blocks per second, dashes aside. */
    public static final double NAUTILUS_SPEED = 11.0;

    private RouteEta() {
    }

    /**
     * The speed to estimate with for the player's current vehicle, or 0 when there is none, it is
     * not a water vehicle, or it is out of the water. A boat's own afloat status is used, since its
     * physics keeps that exact; a nautilus is simply in water or not.
     */
    private static double cruisingSpeed(Entity vehicle) {
        if (vehicle instanceof AbstractBoat boat) {
            AbstractBoat.Status status = ((AbstractBoatAccessor) boat).civmodern$getStatus();
            boolean afloat = status == AbstractBoat.Status.IN_WATER || status == AbstractBoat.Status.UNDER_WATER
                || status == AbstractBoat.Status.UNDER_FLOWING_WATER;
            return afloat ? BOAT_SPEED : 0;
        }
        if (vehicle instanceof AbstractNautilus nautilus) {
            return nautilus.isInWater() ? NAUTILUS_SPEED : 0;
        }
        return 0;
    }

    /**
     * The lines to show, top to bottom: the time to the next route point and to the last one, or
     * just one "ETA" line when they are the same point. Empty when there is nothing to show.
     */
    public static List<Component> lines(AutoNavigation navigation) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || navigation.getDestinations().isEmpty() || player.getVehicle() == null) {
            return List.of();
        }
        Entity vehicle = player.getVehicle();
        double speed = cruisingSpeed(vehicle);
        if (speed <= 0) {
            return List.of();
        }

        // Each leg as the crow flies, from the vehicle through every queued point in turn.
        double fromX = vehicle.getX();
        double fromZ = vehicle.getZ();
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
            return List.of(Component.translatable("civmodern.route.eta", format(nextLeg, speed)));
        }
        return List.of(
            Component.translatable("civmodern.route.eta.next", format(nextLeg, speed)),
            Component.translatable("civmodern.route.eta.final", format(total, speed))
        );
    }

    private static String format(double blocks, double speed) {
        return SnitchRenderer.formatRemaining(Duration.ofSeconds(Math.round(blocks / speed)));
    }
}

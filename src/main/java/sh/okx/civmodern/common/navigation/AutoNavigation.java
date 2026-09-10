package sh.okx.civmodern.common.navigation;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.events.ClientTickEvent;
import sh.okx.civmodern.common.events.WorldRenderLastEvent;

import java.util.ArrayDeque;
import java.util.Deque;

public class AutoNavigation {

    // number of ticks to hold the jump key for a nautilus dash - holding for
    // roughly 9-11 ticks (0.45-0.55s) yields the maximum charge (see
    // AbstractNautilus#getPlayerJumpPendingScale / LocalPlayer#aiStep jump-riding logic)
    private static final int DASH_CHARGE_TICKS = 10;

    private Deque<Vec2> destinations = new ArrayDeque<>();
    private int dashChargeTicks = -1;

    public AutoNavigation(AbstractCivModernMod mod) {
        mod.eventBus.register(this);
    }

    public void addDestination(Vec2 destination) {
        this.destinations.add(destination);
    }

    public Deque<Vec2> getDestinations() {
        return destinations;
    }

    public void reset() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        this.destinations.clear();
        rotation = 0;
        dashChargeTicks = -1;
    }

    private double rotation = 0;

    @Subscribe
    public void tick(WorldRenderLastEvent event) {
        if (destinations.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            this.destinations.clear();
            rotation = 0;
            return;
        }

        boolean isValidVehicle = (
                player.getVehicle() instanceof HappyGhast || player.getVehicle() instanceof AbstractHorse
                        || player.getVehicle() instanceof Boat || player.getVehicle() instanceof AbstractNautilus
        );
        if (!isValidVehicle) {
            reset();
            return;
        }

        // TODO when user hits a key then stop macro (maybe add button to resume previous route)
        // TODO shadow? when showing preview route
        // TODO shift click - show this on the gui instead of queuing
        Vec2 destination = destinations.peek();
        double distanceRemaining = Mth.lengthSquared(player.getVehicle().getX() - destination.x, player.getVehicle().getZ() - destination.y);

        if (player.getVehicle() instanceof AbstractBoat boat) {
            if (distanceRemaining < 5 * 5) {
                mc.options.keyUp.setDown(false);
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
                rotation = 0;
                destinations.poll();
                if ((destination = destinations.peek()) == null) {
                    return;
                }
            }

            float yRot = boat.getYRot();
            float yRotRadians = (float) Math.toRadians(yRot);
            if (yRotRadians < 0) {
                yRotRadians += 2 * Mth.PI;
            }
            double target = Mth.atan2(-destination.x + player.getX(), destination.y - player.getZ());
            double diff = yRotRadians - target;
            while (diff < 0) {
                diff += 2 * Mth.PI;
            }
            while (diff > 2 * Mth.PI) {
                diff -= 2 * Mth.PI;
            }
            double remainingMovement = Math.toRadians((Math.abs(rotation) + 1.1) * 10);
            rotation *= 0.9;
            if (diff < remainingMovement || Math.PI * 2 - diff < remainingMovement) {
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
            } else if (diff < Mth.PI) {
                rotation--;
                mc.options.keyLeft.setDown(true);
                mc.options.keyRight.setDown(false);
            } else {
                rotation++;
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(true);
            }
            mc.options.keyUp.setDown(true);
        } else {
            if (distanceRemaining < 3 * 3) {
                // Look in the direction of the destination
                destinations.poll();
                if (destinations.peek() == null) {
                    reset();
                    return;
                }
            } else {
                 Vec3 destinationVec3 = new Vec3(destination.x, player.getEyeY(), destination.y);

                 float turnAlpha = 0.17f * event.tickDelta();
                 double destinationDistance = destinationVec3.subtract(player.getEyePosition(event.tickDelta())).length();
                 Vec3 turnStart = player.getEyePosition(event.tickDelta()).add(player.getLookAngle().multiply(destinationDistance, 0, destinationDistance));
                 Vec3 turnCurrent = turnStart.lerp(destinationVec3, turnAlpha);

                 player.lookAt(EntityAnchorArgument.Anchor.EYES, turnCurrent);
                 player.getVehicle().lookAt(EntityAnchorArgument.Anchor.EYES, turnCurrent);
            }
            mc.options.keyUp.setDown(true);
        }
    }

    // Automatically charges and releases the jump key to trigger a nautilus's dash
    // as soon as it comes off cooldown, keeping travel speed near-maximal.
    @Subscribe
    public void tickDash(ClientTickEvent event) {
        if (destinations.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (!(player.getVehicle() instanceof AbstractNautilus nautilus) || !nautilus.canJump()) {
            if (dashChargeTicks >= 0) {
                mc.options.keyJump.setDown(false);
                dashChargeTicks = -1;
            }
            return;
        }

        if (dashChargeTicks < 0) {
            if (nautilus.getJumpCooldown() == 0) {
                mc.options.keyJump.setDown(true);
                dashChargeTicks = 0;
            }
        } else if (++dashChargeTicks >= DASH_CHARGE_TICKS) {
            mc.options.keyJump.setDown(false);
            dashChargeTicks = -1;
        }
    }
}

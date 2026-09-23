package sh.okx.civmodern.common.mixins;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the boat's own notion of whether it is afloat, which its physics keeps up to date. */
@Mixin(AbstractBoat.class)
public interface AbstractBoatAccessor {
    @Invoker("getStatus")
    AbstractBoat.Status civmodern$getStatus();
}

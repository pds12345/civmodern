package sh.okx.civmodern.common.mixins;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.okx.civmodern.common.AbstractCivModernMod;

// Every container click, the player's and the snitch auto-pager's alike, so the /jalist capture
// knows the screen is showing the client's guess at the result until the server answers.
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void onHandleInventoryMouseClick(int containerId, int slot, int button, ClickType clickType, Player player, CallbackInfo ci) {
        AbstractCivModernMod.getInstance().getWorldListener().getSnitchCapture().onClick(containerId, slot);
    }
}

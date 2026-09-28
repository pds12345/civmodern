package sh.okx.civmodern.common.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.events.DeathEvent;

/**
 * The server sends the combat-kill packet for every death of the local player, whether or not the
 * death screen is shown (immediate respawn included), so it is the one reliable death signal.
 * Injected at TAIL: the handler re-dispatches itself from the network thread to the render thread
 * and only reaches the tail on the latter, so this fires exactly once, on the render thread.
 */
@Mixin(ClientPacketListener.class)
public class DeathMixin {
    @Inject(method = "handlePlayerCombatKill", at = @At("TAIL"))
    private void onPlayerCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && packet.playerId() == player.getId()) {
            AbstractCivModernMod.getInstance().eventBus.post(new DeathEvent(player.blockPosition()));
        }
    }
}

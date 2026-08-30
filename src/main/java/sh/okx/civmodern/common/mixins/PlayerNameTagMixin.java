package sh.okx.civmodern.common.mixins;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.radar.PlayerRelation;
import sh.okx.civmodern.common.radar.PlayerRelations;

/**
 * Tints a player's floating nametag the same friendly/hostile colour the radar uses. Neutral and
 * unlisted players are left alone so any existing (e.g. team) colouring still applies.
 */
@Mixin(EntityRenderer.class)
public class PlayerNameTagMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void civmodern$recolourPlayerName(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (!(entity instanceof Player player)) {
            return;
        }

        PlayerRelations relations = AbstractCivModernMod.getInstance().getWorldListener().getPlayerRelations();
        if (relations == null) {
            return;
        }

        PlayerRelation relation = relations.getRelation(player.getScoreboardName());
        if (relation == PlayerRelation.NEUTRAL) {
            return;
        }

        Component original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        int rgb = relation.colour() & 0xFFFFFF;
        cir.setReturnValue(original.copy().withStyle(style -> style.withColor(rgb)));
    }
}

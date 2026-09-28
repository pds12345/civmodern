package sh.okx.civmodern.common.events;

import net.minecraft.core.BlockPos;

/** The local player died at {@code pos} (their block position when the kill packet arrived). */
public record DeathEvent(BlockPos pos) {
}

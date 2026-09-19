package sh.okx.civmodern.common.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * The entity the map and minimap should follow.
 * <p>
 * Normally this is the local player. While spectating another player (server {@code /spectate},
 * or attaching to an entity in spectator mode) the server only moves the spectator's entity on
 * its side and swaps the client's <em>camera</em> entity to the target, so the client-side
 * {@link Minecraft#player} stays wherever the spectate began. Following the camera entity keeps
 * the map on what the screen is actually showing.
 */
public final class MapFocus {
    private MapFocus() {
    }

    /**
     * @return the camera entity when one is set, otherwise the local player; {@code null} only
     * when there is no player (not in a world).
     */
    public static Entity entity() {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        return camera != null ? camera : mc.player;
    }
}

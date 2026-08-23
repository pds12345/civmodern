/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;

public interface ClientRenderCallback {

    /**
     * Invoked just before the client's window enters the 'Render' phase, after the client
     * has ticked and cleared the render task queue
     */
    Event<ClientRenderCallback> BEFORE = EventFactory.createArrayBacked(ClientRenderCallback.class, callbacks -> (client) -> {
        for (var callback : callbacks) {
            callback.onRender(client);
        }
    });

    Event<ClientRenderCallback> BEFORE_SWAP = EventFactory.createArrayBacked(ClientRenderCallback.class, callbacks -> (client) -> {
        for (var callback : callbacks) {
            callback.onRender(client);
        }
    });

    /**
     * Called just after the client has finished rendering and drawing the
     * current frame and swapped buffers
     */
    Event<ClientRenderCallback> AFTER = EventFactory.createArrayBacked(ClientRenderCallback.class, callbacks -> (client) -> {
        for (var callback : callbacks) {
            callback.onRender(client);
        }
    });

    void onRender(Minecraft client);
}

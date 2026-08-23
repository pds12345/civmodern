/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.event;

import sh.okx.civmodern.common.ui.observable.EventStream;
import net.minecraft.client.input.MouseButtonEvent;

public interface MouseDrag {
    boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY);

    static EventStream<MouseDrag> newStream() {
        return new EventStream<>(subscribers -> (click, deltaX, deltaY) -> {
            var anyTriggered = false;
            for (var subscriber : subscribers) {
                anyTriggered |= subscriber.onMouseDrag(click, deltaX, deltaY);
            }
            return anyTriggered;
        });
    }
}

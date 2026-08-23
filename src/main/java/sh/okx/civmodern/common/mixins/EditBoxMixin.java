/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.mixins;

import sh.okx.civmodern.common.mixins.TextBoxComponentAccessor;
import sh.okx.civmodern.common.ui.inject.GreedyInputUIComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class EditBoxMixin extends AbstractWidget implements GreedyInputUIComponent {

    public EditBoxMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "onValueChange", at = @At("HEAD"))
    private void callOwoListener(String newText, CallbackInfo ci) {
        if (!(this instanceof TextBoxComponentAccessor accessor)) return;
        accessor.civmodern$textValue().set(newText);
    }

    @Override
    public void onFocusGained(FocusSource source) {
        super.onFocusGained(source);
        this.setFocused(true);
    }

}

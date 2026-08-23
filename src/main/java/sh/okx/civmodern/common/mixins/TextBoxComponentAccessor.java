/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.mixins;

import sh.okx.civmodern.common.ui.component.TextBoxComponent;
import sh.okx.civmodern.common.ui.observable.Observable;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// now you might think that simply AW'ing onChanged in TextFieldWidget
// would be the way to go about this. but you see, tiny remapper (or more specifically how
// loom uses it) begs to differ and simply does not remap your override, causing
// that approach to break in prod. thus we need to mix into TextFieldWidget
// and use this accessor to update it instead
@ApiStatus.Internal
@Mixin(TextBoxComponent.class)
public interface TextBoxComponentAccessor {

    @Accessor("textValue")
    Observable<String> civmodern$textValue();

}

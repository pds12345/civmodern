package sh.okx.civmodern.common.mixins;

import net.minecraft.client.gui.components.Checkbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Vanilla checkboxes only change when clicked; a pair that acts as radio buttons must also be
// set from code, to untick the other one.
@Mixin(Checkbox.class)
public interface CheckboxAccessor {
    @Accessor("selected")
    void civmodern$setSelected(boolean selected);
}

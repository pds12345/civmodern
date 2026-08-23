/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.component;

import sh.okx.civmodern.common.ui.container.UIContainers;
import sh.okx.civmodern.common.ui.container.FlowLayout;
import sh.okx.civmodern.common.ui.core.Sizing;
import sh.okx.civmodern.common.ui.core.UIComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.model.Material;

// TODO paginated and tabbed containers

/**
 * Utility methods for creating UI components
 */
public final class UIComponents {

    private UIComponents() {}

    // -----------------------
    // Wrapped Vanilla Widgets
    // -----------------------

    public static ButtonComponent button(Component message, Consumer<ButtonComponent> onPress) {
        return new ButtonComponent(message, onPress);
    }

    public static TextBoxComponent textBox(Sizing horizontalSizing) {
        return new TextBoxComponent(horizontalSizing);
    }

    public static TextBoxComponent textBox(Sizing horizontalSizing, String text) {
        var textBox = new TextBoxComponent(horizontalSizing);
        textBox.text(text);
        return textBox;
    }

    // ------------------
    // Default Components
    // ------------------

    public static LabelComponent label(net.minecraft.network.chat.Component text) {
        return new LabelComponent(text);
    }

    public static BoxComponent box(Sizing horizontalSizing, Sizing verticalSizing) {
        return new BoxComponent(horizontalSizing, verticalSizing);
    }

    public static SpacerComponent spacer(int percent) {
        return new SpacerComponent(percent);
    }

    public static SpacerComponent spacer() {
        return spacer(100);
    }

    // -------
    // Utility
    // -------

    public static <T, C extends UIComponent> FlowLayout list(List<T> data, Consumer<FlowLayout> layoutConfigurator, Function<T, C> componentMaker, boolean vertical) {
        var layout = vertical ? UIContainers.verticalFlow(Sizing.content(), Sizing.content()) : UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        layoutConfigurator.accept(layout);

        for (var value : data) {
            layout.child(componentMaker.apply(value));
        }

        return layout;
    }

    public static VanillaWidgetComponent wrapVanillaWidget(AbstractWidget widget) {
        return new VanillaWidgetComponent(widget);
    }

    public static <T extends UIComponent> T createWithSizing(Supplier<T> componentMaker, Sizing horizontalSizing, Sizing verticalSizing) {
        var component = componentMaker.get();
        component.sizing(horizontalSizing, verticalSizing);
        return component;
    }

}

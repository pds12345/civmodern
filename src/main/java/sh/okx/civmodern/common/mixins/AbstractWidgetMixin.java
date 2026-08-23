/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.mixins;

import sh.okx.civmodern.common.ui.component.UIComponents;
import sh.okx.civmodern.common.ui.component.VanillaWidgetComponent;
import sh.okx.civmodern.common.ui.core.*;
import sh.okx.civmodern.common.ui.event.*;
import sh.okx.civmodern.common.ui.inject.UIComponentStub;
import sh.okx.civmodern.common.ui.util.FocusHandler;
import sh.okx.civmodern.common.ui.observable.EventSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

@SuppressWarnings("ConstantConditions")
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin implements UIComponentStub, net.minecraft.client.gui.components.events.GuiEventListener {

    @Shadow public boolean active;

    @Shadow protected boolean isHovered;

    @Unique
    protected VanillaWidgetComponent wrapper = null;

    @Override
    public void inflate(Size space) {
        this.civmodern$getWrapper().inflate(space);
    }

    @Override
    public void mount(ParentUIComponent parent, int x, int y) {
        this.civmodern$getWrapper().mount(parent, x, y);
    }

    @Override
    public void dismount(DismountReason reason) {
        this.civmodern$getWrapper().dismount(reason);
    }

    @Nullable
    @Override
    public ParentUIComponent parent() {
        return this.civmodern$getWrapper().parent();
    }

    @Override
    public @Nullable FocusHandler focusHandler() {
        return this.civmodern$getWrapper().focusHandler();
    }

    @Override
    public UIComponent positioning(Positioning positioning) {
        this.civmodern$getWrapper().positioning(positioning);
        return this;
    }

    @Override
    public AnimatableProperty<Positioning> positioning() {
        return this.civmodern$getWrapper().positioning();
    }

    @Override
    public UIComponent margins(Insets margins) {
        this.civmodern$getWrapper().margins(margins);
        return this;
    }

    @Override
    public AnimatableProperty<Insets> margins() {
        return this.civmodern$getWrapper().margins();
    }

    @Override
    public UIComponent horizontalSizing(Sizing horizontalSizing) {
        this.civmodern$getWrapper().horizontalSizing(horizontalSizing);
        return this;
    }

    @Override
    public UIComponent verticalSizing(Sizing verticalSizing) {
        this.civmodern$getWrapper().verticalSizing(verticalSizing);
        return this;
    }

    @Override
    public AnimatableProperty<Sizing> horizontalSizing() {
        return this.civmodern$getWrapper().horizontalSizing();
    }

    @Override
    public AnimatableProperty<Sizing> verticalSizing() {
        return this.civmodern$getWrapper().verticalSizing();
    }

    @Override
    public EventSource<MouseDown> mouseDown() {
        return this.civmodern$getWrapper().mouseDown();
    }

    @Override
    public int x() {
        return this.civmodern$getWrapper().x();
    }

    @Override
    public int y() {
        return this.civmodern$getWrapper().y();
    }

    @Override
    public int width() {
        return this.civmodern$getWrapper().width();
    }

    @Override
    public int height() {
        return this.civmodern$getWrapper().height();
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.civmodern$getWrapper().draw(graphics, mouseX, mouseY, partialTicks, delta);
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return this.civmodern$getWrapper().shouldDrawTooltip(mouseX, mouseY);
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        this.civmodern$getWrapper().update(delta, mouseX, mouseY);
        this.cursorStyle(this.active ? this.civmodern$preferredCursorStyle() : CursorStyle.POINTER);
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        return this.civmodern$getWrapper().onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        return this.civmodern$getWrapper().onMouseUp(click);
    }

    @Override
    public EventSource<MouseUp> mouseUp() {
        return this.civmodern$getWrapper().mouseUp();
    }

    @Override
    public EventSource<MouseScroll> mouseScroll() {
        return this.civmodern$getWrapper().mouseScroll();
    }

    @Override
    public EventSource<MouseDrag> mouseDrag() {
        return this.civmodern$getWrapper().mouseDrag();
    }

    @Override
    public EventSource<KeyPress> keyPress() {
        return this.civmodern$getWrapper().keyPress();
    }

    @Override
    public EventSource<CharTyped> charTyped() {
        return this.civmodern$getWrapper().charTyped();
    }

    @Override
    public EventSource<FocusGained> focusGained() {
        return this.civmodern$getWrapper().focusGained();
    }

    @Override
    public EventSource<FocusLost> focusLost() {
        return this.civmodern$getWrapper().focusLost();
    }

    @Override
    public EventSource<MouseEnter> mouseEnter() {
        return this.civmodern$getWrapper().mouseEnter();
    }

    @Override
    public EventSource<MouseLeave> mouseLeave() {
        return this.civmodern$getWrapper().mouseLeave();
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        return this.civmodern$getWrapper().onMouseScroll(mouseX, mouseY, amount);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        return this.civmodern$getWrapper().onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        return this.civmodern$getWrapper().onKeyPress(input);
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        return this.civmodern$getWrapper().onCharTyped(input);
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override
    public void onFocusGained(FocusSource source) {
        this.setFocused(source == FocusSource.KEYBOARD_CYCLE);
        this.civmodern$getWrapper().onFocusGained(source);
    }

    @Override
    public void onFocusLost() {
        this.setFocused(false);
        this.civmodern$getWrapper().onFocusLost();
    }

    @Override
    public <C extends UIComponent> C configure(Consumer<C> closure) {
        return this.civmodern$getWrapper().configure(closure);
    }

    @Override
    public CursorStyle cursorStyle() {
        return this.civmodern$getWrapper().cursorStyle();
    }

    @Override
    public UIComponent cursorStyle(CursorStyle style) {
        return this.civmodern$getWrapper().cursorStyle(style);
    }

    @Override
    public UIComponent tooltip(List<ClientTooltipComponent> tooltip) {
        return this.civmodern$getWrapper().tooltip(tooltip);
    }

    @Override
    public List<ClientTooltipComponent> tooltip() {
        return this.civmodern$getWrapper().tooltip();
    }

    @Override
    public UIComponent id(@Nullable String id) {
        this.civmodern$getWrapper().id(id);
        return this;
    }

    @Override
    public @Nullable String id() {
        return this.civmodern$getWrapper().id();
    }

    @Unique
    protected VanillaWidgetComponent civmodern$getWrapper() {
        if (this.wrapper == null) {
            this.wrapper = UIComponents.wrapVanillaWidget((AbstractWidget) (Object) this);
        }

        return this.wrapper;
    }

    @Override
    public @Nullable VanillaWidgetComponent widgetWrapper() {
        return this.wrapper;
    }

    @Override
    public int xOffset() {
        return 0;
    }

    @Override
    public int yOffset() {
        return 0;
    }

    @Override
    public int widthOffset() {
        return 0;
    }

    @Override
    public int heightOffset() {
        return 0;
    }

    @Inject(method = "setWidth", at = @At("HEAD"), cancellable = true)
    private void applyWidthToWrapper(int width, CallbackInfo ci) {
        var wrapper = this.wrapper;
        if (wrapper != null) {
            wrapper.horizontalSizing(Sizing.fixed(width));
            ci.cancel();
        }
    }

    @Override
    public void updateX(int x) {
        this.civmodern$getWrapper().updateX(x);
    }

    @Override
    public void updateY(int y) {
        this.civmodern$getWrapper().updateY(y);
    }

    protected CursorStyle civmodern$preferredCursorStyle() {
        return CursorStyle.POINTER;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void setHovered(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.wrapper != null) this.isHovered = this.isHovered && this.wrapper.hovered();
    }
}

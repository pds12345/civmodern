/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.component;

import sh.okx.civmodern.common.ui.CivModernUI;
import sh.okx.civmodern.common.mixins.AbstractWidgetAccessor;
import sh.okx.civmodern.common.mixins.ButtonAccessor;
import sh.okx.civmodern.common.ui.core.Color;
import sh.okx.civmodern.common.ui.core.CursorStyle;
import sh.okx.civmodern.common.ui.core.OwoUIGraphics;
import sh.okx.civmodern.common.ui.core.Sizing;
import sh.okx.civmodern.common.ui.util.NinePatchTexture;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;

public class ButtonComponent extends Button {

    public static final Identifier ACTIVE_TEXTURE = CivModernUI.id("button/active");
    public static final Identifier HOVERED_TEXTURE = CivModernUI.id("button/hovered");
    public static final Identifier DISABLED_TEXTURE = CivModernUI.id("button/disabled");

    protected Renderer renderer = Renderer.VANILLA;
    protected boolean textShadow = true;

    protected ButtonComponent(Component message, Consumer<ButtonComponent> onPress) {
        super(0, 0, 0, 0, message, button -> onPress.accept((ButtonComponent) button), Button.DEFAULT_NARRATION);
        this.sizing(Sizing.content());
    }

    @Override
    public void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderer.draw((OwoUIGraphics) context, this, delta);

        var textRenderer = Minecraft.getInstance().font;
        int color = this.active ? 0xffffffff : 0xffa0a0a0;

        if (this.textShadow) {
            context.drawCenteredString(textRenderer, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
        } else {
            context.drawString(textRenderer, this.getMessage(), (int) (this.getX() + this.width / 2f - textRenderer.width(this.getMessage()) / 2f), (int) (this.getY() + (this.height - 8) / 2f), color, false);
        }

        var tooltip = ((AbstractWidgetAccessor) this).civmodern$getTooltip();
        if (this.isHovered && tooltip.get() != null)
            context.setTooltipForNextFrame(textRenderer, tooltip.get().toCharSequence(Minecraft.getInstance()), DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false);
    }

    public ButtonComponent onPress(Consumer<ButtonComponent> onPress) {
        ((ButtonAccessor) this).civmodern$setOnPress(button -> onPress.accept((ButtonComponent) button));
        return this;
    }

    public ButtonComponent renderer(Renderer renderer) {
        this.renderer = renderer;
        return this;
    }

    public Renderer renderer() {
        return this.renderer;
    }

    public ButtonComponent textShadow(boolean textShadow) {
        this.textShadow = textShadow;
        return this;
    }

    public boolean textShadow() {
        return this.textShadow;
    }

    public ButtonComponent active(boolean active) {
        this.active = active;
        return this;
    }

    public boolean active() {
        return this.active;
    }

    protected CursorStyle civmodern$preferredCursorStyle() {
        return CursorStyle.HAND;
    }

    @FunctionalInterface
    public interface Renderer {
        Renderer VANILLA = (matrices, button, delta) -> {
            var texture = button.active
                    ? button.isHovered ? HOVERED_TEXTURE : ACTIVE_TEXTURE
                    : DISABLED_TEXTURE;
            NinePatchTexture.draw(texture, matrices, button.getX(), button.getY(), button.width, button.height);
        };

        static Renderer flat(int color, int hoveredColor, int disabledColor) {
            return (context, button, delta) -> {
                if (button.active) {
                    if (button.isHovered) {
                        context.fill(button.getX(), button.getY(), button.getX() + button.width, button.getY() + button.height, hoveredColor);
                    } else {
                        context.fill(button.getX(), button.getY(), button.getX() + button.width, button.getY() + button.height, color);
                    }
                } else {
                    context.fill(button.getX(), button.getY(), button.getX() + button.width, button.getY() + button.height, disabledColor);
                }
            };
        }

        static Renderer texture(Identifier texture, int u, int v, int textureWidth, int textureHeight) {
            return (context, button, delta) -> {
                int renderV = v;
                if (!button.active) {
                    renderV += button.height * 2;
                } else if (button.isHovered()) {
                    renderV += button.height;
                }

                context.blit(RenderPipelines.GUI_TEXTURED, texture, button.getX(), button.getY(), u, renderV, button.width, button.height, textureWidth, textureHeight);
            };
        }

        void draw(OwoUIGraphics context, ButtonComponent button, float delta);

    }
}

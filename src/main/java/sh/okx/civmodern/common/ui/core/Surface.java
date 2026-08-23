/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.core;

import sh.okx.civmodern.common.ui.util.NinePatchTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public interface Surface {

    Surface BLANK = (context, component) -> {};

    Surface PANEL = (context, component) -> {
        context.drawPanel(component.x(), component.y(), component.width(), component.height(), false);
    };

    Surface DARK_PANEL = (context, component) -> {
        context.drawPanel(component.x(), component.y(), component.width(), component.height(), true);
    };

    Surface PANEL_INSET = (context, component) -> {
        NinePatchTexture.draw(OwoUIGraphics.PANEL_INSET_NINE_PATCH_TEXTURE, context, component);
    };

    Surface VANILLA_TRANSLUCENT = (context, component) -> {
        context.drawGradientRect(
            component.x(), component.y(), component.width(), component.height(),
            0xC0101010, 0xC0101010, 0xD0101010, 0xD0101010
        );
    };

    Surface TOOLTIP = tooltip(null);

    static Surface tooltip(@Nullable Identifier texture) {
        return (context, component) -> {
            TooltipRenderUtil.renderTooltipBackground(context, component.x() + 4, component.y() + 4, component.width() - 8, component.height() - 8, texture);
        };
    }

    static Surface flat(int color) {
        return (context, component) -> context.fill(component.x(), component.y(), component.x() + component.width(), component.y() + component.height(), color);
    }

    static Surface outline(int color) {
        return (context, component) -> context.drawRectOutline(component.x(), component.y(), component.width(), component.height(), color);
    }

    static Surface tiled(Identifier texture, int textureWidth, int textureHeight) {
        return (context, component) -> {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, component.x(), component.y(), 0, 0, component.width(), component.height(), textureWidth, textureHeight);
        };
    }

    static Surface panelWithInset(int insetWidth) {
        return Surface.PANEL.and((context, component) -> {
            NinePatchTexture.draw(
                OwoUIGraphics.PANEL_INSET_NINE_PATCH_TEXTURE,
                context,
                component.x() + insetWidth,
                component.y() + insetWidth,
                component.width() - insetWidth * 2,
                component.height() - insetWidth * 2
            );
        });
    }

    void draw(OwoUIGraphics context, ParentUIComponent component);

    default Surface and(Surface surface) {
        return (context, component) -> {
            this.draw(context, component);
            surface.draw(context, component);
        };
    }

}

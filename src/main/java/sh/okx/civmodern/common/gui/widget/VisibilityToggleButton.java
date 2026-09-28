package sh.okx.civmodern.common.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/**
 * The same hide/show eye icon used next to each row in the waypoint manager list - an open eye
 * while visible, an eye with a slash through it while hidden - scaled to whatever size the
 * widget is constructed at rather than always drawn at the source texture's native 20x20.
 */
public class VisibilityToggleButton extends AbstractWidget {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("civmodern", "gui/visibility.png");

    private final BooleanSupplier visible;
    private final OnPress onPress;

    public VisibilityToggleButton(int x, int y, int width, int height, BooleanSupplier visible, OnPress onPress) {
        super(x, y, width, height, Component.empty());
        this.visible = visible;
        this.onPress = onPress;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int v = visible.getAsBoolean() ? 0 : 20;
        // (x, y, u, v, destWidth, destHeight, uWidth, vHeight, texWidth, texHeight, colour) -
        // separate dest size from the 20x20 source region lets this scale to any widget size.
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 0, v, this.width, this.height, 20, 20, 20, 40, -1);
        // The icon has no hovered frame of its own, so keyboard focus needs an explicit ring.
        if (this.isFocused()) {
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean bl) {
        onPress.onPress(this);
    }

    /** Space or Enter toggles a focused button, as vanilla's buttons do. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isActive() && ImageButton.isActivation(event)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            onPress.onPress(this);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    public interface OnPress {
        void onPress(VisibilityToggleButton button);
    }
}

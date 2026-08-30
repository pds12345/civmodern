package sh.okx.civmodern.common.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class ImageButton extends AbstractWidget {

    private Identifier image;
    private final OnPress onPress;
    private boolean toggled = false;

    public ImageButton(int x, int y, int width, int height, Identifier image, OnPress onPress) {
        super(x, y, width, height, Component.empty());
        this.image = image;
        this.onPress = onPress;
    }

    public void setImage(Identifier image) {
        this.image = image;
    }

    /** Forces the hovered-frame look, e.g. to show the button's state is currently active. */
    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        int k = this.toggled || this.isHoveredOrFocused() ? 1 : 0;
        // Tint by the widget's inherited alpha (setAlpha), so a button can render its icon faded.
        int tint = Math.round(Mth.clamp(this.alpha, 0f, 1f) * 255f) << 24 | 0xFFFFFF;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED,  image, this.getX(), this.getY(), 0, k * 20, this.width, this.height, 20, 40, tint);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean bl) {
        this.onPress.onPress(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }

    public interface OnPress {
        void onPress(ImageButton button);
    }
}

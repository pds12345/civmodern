package sh.okx.civmodern.common.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import org.jetbrains.annotations.NotNull;

/** A one-pixel horizontal dividing line between groups of config widgets. */
public final class HorizontalRule implements Renderable {
    public int x0;
    public int x1;
    public int y;
    public final int colour;

    public HorizontalRule(int x0, int x1, int y, int colour) {
        this.x0 = x0;
        this.x1 = x1;
        this.y = y;
        this.colour = colour;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(x0, y, x1, y + 1, colour);
    }
}

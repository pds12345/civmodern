package sh.okx.civmodern.common.map.screen;

import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

public abstract class Modal<T extends ParentUIComponent> implements Renderable, GuiEventListener, NarratableEntry {
    protected final OwoUIAdapter<T> layout;
    protected boolean visible = false;

    protected Modal(OwoUIAdapter<T> layout) {
        this.layout = layout;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (visible) {
            this.layout.render(guiGraphics, mouseX, mouseY, delta);
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setFocused(boolean bl) {
        this.layout.setFocused(bl);
    }

    @Override
    public boolean isFocused() {
        return this.layout.isFocused();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (!visible) {
            return false;
        }
        return this.layout.mouseClicked(new MouseButtonEvent(event.x() - this.layout.x(), event.y() - this.layout.y(), event.buttonInfo()), bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!visible) {
            return false;
        }
        return this.layout.mouseReleased(new MouseButtonEvent(event.x() - this.layout.x(), event.y() - this.layout.y(), event.buttonInfo()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double f, double g) {
        if (!visible) {
            return false;
        }
        return this.layout.mouseDragged(new MouseButtonEvent(event.x() - this.layout.x(), event.y() - this.layout.y(), event.buttonInfo()), f, g);
    }

    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        if (!visible) {
            return false;
        }
        return this.layout.mouseScrolled(d - this.layout.x(), e - this.layout.y(), f, g);
    }

    @Override
    public boolean isMouseOver(double d, double e) {
        if (!visible) {
            return false;
        }
        return this.layout.isMouseOver(d, e);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!visible) {
            return false;
        }
        boolean handled = this.layout.keyPressed(event);
        // owo moves its own focus on Tab but reports the key as unhandled. Left at that, vanilla's
        // Screen would run its tab navigation as well and quietly hand focus to the host screen's
        // widgets, after which the modal stops receiving keys at all. Tab belongs to the modal.
        return handled || event.key() == GLFW.GLFW_KEY_TAB;
    }

    public static boolean isEnter(KeyEvent event) {
        return event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER;
    }

    /** Enter in any of {@code fields} acts like the Done button, as in a web form. */
    protected static void submitOnEnter(Runnable done, UIComponent... fields) {
        for (UIComponent field : fields) {
            field.keyPress().subscribe(event -> {
                if (isEnter(event)) {
                    done.run();
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!visible) {
            return false;
        }
        return this.layout.charTyped(event);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return this.layout.narrationPriority();
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
        this.layout.updateNarration(narrationElementOutput);
    }
}

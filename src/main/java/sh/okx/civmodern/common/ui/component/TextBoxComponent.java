/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.component;

import sh.okx.civmodern.common.mixins.EditBoxAccessor;
import sh.okx.civmodern.common.ui.core.CursorStyle;
import sh.okx.civmodern.common.ui.core.OwoUIGraphics;
import sh.okx.civmodern.common.ui.core.Sizing;
import sh.okx.civmodern.common.ui.observable.EventSource;
import sh.okx.civmodern.common.ui.observable.EventStream;
import sh.okx.civmodern.common.ui.observable.Observable;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

public class TextBoxComponent extends EditBox {

    protected final Observable<Boolean> showsBackground = Observable.of(((EditBoxAccessor) this).civmodern$bordered());

    protected final Observable<String> textValue = Observable.of("");
    protected final EventStream<OnChanged> changedEvents = OnChanged.newStream();

    protected TextBoxComponent(Sizing horizontalSizing) {
        super(Minecraft.getInstance().font, 0, 0, 0, 0, Component.empty());

        this.textValue.observe(this.changedEvents.sink()::onChanged);
        this.sizing(horizontalSizing, Sizing.content());

        this.showsBackground.observe(a -> this.widgetWrapper().notifyParentIfMounted());
    }

    /**
     * @deprecated Subscribe to {@link #onChanged()} instead
     */
    @Override
    @Deprecated(forRemoval = true)
    public void setResponder(Consumer<String> changedListener) {
        super.setResponder(changedListener);
    }

    @Override
    public void drawFocusHighlight(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        // noop, since TextFieldWidget already does this
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        boolean result = super.keyPressed(input);

        if (input.isCycleFocus()) {
            this.insertText("    ");
            return true;
        } else {
            return result;
        }
    }

    @Override
    public void updateX(int x) {
        super.updateX(x);
        ((EditBoxAccessor) this).civmodern$updateTextPosition();
    }

    @Override
    public void updateY(int y) {
        super.updateY(y);
        ((EditBoxAccessor) this).civmodern$updateTextPosition();
    }

    @Override
    public void setBordered(boolean drawsBackground) {
        super.setBordered(drawsBackground);
        this.showsBackground.set(drawsBackground);
    }

    public EventSource<OnChanged> onChanged() {
        return changedEvents.source();
    }

    public TextBoxComponent text(String text) {
        this.setValue(text);
        this.moveCursorToStart(false);
        return this;
    }

    protected CursorStyle civmodern$preferredCursorStyle() {
        return CursorStyle.TEXT;
    }

    public interface OnChanged {
        void onChanged(String value);

        static EventStream<OnChanged> newStream() {
            return new EventStream<>(subscribers -> value -> {
                for (var subscriber : subscribers) {
                    subscriber.onChanged(value);
                }
            });
        }
    }
}

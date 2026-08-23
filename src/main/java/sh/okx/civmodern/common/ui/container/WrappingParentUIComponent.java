/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.container;

import sh.okx.civmodern.common.ui.base.BaseParentUIComponent;
import sh.okx.civmodern.common.ui.core.ParentUIComponent;
import sh.okx.civmodern.common.ui.core.Size;
import sh.okx.civmodern.common.ui.core.Sizing;
import sh.okx.civmodern.common.ui.core.UIComponent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class WrappingParentUIComponent<C extends UIComponent> extends BaseParentUIComponent {

    protected C child;
    protected List<UIComponent> childView;

    protected WrappingParentUIComponent(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        super(horizontalSizing, verticalSizing);
        this.child = child;
        this.childView = Collections.singletonList(this.child);
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return this.child.fullSize().width() + this.padding.get().horizontal();
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return this.child.fullSize().height() + this.padding.get().vertical();
    }

    @Override
    public void layout(Size space) {
        this.child.inflate(this.calculateChildSpace(space));
        this.child.mount(this, this.childMountX(), this.childMountY());
    }

    /**
     * @return The x-coordinate at which to mount the child
     */
    protected int childMountX() {
        return this.x + child.margins().get().left() + this.padding.get().left();
    }

    /**
     * @return The y-coordinate at which to mount the child
     */
    protected int childMountY() {
        return this.y + child.margins().get().top() + this.padding.get().top();
    }

    public WrappingParentUIComponent<C> child(C newChild) {
        if (this.child != null) {
            this.child.dismount(DismountReason.REMOVED);
        }

        this.child = newChild;
        this.childView = Collections.singletonList(this.child);

        this.updateLayout();
        return this;
    }

    public C child() {
        return this.child;
    }

    @Override
    public List<UIComponent> children() {
        return this.childView;
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Cannot remove the child of a wrapping component");
    }

}

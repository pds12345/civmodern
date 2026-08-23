package sh.okx.civmodern.common.map.screen;

import sh.okx.civmodern.common.ui.base.BaseOwoScreen;
import sh.okx.civmodern.common.ui.component.UIComponents;
import sh.okx.civmodern.common.ui.container.UIContainers;
import sh.okx.civmodern.common.ui.container.FlowLayout;
import sh.okx.civmodern.common.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import sh.okx.civmodern.common.AbstractCivModernMod;

import java.util.function.Consumer;

public class ImportAvailable extends BaseOwoScreen<FlowLayout> {
    private final String[] mods;
    private final Consumer<String> callback;

    public ImportAvailable(String[] mods, Consumer<String> callback) {
        this.mods = mods;
        this.callback = callback;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        root.child(UIComponents.label(Component.literal("Import Available")).shadow(true).margins(Insets.bottom(5)));
        root.child(UIComponents.label(Component.literal("CivModern can import map data from the following mods, please select one:")));

        var buttons = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        buttons.configure(layout -> {
            layout.margins(Insets.of(5));
        });
        buttons.gap(6);
        buttons.horizontalAlignment(HorizontalAlignment.CENTER);

        for (var mod : mods) {
            buttons.child(UIComponents.button(Component.literal(mod), button -> {
                callback.accept(mod);
                Minecraft.getInstance().setScreen(null);
            }));
        }
        root.child(buttons);

        var closeButtons = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        closeButtons.child(UIComponents.button(Component.literal("Close"), button -> {
            callback.accept("close");
            Minecraft.getInstance().setScreen(null);
        }).margins(Insets.of(5)));
        closeButtons.child(UIComponents.button(Component.literal("Don't show again"), button -> {
            callback.accept("neverShowAgain");
            Minecraft.getInstance().setScreen(null);
        }).margins(Insets.of(5)));
        root.child(closeButtons);
    }

    @Override
    public void removed() {
        super.removed();
        this.callback.accept("close");
    }
}

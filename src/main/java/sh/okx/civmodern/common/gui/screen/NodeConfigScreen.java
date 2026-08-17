package sh.okx.civmodern.common.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.gui.DoubleValue;
import sh.okx.civmodern.common.gui.widget.DoubleOptionUpdateableSliderWidget;
import sh.okx.civmodern.common.gui.widget.TextRenderable;
import sh.okx.civmodern.common.gui.widget.ToggleButton;
import sh.okx.civmodern.common.map.nodes.NodeApiClient;

/**
 * Settings for the node territory overlay, plus the manual handshake for when the automatic one
 * at join does not take.
 */
public class NodeConfigScreen extends AbstractConfigScreen {

    private final NodeApiClient nodeApi;

    /** Feedback from the last handshake attempt, shown until the screen is closed. */
    private Component handshakeResult;

    public NodeConfigScreen(CivMapConfig config, NodeApiClient nodeApi, Screen parent) {
        super(config, parent, Component.translatable("civmodern.screen.nodes.title"));
        this.nodeApi = nodeApi;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableOnly(new TextRenderable.CentreAligned(this.font, this.centreX, getHeaderY(), this.title));

        int left = this.width / 2 - 155;
        int right = left + 160;
        int centre = left + 80;
        int offset = getBodyY();

        addRenderableWidget(new ToggleButton(left, offset, ToggleButton.DEFAULT_BUTTON_WIDTH,
            Component.translatable("civmodern.screen.nodes.overlay"),
            this.config::isNodeOverlayEnabled, this.config::setNodeOverlayEnabled,
            Tooltip.create(Component.translatable("civmodern.screen.nodes.overlay.tooltip")),
            ToggleButton.DEFAULT_NARRATION));

        addRenderableWidget(new ToggleButton(right, offset, ToggleButton.DEFAULT_BUTTON_WIDTH,
            Component.translatable("civmodern.screen.nodes.borders"),
            this.config::isNodeOverlayBorders, this.config::setNodeOverlayBorders,
            Tooltip.create(Component.translatable("civmodern.screen.nodes.borders.tooltip")),
            ToggleButton.DEFAULT_NARRATION));

        offset += 24;

        addRenderableWidget(new DoubleOptionUpdateableSliderWidget(left, offset, 150, 20, 0.1, 1.0, new DoubleValue() {
            @Override
            public double get() {
                return config.getNodeOverlayOpacity();
            }

            @Override
            public void set(double value) {
                config.setNodeOverlayOpacity((float) value);
            }

            @Override
            public Component getText(double value) {
                return Component.translatable("civmodern.screen.nodes.opacity", Math.round(value * 100) + "%");
            }
        }));

        addRenderableWidget(new DoubleOptionUpdateableSliderWidget(right, offset, 150, 20, 3, 31, new DoubleValue() {
            @Override
            public double get() {
                return config.getNodeQuerySize();
            }

            @Override
            public void set(double value) {
                // The server clamps to an odd value anyway; matching it keeps the label honest.
                int size = (int) value;
                config.setNodeQuerySize((size & 1) == 0 ? size - 1 : size);
            }

            @Override
            public Component getText(double value) {
                int size = (int) value;
                if ((size & 1) == 0) {
                    size--;
                }
                return Component.translatable("civmodern.screen.nodes.window", size + "x" + size);
            }
        }));

        offset += 36;

        Button handshake = Button.builder(Component.translatable("civmodern.screen.nodes.handshake"), button -> {
            if (nodeApi != null) {
                this.handshakeResult = nodeApi.retryHandshake();
            }
        }).pos(centre, offset).size(150, 20).build();
        handshake.setTooltip(Tooltip.create(Component.translatable("civmodern.screen.nodes.handshake.tooltip")));
        handshake.active = nodeApi != null && Minecraft.getInstance().getConnection() != null;
        addRenderableWidget(handshake);

        offset += 48;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            config.save();
            Minecraft.getInstance().setScreen(parent);
        }).pos(centre, getFooterY(offset)).size(150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // The status is live, so it is drawn rather than baked into a widget at init time.
        int statusY = getBodyY() + 24 + 36 + 26;
        if (nodeApi != null) {
            graphics.drawCenteredString(this.font, nodeApi.statusLine(), this.width / 2, statusY, 0xFFFFFFFF);
        }
        if (handshakeResult != null) {
            graphics.drawCenteredString(this.font, handshakeResult, this.width / 2, statusY + 12, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        config.save();
        super.onClose();
    }
}

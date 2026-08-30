package sh.okx.civmodern.common.gui.screen;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.ColourProvider;
import sh.okx.civmodern.common.gui.Alignment;
import sh.okx.civmodern.common.gui.DoubleValue;
import sh.okx.civmodern.common.gui.widget.ColourTextEditBox;
import sh.okx.civmodern.common.gui.widget.DoubleOptionUpdateableSliderWidget;
import sh.okx.civmodern.common.gui.widget.HsbColourPicker;
import sh.okx.civmodern.common.gui.widget.ImageButton;
import sh.okx.civmodern.common.gui.widget.TextRenderable;
import sh.okx.civmodern.common.gui.widget.ToggleButton;
import sh.okx.civmodern.common.radar.PlayerRelations;

final class RadarConfigScreen extends AbstractConfigScreen {
    public static final Identifier ROLLBACK_ICON = Identifier.tryBuild("civmodern", "gui/rollback.png");
    private static final int SCROLL_STEP = 20;
    private static final int VIEWPORT_BOTTOM_MARGIN = 6;

    private final ColourProvider colourProvider;

    // for passing move events
    private HsbColourPicker bgPicker;
    private HsbColourPicker fgPicker;

    /** Everything between the header and the Done button - scrollable, and clipped to the viewport. */
    private final List<Renderable> bodyWidgets = new ArrayList<>();
    /** Repositioning info for the same widgets, so scrolling moves them without rebuilding them. */
    private final List<BodyEntry> bodyEntries = new ArrayList<>();
    /** The header and Done button - always visible, never scrolled or clipped. */
    private final List<Renderable> chromeWidgets = new ArrayList<>();

    /**
     * A widget or label positioned at some unscrolled Y, and how to move it. Repositioning
     * existing instances - rather than rebuilding on every scroll tick - matters specifically for
     * {@link HsbColourPicker}: it allocates GPU textures in its constructor and only releases them
     * via an explicit {@link HsbColourPicker#close()}, so recreating it on each scroll tick either
     * leaks those textures or fails outright once a name collides.
     */
    private record BodyEntry(int naturalY, IntConsumer reposition) {
    }

    private double scrollAmount = 0;
    private int viewportTop;
    private int viewportBottom;
    /** The natural (unscrolled) Y just past the last body row - i.e. total content height. */
    private int contentBottom;

    public RadarConfigScreen(
        final @NotNull CivMapConfig config,
        final @NotNull ColourProvider colourProvider,
        final @NotNull MainConfigScreen parent
    ) {
        super(
            config,
            Objects.requireNonNull(parent),
            Component.translatable("civmodern.screen.radar.title")
        );
        this.colourProvider = Objects.requireNonNull(colourProvider);
    }

    @Override
    protected void init() {
        super.init();
        this.bodyWidgets.clear();
        this.bodyEntries.clear();
        this.chromeWidgets.clear();

        this.chromeWidgets.add(addRenderableOnly(new TextRenderable.CentreAligned(
            this.font,
            this.centreX,
            getHeaderY(),
            this.title
        )));

        final int leftSideX = this.centreX - 5 - Button.DEFAULT_WIDTH;
        final int rightSideX = this.centreX + 5;

        this.viewportTop = getBodyY(this.height / 8);
        int offsetY = this.viewportTop - (int) this.scrollAmount;

        addBodyWidget(new ToggleButton(
            this.centreX - (Button.DEFAULT_WIDTH / 2),
            offsetY,
            Button.DEFAULT_WIDTH,
            Component.translatable("civmodern.screen.radar.enabled"),
            this.config::isRadarEnabled,
            this.config::setRadarEnabled,
            Tooltip.create(Component.translatable("civmodern.screen.radar.enabled.tooltip")),
            ToggleButton.DEFAULT_NARRATION
        ));
        offsetY += Button.DEFAULT_HEIGHT + 4;

        addBodyWidget(new ToggleButton(
            leftSideX,
            offsetY,
            ToggleButton.DEFAULT_BUTTON_WIDTH,
            Component.translatable("civmodern.screen.radar.messages"),
            this.config::isPingEnabled,
            this.config::setPingEnabled,
            Tooltip.create(Component.translatable("civmodern.screen.radar.messages.tooltip")),
            ToggleButton.DEFAULT_NARRATION
        ));
        addBodyWidget(new ToggleButton(
            rightSideX,
            offsetY,
            ToggleButton.DEFAULT_BUTTON_WIDTH,
            Component.translatable("civmodern.screen.radar.pings"),
            this.config::isPingSoundEnabled,
            this.config::setPingSoundEnabled,
            Tooltip.create(Component.translatable("civmodern.screen.radar.pings.tooltip")),
            ToggleButton.DEFAULT_NARRATION
        ));
        offsetY += Button.DEFAULT_HEIGHT + 4;

        addBodyWidget(
            Button
                .builder(
                    Component.translatable("civmodern.screen.radar.alignment", this.config.getAlignment().toString()),
                    (button) -> {
                        final Alignment next = this.config.getAlignment().next();
                        this.config.setAlignment(next);
                        button.setMessage(Component.translatable("civmodern.screen.radar.alignment", next.toString()));
                    }
                )
                .pos(leftSideX, offsetY)
                .build()
        );
        addBodyWidget(new ToggleButton(
            rightSideX,
            offsetY,
            ToggleButton.DEFAULT_BUTTON_WIDTH,
            Component.translatable("civmodern.screen.radar.items"),
            this.config::isShowItems,
            this.config::setShowItems,
            Tooltip.create(Component.translatable("civmodern.screen.radar.items.tooltip")),
            ToggleButton.DEFAULT_NARRATION
        ));
        offsetY += Button.DEFAULT_HEIGHT + 4;

        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            rightSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            0, 1,
            new DoubleValue() {
                private final DecimalFormat format = new DecimalFormat("##%");
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getTransparency();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setTransparency((float) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.transparency", this.format.format(value));
                }
            }
        ));
        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            leftSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            0, 1,
            new DoubleValue() {
                private final DecimalFormat format = new DecimalFormat("##%");
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getBackgroundTransparency();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setBackgroundTransparency((float) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.background_transparency", this.format.format(value));
                }
            }
        ));
        offsetY += Button.DEFAULT_HEIGHT + 4;

        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            leftSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            0.5, 2,
            new DoubleValue() {
                private final DecimalFormat format = new DecimalFormat("#.#");
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getIconSize();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setIconSize((float) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.iconsize", this.format.format(value));
                }
            }
        ));
        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            rightSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            0, 2,
            new DoubleValue() {
                private final DecimalFormat format = new DecimalFormat("#.#");
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getTextSize();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setTextSize((float) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.textsize", this.format.format(value));
                }
            }
        ));
        offsetY += Button.DEFAULT_HEIGHT + 4;

        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            leftSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            25, 250,
            new DoubleValue() {
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getRadarSize();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setRadarSize((int) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.size", Integer.toString((int) value));
                }
            }
        ));
        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            rightSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            1, 8,
            new DoubleValue() {
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getRadarCircles();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setRadarCircles((int) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.circles", Integer.toString((int) value));
                }
            }
        ));
        offsetY += Button.DEFAULT_HEIGHT + 4;

        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            leftSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            0, 300,
            new DoubleValue() {
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getX();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setX((int) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.x", String.valueOf((int) value));
                }
            }
        ));
        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            rightSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            0, 300,
            new DoubleValue() {
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getY();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setY((int) value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.y", String.valueOf((int) value));
                }
            }
        ));
        offsetY += Button.DEFAULT_HEIGHT + 4;
        addBodyWidget(new DoubleOptionUpdateableSliderWidget(
            leftSideX,
            offsetY,
            Button.DEFAULT_WIDTH,
            Button.DEFAULT_HEIGHT,
            20, 150,
            new DoubleValue() {
                @Override
                public double get() {
                    return RadarConfigScreen.this.config.getRange();
                }
                @Override
                public void set(final double value) {
                    RadarConfigScreen.this.config.setRange(value);
                }
                @Override
                public @NotNull Component getText(final double value) {
                    return Component.translatable("civmodern.screen.radar.range", String.valueOf((int) value));
                }
            }
        ));
        addBodyWidget(
            Button
                .builder(
                    Component.translatable("civmodern.screen.radar.log", this.config.isRadarLogarithm() ? Component.translatable("civmodern.screen.radar.log.logarithmic") : Component.translatable("civmodern.screen.radar.log.linear")),
                    (button) -> {
                        this.config.setRadarLogarithm(!this.config.isRadarLogarithm());
                        button.setMessage(Component.translatable("civmodern.screen.radar.log", this.config.isRadarLogarithm() ? Component.translatable("civmodern.screen.radar.log.logarithmic") : Component.translatable("civmodern.screen.radar.log.linear")));
                    }
                )
                .pos(rightSideX, offsetY)
                .build());
        offsetY += Button.DEFAULT_HEIGHT + 4;

        PlayerRelations playerRelations = AbstractCivModernMod.getInstance().getWorldListener().getPlayerRelations();
        Button playerRelationsButton = Button.builder(
                Component.translatable("civmodern.screen.radar.playerrelations"),
                (button) -> Minecraft.getInstance().setScreen(new PlayerRelationsScreen(this, playerRelations))
            )
            .pos(this.centreX - (Button.DEFAULT_WIDTH / 2), offsetY)
            .build();
        // Per-world storage, same as the waypoint manager: nothing to manage from the title screen.
        playerRelationsButton.active = playerRelations != null;
        if (playerRelations == null) {
            playerRelationsButton.setTooltip(Tooltip.create(Component.translatable("civmodern.screen.map.waypointmanager.noworld")));
        }
        addBodyWidget(playerRelationsButton);
        offsetY += Button.DEFAULT_HEIGHT + 4;

        offsetY += 10;

        // Colour pickers sit after everything else now, in the normal flow, instead of a fixed
        // offset from the top - previously that clipped into whatever else had grown past it.
        this.bgPicker = addColourPicker(
            leftSideX,
            offsetY,
            CivMapConfig.DEFAULT_RADAR_BG_COLOUR,
            Component.literal("Background colour"),
            this.config::getRadarBgColour,
            this.config::setRadarBgColour,
            this.colourProvider::setTemporaryRadarBackgroundColour
        );
        this.fgPicker = addColourPicker(
            rightSideX,
            offsetY,
            CivMapConfig.DEFAULT_RADAR_FG_COLOUR,
            Component.literal("Line colour"),
            this.config::getRadarColour,
            this.config::setRadarColour,
            this.colourProvider::setTemporaryRadarForegroundColour
        );
        offsetY += this.font.lineHeight + 2 + Button.DEFAULT_HEIGHT;

        // scrollAmount was subtracted exactly once, at the very start - add it back to recover
        // the natural (unscrolled) content height.
        this.contentBottom = offsetY + (int) this.scrollAmount;

        // Done must stay pinned to the fixed bottom margin regardless of content height now that
        // overflow is handled by scrolling - getFooterY(contentBottom) would otherwise let it (and
        // the viewport bottom derived from it) drift below the window whenever content overflows,
        // which is exactly the case scrolling exists for.
        int doneY = getFooterY(0);
        this.viewportBottom = doneY - VIEWPORT_BOTTOM_MARGIN;

        this.chromeWidgets.add(addRenderableWidget(
            Button
                .builder(
                    CommonComponents.GUI_DONE,
                    (button) -> {
                        this.config.save();
                        Minecraft.getInstance().setScreen(this.parent);
                    }
                )
                .pos(this.centreX - (Button.DEFAULT_WIDTH / 2), doneY)
                .build()
        ));
    }

    private <T extends AbstractWidget> T addBodyWidget(T widget) {
        T added = addRenderableWidget(widget);
        this.bodyWidgets.add(added);
        this.bodyEntries.add(new BodyEntry(added.getY() + (int) this.scrollAmount, added::setY));
        return added;
    }

    private <T extends TextRenderable> T addBodyRenderableOnly(T renderable) {
        T added = addRenderableOnly(renderable);
        this.bodyWidgets.add(added);
        this.bodyEntries.add(new BodyEntry(added.y + (int) this.scrollAmount, y -> added.y = y));
        return added;
    }

    /** Moves every existing body widget to reflect the current scroll position, without rebuilding any of them. */
    private void applyScroll() {
        for (BodyEntry entry : this.bodyEntries) {
            entry.reposition().accept(entry.naturalY() - (int) this.scrollAmount);
        }
    }

    private @NotNull HsbColourPicker addColourPicker(
        final int offsetX,
        int offsetY,
        final int defaultColour,
        final @NotNull Component label,
        final @NotNull IntSupplier colourGetter,
        final @NotNull IntConsumer colourSetter,
        final @NotNull Consumer<Integer> preview
    ) {
        final int innerCenterX = offsetX + (Button.DEFAULT_WIDTH / 2);

        addBodyRenderableOnly(new TextRenderable.CentreAligned(
            this.font,
            innerCenterX,
            offsetY,
            label
        ));
        offsetY += this.font.lineHeight + 2;

        final var colourEditBox = addBodyWidget(new ColourTextEditBox(
            this.font,
            innerCenterX - 30,
            offsetY,
            60,
            Button.DEFAULT_HEIGHT,
            colourGetter,
            colourSetter
        ));

        final var hsb = addBodyWidget(new HsbColourPicker(
            innerCenterX - 30 - 4 - 20,
            offsetY,
            20,
            20,
            colourGetter.getAsInt(),
            (colour) -> {
                colourEditBox.setColourFromInt(colour);
                colourSetter.accept(colour);
            },
            preview,
            this::closePickers
        ));

        addBodyWidget(new ImageButton(
            innerCenterX + 30 + 4,
            offsetY,
            20,
            20,
            ROLLBACK_ICON,
            (button) -> {
                colourEditBox.setColourFromInt(defaultColour);
                colourSetter.accept(defaultColour);
                hsb.close();
            }
        ));

        return hsb;
    }

    @Override
    public void onClose() {
        this.config.save();
        this.colourProvider.setTemporaryRadarBackgroundColour(null);
        this.colourProvider.setTemporaryRadarForegroundColour(null);
        super.onClose();
    }

    @Override
    public void mouseMoved(
        final double mouseX,
        final double mouseY
    ) {
        super.mouseMoved(mouseX, mouseY);
        if (this.fgPicker != null) {
            this.fgPicker.mouseMoved(mouseX, mouseY);
        }
        if (this.bgPicker != null) {
            this.bgPicker.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(
        final double mouseX,
        final double mouseY,
        final double scrollX,
        final double scrollY
    ) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        double maxScroll = Math.max(0, this.contentBottom - this.viewportBottom);
        double newScroll = Mth.clamp(this.scrollAmount - scrollY * SCROLL_STEP, 0, maxScroll);
        if (newScroll != this.scrollAmount) {
            this.scrollAmount = newScroll;
            this.applyScroll();
        }
        return true;
    }

    @Override
    public void render(
        final @NotNull GuiGraphics guiGraphics,
        final int mouseX,
        final int mouseY,
        final float partialTick
    ) {
        // Don't call super since we don't want the dark or blurred background to obscure changes to the radar
        guiGraphics.enableScissor(0, this.viewportTop, this.width, this.viewportBottom);
        for (final Renderable renderable : this.bodyWidgets) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.disableScissor();

        for (final Renderable renderable : this.chromeWidgets) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void closePickers() {
        if (this.fgPicker != null) {
            this.fgPicker.close();
        }
        if (this.bgPicker != null) {
            this.bgPicker.close();
        }
    }
}

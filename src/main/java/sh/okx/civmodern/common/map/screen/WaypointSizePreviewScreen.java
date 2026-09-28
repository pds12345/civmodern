package sh.okx.civmodern.common.map.screen;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.gui.Alignment;
import sh.okx.civmodern.common.gui.DoubleValue;
import sh.okx.civmodern.common.gui.widget.DoubleOptionUpdateableSliderWidget;
import sh.okx.civmodern.common.gui.widget.TextRenderable;
import sh.okx.civmodern.common.map.MapCache;
import sh.okx.civmodern.common.map.MapFocus;
import sh.okx.civmodern.common.map.Minimap;
import sh.okx.civmodern.common.map.WaypointScaling;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.mixins.ScreenAccessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lets the player tune the four waypoint-size numbers while watching the effect live, instead of
 * bouncing between the config screen, the map and the minimap.
 * <p>
 * Shows the map (locked on the player, zoomable with the scroll wheel, nothing else drawn) and the
 * minimap at its real size and position, each with one fictional waypoint at the player's position.
 * That waypoint is never handed to the waypoint store, so nothing is persisted. Edits are held as
 * drafts and only copied into the config by "Save and exit"; any other way out discards them. Minimap
 * zoom changes made here are always undone on exit: they exist to see the effect, not to set the zoom.
 */
public class WaypointSizePreviewScreen extends Screen {

    private static final int FIELD_WIDTH = 150;
    private static final int FIELD_HEIGHT = 20;
    private static final int COLUMN_GAP = 10;
    private static final int LABEL_HEIGHT = 12;
    private static final int BOTTOM_PADDING = 15;
    /** Same bounds as the minimap's Z-cycle (see {@link Minimap#cycleZoom()}), in blocks per pixel. */
    private static final float MIN_MINIMAP_ZOOM = 0.5f;
    private static final float MAX_MINIMAP_ZOOM = 16f;
    /** Same bounds as the map screen's scroll-zoom, in blocks per pixel. */
    private static final float MIN_MAP_ZOOM = 0.03125f;
    /** The largest "Max zoom" the map config allows. */
    private static final float MAX_MAP_ZOOM = 32f;
    /** 1 itself is a division by zero in the formula, and values just above it collapse icons instantly. */
    private static final double MIN_LOG_BASE = 1.1;
    private static final double MAX_LOG_BASE = 20;
    private static final double LOG_BASE_STEP = 0.1;
    private static final int PREVIEW_COLOUR = 0xFF0000;
    private static final int TITLE_Y = 10;
    /** Distance from the screen edge to the tooltip's background, which extends 3px past its text. */
    private static final int TOOLTIP_MARGIN = 8 + 3;

    private final Screen parent;
    private final CivMapConfig config;
    private final MapCache mapCache;
    private final Minimap minimap;
    private final KeyMapping minimapZoomKey;
    private final Waypoint previewWaypoint;

    // Drafts: what the screen renders with, and what "Save and exit" copies into the config.
    private float waypointBaseZoom;
    private float waypointZoomLogBase;
    private float minimapIconBaseZoom;
    private float minimapIconZoomLogBase;

    private final float originalMinimapZoom;
    /** Blocks per pixel on the big map. Starts where the map screen left off but never writes back. */
    private float zoom;

    private Button minimapZoomIn;
    private Button minimapZoomOut;
    /**
     * Slider tooltips are drawn by this screen at a fixed spot rather than attached to the widgets,
     * where vanilla would put them beside the cursor, right over the minimap or the waypoint.
     */
    private final Map<AbstractWidget, Component> sliderTooltips = new LinkedHashMap<>();

    public WaypointSizePreviewScreen(Screen parent, CivMapConfig config, MapCache mapCache, Minimap minimap, KeyMapping minimapZoomKey) {
        super(Component.translatable("civmodern.screen.waypointpreview.title"));
        this.parent = parent;
        this.config = config;
        this.mapCache = mapCache;
        this.minimap = minimap;
        this.minimapZoomKey = minimapZoomKey;

        this.waypointBaseZoom = config.getWaypointBaseZoom();
        this.waypointZoomLogBase = config.getWaypointZoomLogBase();
        this.minimapIconBaseZoom = config.getMinimapIconBaseZoom();
        this.minimapIconZoomLogBase = config.getMinimapIconZoomLogBase();
        this.originalMinimapZoom = config.getMinimapZoom();
        this.zoom = MapScreen.currentZoom();

        Entity focus = MapFocus.entity();
        this.previewWaypoint = new Waypoint(Component.translatable("civmodern.screen.waypointpreview.waypointname").getString(),
            focus.getBlockX(), focus.getBlockY(), focus.getBlockZ(), "waypoint", PREVIEW_COLOUR);
    }

    @Override
    protected void init() {
        sliderTooltips.clear();
        int left = this.width / 2 - FIELD_WIDTH - COLUMN_GAP / 2;
        int right = this.width / 2 + COLUMN_GAP / 2;

        // Stacked up from the bottom edge: exit buttons, then the two slider rows with their headings.
        int exitY = this.height - BOTTOM_PADDING - FIELD_HEIGHT;
        int row2Y = exitY - 8 - FIELD_HEIGHT;
        int row2LabelY = row2Y - LABEL_HEIGHT;
        int row1Y = row2LabelY - 4 - FIELD_HEIGHT;
        int row1LabelY = row1Y - LABEL_HEIGHT;

        addRenderableOnly(new TextRenderable.CentreAligned(this.font, this.width / 2, row1LabelY,
            Component.translatable("civmodern.screen.waypointpreview.map")));
        addBaseZoomSlider(left, row1Y, MIN_MAP_ZOOM, MAX_MAP_ZOOM, () -> waypointBaseZoom, v -> waypointBaseZoom = v,
            "civmodern.screen.waypointpreview.waypointbasezoom.tooltip");
        addLogBaseSlider(right, row1Y, () -> waypointZoomLogBase, v -> waypointZoomLogBase = v,
            "civmodern.screen.waypointpreview.waypointzoomlogbase.tooltip");

        addRenderableOnly(new TextRenderable.CentreAligned(this.font, this.width / 2, row2LabelY,
            Component.translatable("civmodern.screen.waypointpreview.minimap")));
        addBaseZoomSlider(left, row2Y, MIN_MINIMAP_ZOOM, MAX_MINIMAP_ZOOM, () -> minimapIconBaseZoom, v -> minimapIconBaseZoom = v,
            "civmodern.screen.waypointpreview.minimapiconbasezoom.tooltip");
        addLogBaseSlider(right, row2Y, () -> minimapIconZoomLogBase, v -> minimapIconZoomLogBase = v,
            "civmodern.screen.waypointpreview.minimapiconzoomlogbase.tooltip");

        addRenderableWidget(Button.builder(Component.translatable("civmodern.screen.waypointpreview.save"), b -> saveAndExit())
            .pos(left, exitY).size(FIELD_WIDTH, FIELD_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("civmodern.screen.waypointpreview.discard"), b -> onClose())
            .pos(right, exitY).size(FIELD_WIDTH, FIELD_HEIGHT).build());

        // Zoom buttons sit just under the minimap (below its coordinates line when that is shown),
        // or above it when a bottom-aligned minimap leaves no room below.
        Minimap.Placement placement = minimap.placement();
        int zoomY = placement.y() + placement.size() + 6 + (config.isShowMinimapCoords() ? this.font.lineHeight + 6 : 0);
        if (zoomY + FIELD_HEIGHT > this.height) {
            zoomY = placement.y() - 6 - FIELD_HEIGHT;
        }
        int middle = placement.x() + placement.size() / 2;
        // "-" zooms out (more blocks per pixel), "+" zooms in, matching the scroll wheel on the map.
        minimapZoomOut = addRenderableWidget(Button.builder(Component.literal("-"), b -> setMinimapZoom(config.getMinimapZoom() * 2))
            .pos(middle - 2 - FIELD_HEIGHT, zoomY).size(FIELD_HEIGHT, FIELD_HEIGHT).build());
        minimapZoomIn = addRenderableWidget(Button.builder(Component.literal("+"), b -> setMinimapZoom(config.getMinimapZoom() / 2))
            .pos(middle + 2, zoomY).size(FIELD_HEIGHT, FIELD_HEIGHT).build());
        refreshMinimapZoomButtons();

        addRenderableOnly(new TextRenderable.CentreAligned(this.font, this.width / 2, TITLE_Y, this.title));
        addRenderableOnly(new TextRenderable.CentreAligned(this.font, this.width / 2, TITLE_Y + this.font.lineHeight + 2,
            Component.translatable("civmodern.screen.waypointpreview.hint", minimapZoomKey.getTranslatedKeyMessage()), 0xFFAAAAAA));
    }

    /**
     * Base zoom on a log2 scale, snapped to the powers of two the map and minimap actually zoom
     * through. A linear slider over 1/32..32 would cram every useful value into its first pixels.
     */
    private void addBaseZoomSlider(int x, int y, float minZoom, float maxZoom, Supplier<Float> draft, Consumer<Float> setDraft, String tooltipKey) {
        double min = log2(minZoom);
        double max = log2(maxZoom);
        DoubleOptionUpdateableSliderWidget slider = new DoubleOptionUpdateableSliderWidget(x, y, FIELD_WIDTH, FIELD_HEIGHT, min, max, 1, new DoubleValue() {
            @Override
            public double get() {
                return Mth.clamp(log2(draft.get()), min, max);
            }

            @Override
            public void set(double value) {
                setDraft.accept((float) Math.pow(2, Math.round(value)));
            }

            @Override
            public Component getText(double value) {
                return Component.translatable("civmodern.screen.waypointpreview.basezoom", formatZoom(Math.pow(2, Math.round(value))));
            }
        });
        sliderTooltips.put(addRenderableWidget(slider), Component.translatable(tooltipKey));
    }

    private void addLogBaseSlider(int x, int y, Supplier<Float> draft, Consumer<Float> setDraft, String tooltipKey) {
        DoubleOptionUpdateableSliderWidget slider = new DoubleOptionUpdateableSliderWidget(x, y, FIELD_WIDTH, FIELD_HEIGHT,
            MIN_LOG_BASE, MAX_LOG_BASE, LOG_BASE_STEP, new DoubleValue() {
            @Override
            public double get() {
                return Mth.clamp(draft.get(), MIN_LOG_BASE, MAX_LOG_BASE);
            }

            @Override
            public void set(double value) {
                setDraft.accept((float) value);
            }

            @Override
            public Component getText(double value) {
                return Component.translatable("civmodern.screen.waypointpreview.logbase", String.format("%.1f", value));
            }
        });
        sliderTooltips.put(addRenderableWidget(slider), Component.translatable(tooltipKey));
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    /** Zoom is blocks per pixel, so "1/8" reads as eight pixels per block. */
    private static String formatZoom(double zoom) {
        if (zoom < 1) {
            return "1/" + Math.round(1 / zoom);
        }
        return Long.toString(Math.round(zoom));
    }

    private void setMinimapZoom(float zoom) {
        config.setMinimapZoom(Mth.clamp(zoom, MIN_MINIMAP_ZOOM, MAX_MINIMAP_ZOOM));
        refreshMinimapZoomButtons();
    }

    private void refreshMinimapZoomButtons() {
        minimapZoomIn.active = config.getMinimapZoom() > MIN_MINIMAP_ZOOM;
        minimapZoomOut.active = config.getMinimapZoom() < MAX_MINIMAP_ZOOM;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // Deliberately not super.render(): vanilla would blur and darken the map.
        Window window = Minecraft.getInstance().getWindow();
        float scale = (float) window.getGuiScale() * zoom;

        // Re-centred every frame, so the map follows the player and the waypoint stays in the middle.
        Entity focus = MapFocus.entity();
        double x = focus.getX() - (window.getWidth() * zoom) / 2;
        double y = focus.getZ() - (window.getHeight() * zoom) / 2;

        guiGraphics.fill(0, 0, this.width, this.height, 0xff000000);
        MapScreen.renderTiles(guiGraphics, mapCache, false, x, y, zoom, scale);

        // Icon and label drawn exactly as MapScreen draws a real waypoint.
        Matrix3x2fStack matrices = guiGraphics.pose();
        float waypointScale = WaypointScaling.scale(zoom, waypointBaseZoom, waypointZoomLogBase);
        matrices.pushMatrix();
        matrices.translate(0, -1);
        matrices.translate((float) ((previewWaypoint.x() + 0.5 - x) / scale), (float) ((previewWaypoint.z() + 0.5 - y) / scale));
        matrices.scale(waypointScale, waypointScale);
        previewWaypoint.render2D(guiGraphics);
        matrices.translate(0, -16);
        Component label = Component.literal(previewWaypoint.name());
        guiGraphics.drawString(this.font, label, -this.font.width(label) / 2, 0, -1, false);
        guiGraphics.fill(-this.font.width(label) / 2, -1, this.font.width(label) / 2, 9, 1056964608);
        matrices.popMatrix();

        minimap.renderPreview(guiGraphics, delta, previewWaypoint, minimapIconBaseZoom, minimapIconZoomLogBase);

        for (Renderable renderable : ((ScreenAccessor) this).civmodern$getRenderables()) {
            renderable.render(guiGraphics, mouseX, mouseY, delta);
        }

        // After the widgets have rendered, so their hover state is current for this frame.
        for (Map.Entry<AbstractWidget, Component> entry : sliderTooltips.entrySet()) {
            if (entry.getKey().isHovered()) {
                List<FormattedCharSequence> lines = Tooltip.splitTooltip(this.minecraft, entry.getValue());
                guiGraphics.setTooltipForNextFrame(this.font, lines, this::positionTooltip, mouseX, mouseY, false);
                break;
            }
        }
    }

    /**
     * Pins the tooltip to the top corner on the side the minimap is not on, below the title and
     * hint, so it never covers what is being previewed.
     */
    private Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
        Alignment alignment = config.getMinimapAlignment();
        boolean minimapOnLeft = alignment == Alignment.TOP_LEFT || alignment == Alignment.BOTTOM_LEFT;
        int x = minimapOnLeft ? screenWidth - TOOLTIP_MARGIN - tooltipWidth : TOOLTIP_MARGIN;
        int y = TITLE_Y + 2 * (this.font.lineHeight + 2) + 8;
        return new Vector2i(x, y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        // Zoom about the centre (not the cursor, as the map screen does) so the waypoint stays put.
        if (scrollY < 0 && zoom < config.getMaxZoom()) {
            zoom *= 2;
        } else if (scrollY > 0 && zoom > MIN_MAP_ZOOM) {
            zoom /= 2;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Escape is handled by vanilla via onClose(), which discards.
        if (super.keyPressed(event)) {
            return true;
        }
        if (minimapZoomKey.matches(event)) {
            minimap.cycleZoom();
            refreshMinimapZoomButtons();
            return true;
        }
        return false;
    }

    private void saveAndExit() {
        config.setWaypointBaseZoom(waypointBaseZoom);
        config.setWaypointZoomLogBase(waypointZoomLogBase);
        config.setMinimapIconBaseZoom(minimapIconBaseZoom);
        config.setMinimapIconZoomLogBase(minimapIconZoomLogBase);
        // Restore before saving so the preview's zoom never reaches the file.
        config.setMinimapZoom(originalMinimapZoom);
        config.save();
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        // Covers every way off this screen, including a disconnect.
        config.setMinimapZoom(originalMinimapZoom);
    }
}

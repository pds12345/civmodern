package sh.okx.civmodern.common.map.screen;

import com.mojang.blaze3d.platform.Window;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIPipelines;
import io.wispforest.owo.ui.renderstate.LineElementRenderState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.navigation.AutoNavigation;
import sh.okx.civmodern.common.gui.widget.ImageButton;
import sh.okx.civmodern.common.map.MapCache;
import sh.okx.civmodern.common.map.RegionAtlasTexture;
import sh.okx.civmodern.common.map.RegionKey;
import sh.okx.civmodern.common.map.nodes.NodeApiClient;
import sh.okx.civmodern.common.map.nodes.NodeCache;
import sh.okx.civmodern.common.map.nodes.NodeOverlayMode;
import sh.okx.civmodern.common.map.nodes.NodeOverlayRenderer;
import sh.okx.civmodern.common.map.waypoints.PlayerWaypoint;
import sh.okx.civmodern.common.map.waypoints.PlayerWaypoints;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.map.waypoints.Waypoints;
import sh.okx.civmodern.common.mixins.ScreenAccessor;
import sh.okx.civmodern.common.rendering.BlitRenderState;
import sh.okx.civmodern.common.rendering.ChevronRenderState;
import sh.okx.civmodern.common.rendering.CivModernPipelines;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static sh.okx.civmodern.common.map.RegionAtlasTexture.SIZE;

public class MapScreen extends Screen {

    private static final int BOAT_PREVIEW_LINE_COLOUR = 0xFFFF0000;
    private static float zoom = 1; // blocks per pixel

    private final AbstractCivModernMod mod;
    private final KeyMapping key;
    private final MapCache mapCache;
    private final NodeCache nodeCache;
    private final NodeApiClient nodeApi;
    private final AutoNavigation navigation;
    private final Waypoints waypoints;
    private final PlayerWaypoints playerWaypoints;

    private final CivMapConfig config;

    private NewWaypointModal newWaypointModal;
    private EditWaypointModal editWaypointModal;
    private ImageButton openWaypointButton;
    private ImageButton toggleNodes;
    private ImageButton toggleBiomes;

    /** Tracks the API state the node button's tooltip was built for, so it can follow along. */
    private NodeApiClient.State nodeTooltipState;

    private PositionContextMenu positionContextMenu;
    private HighlightContextMenu highlightContextMenu;

    private double x;
    private double y;

    private Waypoint hoveredWaypoint;

    private int mouseBlockX;
    private int mouseBlockY;

    private boolean targeting = false;

    private boolean boating = false;

    private Waypoint newWaypoint;

    private final Set<RegionKey> yLevelInterests = new HashSet<>();

    private boolean changedConfig = false;

    public MapScreen(AbstractCivModernMod mod, KeyMapping key, CivMapConfig config, MapCache mapCache, NodeCache nodeCache, NodeApiClient nodeApi, AutoNavigation navigation, Waypoints waypoints, PlayerWaypoints playerWaypoints) {
        super(Component.translatable("civmodern.screen.map.title"));

        this.mod = mod;
        this.key = key;
        this.config = config;
        this.mapCache = mapCache;
        this.nodeCache = nodeCache;
        this.nodeApi = nodeApi;
        this.waypoints = waypoints;
        this.playerWaypoints = playerWaypoints;
        Window window = Minecraft.getInstance().getWindow();

        x = Minecraft.getInstance().player.getX() - (window.getWidth() * zoom) / 2;
        y = Minecraft.getInstance().player.getZ() - (window.getHeight() * zoom) / 2;
        this.navigation = navigation;
    }

    @Override
    protected void init() {
        ImageButton boatButton = new ImageButton(10, 10, 20, 20, Identifier.fromNamespaceAndPath("civmodern", "gui/boat.png"), imbg -> {
            this.boating = !boating;
        });
        boatButton.setTooltip(Tooltip.create(Component.translatable("civmodern.map.boat.tooltip")));
        addRenderableWidget(boatButton);
        newWaypointModal = new NewWaypointModal(waypoints);
        if (newWaypoint == null) {
            LocalPlayer player = Minecraft.getInstance().player;
            newWaypointModal.open("", player.getBlockX(), player.getBlockY() + 1, player.getBlockZ());
        } else {
            newWaypointModal.open(newWaypoint.name(), newWaypoint.x(), newWaypoint.y(), newWaypoint.z());
            newWaypointModal.setVisible(true);
        }
        editWaypointModal = new EditWaypointModal(waypoints);

        positionContextMenu = new PositionContextMenu(this.waypoints, newWaypointModal, this::focusNewWaypointModal);
        addRenderableWidget(positionContextMenu);

        highlightContextMenu = new HighlightContextMenu(this.waypoints, newWaypointModal, this::focusNewWaypointModal);
        addRenderableWidget(highlightContextMenu);

        openWaypointButton = new ImageButton(this.width / 2 - 22, 10, 20, 20, Identifier.fromNamespaceAndPath("civmodern", "gui/new.png"), imbg -> {
            if (editWaypointModal.isTargeting()) {
                return;
            }
            if (newWaypointModal.isVisible()) {
                newWaypointModal.setVisible(false);
                return;
            }
            LocalPlayer player = Minecraft.getInstance().player;
            newWaypointModal.open("", player.getBlockX(), player.getBlockY() + 1, player.getBlockZ());
            newWaypointModal.setVisible(true);
            editWaypointModal.setVisible(false);
            editWaypointModal.setWaypoint(null);
            focusNewWaypointModal();
        });
        openWaypointButton.setTooltip(Tooltip.create(Component.translatable("civmodern.map.newwaypoint.tooltip")));
        addRenderableWidget(openWaypointButton);

        ImageButton targetButton = new ImageButton(this.width / 2 + 2, 10, 20, 20, Identifier.fromNamespaceAndPath("civmodern", "gui/target.png"), imbg -> {
            this.waypoints.setTarget(null);
            targeting = !targeting;
        });
        targetButton.setTooltip(Tooltip.create(Component.translatable("civmodern.map.highlight.tooltip")));
        addRenderableWidget(targetButton);

        addRenderableWidget(newWaypointModal);
        addRenderableWidget(editWaypointModal);
        if (newWaypointModal.isVisible()) {
            setFocused(newWaypointModal);
        }

        Identifier toggleWaypointImage;
        if (config.isWaypointRenderingEnabled()) {
            toggleWaypointImage = Identifier.fromNamespaceAndPath("civmodern", "gui/waypoint.png");
        } else {
            toggleWaypointImage = Identifier.fromNamespaceAndPath("civmodern", "gui/waypointoff.png");
        }
        ImageButton toggleWaypoints = new ImageButton(this.width - 78, 10, 20, 20, toggleWaypointImage, imbg -> {
            config.setWaypointRenderingEnabled(!config.isWaypointRenderingEnabled());
            changedConfig = true;
            if (config.isWaypointRenderingEnabled()) {
                imbg.setImage(Identifier.fromNamespaceAndPath("civmodern", "gui/waypoint.png"));
            } else {
                imbg.setImage(Identifier.fromNamespaceAndPath("civmodern", "gui/waypointoff.png"));
            }
        });
        toggleWaypoints.setTooltip(Tooltip.create(Component.translatable("civmodern.map.waypoints.tooltip")));
        addRenderableWidget(toggleWaypoints);

        Identifier togglePlayersImage;
        if (config.isPlayerWaypointsEnabled()) {
            togglePlayersImage = Identifier.fromNamespaceAndPath("civmodern", "gui/toggleplayersoff.png");
        } else {
            togglePlayersImage = Identifier.fromNamespaceAndPath("civmodern", "gui/toggleplayers.png");
        }
        ImageButton togglePlayers = new ImageButton(this.width - 54, 10, 20, 20, togglePlayersImage, imbg -> {
            // TODO use world config
            config.setPlayerWaypointsEnabled(!config.isPlayerWaypointsEnabled());
            changedConfig = true;
            if (config.isPlayerWaypointsEnabled()) {
                imbg.setImage(Identifier.fromNamespaceAndPath("civmodern", "gui/toggleplayersoff.png"));
            } else {
                imbg.setImage(Identifier.fromNamespaceAndPath("civmodern", "gui/toggleplayers.png"));
            }
        });
        togglePlayers.setTooltip(Tooltip.create(Component.translatable("civmodern.map.players.tooltip")));
        addRenderableWidget(togglePlayers);

        toggleNodes = new ImageButton(this.width - 102, 10, 20, 20, nodeOverlayImage(), imbg -> {
            config.setNodeOverlayMode(config.getNodeOverlayMode().next());
            changedConfig = true;
            updateNodeOverlayButton(imbg);
        });
        updateNodeOverlayButton(toggleNodes);
        addRenderableWidget(toggleNodes);

        toggleBiomes = new ImageButton(this.width - 150, 10, 20, 20, biomeOverlayImage(), imbg -> {
            config.setBiomeOverlayEnabled(!config.isBiomeOverlayEnabled());
            changedConfig = true;
            updateBiomeOverlayButton(imbg);
            updateNodeOverlayButton(toggleNodes);
        });
        updateBiomeOverlayButton(toggleBiomes);
        addRenderableWidget(toggleBiomes);

        ImageButton managerButton = new ImageButton(this.width - 126, 10, 20, 20,
            Identifier.fromNamespaceAndPath("civmodern", "gui/manager.png"), imbg -> {
            Minecraft.getInstance().setScreen(new WaypointManagerScreen(this, waypoints));
        });
        managerButton.setTooltip(Tooltip.create(Component.translatable("civmodern.map.waypointmanager.tooltip")));
        addRenderableWidget(managerButton);

        ImageButton settingsButton = new ImageButton(this.width - 30, 10, 20, 20,
            Identifier.fromNamespaceAndPath("civmodern", "gui/settings.png"), imbg -> {
            Minecraft.getInstance().setScreen(mod.newConfigGui(this));
        });
        settingsButton.setTooltip(Tooltip.create(Component.translatable("civmodern.map.settings.tooltip")));
        addRenderableWidget(settingsButton);
    }

    /**
     * Deferred: the click that opens the modal also lands on this screen, which then focuses
     * the clicked widget, overwriting a setFocused done synchronously within the same click.
     */
    private void focusNewWaypointModal() {
        Minecraft.getInstance().execute(() -> {
            if (newWaypointModal.isVisible()) {
                setFocused(newWaypointModal);
                newWaypointModal.focusNameBox();
            }
        });
    }

    /** The icon carries the state: full for ON, ghosted for TRANSLUCENT, struck out for OFF. */
    private void updateNodeOverlayButton(ImageButton button) {
        button.setImage(nodeOverlayImage());
        button.setAlpha(config.getNodeOverlayMode() == NodeOverlayMode.TRANSLUCENT ? 0.5f : 1f);
        button.setTooltip(Tooltip.create(nodeOverlayTooltip()));
    }

    private Identifier nodeOverlayImage() {
        return Identifier.fromNamespaceAndPath("civmodern",
            config.getNodeOverlayMode().isVisible() ? "gui/nodes.png" : "gui/nodesoff.png");
    }

    private void updateBiomeOverlayButton(ImageButton button) {
        button.setImage(biomeOverlayImage());
        button.setTooltip(Tooltip.create(Component.translatable("civmodern.map.biomes.tooltip")));
    }

    private Identifier biomeOverlayImage() {
        return Identifier.fromNamespaceAndPath("civmodern",
            config.isBiomeOverlayEnabled() ? "gui/biome.png" : "gui/biomeoff.png");
    }

    /**
     * Explains that this button only hides the layer, and why nothing is drawn when the server is
     * not serving node data.
     */
    private Component nodeOverlayTooltip() {
        MutableComponent tooltip = Component.translatable("civmodern.map.nodes.tooltip");
        if (nodeApi != null && !nodeApi.isAvailable()) {
            tooltip.append(Component.literal("\n")).append(nodeApi.statusLine());
        } else if (!config.isNodeQueryEnabled()) {
            // Otherwise a frozen overlay looks like a bug rather than a setting.
            tooltip.append(Component.literal("\n"))
                .append(Component.translatable("civmodern.map.nodes.tooltip.frozen"));
        }
        return tooltip;
    }

    /**
     * Whether there is territory to draw. A handshake that never completed disables the feature
     * outright, so a server that does not serve node data draws nothing at all.
     */
    private boolean nodeOverlayActive() {
        return config.getNodeOverlayMode().isVisible() && nodeCache != null && nodeApi != null && nodeApi.isAvailable();
    }

    public void setNewWaypoint(Waypoint waypoint) {
        this.newWaypoint = waypoint;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        Matrix3x2fStack matrices = guiGraphics.pose();

        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale() * zoom;
        float waypointScale = waypointScale();
        Window window = Minecraft.getInstance().getWindow();

        if (!positionContextMenu.isVisible()) {
            this.mouseBlockX = (int) Math.floor(mouseX * scale + x);
            this.mouseBlockY = (int) Math.floor(mouseY * scale + y);
        }

        guiGraphics.fill(0, 0, window.getWidth(), window.getHeight(), 0xff000000);

        float renderY;
        List<BlitRenderState.Renderer> renderers = new ArrayList<>();
        for (int screenX = 0; screenX < (window.getWidth() * zoom) + SIZE; screenX += SIZE) {
            for (int screenY = 0; screenY < (window.getHeight() * zoom) + SIZE; screenY += SIZE) {
                float realX = (float) this.x + screenX;
                float realY = (float) this.y + screenY;

                float renderX = realX - floatMod(realX, SIZE);
                renderY = realY - floatMod(realY, SIZE);

                RegionKey key = new RegionKey(Math.floorDiv((int) renderX, SIZE), Math.floorDiv((int) renderY, SIZE));
                // todo if loading at low zoom, only render downsampled version to save memory
                RegionAtlasTexture texture = config.isBiomeOverlayEnabled() ? mapCache.getBiomeTexture(key) : mapCache.getTexture(key);
                if (texture != null) {
                    renderers.add(texture.draw(guiGraphics, renderX - (float) this.x, renderY - (float) this.y, scale));
                }
            }
        }
        guiGraphics.guiRenderState.submitPicturesInPictureState(new BlitRenderState(guiGraphics, 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), guiGraphics.pose(),
            ((source, stack) -> renderers.forEach(r -> r.render(source, stack)))));

        // The handshake can land or lapse while the map is open, so keep the button honest.
        if (toggleNodes != null && nodeApi != null && nodeApi.getState() != nodeTooltipState) {
            nodeTooltipState = nodeApi.getState();
            toggleNodes.setTooltip(Tooltip.create(nodeOverlayTooltip()));
        }

        // Node territory sits over the map tiles but under the waypoints and the chevron.
        if (nodeOverlayActive()) {
            NodeOverlayRenderer.render(guiGraphics, nodeCache, config, config.getNodeOverlayMode(), this.x, this.y,
                window.getGuiScaledWidth(), window.getGuiScaledHeight(), scale, null);
        }

        matrices.pushMatrix();
        matrices.translate(0, -1);

        List<Waypoint> waypointList = waypoints.getWaypoints();
        if (config.isWaypointRenderingEnabled()) {
            Map<String, List<Waypoint>> waypointByIcon = new HashMap<>();
            for (Waypoint waypoint : waypointList) {
                if (editWaypointModal.getWaypoint() == waypoint && editWaypointModal.hasChanged()) {
                    continue;
                }
                if (!waypoint.visible()) {
                    continue;
                }
                waypointByIcon.computeIfAbsent(waypoint.icon(), k -> new ArrayList<>()).add(waypoint);
            }

            for (List<Waypoint> waypointGroup : waypointByIcon.values()) {
                for (Waypoint waypoint : waypointGroup) {
                    matrices.pushMatrix();
                    double x = waypoint.x() + 0.5;
                    double z = waypoint.z() + 0.5;
                    matrices.translate((float) ((x - this.x) / scale), (float) ((z - this.y) / scale));
                    matrices.scale(waypointScale, waypointScale);

                    waypoint.render2D(guiGraphics);
                    matrices.popMatrix();
                }

                for (Waypoint waypoint : waypointGroup) {
                    if (waypoint.name().isBlank()) {
                        continue;
                    }
                    matrices.pushMatrix();
                    double x = waypoint.x() + 0.5;
                    double z = waypoint.z() + 0.5;
                    matrices.translate((float) ((x - this.x) / scale), (float) ((z - this.y) / scale));
                    matrices.scale(waypointScale, waypointScale);

                    Font font = Minecraft.getInstance().font;

                    String str = waypoint.name();

                    matrices.translate(0, -16);//, -10);
                    MutableComponent comp = Component.literal(str);
                    guiGraphics.drawString(font, comp, -font.width(comp) / 2, 0, -1, false);//, false, last, Font.DisplayMode.NORMAL, 0, 15728880, true);
                    guiGraphics.fill(-font.width(comp) / 2, -1, font.width(comp) / 2, 9, 1056964608);
                    matrices.popMatrix();
                }
            }
        }

        if (config.isPlayerWaypointsEnabled()) {
            for (PlayerWaypoint waypoint : this.playerWaypoints.getWaypoints()) {
                boolean old = waypoint.timestamp().until(Instant.now(), ChronoUnit.MINUTES) >= 10;
                int colour = (old ? 0x77 : 0xFF) << 24 | 0xFFFFFF;
                int bgcolour = (old ? 0x66 : 0xCC) << 24 | 0xCCCCCC;

                // TODO cycle between players on the same snitch
                matrices.pushMatrix();
                double x = waypoint.x() + 0.5;
                double z = waypoint.z() + 0.5;
                matrices.translate((float) ((x - this.x) / scale), (float) ((z - this.y) / scale));
                matrices.scale(waypointScale, waypointScale);
                waypoint.render(guiGraphics, colour);
                matrices.scale(0.8f, 0.8f);

                Font font = Minecraft.getInstance().font;

                String str = waypoint.playerName();

                matrices.translate(0, -16);
                if (zoom <= 2) {
                    MutableComponent comp = Component.literal(str);
                    guiGraphics.drawString(font, comp, (int) (-font.width(comp) / 2f), 0, colour, true);
                    MutableComponent comp2 = Component.literal("(" + getAgo(waypoint.timestamp()) + ")");
                    guiGraphics.drawString(font, comp2, (int) (-font.width(comp2) / 2f), 24, colour, true);
                }
                matrices.popMatrix();
            }
        }

//        RenderSystem.depthFunc(GL_LEQUAL);

        if (targeting || newWaypointModal.isTargeting()) {
            matrices.pushMatrix();
            matrices.translate(mouseX, mouseY);
            matrices.scale(waypointScale, waypointScale);

            Waypoint targetWaypoint = new Waypoint("", 0, 0, 0, targeting ? "target" : "waypoint", 0xFF0000);
            int transparency = newWaypointModal.isTargeting() ? 0x7F : 0xFF;
            targetWaypoint.render2D(guiGraphics, transparency);

            matrices.popMatrix();
        }

        if (newWaypointModal.isVisible()) {
            try {
                double x = newWaypointModal.getX() + 0.5;
                double z = newWaypointModal.getZ() + 0.5;

                matrices.pushMatrix();
                matrices.translate((float) ((x - this.x) / scale), (float) ((z - this.y) / scale));
                matrices.scale(waypointScale, waypointScale);

                Waypoint targetWaypoint = new Waypoint("", 0, 0, 0, "waypoint", newWaypointModal.getPreviewColour());
                targetWaypoint.render2D(guiGraphics);

                matrices.popMatrix();
            } catch (NumberFormatException ignored) {
            }
        }

        if (hoveredWaypoint != null) {
            matrices.pushMatrix();
            double x = hoveredWaypoint.x() + 0.5;
            double z = hoveredWaypoint.z() + 0.5;
            matrices.translate((float) ((x - this.x) / scale), (float) ((z - this.y) / scale));
            matrices.scale(waypointScale, waypointScale);

            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("civmodern", "map/focus.png"), -8, -8, 0, 0, 16, 16, 16, 16, -1);

            matrices.popMatrix();
        } else if (!targeting && !editWaypointModal.isTargeting() && !newWaypointModal.isTargeting()) {
            matrices.pushMatrix();

            matrices.translate((float) ((mouseBlockX - this.x) / scale), (float) ((mouseBlockY - this.y) / scale + 1));

            matrices.scale(1 / scale, 1 / scale);
            int size = 1;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("civmodern", "map/focus.png"), 0, 0, 0, 0, size, size, size, size, -1);
            matrices.popMatrix();
        }

        if (editWaypointModal.isTargeting() || (editWaypointModal.getWaypoint() != null && editWaypointModal.hasChanged())) {
            matrices.pushMatrix();
            if (editWaypointModal.isTargeting()) {
                matrices.translate(mouseX, mouseY);
            } else {
                double x = editWaypointModal.getX() + 0.5;
                double z = editWaypointModal.getZ() + 0.5;
                matrices.translate((float) ((x - this.x) / scale), (float) ((z - this.y) / scale));
            }
            matrices.scale(waypointScale, waypointScale);

            Waypoint targetWaypoint = new Waypoint("", 0, 0, 0, editWaypointModal.getWaypoint().icon(), editWaypointModal.getPreviewColour());
            if (editWaypointModal.getPreviewColour() != editWaypointModal.getColour()) {
                targetWaypoint.render2D(guiGraphics);
            } else {
                targetWaypoint.render2D(guiGraphics, 0x7f);
            }

            Font font = Minecraft.getInstance().font;

            String str = editWaypointModal.getName();
            if (!str.isBlank()) {
                matrices.translate(0, -16);
                Component comp = Component.literal(str);
                guiGraphics.drawString(font, comp, -font.width(comp) / 2, 0, -1);
                guiGraphics.fill(-font.width(comp) / 2, -1, font.width(comp) / 2, 9, 1056964608);
            }
            matrices.popMatrix();
        }

        LocalPlayer player = Minecraft.getInstance().player;
        float prx = (float) (player.getX() - this.x) / scale;
        float pry = (float) (player.getZ() - this.y) / scale;
        matrices.pushMatrix();
        int chevron = 0xFF000000 | mod.getColourProvider().getChevronColour();
        matrices.translate(prx, pry);
        matrices.scale(4, 4);
        matrices.rotate((float) Math.toRadians(player.getViewYRot(delta) % 360f));
        guiGraphics.guiRenderState.submitGuiElement(new ChevronRenderState(
            CivModernPipelines.GUI_TRIANGLE_STRIP_BLEND,
            new Matrix3x2f(guiGraphics.pose()),
            guiGraphics.scissorStack.peek(),
            chevron));
        matrices.popMatrix();


        Queue<Vec2> dests = navigation.getDestinations();
        if (boating || !dests.isEmpty()) {
            guiGraphics.guiRenderState.nextStratum();
            List<Vec2> points = new ArrayList<>();
            float px;
            float pz;
            if (player.getVehicle() != null) {
                px = (float) Mth.lerp(delta, player.getVehicle().xOld, player.getVehicle().getX());
                pz = (float) Mth.lerp(delta, player.getVehicle().zOld, player.getVehicle().getZ());
            } else {
                px = (float) player.getX();
                pz = (float) player.getZ();
            }
            points.add(new Vec2(px, pz));
            points.addAll(dests);
            if (boating) {
                points.add(new Vec2(mouseX * scale + (float) x, mouseY * scale + (float) y));
            }

            for (int i = 0; i < points.size() - 1; i++) {
                Vec2 from = points.get(i);
                Vec2 to = points.get(i + 1);

                double dx = (to.x - x) / scale - (from.x - x) / scale;
                double dy = (to.y - y) / scale - (from.y - y) / scale;
                float dist = (float) Mth.length(dx, dy) + 0.5f;

                matrices.pushMatrix();
                matrices.translate((float) ((to.x - x) / scale), (float) ((to.y - y) / scale));
                guiGraphics.pose().rotate(((float) Mth.atan2(dx, -dy)));
                guiGraphics.guiRenderState.submitGuiElement(new LineElementRenderState(
                    CivModernPipelines.GUI_QUADS,
                    new Matrix3x2f(guiGraphics.pose()),
                    guiGraphics.scissorStack.peek(),
                    0, 0, 0, (int) dist, 1, Color.ofArgb(BOAT_PREVIEW_LINE_COLOUR)
                ));
                matrices.popMatrix();
            }
        }

        matrices.popMatrix();

        matrices.pushMatrix();
        float textScale = 1f;
        matrices.scale(textScale, textScale);
        int px = (int) Math.floor(mouseX * scale + (float) x);
        int pz = (int) Math.floor(mouseY * scale + (float) y);
        RegionKey key = mapCache.getRegionKey(px, pz);
        if (this.yLevelInterests.add(key)) {
            mapCache.addReference(key);
        }
        Short y = mapCache.getYLevel(px, pz);
        guiGraphics.drawCenteredString(font, "(%d, %s, %d)".formatted(px, y == null ? "?" : Short.toString(y), pz), (int) (this.width / 2 / textScale), (int) ((this.height - 16) / textScale), -1);

        matrices.popMatrix();

        for (Renderable renderable : ((ScreenAccessor) this).civmodern$getRenderables()) {
            renderable.render(guiGraphics, mouseX, mouseY, delta);
        }

        // Set after the widgets render: the frame's first tooltip wins the slot (a later set is
        // dropped unless the widget is focused), so a hovered toolbar button has claimed it by
        // now and the node tooltip only fills in when nothing else did.
        if (nodeOverlayActive() && hoveredWaypoint == null && !newWaypointModal.isVisible()
            && !editWaypointModal.isVisible() && !positionContextMenu.isVisible() && !highlightContextMenu.isVisible()) {
            List<Component> lines = NodeOverlayRenderer.tooltip(nodeCache, config, mouseBlockX >> 4, mouseBlockY >> 4);
            if (!lines.isEmpty()) {
                guiGraphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        } else if (config.isBiomeOverlayEnabled() && hoveredWaypoint == null && !newWaypointModal.isVisible()
            && !editWaypointModal.isVisible() && !positionContextMenu.isVisible() && !highlightContextMenu.isVisible()) {
            Component tooltip = biomeTooltip(mouseBlockX, mouseBlockY);
            if (tooltip != null) {
                guiGraphics.setComponentTooltipForNextFrame(font, List.of(tooltip), mouseX, mouseY);
            }
        }
    }

    /**
     * The biome's own vanilla translation ("biome.minecraft.plains"), so a datapack biome with its
     * own lang entry resolves correctly too. {@code null} where nothing is recorded there
     * ({@link MapCache#biomeNameAt} already folds "legacy region" and "not yet loaded" together),
     * so both simply show no tooltip.
     */
    private Component biomeTooltip(int blockX, int blockZ) {
        String biomeName = mapCache.biomeNameAt(blockX, blockZ);
        if (biomeName == null) {
            return null;
        }
        int colon = biomeName.indexOf(':');
        if (colon < 0) {
            return Component.literal(biomeName);
        }
        return Component.translatable("biome." + biomeName.substring(0, colon) + "." + biomeName.substring(colon + 1));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            return true;
        }

        // 0 = left click
        // 1 = right click
        // 2 = middle click

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        Window window = Minecraft.getInstance().getWindow();
        float scale = (float) window.getGuiScale() * zoom;
        if ((targeting || newWaypointModal.isTargeting() || editWaypointModal.isTargeting()) && button == 0) {
            double mouseWorldX = (mouseX * scale + x) - 0.5;
            double mouseWorldY = (mouseY * scale + y) - 0.5;

            int x = (int) Math.round(mouseWorldX);
            int z = (int) Math.round(mouseWorldY);
            Short yLevel = mapCache.getYLevel(x, z);
            if (newWaypointModal.isTargeting()) {
                newWaypointModal.setTargetResult(x, yLevel == null ? newWaypointModal.getY() : yLevel + 2, z);
            } else if (editWaypointModal.isTargeting()) {
                editWaypointModal.setTargetResult(x, yLevel == null ? newWaypointModal.getY() : yLevel + 2, z);
            } else {
                this.waypoints.setTarget(new Waypoint("", x, yLevel == null ? 64 : yLevel + 2, z, "target", 0xFF0000));
                targeting = false;
            }
            return true;
        }

        if (newWaypointModal.isVisible() && button == 1) {
            newWaypointModal.setVisible(false);
            return true;
        } else if (editWaypointModal.isVisible() && button == 1) {
            editWaypointModal.setVisible(false);
            editWaypointModal.setWaypoint(null);
            return true;
        } else if (highlightContextMenu.isVisible() && button == 1) {
            highlightContextMenu.setVisible(false);
            return true;
        }

        if (boating && button == 1) {
            double mouseWorldX = (mouseX * scale + x);
            double mouseWorldY = (mouseY * scale + y);
            if (event.hasShiftDown()) {
                this.navigation.getDestinations().pollLast();
            } else if (event.hasControlDown()) {
                this.navigation.reset();
            } else {
                this.navigation.addDestination(new Vec2((float) mouseWorldX, (float) mouseWorldY));
            }
            return true;
        }

        if (hoveredWaypoint != null && button == 0) {
            if (hoveredWaypoint.equals(waypoints.getTarget())) {
                editWaypointModal.setVisible(false);
                editWaypointModal.setWaypoint(null);
                newWaypointModal.setVisible(false);
                highlightContextMenu.open(hoveredWaypoint, (int) mouseX, (int) mouseY);
                highlightContextMenu.setVisible(true);
            } else if (editWaypointModal.getWaypoint() != hoveredWaypoint) {
                editWaypointModal.setWaypoint(hoveredWaypoint);
                editWaypointModal.setVisible(true);
                newWaypointModal.setVisible(false);
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (super.mouseReleased(event)) {
            return true;
        }

        double x = event.x();
        double y = event.y();
        int button = event.button();

        Window window = Minecraft.getInstance().getWindow();
        float scale = (float) window.getGuiScale() * zoom;

        if (!boating) {
            if (hoveredWaypoint == null && button == 1 && !positionContextMenu.isVisible() && !highlightContextMenu.isVisible()) {
                Short yLevel = mapCache.getYLevel(this.mouseBlockX, this.mouseBlockY);
                positionContextMenu.open(this.mouseBlockX, yLevel, this.mouseBlockY, (int) ((this.mouseBlockX - this.x) / scale), (int) ((this.mouseBlockY - this.y + 1) / scale + 1));
                positionContextMenu.setVisible(true);
                return true;
            } else if (positionContextMenu.isVisible() && !positionContextMenu.isMouseOver(x, y)) {
                positionContextMenu.setVisible(false);
                return true;
            } else if (highlightContextMenu.isVisible() && !highlightContextMenu.isMouseOver(x, y)) {
                highlightContextMenu.setVisible(false);
                return true;
            }
        }

        return false;
    }

    /**
     * How much smaller than their native size waypoint icons/labels are currently drawn,
     * per {@link CivMapConfig#getWaypointBaseZoom()}/{@link CivMapConfig#getWaypointZoomLogBase()}.
     * Shared by render() (to scale the drawing) and mouseMoved() (to scale the hitbox to match).
     */
    private float waypointScale() {
        float zoomSteps = (float) (Math.log(zoom / config.getWaypointBaseZoom()) / Math.log(config.getWaypointZoomLogBase()));
        return 1f / (1f + Math.max(0f, zoomSteps));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        Window window = Minecraft.getInstance().getWindow();
        float scale = (float) window.getGuiScale() * zoom;
        float waypointScale = waypointScale();

        List<Waypoint> waypointList = waypoints.getWaypoints();
        Waypoint closest = null;
        double mouseWorldX = (mouseX * scale + x);
        double mouseWorldY = (mouseY * scale + y);
        hoveredWaypoint = null;
        for (Waypoint waypoint : waypointList) {
            if (!waypoint.visible()) {
                continue;
            }
            if (closest == null) {
                closest = waypoint;
            } else if (Math.abs(waypoint.x() - mouseWorldX) + Math.abs(waypoint.z() - mouseWorldY) < Math.abs(closest.x() - mouseWorldX) + Math.abs(closest.z() - mouseWorldY)) {
                closest = waypoint;
            }
        }
        if (closest != null) {
            double offsetX = (closest.x() + 0.5 - mouseWorldX) / scale;
            double offsetY = (closest.z() + 0.5 - mouseWorldY) / scale;
            double hitboxHalfSize = 8 * waypointScale;
            if (Math.abs(offsetX) < hitboxHalfSize && Math.abs(offsetY) < hitboxHalfSize) {
                hoveredWaypoint = closest;
            }
        }

        editWaypointModal.mouseMoved(mouseX, mouseY);
        newWaypointModal.mouseMoved(mouseX, mouseY);

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDirY, double scrollDir) {
        if (super.mouseScrolled(mouseX, mouseY, scrollDirY, scrollDir)) {
            return true;
        }

        if (scrollDir < 0 && zoom < config.getMaxZoom()) {
            // zoom out
            Window window = Minecraft.getInstance().getWindow();
            float scale = (float) window.getGuiScale() * zoom;

            double centreX = x + window.getWidth() * zoom * 0.5;
            double centreY = y + window.getHeight() * zoom * 0.5;

            double mouseWorldX = (mouseX * scale + x);
            double mouseWorldY = (mouseY * scale + y);

            zoom *= 2;

            x = (mouseWorldX - (mouseWorldX - centreX) / 0.5 - (window.getWidth() * zoom) / 2);
            y = (mouseWorldY - (mouseWorldY - centreY) / 0.5 - (window.getHeight() * zoom) / 2);
        } else if (scrollDir > 0 && zoom > 0.03125) {
            // zoom in
            Window window = Minecraft.getInstance().getWindow();
            float scale = (float) window.getGuiScale() * zoom;

            double centreX = x + window.getWidth() * zoom * 0.5;
            double centreY = y + window.getHeight() * zoom * 0.5;

            double mouseWorldX = (mouseX * scale + x);
            double mouseWorldY = (mouseY * scale + y);

            zoom /= 2;

            x = (mouseWorldX - (mouseWorldX - centreX) * 0.5 - (window.getWidth() * zoom) / 2);
            y = (mouseWorldY - (mouseWorldY - centreY) * 0.5 - (window.getHeight() * zoom) / 2);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double changeX, double changeY) {
        if (super.mouseDragged(event, changeX, changeY)) {
            return true;
        }

        double x = event.x();
        double y = event.y();
        int button = event.button();

        if (positionContextMenu.isVisible() && !positionContextMenu.isMouseOver(x, y)) {
            positionContextMenu.setVisible(false);
            return true;
        }

        if (highlightContextMenu.isVisible() && !highlightContextMenu.isMouseOver(x, y)) {
            highlightContextMenu.setVisible(false);
            return true;
        }

        // While boating, right-click places route points rather than panning - see mouseClicked.
        if (button == 0 || (button == 1 && !boating)) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale() * zoom;
            this.x -= changeX * scale;
            this.y -= changeY * scale;
            return true;
        }
        return false;
        // 0 = left
        // 1 = right
        // 2 = middle
    }

    private float floatMod(float x, float y) {
        // x mod y behaving the same way as Math.floorMod but with floats
        return (x - (float) Math.floor(x / y) * y);
    }

    @Override
    public void added() {
        this.yLevelInterests.clear();
    }

    @Override
    public void removed() {
        for (RegionKey key : this.yLevelInterests) {
            mapCache.removeReference(key);
        }
        this.yLevelInterests.clear();
        if (changedConfig) {
            config.save();
        }
    }

    public static String getAgo(Instant timestamp) {
        Instant now = Instant.now();
        long minutesDiff = timestamp.until(now, ChronoUnit.MINUTES);
        if (minutesDiff > 0) {
            return minutesDiff + "m ago";
        }
        long secondsDiff = timestamp.until(now, ChronoUnit.SECONDS);
        long lastDigit = secondsDiff % 10;
        secondsDiff -= lastDigit;
        if (secondsDiff < 10) {
            return "now";
        }
        return secondsDiff + "s ago";
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Esc must close just the open modal, not the map behind it - vanilla Screen would
        // otherwise treat it as unhandled and close the whole screen.
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (newWaypointModal.isVisible()) {
                newWaypointModal.cancel();
                return true;
            }
            if (editWaypointModal.isVisible()) {
                editWaypointModal.cancel();
                return true;
            }
        }
        if (this.key.matches(event) && !newWaypointModal.isVisible() && !editWaypointModal.isVisible()) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }
}

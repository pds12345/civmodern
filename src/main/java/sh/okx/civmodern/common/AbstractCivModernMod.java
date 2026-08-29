package sh.okx.civmodern.common;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.navigation.AutoNavigation;
import sh.okx.civmodern.common.events.*;
import sh.okx.civmodern.common.gui.screen.MainConfigScreen;
import sh.okx.civmodern.common.macro.AttackMacro;
import sh.okx.civmodern.common.macro.HoldKeyMacro;
import sh.okx.civmodern.common.macro.IceRoadMacro;
import sh.okx.civmodern.common.map.*;
import sh.okx.civmodern.common.map.nodes.NodeApiClient;
import sh.okx.civmodern.common.map.nodes.NodeInfo;
import sh.okx.civmodern.common.map.nodes.NodeProtocol;
import sh.okx.civmodern.common.map.screen.MapScreen;
import sh.okx.civmodern.common.map.screen.QuickWaypointScreen;
import sh.okx.civmodern.common.map.waypoints.Waypoint;
import sh.okx.civmodern.common.parser.ParsedWaypoint;
import sh.okx.civmodern.common.radar.Radar;
import sh.okx.civmodern.common.rendering.BlitRenderer;
import sh.okx.civmodern.common.rendering.CivModernPipelines;

public abstract class AbstractCivModernMod {

    private static AbstractCivModernMod INSTANCE;
    public static final Logger LOGGER = LogManager.getLogger();

    private static final KeyMapping.Category CIVMODERN_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("civmodern", "category"));

    private final KeyMapping configBinding;
    private final KeyMapping holdLeftBinding;
    private final KeyMapping holdRightBinding;
    private final KeyMapping iceRoadBinding;
    private final KeyMapping attackBinding;

    private final KeyMapping mapBinding;
    private final KeyMapping minimapZoomBinding;
    private final KeyMapping newWaypointBinding;
    private final KeyMapping minimapNodesBinding;

    private CivMapConfig config;
    private ColourProvider colourProvider;
    private Radar radar;

    private WorldListener worlds;
    private AutoNavigation autoNavigation;
    private NodeApiClient nodeApi;

    public final EventBus eventBus = new EventBus("CivModernEvents");

    public AbstractCivModernMod() {
        this.configBinding = new KeyMapping(
            "key.civmodern.config",
            Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CIVMODERN_CATEGORY
        );
        this.holdLeftBinding = new KeyMapping(
            "key.civmodern.left",
            Type.KEYSYM,
            GLFW.GLFW_KEY_MINUS,
            CIVMODERN_CATEGORY
        );
        this.holdRightBinding = new KeyMapping(
            "key.civmodern.right",
            Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            CIVMODERN_CATEGORY
        );
        this.iceRoadBinding = new KeyMapping(
            "key.civmodern.ice",
            Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSPACE,
            CIVMODERN_CATEGORY
        );
        this.attackBinding = new KeyMapping(
            "key.civmodern.attack",
            Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            CIVMODERN_CATEGORY
        );
        this.mapBinding = new KeyMapping(
            "key.civmodern.map",
            Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CIVMODERN_CATEGORY
        );
        this.minimapZoomBinding = new KeyMapping(
            "key.civmodern.minimapzoom",
            Type.KEYSYM,
            GLFW.GLFW_KEY_KP_DIVIDE,
            CIVMODERN_CATEGORY
        );
        this.newWaypointBinding = new KeyMapping(
            "key.civmodern.newwaypoint",
            Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CIVMODERN_CATEGORY
        );
        this.minimapNodesBinding = new KeyMapping(
            "key.civmodern.minimapnodes",
            Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            CIVMODERN_CATEGORY
        );


        if (INSTANCE == null) {
            INSTANCE = this;
        } else {
            throw new IllegalStateException("AbstractCivModernMod initialised twice");
        }
    }

    public final void init() {
        SpecialGuiElementRegistry.register(ctx -> new BlitRenderer(ctx.vertexConsumers()));
        CivModernPipelines.register();

        registerKeyBinding(this.configBinding);
        registerKeyBinding(this.holdLeftBinding);
        registerKeyBinding(this.holdRightBinding);
        registerKeyBinding(this.attackBinding);
        registerKeyBinding(this.iceRoadBinding);
        registerKeyBinding(this.mapBinding);
        registerKeyBinding(this.minimapZoomBinding);
        registerKeyBinding(this.newWaypointBinding);
        registerKeyBinding(this.minimapNodesBinding);
    }

    public final void enable() {
        loadConfig();
        loadRadar();

        this.worlds = new WorldListener(config, colourProvider);
        this.nodeApi = new NodeApiClient(config);

        this.eventBus.register(this);

        this.eventBus.register(this.worlds);

        this.eventBus.register(this.radar);

        this.eventBus.register(this.nodeApi);

        Options options = Minecraft.getInstance().options;
        this.eventBus.register(new HoldKeyMacro(this.holdLeftBinding, options.keyAttack));
        this.eventBus.register(new HoldKeyMacro(this.holdRightBinding, options.keyUse));
        this.eventBus.register(new IceRoadMacro(this.config, this.iceRoadBinding));
        this.eventBus.register(new AttackMacro(this.attackBinding, options.keyAttack));

        this.autoNavigation = new AutoNavigation(this);
    }

    public abstract void registerKeyBinding(KeyMapping mapping);

    @Subscribe
    private void registerCommands(CommandRegistration registration) {
        registration.dispatcher().register(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("civmodern_openwaypoint").then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("data", StringArgumentType.greedyString()).executes(context -> {
            ParsedWaypoint parsed = ParsedWaypoint.parseWaypoints(StringArgumentType.getString(context, "data")).getFirst();
            if (parsed == null) {
                return 0;
            }
            Waypoint waypoint = new Waypoint(
                parsed.name(),
                parsed.x(),
                parsed.y(),
                parsed.z(),
                "target",
                0xFF0000
            );
            if (!InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
                this.worlds.getWaypoints().setTarget(waypoint);
            } else {
                MapScreen screen = new MapScreen(this, this.mapBinding, config, worlds.getCache(), worlds.getNodes(), nodeApi, autoNavigation, worlds.getWaypoints(), worlds.getPlayerWaypoints());
                screen.setNewWaypoint(waypoint);
                Minecraft.getInstance().setScreen(screen);
            }
            return 0;
        })));

        // Client-side mirror of the server's /nodeapidump: shows what our decoder made of the
        // last S2C_REGION, so a disagreement with /nodeprint can be pinned on one side or the other.
        registration.dispatcher().register(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("civmodern_nodedump").executes(context -> {
            dumpLastRegion();
            return 0;
        }));
    }

    private void dumpLastRegion() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        NodeProtocol.Region region = this.nodeApi == null ? null : this.nodeApi.getLastRegion();
        if (region == null) {
            player.displayClientMessage(Component.translatable("civmodern.nodes.dump.none"), false);
            return;
        }

        player.displayClientMessage(Component.literal("civnodes:v1 last region — origin %d,%d size %d, %d palette entries, %d byte frame"
            .formatted(region.originChunkX(), region.originChunkZ(), region.size(), region.palette().length, region.frameLength())), false);

        for (int i = 0; i < region.palette().length; i++) {
            NodeInfo node = region.palette()[i];
            player.displayClientMessage(Component.literal("  [%d] id=%d flags=0x%02X colour=%d name=%s group=%s%s"
                .formatted(i, node.nodeId(), node.flags(), node.colorIndex(),
                    node.name() == null ? "-" : node.name(),
                    node.groupName() == null ? "-" : node.groupName(),
                    node.bastionInWindow() ? " bastion=%d,%d".formatted(node.bastionChunkX(), node.bastionChunkZ()) : "")), false);
        }

        // Upper case means protected, '.' means no node owns the chunk.
        for (int dz = 0; dz < region.size(); dz++) {
            StringBuilder row = new StringBuilder(region.size());
            for (int dx = 0; dx < region.size(); dx++) {
                int index = region.indexAt(dx, dz);
                if (index == NodeProtocol.NO_NODE) {
                    row.append('.');
                } else {
                    char c = (char) ('a' + (index % 26));
                    row.append(region.isProtected(dx, dz) ? Character.toUpperCase(c) : c);
                }
            }
            player.displayClientMessage(Component.literal(row.toString()), false);
        }
    }

    @Subscribe
    private void tick(
        final @NotNull ClientTickEvent event
    ) {
        while (this.configBinding.consumeClick()) {
            Minecraft.getInstance().setScreen(newConfigGui(null));
        }
        while (mapBinding.consumeClick()) {
            if (worlds.getCache() != null) {
                Minecraft.getInstance().setScreen(new MapScreen(this, this.mapBinding, config, worlds.getCache(), worlds.getNodes(), nodeApi, autoNavigation, worlds.getWaypoints(), worlds.getPlayerWaypoints()));
            }
        }
        while (minimapZoomBinding.consumeClick()) {
            worlds.cycleMinimapZoom();
        }
        while (newWaypointBinding.consumeClick()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && worlds.getWaypoints() != null) {
                Minecraft.getInstance().setScreen(new QuickWaypointScreen(worlds.getWaypoints()));
            }
        }
        while (minimapNodesBinding.consumeClick()) {
            // Cycles off -> solid -> translucent -> off. Saved at once, since unlike the map
            // screen's toggle there is no screen-close moment to piggyback the save on.
            config.setMinimapNodeOverlayMode(config.getMinimapNodeOverlayMode().next());
            config.save();
        }
    }

    private void loadConfig() {
        Properties properties = new Properties();
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        File configFile = gameDir.resolve("config").resolve("civmodern.properties").toFile();
        try {
            if (!configFile.exists()) {
                InputStream resource = AbstractCivModernMod.class
                    .getResourceAsStream("/civmodern.properties");
                byte[] buffer = new byte[resource.available()];
                resource.read(buffer);
                FileOutputStream fos = new FileOutputStream(configFile);
                fos.write(buffer);
            }

            FileInputStream input = new FileInputStream(configFile);
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        this.config = new CivMapConfig(configFile, properties);

    }

    private void loadRadar() {
        this.colourProvider = new ColourProvider(config);
        this.radar = new Radar(config, colourProvider);
    }

    public ColourProvider getColourProvider() {
        return colourProvider;
    }

    public @NotNull Screen newConfigGui(
        final Screen previousScreen
    ) {
        return new MainConfigScreen(this.config, this.colourProvider, previousScreen);
    }

    public WorldListener getWorldListener() {
        return worlds;
    }

    public NodeApiClient getNodeApi() {
        return nodeApi;
    }

    public CivMapConfig getConfig() {
        return config;
    }

    public static AbstractCivModernMod getInstance() {
        return INSTANCE;
    }
}

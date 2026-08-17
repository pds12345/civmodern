package sh.okx.civmodern.common.map.nodes;

import com.google.common.eventbus.Subscribe;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.events.ClientTickEvent;
import sh.okx.civmodern.common.events.JoinEvent;
import sh.okx.civmodern.common.events.LeaveEvent;

import java.io.IOException;

/**
 * Owns the civnodes:v1 session: the handshake, the per-connection limits, and the query pacing.
 *
 * <p>The overlay is served only in {@link State#READY}. Every other state — no plugin on the
 * server, a world without nodes, an operator who turned the API off — leaves the overlay
 * unavailable and sends nothing.
 */
public class NodeApiClient {

    private static final String MOD_ID = "civmodern";

    /** How long to wait for the server to greet us before declaring the API absent. */
    private static final long HANDSHAKE_TIMEOUT_MS = 10_000;

    /** Re-ask for the current window this often, so decaying protection is visible standing still. */
    private static final long REFRESH_INTERVAL_MS = 15_000;

    /** Never out-pace this, whatever the server reports. */
    private static final long MIN_QUERY_INTERVAL_MS = 250;

    public enum State {
        /** Not in game, or nothing has happened yet. */
        IDLE,
        /** Registered the channel; waiting for the server to greet us. */
        AWAITING_HELLO,
        /** Sent C2S_HELLO; waiting for the acknowledging S2C_HELLO. */
        AWAITING_ACK,
        /** Handshaked. The overlay is live. */
        READY,
        /** Handshaked, but this world has no node map. */
        NO_NODES,
        /** The operator turned the API off, or rejected our version. */
        SERVER_DISABLED,
        /** Nobody answered. Either not a CivNodes server, or the greeting was lost. */
        TIMED_OUT
    }

    private final CivMapConfig config;

    private volatile State state = State.IDLE;
    private long deadline;

    private int serverMinor;
    private int maxSize = 31;
    private int minQueryIntervalMs = 500;
    private boolean worldHasNodes;
    private String worldName;

    private long lastQueryAt;
    private int lastQueryChunkX = Integer.MIN_VALUE;
    private int lastQueryChunkZ = Integer.MIN_VALUE;

    private boolean sentHello;
    private boolean retriedAfterMalformed;
    private boolean warnedTimedOut;

    /** Kept purely so {@code /civmodern_nodedump} can show what we last decoded. */
    private NodeProtocol.Region lastRegion;

    public NodeApiClient(CivMapConfig config) {
        this.config = config;
    }

    public State getState() {
        return state;
    }

    /** Whether there is anything worth drawing, i.e. the handshake completed on a node world. */
    public boolean isAvailable() {
        return state == State.READY;
    }

    public NodeProtocol.Region getLastRegion() {
        return lastRegion;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public String getWorldName() {
        return worldName;
    }

    // ---------------------------------------------------------------- lifecycle

    @Subscribe
    public void onJoin(JoinEvent event) {
        reset();
        // Fabric sends minecraft:register for us just after this event fires, which is what
        // prompts the server's unprompted greeting.
        state = State.AWAITING_HELLO;
        deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
    }

    @Subscribe
    public void onLeave(LeaveEvent event) {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        serverMinor = 0;
        maxSize = 31;
        minQueryIntervalMs = 500;
        worldHasNodes = false;
        worldName = null;
        lastQueryAt = 0;
        lastQueryChunkX = Integer.MIN_VALUE;
        lastQueryChunkZ = Integer.MIN_VALUE;
        sentHello = false;
        retriedAfterMalformed = false;
        warnedTimedOut = false;
        lastRegion = null;
    }

    /**
     * Re-runs the handshake by hand, for when the automatic one at join did not take.
     * Unregistering and re-registering the receiver makes Fabric emit
     * {@code minecraft:unregister} then {@code minecraft:register}, and the server greets a
     * fresh registration.
     *
     * @return a message describing what happened, for the config screen
     */
    public Component retryHandshake() {
        if (Minecraft.getInstance().getConnection() == null) {
            return Component.translatable("civmodern.nodes.handshake.notingame").withStyle(ChatFormatting.RED);
        }
        try {
            ClientPlayNetworking.unregisterReceiver(CivNodesPayload.CHANNEL);
            ClientPlayNetworking.registerReceiver(CivNodesPayload.TYPE,
                (payload, context) -> handle(payload.data()));
        } catch (RuntimeException e) {
            AbstractCivModernMod.LOGGER.warn("Re-registering " + CivNodesPayload.CHANNEL, e);
            // The unregister may already have gone out, so nothing will arrive on this channel
            // again. Say so rather than leaving a stale "connected" state on screen.
            reset();
            state = State.TIMED_OUT;
            return Component.translatable("civmodern.nodes.handshake.failed").withStyle(ChatFormatting.RED);
        }

        reset();
        state = State.AWAITING_HELLO;
        deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
        return Component.translatable("civmodern.nodes.handshake.sent").withStyle(ChatFormatting.YELLOW);
    }

    // ---------------------------------------------------------------- inbound

    /**
     * Handles one inbound frame. Fabric calls payload handlers on the render thread, so this is
     * free to touch client state directly.
     */
    public void handle(byte[] payload) {
        NodeProtocol.Frame frame;
        try {
            frame = NodeProtocol.decode(payload);
        } catch (IOException | RuntimeException e) {
            AbstractCivModernMod.LOGGER.warn("Malformed civnodes frame of " + payload.length + " bytes", e);
            return;
        }

        if (frame == null) {
            // An id from a future minor version. Skipping it is the documented behaviour.
            return;
        }

        if (frame instanceof NodeProtocol.Hello hello) {
            onHello(hello);
        } else if (frame instanceof NodeProtocol.Region region) {
            onRegion(region);
        } else if (frame instanceof NodeProtocol.ErrorFrame error) {
            onError(error);
        }
    }

    private void onHello(NodeProtocol.Hello hello) {
        this.serverMinor = hello.serverMinor();
        this.maxSize = Math.max(3, hello.maxSize());
        this.minQueryIntervalMs = Math.max(0, hello.minQueryIntervalMs());
        this.worldHasNodes = hello.worldHasNodes();
        this.worldName = hello.worldName();

        NodeCache cache = cache();
        if (cache != null) {
            cache.setWorldName(hello.worldName());
        }

        if (!sentHello) {
            // Covers the greeting we expect at join, and a late one arriving after we gave up:
            // either way the server will answer queries with MALFORMED until it has our hello.
            send(NodeProtocol.encodeHello(MOD_ID));
            sentHello = true;
            state = State.AWAITING_ACK;
            deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
        } else if (state == State.AWAITING_ACK) {
            AbstractCivModernMod.LOGGER.info(
                "civnodes:v1 handshake complete (server minor {}, max window {}, {} ms, world '{}', has nodes: {})",
                serverMinor, maxSize, minQueryIntervalMs, worldName, worldHasNodes);
            settle();
        } else {
            // A world change re-greets us mid-session; just take the new limits.
            settle();
        }
    }

    private void settle() {
        state = worldHasNodes ? State.READY : State.NO_NODES;
        // The window moved with us, so the next tick should query regardless of where we stand.
        lastQueryChunkX = Integer.MIN_VALUE;
        lastQueryChunkZ = Integer.MIN_VALUE;
    }

    private void onRegion(NodeProtocol.Region region) {
        this.lastRegion = region;
        NodeCache cache = cache();
        if (cache != null) {
            cache.apply(region);
        }
    }

    private void onError(NodeProtocol.ErrorFrame error) {
        switch (error.reason()) {
            case DISABLED -> {
                AbstractCivModernMod.LOGGER.info("civnodes:v1 is disabled on this server");
                state = State.SERVER_DISABLED;
            }
            case NO_NODES_HERE -> state = State.NO_NODES;
            case MALFORMED -> {
                // Either we queried before handshaking, or a frame did not decode. One retry.
                if (retriedAfterMalformed) {
                    AbstractCivModernMod.LOGGER.warn("civnodes:v1 rejected our frames twice, giving up");
                    state = State.SERVER_DISABLED;
                } else {
                    retriedAfterMalformed = true;
                    AbstractCivModernMod.LOGGER.warn("civnodes:v1 rejected a frame, re-running the handshake");
                    send(NodeProtocol.encodeHello(MOD_ID));
                    state = State.AWAITING_ACK;
                    deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
                }
            }
            case UNSUPPORTED_VERSION -> {
                AbstractCivModernMod.LOGGER.warn("civnodes:v1 rejected our version");
                state = State.SERVER_DISABLED;
            }
            case UNKNOWN -> {
                // A reason from a future minor version. Nothing sensible to do but note it.
                AbstractCivModernMod.LOGGER.info("civnodes:v1 returned an error we do not recognise");
            }
        }
    }

    // ---------------------------------------------------------------- outbound

    @Subscribe
    public void onTick(ClientTickEvent event) {
        long now = System.currentTimeMillis();

        if ((state == State.AWAITING_HELLO || state == State.AWAITING_ACK) && now > deadline) {
            state = State.TIMED_OUT;
            if (!warnedTimedOut) {
                warnedTimedOut = true;
                // Logged, not announced in chat: most servers are not CivNodes servers, and a
                // line every join would be noise. The map button tooltip and the config screen
                // both show this state to anyone who goes looking for the overlay.
                AbstractCivModernMod.LOGGER.info(
                    "No civnodes:v1 greeting after {} ms; the node overlay is unavailable here", HANDSHAKE_TIMEOUT_MS);
            }
            return;
        }

        maybeQuery(now);
    }

    private void maybeQuery(long now) {
        // The toggle being off means we stop asking the server anything at all.
        if (state != State.READY || !worldHasNodes || !config.isNodeOverlayEnabled()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        long floor = Math.max(minQueryIntervalMs, MIN_QUERY_INTERVAL_MS);
        if (now - lastQueryAt < floor) {
            return;
        }

        int chunkX = player.getBlockX() >> 4;
        int chunkZ = player.getBlockZ() >> 4;
        boolean moved = chunkX != lastQueryChunkX || chunkZ != lastQueryChunkZ;
        boolean stale = now - lastQueryAt >= REFRESH_INTERVAL_MS;
        if (!moved && !stale) {
            return;
        }

        int size = clampSize(config.getNodeQuerySize());
        // The centre is advisory; the server answers from our real position and echoes the origin.
        if (send(NodeProtocol.encodeQuery(chunkX, chunkZ, size))) {
            lastQueryAt = now;
            lastQueryChunkX = chunkX;
            lastQueryChunkZ = chunkZ;
        }
    }

    /** Clamps to an odd value in {@code [3, maxSize]}; an even request rounds down. */
    private int clampSize(int requested) {
        int size = Math.min(requested, maxSize);
        if ((size & 1) == 0) {
            size--;
        }
        return Math.max(3, size);
    }

    private boolean send(byte[] frame) {
        try {
            if (!ClientPlayNetworking.canSend(CivNodesPayload.TYPE)) {
                return false;
            }
            ClientPlayNetworking.send(new CivNodesPayload(frame));
            return true;
        } catch (RuntimeException e) {
            AbstractCivModernMod.LOGGER.warn("Sending on " + CivNodesPayload.CHANNEL, e);
            return false;
        }
    }

    private NodeCache cache() {
        AbstractCivModernMod mod = AbstractCivModernMod.getInstance();
        return mod == null || mod.getWorldListener() == null ? null : mod.getWorldListener().getNodes();
    }

    /** A one-line summary of the session for the config screen. */
    public Component statusLine() {
        return switch (state) {
            case IDLE -> Component.translatable("civmodern.nodes.status.idle").withStyle(ChatFormatting.GRAY);
            case AWAITING_HELLO, AWAITING_ACK ->
                Component.translatable("civmodern.nodes.status.waiting").withStyle(ChatFormatting.YELLOW);
            case READY -> Component.translatable("civmodern.nodes.status.ready",
                worldName, maxSize, minQueryIntervalMs).withStyle(ChatFormatting.GREEN);
            case NO_NODES -> Component.translatable("civmodern.nodes.status.nonodes").withStyle(ChatFormatting.YELLOW);
            case SERVER_DISABLED ->
                Component.translatable("civmodern.nodes.status.disabled").withStyle(ChatFormatting.RED);
            case TIMED_OUT -> Component.translatable("civmodern.nodes.status.timedout").withStyle(ChatFormatting.RED);
        };
    }
}

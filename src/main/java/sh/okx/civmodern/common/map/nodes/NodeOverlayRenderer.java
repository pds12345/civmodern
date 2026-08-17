package sh.okx.civmodern.common.map.nodes;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import sh.okx.civmodern.common.CivMapConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws node territory over the map and builds the hover text for a chunk.
 *
 * <p>The colours mirror what the in-game sidebar already shows, so the overlay reads the same
 * way {@code /nodemap} does: green where you have access, gold where you do not, red-brown
 * where a claimed node's bastion no longer reaches, and grey for unclaimed ground.
 */
public final class NodeOverlayRenderer {

    /** Always fully opaque, whatever the fill opacity: the seams are what define the territory. */
    private static final int BORDER_COLOUR = 0xFF080B0E;

    /**
     * Consecutive palette indices are spread by the golden angle, so two nodes the server gave
     * neighbouring indices land far apart on the colour wheel rather than looking alike.
     */
    private static final float HUE_STEP_DEGREES = 137.508f;

    // Saturation and value per state. The hue always identifies the node; these say what it is.
    // Brightness forms a deliberate ladder - access is brightest, then no-access and unclaimed at
    // a similar level distinguished by how grey they are, then unprotected darkest of all.
    private static final float ACCESS_S = 0.62f, ACCESS_V = 0.84f;
    private static final float NO_ACCESS_S = 0.80f, NO_ACCESS_V = 0.52f;
    private static final float UNCLAIMED_S = 0.15f, UNCLAIMED_V = 0.50f;

    /** An unprotected chunk keeps its node's hue but drops to a dead, shadowed version of it. */
    private static final float UNPROTECTED_S = 0.55f, UNPROTECTED_V = 0.32f;

    /** Below this many GUI pixels a chunk is not worth painting, and the map stays readable. */
    private static final float MIN_CHUNK_PIXELS = 1.5f;

    /** Borders need a chunk big enough that a 1px line does not swallow the fill. */
    private static final float MIN_BORDER_CHUNK_PIXELS = 4f;

    /** Once chunks are this large a single pixel reads as a hairline, so thicken the seams. */
    private static final float THICK_BORDER_CHUNK_PIXELS = 16f;

    /**
     * Stands in for "no node here" in the row arrays. Node ids are opaque ints, so the rows hold
     * them widened to long: that leaves {@code Long.MIN_VALUE} free as a sentinel no real id can
     * ever collide with.
     */
    private static final long NO_ID = Long.MIN_VALUE;

    private NodeOverlayRenderer() {
    }

    /**
     * Paints the visible chunks.
     *
     * @param originX  world block X at the left edge of the screen
     * @param originZ  world block Z at the top edge of the screen
     * @param scale    world blocks per GUI pixel
     */
    public static void render(GuiGraphics guiGraphics, NodeCache cache, CivMapConfig config,
                              double originX, double originZ, int screenWidth, int screenHeight, float scale) {
        float chunkPixels = 16f / scale;
        if (chunkPixels < MIN_CHUNK_PIXELS) {
            return;
        }

        int alpha = Math.round(Math.min(1f, Math.max(0f, config.getNodeOverlayOpacity())) * 255f) << 24;
        if (alpha == 0) {
            return;
        }

        // Scanned one chunk beyond the viewport on every side. A seam is drawn on the west and
        // north edge of whichever chunk differs from its neighbour, so without the extra ring the
        // east and south edges of territory meeting open ground would never get a line at all.
        int minChunkX = Math.floorDiv((int) Math.floor(originX), 16) - 1;
        int minChunkZ = Math.floorDiv((int) Math.floor(originZ), 16) - 1;
        int maxChunkX = Math.floorDiv((int) Math.ceil(originX + screenWidth * scale), 16) + 1;
        int maxChunkZ = Math.floorDiv((int) Math.ceil(originZ + screenHeight * scale), 16) + 1;

        boolean borders = config.isNodeOverlayBorders() && chunkPixels >= MIN_BORDER_CHUNK_PIXELS;
        int borderWidth = chunkPixels >= THICK_BORDER_CHUNK_PIXELS ? 2 : 1;

        // The palette is copied once rather than locked per chunk, and each row is resolved into
        // plain arrays so the fill and border passes are array reads.
        Int2ObjectMap<NodeInfo> palette = cache.snapshotNodes();
        int width = maxChunkX - minChunkX + 1;
        long[] ids = new long[width];
        boolean[] protectedHere = new boolean[width];
        long[] northIds = new long[width];
        Arrays.fill(northIds, NO_ID);

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            int top = screenY(chunkZ, originZ, scale);
            int bottom = screenY(chunkZ + 1, originZ, scale);

            readRow(cache, minChunkX, chunkZ, width, ids, protectedHere);

            if (bottom > top) {
                // Merge horizontal runs into one fill. Nodes are large contiguous blobs, so this
                // turns tens of thousands of quads into a few hundred. Runs are detected by node
                // identity rather than by colour so the palette is only consulted once per run,
                // which keeps the per-chunk work to a couple of array reads at low zoom.
                long runId = ids[0];
                boolean runProtected = protectedHere[0];
                int runFrom = 0;

                for (int i = 1; i < width; i++) {
                    if (ids[i] != runId || protectedHere[i] != runProtected) {
                        fillRun(guiGraphics, colourOf(palette, runId, runProtected, alpha),
                            minChunkX + runFrom, minChunkX + i, top, bottom, originX, scale);
                        runId = ids[i];
                        runProtected = protectedHere[i];
                        runFrom = i;
                    }
                }
                fillRun(guiGraphics, colourOf(palette, runId, runProtected, alpha),
                    minChunkX + runFrom, minChunkX + width, top, bottom, originX, scale);

                if (borders) {
                    drawBorders(guiGraphics, ids, northIds, minChunkX, width, top, bottom, originX, scale, borderWidth);
                }
            }

            System.arraycopy(ids, 0, northIds, 0, width);
        }
    }

    /** Resolves one chunk row, looking the region up only when crossing a region boundary. */
    private static void readRow(NodeCache cache, int minChunkX, int chunkZ, int width,
                                long[] ids, boolean[] protectedHere) {
        int lz = chunkZ & (NodeRegion.CHUNKS - 1);
        NodeRegion region = null;
        int regionX = Integer.MIN_VALUE;

        for (int i = 0; i < width; i++) {
            int chunkX = minChunkX + i;
            int thisRegionX = chunkX >> NodeRegion.SHIFT;
            if (thisRegionX != regionX) {
                regionX = thisRegionX;
                region = cache.getRegion(NodeCache.regionOf(chunkX, chunkZ));
            }

            int lx = chunkX & (NodeRegion.CHUNKS - 1);
            if (region == null || !region.hasNode(lx, lz)) {
                ids[i] = NO_ID;
                protectedHere[i] = false;
            } else {
                ids[i] = region.nodeId(lx, lz);
                protectedHere[i] = region.isProtected(lx, lz);
            }
        }
    }

    private static void fillRun(GuiGraphics guiGraphics, int colour, int fromChunkX, int toChunkX,
                                int top, int bottom, double originX, float scale) {
        if (colour == 0) {
            return;
        }
        int left = screenX(fromChunkX, originX, scale);
        int right = screenX(toChunkX, originX, scale);
        if (right > left) {
            guiGraphics.fill(left, top, right, bottom, colour);
        }
    }

    /**
     * Draws the seams where a chunk's node differs from its west or north neighbour.
     *
     * <p>Chunks owned by nobody take part rather than being skipped: the line between territory
     * and open ground is exactly as much a border as the line between two nodes, and skipping the
     * empty side is what used to leave the east and south edges of a node unoutlined.
     */
    private static void drawBorders(GuiGraphics guiGraphics, long[] ids, long[] northIds,
                                    int minChunkX, int width, int top, int bottom,
                                    double originX, float scale, int borderWidth) {
        for (int i = 0; i < width; i++) {
            long id = ids[i];
            long west = i == 0 ? NO_ID : ids[i - 1];
            long north = northIds[i];

            int left = screenX(minChunkX + i, originX, scale);
            int right = screenX(minChunkX + i + 1, originX, scale);

            // Differing implies at least one side is a real node, so no extra check is needed.
            if (id != west) {
                guiGraphics.fill(left, top, Math.min(left + borderWidth, right), bottom, BORDER_COLOUR);
            }
            if (id != north) {
                guiGraphics.fill(left, top, right, Math.min(top + borderWidth, bottom), BORDER_COLOUR);
            }
        }
    }

    /**
     * @return the packed ARGB for a chunk, or {@code 0} where there is nothing to paint
     */
    private static int colourOf(Int2ObjectMap<NodeInfo> palette, long nodeId, boolean isProtected, int alpha) {
        if (nodeId == NO_ID) {
            // Nothing known, or known to belong to no node. Either way, leave the map bare.
            return 0;
        }
        NodeInfo node = palette.get((int) nodeId);
        if (node == null) {
            return 0;
        }

        float hue = hueFor(node);
        float s;
        float v;
        if (!node.claimed()) {
            // Nearly grey, but keeping enough hue that two adjacent unclaimed nodes still differ.
            s = UNCLAIMED_S;
            v = UNCLAIMED_V;
        } else if (!isProtected) {
            s = UNPROTECTED_S;
            v = UNPROTECTED_V;
        } else if (node.hasAccess()) {
            s = ACCESS_S;
            v = ACCESS_V;
        } else {
            s = NO_ACCESS_S;
            v = NO_ACCESS_V;
        }

        return alpha | hsvToRgb(hue, s, v);
    }

    /**
     * The node's own hue. Prefers the server's map palette index, which is assigned at generation
     * so that adjacent nodes never share one; falls back to scrambling the node id when the
     * server left it unassigned, which at least keeps the colour stable for that node.
     */
    private static float hueFor(NodeInfo node) {
        int seed;
        if (node.colorIndex() >= 0) {
            seed = node.colorIndex();
        } else {
            seed = (node.nodeId() * 0x9E3779B1) >>> 24;
        }
        return (seed * HUE_STEP_DEGREES) % 360f;
    }

    /** Hand-rolled rather than via java.awt.Color, which Minecraft avoids loading on clients. */
    private static int hsvToRgb(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s;
        float x = c * (1f - Math.abs(((h / 60f) % 2f) - 1f));
        float m = v - c;

        float r;
        float g;
        float b;
        switch ((int) (h / 60f) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }

        return (channel(r + m) << 16) | (channel(g + m) << 8) | channel(b + m);
    }

    private static int channel(float value) {
        return Math.min(255, Math.max(0, Math.round(value * 255f)));
    }

    private static int screenX(int chunkX, double originX, float scale) {
        return (int) Math.round((chunkX * 16.0 - originX) / scale);
    }

    private static int screenY(int chunkZ, double originZ, float scale) {
        return (int) Math.round((chunkZ * 16.0 - originZ) / scale);
    }

    // ---------------------------------------------------------------- hover

    /**
     * The tooltip for a chunk, or an empty list when there is nothing cached there.
     *
     * <p>Only shows what the API served, which is only what a player can already see in game.
     */
    public static List<Component> tooltip(NodeCache cache, int chunkX, int chunkZ) {
        List<Component> lines = new ArrayList<>();

        NodeRegion region = cache.getRegion(NodeCache.regionOf(chunkX, chunkZ));
        if (region == null) {
            return lines;
        }
        int lx = chunkX & (NodeRegion.CHUNKS - 1);
        int lz = chunkZ & (NodeRegion.CHUNKS - 1);
        if (!region.hasNode(lx, lz)) {
            return lines;
        }

        NodeInfo node = cache.getNode(region.nodeId(lx, lz));
        if (node == null) {
            return lines;
        }

        Component title = node.name() != null && !node.name().isBlank()
            ? Component.literal(node.name())
            : Component.translatable("civmodern.nodes.tooltip.unnamed", node.nodeId());
        lines.add(title.copy().withStyle(ChatFormatting.WHITE));

        if (node.groupName() != null && !node.groupName().isBlank()) {
            lines.add(Component.translatable("civmodern.nodes.tooltip.group", node.groupName())
                .withStyle(ChatFormatting.AQUA));
        }

        if (!node.claimed()) {
            lines.add(Component.translatable("civmodern.nodes.tooltip.unclaimed").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(node.hasAccess()
                ? Component.translatable("civmodern.nodes.tooltip.access").withStyle(ChatFormatting.GREEN)
                : Component.translatable("civmodern.nodes.tooltip.noaccess").withStyle(ChatFormatting.GOLD));

            lines.add(region.isProtected(lx, lz)
                ? Component.translatable("civmodern.nodes.tooltip.protected").withStyle(ChatFormatting.GREEN)
                : Component.translatable("civmodern.nodes.tooltip.unprotected").withStyle(ChatFormatting.RED));

            if (node.bastionInWindow()) {
                int distance = Math.abs(node.bastionChunkX() - chunkX) + Math.abs(node.bastionChunkZ() - chunkZ);
                lines.add(Component.translatable("civmodern.nodes.tooltip.bastion",
                    node.bastionChunkX() * 16 + 8, node.bastionChunkZ() * 16 + 8, distance)
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        long age = cache.ageAt(chunkX, chunkZ);
        if (age >= 0) {
            lines.add(Component.translatable("civmodern.nodes.tooltip.age", describeAge(age))
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        return lines;
    }

    private static String describeAge(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        return hours < 24 ? hours + "h" : (hours / 24) + "d";
    }
}

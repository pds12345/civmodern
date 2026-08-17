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

    private static final int COLOUR_ACCESS = 0x3F8B5C;
    private static final int COLOUR_NO_ACCESS = 0xA9791F;
    private static final int COLOUR_BROKEN = 0xB04A34;
    private static final int COLOUR_UNCLAIMED = 0x79838B;
    private static final int BORDER_COLOUR = 0xFF14181B;

    /** Below this many GUI pixels a chunk is not worth painting, and the map stays readable. */
    private static final float MIN_CHUNK_PIXELS = 1.5f;

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

        int minChunkX = Math.floorDiv((int) Math.floor(originX), 16);
        int minChunkZ = Math.floorDiv((int) Math.floor(originZ), 16);
        int maxChunkX = Math.floorDiv((int) Math.ceil(originX + screenWidth * scale), 16);
        int maxChunkZ = Math.floorDiv((int) Math.ceil(originZ + screenHeight * scale), 16);

        boolean borders = config.isNodeOverlayBorders() && chunkPixels >= 4f;

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
                    drawBorders(guiGraphics, ids, northIds, minChunkX, width, top, bottom, originX, scale);
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

    /** Draws the seams where a chunk's node differs from its west or north neighbour. */
    private static void drawBorders(GuiGraphics guiGraphics, long[] ids, long[] northIds,
                                    int minChunkX, int width, int top, int bottom, double originX, float scale) {
        for (int i = 0; i < width; i++) {
            long id = ids[i];
            if (id == NO_ID) {
                continue;
            }
            int left = screenX(minChunkX + i, originX, scale);
            int right = screenX(minChunkX + i + 1, originX, scale);

            if (i == 0 || ids[i - 1] != id) {
                guiGraphics.fill(left, top, left + 1, bottom, BORDER_COLOUR);
            }
            if (northIds[i] != id) {
                guiGraphics.fill(left, top, right, top + 1, BORDER_COLOUR);
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

        int base;
        if (!node.claimed()) {
            base = COLOUR_UNCLAIMED;
        } else if (!isProtected) {
            base = COLOUR_BROKEN;
        } else {
            base = node.hasAccess() ? COLOUR_ACCESS : COLOUR_NO_ACCESS;
        }

        return alpha | shade(base, node.colorIndex());
    }

    /**
     * Nudges the brightness by the node's map palette index so that two adjacent nodes in the
     * same state stay distinguishable. Adjacent nodes never share an index, and {@code -1}
     * means the server never assigned one.
     */
    private static int shade(int rgb, byte colorIndex) {
        if (colorIndex < 0) {
            return rgb;
        }
        // +/- ~12% across the palette, which separates neighbours without losing the state colour.
        float factor = 0.88f + ((colorIndex & 0x07) / 7f) * 0.24f;
        int r = clamp((int) (((rgb >> 16) & 0xFF) * factor));
        int g = clamp((int) (((rgb >> 8) & 0xFF) * factor));
        int b = clamp((int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int channel) {
        return Math.min(255, Math.max(0, channel));
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

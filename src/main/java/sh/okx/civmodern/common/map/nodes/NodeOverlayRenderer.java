package sh.okx.civmodern.common.map.nodes;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import sh.okx.civmodern.common.CivMapConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws node territory over the map and builds the hover text for a chunk.
 *
 * <p>Colour says one thing only, so it can be read without a legend: green where a protected
 * chunk is yours, yellow where a protected chunk is somebody else's, and red wherever protection
 * has lapsed, friendly or not. Which node a chunk belongs to is carried by the border seams
 * rather than by its fill, and the node's controlling bastion is marked with a hollow square.
 *
 * <p>Nodes nobody has claimed are greys off the same ramp, which keeps them plainly outside that
 * hue-coded scheme while still telling one unclaimed node from the next. A player who does not
 * want to see them at all can turn them off, and then those chunks are drawn as bare map.
 *
 * <p>Inside a node the individual chunks are separated by a slim dashed grid, so a player can
 * count chunks off a bastion without mistaking one of those lines for the edge of the node.
 */
public final class NodeOverlayRenderer {

    /**
     * Fully opaque whatever the fill opacity, since the seams are what define the territory.
     * Only {@link NodeOverlayMode#TRANSLUCENT} fades it, and that fades the layer as a whole.
     */
    private static final int BORDER_COLOUR = 0xFF080B0E;

    /**
     * The chunk grid inside a node. Same ink as the seams but see-through and only ever a pixel
     * wide, so an interior chunk edge can never be misread as the boundary of the node itself.
     */
    private static final int CHUNK_GRID_COLOUR = 0x66080B0E;

    /**
     * The seam where territory meets chunks the server has never described — the edge of what we
     * know, not necessarily the edge of the node. Light where real borders are near-black, and
     * dashed with finer ticks than the chunk grid, so it reads as tentative next to both.
     */
    private static final int DATA_EDGE_COLOUR = 0xCCC3CBD1;

    // Status colours. Deliberately fixed rather than varied per node: the fill answers "can I
    // build here", and the seams answer "whose is it".
    private static final int COLOUR_FRIENDLY = 0x33A457;
    private static final int COLOUR_UNFRIENDLY = 0xD8C230;
    private static final int COLOUR_UNPROTECTED = 0xC63A2C;

    /**
     * Unclaimed ground is neither friendly nor unfriendly and has nothing to protect, so it stays
     * neutral. Painting it red would confuse "nobody owns this" with "someone owns this and their
     * bastion has stopped reaching", which is the one thing red is meant to call out.
     *
     * <p>Greys rather than one grey, so a stretch of unclaimed land reads as the several nodes it
     * actually is instead of one blob. The shade comes from the node's palette colour index, which
     * the server assigns so that no two adjacent nodes share one — meaning neighbours always land
     * on different rungs of this ramp, and a node keeps the same shade from one query to the next.
     *
     * <p>Six rungs, matching the colour count the server plans for, evenly spaced in brightness and
     * holding the same cool tint throughout so none of them starts to read as a status colour. They
     * are listed out of brightness order, so the low indices a small map mostly draws land at
     * opposite ends of the ramp rather than side by side. The spacing is deliberately wide: with
     * borders turned off, the change in shade is the only seam between two unclaimed nodes.
     */
    private static final int[] COLOUR_UNCLAIMED = {
        0x6B757C, 0x43494E, 0xA7B6C2, 0x7F8B94, 0x575F65, 0x93A0AB,
    };

    /** The shade for a node the server never assigned a colour index. Deliberately the mid grey. */
    private static final int COLOUR_UNCLAIMED_DEFAULT = COLOUR_UNCLAIMED[0];

    /** The hollow square marking a node's controlling bastion chunk. */
    private static final int BASTION_MARKER_COLOUR = 0xFFFFFFFF;
    private static final int BASTION_MARKER_SHADOW = 0xFF101418;

    /**
     * A floor on how small a chunk can get before the overlay gives up, well below anything the
     * map's own zoom range reaches: in practice territory stays drawn all the way out.
     *
     * <p>It is here to bound {@link #samplingStep} rather than to hide the layer — a chunk this
     * small is a sample every 256 blocks, and past that the arithmetic is more interesting than
     * the picture.
     */
    private static final float MIN_CHUNK_PIXELS = 1f / 16f;

    /**
     * Zoomed out far enough that chunks fall below a pixel, the overlay reads one chunk per this
     * many pixels and paints its colour across the whole sample rather than walking every chunk.
     *
     * <p>One keeps the picture at the full resolution the screen can show. It also fixes the cost:
     * a frame does work proportional to the pixels on screen instead of to the chunks behind them,
     * which at full zoom-out is millions of chunks and would be what stopped the layer being drawn
     * there at all.
     */
    private static final float SAMPLE_PIXELS = 1f;

    /**
     * Seams are drawn down to chunks this small — one zoom step past where a 1px line stops being
     * a hairline on the chunk it edges.
     *
     * <p>At two pixels a seam takes half of the boundary chunk it is drawn on, so the fill there
     * is no longer an honest picture of that one chunk. That is the deliberate trade: zoomed this
     * far out the question being asked is where the territory ends, not which chunk is whose, and
     * the shape of a claim is far easier to follow with its outline drawn thick than with the
     * outline dropped. Only chunks on a boundary pay it; interior fill is untouched.
     *
     * <p>Still exact, for all that: two pixels a chunk is above the point where the fill starts
     * sampling, so a seam is drawn between chunks that really do neighbour each other.
     */
    private static final float MIN_BORDER_CHUNK_PIXELS = 2f;

    /** Once chunks are this large a single pixel reads as a hairline, so thicken the seams. */
    private static final float THICK_BORDER_CHUNK_PIXELS = 16f;

    /**
     * Under this a dash and its gap are a pixel each, and the grid stops reading as a grid and
     * starts reading as noise laid over the fill.
     */
    private static final float MIN_GRID_CHUNK_PIXELS = 8f;

    /** Under this the hollow square would collapse into a dot, so it is not drawn. */
    private static final float MIN_BASTION_CHUNK_PIXELS = 6f;

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
     * @param mode     how the layer is faded; passed in rather than read off the config, because
     *                 the map screen and the minimap each keep their own mode
     * @param originX  world block X at the left edge of the viewport
     * @param originZ  world block Z at the top edge of the viewport
     * @param scale    world blocks per GUI pixel
     * @param clip     the viewport's on-screen rectangle in GUI coordinates, or {@code null} when
     *                 the viewport is the whole screen — the minimap passes its square so the
     *                 layer is scissored to it
     */
    public static void render(GuiGraphics guiGraphics, NodeCache cache, CivMapConfig config, NodeOverlayMode mode,
                              double originX, double originZ, int screenWidth, int screenHeight, float scale,
                              ScreenRectangle clip) {
        float chunkPixels = 16f / scale;
        if (chunkPixels < MIN_CHUNK_PIXELS) {
            return;
        }

        // TRANSLUCENT mode fades every colour the layer draws, on top of the configured fill
        // opacity — fading only the fill would leave opaque seams floating over a ghosted map.
        float ink = mode.inkMultiplier();
        if (ink <= 0f) {
            return;
        }
        int alpha = Math.round(Math.min(1f, Math.max(0f, config.getNodeOverlayOpacity())) * ink * 255f) << 24;
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

        // Snapped to a multiple of the step, in world coordinates rather than screen ones, so
        // panning slides the picture instead of moving which chunks get sampled. Sampled off the
        // viewport edge the lattice would shift with every drag and the territory would crawl.
        int step = samplingStep(chunkPixels);
        minChunkX = Math.floorDiv(minChunkX, step) * step;
        minChunkZ = Math.floorDiv(minChunkZ, step) * step;

        boolean borders = config.isNodeOverlayBorders() && chunkPixels >= MIN_BORDER_CHUNK_PIXELS;
        int borderWidth = chunkPixels >= THICK_BORDER_CHUNK_PIXELS ? 2 : 1;

        // Dashes are measured off the chunk rather than fixed, so a chunk carries the same three
        // dashes a side at every zoom and the cost of the grid does not grow as the map is zoomed.
        // The pattern is computed once from the chunk size, not per edge from rounded screen
        // bounds: recomputing it per edge is what made the dashes crawl against the map while
        // dragging, as each edge's rounded length flickered by a pixel and re-centred its dashes.
        boolean grid = config.isNodeChunkGrid() && chunkPixels >= MIN_GRID_CHUNK_PIXELS;
        int dash = Math.max(1, Math.round(chunkPixels / 6f));
        int dashGap = Math.max(1, Math.round(chunkPixels / 5f));
        int[] gridPattern = dashPattern(Math.round(chunkPixels), dash, dashGap);

        // The palette is copied once rather than locked per chunk, and each row is resolved into
        // plain arrays so the fill and border passes are array reads.
        Int2ObjectMap<NodeInfo> palette = cache.snapshotNodes();
        // Samples, not chunks: one entry per step chunks, and the last one covers whatever is left
        // over past maxChunkX so the east edge of the screen is never short of a column.
        int width = (maxChunkX - minChunkX) / step + 1;
        long[] ids = new long[width];
        boolean[] protectedHere = new boolean[width];
        boolean[] known = new boolean[width];
        long[] northIds = new long[width];
        boolean[] northKnown = new boolean[width];
        Arrays.fill(northIds, NO_ID);

        // The fine ticking of the edge-of-data seam: twice the cadence of the chunk grid, so the
        // two dashed lines stay tellable apart even where they meet at a corner.
        int edgeDash = Math.max(1, Math.round(chunkPixels / 12f));
        int edgeGap = Math.max(1, Math.round(chunkPixels / 10f));
        int[] edgePattern = dashPattern(Math.round(chunkPixels), edgeDash, edgeGap);
        int edgeColour = fade(DATA_EDGE_COLOUR, ink);

        // One GUI element for the whole layer. Submitting each fill separately is a render-state
        // element per quad, and at grid zoom levels that is thousands of elements a frame.
        NodeOverlayQuadBatch batch = new NodeOverlayQuadBatch(
            new Matrix3x2f(guiGraphics.pose()),
            clip != null ? clip : guiGraphics.scissorStack.peek(),
            clip != null ? clip : new ScreenRectangle(0, 0, screenWidth, screenHeight));

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ += step) {
            float top = screenY(chunkZ, originZ, scale);
            float bottom = screenY(chunkZ + step, originZ, scale);

            readRow(cache, minChunkX, chunkZ, width, step, ids, protectedHere, known);
            if (!config.isNodeShowUnclaimed()) {
                dropUnclaimed(palette, ids, protectedHere, width);
            }

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
                        fillRun(batch, colourOf(palette, runId, runProtected, alpha),
                            minChunkX + runFrom * step, minChunkX + i * step, top, bottom, originX, scale);
                        runId = ids[i];
                        runProtected = protectedHere[i];
                        runFrom = i;
                    }
                }
                fillRun(batch, colourOf(palette, runId, runProtected, alpha),
                    minChunkX + runFrom * step, minChunkX + width * step, top, bottom, originX, scale);

                // Both need chunks several pixels across, which is far inside the zoom range where
                // the step is 1, so these still walk real neighbouring chunks.
                if (borders || grid) {
                    drawEdges(batch, ids, northIds, known, northKnown, minChunkX, width, top, bottom,
                        originX, scale, borders, borderWidth, grid, dash, gridPattern,
                        fade(BORDER_COLOUR, ink), fade(CHUNK_GRID_COLOUR, ink),
                        edgeColour, edgeDash, edgePattern);
                }
            }

            System.arraycopy(ids, 0, northIds, 0, width);
            System.arraycopy(known, 0, northKnown, 0, width);
        }

        if (chunkPixels >= MIN_BASTION_CHUNK_PIXELS) {
            drawBastions(batch, palette, minChunkX, maxChunkX, minChunkZ, maxChunkZ,
                originX, originZ, scale, chunkPixels, ink);
        }

        if (!batch.isEmpty()) {
            guiGraphics.guiRenderState.submitGuiElement(batch);
        }
    }

    /**
     * Start offsets of each dash along a chunk edge, measured from the chunk corner. Derived from
     * the chunk size alone so the pattern is rigid relative to the chunk at a given zoom: the
     * dashes slide with the map instead of re-centring themselves every frame. Centred with a
     * short setback from both corners, so the grid never closes up into a solid outline.
     */
    private static int[] dashPattern(int length, int dash, int gap) {
        int count = Math.max(1, (length + gap) / (dash + gap));
        int pad = Math.max(0, (length - (count * dash + (count - 1) * gap)) / 2);
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = pad + i * (dash + gap);
        }
        return offsets;
    }

    /**
     * Outlines each node's controlling bastion chunk with a hollow square.
     *
     * <p>Driven off the palette rather than the chunk grid: a window touches tens of nodes but
     * hundreds of thousands of chunks, and a node carries its bastion position directly. The
     * position sticks around in the cache once seen, so the marker keeps showing even after the
     * bastion falls outside the queried window.
     */
    private static void drawBastions(NodeOverlayQuadBatch batch, Int2ObjectMap<NodeInfo> palette,
                                     int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                                     double originX, double originZ, float scale, float chunkPixels,
                                     float ink) {
        int markerColour = fade(BASTION_MARKER_COLOUR, ink);
        int shadowColour = fade(BASTION_MARKER_SHADOW, ink);
        // Thick enough to read against the fill, but never more than a quarter of the chunk.
        int thickness = Math.max(1, Math.min(Math.round(chunkPixels / 10f), Math.round(chunkPixels / 4f)));
        int inset = Math.max(1, Math.round(chunkPixels * 0.18f));

        for (NodeInfo node : palette.values()) {
            if (!node.bastionInWindow()) {
                continue;
            }
            int chunkX = node.bastionChunkX();
            int chunkZ = node.bastionChunkZ();
            if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                continue;
            }

            float left = screenX(chunkX, originX, scale) + inset;
            float right = screenX(chunkX + 1, originX, scale) - inset;
            float top = screenY(chunkZ, originZ, scale) + inset;
            float bottom = screenY(chunkZ + 1, originZ, scale) - inset;
            if (right - left < 3 || bottom - top < 3) {
                continue;
            }

            // A dark square one pixel out, so the white stays legible on every fill colour.
            hollowSquare(batch, left - 1, top - 1, right + 1, bottom + 1, thickness, shadowColour);
            hollowSquare(batch, left, top, right, bottom, thickness, markerColour);
        }
    }

    private static void hollowSquare(NodeOverlayQuadBatch batch, float left, float top, float right, float bottom,
                                     int thickness, int colour) {
        float t = Math.min(thickness, Math.min(right - left, bottom - top) / 2f);
        if (t < 1) {
            return;
        }
        batch.add(left, top, right, top + t, colour);
        batch.add(left, bottom - t, right, bottom, colour);
        batch.add(left, top + t, left + t, bottom - t, colour);
        batch.add(right - t, top + t, right, bottom - t, colour);
    }

    /**
     * How many chunks one sample stands for: 1 wherever a chunk is at least a pixel across, and
     * otherwise enough chunks to make a sample {@link #SAMPLE_PIXELS} wide.
     */
    private static int samplingStep(float chunkPixels) {
        if (chunkPixels >= SAMPLE_PIXELS) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(SAMPLE_PIXELS / chunkPixels));
    }

    /**
     * Resolves one row of samples, looking the region up only when crossing a region boundary.
     *
     * <p>With a step above 1 the chunk read stands in for the whole sample. That is the trade the
     * zoom asks for: below a pixel per chunk the screen cannot show every chunk anyway, and a node
     * has to be smaller than the sample before it can slip between two of them.
     */
    private static void readRow(NodeCache cache, int minChunkX, int chunkZ, int width, int step,
                                long[] ids, boolean[] protectedHere, boolean[] known) {
        int lz = chunkZ & (NodeRegion.CHUNKS - 1);
        NodeRegion region = null;
        int regionX = Integer.MIN_VALUE;

        for (int i = 0; i < width; i++) {
            int chunkX = minChunkX + i * step;
            int thisRegionX = chunkX >> NodeRegion.SHIFT;
            if (thisRegionX != regionX) {
                regionX = thisRegionX;
                region = cache.getRegion(NodeCache.regionOf(chunkX, chunkZ));
            }

            int lx = chunkX & (NodeRegion.CHUNKS - 1);
            known[i] = region != null && region.isKnown(lx, lz);
            if (region == null || !region.hasNode(lx, lz)) {
                ids[i] = NO_ID;
                protectedHere[i] = false;
            } else {
                ids[i] = region.nodeId(lx, lz);
                protectedHere[i] = region.isProtected(lx, lz);
            }
        }
    }

    /**
     * Rewrites unclaimed nodes out of a resolved row, leaving those chunks looking exactly like
     * chunks that belong to no node at all.
     *
     * <p>Done to the row rather than in {@link #colourOf} so that the whole overlay agrees: the
     * fill is skipped, the chunk grid stops where the fill stops, and the line where held
     * territory meets unclaimed ground is drawn as the border of the claim, which is what a player
     * hiding this layer is asking to see.
     *
     * <p>The palette is consulted once per run of equal ids, the same way the fill pass does it,
     * so hiding costs a lookup per node in the row rather than one per chunk.
     */
    private static void dropUnclaimed(Int2ObjectMap<NodeInfo> palette, long[] ids,
                                      boolean[] protectedHere, int width) {
        long runId = NO_ID;
        boolean hide = false;

        for (int i = 0; i < width; i++) {
            // Compares against the id as it was read: the write below only touches entries already
            // passed, so a run is still detected off its original ids.
            if (ids[i] != runId) {
                runId = ids[i];
                NodeInfo node = runId == NO_ID ? null : palette.get((int) runId);
                hide = node != null && !node.claimed();
            }
            if (hide) {
                ids[i] = NO_ID;
                protectedHere[i] = false;
            }
        }
    }

    private static void fillRun(NodeOverlayQuadBatch batch, int colour, int fromChunkX, int toChunkX,
                                float top, float bottom, double originX, float scale) {
        if (colour == 0) {
            return;
        }
        batch.add(screenX(fromChunkX, originX, scale), top, screenX(toChunkX, originX, scale), bottom, colour);
    }

    /**
     * Draws the west and north edge of each chunk in a row: a solid seam where the chunk's node
     * differs from that neighbour, and otherwise the dashed grid that separates chunks of one node.
     *
     * <p>Chunks owned by nobody take part in the seams rather than being skipped: the line between
     * territory and open ground is exactly as much a border as the line between two nodes, and
     * skipping the empty side is what used to leave the east and south edges of a node unoutlined.
     * They take no part in the grid, which belongs to the fill and so stops where the fill does.
     *
     * <p>The two never stack on one edge, so a seam always stays the heavier line. With seams
     * turned off the grid takes the node boundaries over as well, since there is nothing left to
     * confuse them with.
     *
     * <p>A seam against ground the server has never described is not drawn solid: that line is the
     * edge of our data, not necessarily of the node, so it gets the light finely-ticked dash
     * instead. Ground the server has said is empty keeps the solid border — that edge is real.
     */
    private static void drawEdges(NodeOverlayQuadBatch batch, long[] ids, long[] northIds,
                                  boolean[] known, boolean[] northKnown,
                                  int minChunkX, int width, float top, float bottom,
                                  double originX, float scale,
                                  boolean seams, int borderWidth, boolean grid, int dash, int[] gridPattern,
                                  int borderColour, int gridColour,
                                  int edgeColour, int edgeDash, int[] edgePattern) {
        for (int i = 0; i < width; i++) {
            long id = ids[i];
            long west = i == 0 ? NO_ID : ids[i - 1];
            long north = northIds[i];

            float left = screenX(minChunkX + i, originX, scale);
            float right = screenX(minChunkX + i + 1, originX, scale);

            // Differing implies at least one side is a real node, so no extra check is needed.
            if (seams && id != west) {
                // Off-screen column: i == 0 never shows, so its west neighbour can pass as known.
                boolean unknownSide = (id == NO_ID && !known[i]) || (i > 0 && west == NO_ID && !known[i - 1]);
                if (unknownSide) {
                    dashedVertical(batch, left, top, bottom, edgePattern, edgeDash,
                        Math.min(left + borderWidth, right) - left, edgeColour);
                } else {
                    batch.add(left, top, Math.min(left + borderWidth, right), bottom, borderColour);
                }
            } else if (grid && id != NO_ID) {
                dashedVertical(batch, left, top, bottom, gridPattern, dash, 1, gridColour);
            }

            if (seams && id != north) {
                boolean unknownSide = (id == NO_ID && !known[i]) || (north == NO_ID && !northKnown[i]);
                if (unknownSide) {
                    dashedHorizontal(batch, left, right, top, edgePattern, edgeDash,
                        Math.min(top + borderWidth, bottom) - top, edgeColour);
                } else {
                    batch.add(left, top, right, Math.min(top + borderWidth, bottom), borderColour);
                }
            } else if (grid && id != NO_ID) {
                dashedHorizontal(batch, left, right, top, gridPattern, dash, 1, gridColour);
            }
        }
    }

    /**
     * Lays a dashed hairline down a chunk's west edge.
     *
     * <p>The pattern is centred on the edge rather than started at one end, so the dashes stop
     * short of both corners and the grid does not close up into something that could pass for a
     * solid outline around every chunk.
     */
    /**
     * Lays the precomputed dash pattern down a chunk's west edge. Offsets are measured from the
     * chunk's own corner, so the dashes are pinned to the chunk and pan with the map; only the
     * last dash may be clipped where rounding leaves this edge a pixel short of the pattern.
     */
    private static void dashedVertical(NodeOverlayQuadBatch batch, float x, float top, float bottom,
                                       int[] pattern, int dash, float lineWidth, int colour) {
        for (int offset : pattern) {
            batch.add(x, top + offset, x + lineWidth, Math.min(top + offset + dash, bottom), colour);
        }
    }

    /** As {@link #dashedVertical}, along a chunk's north edge. */
    private static void dashedHorizontal(NodeOverlayQuadBatch batch, float left, float right, float y,
                                         int[] pattern, int dash, float lineWidth, int colour) {
        for (int offset : pattern) {
            batch.add(left + offset, y, Math.min(left + offset + dash, right), y + lineWidth, colour);
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

        int rgb;
        if (!node.claimed()) {
            rgb = unclaimedColour(node);
        } else if (!isProtected) {
            // Red regardless of whose node it is: a lapsed bastion is the same fact either way.
            rgb = COLOUR_UNPROTECTED;
        } else {
            rgb = node.hasAccess() ? COLOUR_FRIENDLY : COLOUR_UNFRIENDLY;
        }

        return alpha | rgb;
    }

    /**
     * @return the grey this unclaimed node is painted, from its palette colour index
     */
    private static int unclaimedColour(NodeInfo node) {
        int index = node.colorIndex();
        if (index < 0) {
            return COLOUR_UNCLAIMED_DEFAULT;
        }
        // Wrapped rather than clamped: the server aims for a handful of colours but makes no
        // promise about the count, and wrapping only ever costs two neighbours the same shade
        // where clamping would flatten every index past the end into one.
        return COLOUR_UNCLAIMED[index % COLOUR_UNCLAIMED.length];
    }

    /** Scales an ARGB colour's alpha channel, leaving the RGB untouched. */
    private static int fade(int argb, float multiplier) {
        int alpha = Math.round((argb >>> 24) * multiplier);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    // Float, not rounded: the map texture below pans at sub-pixel precision, and rounding these
    // is what made every overlay line re-snap against it by a pixel while dragging.
    private static float screenX(int chunkX, double originX, float scale) {
        return (float) ((chunkX * 16.0 - originX) / scale);
    }

    private static float screenY(int chunkZ, double originZ, float scale) {
        return (float) ((chunkZ * 16.0 - originZ) / scale);
    }

    // ---------------------------------------------------------------- hover

    /**
     * The tooltip for a chunk, or an empty list when there is nothing cached there.
     *
     * <p>Only shows what the API served, which is only what a player can already see in game.
     */
    public static List<Component> tooltip(NodeCache cache, CivMapConfig config, int chunkX, int chunkZ) {
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
        if (!node.claimed() && !config.isNodeShowUnclaimed()) {
            // Nothing is painted here, so naming a node under the cursor would be describing
            // something the player cannot see.
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
                if (node.bastionChunkX() == chunkX && node.bastionChunkZ() == chunkZ) {
                    // The chunk under the hollow square.
                    lines.add(Component.translatable("civmodern.nodes.tooltip.controller")
                        .withStyle(ChatFormatting.WHITE));
                }
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

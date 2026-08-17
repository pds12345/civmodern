package sh.okx.civmodern.common.map.nodes;

/**
 * One entry of an {@code S2C_REGION} palette: everything the server will tell us about a node.
 *
 * <p>{@code nodeId} is opaque but stable within a world, so it is the cache key. Nothing about
 * ordering may be assumed. {@code colorIndex} is a map palette index assigned at generation
 * (adjacent nodes never share one), or {@code -1} when unassigned.
 */
public record NodeInfo(
    int nodeId,
    int flags,
    byte colorIndex,
    String name,
    String groupName,
    boolean bastionInWindow,
    int bastionChunkX,
    int bastionChunkZ
) {

    /**
     * Drops everything that belongs to a claim when there is no claim.
     *
     * <p>The server holds the name, the group and the bastion on the bastion itself: destroying one
     * clears the node's name with it, since an unclaimed node has no group and so nobody left who
     * could rename it. So an entry arriving without {@code CLAIMED} is not an entry missing those
     * fields, it is a node that genuinely has none — and the map must stop naming the group that
     * used to hold it and stop marking the chunk its bastion used to stand in.
     *
     * <p>Enforced here rather than at each place that reads them, so it also covers rows written to
     * disk by an older build and anything a future caller adds. The colour is untouched: that is
     * assigned to the land at generation and outlives any claim on it.
     */
    public NodeInfo {
        if ((flags & NodeProtocol.FLAG_CLAIMED) == 0) {
            name = null;
            groupName = null;
            bastionInWindow = false;
            bastionChunkX = 0;
            bastionChunkZ = 0;
        }
    }

    public boolean claimed() {
        return (flags & NodeProtocol.FLAG_CLAIMED) != 0;
    }

    public boolean hasAccess() {
        return (flags & NodeProtocol.FLAG_HAS_ACCESS) != 0;
    }

    /**
     * Merges a freshly received entry over a cached one, keeping fields the new entry did not
     * carry. A bastion outside the queried window arrives with {@code BASTION_IN_WINDOW} clear,
     * and group names disappear entirely when the operator turns {@code expose_group_names} off,
     * so neither absence should erase what we already knew.
     *
     * <p>Losing the claim is the one absence that is not a gap, and carrying those fields over
     * here would be how a dead node keeps its old group name and bastion marker. The constructor
     * throws them away again on the way out, so this only has to keep the colour right.
     */
    public NodeInfo mergeOver(NodeInfo previous) {
        if (previous == null || previous.nodeId != this.nodeId) {
            return this;
        }
        return new NodeInfo(
            nodeId,
            flags,
            colorIndex < 0 ? previous.colorIndex : colorIndex,
            name == null ? previous.name : name,
            groupName == null ? previous.groupName : groupName,
            bastionInWindow || previous.bastionInWindow,
            bastionInWindow ? bastionChunkX : previous.bastionChunkX,
            bastionInWindow ? bastionChunkZ : previous.bastionChunkZ
        );
    }
}

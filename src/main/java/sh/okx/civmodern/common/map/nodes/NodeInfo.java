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

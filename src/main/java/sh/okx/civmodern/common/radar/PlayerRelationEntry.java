package sh.okx.civmodern.common.radar;

/** One row of the player_relations table. Timestamps are epoch millis. */
public record PlayerRelationEntry(String username, PlayerRelation relation, long created, long updated) {
}

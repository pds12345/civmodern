package sh.okx.civmodern.common.map.snitches;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** The two JukeAlert snitch blocks, with the lifespan each starts with when placed or refreshed. */
public enum SnitchType {
    JUKEBOX(Items.JUKEBOX, Duration.ofHours(1008), Identifier.withDefaultNamespace("textures/block/jukebox_side.png")),
    NOTE_BLOCK(Items.NOTE_BLOCK, Duration.ofHours(672), Identifier.withDefaultNamespace("textures/block/note_block.png"));

    private final Item item;
    private final Duration lifespan;
    /** The vanilla block texture, drawn flat: the client already ships it, so no asset of our own. */
    private final Identifier texture;

    SnitchType(Item item, Duration lifespan, Identifier texture) {
        this.item = item;
        this.lifespan = lifespan;
        this.texture = texture;
    }

    public Duration lifespan() {
        return lifespan;
    }

    public Identifier texture() {
        return texture;
    }

    public static @Nullable SnitchType fromItem(Item item) {
        for (SnitchType type : values()) {
            if (type.item == item) {
                return type;
            }
        }
        return null;
    }

    public static @Nullable SnitchType fromName(String name) {
        for (SnitchType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}

package sh.okx.civmodern.common.map.mobs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static sh.okx.civmodern.common.map.mobs.MobThreatCategory.HOSTILE;
import static sh.okx.civmodern.common.map.mobs.MobThreatCategory.NEUTRAL;
import static sh.okx.civmodern.common.map.mobs.MobThreatCategory.PASSIVE;

/**
 * The vanilla mobs the minimap knows how to show, and each one's {@link MobThreatCategory}.
 * Categories were assigned by checking, for every mob class, whether it implements
 * {@code net.minecraft.world.entity.NeutralMob} (anger/provoke mechanic -> NEUTRAL) or
 * {@code net.minecraft.world.entity.monster.Enemy} (attacks unprovoked -> HOSTILE), defaulting
 * to PASSIVE otherwise - not by category-guessing from name. Raid bosses (ender dragon, wither,
 * giant) are left out entirely: rare, huge, and not what "mobs on the minimap" is about. So is
 * the illusioner: unobtainable without commands, and correspondingly missing from the mob list
 * page below that the icons came from.
 *
 * <p>Icons under {@code assets/civmodern/mob/<id>.png} (see {@link #iconLocation}) are 32x32
 * "face" crops pulled from https://minecraft.wiki/w/Mob#List_of_mobs (community-extracted from
 * the game's own textures, not shipped by Mojang), padded onto a transparent square - one-off
 * fetch, not build-time generated, so they are committed as plain PNGs.
 */
public final class MinimapMobTypes {

    /** Every icon under assets/civmodern/mob/ is a square this many pixels on a side. */
    public static final int ICON_SIZE = 32;

    private static final Map<EntityType<?>, MobThreatCategory> CATEGORIES = new LinkedHashMap<>();

    static {
        // Hostile: attacks on sight, unprovoked.
        register(EntityType.BLAZE, HOSTILE);
        register(EntityType.BOGGED, HOSTILE);
        register(EntityType.BREEZE, HOSTILE);
        register(EntityType.CAVE_SPIDER, HOSTILE);
        register(EntityType.CREAKING, HOSTILE);
        register(EntityType.CREEPER, HOSTILE);
        register(EntityType.DROWNED, HOSTILE);
        register(EntityType.ELDER_GUARDIAN, HOSTILE);
        register(EntityType.ENDERMITE, HOSTILE);
        register(EntityType.EVOKER, HOSTILE);
        register(EntityType.GHAST, HOSTILE);
        register(EntityType.GUARDIAN, HOSTILE);
        register(EntityType.HOGLIN, HOSTILE);
        register(EntityType.HUSK, HOSTILE);
        register(EntityType.MAGMA_CUBE, HOSTILE);
        register(EntityType.PARCHED, HOSTILE);
        register(EntityType.PHANTOM, HOSTILE);
        register(EntityType.PIGLIN, HOSTILE);
        register(EntityType.PIGLIN_BRUTE, HOSTILE);
        register(EntityType.PILLAGER, HOSTILE);
        register(EntityType.RAVAGER, HOSTILE);
        register(EntityType.SHULKER, HOSTILE);
        register(EntityType.SILVERFISH, HOSTILE);
        register(EntityType.SKELETON, HOSTILE);
        register(EntityType.SLIME, HOSTILE);
        register(EntityType.SPIDER, HOSTILE);
        register(EntityType.STRAY, HOSTILE);
        register(EntityType.VEX, HOSTILE);
        register(EntityType.VINDICATOR, HOSTILE);
        register(EntityType.WARDEN, HOSTILE);
        register(EntityType.WITCH, HOSTILE);
        register(EntityType.WITHER_SKELETON, HOSTILE);
        register(EntityType.ZOGLIN, HOSTILE);
        register(EntityType.ZOMBIE, HOSTILE);
        register(EntityType.ZOMBIE_VILLAGER, HOSTILE);

        // Neutral: implements NeutralMob - ignores the player until provoked.
        register(EntityType.BEE, NEUTRAL);
        register(EntityType.ENDERMAN, NEUTRAL);
        register(EntityType.IRON_GOLEM, NEUTRAL);
        register(EntityType.POLAR_BEAR, NEUTRAL);
        register(EntityType.WOLF, NEUTRAL);
        register(EntityType.ZOMBIFIED_PIGLIN, NEUTRAL);

        // Passive: never attacks the player.
        register(EntityType.ALLAY, PASSIVE);
        register(EntityType.ARMADILLO, PASSIVE);
        register(EntityType.AXOLOTL, PASSIVE);
        register(EntityType.BAT, PASSIVE);
        register(EntityType.CAMEL, PASSIVE);
        register(EntityType.CAMEL_HUSK, PASSIVE);
        register(EntityType.CAT, PASSIVE);
        register(EntityType.CHICKEN, PASSIVE);
        register(EntityType.COD, PASSIVE);
        register(EntityType.COPPER_GOLEM, PASSIVE);
        register(EntityType.COW, PASSIVE);
        register(EntityType.DOLPHIN, PASSIVE);
        register(EntityType.DONKEY, PASSIVE);
        register(EntityType.FOX, PASSIVE);
        register(EntityType.FROG, PASSIVE);
        register(EntityType.GLOW_SQUID, PASSIVE);
        register(EntityType.GOAT, PASSIVE);
        register(EntityType.HAPPY_GHAST, PASSIVE);
        register(EntityType.HORSE, PASSIVE);
        register(EntityType.LLAMA, PASSIVE);
        register(EntityType.MOOSHROOM, PASSIVE);
        register(EntityType.MULE, PASSIVE);
        register(EntityType.NAUTILUS, PASSIVE);
        register(EntityType.OCELOT, PASSIVE);
        register(EntityType.PANDA, PASSIVE);
        register(EntityType.PARROT, PASSIVE);
        register(EntityType.PIG, PASSIVE);
        register(EntityType.PUFFERFISH, PASSIVE);
        register(EntityType.RABBIT, PASSIVE);
        register(EntityType.SALMON, PASSIVE);
        register(EntityType.SHEEP, PASSIVE);
        register(EntityType.SKELETON_HORSE, PASSIVE);
        register(EntityType.SNIFFER, PASSIVE);
        register(EntityType.SNOW_GOLEM, PASSIVE);
        register(EntityType.SQUID, PASSIVE);
        register(EntityType.STRIDER, PASSIVE);
        register(EntityType.TADPOLE, PASSIVE);
        register(EntityType.TRADER_LLAMA, PASSIVE);
        register(EntityType.TROPICAL_FISH, PASSIVE);
        register(EntityType.TURTLE, PASSIVE);
        register(EntityType.VILLAGER, PASSIVE);
        register(EntityType.WANDERING_TRADER, PASSIVE);
        register(EntityType.ZOMBIE_HORSE, PASSIVE);
        register(EntityType.ZOMBIE_NAUTILUS, PASSIVE);
    }

    private MinimapMobTypes() {
    }

    private static void register(EntityType<?> type, MobThreatCategory category) {
        CATEGORIES.put(type, category);
    }

    /** Every tracked mob type, in registration order - every one of these has an icon. */
    public static Map<EntityType<?>, MobThreatCategory> all() {
        return Collections.unmodifiableMap(CATEGORIES);
    }

    public static MobThreatCategory categoryOf(EntityType<?> type) {
        return CATEGORIES.get(type);
    }

    public static boolean isTracked(EntityType<?> type) {
        return CATEGORIES.containsKey(type);
    }

    public static Identifier idOf(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    /** The 32x32 face icon for this mob - see the class doc for where these came from. */
    public static Identifier iconLocation(EntityType<?> type) {
        return Identifier.fromNamespaceAndPath("civmodern", "mob/" + idOf(type).getPath() + ".png");
    }
}
